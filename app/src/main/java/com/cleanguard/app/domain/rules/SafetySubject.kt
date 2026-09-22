package com.cleanguard.app.domain.rules

import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.ScanSource

data class SafetySubject(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val relativePath: String?,
    val source: ScanSource,
    val category: FileCategory,
    val isAppOwnedRebuildable: Boolean = false,
)
