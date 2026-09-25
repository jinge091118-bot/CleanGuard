# 《CleanGuard V0.2 Phase 3 Report》

## 1. Phase / 完成状态

- Phase：V0.2 Phase 3 — Recommendation Engine
- 完成状态：完成
- 当前分支：`feature/v0.2`
- Phase 2 基线提交：`5dd18fe`
- 结论：`READY FOR PHASE 4`

## 2. 实现内容

- 新增独立的 `RecommendationEngine`、`Recommendation`、`RecommendationPriority` 与 `RecommendationReason`。
- Recommendation 只包含项目 ID、优先级、原因和解释，不包含可修改的 `RiskLevel`。
- 实现首版推荐规则：
  - 30 天以上 APK：HIGH。
  - 180 天以上 Downloads：HIGH。
  - 90～180 天 Downloads：MEDIUM。
  - 90 天以上且超过 500 MB 的录屏：HIGH。
  - 180 天以上截图：MEDIUM。
  - 1 GB 及以上大文件：HIGH。
  - 100 MB～1 GB 大文件：MEDIUM。
  - 最近 7 天文件：LOW。
- 多条规则同时命中时保留全部原因，并按最高优先级排序。
- 新增截图/录屏识别：依据 `relativePath` 所表达的目录/bucket 特征、MIME 类型与原媒体分类判断；不依赖文件名。
- 新增 `FileCategory.SCREENSHOT` 与 `FileCategory.SCREEN_RECORDING`，两者继续由现有安全规则裁定为 REVIEW。
- ScanResults 增加“值得看看”摘要，显示 HIGH/MEDIUM/LOW 数量与候选空间。
- 清理建议列表按推荐优先级排序，并将“建议优先级”和 RiskLevel 分开展示。
- 新增生产可见文案均放入 `strings.xml`；推荐解释通过 Android 资源生成。

## 3. 明确未实现的后续范围

- 未实现 Hash cache、QuickHash、Full SHA-256、重复文件确认或重复文件 UI（Phase 4）。
- 未实现 APK 包信息读取、已安装版本比较或 Download 洞察 UI（Phase 5）。
- 未实现 App 增长分析（Phase 6）。
- 未实现 Ignore UI/设置、扫描历史或恢复中心。
- 未增加任何删除能力。

## 4. 新增文件

- `app/src/main/java/com/cleanguard/app/data/recommendation/RecommendationEngineImpl.kt`
- `app/src/main/java/com/cleanguard/app/data/recommendation/AndroidRecommendationExplanationProvider.kt`
- `app/src/main/java/com/cleanguard/app/domain/recommendation/Recommendation.kt`
- `app/src/main/java/com/cleanguard/app/domain/recommendation/RecommendationEngine.kt`
- `app/src/main/java/com/cleanguard/app/domain/recommendation/MediaCategoryClassifier.kt`
- `app/src/main/java/com/cleanguard/app/ui/util/RecommendationFormatters.kt`
- `app/src/test/java/com/cleanguard/app/data/recommendation/RecommendationEngineImplTest.kt`
- `app/src/test/java/com/cleanguard/app/domain/recommendation/MediaCategoryClassifierTest.kt`
- `docs/CleanGuard-V0.2-Phase-3-Report.md`

## 5. 修改文件

- `app/src/androidTest/java/com/cleanguard/app/Phase10UiTest.kt`
- `app/src/main/assets/safety_rules_v1.json`
- `app/src/main/java/com/cleanguard/app/di/AppContainer.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/FileCategory.kt`
- `app/src/main/java/com/cleanguard/app/domain/usecase/ApplySafetyRulesUseCase.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanupdetail/CleanupDetailScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanupdetail/CleanupDetailUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanupdetail/CleanupDetailViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardViewModelFactory.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/domain/usecase/ApplySafetyRulesUseCaseTest.kt`
- `app/src/test/java/com/cleanguard/app/feature/results/ScanResultsViewModelTest.kt`

## 6. 数据库与权限变化

