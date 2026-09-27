package com.cleanguard.app.domain.duplicate

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow

interface DuplicateVerificationEngine {
    suspend fun verify(files: List<DuplicateFile>): DuplicateVerificationResult
}

interface DuplicateFileHasher {
    suspend fun quickHash(file: DuplicateFile): String
    suspend fun fullHash(file: DuplicateFile): String
}

interface DuplicateSessionRepository {
    val groups: StateFlow<List<DuplicateGroup>>

    suspend fun replaceGroups(groups: List<DuplicateGroup>)
    fun findGroup(groupId: String): DuplicateGroup?
    fun stageCleanupSelection(itemIds: Set<String>)
    fun consumeCleanupSelection(): Set<String>
}

object NoOpDuplicateVerificationEngine : DuplicateVerificationEngine {
    override suspend fun verify(files: List<DuplicateFile>) = DuplicateVerificationResult(emptyList())
}

object NoOpDuplicateSessionRepository : DuplicateSessionRepository {
    override val groups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    override suspend fun replaceGroups(groups: List<DuplicateGroup>) = Unit
    override fun findGroup(groupId: String): DuplicateGroup? = null
    override fun stageCleanupSelection(itemIds: Set<String>) = Unit
    override fun consumeCleanupSelection(): Set<String> = emptySet()
}
