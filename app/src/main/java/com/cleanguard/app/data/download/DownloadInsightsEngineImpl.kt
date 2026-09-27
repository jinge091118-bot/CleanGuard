package com.cleanguard.app.data.download

import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.download.DownloadAgeInsight
import com.cleanguard.app.domain.download.DownloadInsightsEngine
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanSource

class DownloadInsightsEngineImpl : DownloadInsightsEngine {
    override fun analyze(files: List<ScannedFile>, nowEpochMillis: Long): List<DownloadAgeInsight> {
        val grouped = files.asSequence()
            .filter { it.source == ScanSource.SAF }
            .groupBy { it.ageBucket(nowEpochMillis) }
        return DownloadAgeBucket.entries.map { bucket ->
            val bucketFiles = grouped[bucket].orEmpty().sortedWith(
                compareByDescending<ScannedFile> { it.sizeBytes }.thenBy { it.id },
            )
            DownloadAgeInsight(
                bucket = bucket,
                files = bucketFiles,
                fileCount = bucketFiles.size,
                totalBytes = bucketFiles.fold(0L) { total, file -> saturatingAdd(total, file.sizeBytes) },
                largestFile = bucketFiles.maxByOrNull { it.sizeBytes },
                fileTypes = bucketFiles.mapTo(sortedSetOf()) { file -> file.typeLabel() },
            )
        }
    }
}

private fun ScannedFile.ageBucket(now: Long): DownloadAgeBucket {
    if (modifiedAtEpochMillis <= 0L) return DownloadAgeBucket.UNKNOWN
    val age = (now - modifiedAtEpochMillis).coerceAtLeast(0L)
    return when {
        age < DAYS_30 -> DownloadAgeBucket.RECENT_30_DAYS
        age < DAYS_90 -> DownloadAgeBucket.DAYS_30_TO_90
        age < DAYS_180 -> DownloadAgeBucket.DAYS_90_TO_180
        else -> DownloadAgeBucket.OVER_180_DAYS
    }
}

private fun ScannedFile.typeLabel(): String = mimeType
    ?.substringBefore('/')
    ?.takeIf { it.isNotBlank() }
    ?: category.name.lowercase()

private fun saturatingAdd(total: Long, value: Long): Long =
    if (value > 0L && total > Long.MAX_VALUE - value) Long.MAX_VALUE else total + value

private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
private const val DAYS_30 = 30L * DAY_MILLIS
private const val DAYS_90 = 90L * DAY_MILLIS
private const val DAYS_180 = 180L * DAY_MILLIS
