package com.cleanguard.app.domain.model

enum class PermissionGrantState {
    AUTHORIZED,
    PARTIAL,
    NOT_AUTHORIZED,
}

data class MediaPermissionOverview(
    val photos: PermissionGrantState,
    val videos: PermissionGrantState,
    val audio: PermissionGrantState,
)
