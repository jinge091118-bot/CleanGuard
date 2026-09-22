package com.cleanguard.app.data.rules

import com.cleanguard.app.domain.model.CleanupAction
import com.cleanguard.app.domain.model.FileCategory
import com.cleanguard.app.domain.model.RiskLevel
import com.cleanguard.app.domain.model.ScanSource
import com.cleanguard.app.domain.rules.SafetyRule
import com.cleanguard.app.domain.rules.SafetyRuleMatcher
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class SafetyRulesJsonParser {
    fun parse(json: String): List<SafetyRule> {
        val root = JSONObject(json)
        require(root.getInt("version") == SUPPORTED_VERSION) {
            "Unsupported safety rule version"
        }

        val rulesJson = root.getJSONArray("rules")
        require(rulesJson.length() > 0) { "Safety rule list must not be empty" }
        return List(rulesJson.length()) { index -> parseRule(rulesJson.getJSONObject(index)) }
    }

    private fun parseRule(json: JSONObject): SafetyRule {
        val actions = json.getJSONArray("allowedActions").enumSet<CleanupAction>()
        require(actions.isNotEmpty() && CleanupAction.NONE in actions) {
            "Every safety rule must allow NONE"
        }
        return SafetyRule(
            id = json.getString("id").also { require(it.isNotBlank()) },
            riskLevel = enumValueOf(json.getString("riskLevel")),
            reason = json.getString("reason").also { require(it.isNotBlank()) },
            deletionImpact = json.getString("deletionImpact").also { require(it.isNotBlank()) },
            allowedActions = actions,
            matcher = parseMatcher(json.getJSONObject("matcher")),
        )
    }

    private fun parseMatcher(json: JSONObject) = SafetyRuleMatcher(
        sources = json.optArray("sources").enumSet(),
        categories = json.optArray("categories").enumSet(),
        mimeTypes = json.optArray("mimeTypes").stringSet { it.lowercase(Locale.ROOT) },
        mimeTypePrefixes = json.optArray("mimeTypePrefixes").stringSet { it.lowercase(Locale.ROOT) },
        fileExtensions = json.optArray("fileExtensions").stringSet {
            it.removePrefix(".").lowercase(Locale.ROOT)
        },
        pathPrefixes = json.optArray("pathPrefixes").stringSet { it.normalizePath() },
        pathContains = json.optArray("pathContains").stringSet { it.normalizePath() },
        requiresAppOwnedRebuildable = if (json.has("requiresAppOwnedRebuildable")) {
            json.getBoolean("requiresAppOwnedRebuildable")
        } else {
            null
        },
    )

    private fun JSONObject.optArray(name: String): JSONArray? =
        if (has(name)) getJSONArray(name) else null

    private inline fun <reified T : Enum<T>> JSONArray?.enumSet(): Set<T> {
        if (this == null) return emptySet()
        return buildSet {
            repeat(length()) { index -> add(enumValueOf<T>(getString(index))) }
        }
    }

    private fun JSONArray?.stringSet(transform: (String) -> String = { it }): Set<String> {
        if (this == null) return emptySet()
        return buildSet {
            repeat(length()) { index -> add(transform(getString(index))) }
        }
    }

    private fun String.normalizePath(): String =
        replace('\\', '/').trimStart('/').lowercase(Locale.ROOT)

    private companion object {
        const val SUPPORTED_VERSION = 1
    }
}
