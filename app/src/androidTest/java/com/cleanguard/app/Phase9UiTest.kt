package com.cleanguard.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cleanguard.app.domain.model.MediaPermissionOverview
import com.cleanguard.app.domain.model.LargeFileThreshold
import com.cleanguard.app.domain.model.PermissionGrantState
import com.cleanguard.app.feature.permissions.PermissionCenterScreen
import com.cleanguard.app.feature.permissions.PermissionCenterUiState
import com.cleanguard.app.feature.settings.SettingsScreen
import com.cleanguard.app.feature.settings.SettingsUiState
import com.cleanguard.app.feature.settings.SettingsUiEvent
import com.cleanguard.app.ui.theme.CleanGuardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class Phase9UiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsShowsPrivacyAndNoAutomaticDuplicateCleanup() {
        composeRule.setContent {
            CleanGuardTheme {
                SettingsScreen(SettingsUiState(), {}, {}, {})
            }
        }

        composeRule.onNodeWithText("不提供自动清理重复文件，仍需你逐项确认。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("所有扫描均在本机完成。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "CleanGuard 不上传文件；除移入 Android 系统回收站与你主动使用系统分享/预览外，数据不会离开设备。",
        )
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("常规扫描只读取文件名、类型、大小、修改时间与目录，不读取媒体内容。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("核对重复文件时会在本机流式计算 SHA-256，不保存文件副本。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CleanGuard V0.2 不读取聊天内容。")
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("CleanGuard V0.2 不访问其他 App 私有目录。")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settingsLargeFileThreshold_500MbSelectionEmitsExplicitEvent() {
        val receivedEvent = AtomicReference<SettingsUiEvent?>()
        composeRule.setContent {
            CleanGuardTheme {
                SettingsScreen(
                    uiState = SettingsUiState(
                        largeFileThreshold = LargeFileThreshold.MB_500,
                    ),
                    onBack = {},
                    onOpenPermissionCenter = {},
                    onEvent = receivedEvent::set,
                )
            }
        }

        composeRule.onNodeWithText("500 MB").assertIsSelected().performClick()

        assertEquals(
            SettingsUiEvent.SelectLargeFileThreshold(LargeFileThreshold.MB_500),
            receivedEvent.get(),
        )
    }

    @Test
    fun permissionCenterExplainsPartialMediaScopeWithoutBlocking() {
        composeRule.setContent {
            CleanGuardTheme {
                PermissionCenterScreen(
                    state = PermissionCenterUiState(
                        media = MediaPermissionOverview(
                            photos = PermissionGrantState.PARTIAL,
                            videos = PermissionGrantState.PARTIAL,
                            audio = PermissionGrantState.NOT_AUTHORIZED,
                        ),
                    ),
                    onBack = {},
                    onRequestMedia = {},
                    onRequestDownloads = {},
                    onRequestUsage = {},
                )
            }
        }

        composeRule.onNodeWithText("当前仅分析你允许 CleanGuard 访问的照片和视频。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("媒体容量统计基于当前授权范围。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("权限不会阻止进入 CleanGuard。你可以只授予需要分析的范围。")
            .assertIsDisplayed()
    }
}
