package com.cleanguard.app.data.duplicate

import com.cleanguard.app.domain.duplicate.DuplicateGroup
import com.cleanguard.app.domain.duplicate.DuplicateSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CurrentDuplicateSessionRepository : DuplicateSessionRepository {
    private val mutableGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    private var cleanupSelection: Set<String> = emptySet()

    override val groups: StateFlow<List<DuplicateGroup>> = mutableGroups.asStateFlow()

    override suspend fun replaceGroups(groups: List<DuplicateGroup>) {
        mutableGroups.value = groups.toList()
    }

    override fun findGroup(groupId: String): DuplicateGroup? =
        mutableGroups.value.firstOrNull { it.id == groupId }

    override fun stageCleanupSelection(itemIds: Set<String>) {
        cleanupSelection = itemIds.toSet()
    }

    override fun consumeCleanupSelection(): Set<String> = cleanupSelection.also {
        cleanupSelection = emptySet()
    }
}
