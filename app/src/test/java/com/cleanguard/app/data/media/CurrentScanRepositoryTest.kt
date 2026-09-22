package com.cleanguard.app.data.media

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.SafetyDecision
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.toScannedFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentScanRepositoryTest {
    @Test
    fun `stored real scan items produce risk and large file summary`() = runTest {
        val repository = CurrentScanRepository()
        val media = mediaItem()
        val analyzed = scanItem()

        repository.replaceScanSession(
            scannedFiles = listOf(media.toScannedFile()),
            analyzedItems = listOf(analyzed),
            threshold = LargeFileThreshold.MB_100,
        )

        val summary = repository.scanSummary.first()
        assertEquals(1, summary.totalFiles)
        assertEquals(analyzed.sizeBytes, summary.totalBytes)
        assertEquals(analyzed.sizeBytes, summary.largeFileBytes)
        assertEquals(analyzed.sizeBytes, summary.reviewBytes)
        assertTrue(repository.hasCompletedScan.first())
    }
}

private fun mediaItem() = MediaStoreItem(
    id = "media:video:1",
    mediaStoreId = 1L,
    displayName = "movie.mp4",
    mimeType = "video/mp4",
    sizeBytes = LargeFileThreshold.MB_100.bytes,
    modifiedAtEpochMillis = 1_000L,
    relativePath = "Movies/",
    category = FileCategory.VIDEO,
)

private fun scanItem() = ScanItem(
    id = "media:video:1",
    displayName = "movie.mp4",
    sizeBytes = LargeFileThreshold.MB_100.bytes,
    modifiedAtEpochMillis = 1_000L,
    source = ScanSource.MEDIA_STORE,
    category = FileCategory.LARGE_FILE,
    mimeType = "video/mp4",
    relativePath = "Movies/",
    safetyDecision = SafetyDecision(
        riskLevel = RiskLevel.REVIEW,
        reason = "大文件需要确认",
        deletionImpact = "处理前需要确认",
        allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
    ),
)
