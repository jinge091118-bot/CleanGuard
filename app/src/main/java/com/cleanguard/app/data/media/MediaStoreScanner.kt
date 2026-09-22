package com.cleanguard.app.data.media

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.SystemClock
import android.provider.MediaStore
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanProgress
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaScanStage
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.ScanError
import com.cleanguard.app.domain.repository.MediaScanner
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine

class MediaStoreScanner(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : MediaScanner {
    override fun scan(): Flow<MediaScanEvent> = flow {
        val items = mutableListOf<MediaStoreItem>()
        val errors = mutableListOf<ScanError>()
        var scannedFileCount = 0L
        var scannedBytes = 0L
        val throttle = ScanProgressThrottle(startTimeMillis = elapsedRealtime())

        suspend fun publishProgress(
            stage: MediaScanStage,
            fraction: Float,
        ) {
            emit(
                MediaScanEvent.Progress(
                    MediaScanProgress(
                        stage = stage,
                        fraction = fraction.coerceIn(0f, 1f),
                        scannedFileCount = scannedFileCount,
                        scannedBytes = scannedBytes,
                        errorCount = errors.size,
                    ),
                ),
            )
            throttle.markEmitted(scannedFileCount, elapsedRealtime())
        }

        publishProgress(MediaScanStage.PREPARING, 0f)

        collections.forEachIndexed { collectionIndex, collection ->
            currentCoroutineContext().ensureActive()
            publishProgress(
                stage = collection.stage,
                fraction = mediaScanFraction(collectionIndex, 0, 0),
            )

            val cursor = try {
                queryCancellable(collection.uri)
            } catch (error: Throwable) {
                throwIfCancellation(error)
                if (!error.isRecoverableScanFailure()) throw error
                errors += collection.toScanError(error)
                null
            }

            if (cursor == null) {
                if (errors.none { it.itemId == collection.errorItemId }) {
                    errors += ScanError(
                        itemId = collection.errorItemId,
                        message = "系统未返回${collection.displayLabel}媒体索引，已继续扫描其他类型。",
                    )
                }
                publishProgress(
                    stage = collection.stage,
                    fraction = mediaScanFraction(collectionIndex, 0, 0, collectionComplete = true),
                )
                return@forEachIndexed
            }

            try {
                cursor.use { mediaCursor ->
                    val columns = MediaCursorColumns.from(mediaCursor)
                    val collectionSize = mediaCursor.count.coerceAtLeast(0)
                    var collectionPosition = 0

                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val hasNext = mediaCursor.moveToNext()
                        if (!hasNext) break
                        collectionPosition += 1

                        val item = try {
                            mediaCursor.toMediaStoreItem(collection, columns)
                        } catch (error: Throwable) {
                            throwIfCancellation(error)
                            if (!error.isRecoverableScanFailure()) throw error
                            errors += ScanError(
                                itemId = null,
                                message = "一项${collection.displayLabel}媒体记录无法读取，已跳过。",
                            )
                            continue
                        }

                        items += item
                        scannedFileCount += 1
                        scannedBytes = saturatingAdd(scannedBytes, item.sizeBytes)

                        val now = elapsedRealtime()
                        if (throttle.shouldEmit(scannedFileCount, now)) {
                            publishProgress(
                                stage = collection.stage,
                                fraction = mediaScanFraction(
                                    collectionIndex = collectionIndex,
                                    collectionPosition = collectionPosition,
                                    collectionSize = collectionSize,
                                ),
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                throwIfCancellation(error)
                if (!error.isRecoverableScanFailure()) throw error
                errors += collection.toScanError(error)
            }

            publishProgress(
                stage = collection.stage,
                fraction = mediaScanFraction(
                    collectionIndex = collectionIndex,
                    collectionPosition = 0,
                    collectionSize = 0,
                    collectionComplete = true,
                ),
            )
        }

        val result = MediaScanResult(
            items = items.toList(),
            errors = errors.toList(),
        )
        publishProgress(MediaScanStage.COMPLETE, 1f)
        emit(MediaScanEvent.Completed(result))
    }.flowOn(ioDispatcher)

    private suspend fun queryCancellable(uri: Uri): Cursor? =
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            val cursorReference = AtomicReference<Cursor?>()

            continuation.invokeOnCancellation {
                cancellationSignal.cancel()
                cursorReference.getAndSet(null)?.close()
            }

            try {
                val cursor = contentResolver.query(
                    uri,
                    projection,
                    null,
                    null,
                    null,
                    cancellationSignal,
                )
                cursorReference.set(cursor)
                if (continuation.isActive) {
                    continuation.resume(cursor)
                } else {
                    cursorReference.getAndSet(null)?.close()
                }
            } catch (error: Throwable) {
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }

    private fun throwIfCancellation(error: Throwable) {
        if (error is CancellationException) throw error
        if (error is OperationCanceledException) {
            throw CancellationException("媒体扫描已取消").also { it.initCause(error) }
        }
    }

    private companion object {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )

        val collections = listOf(
            MediaCollection(
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                idPrefix = "image",
                displayLabel = "图片",
                category = FileCategory.IMAGE,
                stage = MediaScanStage.IMAGES,
            ),
            MediaCollection(
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                idPrefix = "video",
                displayLabel = "视频",
                category = FileCategory.VIDEO,
                stage = MediaScanStage.VIDEOS,
            ),
            MediaCollection(
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                idPrefix = "audio",
                displayLabel = "音频",
                category = FileCategory.AUDIO,
                stage = MediaScanStage.AUDIO,
            ),
        )
    }
}

private data class MediaCollection(
    val uri: Uri,
    val idPrefix: String,
    val displayLabel: String,
    val category: FileCategory,
    val stage: MediaScanStage,
) {
    val errorItemId: String = "media:$idPrefix"

    fun toScanError(error: Throwable): ScanError {
        val reason = when (error) {
            is SecurityException -> "没有系统授权"
            is IOException -> "发生 I/O 异常"
            is IllegalArgumentException -> "媒体索引字段不可用"
            else -> "发生系统异常"
        }
        return ScanError(
            itemId = errorItemId,
            message = "${displayLabel}媒体索引$reason，已继续扫描其他类型。",
        )
    }
}

private data class MediaCursorColumns(
    val id: Int,
    val displayName: Int,
    val mimeType: Int,
    val size: Int,
    val dateModified: Int,
    val relativePath: Int,
) {
    companion object {
        fun from(cursor: Cursor) = MediaCursorColumns(
            id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID),
            displayName = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME),
            mimeType = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE),
            size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE),
            dateModified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED),
            relativePath = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH),
        )
    }
}

