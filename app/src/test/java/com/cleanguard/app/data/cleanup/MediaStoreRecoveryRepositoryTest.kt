package com.cleanguard.app.data.cleanup

import com.cleanguard.app.domain.model.SystemConfirmationOutcome
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryItem
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.domain.repository.CleanupHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreRecoveryRepositoryTest {
    @Test
    fun `restore URI validation accepts only normalized MediaStore content URIs`() {
        val entry = entry(
            items = listOf(
                CleanupHistoryItem("one", "content://media/external/images/media/1"),
                CleanupHistoryItem("two", "content://media/external/images/media/1"),
            ),
        )

        assertEquals(
            listOf("content://media/external/images/media/1"),
            restoreUriStrings(entry),
        )
    }

    @Test
    fun `invalid URI becomes unavailable and is not sent to system`() = runBlocking {
        val history = FakeHistoryRepository()
        val repository = MediaStoreRecoveryRepository(
            historyRepository = history,
            ioDispatcher = Dispatchers.Unconfined,
            sdkInt = 36,
            requestFactory = MediaStoreTrashRequestFactory { _, _ -> error("must not run") },
        )

        val result = repository.createRestoreRequest(
            entry(items = listOf(CleanupHistoryItem("bad", "file:///sdcard/a.jpg"))),
        )

        assertTrue(result.isFailure)
        assertEquals(CleanupRestoreStatus.FAILED, history.status)
    }

    @Test
    fun `confirmed restore marks restored while cancellation remains available`() = runBlocking {
        val history = FakeHistoryRepository()
        val repository = MediaStoreRecoveryRepository(
            historyRepository = history,
            ioDispatcher = Dispatchers.Unconfined,
            sdkInt = 36,
            requestFactory = MediaStoreTrashRequestFactory { _, _ -> error("unused") },
        )
        val entry = entry()

        repository.completeRestore(entry, SystemConfirmationOutcome.CANCELLED)
        assertEquals(null, history.status)
        repository.completeRestore(entry, SystemConfirmationOutcome.CONFIRMED)
        assertEquals(CleanupRestoreStatus.RESTORED, history.status)
    }

    private fun entry(
        items: List<CleanupHistoryItem> = listOf(
            CleanupHistoryItem("one", "content://media/external/images/media/1"),
        ),
    ) = CleanupHistoryEntry(
        cleanupId = "cleanup-1",
        timestamp = 1L,
        requestedItemCount = 1,
        processedItemCount = 1,
        failedItemCount = 0,
        requestedBytes = 100L,
        freedBytes = 0L,
        cleanupType = CleanupHistoryType.MEDIA_STORE_TRASH,
        usedMediaStoreTrash = true,
        restoreStatus = CleanupRestoreStatus.AVAILABLE,
        items = items,
    )
}

private class FakeHistoryRepository : CleanupHistoryRepository {
    var status: CleanupRestoreStatus? = null
    override suspend fun save(entry: CleanupHistoryEntry) = Unit
    override suspend fun findById(cleanupId: String): CleanupHistoryEntry? = null
    override fun observeRecent(limit: Int): Flow<List<CleanupHistoryEntry>> = flowOf(emptyList())
    override suspend fun updateRestoreStatus(
        cleanupId: String,
        status: CleanupRestoreStatus,
    ): Int {
        this.status = status
        return 1
    }
}
