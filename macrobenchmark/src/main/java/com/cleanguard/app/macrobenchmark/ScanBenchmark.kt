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
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

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
        val button = findStartScanButton(device)
        val bounds = button.visibleBounds
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // The label is a plain text node inside the Compose button, and it
        // reports itself as not clickable. An accessibility click on it is
        // delivered to the label, which does not handle it, so the tap never
        // reaches the button and the scan never starts. Injecting the touch at
        // the coordinates of the label reaches the button that contains it,
        // which is the same path a finger takes.
        device.executeShellCommand("input tap $centerX $centerY")

        // The scan itself takes well under a second on this dataset, so a wait
        // that runs for minutes means the click did not start a scan. The
        // screen contents are reported so the failure says which state the
        // app was actually in.
        val finished = device.wait(Until.hasObject(By.text(SCAN_FINISHED_LABEL)), SCAN_TIMEOUT_MS)
        if (!finished) {
            val visible = visibleLabels(device).joinToString(" | ")
            error(
                "CleanGuard scan did not finish within the R3 timeout. " +
                    "Tapped ($centerX, $centerY) within $bounds. " +
                    "Visible text: $visible"
            )
        }
        println("SCAN_FINISHED")
    }

    private fun visibleLabels(device: UiDevice): List<String> =
        device.findObjects(By.text(Pattern.compile(".+")))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .distinct()
            .take(24)

    private fun findStartScanButton(device: UiDevice): UiObject2 {
        repeat(MAX_HOME_SCROLLS) {
            device.findObject(By.text("开始安全体检"))
                ?.takeIf { it.visibleBounds.height() > 0 && it.visibleBounds.bottom > 0 }
                ?.let { return it }
            val centerX = device.displayWidth / 2
            device.swipe(
                centerX,
                (device.displayHeight * 0.8f).toInt(),
                centerX,
                (device.displayHeight * 0.2f).toInt(),
                20,
            )
            device.waitForIdle()
        }
        error("Could not find the Start Safety Check button on the home screen.")
    }

    private fun scanMetrics() = listOf(
        TraceSectionMetric(SCAN_TRACE_SECTION, TraceSectionMetric.Mode.First),
        MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
    )

    private fun device(): UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}

private const val SCAN_TRACE_SECTION = "CleanGuardScan"
private const val MAX_HOME_SCROLLS = 8

/**
 * The scan of the ten thousand row dataset completes in about a tenth of a
 * second, so a wait of a few minutes already separates a slow run from a
 * click that never started a scan. The earlier value of fifteen minutes made
 * a broken click look like a slow device.
 */
private const val SCAN_TIMEOUT_MS = 3 * 60 * 1_000L

/**
 * The label the scan screen shows once the local safety assessment has
 * finished. It is defined here rather than in the app module because the
 * benchmark module does not depend on the application resources.
 */
private const val SCAN_FINISHED_LABEL = "安全规则判断完成"
