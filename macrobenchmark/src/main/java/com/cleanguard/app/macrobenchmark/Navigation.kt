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
    const val RESULTS_TITLE_LABEL = "体检结果"

    /**
     * The headline the results screen shows once a scan has been analysed. It
     * is used together with the title bar because the headline stays on
     * screen while the title bar scrolls away with the content.
     */
    const val RESULTS_HEADLINE_LABEL = "分析完成"
    const val HISTORY_ENTRY_LABEL = "查看扫描历史"
    const val HISTORY_TITLE_LABEL = "扫描历史"
    const val DUPLICATES_ENTRY_LABEL = "核对重复文件"
    const val DUPLICATES_TITLE_LABEL = "重复文件"

    private const val MAX_SCROLLS = 8
    private const val MAX_TAP_ATTEMPTS = 5

    /**
     * How long the scan is given to finish once it has started.
     */
    private const val SCAN_WAIT_MS = 60_000L

    /**
     * How many times the screen is polled while waiting for the scan to start.
     */
    private const val SCAN_START_ATTEMPTS = 8

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
     * Grant the media permissions the scanner needs.
     *
     * The permission state matters because clearing the application data, which
     * the benchmark does before each series, also revokes the runtime
     * permissions. Without them the scan screen reports that it needs an
     * authorisation and no scan ever runs.
     */
    fun grantMediaAccess(device: UiDevice) {
        val permissions = if (android.os.Build.VERSION.SDK_INT >= 33) {
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
        device.executeShellCommand("appops set $TARGET_PACKAGE GET_USAGE_STATS allow")
    }

    /**
     * Run a scan and leave the application on the scan screen.
     *
     * The tap is not repeated blindly. Once the scan has started, the screen
     * changes and the coordinates that were taken on the home screen now sit
     * over a different control, so a second tap at the same place would open
     * the results screen instead. The wait therefore looks for the finished
     * label, and only taps again while nothing has happened yet.
     */
    fun runScan(device: UiDevice) {
        grantMediaAccess(device)
        val button = find(device, SCAN_BUTTON_LABEL)
            ?: error("Could not find the scan button on the home screen.")

        val bounds = button.visibleBounds
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        var attempts = 0
        var started = false
        while (attempts < MAX_TAP_ATTEMPTS && !started) {
            attempts += 1
            device.click(centerX, centerY)
            started = waitForScanToStart(device)
        }
        check(started) {
            "The scan did not start after $attempts taps. Visible text: ${visibleLabels(device).joinToString(" | ")}"
        }

        // The scan itself takes well under a second, but it is given time to
        // finish and the results screen counts as finished as well.
        val deadline = System.currentTimeMillis() + SCAN_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (device.hasObject(By.text(SCAN_FINISHED_LABEL)) || onResults(device)) {
                return
            }
            Thread.sleep(300)
        }
        error("The scan did not finish. Visible text: ${visibleLabels(device).joinToString(" | ")}")
    }

    /**
     * Whether the scan has begun. The scan screen shows a preparation label
     * while it runs and the finished label once it is done, and the results
     * screen means the scan was finished and the results were opened.
     */
    private fun waitForScanToStart(device: UiDevice): Boolean {
        repeat(SCAN_START_ATTEMPTS) {
            if (device.hasObject(By.text(SCAN_FINISHED_LABEL)) ||
                device.hasObject(By.text(SCAN_STARTED_LABEL)) ||
                onResults(device)
            ) {
                return true
            }
            Thread.sleep(400)
        }
        return false
    }

    /**
     * Open the results screen.
     */
    fun openResults(device: UiDevice) {
        runScan(device)
        if (onResults(device)) {
            return
        }
        val entry = find(device, RESULTS_ENTRY_LABEL)
            ?: error("Could not find the results entry on the scan screen.")
        tapUntil(device, entry) { onResults(device) }
    }

    /**
     * Whether the results screen is showing. The title bar scrolls away with
     * the content, so the headline is the more reliable of the two markers.
     */
    fun onResults(device: UiDevice): Boolean =
        device.hasObject(By.text(RESULTS_HEADLINE_LABEL)) ||
            device.hasObject(By.text(RESULTS_TITLE_LABEL))

    /**
     * Open the scan history screen.
     */
    fun openHistory(device: UiDevice) {
        grantMediaAccess(device)
        val entry = find(device, HISTORY_ENTRY_LABEL)
            ?: error("Could not find the scan history entry on the home screen.")
        tapUntil(device, entry) { device.hasObject(By.text(HISTORY_TITLE_LABEL)) }
    }

    /**
     * Open the duplicate groups screen.
     *
     * The screen is recognised by its title rather than by its heading,
     * because the heading only appears once the content comparison has
     * finished, and the title is on screen immediately. The entry that leads
     * to the screen is worded differently, so an exact match tells them apart.
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