private fun Cursor.toMediaStoreItem(
    collection: MediaCollection,
    columns: MediaCursorColumns,
): MediaStoreItem {
    val mediaStoreId = getLong(columns.id)
    val modifiedSeconds = nullableLong(columns.dateModified).coerceAtLeast(0L)
    return MediaStoreItem(
        id = "media:${collection.idPrefix}:$mediaStoreId",
        mediaStoreId = mediaStoreId,
        displayName = getString(columns.displayName)?.takeIf(String::isNotBlank)
            ?: "未命名${collection.displayLabel}",
        mimeType = getString(columns.mimeType),
        sizeBytes = nullableLong(columns.size).coerceAtLeast(0L),
        modifiedAtEpochMillis = secondsToMillis(modifiedSeconds),
        relativePath = getString(columns.relativePath),
        category = collection.category,
    )
}

private fun Cursor.nullableLong(columnIndex: Int): Long =
    if (isNull(columnIndex)) 0L else getLong(columnIndex)

private fun secondsToMillis(seconds: Long): Long =
    if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L

private fun Throwable.isRecoverableScanFailure(): Boolean =
    this is SecurityException || this is IOException || this is IllegalArgumentException

private fun saturatingAdd(current: Long, value: Long): Long =
    if (value > Long.MAX_VALUE - current) Long.MAX_VALUE else current + value

internal fun mediaScanFraction(
    collectionIndex: Int,
    collectionPosition: Int,
    collectionSize: Int,
    collectionComplete: Boolean = false,
): Float {
    val withinCollection = when {
        collectionComplete -> 1f
        collectionSize <= 0 -> 0f
        else -> collectionPosition.toFloat() / collectionSize.toFloat()
    }
    return ((collectionIndex + withinCollection) / 3f).coerceIn(0f, 1f)
}

internal class ScanProgressThrottle(
    startTimeMillis: Long,
    private val fileBatchSize: Long = 200L,
    private val intervalMillis: Long = 250L,
) {
    private var lastEmittedFileCount: Long = 0L
    private var lastEmittedAtMillis: Long = startTimeMillis

    fun shouldEmit(scannedFileCount: Long, nowMillis: Long): Boolean =
        scannedFileCount - lastEmittedFileCount >= fileBatchSize ||
            nowMillis - lastEmittedAtMillis >= intervalMillis

    fun markEmitted(scannedFileCount: Long, nowMillis: Long) {
        lastEmittedFileCount = scannedFileCount
        lastEmittedAtMillis = nowMillis
    }
}
