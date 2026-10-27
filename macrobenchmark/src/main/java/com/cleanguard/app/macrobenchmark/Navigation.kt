package com.cleanguard.app.macrobenchmark

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import java.util.regex.Pattern

/**
 * Shared navigation helpers for the benchmark suite.
 *
 * Two properties of this application on this ROM shape everything here.
 *
 * A Compose button exposes its label as a text node that reports itself as
 * not clickable, and the button around it reports the same, so an
 * accessibility click is delivered to a node that does not handle it. The
 * touch has to be injected at the coordinates of the label.
 *
 * The first touch after an activity settles is lost to the window transition.
 * The touch therefore has to be repeated until the screen shows that the
 * action took effect.
 */
internal object Navigation {

    const val SCAN_BUTTON_LABEL = "开始安全体检"
    const val SCAN_FINISHED_LABEL = "安全规则判断完成"
    const val SCAN_STARTED_LABEL = "正在准备媒体索引"
    const val RESULTS_ENTRY_LABEL = "查看体检结果"
    const val RESULTS_TITLE_LABEL = "空间线索"
    const val HISTORY_ENTRY_LABEL = "查看扫描历史"
    const val HISTORY_TITLE_LABEL = "扫描历史"
    const val DUPLICATES_ENTRY_LABEL = "核对重复文件"
    const val DUPLICATES_TITLE_LABEL = "重复文件"

    private const val MAX_SCROLLS = 8
    private const val MAX_TAP_ATTEMPTS = 5

    /**
     * Find a node by its text, scrolling down until it comes into view.
     */
    fun find(device: UiDevice, label: String): UiObject2? {
        device.findObject(By.text(label))
            ?.takeIf { it.visibleBounds.height() > 0 }
            ?.let { return it }

        val centerX = device.displayWidth / 2
        repeat(MAX_SCROLLS) {
            device.swipe(
                centerX,
                (device.displayHeight * 0.8f).toInt(),
                centerX,
                (device.displayHeight * 0.2f).toInt(),
                20,
            )
            device.waitForIdle()
            device.findObject(By.text(label))
                ?.takeIf { it.visibleBounds.height() > 0 }
                ?.let { return it }
        }
        return null
    }

    /**
     * Tap the node until the screen reports the expected state, then wait for
     * that state. The tap is repeated because the first one is lost to the
     * window transition.
     */
    fun tapUntil(
        device: UiDevice,
        target: UiObject2,
        condition: (UiDevice) -> Boolean,
    ) {
        val bounds = target.visibleBounds
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()
        var attempts = 0
        while (attempts < MAX_TAP_ATTEMPTS && !condition(device)) {
            attempts += 1
            device.click(centerX, centerY)
            device.waitForIdle()
            Thread.sleep(1000)
        }
        check(condition(device)) {
            "The screen did not open after $attempts taps. Visible text: ${visibleLabels(device).joinToString(" | ")}"
        }
    }

    /**
     * Run a scan and leave the application on the scan screen.
     */
    fun runScan(device: UiDevice) {
        val button = find(device, SCAN_BUTTON_LABEL)
            ?: error("Could not find the scan button on the home screen.")
        tapUntil(device, button) { device.hasObject(By.text(SCAN_FINISHED_LABEL)) }
    }

    /**
     * Run a scan and open the results screen.
     */
    fun openResults(device: UiDevice) {
        runScan(device)
        val entry = find(device, RESULTS_ENTRY_LABEL)
            ?: error("Could not find the results entry on the scan screen.")
        tapUntil(device, entry) { device.hasObject(By.text(RESULTS_TITLE_LABEL)) }
    }

    /**
     * Open the scan history screen.
     */
    fun openHistory(device: UiDevice) {
        val entry = find(device, HISTORY_ENTRY_LABEL)
            ?: error("Could not find the scan history entry on the home screen.")
        tapUntil(device, entry) { device.hasObject(By.text(HISTORY_TITLE_LABEL)) }
    }

    /**
     * Open the duplicate groups screen.
     */
    fun openDuplicates(device: UiDevice) {
        openResults(device)
        val entry = find(device, DUPLICATES_ENTRY_LABEL)
            ?: error("Could not find the duplicate groups entry on the results screen.")
        tapUntil(device, entry) { device.hasObject(By.text(DUPLICATES_TITLE_LABEL)) }
    }

    /**
     * Every text on the current screen, which is what a failure reports.
     */
    fun visibleLabels(device: UiDevice): List<String> =
        device.findObjects(By.text(Pattern.compile(".+")))
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .distinct()
            .take(24)
}
