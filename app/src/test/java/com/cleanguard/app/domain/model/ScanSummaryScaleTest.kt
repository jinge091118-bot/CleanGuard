package com.cleanguard.app.domain.model

import kotlin.system.measureTimeMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSummaryScaleTest {
    @Test
    fun summaryHandlesTenFiftyAndOneHundredThousandItemsInLinearPass() {
        val items = List(100_000) { index ->
            ScanItem(
                id = "scale-$index",
                displayName = "item-$index.bin",
                sizeBytes = 1_024L,
                modifiedAtEpochMillis = index.toLong(),
                source = ScanSource.MEDIA_STORE,
                category = if (index % 10 == 0) FileCategory.LARGE_FILE else FileCategory.OTHER,
                safetyDecision = SafetyDecision(
                    riskLevel = RiskLevel.REVIEW,
                    reason = "scale test",
                    deletionImpact = "manual review",
                ),
            )
        }
        val timings = linkedMapOf<Int, Long>()

        listOf(10_000, 50_000, 100_000).forEach { itemCount ->
            lateinit var summary: ScanSummary
            timings[itemCount] = measureTimeMillis {
                summary = items.subList(0, itemCount).toScanSummary()
            }
            assertEquals(itemCount, summary.totalFiles)
            assertEquals(itemCount * 1_024L, summary.totalBytes)
            assertEquals(itemCount * 1_024L, summary.reviewBytes)
        }

        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        println("PHASE10_SUMMARY_TIMINGS_MS=$timings usedHeapBytes=$usedHeap maxHeapBytes=${runtime.maxMemory()}")
        assertTrue(timings.getValue(100_000) < 30_000L)
        assertTrue(usedHeap < runtime.maxMemory())
    }
}
