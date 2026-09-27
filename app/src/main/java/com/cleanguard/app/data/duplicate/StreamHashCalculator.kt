package com.cleanguard.app.data.duplicate

import android.content.ContentResolver
import androidx.core.net.toUri
import com.cleanguard.app.domain.duplicate.DuplicateFile
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface HashInputSource {
    fun open(contentUri: String): InputStream
}

class ContentResolverHashInputSource(
    private val contentResolver: ContentResolver,
) : HashInputSource {
    override fun open(contentUri: String): InputStream =
        contentResolver.openInputStream(contentUri.toUri())
            ?: throw FileNotFoundException("Unable to open content URI")
}

class StreamHashCalculator(
    private val inputSource: HashInputSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun quickHash(file: DuplicateFile): String = withContext(ioDispatcher) {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(file.sizeBytes).array())
        inputSource.open(file.contentUri).use { stream ->
            digest.update(stream.readAtMost(QUICK_HASH_CHUNK_SIZE))
        }
        if (file.sizeBytes > QUICK_HASH_CHUNK_SIZE) {
            inputSource.open(file.contentUri).use { stream ->
                stream.skipFully((file.sizeBytes - QUICK_HASH_CHUNK_SIZE).coerceAtLeast(0L))
                digest.update(stream.readAtMost(QUICK_HASH_CHUNK_SIZE))
            }
        }
        digest.digest().toHex()
    }

    suspend fun fullHash(file: DuplicateFile): String = withContext(ioDispatcher) {
        val digest = MessageDigest.getInstance(HASH_ALGORITHM)
        val buffer = ByteArray(STREAM_BUFFER_SIZE)
        inputSource.open(file.contentUri).use { stream ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = stream.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        digest.digest().toHex()
    }

    private suspend fun InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        val fallback = ByteArray(STREAM_BUFFER_SIZE)
        while (remaining > 0L) {
            currentCoroutineContext().ensureActive()
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = read(fallback, 0, minOf(fallback.size.toLong(), remaining).toInt())
                if (read < 0) break
                remaining -= read
            }
        }
    }

    private suspend fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val output = ByteArray(maxBytes)
        var offset = 0
        while (offset < maxBytes) {
            currentCoroutineContext().ensureActive()
            val count = read(output, offset, maxBytes - offset)
            if (count < 0) break
            offset += count
        }
        return if (offset == output.size) output else output.copyOf(offset)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte) }

    companion object {
        const val QUICK_HASH_CHUNK_SIZE = 64 * 1024
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val HASH_ALGORITHM = "SHA-256"
    }
}
