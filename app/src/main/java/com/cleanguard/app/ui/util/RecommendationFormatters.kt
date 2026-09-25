package com.cleanguard.app.ui.util

import androidx.annotation.StringRes
import com.cleanguard.app.R
import com.cleanguard.app.domain.recommendation.RecommendationPriority
import com.cleanguard.app.domain.recommendation.RecommendationReason

@StringRes
fun RecommendationPriority.labelResource(): Int = when (this) {
    RecommendationPriority.HIGH -> R.string.recommendation_priority_high
    RecommendationPriority.MEDIUM -> R.string.recommendation_priority_medium
    RecommendationPriority.LOW -> R.string.recommendation_priority_low
    RecommendationPriority.NONE -> R.string.recommendation_priority_none
}

@StringRes
fun RecommendationReason.labelResource(): Int = when (this) {
    RecommendationReason.CONFIRMED_DUPLICATE -> R.string.recommendation_reason_confirmed_duplicate
    RecommendationReason.OLD_APK -> R.string.recommendation_reason_old_apk
    RecommendationReason.OLD_DOWNLOAD -> R.string.recommendation_reason_old_download
    RecommendationReason.LONG_UNUSED_DOWNLOAD -> R.string.recommendation_reason_long_unused_download
    RecommendationReason.LARGE_SCREEN_RECORDING -> R.string.recommendation_reason_large_screen_recording
    RecommendationReason.OLD_SCREENSHOT -> R.string.recommendation_reason_old_screenshot
    RecommendationReason.LARGE_FILE -> R.string.recommendation_reason_large_file
    RecommendationReason.VERY_LARGE_FILE -> R.string.recommendation_reason_very_large_file
    RecommendationReason.APP_STORAGE_GROWTH -> R.string.recommendation_reason_app_storage_growth
    RecommendationReason.RECENT_FILE -> R.string.recommendation_reason_recent_file
    RecommendationReason.USER_IGNORED -> R.string.recommendation_reason_user_ignored
}
