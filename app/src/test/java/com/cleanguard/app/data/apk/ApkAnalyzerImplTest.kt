package com.cleanguard.app.data.apk

import com.cleanguard.app.domain.apk.ApkArchiveMetadata
import com.cleanguard.app.domain.apk.ApkCandidate
import com.cleanguard.app.domain.apk.ApkInstalledComparison
import com.cleanguard.app.domain.apk.InstalledPackageLookup
import com.cleanguard.app.domain.model.RiskLevel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkAnalyzerImplTest {
    @Test
    fun olderArchive_isComparedWithoutChangingReviewRisk() = runTest {
        val analyzer = analyzer(archiveVersion = 10L, lookup = InstalledPackageLookup.Installed(11L))

        val result = analyzer.analyze(candidate())

        assertEquals(ApkInstalledComparison.OLDER_THAN_INSTALLED, result.installedComparison)
        assertEquals(11L, result.installedVersionCode)
        assertEquals(RiskLevel.REVIEW, result.candidate.riskLevel)
    }

    @Test
    fun sameAndNewerArchives_haveExpectedComparisons() = runTest {
        val same = analyzer(10L, InstalledPackageLookup.Installed(10L)).analyze(candidate("same"))
        val newer = analyzer(11L, InstalledPackageLookup.Installed(10L)).analyze(candidate("newer"))

        assertEquals(ApkInstalledComparison.SAME_AS_INSTALLED, same.installedComparison)
        assertEquals(ApkInstalledComparison.NEWER_THAN_INSTALLED, newer.installedComparison)
    }

    @Test
    fun explicitNotInstalled_isReported() = runTest {
        val result = analyzer(10L, InstalledPackageLookup.NotInstalled).analyze(candidate())

        assertEquals(ApkInstalledComparison.NOT_INSTALLED, result.installedComparison)
    }

    @Test
    fun packageVisibilityUnknown_isNeverClaimedAsNotInstalled() = runTest {
        val result = analyzer(10L, InstalledPackageLookup.Unknown).analyze(candidate())

        assertEquals(ApkInstalledComparison.UNKNOWN, result.installedComparison)
        assertEquals("受 Android 包可见性限制，无法确认是否已安装。", result.issueMessage)
    }

    @Test
    fun invalidArchive_returnsUnknownWithoutMetadata() = runTest {
        val analyzer = ApkAnalyzerImpl(
            archiveInspector = { null },
            installedVersionProvider = { InstalledPackageLookup.Installed(1L) },
        )

        val result = analyzer.analyze(candidate())

        assertEquals(ApkInstalledComparison.UNKNOWN, result.installedComparison)
        assertNull(result.metadata)
    }

    @Test
    fun ioFailure_isContainedButCancellationIsRethrown() = runTest {
        val ioAnalyzer = ApkAnalyzerImpl(
            archiveInspector = { throw IOException("broken") },
            installedVersionProvider = { InstalledPackageLookup.Unknown },
        )
        val cancelledAnalyzer = ApkAnalyzerImpl(
            archiveInspector = { throw CancellationException("stop") },
            installedVersionProvider = { InstalledPackageLookup.Unknown },
        )

        assertEquals(
            ApkInstalledComparison.UNKNOWN,
            ioAnalyzer.analyze(candidate()).installedComparison,
        )
        var cancellationRethrown = false
        try {
            cancelledAnalyzer.analyze(candidate())
        } catch (_: CancellationException) {
            cancellationRethrown = true
        }
        assertTrue(cancellationRethrown)
    }

    private fun analyzer(archiveVersion: Long, lookup: InstalledPackageLookup) = ApkAnalyzerImpl(
        archiveInspector = {
            ApkArchiveMetadata(
                packageName = "com.example.app",
                versionName = "1.0",
                longVersionCode = archiveVersion,
                applicationLabel = "Example",
            )
        },
        installedVersionProvider = { lookup },
    )

    private fun candidate(id: String = "apk") = ApkCandidate(
        itemId = id,
        contentUri = "content://downloads/$id",
        displayName = "$id.apk",
        sizeBytes = 100L,
        modifiedAtEpochMillis = 1L,
        riskLevel = RiskLevel.REVIEW,
    )
}
