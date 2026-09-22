package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.model.DownloadAccessState
import kotlinx.coroutines.flow.StateFlow

interface DownloadAccessRepository {
    val accessState: StateFlow<DownloadAccessState>

    suspend fun refreshAccess(): DownloadAccessState

    suspend fun persistAccess(uri: String, resultFlags: Int): Result<Unit>

    suspend fun persistedTreeUri(): String?
}
