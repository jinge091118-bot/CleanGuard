package com.cleanguard.app.domain.model

data class ScanSummary(
    val totalFiles: Int,
    val totalBytes: Long,
    val largeFileBytes: Long,
    val safeBytes: Long,
    val reviewBytes: Long,
    val dangerBytes: Long,
    val unknownBytes: Long,
    val categoryBytes: Map<FileCategory, Long>,
) {
    val riskBytes: Map<RiskLevel, Long> = mapOf(
        RiskLevel.SAFE to safeBytes,
        RiskLevel.REVIEW to reviewBytes,
        RiskLevel.DANGER to dangerBytes,
        RiskLevel.UNKNOWN to unknownBytes,
    )

    companion object {
        val EMPTY = ScanSummary(
            totalFiles = 0,
            totalBytes = 0L,
            largeFileBytes = 0L,
            safeBytes = 0L,
            reviewBytes = 0L,
            dangerBytes = 0L,
            unknownBytes = 0L,
            categoryBytes = emptyMap(),
        )
    }
}

fun List<ScanItem>.toScanSummary(): ScanSummary {
    val riskBytes = RiskLevel.entries.associateWithTo(mutableMapOf()) { 0L }
    val categoryBytes = mutableMapOf<FileCategory, Long>()
    var totalBytes = 0L
    forEach { item ->
        totalBytes = saturatingAdd(totalBytes, item.sizeBytes)
        val risk = item.safetyDecision.riskLevel
        riskBytes[risk] = saturatingAdd(riskBytes.getValue(risk), item.sizeBytes)
        categoryBytes[item.category] = saturatingAdd(
            categoryBytes[item.category] ?: 0L,
            item.sizeBytes,
        )
    }
    return ScanSummary(
        totalFiles = size,
        totalBytes = totalBytes,
        largeFileBytes = categoryBytes[FileCategory.LARGE_FILE] ?: 0L,
        safeBytes = riskBytes.getValue(RiskLevel.SAFE),
        reviewBytes = riskBytes.getValue(RiskLevel.REVIEW),
        dangerBytes = riskBytes.getValue(RiskLevel.DANGER),
        unknownBytes = riskBytes.getValue(RiskLevel.UNKNOWN),
        categoryBytes = categoryBytes,
    )
}

private fun saturatingAdd(left: Long, right: Long): Long = when {
    right <= 0L -> left
    left > Long.MAX_VALUE - right -> Long.MAX_VALUE
    else -> left + right
}
