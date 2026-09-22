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
    val riskBytes = RiskLevel.entries.associateWith { risk ->
        filter { it.safetyDecision.riskLevel == risk }.sumOf(ScanItem::sizeBytes)
    }
    val categoryBytes = groupBy(ScanItem::category)
        .mapValues { (_, items) -> items.sumOf(ScanItem::sizeBytes) }
    return ScanSummary(
        totalFiles = size,
        totalBytes = sumOf(ScanItem::sizeBytes),
        largeFileBytes = categoryBytes[FileCategory.LARGE_FILE] ?: 0L,
        safeBytes = riskBytes.getValue(RiskLevel.SAFE),
        reviewBytes = riskBytes.getValue(RiskLevel.REVIEW),
        dangerBytes = riskBytes.getValue(RiskLevel.DANGER),
        unknownBytes = riskBytes.getValue(RiskLevel.UNKNOWN),
        categoryBytes = categoryBytes,
    )
}
