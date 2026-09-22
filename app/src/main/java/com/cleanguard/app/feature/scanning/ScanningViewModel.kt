package com.cleanguard.app.feature.scanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cleanguard.app.domain.model.MediaPermissionStatus
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.model.MediaScanStage
import com.cleanguard.app.domain.model.SafScanEvent
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.toScannedFile
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import com.cleanguard.app.domain.repository.PermissionRepository
import com.cleanguard.app.domain.repository.SafScanner
import com.cleanguard.app.domain.repository.ScanSessionRepository
import com.cleanguard.app.domain.usecase.ScanMediaUseCase
import com.cleanguard.app.domain.usecase.ApplySafetyRulesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class ScanningViewModel(
    private val scanMedia: ScanMediaUseCase,
    private val applySafetyRules: ApplySafetyRulesUseCase,
    private val permissionRepository: PermissionRepository,
    private val scanSessionRepository: ScanSessionRepository,
    private val downloadAccessRepository: DownloadAccessRepository,
    private val safScanner: SafScanner,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScanningUiState())
    val uiState: StateFlow<ScanningUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    init {
        startScan()
    }

    fun onEvent(event: ScanningUiEvent) {
        when (event) {
            ScanningUiEvent.StopScan -> stopScan()
            ScanningUiEvent.RetryScan -> startScan()
            ScanningUiEvent.ViewResults -> Unit
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        _uiState.value = ScanningUiState(isScanning = true)
        scanJob = viewModelScope.launch {
            try {
                val hasMediaAccess = permissionRepository.getMediaPermissionStatus() != MediaPermissionStatus.NONE
                val treeUri = downloadAccessRepository.persistedTreeUri()
                if (!hasMediaAccess && treeUri == null) {
                    _uiState.value = ScanningUiState(
                        stage = "需要扫描范围授权",
                        statusMessage = "返回首页授权媒体范围，或主动选择 Download 目录后再开始扫描。",
                        requiresPermission = true,
                    )
                    scanJob = null
                    return@launch
                }

                val scannedFiles = mutableListOf<ScannedFile>()
                var scannedBytes = 0L
                var errorCount = 0
                var mediaFileCount = 0L
                var mediaBytes = 0L

                if (hasMediaAccess) {
                    scanMedia().collect { event ->
                        when (event) {
                            is MediaScanEvent.Progress -> {
                                val progress = event.value
                                _uiState.update { state ->
                                    state.copy(
                                        stage = progress.stage.displayName(),
                                        statusMessage = "仅读取系统媒体索引，不读取媒体文件内容。",
                                        progress = progress.fraction * if (treeUri == null) 0.85f else 0.65f,
                                        scannedFileCount = progress.scannedFileCount,
                                        scannedBytes = progress.scannedBytes,
                                        errorCount = progress.errorCount,
                                        isScanning = true,
                                        isComplete = false,
                                        failureMessage = null,
                                    )
                                }
                            }

                            is MediaScanEvent.Completed -> {
                                val mediaFiles = event.result.items.map { it.toScannedFile() }
                                scannedFiles += mediaFiles
                                mediaFileCount = mediaFiles.size.toLong()
                                mediaBytes = event.result.totalBytes
                                scannedBytes = mediaBytes
                                errorCount = event.result.errors.size
                            }
                        }
                    }
                }

                if (treeUri != null) {
                    safScanner.scan(treeUri).collect { event ->
                        when (event) {
                            is SafScanEvent.Progress -> {
                                val progress = event.value
                                _uiState.update {
                                    it.copy(
                                        stage = "正在扫描已授权下载目录",
                                        statusMessage = "扫描范围仅限你在系统选择器中明确授权的目录。",
                                        progress = if (hasMediaAccess) 0.78f else 0.72f,
                                        scannedFileCount = mediaFileCount + progress.scannedFileCount,
                                        scannedBytes = saturatingAdd(mediaBytes, progress.scannedBytes),
                                        errorCount = errorCount + progress.errorCount,
                                        isScanning = true,
                                        isComplete = false,
                                    )
                                }
                            }

                            is SafScanEvent.Completed -> {
                                scannedFiles += event.result.items
                                scannedBytes = saturatingAdd(mediaBytes, event.result.totalBytes)
                                errorCount += event.result.errors.size
                            }
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        stage = "正在应用安全规则",
                        statusMessage = "正在逐项执行本地安全判断，不会删除或修改任何文件。",
                        progress = 0.92f,
                        scannedFileCount = scannedFiles.size.toLong(),
                        scannedBytes = scannedBytes,
                        errorCount = errorCount,
                    )
                }
                val threshold = LargeFileThreshold.DEFAULT
                val classifiedResult = applySafetyRules(scannedFiles, threshold)
                if (classifiedResult.isFailure) {
                    _uiState.update {
                        it.copy(
                            stage = "安全规则暂时无法使用",
                            statusMessage = "本地安全规则未能加载，扫描结果不会进入后续处理。",
                            isScanning = false,
                            isComplete = false,
                            failureMessage = "安全规则加载失败",
                        )
                    }
                    return@launch
                }
                val classifiedItems = classifiedResult.getOrThrow()
                scanSessionRepository.replaceScanSession(
                    scannedFiles = scannedFiles,
                    analyzedItems = classifiedItems,
                    threshold = threshold,
                )
                val largeFileBytes = classifiedItems
                    .filter { it.category == FileCategory.LARGE_FILE }
                    .sumOf { it.sizeBytes }
                _uiState.update {
                    it.copy(
                        stage = "安全规则判断完成",
                        statusMessage = if (errorCount == 0) {
                            "已对授权范围内的项目完成本地安全判断。"
                        } else {
                            "已完成安全判断，$errorCount 项读取异常已跳过。"
                        },
                        progress = 1f,
                        scannedFileCount = scannedFiles.size.toLong(),
                        scannedBytes = scannedBytes,
                        classifiedFileCount = classifiedItems.size.toLong(),
                        largeFileBytes = largeFileBytes,
                        errorCount = errorCount,
                        isScanning = false,
                        isComplete = true,
                    )
                }
                if (currentCoroutineContext().job === scanJob) {
                    scanJob = null
                }
            } catch (cancelled: CancellationException) {
                if (currentCoroutineContext().job === scanJob) {
                    scanJob = null
                    _uiState.update {
                        it.copy(
                            stage = "扫描已停止",
                            statusMessage = "扫描已停止；未完成的数据不会进入后续处理。",
                            isScanning = false,
                            isComplete = false,
                            isStopped = true,
                        )
                    }
                }
                throw cancelled
            } catch (_: Exception) {
                if (currentCoroutineContext().job === scanJob) {
                    scanJob = null
                    _uiState.update {
                        it.copy(
                            stage = "扫描暂时无法完成",
                            statusMessage = "授权范围暂时无法读取，请检查权限后重试。",
                            isScanning = false,
                            isComplete = false,
                            failureMessage = "扫描失败",
                        )
                    }
                }
            }
        }
    }

    private fun stopScan() {
        val activeJob = scanJob ?: return
        scanJob = null
        activeJob.cancel(CancellationException("用户停止媒体扫描"))
        _uiState.update {
            it.copy(
                stage = "扫描已停止",
                statusMessage = "扫描已停止；未完成的数据不会进入后续处理。",
                isScanning = false,
                isComplete = false,
                isStopped = true,
            )
        }
    }
}

private fun saturatingAdd(left: Long, right: Long): Long =
    if (right > 0L && left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private fun MediaScanStage.displayName(): String = when (this) {
    MediaScanStage.PREPARING -> "正在准备媒体索引"
    MediaScanStage.IMAGES -> "正在扫描照片"
    MediaScanStage.VIDEOS -> "正在扫描视频"
    MediaScanStage.AUDIO -> "正在扫描音频"
    MediaScanStage.COMPLETE -> "正在准备安全判断"
}
