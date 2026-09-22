package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSummary
import kotlinx.coroutines.flow.Flow

interface ScanRepository {
    val scanSummary: Flow<ScanSummary>
    val scanItems: Flow<List<ScanItem>>
    val appStorageItems: Flow<List<AppStorageItem>>
}
