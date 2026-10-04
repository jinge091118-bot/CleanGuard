package com.cleanguard.app.data.cleanup

import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.CleanupOutcome
import com.cleanguard.app.domain.model.CleanupPlan
import com.cleanguard.app.domain.model.CleanupResult
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.repository.CleanupRepository
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.NoOpCleanupHistoryRepository
import com.cleanguard.app.domain.repository.StorageRepository
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryItem
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MediaStoreCleanupRepository(
    private val contentResolver: ContentResolver,
    private val storageRepository: StorageRepository,
    private val cleanupHistoryRepository: CleanupHistoryRepository = NoOpCleanupHistoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : CleanupRepository {
    private val result = MutableStateFlow<CleanupResult?>(null)
    private var pendingPlan: CleanupPlan? = null
    private var undoPlan: CleanupPlan? = null
    private var beforeUsedBytes: Long = 0L

    override val latestResult: Flow<CleanupResult?> = result.asStateFlow()

    override suspend fun createTrashRequest(plan: CleanupPlan): Result<IntentSender> =
        withContext(ioDispatcher) {
            beforeUsedBytes = currentUsedBytes(fallback = 0L)
            pendingPlan = plan
            createMediaStoreTrashRequest(plan, trash = true)
        }

    override suspend fun completeTrashRequest(
        plan: CleanupPlan,
        outcome: SystemConfirmationOutcome,
    ): CleanupResult = withContext(ioDispatcher) {
        val activePlan = pendingPlan?.takeIf { it.id == plan.id } ?: plan
        val afterUsedBytes = currentUsedBytes(fallback = beforeUsedBytes)
        val completedResult = CleanupResultFactory.fromTrashOutcome(
            plan = activePlan,
            outcome = outcome,
            beforeUsedBytes = beforeUsedBytes,
            afterUsedBytes = afterUsedBytes,
        )
        undoPlan = activePlan.takeIf { outcome == SystemConfirmationOutcome.CONFIRMED }
        pendingPlan = null
        result.value = completedResult
        persistHistory(activePlan, completedResult, outcome)
        completedResult
    }

    override suspend fun createUndoRequest(): Result<IntentSender> = withContext(ioDispatcher) {
        val plan = undoPlan
            ?: return@withContext Result.failure(IllegalStateException("No trash operation is available to undo"))
        createMediaStoreTrashRequest(plan, trash = false)
    }

    override suspend fun completeUndoRequest(confirmed: Boolean): Result<CleanupResult> =
        withContext(ioDispatcher) {
            val currentResult = result.value
                ?: return@withContext Result.failure(IllegalStateException("No cleanup result is available"))
            if (!confirmed) return@withContext Result.success(currentResult)

            val updatedResult = CleanupResultFactory.afterUndo(
                result = currentResult,
                afterUsedBytes = currentUsedBytes(fallback = currentResult.beforeUsedBytes),
            )
            undoPlan = null
            result.value = updatedResult
            runCatching {
                cleanupHistoryRepository.updateRestoreStatus(
                    currentResult.cleanupId ?: return@runCatching,
                    CleanupRestoreStatus.RESTORED,
                )
            }
            Result.success(updatedResult)
        }

    private suspend fun persistHistory(
        plan: CleanupPlan,
        cleanupResult: CleanupResult,
        outcome: SystemConfirmationOutcome,
    ) {
        val entry = CleanupHistoryEntryFactory.fromTrashOutcome(
            plan = plan,
            result = cleanupResult,
            outcome = outcome,
            timestamp = currentTimeMillis(),
        )
        try {
            cleanupHistoryRepository.save(entry)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The system operation is already complete. A local history write must not change it.
        }
    }

    private fun createMediaStoreTrashRequest(
        plan: CleanupPlan,
        trash: Boolean,
    ): Result<IntentSender> {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return Result.failure(
                    UnsupportedOperationException("MediaStore Trash requires Android 11 or newer"),
                )
            }
            val uris = plan.items.map { item ->
                require(item.source == ScanSource.MEDIA_STORE) {
                    "Only MediaStore items can enter a MediaStore trash request"
                }
                require(item.action == CleanupAction.TRASH) {
                    "Only TRASH actions can enter a MediaStore trash request"
                }
                require(item.riskLevel == RiskLevel.SAFE || item.riskLevel == RiskLevel.REVIEW) {
                    "Protected items cannot enter a MediaStore trash request"
                }
                mediaStoreUri(item.itemId)
                    ?: throw IllegalArgumentException("Invalid MediaStore item id: ${item.itemId}")
            }
            require(uris.isNotEmpty()) { "Trash request cannot be empty" }
            return Result.success(
                MediaStore.createTrashRequest(contentResolver, uris, trash).intentSender,
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: SecurityException) {
            return Result.failure(exception)
        } catch (exception: IOException) {
            return Result.failure(exception)
        } catch (exception: IllegalArgumentException) {
            return Result.failure(exception)
        } catch (exception: IllegalStateException) {
            return Result.failure(exception)
        }
    }

    private suspend fun currentUsedBytes(fallback: Long): Long =
        storageRepository.loadStorageOverview().getOrNull()?.usedBytes ?: fallback
}

