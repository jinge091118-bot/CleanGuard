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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

class DuplicateVerificationEngineImpl(
    private val fileHasher: DuplicateFileHasher,
    fullHashParallelism: Int = MAX_FULL_HASH_PARALLELISM,
) : DuplicateVerificationEngine {
    private val fullHashParallelism = fullHashParallelism.coerceIn(1, MAX_FULL_HASH_PARALLELISM)

    override suspend fun verify(files: List<DuplicateFile>): DuplicateVerificationResult {
        val issues = mutableListOf<DuplicateVerificationIssue>()
        val sizeGroups = files.asSequence()
            .filter { it.sizeBytes > 0L && it.riskLevel == RiskLevel.REVIEW }
            .groupBy(DuplicateFile::sizeBytes)
            .values
            .filter { it.size >= 2 }
        val quickGroups = buildList {
            sizeGroups.forEach { sameSizeFiles ->
                val quickHashes = ArrayList<Pair<String, DuplicateFile>>(sameSizeFiles.size)
                sameSizeFiles.forEachIndexed { index, file ->
                    if (index % CANCELLATION_CHECK_INTERVAL == 0) yield()
                    runHash(file, issues) { fileHasher.quickHash(file) }
                        ?.let { quickHashes += it to file }
                }
                quickHashes.groupBy(keySelector = { it.first }, valueTransform = { it.second })
                    .filterValues { it.size >= 2 }
                    .forEach { (quickHash, groupedFiles) -> add(quickHash to groupedFiles) }
            }
        }

        val confirmedGroups = quickGroups.flatMap { (quickHash, groupedFiles) ->
            val fullHashes = hashFullWithBoundedWorkers(groupedFiles, issues)
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

    private suspend fun hashFullWithBoundedWorkers(
        files: List<DuplicateFile>,
        issues: MutableList<DuplicateVerificationIssue>,
    ): List<Pair<String, DuplicateFile>> = coroutineScope {
        val nextIndex = AtomicInteger(0)
        val results = Collections.synchronizedList(
            ArrayList<Pair<String, DuplicateFile>>(files.size),
        )
        List(minOf(fullHashParallelism, files.size)) {
            async {
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val index = nextIndex.getAndIncrement()
                    if (index >= files.size) break
                    if (index % CANCELLATION_CHECK_INTERVAL == 0) yield()
                    val file = files[index]
                    runHash(file, issues) { fileHasher.fullHash(file) }
                        ?.let { hash -> results += hash to file }
                }
            }
        }.awaitAll()
        results.toList()
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

private const val CANCELLATION_CHECK_INTERVAL = 64

private fun saturatingMultiply(value: Long, multiplier: Int): Long {
    if (value <= 0L || multiplier <= 0) return 0L
    return if (value > Long.MAX_VALUE / multiplier) Long.MAX_VALUE else value * multiplier
}
