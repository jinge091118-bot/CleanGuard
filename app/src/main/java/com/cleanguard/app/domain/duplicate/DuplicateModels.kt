package com.cleanguard.app.domain.duplicate

import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource

data class DuplicateFile(
    val itemId: String,
    val contentUri: String,
    val displayName: String,
    val mimeType: String?,
    val relativePath: String?,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val source: ScanSource,
    val riskLevel: RiskLevel,
)

enum class DuplicateVerificationStatus {
    CANDIDATE,
    CONFIRMED_DUPLICATE,
}

data class DuplicateGroup(
    val id: String,
    val files: List<DuplicateFile>,
    val totalBytes: Long,
    val reclaimableBytes: Long,
    val verificationStatus: DuplicateVerificationStatus,
    val quickHash: String,
    val fullHash: String?,
)

data class DuplicateVerificationIssue(
    val itemId: String,
    val message: String,
)

data class DuplicateVerificationResult(
    val groups: List<DuplicateGroup>,
    val issues: List<DuplicateVerificationIssue> = emptyList(),
)
