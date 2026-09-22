package com.cleanguard.app.domain.model

data class ScanItem(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val source: ScanSource,
    val category: FileCategory,
    val mimeType: String? = null,
    val relativePath: String? = null,
    val safetyDecision: SafetyDecision,
)

