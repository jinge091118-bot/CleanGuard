package com.cleanguard.app.data.media

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cleanguard.app.data.local.CleanGuardDatabase
import com.cleanguard.app.data.local.repository.RoomIncrementalScanPersistenceRepository
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3 physical-device scan performance measurement.
 *
 * This test drives the production scanner directly instead of going through
 * the Macrobenchmark harness. It exists because the R3 gate needs scan timing
 * and memory numbers for a given dataset tier, and the measurement must be
 * taken on a device whose identity has been confirmed as physical hardware.
 *
 * The test does not create, modify, or delete any media. It only reads the
 * MediaStore index that is already present on the device and reports the
 * measured numbers. The caller is responsible for generating the dataset and
 * for recording the actual MediaStore row count before running this test.
 *
 * Results are written to the instrumentation output as a single tagged line so
 * the reviewed numbers can be transcribed into `PERFORMANCE-REPORT.md`.
 */
@RunWith(AndroidJUnit4::class)
class R3ScanPerformanceTest {

    @Test
    fun measureFullAndIncrementalScanPerformance() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(
                context,
                CleanGuardDatabase::class.java,
            ).build()
            try {
            val gateway = AndroidMediaStoreGateway(
                context = context.applicationContext,
                contentResolver = context.contentResolver,
            )
            val scanner = IncrementalMediaStoreScanner(
                sdkInt = Build.VERSION.SDK_INT,
                gateway = gateway,
                persistence = RoomIncrementalScanPersistenceRepository(database),
                legacyScanner = MediaStoreScanner(context.contentResolver, Dispatchers.IO),
                ioDispatcher = Dispatchers.IO,
            )

            // The very first scan of a fresh database has no generation
            // baseline, so it is the measured full scan. The second scan
            // against that baseline is the measured no-change incremental
            // scan. Both are measured; the warm-up is the first scan itself.
            val baselineMemory = memorySnapshotKb()
            val fullResult = measureScan(scanner, label = "full")
            val fullMemory = memorySnapshotKb()

            val incrementalResult = measureScan(scanner, label = "incremental")
            val incrementalMemory = memorySnapshotKb()

            val report = buildString {
                appendLine("R3_SCAN_PERF start")
                appendLine("device_model=${Build.MODEL}")
                appendLine("device_brand=${Build.BRAND}")
                appendLine("device_manufacturer=${Build.MANUFACTURER}")
                appendLine("android_release=${Build.VERSION.RELEASE}")
                appendLine("api_level=${Build.VERSION.SDK_INT}")
                appendLine("fingerprint=${Build.FINGERPRINT}")
                appendLine("media_rows_indexed=${fullResult.items.size}")
                appendLine("scan_1_execution_type=${fullResult.metadata.executionType}")
                appendLine("scan_1_duration_ms=${fullResult.metadata.durationMs}")
                appendLine("scan_1_changed=${fullResult.metadata.changedItemCount}")
                appendLine("scan_1_removed=${fullResult.metadata.removedItemCount}")
                appendLine("scan_1_volumes=${fullResult.metadata.scannedVolumeCount}")
                appendLine("scan_1_error_count=${fullResult.errors.size}")
                appendLine("scan_2_execution_type=${incrementalResult.metadata.executionType}")
                appendLine("scan_2_duration_ms=${incrementalResult.metadata.durationMs}")
                appendLine("scan_2_changed=${incrementalResult.metadata.changedItemCount}")
                appendLine("scan_2_removed=${incrementalResult.metadata.removedItemCount}")
                appendLine("scan_2_error_count=${incrementalResult.errors.size}")
                appendLine("heap_before_kb=$baselineMemory")
                appendLine("heap_after_scan_1_kb=$fullMemory")
                appendLine("heap_after_scan_2_kb=$incrementalMemory")
                appendLine("R3_SCAN_PERF end")
            }
            println(report)
            Log.i("R3ScanPerf", report)
            } finally {
                database.close()
            }
        }
    }

    private suspend fun measureScan(
        scanner: IncrementalMediaStoreScanner,
        label: String,
    ): MediaScanResult {
        val startedAt = System.nanoTime()
        val result = scanner.scan().filterIsInstance<MediaScanEvent.Completed>().single().result
        val wallClockMs = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue("The $label scan returned no items.", result.items.isNotEmpty())
        println(
            "R3_SCAN_WALL_CLOCK label=$label wall_clock_ms=$wallClockMs " +
                "metadata_duration_ms=${result.metadata.durationMs} " +
                "execution_type=${result.metadata.executionType}",
        )
        return result
    }

    private fun memorySnapshotKb(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        return used / 1024L + nativeHeap / 1024L
    }
}