- 数据库变化：无。
- Room Database Version：仍为 `1`。
- Schema 文件：无变化；工作区与 Phase 2 的 Git blob 均为 `e22f9a64554b3a485c3552d6b7283a7f849fd603`。
- Manifest 权限变化：无。
- 未新增 `INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE` 或 `MANAGE_MEDIA`。
- 未新增 Accessibility Service、Root、Shizuku、ADB、Shell 或后台扫描配置。

## 7. 测试结果

### 新增测试

- JVM 单元测试：16 个。
  - Recommendation 各日期/大小边界、优先级合并与排序。
  - HIGH 推荐不会修改 REVIEW/DANGER/UNKNOWN 风险等级。
  - 截图、录屏、中文目录、MIME 不匹配与“非文件名识别”测试。
  - 截图/录屏分类后仍由 SafetyRuleEngine 得到 REVIEW。
  - ScanResultsViewModel 输出 HIGH 推荐但保留 REVIEW。
- API 36 Compose 仪器测试：1 个。
  - 推荐优先级与 REVIEW 风险在 UI 中分开展示，REVIEW 默认仍未选中。

### 全量结果

- `testDebugUnitTest`：68 passed，0 failed，0 skipped。
- `testReleaseUnitTest`：68 passed，0 failed，0 skipped。
- API 36 `connectedDebugAndroidTest`：20 passed，0 failed，0 skipped。
- Debug APK 冷启动：成功；`MainActivity` 状态为 `ok`，无 AndroidRuntime crash。

## 8. Build / Lint

- `test`：成功。
- `lintDebug`：成功，0 errors，13 warnings。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`：成功。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 9. Warnings

- 13 个 Lint warning 均为既有版本建议：7 个 `GradleDependency`、5 个 `NewerVersionAvailable`、1 个 `AndroidGradlePluginVersion`。
- `android.overridePathCheck=true` 仍为实验性选项，用于当前含中文的工作区路径。
- Android SDK XML 工具版本存在非阻塞提示。
- 受限执行环境无法写入用户 AppData 下的 Kotlin daemon 临时文件；Gradle 自动回退到非 daemon 编译并成功。
- 本机缺少 LibreOffice，任务书 DOCX 未能生成 PNG 做视觉渲染；Phase 3 原文已通过 DOCX 结构完整提取并核对。

## 10. Safety 检查

- `SafetyRuleEngine.kt`：无修改。
- `MediaStoreCleanupRepository.kt`：无修改。
- RecommendationEngine 不依赖 CleanupRepository、ContentResolver 或 Trash API。
- RecommendationEngine 不返回 SAFE，且没有修改 RiskLevel 的接口。
- 新增截图/录屏分类仍为 REVIEW。
- HIGH 仅表示“值得优先查看”，不改变清理资格。
- REVIEW 仍默认不选中。
- DANGER 与 UNKNOWN 仍不可进入处理计划。
- 原有 MediaStore Trash、Undo 与系统取消流程未变化。

## 11. 已知问题

- Phase 3 无阻塞问题。
- APK 推荐仅基于已识别 APK 的日期形成候选；包名、版本和已安装版本比较按任务书留到 Phase 5。
- `CONFIRMED_DUPLICATE`、`APP_STORAGE_GROWTH`、`USER_IGNORED` 已作为前向兼容原因枚举保留，但本阶段不会产生这些原因。

## 12. 下一 Phase 风险

- Phase 4 读取文件内容计算 Hash 时必须使用流式读取，禁止 `readBytes()`，完整 SHA-256 最大并发为 2。
- Hash cache 必须以 URI、sizeBytes、modifiedAt 校验文件版本并正确失效。
- 只有完整 SHA-256 一致才能标记确认重复；确认重复仍为 REVIEW，且 UI 默认不得选中任何待删除文件。
- Phase 4 不得将 RecommendationPriority 误用为删除安全等级。

## 13. Git Diff 摘要

- 15 个现有文件修改。
- 8 个实现/测试文件新增。
- 1 个 Phase Report 新增。
- 无 Gradle 依赖、Manifest、Room schema、SafetyRuleEngine 或 CleanupRepository 修改。

`READY FOR PHASE 4`
