package com.cleanguard.app.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing for the scrolling screens.
 *
 * The plan asks for five scrolling iterations on each screen that holds a
 * list, so the home screen, the results screen, the scan history, and the
 * duplicate groups are each measured.
 *
 * The screens other than the home one are only reachable after a scan, and
 * the navigation to them is part of what a user does with a large dataset, so
 * the setup performs the scan and the navigation, and the measured block only
 * scrolls. The navigation happens outside the measurement window for exactly
 * that reason.
 */
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHome() {
        measureScrolling("CleanGuard home screen") { }
    }

    @Test
    fun scrollResults() {
        measureScrolling("CleanGuard results screen") {
            Navigation.openResults(it)
        }
    }

    @Test
    fun scrollHistory() {
        measureScrolling("CleanGuard scan history") {
            Navigation.openHistory(it)
        }
    }

    @Test
    fun scrollDuplicates() {
        measureScrolling("CleanGuard duplicate groups") {
            Navigation.openDuplicates(it)
        }
    }

    private fun measureScrolling(
        screen: String,
        prepare: (UiDevice) -> Unit,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = BenchmarkConfig.compilationMode,
            startupMode = StartupMode.WARM,
            iterations = BenchmarkConfig.uiIterations,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                check(device.wait(Until.hasObject(By.text("CleanGuard")), UI_TIMEOUT_MS)) {
                    "$screen did not become ready."
                }
                prepare(device)
            },
        ) {
            scrollUpAndDown(device)
        }
    }

    private fun scrollUpAndDown(device: UiDevice) {
        val centerX = device.displayWidth / 2
        val upperY = (device.displayHeight * 0.2f).toInt()
        val lowerY = (device.displayHeight * 0.8f).toInt()
        repeat(4) {
            device.swipe(centerX, lowerY, centerX, upperY, 20)
            device.waitForIdle()
            device.swipe(centerX, upperY, centerX, lowerY, 20)
            device.waitForIdle()
        }
    }
}
