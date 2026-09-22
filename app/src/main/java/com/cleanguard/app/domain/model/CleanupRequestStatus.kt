package com.cleanguard.app.domain.model

enum class CleanupRequestStatus {
    IDLE,
    PREPARING_SYSTEM_CONFIRMATION,
    AWAITING_SYSTEM_CONFIRMATION,
    CONFIRMED,
    CANCELLED,
    FAILED,
}
