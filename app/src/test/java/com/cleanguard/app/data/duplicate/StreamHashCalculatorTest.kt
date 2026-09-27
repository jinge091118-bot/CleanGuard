package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
