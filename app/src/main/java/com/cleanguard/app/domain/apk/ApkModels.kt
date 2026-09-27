package com.cleanguard.app.domain.apk

import com.cleanguard.app.domain.model.RiskLevel

data class ApkCandidate(
    val itemId: String,
    val contentUri: String,
    val displayName: String,
    val sizeBytes: Long,
    val modifiedAtEpochMillis: Long,
    val riskLevel: RiskLevel,
)

data class ApkArchiveMetadata(
    val packageName: String,
    val versionName: String?,
    val longVersionCode: Long,
    val applicationLabel: String?,
)

enum class ApkInstalledComparison {
    OLDER_THAN_INSTALLED,
    SAME_AS_INSTALLED,
    NEWER_THAN_INSTALLED,
    NOT_INSTALLED,
    UNKNOWN,
}

data class ApkAnalysis(
    val candidate: ApkCandidate,
    val metadata: ApkArchiveMetadata?,
    val installedComparison: ApkInstalledComparison,
    val installedVersionCode: Long? = null,
    val issueMessage: String? = null,
)

sealed interface InstalledPackageLookup {
    data class Installed(val longVersionCode: Long) : InstalledPackageLookup
    data object NotInstalled : InstalledPackageLookup
    data object Unknown : InstalledPackageLookup
}
