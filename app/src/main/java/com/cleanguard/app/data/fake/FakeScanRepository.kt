package com.cleanguard.app.data.fake

import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.model.ScanSummary
import com.cleanguard.app.domain.repository.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeScanRepository : ScanRepository {
    override val scanSummary: Flow<ScanSummary> = flowOf(FakeCleanGuardData.scanSummary)
    override val scanItems: Flow<List<ScanItem>> = flowOf(FakeCleanGuardData.scanItems)
    override val appStorageItems: Flow<List<AppStorageItem>> = flowOf(FakeCleanGuardData.appStorageItems)
}
