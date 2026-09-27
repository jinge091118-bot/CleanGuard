package com.cleanguard.app.domain.apk

interface ApkAnalyzer {
    suspend fun analyze(candidate: ApkCandidate): ApkAnalysis
}

fun interface ApkArchiveInspector {
    suspend fun inspect(contentUri: String): ApkArchiveMetadata?
}

fun interface InstalledPackageVersionProvider {
    fun lookup(packageName: String): InstalledPackageLookup
}

object NoOpApkAnalyzer : ApkAnalyzer {
    override suspend fun analyze(candidate: ApkCandidate) = ApkAnalysis(
        candidate = candidate,
        metadata = null,
        installedComparison = ApkInstalledComparison.UNKNOWN,
        issueMessage = "APK analyzer is not configured",
    )
}
