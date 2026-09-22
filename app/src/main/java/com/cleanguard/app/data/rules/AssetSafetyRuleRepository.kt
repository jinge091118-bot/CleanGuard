package com.cleanguard.app.data.rules

import android.content.Context
import com.cleanguard.app.domain.repository.SafetyRuleRepository
import com.cleanguard.app.domain.rules.SafetyRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException

class AssetSafetyRuleRepository(
    private val context: Context,
    private val parser: SafetyRulesJsonParser = SafetyRulesJsonParser(),
) : SafetyRuleRepository {
    override suspend fun loadRules(): Result<List<SafetyRule>> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            Result.success(parser.parse(json))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: JSONException) {
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Result.failure(error)
        }
    }

    private companion object {
        const val ASSET_NAME = "safety_rules_v1.json"
    }
}
