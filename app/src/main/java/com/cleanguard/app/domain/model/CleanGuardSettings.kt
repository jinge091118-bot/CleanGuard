package com.cleanguard.app.domain.model

import com.cleanguard.app.domain.model.persistence.ScanHistoryRetentionPolicy

data class CleanGuardSettings(
    val largeFileThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    val historyRetentionPolicy: ScanHistoryRetentionPolicy = ScanHistoryRetentionPolicy.DEFAULT,
)
