package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.repository.HashCacheRepository
import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CachingDuplicateFileHasherTest {
    @Test
    fun matchingFileVersion_reusesCachedHashes() = runTest {
        val cache = MemoryHashCacheRepository()
        val file = file(modifiedAt = 1L)
        cache.upsert(entry(file, quickHash = "quick-cached", fullHash = "full-cached"))
        var opens = 0
        val hasher = CachingDuplicateFileHasher(
            cache,
            StreamHashCalculator(HashInputSource {
                opens += 1
                ByteArrayInputStream(byteArrayOf(1, 2, 3))
            }),
        )

        assertEquals("quick-cached", hasher.quickHash(file))
        assertEquals("full-cached", hasher.fullHash(file))
        assertEquals(0, opens)
    }

    @Test
    fun modifiedFile_invalidatesOldVersionAndComputesNewHashes() = runTest {
        val cache = MemoryHashCacheRepository()
        val old = file(modifiedAt = 1L)
        val changed = file(modifiedAt = 2L)
        cache.upsert(entry(old, quickHash = "old", fullHash = "old-full"))
        val bytes = ByteArray(100) { it.toByte() }
        val hasher = CachingDuplicateFileHasher(
            cache,
            StreamHashCalculator(HashInputSource { ByteArrayInputStream(bytes) }),
            currentTimeMillis = { 10L },
        )

        val newQuick = hasher.quickHash(changed)

        assertNotEquals("old", newQuick)
        assertEquals(1, cache.entries.size)
        assertEquals(2L, cache.entries.single().modifiedAt)
        assertEquals(null, cache.entries.single().fullHash)
    }

    private fun file(modifiedAt: Long) = DuplicateFile(
        itemId = "item",
        contentUri = "content://test/item",
        displayName = "item.bin",
        mimeType = null,
        relativePath = null,
        sizeBytes = 100L,
        modifiedAtEpochMillis = modifiedAt,
        source = ScanSource.MEDIA_STORE,
        riskLevel = RiskLevel.REVIEW,
    )

    private fun entry(file: DuplicateFile, quickHash: String?, fullHash: String?) = HashCacheEntry(
        id = "entry",
        contentUri = file.contentUri,
        sizeBytes = file.sizeBytes,
        modifiedAt = file.modifiedAtEpochMillis,
        quickHash = quickHash,
        fullHash = fullHash,
        updatedAt = 1L,
    )
}

private class MemoryHashCacheRepository : HashCacheRepository {
    val entries = mutableListOf<HashCacheEntry>()

    override suspend fun upsert(entry: HashCacheEntry) {
        entries.removeAll { it.id == entry.id || fileVersionMatches(it, entry.contentUri, entry.sizeBytes, entry.modifiedAt) }
        entries += entry
    }

    override suspend fun findCurrent(contentUri: String, sizeBytes: Long, modifiedAt: Long): HashCacheEntry? =
        entries.firstOrNull { fileVersionMatches(it, contentUri, sizeBytes, modifiedAt) }

    override suspend fun invalidateStale(contentUri: String, sizeBytes: Long, modifiedAt: Long): Int {
        val before = entries.size
        entries.removeAll {
            it.contentUri == contentUri && (it.sizeBytes != sizeBytes || it.modifiedAt != modifiedAt)
        }
        return before - entries.size
    }

    private fun fileVersionMatches(
        entry: HashCacheEntry,
        contentUri: String,
        sizeBytes: Long,
        modifiedAt: Long,
    ) = entry.contentUri == contentUri && entry.sizeBytes == sizeBytes && entry.modifiedAt == modifiedAt
}
