package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.duplicate.DuplicateFileHasher
import com.cleanguard.app.domain.duplicate.DuplicateVerificationStatus
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateVerificationEngineImplTest {
    @Test
    fun differentSizes_areNotDuplicatesAndAreNotHashed() = runTest {
        val hasher = FakeDuplicateFileHasher()
        val engine = DuplicateVerificationEngineImpl(hasher)

        val result = engine.verify(listOf(file("a", 10L), file("b", 11L)))

        assertTrue(result.groups.isEmpty())
        assertTrue(hasher.quickRequests.isEmpty())
    }

    @Test
    fun sameSizeDifferentQuickHash_areNotDuplicates() = runTest {
        val hasher = FakeDuplicateFileHasher(
            quick = mapOf("a" to "qa", "b" to "qb"),
        )

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            listOf(file("a"), file("b")),
        )

        assertTrue(result.groups.isEmpty())
        assertTrue(hasher.fullRequests.isEmpty())
    }

    @Test
    fun sameQuickDifferentFullHash_isNotConfirmed() = runTest {
        val hasher = FakeDuplicateFileHasher(
            quick = mapOf("a" to "same", "b" to "same"),
            full = mapOf("a" to "fa", "b" to "fb"),
        )

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            listOf(file("a"), file("b")),
        )

        assertTrue(result.groups.isEmpty())
    }

    @Test
    fun identicalFullHash_confirmsGroupButPreservesReviewRisk() = runTest {
        val hasher = FakeDuplicateFileHasher(
            quick = mapOf("a" to "same", "b" to "same", "c" to "same"),
            full = mapOf("a" to "full", "b" to "full", "c" to "full"),
        )

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            listOf(file("a"), file("b"), file("c")),
        )

        val group = result.groups.single()
        assertEquals(DuplicateVerificationStatus.CONFIRMED_DUPLICATE, group.verificationStatus)
        assertEquals(3, group.files.size)
        assertEquals(300L, group.totalBytes)
        assertEquals(200L, group.reclaimableBytes)
        assertTrue(group.files.all { it.riskLevel == RiskLevel.REVIEW })
    }

    @Test
    fun dangerAndUnknownItems_areNeverPromotedToConfirmedDuplicate() = runTest {
        val hasher = FakeDuplicateFileHasher()

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            listOf(
                file("danger", risk = RiskLevel.DANGER),
                file("unknown", risk = RiskLevel.UNKNOWN),
            ),
        )

        assertTrue(result.groups.isEmpty())
        assertTrue(hasher.quickRequests.isEmpty())
    }

    @Test
    fun unreadableFile_doesNotStopOtherConfirmedGroup() = runTest {
        val hasher = FakeDuplicateFileHasher(
            quick = mapOf("a" to "same", "b" to "same", "broken" to "same"),
            full = mapOf("a" to "full", "b" to "full"),
            fullFailures = setOf("broken"),
        )

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            listOf(file("a"), file("b"), file("broken")),
        )

        assertEquals(1, result.groups.size)
        assertEquals(1, result.issues.size)
        assertEquals("broken", result.issues.single().itemId)
    }

    @Test
    fun fullHashConcurrency_neverExceedsTwo() = runTest {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val hasher = object : DuplicateFileHasher {
            override suspend fun quickHash(file: DuplicateFile) = "same"

            override suspend fun fullHash(file: DuplicateFile): String {
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                delay(100)
                active.decrementAndGet()
                return "full"
            }
        }

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            (1..6).map { file(it.toString()) },
        )

        assertEquals(1, result.groups.size)
        assertEquals(2, maximum.get())
    }

    @Test
    fun tenThousandCandidates_useTwoFixedFullHashWorkers() = runTest {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        val fullHashCount = AtomicInteger(0)
        val hasher = object : DuplicateFileHasher {
            override suspend fun quickHash(file: DuplicateFile) = "same"

            override suspend fun fullHash(file: DuplicateFile): String {
                val current = active.incrementAndGet()
                maximum.updateAndGet { maxOf(it, current) }
                yield()
                fullHashCount.incrementAndGet()
                active.decrementAndGet()
                return "full"
            }
        }

        val result = DuplicateVerificationEngineImpl(hasher).verify(
            List(10_000) { index -> file("scale-$index") },
        )

        assertEquals(10_000, fullHashCount.get())
        assertEquals(2, maximum.get())
        assertEquals(10_000, result.groups.single().files.size)
    }

    private fun file(
        id: String,
        size: Long = 100L,
        risk: RiskLevel = RiskLevel.REVIEW,
    ) = DuplicateFile(
        itemId = id,
        contentUri = "content://test/$id",
        displayName = "$id.bin",
        mimeType = null,
        relativePath = "Download/",
        sizeBytes = size,
        modifiedAtEpochMillis = id.hashCode().toLong(),
        source = ScanSource.MEDIA_STORE,
        riskLevel = risk,
    )
}

private class FakeDuplicateFileHasher(
    private val quick: Map<String, String> = emptyMap(),
    private val full: Map<String, String> = emptyMap(),
    private val fullFailures: Set<String> = emptySet(),
) : DuplicateFileHasher {
    val quickRequests = mutableListOf<String>()
    val fullRequests = mutableListOf<String>()

    override suspend fun quickHash(file: DuplicateFile): String {
        quickRequests += file.itemId
        return quick[file.itemId] ?: "quick-${file.itemId}"
    }

    override suspend fun fullHash(file: DuplicateFile): String {
        fullRequests += file.itemId
        if (file.itemId in fullFailures) throw IOException("broken")
        return full[file.itemId] ?: "full-${file.itemId}"
    }
}
