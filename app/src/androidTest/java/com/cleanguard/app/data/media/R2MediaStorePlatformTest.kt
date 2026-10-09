package com.cleanguard.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cleanguard.app.data.duplicate.CachingDuplicateFileHasher
import com.cleanguard.app.data.duplicate.ContentResolverHashInputSource
import com.cleanguard.app.data.duplicate.DuplicateVerificationEngineImpl
import com.cleanguard.app.data.duplicate.StreamHashCalculator
import com.cleanguard.app.data.local.CleanGuardDatabase
import com.cleanguard.app.data.local.repository.RoomHashCacheRepository
import com.cleanguard.app.data.local.repository.RoomIncrementalScanPersistenceRepository
import com.cleanguard.app.domain.duplicate.DuplicateFile
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanExecutionType
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
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

    @Test
    fun incrementalScanner_tracksNoChangeModificationAndDeletion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            CleanGuardDatabase::class.java,
        ).build()
        var fixtureUri: Uri? = null
        try {
            fixtureUri = insertTestMedia(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-incremental-before.jpg",
                mimeType = "image/jpeg",
                relativePath = "Pictures/CleanGuardR2/",
                content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
            )
            val fixtureMediaId = checkNotNull(fixtureUri.lastPathSegment?.toLongOrNull())
            val scanner = IncrementalMediaStoreScanner(
                sdkInt = Build.VERSION.SDK_INT,
                gateway = AndroidMediaStoreGateway(
                    context = context.applicationContext,
                    contentResolver = context.contentResolver,
                ),
                persistence = RoomIncrementalScanPersistenceRepository(database),
                legacyScanner = MediaStoreScanner(context.contentResolver, Dispatchers.IO),
                ioDispatcher = Dispatchers.IO,
            )

            val initial = scanner.completedResult()
            assertEquals(MediaScanExecutionType.FULL, initial.metadata.executionType)
            assertTrue(initial.items.any { it.mediaStoreId == fixtureMediaId })

            val unchanged = scanner.completedResult()
            assertEquals(MediaScanExecutionType.INCREMENTAL, unchanged.metadata.executionType)
            assertEquals(0, unchanged.metadata.changedItemCount)
            assertEquals(0, unchanged.metadata.removedItemCount)
            assertTrue(unchanged.items.any { it.mediaStoreId == fixtureMediaId })

            updateTestMedia(
                context = context,
                uri = fixtureUri,
                displayName = "cleanguard-r2-incremental-after.jpg",
                content = ByteArray(128 * 1024) { index -> (index % 251).toByte() },
            )
            val modified = scanner.completedResult()
            assertEquals(MediaScanExecutionType.INCREMENTAL, modified.metadata.executionType)
            assertTrue(modified.metadata.changedItemCount >= 1)
            val modifiedFixture = modified.items.single { it.mediaStoreId == fixtureMediaId }
            assertEquals("cleanguard-r2-incremental-after.jpg", modifiedFixture.displayName)
            assertEquals(128L * 1024L, modifiedFixture.sizeBytes)

            assertEquals(1, context.contentResolver.delete(fixtureUri, null, null))
            fixtureUri = null
            val deleted = scanner.completedResult()
            assertEquals(MediaScanExecutionType.INCREMENTAL, deleted.metadata.executionType)
            assertTrue(deleted.metadata.removedItemCount >= 1)
            assertTrue(deleted.items.none { it.mediaStoreId == fixtureMediaId })
        } finally {
            fixtureUri?.let { context.contentResolver.delete(it, null, null) }
            database.close()
        }
    }

    @Test
    fun duplicateVerification_hashesRealAppOwnedRowsAndInvalidatesStaleCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(
            context,
            CleanGuardDatabase::class.java,
        ).build()
        val createdUris = mutableListOf<Uri>()
        try {
            val duplicateBytes = ByteArray(192 * 1024) { index -> (index % 239).toByte() }
            createdUris += insertTestMedia(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-duplicate-a.mp4",
                mimeType = "video/mp4",
                relativePath = "Movies/CleanGuardR2/",
                content = duplicateBytes,
            )
            createdUris += insertTestMedia(
                context = context,
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r2-duplicate-b.mp4",
                mimeType = "video/mp4",
                relativePath = "Movies/CleanGuardR2/",
                content = duplicateBytes,
            )

            val uriStrings = createdUris.map(Uri::toString).toSet()
            val scannedFiles = MediaStoreScanner(
                contentResolver = context.contentResolver,
                ioDispatcher = Dispatchers.IO,
            ).scan().filterIsInstance<MediaScanEvent.Completed>().single().result.items
                .filter { it.contentUri in uriStrings }
                .map { item -> item.toDuplicateFile() }
            assertEquals(2, scannedFiles.size)

            val cacheRepository = RoomHashCacheRepository(database.hashCacheDao())
            val hasher = CachingDuplicateFileHasher(
                cacheRepository = cacheRepository,
                calculator = StreamHashCalculator(
                    ContentResolverHashInputSource(context.contentResolver),
                    Dispatchers.IO,
                ),
            )
            val verifier = DuplicateVerificationEngineImpl(hasher)
            val firstVerification = verifier.verify(scannedFiles)
            assertTrue(firstVerification.issues.isEmpty())
            assertEquals(1, firstVerification.groups.size)
            assertEquals(2, firstVerification.groups.single().files.size)
            assertEquals(duplicateBytes.size.toLong(), firstVerification.groups.single().reclaimableBytes)
            assertTrue(firstVerification.groups.single().fullHash?.isNotBlank() == true)

            val cachedVerification = verifier.verify(scannedFiles)
            assertEquals(firstVerification.groups, cachedVerification.groups)
            scannedFiles.forEach { file ->
                assertEquals(1, database.hashCacheDao().countForUri(file.contentUri))
            }

            val original = scannedFiles.first()
            updateTestMedia(
                context = context,
                uri = createdUris.first(),
                displayName = "cleanguard-r2-duplicate-a-modified.mp4",
                content = duplicateBytes + byteArrayOf(1),
            )
            val rescanned = MediaStoreScanner(
                contentResolver = context.contentResolver,
                ioDispatcher = Dispatchers.IO,
            ).scan().filterIsInstance<MediaScanEvent.Completed>().single().result.items
                .single { it.contentUri == original.contentUri }
                .toDuplicateFile()
            assertTrue(rescanned.sizeBytes != original.sizeBytes)

            hasher.quickHash(rescanned)
            assertEquals(1, database.hashCacheDao().countForUri(original.contentUri))
            assertEquals(
                null,
                database.hashCacheDao().findCurrent(
                    original.contentUri,
                    original.sizeBytes,
                    original.modifiedAtEpochMillis,
                ),
            )
            assertTrue(
                database.hashCacheDao().findCurrent(
                    rescanned.contentUri,
                    rescanned.sizeBytes,
                    rescanned.modifiedAtEpochMillis,
                ) != null,
            )
        } finally {
            createdUris.forEach { uri -> context.contentResolver.delete(uri, null, null) }
            database.close()
        }
    }

    private suspend fun com.cleanguard.app.domain.repository.MediaScanner.completedResult(): MediaScanResult =
        scan().filterIsInstance<MediaScanEvent.Completed>().single().result

    private fun MediaStoreItem.toDuplicateFile() = DuplicateFile(
        itemId = id,
        contentUri = checkNotNull(contentUri),
        displayName = displayName,
        mimeType = mimeType,
        relativePath = relativePath,
        sizeBytes = sizeBytes,
        modifiedAtEpochMillis = modifiedAtEpochMillis,
        source = ScanSource.MEDIA_STORE,
        riskLevel = RiskLevel.REVIEW,
    )

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

    private fun updateTestMedia(
        context: Context,
        uri: Uri,
        displayName: String,
        content: ByteArray,
    ) {
        val resolver = context.contentResolver
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 1) },
            null,
            null,
        )
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(content)
            } ?: throw IOException("MediaStore returned no output stream for the R2 update fixture.")
            val updated = resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            check(updated == 1) { "MediaStore rejected the R2 fixture update." }
        } catch (error: Throwable) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            throw error
        }
    }
}
