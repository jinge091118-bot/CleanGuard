package com.cleanguard.app.ui.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val KIB = 1024.0
private const val MIB = KIB * 1024.0
private const val GIB = MIB * 1024.0

fun Long.formatBytes(): String = when {
    this >= GIB -> String.format(Locale.SIMPLIFIED_CHINESE, "%.1f GB", this / GIB)
    this >= MIB -> String.format(Locale.SIMPLIFIED_CHINESE, "%.0f MB", this / MIB)
    this >= KIB -> String.format(Locale.SIMPLIFIED_CHINESE, "%.0f KB", this / KIB)
    else -> "$this B"
}

fun Long.formatDate(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    .withLocale(Locale.SIMPLIFIED_CHINESE)
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(this))

