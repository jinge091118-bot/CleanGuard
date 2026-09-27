package com.cleanguard.app.data.download

import com.cleanguard.app.domain.download.DownloadAgeBucket
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadInsightsEngineImplTest {
    private val engine = DownloadInsightsEngineImpl()

    @Test
    fun ageBoundaries_areGroupedAt30_90And180Days() {
        val insights = engine.analyze(
            listOf(
                file("recent", daysAgo = 29),
                file("30", daysAgo = 30),
                file("90", daysAgo = 90),
                file("180", daysAgo = 180),
            ),
            NOW,
        ).associateBy { it.bucket }

        assertEquals(listOf("recent"), insights.getValue(DownloadAgeBucket.RECENT_30_DAYS).files.map { it.id })
        assertEquals(listOf("30"), insights.getValue(DownloadAgeBucket.DAYS_30_TO_90).files.map { it.id })
        assertEquals(listOf("90"), insights.getValue(DownloadAgeBucket.DAYS_90_TO_180).files.map { it.id })
        assertEquals(listOf("180"), insights.getValue(DownloadAgeBucket.OVER_180_DAYS).files.map { it.id })
    }

    @Test
    fun onlySafFiles_areIncluded() {
        val insights = engine.analyze(
            listOf(
                file("saf", 200, source = ScanSource.SAF),
                file("media", 200, source = ScanSource.MEDIA_STORE),
            ),
            NOW,
        )

        assertEquals(1, insights.sumOf { it.fileCount })
        assertEquals("saf", insights.single { it.fileCount == 1 }.files.single().id)
    }

    @Test
    fun summaryIncludesCountSpaceLargestFileAndTypes() {
        val insight = engine.analyze(
            listOf(
                file("small", 200, size = 10L, mime = "image/png"),
                file("large", 200, size = 30L, mime = "application/pdf"),
            ),
            NOW,
        ).single { it.bucket == DownloadAgeBucket.OVER_180_DAYS }

        assertEquals(2, insight.fileCount)
        assertEquals(40L, insight.totalBytes)
        assertEquals("large", insight.largestFile?.id)
        assertEquals(setOf("application", "image"), insight.fileTypes)
    }

    @Test
    fun unknownTimestamp_isNotTreatedAsOld() {
        val insight = engine.analyze(
            listOf(file("unknown", 0).copy(modifiedAtEpochMillis = 0L)),
            NOW,
        ).single { it.bucket == DownloadAgeBucket.UNKNOWN }

        assertEquals(1, insight.fileCount)
        assertTrue(insight.files.any { it.id == "unknown" })
        assertNull(engine.analyze(emptyList(), NOW).single { it.bucket == DownloadAgeBucket.UNKNOWN }.largestFile)
    }

    private fun file(
        id: String,
        daysAgo: Int,
        size: Long = 1L,
        mime: String? = "application/octet-stream",
        source: ScanSource = ScanSource.SAF,
    ) = ScannedFile(
        id = id,
        displayName = "$id.bin",
        mimeType = mime,
        sizeBytes = size,
        modifiedAtEpochMillis = NOW - daysAgo * DAY,
        relativePath = "Download/",
        source = source,
        category = FileCategory.DOWNLOAD,
        contentUri = "content://downloads/$id",
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1_000L
        const val NOW = 2_000L * DAY
    }
}
