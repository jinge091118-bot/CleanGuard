package com.cleanguard.app.domain.model

enum class LargeFileThreshold(
    val bytes: Long,
    val displayName: String,
) {
    MB_50(50L * 1024L * 1024L, "50 MB"),
    MB_100(100L * 1024L * 1024L, "100 MB"),
    MB_500(500L * 1024L * 1024L, "500 MB"),
    GB_1(1024L * 1024L * 1024L, "1 GB"),
    ;

    companion object {
        val DEFAULT = MB_100
    }
}