internal object CleanupResultFactory {
    fun fromTrashOutcome(
        plan: CleanupPlan,
        outcome: SystemConfirmationOutcome,
        beforeUsedBytes: Long,
        afterUsedBytes: Long,
    ): CleanupResult {
        val requestedCount = plan.items.size
        return CleanupResult(
            cleanupId = plan.id,
            requestedCount = requestedCount,
            successCount = if (outcome == SystemConfirmationOutcome.CONFIRMED) requestedCount else 0,
            failureCount = if (outcome == SystemConfirmationOutcome.FAILED) requestedCount else 0,
            cancelledCount = if (outcome == SystemConfirmationOutcome.CANCELLED) requestedCount else 0,
            reclaimedBytes = if (outcome == SystemConfirmationOutcome.CONFIRMED) {
                (beforeUsedBytes - afterUsedBytes).coerceAtLeast(0L)
            } else {
                0L
            },
            beforeUsedBytes = beforeUsedBytes.coerceAtLeast(0L),
            afterUsedBytes = afterUsedBytes.coerceAtLeast(0L),
            undoAvailable = outcome == SystemConfirmationOutcome.CONFIRMED,
            outcome = when (outcome) {
                SystemConfirmationOutcome.CONFIRMED -> CleanupOutcome.COMPLETED
                SystemConfirmationOutcome.CANCELLED -> CleanupOutcome.CANCELLED
                SystemConfirmationOutcome.FAILED -> CleanupOutcome.FAILED
            },
        )
    }

    fun afterUndo(
        result: CleanupResult,
        afterUsedBytes: Long,
    ): CleanupResult = result.copy(
        reclaimedBytes = 0L,
        afterUsedBytes = afterUsedBytes.coerceAtLeast(0L),
        undoAvailable = false,
        outcome = CleanupOutcome.UNDONE,
    )
}

internal object CleanupHistoryEntryFactory {
    fun fromTrashOutcome(
        plan: CleanupPlan,
        result: CleanupResult,
        outcome: SystemConfirmationOutcome,
        timestamp: Long,
    ): CleanupHistoryEntry = CleanupHistoryEntry(
        cleanupId = plan.id,
        timestamp = timestamp,
        requestedItemCount = result.requestedCount,
        processedItemCount = result.successCount,
        failedItemCount = result.failureCount,
        requestedBytes = plan.estimatedReclaimBytes,
        freedBytes = result.reclaimedBytes,
        cleanupType = CleanupHistoryType.MEDIA_STORE_TRASH,
        usedMediaStoreTrash = true,
        restoreStatus = if (outcome == SystemConfirmationOutcome.CONFIRMED) {
            CleanupRestoreStatus.AVAILABLE
        } else {
            CleanupRestoreStatus.NOT_AVAILABLE
        },
        items = plan.items.map { item ->
            CleanupHistoryItem(
                itemKey = item.itemId,
                contentUri = mediaStoreContentUriString(item.itemId),
            )
        },
    )
}

internal fun mediaStoreUri(itemId: String): Uri? {
    val reference = parseMediaStoreItemId(itemId) ?: return null
    val collectionUri = when (reference.collection) {
        "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> return null
    }
    return ContentUris.withAppendedId(collectionUri, reference.mediaId)
}

internal fun mediaStoreContentUriString(itemId: String): String? {
    val reference = parseMediaStoreItemId(itemId) ?: return null
    val path = when (reference.collection) {
        "image" -> "images"
        "video" -> "video"
        "audio" -> "audio"
        else -> return null
    }
    return "content://media/external/$path/media/${reference.mediaId}"
}

internal data class MediaStoreItemReference(
    val collection: String,
    val mediaId: Long,
)

internal fun parseMediaStoreItemId(itemId: String): MediaStoreItemReference? {
    val match = MEDIA_ITEM_ID.matchEntire(itemId) ?: return null
    val mediaId = match.groupValues[2].toLongOrNull() ?: return null
    return MediaStoreItemReference(match.groupValues[1], mediaId)
}

private val MEDIA_ITEM_ID = Regex("^media:(image|video|audio):([0-9]+)$")
