package com.cleanguard.app.data.saf

import com.cleanguard.app.domain.model.FileCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class SafFileClassifierTest {
    @Test
    fun `APK mime type is classified as APK`() {
        assertEquals(
            FileCategory.APK,
            classifySafFile("package.bin", "application/vnd.android.package-archive"),
        )
    }

    @Test
    fun `APK extension is classified as APK when provider mime is generic`() {
        assertEquals(
            FileCategory.APK,
            classifySafFile("offline-map.APK", "application/octet-stream"),
        )
    }

    @Test
    fun `unrecognized download remains DOWNLOAD`() {
        assertEquals(
            FileCategory.DOWNLOAD,
            classifySafFile("archive.bin", "application/octet-stream"),
        )
    }
}
