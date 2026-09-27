package com.cleanguard.app.data.saf

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.SafScanEvent
import com.cleanguard.app.domain.model.SafScanProgress
import com.cleanguard.app.domain.model.SafScanResult
import com.cleanguard.app.domain.model.ScanError
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.repository.SafScanner
import java.io.IOException
import java.util.ArrayDeque
import java.util.Locale
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

class SafScannerImpl(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : SafScanner {
    override fun scan(treeUri: String): Flow<SafScanEvent> = flow {
        val rootUri = treeUri.toUri()
        require(DocumentsContract.isTreeUri(rootUri)) { "SAF scan requires a tree URI" }
        val rootDocumentId = DocumentsContract.getTreeDocumentId(rootUri)
        val rootName = rootDocumentId.substringAfterLast(':').ifBlank { "已选目录" }
        val pendingDirectories = ArrayDeque<DirectoryNode>()
        val visitedDirectories = mutableSetOf<String>()
        val items = mutableListOf<ScannedFile>()
        val errors = mutableListOf<ScanError>()
        var scannedBytes = 0L
        var lastProgressCount = 0L
        var lastProgressTime = elapsedRealtime()

        pendingDirectories.add(DirectoryNode(rootDocumentId, rootName))
        emit(SafScanEvent.Progress(SafScanProgress(0L, 0L, 0)))

        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = pendingDirectories.removeFirst()
            if (!visitedDirectories.add(directory.documentId)) continue
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                rootUri,
                directory.documentId,
            )
            val cursor = try {
                queryCancellable(childrenUri)
            } catch (error: Throwable) {
                throwIfCancellation(error)
                if (!error.isRecoverableSafFailure()) throw error
                errors += ScanError(
                    itemId = directory.documentId,
                    message = "目录“${directory.displayPath}”暂时无法读取，已继续扫描其他目录。",
                )
                null
            }
            if (cursor == null) continue

            try {
                cursor.use { documents ->
                    val columns = SafCursorColumns.from(documents)
                    while (documents.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        try {
                            val documentId = documents.getString(columns.documentId)
                            val displayName = documents.getString(columns.displayName).orEmpty()
                                .ifBlank { "未命名项目" }
                            val mimeType = documents.getString(columns.mimeType)
                            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                                pendingDirectories.add(
                                    DirectoryNode(
                                        documentId = documentId,
                                        displayPath = "${directory.displayPath}/$displayName",
                                    ),
                                )
                                continue
                            }

                            val sizeBytes = documents.getNullableLong(columns.size)?.coerceAtLeast(0L) ?: 0L
                            val modifiedAt = documents.getNullableLong(columns.lastModified)?.coerceAtLeast(0L) ?: 0L
                            items += ScannedFile(
                                id = "saf:${rootUri.authority}:$documentId",
                                displayName = displayName,
                                mimeType = mimeType,
                                sizeBytes = sizeBytes,
                                modifiedAtEpochMillis = modifiedAt,
                                relativePath = "${directory.displayPath}/",
                                source = ScanSource.SAF,
                                category = classifySafFile(displayName, mimeType),
                                contentUri = DocumentsContract.buildDocumentUriUsingTree(
                                    rootUri,
                                    documentId,
                                ).toString(),
                            )
                            scannedBytes = saturatingAdd(scannedBytes, sizeBytes)

                            val count = items.size.toLong()
                            val now = elapsedRealtime()
                            if (count - lastProgressCount >= PROGRESS_FILE_INTERVAL ||
                                now - lastProgressTime >= PROGRESS_TIME_INTERVAL_MILLIS
                            ) {
                                emit(SafScanEvent.Progress(SafScanProgress(count, scannedBytes, errors.size)))
                                lastProgressCount = count
                                lastProgressTime = now
                            }
                        } catch (error: Throwable) {
                            throwIfCancellation(error)
                            if (!error.isRecoverableSafFailure()) throw error
                            errors += ScanError(
                                itemId = null,
                                message = "下载目录中的一个项目无法读取，已跳过。",
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                throwIfCancellation(error)
                if (!error.isRecoverableSafFailure()) throw error
                errors += ScanError(
                    itemId = directory.documentId,
                    message = "目录“${directory.displayPath}”读取中断，已继续扫描其他目录。",
                )
            }
        }

        emit(
            SafScanEvent.Progress(
                SafScanProgress(items.size.toLong(), scannedBytes, errors.size),
            ),
        )
        emit(SafScanEvent.Completed(SafScanResult(items.toList(), errors.toList())))
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
                    PROJECTION,
                    null,
                    null,
                    null,
                    cancellationSignal,
                )
                cursorReference.set(cursor)
                if (continuation.isActive) continuation.resume(cursor) else cursor?.close()
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }

    private data class DirectoryNode(val documentId: String, val displayPath: String)

    private data class SafCursorColumns(
        val documentId: Int,
        val displayName: Int,
        val mimeType: Int,
        val size: Int,
        val lastModified: Int,
    ) {
        companion object {
            fun from(cursor: Cursor) = SafCursorColumns(
                documentId = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                displayName = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                mimeType = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE),
                size = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE),
                lastModified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            )
        }
    }

    private companion object {
        const val PROGRESS_FILE_INTERVAL = 200L
        const val PROGRESS_TIME_INTERVAL_MILLIS = 250L
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

internal fun classifySafFile(displayName: String, mimeType: String?): FileCategory {
    val normalizedMime = mimeType?.lowercase(Locale.ROOT)
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when {
        normalizedMime == "application/vnd.android.package-archive" || extension == "apk" -> FileCategory.APK
        normalizedMime?.startsWith("image/") == true -> FileCategory.IMAGE
        normalizedMime?.startsWith("video/") == true -> FileCategory.VIDEO
        normalizedMime?.startsWith("audio/") == true -> FileCategory.AUDIO
        normalizedMime == "application/pdf" || extension in setOf("pdf", "zip") -> FileCategory.DOCUMENT
        else -> FileCategory.DOWNLOAD
    }
}

private fun Cursor.getNullableLong(index: Int): Long? =
    if (index < 0 || isNull(index)) null else getLong(index)

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private fun Throwable.isRecoverableSafFailure(): Boolean =
    this is SecurityException || this is IOException || this is IllegalArgumentException

private fun throwIfCancellation(error: Throwable) {
    if (error is CancellationException || error is OperationCanceledException) {
        throw CancellationException("SAF scan cancelled", error)
    }
}
