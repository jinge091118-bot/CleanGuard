package com.cleanguard.app.domain.recommendation

import com.cleanguard.app.domain.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaCategoryClassifierTest {
    @Test
    fun `image in screenshot directory is classified as screenshot`() {
        assertEquals(
            FileCategory.SCREENSHOT,
            classifyMediaCategory(FileCategory.IMAGE, "image/png", "Pictures/Screenshots/"),
        )
    }

    @Test
    fun `localized screenshot directory is recognized`() {
        assertEquals(
            FileCategory.SCREENSHOT,
            classifyMediaCategory(FileCategory.IMAGE, "image/jpeg", "DCIM/截图/"),
        )
    }

    @Test
    fun `video in screen recording directory is classified as screen recording`() {
        assertEquals(
            FileCategory.SCREEN_RECORDING,
            classifyMediaCategory(
                FileCategory.VIDEO,
                "video/mp4",
                "Movies/Screen recordings/",
            ),
        )
    }

    @Test
    fun `file name alone never classifies screenshot`() {
        assertEquals(
            FileCategory.IMAGE,
            classifyMediaCategory(FileCategory.IMAGE, "image/png", "Pictures/Camera/"),
        )
    }

    @Test
    fun `directory keyword with incompatible media type remains unchanged`() {
        assertEquals(
            FileCategory.VIDEO,
            classifyMediaCategory(FileCategory.VIDEO, "video/mp4", "Pictures/Screenshots/"),
        )
    }
}
