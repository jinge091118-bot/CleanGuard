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
            grantMediaPermissions()
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

                // 100 additions. The first hundred are the rows that will be
                // modified afterwards.
                val modifyTargets = addRows(context, createdUris, "added", MUTATION_COUNT)
                indexMediaStore(context)
                val afterAdd = measure(scanner, "after_add_100")
                assertEquals(
                    "The incremental scan did not observe the added rows.",
                    MUTATION_COUNT,
                    afterAdd.metadata.changedItemCount,
                )

                // 100 modifications of rows that are already indexed.
                val modified = modifyTargets.take(MUTATION_COUNT)
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

                // The deletions need rows of their own. The hundred rows that
                // were just modified are kept, because deleting them would
                // undo the modification that was just measured, so a second
                // hundred rows are added and then removed.
                val deleteTargets = addRows(context, createdUris, "removed", MUTATION_COUNT)
                indexMediaStore(context)
                measure(scanner, "after_add_100_for_delete")

                deleteTargets.forEach { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
                indexMediaStore(context)
                val afterDelete = measure(scanner, "after_delete_100")
                assertTrue(
                    "The incremental scan did not observe the removed rows.",
                    afterDelete.metadata.removedItemCount >= deleteTargets.size,
                )
            } finally {
                createdUris.forEach { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                database.close()
            }
        }
    }

    /**
     * Grant the media permissions the scanner needs.
     *
     * Installing the application for a test run resets the runtime
     * permissions, and a scanner without them sees an empty collection. The
     * baseline scan then reports no items and the measurement is meaningless,
     * so the permissions are granted here rather than assumed from the device
     * state.
     *
     * The grant is issued as a shell command rather than through the package
     * manager API, because the API route is refused on this ROM and leaves
     * the permission denied. The automation connection can run the command.
     */
    private fun grantMediaPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext.packageName
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO",
            )
        } else {
            listOf("android.permission.READ_EXTERNAL_STORAGE")
        }
        permissions.forEach { permission ->
            runCatching {
                instrumentation.uiAutomation.executeShellCommand("pm grant $target $permission")
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

    /**
     * Add a batch of rows to the fixture root and return their identifiers.
     */
    private fun addRows(
        context: Context,
        sink: MutableList<Uri>,
        label: String,
        count: Int,
    ): List<Uri> {
        val uris = mutableListOf<Uri>()
        repeat(count) { index ->
            uris += insertFixture(
                context = context,
                collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                displayName = "cleanguard-r3-$label-$index.jpg",
                mimeType = "image/jpeg",
                relativePath = "Pictures/CleanGuardR3/",
                content = ByteArray(4 * 1024) { (it + index).toByte() },
            )
        }
        sink += uris
        return uris
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
