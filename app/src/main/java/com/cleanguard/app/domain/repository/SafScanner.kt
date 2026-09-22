package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.SafScanEvent
import kotlinx.coroutines.flow.Flow

interface SafScanner {
    fun scan(treeUri: String): Flow<SafScanEvent>
}
