package com.cleanguard.app.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageOverviewFactoryTest {
    @Test
    fun `used bytes are derived from total and available bytes`() {
        val overview = StorageOverviewFactory.create(
            totalBytes = 100L,
            availableBytes = 40L,
        )

        assertEquals(100L, overview.totalBytes)
        assertEquals(60L, overview.usedBytes)
        assertEquals(40L, overview.availableBytes)
        assertTrue(overview.categories.isEmpty())
    }

    @Test
    fun `invalid values are clamped to a safe range`() {
        val overview = StorageOverviewFactory.create(
            totalBytes = 100L,
            availableBytes = 140L,
        )

        assertEquals(0L, overview.usedBytes)
        assertEquals(100L, overview.availableBytes)
    }
}
