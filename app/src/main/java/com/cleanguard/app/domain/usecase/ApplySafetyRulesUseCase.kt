package com.cleanguard.app.domain.usecase

import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.ScannedFile
import com.cleanguard.app.domain.model.ScanItem
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.rules.SafetyRuleEngine
import com.cleanguard.app.domain.rules.SafetySubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ApplySafetyRulesUseCase(
    private val repository: SafetyRuleRepository,
    private val evaluationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val engineMutex = Mutex()
    private var cachedEngine: SafetyRuleEngine? = null

    suspend operator fun invoke(
        items: List<ScannedFile>,
        largeFileThreshold: LargeFileThreshold = LargeFileThreshold.DEFAULT,
    ): Result<List<ScanItem>> {
        val engineResult = loadEngine()
        if (engineResult.isFailure) return Result.failure(engineResult.exceptionOrNull()!!)
        val engine = engineResult.getOrThrow()

        return try {
            withContext(evaluationDispatcher) {
                Result.success(items.map { item -> item.toScanItem(engine, largeFileThreshold) })
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun loadEngine(): Result<SafetyRuleEngine> = engineMutex.withLock {
        cachedEngine?.let { return@withLock Result.success(it) }
        repository.loadRules()
            .mapCatching(::SafetyRuleEngine)
            .onSuccess { cachedEngine = it }
    }
}

private fun ScannedFile.toScanItem(
    engine: SafetyRuleEngine,
    largeFileThreshold: LargeFileThreshold,
): ScanItem {
    val analyzedCategory = if (sizeBytes >= largeFileThreshold.bytes) {
        FileCategory.LARGE_FILE
    } else {
        category
    }
    val decision = engine.evaluate(
        SafetySubject(
            id = id,
            displayName = displayName,
            mimeType = mimeType,
            relativePath = relativePath,
            source = source,
            category = analyzedCategory,
        ),
    )
    return ScanItem(
        id = id,
        displayName = displayName,
        sizeBytes = sizeBytes,
        modifiedAtEpochMillis = modifiedAtEpochMillis,
        source = source,
        category = analyzedCategory,
        mimeType = mimeType,
        relativePath = relativePath,
        safetyDecision = decision,
    )
}
