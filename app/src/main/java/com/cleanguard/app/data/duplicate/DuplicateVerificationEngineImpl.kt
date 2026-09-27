package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.duplicate.DuplicateFileHasher
import com.cleanguard.app.domain.duplicate.DuplicateGroup
import com.cleanguard.app.domain.duplicate.DuplicateVerificationEngine
import com.cleanguard.app.domain.duplicate.DuplicateVerificationIssue
import com.cleanguard.app.domain.duplicate.DuplicateVerificationResult
import com.cleanguard.app.domain.duplicate.DuplicateVerificationStatus
import com.cleanguard.app.domain.model.RiskLevel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DuplicateVerificationEngineImpl(
    private val fileHasher: DuplicateFileHasher,
    fullHashParallelism: Int = MAX_FULL_HASH_PARALLELISM,
) : DuplicateVerificationEngine {
    private val fullHashSemaphore = Semaphore(fullHashParallelism.coerceIn(1, MAX_FULL_HASH_PARALLELISM))

    override suspend fun verify(files: List<DuplicateFile>): DuplicateVerificationResult {
        val issues = mutableListOf<DuplicateVerificationIssue>()
        val quickGroups = files.asSequence()
            .filter { it.sizeBytes > 0L && it.riskLevel == RiskLevel.REVIEW }
            .groupBy(DuplicateFile::sizeBytes)
            .values
            .filter { it.size >= 2 }
            .flatMap { sameSizeFiles ->
                sameSizeFiles.mapNotNull { file ->
                    runHash(file, issues) { fileHasher.quickHash(file) }?.let { it to file }
                }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
                    .filterValues { it.size >= 2 }
                    .map { (quickHash, groupedFiles) -> quickHash to groupedFiles }
            }

        val confirmedGroups = quickGroups.flatMap { (quickHash, groupedFiles) ->
            val fullHashes = coroutineScope {
                groupedFiles.map { file ->
                    async {
                        fullHashSemaphore.withPermit {
                            runHash(file, issues) { fileHasher.fullHash(file) }?.let { it to file }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            fullHashes.groupBy(keySelector = { it.first }, valueTransform = { it.second })
                .filterValues { it.size >= 2 }
                .map { (fullHash, filesWithSameContent) ->
                    val sortedFiles = filesWithSameContent.sortedWith(
                        compareByDescending<DuplicateFile> { it.modifiedAtEpochMillis }
                            .thenBy(DuplicateFile::itemId),
                    )
                    val totalBytes = saturatingMultiply(sortedFiles.first().sizeBytes, sortedFiles.size)
                    DuplicateGroup(
                        id = "duplicate:$fullHash",
                        files = sortedFiles,
                        totalBytes = totalBytes,
                        reclaimableBytes = saturatingMultiply(
                            sortedFiles.first().sizeBytes,
                            sortedFiles.size - 1,
                        ),
                        verificationStatus = DuplicateVerificationStatus.CONFIRMED_DUPLICATE,
                        quickHash = quickHash,
                        fullHash = fullHash,
                    )
                }
        }.sortedWith(compareByDescending<DuplicateGroup>(DuplicateGroup::reclaimableBytes).thenBy { it.id })

        return DuplicateVerificationResult(
            groups = confirmedGroups,
            issues = issues.distinctBy { it.itemId },
        )
    }

    private suspend fun runHash(
        file: DuplicateFile,
        issues: MutableList<DuplicateVerificationIssue>,
        block: suspend () -> String,
    ): String? = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (error !is IOException && error !is SecurityException && error !is IllegalArgumentException) {
            throw error
        }
        synchronized(issues) {
            issues += DuplicateVerificationIssue(
                itemId = file.itemId,
                message = "文件无法读取，已跳过且未中断其他重复项确认。",
            )
        }
        null
    }

    companion object {
        const val MAX_FULL_HASH_PARALLELISM = 2
    }
}

private fun saturatingMultiply(value: Long, multiplier: Int): Long {
    if (value <= 0L || multiplier <= 0) return 0L
    return if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier
}
