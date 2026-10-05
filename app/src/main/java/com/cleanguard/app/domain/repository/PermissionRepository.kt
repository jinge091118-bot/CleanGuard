package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.MediaPermissionOverview
import com.cleanguard.app.domain.model.PermissionGrantState

interface PermissionRepository {
    fun getMediaPermissionStatus(): MediaPermissionStatus

    fun permissionsForRequest(): List<String>

    fun getMediaPermissionOverview(): MediaPermissionOverview = when (getMediaPermissionStatus()) {
        MediaPermissionStatus.FULL -> MediaPermissionOverview(
            PermissionGrantState.AUTHORIZED,
            PermissionGrantState.AUTHORIZED,
            PermissionGrantState.AUTHORIZED,
        )
        MediaPermissionStatus.PARTIAL -> MediaPermissionOverview(
            PermissionGrantState.PARTIAL,
            PermissionGrantState.PARTIAL,
            PermissionGrantState.NOT_AUTHORIZED,
        )
        MediaPermissionStatus.NONE -> MediaPermissionOverview(
            PermissionGrantState.NOT_AUTHORIZED,
            PermissionGrantState.NOT_AUTHORIZED,
            PermissionGrantState.NOT_AUTHORIZED,
        )
    }
}
