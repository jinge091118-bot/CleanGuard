package com.cleanguard.app.domain.repository

import com.cleanguard.app.domain.rules.SafetyRule

interface SafetyRuleRepository {
    suspend fun loadRules(): Result<List<SafetyRule>>
}
