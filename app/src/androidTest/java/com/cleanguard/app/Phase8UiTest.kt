package com.cleanguard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanguard.app.domain.model.persistence.CleanupHistoryEntry
import com.cleanguard.app.domain.model.persistence.CleanupHistoryItem
import com.cleanguard.app.domain.model.persistence.CleanupHistoryType
import com.cleanguard.app.domain.model.persistence.CleanupRestoreStatus
import com.cleanguard.app.feature.cleanuphistory.CleanupHistoryScreen
import com.cleanguard.app.feature.cleanuphistory.CleanupHistoryUiState
import com.cleanguard.app.feature.recovery.RecoveryCenterScreen
import com.cleanguard.app.feature.recovery.RecoveryCenterUiState
import com.cleanguard.app.ui.theme.CleanGuardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase8UiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cleanupHistory_displaysPersistedSummaryWithoutRawUri() {
        composeRule.setContent {
            CleanGuardTheme {
                CleanupHistoryScreen(
                    uiState = CleanupHistoryUiState(
                        isLoading = false,
                        entries = listOf(entry(CleanupRestoreStatus.AVAILABLE)),
                    ),
                    onBack = {},
                    onOpenRecoveryCenter = {},
                )
            }
        }

        composeRule.onNodeWithText("请求 1 项 · 已处理 1 项 · 失败 0 项").assertIsDisplayed()
        composeRule.onNodeWithText("可请求恢复").assertIsDisplayed()
        composeRule.onAllNodesWithText("content://media/external/images/media/1")
            .assertCountEquals(0)
    }

    @Test
    fun recoveryCenter_disablesFailedRestoreAndUsesSystemRetentionWording() {
        composeRule.setContent {
            CleanGuardTheme {
                RecoveryCenterScreen(
                    uiState = RecoveryCenterUiState(
                        isLoading = false,
                        entries = listOf(entry(CleanupRestoreStatus.FAILED)),
                    ),
                    onBack = {},
                    onRestore = {},
                )
            }
        }

        composeRule.onNodeWithText("该项目已移动到系统垃圾箱；实际保留时间由系统决定。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("当前无法恢复").assertIsNotEnabled()
    }

    private fun entry(status: CleanupRestoreStatus) = CleanupHistoryEntry(
        cleanupId = "cleanup-1",
        timestamp = 1L,
        requestedItemCount = 1,
        processedItemCount = 1,
        failedItemCount = 0,
        requestedBytes = 100L,
        freedBytes = 0L,
        cleanupType = CleanupHistoryType.MEDIA_STORE_TRASH,
        usedMediaStoreTrash = true,
        restoreStatus = status,
        items = listOf(
            CleanupHistoryItem("media:image:1", "content://media/external/images/media/1"),
        ),
    )
}
