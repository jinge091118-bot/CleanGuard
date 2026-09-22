package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.MediaScanEvent
import com.cleanguard.app.domain.repository.MediaScanner
import kotlinx.coroutines.flow.Flow

class ScanMediaUseCase(
    private val mediaScanner: MediaScanner,
) {
    operator fun invoke(): Flow<MediaScanEvent> = mediaScanner.scan()
}
