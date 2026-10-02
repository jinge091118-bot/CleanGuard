package com.cleanguard.app.feature.home

import com.cleanguard.app.domain.model.persistence.AppStorageTotalSnapshot
import com.cleanguard.app.domain.model.persistence.PersistedMediaAccessState
import com.cleanguard.app.domain.model.persistence.PersistedScanStatus
import com.cleanguard.app.domain.model.persistence.ScanHistoryTimeline
import com.cleanguard.app.domain.model.persistence.ScanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeHistoryDeltaTest {
    @Test
    fun twoSuccessfulScans_buildStorageVideoAndAppDeltas() {
        val timeline = ScanHistoryTimeline(
            successfulScans = listOf(
                snapshot("new", used = 160L, video = 70L),
                snapshot("old", used = 100L, video = 50L),
            ),
            appStorageTotals = listOf(
                AppStorageTotalSnapshot(200L, 90L),
                AppStorageTotalSnapshot(100L, 60L),
            ),
        )

        val delta = timeline.toHomeHistoryDelta()

        assertEquals(60L, delta?.usedBytes)
        assertEquals(20L, delta?.videoBytes)
        assertEquals(30L, delta?.appBytes)
    }

    @Test
    fun firstSuccessfulScan_isBaselineWithoutInventedDelta() {
        val delta = ScanHistoryTimeline(
            successfulScans = listOf(snapshot("only", 100L, 20L)),
            appStorageTotals = emptyList(),
        ).toHomeHistoryDelta()

        assertNull(delta?.usedBytes)
        assertNull(delta?.videoBytes)
        assertNull(delta?.appBytes)
    }

    private fun snapshot(id: String, used: Long, video: Long) = ScanSnapshot(
        scanId = id,
        startedAt = used,
        completedAt = used + 1L,
        durationMs = 1L,
        scanStatus = PersistedScanStatus.COMPLETED,
        usedBytes = used,
        freeBytes = 1L,
        totalBytes = used + 1L,
        imageBytes = 0L,
        videoBytes = video,
        audioBytes = 0L,
        downloadBytes = 0L,
        apkBytes = 0L,
        largeFileBytes = 0L,
        duplicateCandidateBytes = 0L,
        safeBytes = 0L,
        reviewBytes = 0L,
        dangerBytes = 0L,
        unknownBytes = 0L,
        scannedItemCount = 0,
        isIncremental = false,
        mediaAccessState = PersistedMediaAccessState.GRANTED,
    )
}
