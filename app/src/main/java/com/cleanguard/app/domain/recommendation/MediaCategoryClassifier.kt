package com.cleanguard.app.domain.recommendation

import com.cleanguard.app.domain.model.FileCategory
import java.util.Locale

fun classifyMediaCategory(
    category: FileCategory,
    mimeType: String?,
    relativePath: String?,
): FileCategory {
    val normalizedMime = mimeType?.lowercase(Locale.ROOT)
    val normalizedPath = relativePath
        ?.replace('\\', '/')
        ?.lowercase(Locale.ROOT)
        ?: return category
    val isImage = category == FileCategory.IMAGE || normalizedMime?.startsWith("image/") == true
    val isVideo = category == FileCategory.VIDEO || normalizedMime?.startsWith("video/") == true

    return when {
        isImage && SCREENSHOT_DIRECTORIES.any(normalizedPath::contains) -> FileCategory.SCREENSHOT
        isVideo && SCREEN_RECORDING_DIRECTORIES.any(normalizedPath::contains) -> {
            FileCategory.SCREEN_RECORDING
        }
        else -> category
    }
}

private val SCREENSHOT_DIRECTORIES = setOf(
    "screenshots",
    "screenshot",
    "截屏",
    "截图",
    "截圖",
)

private val SCREEN_RECORDING_DIRECTORIES = setOf(
    "screen recordings",
    "screen_recordings",
    "screenrecordings",
    "screenrecorder",
    "screenrecord",
    "录屏",
    "錄屏",
    "螢幕錄製",
)
