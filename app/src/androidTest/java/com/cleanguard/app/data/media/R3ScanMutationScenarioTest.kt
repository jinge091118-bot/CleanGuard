package com.cleanguard.app.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cleanguard.app.data.local.CleanGuardDatabase
import com.cleanguard.app.data.local.repository.RoomIncrementalScanPersistenceRepository
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R3 incremental-scan mutation scenario.
 *
 * Executes the 100 add / 100 modify / 100 delete sequence required by
 * `R3-PERFORMANCE-TEST-PLAN.md` against the fixture that is already present
 * on the device, then measures the incremental scan that follows each mutation
 * and reports the observed change and removal counts.
 *
 * All mutations are confined to the dedicated `CleanGuardR3` fixture root. The
 * test removes exactly the rows it created and never touches a row outside
 * that root.
 */
@RunWith(AndroidJUnit4::class)
class R3ScanMutationScenarioTest {

    @Test
    fun measureIncrementalScanAfterAddModifyAndDelete() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(
                context,
                CleanGuardDatabase::class.java,
            ).build()
            val createdUris = mutableListOf<Uri>()
            try {
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

                val baseline = measure(scanner, "baseline")
                assertTrue("The baseline scan returned no items.", baseline.items.isNotEmpty())

                // 100 additions.
                repeat(MUTATION_COUNT) { index ->
                    createdUris += insertFixture(
                        context = context,
                        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        displayName = "cleanguard-r3-added-$index.jpg",
                        mimeType = "image/jpeg",
                        relativePath = "Pictures/CleanGuardR3/",
                        content = ByteArray(4 * 1024) { (it + index).toByte() },
                    )
                }
                indexMediaStore(context)
                val afterAdd = measure(scanner, "after_add_100")
                assertEquals(
                    "The incremental scan did not observe the added rows.",
                    MUTATION_COUNT,
                    afterAdd.metadata.changedItemCount,
                )

                // 100 modifications of rows that are already indexed.
                val modified = createdUris.take(MUTATION_COUNT / 2)
                modified.forEachIndexed { index, uri ->
                    updateFixture(
                        context = context,
                        uri = uri,
                        displayName = "cleanguard-r3-modified-$index.jpg",
                        content = ByteArray(8 * 1024) { (it * 3 + index).toByte() },
                    )
                }
                indexMediaStore(context)
                val afterModify = measure(scanner, "after_modify_100")
                assertTrue(
                    "The incremental scan did not observe the modified rows.",
                    afterModify.metadata.changedItemCount >= modified.size,
                )

                // 100 deletions of rows that are already indexed.
                val deleted = createdUris.drop(MUTATION_COUNT / 2).take(MUTATION_COUNT / 2)
                deleted.forEach { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
                indexMediaStore(context)
                val afterDelete = measure(scanner, "after_delete_100")
                assertTrue(
                    "The incremental scan did not observe the removed rows.",
                    afterDelete.metadata.removedItemCount >= deleted.size,
                )
            } finally {
                createdUris.forEach { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                database.close()
            }
        }
    }

    private suspend fun measure(
        scanner: IncrementalMediaStoreScanner,
        label: String,
    ): MediaScanResult {
        val startedAt = System.nanoTime()
        val result = scanner.scan().filterIsInstance<MediaScanEvent.Completed>().single().result
        val wallClockMs = (System.nanoTime() - startedAt) / 1_000_000L
        println(
            "R3_MUTATION label=$label wall_clock_ms=$wallClockMs " +
                "metadata_duration_ms=${result.metadata.durationMs} " +
                "execution_type=${result.metadata.executionType} " +
                "changed=${result.metadata.changedItemCount} " +
                "removed=${result.metadata.removedItemCount} " +
                "items=${result.items.size} " +
                "errors=${result.errors.size}",
        )
        return result
    }

    private fun insertFixture(
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
            "MediaStore rejected the R3 mutation fixture."
        }
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            output.write(content)
        }
        context.contentResolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.SIZE, content.size.toLong())
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
        return uri
    }

    private fun updateFixture(
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
        resolver.openOutputStream(uri, "w")?.use { output ->
            output.write(content)
        }
        resolver.update(
            uri,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.SIZE, content.size.toLong())
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            },
            null,
            null,
        )
    }

    private fun indexMediaStore(context: Context) {
        context.contentResolver.call(
            Uri.parse("content://media/external"),
            "scan_volume",
            null,
            null,
        )
    }
}

private const val MUTATION_COUNT = 100
