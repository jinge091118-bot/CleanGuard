package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.MediaScanEvent
import kotlinx.coroutines.flow.Flow

interface MediaScanner {
    fun scan(): Flow<MediaScanEvent>
}
