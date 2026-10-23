package com.cleanguard.app.macrobenchmark

import android.os.Bundle
import androidx.benchmark.macro.CompilationMode
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Iteration counts are configurable so a harness-validation run can use a
 * reduced iteration count on a constrained device. The defaults are the R3
 * acceptance counts from `R3-PERFORMANCE-TEST-PLAN.md`.
 */
internal object BenchmarkConfig {

    private val arguments: Bundle = InstrumentationRegistry.getArguments()

    val startupIterations: Int = intArgument("cleanguardIterationsStartup", STARTUP_ITERATIONS)

    val uiIterations: Int = intArgument("cleanguardIterationsUi", UI_ITERATIONS)

    val fullScanIterations: Int = intArgument("cleanguardIterationsFullScan", FULL_SCAN_ITERATIONS)

    val incrementalScanIterations: Int =
        intArgument("cleanguardIterationsIncrementalScan", INCREMENTAL_SCAN_ITERATIONS)

    /**
     * `Partial` compilation is the R3 default. A harness-validation run on a
     * device that cannot resolve a baseline profile may pass
     * `-Pandroid.testInstrumentationRunnerArguments.cleanguardCompilation=none`
     * to skip cloud profile resolution. A `none` result is never accepted as
     * R3 product performance evidence.
     */
    val compilationMode: CompilationMode = when (
        arguments.getString("cleanguardCompilation")?.lowercase()
    ) {
        "none" -> CompilationMode.None()
        "full" -> CompilationMode.Full()
        else -> CompilationMode.Partial()
    }

    private fun intArgument(key: String, default: Int): Int =
        arguments.getString(key)?.toIntOrNull()?.takeIf { it > 0 } ?: default
}
