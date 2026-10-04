package com.cleanguard.app.data.cleanup

import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import com.cleanguard.app.domain.repository.MediaRecoveryRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRecoveryRepository internal constructor(
    private val historyRepository: CleanupHistoryRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val requestFactory: MediaStoreTrashRequestFactory,
) : MediaRecoveryRepository {
    constructor(
        contentResolver: ContentResolver,
        historyRepository: CleanupHistoryRepository,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        historyRepository = historyRepository,
        ioDispatcher = ioDispatcher,
        sdkInt = Build.VERSION.SDK_INT,
        requestFactory = AndroidMediaStoreTrashRequestFactory(contentResolver),
    )

    override suspend fun createRestoreRequest(
        entry: CleanupHistoryEntry,
    ): Result<IntentSender> = withContext(ioDispatcher) {
        if (entry.restoreStatus != CleanupRestoreStatus.AVAILABLE) {
            return@withContext Result.failure(
                IllegalStateException("This cleanup history entry is not available for restore"),
            )
        }
        try {
            if (sdkInt < Build.VERSION_CODES.R) {
                throw UnsupportedOperationException("MediaStore restore requires Android 11 or newer")
            }
            Result.success(requestFactory.create(requireRestoreUris(entry), trash = false))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            if (exception is SecurityException ||
                exception is IOException ||
                exception is IllegalArgumentException ||
                exception is IllegalStateException ||
                exception is UnsupportedOperationException
            ) {
                markUnavailable(entry.cleanupId)
                Result.failure(exception)
            } else {
                throw exception
            }
        }
    }

    override suspend fun completeRestore(
        entry: CleanupHistoryEntry,
        outcome: SystemConfirmationOutcome,
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            when (outcome) {
                SystemConfirmationOutcome.CONFIRMED -> updateStatus(
                    entry.cleanupId,
                    CleanupRestoreStatus.RESTORED,
                )

                SystemConfirmationOutcome.CANCELLED -> Unit
                SystemConfirmationOutcome.FAILED -> markUnavailable(entry.cleanupId)
            }
            Result.success(Unit)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }

    private suspend fun markUnavailable(cleanupId: String) {
        try {
            updateStatus(cleanupId, CleanupRestoreStatus.FAILED)
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // The original restore failure remains the result even if local status persistence fails.
        }
    }

    private suspend fun updateStatus(cleanupId: String, status: CleanupRestoreStatus) {
        check(historyRepository.updateRestoreStatus(cleanupId, status) == 1) {
            "Cleanup history entry no longer exists"
        }
    }
}

internal fun requireRestoreUris(entry: CleanupHistoryEntry): List<Uri> {
    return restoreUriStrings(entry).map(Uri::parse)
}

internal fun restoreUriStrings(entry: CleanupHistoryEntry): List<String> {
    require(entry.cleanupType == CleanupHistoryType.MEDIA_STORE_TRASH)
    require(entry.usedMediaStoreTrash)
    require(entry.processedItemCount > 0)
    val uris = entry.items.map { item ->
        val uri = item.contentUri
            ?: throw IllegalArgumentException("Missing MediaStore URI")
        require(uri.startsWith("${ContentResolver.SCHEME_CONTENT}://media/")) {
            "Invalid MediaStore URI"
        }
        uri
    }.distinct()
    require(uris.isNotEmpty()) { "Restore request cannot be empty" }
    return uris
}

internal fun interface MediaStoreTrashRequestFactory {
    fun create(uris: List<Uri>, trash: Boolean): IntentSender
}

private class AndroidMediaStoreTrashRequestFactory(
    private val contentResolver: ContentResolver,
) : MediaStoreTrashRequestFactory {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun create(uris: List<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(contentResolver, uris, trash).intentSender
}
