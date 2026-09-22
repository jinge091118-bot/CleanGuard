package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.MediaPermissionStatus

interface PermissionRepository {
    fun getMediaPermissionStatus(): MediaPermissionStatus

    fun permissionsForRequest(): List<String>
}
