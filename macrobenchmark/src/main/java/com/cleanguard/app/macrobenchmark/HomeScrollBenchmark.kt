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

@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHome() {
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
                    "CleanGuard home screen did not become ready."
                }
            },
        ) {
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
}
