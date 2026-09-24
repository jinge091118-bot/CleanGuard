package com.cleanguard.app.data.media

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ext.SdkExtensions
import android.provider.MediaStore
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.IndexedItemRecord
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException

internal interface MediaStoreGateway {
    val supportsGeneration: Boolean

    suspend fun availableVolumes(): Set<String>
    suspend fun version(volumeName: String): String?
    suspend fun generation(volumeName: String): Long?
    suspend fun readMetadata(volumeName: String, modifiedAfterGeneration: Long?): List<IndexedItemRecord>
    suspend fun readCurrentItemIds(volumeName: String): Set<String>
}

internal class AndroidMediaStoreGateway(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val rExtensionVersion: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R)
    } else {
        0
    },
) : MediaStoreGateway {
    override val supportsGeneration: Boolean =
        sdkInt >= Build.VERSION_CODES.R && rExtensionVersion >= 1

    override suspend fun availableVolumes(): Set<String> =
        MediaStore.getExternalVolumeNames(context).ifEmpty { setOf(MediaStore.VOLUME_EXTERNAL) }

    override suspend fun version(volumeName: String): String? =
        MediaStore.getVersion(context, volumeName)

    @SuppressLint("NewApi") // Guarded by the Android R extension-version capability check above.
    override suspend fun generation(volumeName: String): Long? =
        if (supportsGeneration) MediaStore.getGeneration(context, volumeName) else null

    override suspend fun readMetadata(
        volumeName: String,
        modifiedAfterGeneration: Long?,
    ): List<IndexedItemRecord> = buildList {
        collections(volumeName).forEach { collection ->
            val selection = modifiedAfterGeneration?.let {
                "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?"
            }
            val selectionArgs = modifiedAfterGeneration?.let { arrayOf(it.toString()) }
            val cursor = queryCancellable(
                uri = collection.uri,
                projection = if (supportsGeneration) GENERATION_PROJECTION else LEGACY_PROJECTION,
                selection = selection,
                selectionArgs = selectionArgs,
            ) ?: throw IOException("MediaStore returned no cursor for ${collection.idPrefix}")
            cursor.use { mediaCursor ->
                val columns = GatewayCursorColumns.from(mediaCursor, supportsGeneration)
                while (mediaCursor.moveToNext()) {
                    add(mediaCursor.toIndexedItem(collection, columns, volumeName))
                }
            }
        }
    }

    override suspend fun readCurrentItemIds(volumeName: String): Set<String> = buildSet {
        collections(volumeName).forEach { collection ->
            val cursor = queryCancellable(
                uri = collection.uri,
                projection = ID_PROJECTION,
                selection = null,
                selectionArgs = null,
            ) ?: throw IOException("MediaStore returned no ID cursor for ${collection.idPrefix}")
            cursor.use { mediaCursor ->
                val idColumn = mediaCursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                while (mediaCursor.moveToNext()) {
                    add(mediaItemId(volumeName, collection.idPrefix, mediaCursor.getLong(idColumn)))
                }
            }
        }
    }

    private suspend fun queryCancellable(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Cursor? = suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        val cursorReference = AtomicReference<Cursor?>()
        continuation.invokeOnCancellation {
            signal.cancel()
            cursorReference.getAndSet(null)?.close()
        }
        try {
            val cursor = contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null,
                signal,
            )
            cursorReference.set(cursor)
            if (continuation.isActive) {
                continuation.resume(cursor)
            } else {
                cursorReference.getAndSet(null)?.close()
            }
        } catch (error: Throwable) {
            if (continuation.isActive) {
                val propagated = if (error is OperationCanceledException) {
                    CancellationException("MediaStore query cancelled").also { it.initCause(error) }
                } else {
                    error
                }
                continuation.resumeWithException(propagated)
            }
        }
    }

    private companion object {
        val ID_PROJECTION = arrayOf(MediaStore.MediaColumns._ID)
        val LEGACY_PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val GENERATION_PROJECTION = LEGACY_PROJECTION + arrayOf(
            MediaStore.MediaColumns.GENERATION_MODIFIED,
        )
    }
}

private data class GatewayCollection(
    val uri: Uri,
    val idPrefix: String,
    val label: String,
    val category: FileCategory,
)

private fun collections(volumeName: String): List<GatewayCollection> = listOf(
    GatewayCollection(
        MediaStore.Images.Media.getContentUri(volumeName),
        "image",
        "图片",
        FileCategory.IMAGE,
    ),
    GatewayCollection(
        MediaStore.Video.Media.getContentUri(volumeName),
        "video",
        "视频",
        FileCategory.VIDEO,
    ),
    GatewayCollection(
        MediaStore.Audio.Media.getContentUri(volumeName),
        "audio",
        "音频",
        FileCategory.AUDIO,
    ),
)

private data class GatewayCursorColumns(
    val id: Int,
    val displayName: Int,
    val mimeType: Int,
    val size: Int,
    val dateModified: Int,
    val relativePath: Int,
    val generationModified: Int?,
) {
    companion object {
        fun from(cursor: Cursor, hasGeneration: Boolean) = GatewayCursorColumns(
            id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID),
            displayName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME),
            mimeType = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE),
            size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE),
            dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED),
            relativePath = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH),
            generationModified = if (hasGeneration) {
                cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.GENERATION_MODIFIED)
            } else {
                null
            },
        )
    }
}

private fun Cursor.toIndexedItem(
    collection: GatewayCollection,
    columns: GatewayCursorColumns,
    volumeName: String,
): IndexedItemRecord {
    val mediaId = getLong(columns.id)
    val contentUri = ContentUris.withAppendedId(collection.uri, mediaId).toString()
    val modifiedSeconds = nullableLong(columns.dateModified).coerceAtLeast(0L)
    return IndexedItemRecord(
        id = mediaItemId(volumeName, collection.idPrefix, mediaId),
        contentUri = contentUri,
        source = ScanSource.MEDIA_STORE,
        displayName = getString(columns.displayName)?.takeIf(String::isNotBlank)
            ?: "未命名${collection.label}",
        mimeType = getString(columns.mimeType),
        sizeBytes = nullableLong(columns.size).coerceAtLeast(0L),
        modifiedAt = secondsToMillis(modifiedSeconds),
        relativePath = getString(columns.relativePath),
        category = collection.category,
        riskLevel = RiskLevel.UNKNOWN,
        quickHash = null,
        fullHash = null,
        mediaStoreGeneration = columns.generationModified?.let(::nullableLong),
        lastSeenScanId = null,
        lastIndexedAt = 0L,
        volumeName = volumeName,
    )
}

internal fun mediaItemId(volumeName: String, collection: String, mediaId: Long): String =
    if (volumeName == MediaStore.VOLUME_EXTERNAL || volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
        "media:$collection:$mediaId"
    } else {
        "media:$volumeName:$collection:$mediaId"
    }

private fun Cursor.nullableLong(columnIndex: Int): Long =
    if (isNull(columnIndex)) 0L else getLong(columnIndex)

private fun secondsToMillis(seconds: Long): Long =
    if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
