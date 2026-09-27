package com.cleanguard.app.data.apk

import com.cleanguard.app.domain.apk.ApkAnalysis
import com.cleanguard.app.domain.apk.ApkAnalyzer
import com.cleanguard.app.domain.apk.ApkArchiveInspector
import com.cleanguard.app.domain.apk.ApkCandidate
import com.cleanguard.app.domain.apk.ApkInstalledComparison
import com.cleanguard.app.domain.apk.InstalledPackageLookup
import com.cleanguard.app.domain.apk.InstalledPackageVersionProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException

class ApkAnalyzerImpl(
    private val archiveInspector: ApkArchiveInspector,
    private val installedVersionProvider: InstalledPackageVersionProvider,
) : ApkAnalyzer {
    override suspend fun analyze(candidate: ApkCandidate): ApkAnalysis = try {
        val metadata = archiveInspector.inspect(candidate.contentUri)
            ?: return ApkAnalysis(
                candidate = candidate,
                metadata = null,
                installedComparison = ApkInstalledComparison.UNKNOWN,
                issueMessage = "无法读取 APK 元数据。",
            )
        when (val installed = installedVersionProvider.lookup(metadata.packageName)) {
            is InstalledPackageLookup.Installed -> ApkAnalysis(
                candidate = candidate,
                metadata = metadata,
                installedComparison = when {
                    metadata.longVersionCode < installed.longVersionCode ->
                        ApkInstalledComparison.OLDER_THAN_INSTALLED
                    metadata.longVersionCode == installed.longVersionCode ->
                        ApkInstalledComparison.SAME_AS_INSTALLED
                    else -> ApkInstalledComparison.NEWER_THAN_INSTALLED
                },
                installedVersionCode = installed.longVersionCode,
            )
            InstalledPackageLookup.NotInstalled -> ApkAnalysis(
                candidate = candidate,
                metadata = metadata,
                installedComparison = ApkInstalledComparison.NOT_INSTALLED,
            )
            InstalledPackageLookup.Unknown -> ApkAnalysis(
                candidate = candidate,
                metadata = metadata,
                installedComparison = ApkInstalledComparison.UNKNOWN,
                issueMessage = "受 Android 包可见性限制，无法确认是否已安装。",
            )
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        if (error !is IOException && error !is SecurityException && error !is IllegalArgumentException) {
            throw error
        }
        ApkAnalysis(
            candidate = candidate,
            metadata = null,
            installedComparison = ApkInstalledComparison.UNKNOWN,
            issueMessage = "APK 暂时无法读取，已安全跳过。",
        )
    }
}
