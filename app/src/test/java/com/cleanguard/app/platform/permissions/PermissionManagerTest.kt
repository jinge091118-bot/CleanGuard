package com.cleanguard.app.platform.permissions

import android.Manifest
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.PermissionGrantState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerTest {
    @Test
    fun `android 12 storage permission maps to full or none`() {
        assertEquals(
            MediaPermissionStatus.NONE,
            resolve(sdkInt = 32),
        )
        assertEquals(
            MediaPermissionStatus.FULL,
            resolve(
                sdkInt = 32,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ),
        )
    }

    @Test
    fun `android 13 subset of media permissions is partial`() {
        assertEquals(
            MediaPermissionStatus.PARTIAL,
            resolve(
                sdkInt = 33,
                Manifest.permission.READ_MEDIA_IMAGES,
            ),
        )
    }

    @Test
    fun `android 13 all media permissions are full`() {
        assertEquals(
            MediaPermissionStatus.FULL,
            resolve(
                sdkInt = 33,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            ),
        )
    }

    @Test
    fun `android 14 selected visual access is partial`() {
        assertEquals(
            MediaPermissionStatus.PARTIAL,
            resolve(
                sdkInt = 34,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
        )
    }

    @Test
    fun `android 14 request includes selected visual permission`() {
        val permissions = MediaPermissionPolicy.permissionsForRequest(sdkInt = 34)

        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_VIDEO))
        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_AUDIO))
        assertTrue(permissions.contains(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))
    }

    @Test
    fun `android 14 selected visuals are partial while audio stays unauthorized`() {
        val overview = MediaPermissionPolicy.overview(sdkInt = 34) { permission ->
            permission == Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }

        assertEquals(PermissionGrantState.PARTIAL, overview.photos)
        assertEquals(PermissionGrantState.PARTIAL, overview.videos)
        assertEquals(PermissionGrantState.NOT_AUTHORIZED, overview.audio)
    }

    private fun resolve(
        sdkInt: Int,
        vararg grantedPermissions: String,
    ): MediaPermissionStatus {
        val granted = grantedPermissions.toSet()
        return MediaPermissionPolicy.resolve(sdkInt) { permission -> permission in granted }
    }
}
