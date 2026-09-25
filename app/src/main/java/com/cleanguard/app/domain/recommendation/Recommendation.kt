package com.cleanguard.app.domain.recommendation

data class Recommendation(
    val itemId: String,
    val priority: RecommendationPriority,
    val reasons: List<RecommendationReason>,
    val explanation: String,
)

enum class RecommendationPriority {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

enum class RecommendationReason {
    CONFIRMED_DUPLICATE,
    OLD_APK,
    OLD_DOWNLOAD,
    LONG_UNUSED_DOWNLOAD,
    LARGE_SCREEN_RECORDING,
    OLD_SCREENSHOT,
    LARGE_FILE,
    VERY_LARGE_FILE,
    APP_STORAGE_GROWTH,
    RECENT_FILE,
    USER_IGNORED,
}
