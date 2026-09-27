package com.cleanguard.app.domain.download

import com.cleanguard.app.domain.model.ScannedFile

enum class DownloadAgeBucket {
    RECENT_30_DAYS,
    DAYS_30_TO_90,
    DAYS_90_TO_180,
    OVER_180_DAYS,
    UNKNOWN,
}

data class DownloadAgeInsight(
    val bucket: DownloadAgeBucket,
    val files: List<ScannedFile>,
    val fileCount: Int,
    val totalBytes: Long,
    val largestFile: ScannedFile?,
    val fileTypes: Set<String>,
)

interface DownloadInsightsEngine {
    fun analyze(files: List<ScannedFile>, nowEpochMillis: Long = System.currentTimeMillis()):
        List<DownloadAgeInsight>
}
