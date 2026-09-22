package com.cleanguard.app.platform.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.repository.PermissionRepository

class PermissionManager(
    context: Context,
) : PermissionRepository {
    private val appContext = context.applicationContext

    override fun getMediaPermissionStatus(): MediaPermissionStatus =
        MediaPermissionPolicy.resolve(Build.VERSION.SDK_INT) { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
        }

    override fun permissionsForRequest(): List<String> =
        MediaPermissionPolicy.permissionsForRequest(Build.VERSION.SDK_INT)
}

internal object MediaPermissionPolicy {
    private const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    private const val READ_MEDIA_AUDIO = "android.permission.READ_MEDIA_AUDIO"
    private const val READ_MEDIA_VISUAL_USER_SELECTED =
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    fun permissionsForRequest(sdkInt: Int): List<String> = when {
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
            READ_MEDIA_AUDIO,
            READ_MEDIA_VISUAL_USER_SELECTED,
        )

        sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
            READ_MEDIA_IMAGES,
            READ_MEDIA_VIDEO,
            READ_MEDIA_AUDIO,
        )

        else -> listOf(READ_EXTERNAL_STORAGE)
    }

    fun resolve(
        sdkInt: Int,
        isGranted: (String) -> Boolean,
    ): MediaPermissionStatus {
        val fullAccessPermissions = when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
                READ_MEDIA_IMAGES,
                READ_MEDIA_VIDEO,
                READ_MEDIA_AUDIO,
            )

            else -> listOf(READ_EXTERNAL_STORAGE)
        }

        if (fullAccessPermissions.all(isGranted)) {
            return MediaPermissionStatus.FULL
        }

        val hasSelectedVisualAccess =
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                isGranted(READ_MEDIA_VISUAL_USER_SELECTED)
        val hasAnyMediaAccess = fullAccessPermissions.any(isGranted)

        return if (hasSelectedVisualAccess || hasAnyMediaAccess) {
            MediaPermissionStatus.PARTIAL
        } else {
            MediaPermissionStatus.NONE
        }
    }
}
