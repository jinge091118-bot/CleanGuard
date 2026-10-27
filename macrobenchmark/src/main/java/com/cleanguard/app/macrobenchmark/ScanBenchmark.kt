package com.cleanguard.app.macrobenchmark

import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMetricApi::class)
class ScanBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun firstFullScan() {
        val device = device()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = scanMetrics(),
            compilationMode = BenchmarkConfig.compilationMode,
            iterations = BenchmarkConfig.fullScanIterations,
            setupBlock = {
                clearTargetDataAndGrantMediaAccess(device)
                startActivityAndWait()
                waitForHome(device)
            },
        ) {
            startScanAndWait(device)
        }
    }

    @Test
    fun noChangeIncrementalScan() {
        val device = device()
        prepareFullScanBaseline(device)
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = scanMetrics(),
            compilationMode = BenchmarkConfig.compilationMode,
            iterations = BenchmarkConfig.incrementalScanIterations,
            setupBlock = {
                killProcess()
                startActivityAndWait()
                waitForHome(device)
            },
        ) {
            startScanAndWait(device)
        }
    }

    private fun prepareFullScanBaseline(device: UiDevice) {
        clearTargetDataAndGrantMediaAccess(device)
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/.MainActivity")
        waitForHome(device)
        startScanAndWait(device)
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
    }

    private fun clearTargetDataAndGrantMediaAccess(device: UiDevice) {
        check(device.executeShellCommand("pm clear $TARGET_PACKAGE").contains("Success")) {
            "Could not clear CleanGuard app data before the full-scan benchmark."
        }
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
            )
        } else {
            listOf("android.permission.READ_EXTERNAL_STORAGE")
        }
        permissions.forEach { permission ->
            device.executeShellCommand("pm grant $TARGET_PACKAGE $permission")
        }
    }

    private fun waitForHome(device: UiDevice) {
        check(device.wait(Until.hasObject(By.text("CleanGuard")), UI_TIMEOUT_MS)) {
            "CleanGuard home screen did not become ready."
        }
    }

    private fun startScanAndWait(device: UiDevice) {
        val button = Navigation.find(device, Navigation.SCAN_BUTTON_LABEL)
            ?: error("Could not find the scan button on the home screen.")
        val bounds = button.visibleBounds
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // The touch is injected at the coordinates of the label and repeated
        // until the screen changes. Both details matter on this device, and
        // the reasoning is recorded in the navigation helper.
        var attempts = 0
        var started = false
        while (attempts < MAX_SCAN_TAP_ATTEMPTS && !started) {
            attempts += 1
            device.click(centerX, centerY)
            device.waitForIdle()
            Thread.sleep(1200)
            started = device.hasObject(By.text(Navigation.SCAN_FINISHED_LABEL)) ||
                device.hasObject(By.text(Navigation.SCAN_STARTED_LABEL))
        }
        println("SCAN_TAP_ATTEMPTS=$attempts")

        // Give the tap a moment to land before the screen is inspected, so a
        // screen that is still the home page means the tap did not reach the
        // button rather than that the inspection was too early.
        val afterTap = Navigation.visibleLabels(device).joinToString(" | ")
        val focus = focusedWindow(device)
        captureScreen(device, "after-tap")

        // The scan itself takes well under a second on this dataset, so a wait
        // that runs for minutes means the click did not start a scan. The
        // screen contents are reported so the failure says which state the
        // app was actually in.
        val finished = device.wait(Until.hasObject(By.text(Navigation.SCAN_FINISHED_LABEL)), SCAN_TIMEOUT_MS)
        if (!finished) {
            val visible = Navigation.visibleLabels(device).joinToString(" | ")
            error(
                "CleanGuard scan did not finish within the R3 timeout. " +
                    "Tapped ($centerX, $centerY) within $bounds. " +
                    "Focused window: $focus. " +
                    "Text right after the tap: $afterTap. " +
                    "Text at timeout: $visible"
            )
        }
        println("SCAN_FINISHED")
    }

    /**
     * The window that currently holds the input focus. A shade or a system
     * dialog in front of the application swallows every touch, which looks
     * exactly like a button that cannot be clicked.
     */
    private fun focusedWindow(device: UiDevice): String =
        device.executeShellCommand(
            "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -2"
        ).trim().replace("\n", " ; ")

    /**
     * Write a screenshot into the shared download area, where the host can
     * collect it. The image shows what was on the display at the moment the
     * tap was issued, which settles whether the tap reached the application
     * or was swallowed by something in front of it.
     */
    private fun captureScreen(device: UiDevice, name: String) {
        val path = "/sdcard/Download/cleanguard-$name.png"
        device.executeShellCommand("screencap -p $path")
        println("SCREENSHOT_PATH=$path")
    }

    private fun scanMetrics() = listOf(
        TraceSectionMetric(SCAN_TRACE_SECTION, TraceSectionMetric.Mode.First),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}

private const val SCAN_TRACE_SECTION = "CleanGuardScan"

/**
 * The scan of the ten thousand row dataset completes in about a tenth of a
 * second, so a wait of a few minutes already separates a slow run from a
 * click that never started a scan. The earlier value of fifteen minutes made
 * a broken click look like a slow device.
 */
private const val SCAN_TIMEOUT_MS = 3 * 60 * 1_000L

/**
 * How many times the start button is pressed before the run is given up on.
 */
private const val MAX_SCAN_TAP_ATTEMPTS = 4
