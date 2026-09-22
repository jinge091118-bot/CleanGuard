package com.cleanguard.app.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreScannerTest {
    @Test
    fun `progress throttle emits after 200 files or 250 milliseconds`() {
        val throttle = ScanProgressThrottle(startTimeMillis = 1_000L)

        assertFalse(throttle.shouldEmit(scannedFileCount = 199L, nowMillis = 1_249L))
        assertTrue(throttle.shouldEmit(scannedFileCount = 200L, nowMillis = 1_100L))

        throttle.markEmitted(scannedFileCount = 200L, nowMillis = 1_100L)

        assertFalse(throttle.shouldEmit(scannedFileCount = 399L, nowMillis = 1_349L))
        assertTrue(throttle.shouldEmit(scannedFileCount = 399L, nowMillis = 1_350L))
    }

    @Test
    fun `progress fraction advances across the three media collections`() {
        assertEquals(0f, mediaScanFraction(0, 0, 100), 0.0001f)
        assertEquals(1f / 6f, mediaScanFraction(0, 50, 100), 0.0001f)
        assertEquals(1f / 3f, mediaScanFraction(0, 0, 0, collectionComplete = true), 0.0001f)
        assertEquals(2f / 3f, mediaScanFraction(1, 0, 0, collectionComplete = true), 0.0001f)
        assertEquals(1f, mediaScanFraction(2, 0, 0, collectionComplete = true), 0.0001f)
    }
}
