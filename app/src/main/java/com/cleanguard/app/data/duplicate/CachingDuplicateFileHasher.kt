package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.duplicate.DuplicateFileHasher
import com.cleanguard.app.domain.model.persistence.HashCacheEntry
import com.cleanguard.app.domain.repository.HashCacheRepository

class CachingDuplicateFileHasher(
    private val cacheRepository: HashCacheRepository,
    private val calculator: StreamHashCalculator,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : DuplicateFileHasher {
    override suspend fun quickHash(file: DuplicateFile): String {
        val cached = currentEntry(file)
        cached?.quickHash?.let { return it }
        val hash = calculator.quickHash(file)
        cacheRepository.upsert(
            cached.orNew(file).copy(
                quickHash = hash,
                updatedAt = currentTimeMillis(),
            ),
        )
        return hash
    }

    override suspend fun fullHash(file: DuplicateFile): String {
        val cached = currentEntry(file)
        cached?.fullHash?.let { return it }
        val hash = calculator.fullHash(file)
        cacheRepository.upsert(
            cached.orNew(file).copy(
                fullHash = hash,
                updatedAt = currentTimeMillis(),
            ),
        )
        return hash
    }

    private suspend fun currentEntry(file: DuplicateFile): HashCacheEntry? {
        cacheRepository.invalidateStale(
            contentUri = file.contentUri,
            sizeBytes = file.sizeBytes,
            modifiedAt = file.modifiedAtEpochMillis,
        )
        return cacheRepository.findCurrent(
            contentUri = file.contentUri,
            sizeBytes = file.sizeBytes,
            modifiedAt = file.modifiedAtEpochMillis,
        )
    }

    private fun HashCacheEntry?.orNew(file: DuplicateFile): HashCacheEntry = this ?: HashCacheEntry(
        id = "${file.contentUri}#${file.sizeBytes}#${file.modifiedAtEpochMillis}",
        contentUri = file.contentUri,
        sizeBytes = file.sizeBytes,
        modifiedAt = file.modifiedAtEpochMillis,
        quickHash = null,
        fullHash = null,
        updatedAt = currentTimeMillis(),
    )
}
