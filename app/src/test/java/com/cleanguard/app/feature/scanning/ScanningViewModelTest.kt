package com.cleanguard.app.feature.scanning

import com.cleanguard.app.data.media.CurrentScanRepository
import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanProgress
import com.cleanguard.app.domain.model.MediaScanResult
import com.cleanguard.app.domain.model.MediaScanStage
import com.cleanguard.app.domain.model.MediaStoreItem
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.DownloadAccessState
import com.cleanguard.app.domain.model.SafScanEvent
import com.cleanguard.app.domain.model.SafScanProgress
import com.cleanguard.app.domain.model.SafScanResult
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.MediaScanner
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.repository.SafScanner
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import com.cleanguard.app.domain.usecase.ApplySafetyRulesUseCase
import com.cleanguard.app.domain.usecase.ScanMediaUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScanningViewModelTest {
    @Test
    fun `stop scan cancels the active scanner coroutine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val scanner = CancelAwareMediaScanner()
            val scanRepository = CurrentScanRepository()
            val viewModel = ScanningViewModel(
                scanMedia = ScanMediaUseCase(scanner),
                applySafetyRules = ApplySafetyRulesUseCase(
                    TestSafetyRuleRepository,
                    StandardTestDispatcher(testScheduler),
                ),
                permissionRepository = GrantedPermissionRepository,
                scanSessionRepository = scanRepository,
                downloadAccessRepository = NoDownloadAccessRepository,
                safScanner = EmptySafScanner,
            )

            runCurrent()
            assertTrue(viewModel.uiState.value.isScanning)

            viewModel.onEvent(ScanningUiEvent.StopScan)
            runCurrent()

            assertTrue(scanner.wasCancelled)
            assertTrue(viewModel.uiState.value.isStopped)
            assertFalse(viewModel.uiState.value.isScanning)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `completed scan exposes real item count and bytes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val item = MediaStoreItem(
                id = "media:image:7",
                mediaStoreId = 7L,
                displayName = "sample.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 2_048L,
                modifiedAtEpochMillis = 1_000L,
                relativePath = "Pictures/",
                category = FileCategory.IMAGE,
            )
            val result = MediaScanResult(listOf(item), emptyList())
            val scanRepository = CurrentScanRepository()
            val scanner = StubMediaScanner(
                flowOf(
                    MediaScanEvent.Progress(
                        MediaScanProgress(
                            stage = MediaScanStage.COMPLETE,
                            fraction = 1f,
                            scannedFileCount = 1L,
                            scannedBytes = 2_048L,
                            errorCount = 0,
                        ),
                    ),
                    MediaScanEvent.Completed(result),
                ),
            )
            val viewModel = ScanningViewModel(
                scanMedia = ScanMediaUseCase(scanner),
                applySafetyRules = ApplySafetyRulesUseCase(
                    TestSafetyRuleRepository,
                    StandardTestDispatcher(testScheduler),
                ),
                permissionRepository = GrantedPermissionRepository,
                scanSessionRepository = scanRepository,
                downloadAccessRepository = NoDownloadAccessRepository,
                safScanner = EmptySafScanner,
            )

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isComplete)
            assertEquals(1L, viewModel.uiState.value.scannedFileCount)
            assertEquals(2_048L, viewModel.uiState.value.scannedBytes)
            assertEquals(1L, viewModel.uiState.value.classifiedFileCount)
            assertEquals(1, scanRepository.currentScannedFiles().size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `SAF scan works without media permission and stores APK result`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val scanRepository = CurrentScanRepository()
            val apk = ScannedFile(
                id = "saf:downloads:app.apk",
                displayName = "app.apk",
                mimeType = "application/vnd.android.package-archive",
                sizeBytes = 4_096L,
                modifiedAtEpochMillis = 1_000L,
                relativePath = "Download/",
                source = ScanSource.SAF,
                category = FileCategory.APK,
            )
            val viewModel = ScanningViewModel(
                scanMedia = ScanMediaUseCase(FailingMediaScanner),
                applySafetyRules = ApplySafetyRulesUseCase(
                    TestSafetyRuleRepository,
                    StandardTestDispatcher(testScheduler),
                ),
                permissionRepository = DeniedPermissionRepository,
                scanSessionRepository = scanRepository,
                downloadAccessRepository = GrantedDownloadAccessRepository,
                safScanner = StubSafScanner(
                    flowOf(
                        SafScanEvent.Progress(SafScanProgress(1L, 4_096L, 0)),
                        SafScanEvent.Completed(SafScanResult(listOf(apk), emptyList())),
                    ),
                ),
            )

            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isComplete)
            assertFalse(viewModel.uiState.value.requiresPermission)
            assertEquals(1L, viewModel.uiState.value.scannedFileCount)
            val stored = scanRepository.scanItems.first().single()
            assertEquals(ScanSource.SAF, stored.source)
            assertEquals(FileCategory.APK, stored.category)
            assertEquals(RiskLevel.REVIEW, stored.safetyDecision.riskLevel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class CancelAwareMediaScanner : MediaScanner {
    var wasCancelled: Boolean = false
        private set

    override fun scan(): Flow<MediaScanEvent> = flow {
        try {
            emit(
                MediaScanEvent.Progress(
                    MediaScanProgress(
                        stage = MediaScanStage.IMAGES,
                        fraction = 0.1f,
                        scannedFileCount = 1L,
                        scannedBytes = 512L,
                        errorCount = 0,
                    ),
                ),
            )
            awaitCancellation()
        } finally {
            wasCancelled = true
        }
    }
}

private class StubMediaScanner(
    private val events: Flow<MediaScanEvent>,
) : MediaScanner {
    override fun scan(): Flow<MediaScanEvent> = events
}

private data object FailingMediaScanner : MediaScanner {
    override fun scan(): Flow<MediaScanEvent> = flow {
        error("Media scanner must not run without media permission")
    }
}

private class StubSafScanner(private val events: Flow<SafScanEvent>) : SafScanner {
    override fun scan(treeUri: String): Flow<SafScanEvent> = events
}

private data object GrantedPermissionRepository : PermissionRepository {
    override fun getMediaPermissionStatus(): MediaPermissionStatus = MediaPermissionStatus.FULL

    override fun permissionsForRequest(): List<String> = emptyList()
}

private data object DeniedPermissionRepository : PermissionRepository {
    override fun getMediaPermissionStatus(): MediaPermissionStatus = MediaPermissionStatus.NONE

    override fun permissionsForRequest(): List<String> = emptyList()
}

private data object NoDownloadAccessRepository : DownloadAccessRepository {
    private val state = MutableStateFlow(DownloadAccessState())
    override val accessState: StateFlow<DownloadAccessState> = state

    override suspend fun refreshAccess(): DownloadAccessState = state.value

    override suspend fun persistAccess(uri: String, resultFlags: Int): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    override suspend fun persistedTreeUri(): String? = null
}

private data object EmptySafScanner : SafScanner {
    override fun scan(treeUri: String): Flow<SafScanEvent> = flowOf()
}

private data object GrantedDownloadAccessRepository : DownloadAccessRepository {
    private val state = MutableStateFlow(
        DownloadAccessState(isGranted = true, directoryName = "Download"),
    )
    override val accessState: StateFlow<DownloadAccessState> = state

    override suspend fun refreshAccess(): DownloadAccessState = state.value

    override suspend fun persistAccess(uri: String, resultFlags: Int): Result<Unit> = Result.success(Unit)

    override suspend fun persistedTreeUri(): String =
        "content://com.android.externalstorage.documents/tree/primary%3ADownload"
}

private data object TestSafetyRuleRepository : SafetyRuleRepository {
    override suspend fun loadRules(): Result<List<SafetyRule>> = Result.success(
        listOf(
            SafetyRule(
                id = "review-media",
                riskLevel = RiskLevel.REVIEW,
                reason = "用户媒体",
                deletionImpact = "处理前需要确认",
                allowedActions = setOf(CleanupAction.NONE, CleanupAction.PREVIEW),
                matcher = SafetyRuleMatcher(
                    categories = setOf(
                        FileCategory.IMAGE,
                        FileCategory.VIDEO,
                        FileCategory.AUDIO,
                        FileCategory.APK,
                    ),
                ),
            ),
        ),
    )
}
