package com.cleanguard.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaScanEvent
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class R2MediaStorePlatformTest {
    @Test
    fun realMediaStoreScanner_readsAppOwnedImageVideoAndAudioRows() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        val createdUris = mutableListOf<Uri>()
        try {
            createdUris += insertTestMedia(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-image.jpg",
                mimeType = "image/jpeg",
                relativePath = "Pictures/CleanGuardR2/",
                content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
            )
            createdUris += insertTestMedia(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-video.mp4",
                mimeType = "video/mp4",
                relativePath = "Movies/CleanGuardR2/",
                content = "CleanGuard R2 video fixture".encodeToByteArray(),
            )
            createdUris += insertTestMedia(
                context = context,
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-audio.mp3",
                mimeType = "audio/mpeg",
                relativePath = "Music/CleanGuardR2/",
                content = "CleanGuard R2 audio fixture".encodeToByteArray(),
            )

            val completed = MediaStoreScanner(
                contentResolver = context.contentResolver,
                ioDispatcher = Dispatchers.IO,
            ).scan().filterIsInstance<MediaScanEvent.Completed>().single()

            val createdUriStrings = createdUris.map(Uri::toString).toSet()
            val scannedFixtures = completed.result.items
                .filter { it.contentUri in createdUriStrings }

            assertTrue(completed.result.errors.isEmpty())
            assertEquals(3, scannedFixtures.size)
            assertEquals(
                setOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO),
                scannedFixtures.map { it.category }.toSet(),
            )
            assertTrue(scannedFixtures.all { it.sizeBytes > 0L })
            assertTrue(scannedFixtures.all { it.relativePath?.contains("CleanGuardR2") == true })
        } finally {
            createdUris.forEach { uri ->
                context.contentResolver.delete(uri, null, null)
            }
        }
    }

    private fun insertTestMedia(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        content: ByteArray,
    ): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = checkNotNull(context.contentResolver.insert(collection, values)) {
            "MediaStore rejected the R2 test fixture."
        }
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                output.write(content)
            } ?: throw IOException("MediaStore returned no output stream for the R2 fixture.")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        }
    }
}
