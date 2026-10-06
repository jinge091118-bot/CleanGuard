package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamHashCalculatorTest {
    @Test
    fun quickHash_usesSizeAndFirstAndLast64KiB() = runTest {
        val bytes = ByteArray(StreamHashCalculator.QUICK_HASH_CHUNK_SIZE * 3) { index ->
            (index % 251).toByte()
        }
        val calculator = StreamHashCalculator(HashInputSource { ByteArrayInputStream(bytes) })

        val actual = calculator.quickHash(file(sizeBytes = bytes.size.toLong()))

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(bytes.size.toLong()).array())
        digest.update(bytes.copyOfRange(0, StreamHashCalculator.QUICK_HASH_CHUNK_SIZE))
        digest.update(bytes.copyOfRange(bytes.size - StreamHashCalculator.QUICK_HASH_CHUNK_SIZE, bytes.size))
        assertEquals(digest.digest().toHex(), actual)
    }

    @Test
    fun fullHash_matchesSha256WithoutWholeFileReadApi() = runTest {
        val bytes = ByteArray(200_000) { index -> (index % 127).toByte() }
        val calculator = StreamHashCalculator(HashInputSource { ByteArrayInputStream(bytes) })

        assertEquals(
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex(),
            calculator.fullHash(file(sizeBytes = bytes.size.toLong())),
        )
    }

    @Test
    fun fullHash_streamsLargeInputWithAtMost64KiBReads() = runTest {
        val stream = GeneratedInputStream(16 * 1024 * 1024)
        val calculator = StreamHashCalculator(HashInputSource { stream })

        val hash = calculator.fullHash(file(sizeBytes = stream.totalBytes.toLong()))

        assertEquals(64, hash.length)
        assertTrue(stream.readCalls > 1)
        assertTrue(stream.maximumRequestedBytes <= 64 * 1024)
    }

    private fun file(sizeBytes: Long) = DuplicateFile(
        itemId = "item",
        contentUri = "content://test/item",
        displayName = "item.bin",
        mimeType = "application/octet-stream",
        relativePath = "Download/",
        sizeBytes = sizeBytes,
        modifiedAtEpochMillis = 1L,
        source = ScanSource.MEDIA_STORE,
        riskLevel = RiskLevel.REVIEW,
    )
}

private class GeneratedInputStream(
    val totalBytes: Int,
) : InputStream() {
    private var position = 0
    var maximumRequestedBytes: Int = 0
        private set
    var readCalls: Int = 0
        private set

    override fun read(): Int = if (position >= totalBytes) {
        -1
    } else {
        (position++ % 251)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        maximumRequestedBytes = maxOf(maximumRequestedBytes, length)
        if (position >= totalBytes) return -1
        val count = minOf(length, totalBytes - position)
        repeat(count) { index -> buffer[offset + index] = ((position + index) % 251).toByte() }
        position += count
        readCalls += 1
        return count
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
