# 《CleanGuard V0.2 Phase 4 Report》

## 1. Phase / 完成状态

- Phase：V0.2 Phase 4 — Duplicate Verification V2
- 完成状态：完成
- 当前分支：`feature/v0.2`
- Phase 3 基线提交：`8d2c9e2`
- 结论：`READY FOR PHASE 5`

## 2. 实现内容

- 新增独立的重复文件领域模型：`DuplicateFile`、`DuplicateGroup`、`DuplicateVerificationStatus`、`DuplicateVerificationResult`。
- 实现严格的五阶段确认流程：
  1. 按 `sizeBytes` 分组。
  2. 计算 `size + first 64KB + last 64KB` 的 QuickHash。
  3. 仅保留 QuickHash 相同的候选组。
  4. 仅在进入重复文件页面后计算完整 SHA-256。
  5. 仅将完整 SHA-256 完全相同且至少两个文件的组标记为 `CONFIRMED_DUPLICATE`。
- QuickHash 与 FullHash 全程使用流式读取，未调用 `readBytes()`，未将完整视频或文件加载进内存。
- Full SHA-256 由共享 Semaphore 限制，生产实现最大并发固定为 2。
- 文件读取失败只记录单项 issue，不中断其他候选文件确认。
- 使用现有 `HashCacheEntity`，缓存按 `contentUri + sizeBytes + modifiedAt` 命中；大小或修改时间变化时先失效旧记录。
- 新增 `DuplicateGroupsScreen`：显示缩略图、文件数量、总占用、理论可释放空间和 SHA-256 确认状态。
- 新增 `DuplicateDetailScreen`：显示文件名、位置、大小、修改日期、预览入口、保留提示和人工复选框。
- 重复文件详情默认没有选择任何文件；“建议保留最新/最早”仅为视觉提示，不会修改 checkbox。
- 用户手动选择后仍进入现有安全清理复核流程，继续经过 CleanupPlan、应用确认和 Android 系统 Trash 确认。
- `CONFIRMED_DUPLICATE` 会产生 HIGH Recommendation，但不会修改 `RiskLevel`。

## 3. 安全边界

- 仅 `RiskLevel.REVIEW` 条目参与重复确认；DANGER、UNKNOWN 不会被提升为确认重复。
- `DuplicateVerificationEngine` 不依赖或调用 `CleanupRepository`、`ContentResolver.delete()` 或 Trash API。
- 重复确认只比较内容，不提供自动删除。
- 没有“自动选择旧文件”“自动删除除最新文件外其他文件”逻辑。
- 所有删除资格仍由现有 SafetyRuleEngine 与 CleanupPlan 决定。
- `SafetyRuleEngine.kt` 与 `MediaStoreCleanupRepository.kt` 均未修改。

## 4. 新增文件

- `app/src/main/java/com/cleanguard/app/domain/duplicate/DuplicateModels.kt`
- `app/src/main/java/com/cleanguard/app/domain/duplicate/DuplicateVerification.kt`
- `app/src/main/java/com/cleanguard/app/data/duplicate/StreamHashCalculator.kt`
- `app/src/main/java/com/cleanguard/app/data/duplicate/CachingDuplicateFileHasher.kt`
- `app/src/main/java/com/cleanguard/app/data/duplicate/DuplicateVerificationEngineImpl.kt`
- `app/src/main/java/com/cleanguard/app/data/duplicate/CurrentDuplicateSessionRepository.kt`
- `app/src/main/java/com/cleanguard/app/feature/duplicates/DuplicateGroupsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/duplicates/DuplicateGroupsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/duplicates/DuplicateGroupsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/duplicates/DuplicateDetailViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/duplicates/DuplicateDetailScreen.kt`
- `app/src/test/java/com/cleanguard/app/data/duplicate/StreamHashCalculatorTest.kt`
- `app/src/test/java/com/cleanguard/app/data/duplicate/CachingDuplicateFileHasherTest.kt`
- `app/src/test/java/com/cleanguard/app/data/duplicate/DuplicateVerificationEngineImplTest.kt`
- `docs/CleanGuard-V0.2-Phase-4-Report.md`

## 5. 修改范围

- AppContainer：增加重复确认 Engine 与当前重复会话 Repository 的 Application 生命周期 lazy provider。
- Scan session：`ScannedFile` 增加可空 `contentUri`；MediaStore 与 SAF 扫描结果保留可读取 URI。
- Navigation：增加 DuplicateGroups 与 DuplicateDetail 两个目的地。
- ScanResults：增加“核对重复文件”入口。
- CleanupDetail：允许接收用户在重复详情页手动选择的条目，并继续使用原有安全清理流程。
- RecommendationEngine：接受“已确认重复 ID”作为只读输入，产生 HIGH Recommendation，接口仍不能修改风险等级。
- strings.xml：增加 Phase 4 新 UI 文案。
- 既有导航、Recommendation 与 API 36 UI 测试增加 Phase 4 覆盖。

## 6. 数据库与权限变化

- Room Database Version：仍为 `1`。
- Database schema：无变化。
- 未新增 Entity、DAO 或 Migration；复用 Phase 1 已建立的 `HashCacheEntity` / `HashCacheDao`。
- Gradle dependencies：无变化。
- AndroidManifest：无变化。
- 未新增 `INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE` 或 `MANAGE_MEDIA`。
- 未新增 Accessibility Service、Root、Shizuku、ADB、Shell 或后台扫描配置。

## 7. 新增测试

- JVM 单元测试：12 个。
  - QuickHash 首尾 64KB 与 size 输入验证。
  - Full SHA-256 正确性。
  - Hash 缓存命中与文件版本变化失效。
  - 大小不同、QuickHash 不同、FullHash 不同、完整 Hash 相同等分组路径。
  - DANGER / UNKNOWN 不参与确认。
  - 损坏文件不中断任务。
  - Full SHA-256 并发不超过 2。
  - CONFIRMED_DUPLICATE Recommendation 为 HIGH 且 RiskLevel 保持 REVIEW。
- API 36 Compose 仪器测试：1 个。
  - 已确认重复组内所有复选框默认未选择，继续按钮默认不可用。

## 8. 全量测试结果

- `testDebugUnitTest`：80 passed，0 failed，0 skipped。
- `testReleaseUnitTest`：80 passed，0 failed，0 skipped。
- API 36 `connectedDebugAndroidTest`：21 passed，0 failed，0 skipped。
- Debug APK 冷启动：成功；`MainActivity` 为 `COLD` 启动，状态 `ok`，进程存活，无 AndroidRuntime crash。

## 9. Build / Lint

- `test`：成功。
- `lintDebug`：成功，0 errors，5 warnings。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`：成功。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 10. Warnings

- 5 个 Lint warning 均为 `NewerVersionAvailable` 版本建议，没有阻塞项。
- `android.overridePathCheck=true` 仍为当前中文工作区路径使用的实验性选项。
- Android SDK XML 工具版本存在非阻塞提示。
- 受限执行环境无法写入用户 AppData 下的 Kotlin daemon 临时文件；Gradle 自动回退为非 daemon 编译并成功。
- 本机仍缺少 LibreOffice，任务书 DOCX 无法生成 PNG 做视觉渲染；Phase 4 原文已通过 DOCX 结构完整提取并逐条核对。

## 11. V0.1 / Safety 回归

- V0.1 Safety、扫描、Trash、Undo、系统取消等既有单元测试全部通过。
- REVIEW 默认不选中。
- DANGER 与 UNKNOWN 仍不可进入处理计划。
- 媒体处理仍使用 `MediaStore.createTrashRequest()`，没有默认改用直接删除。
- Manifest 权限没有扩大。

## 12. 已知问题

- Phase 4 无阻塞问题。
- 缩略图依赖系统 `ContentResolver.loadThumbnail()`；无法提供缩略图时显示中性文件占位，不影响 Hash 确认。
- “查看位置”在本阶段以相对路径/来源文字呈现；系统没有提供跨所有媒体提供者统一可靠的“打开所在文件夹”能力。
- Full SHA-256 仅在用户进入重复文件页时运行，超大候选集可能需要等待；Phase 10 将继续进行 Hash 性能与取消响应测试。

## 13. 下一 Phase 风险

- Phase 5 APK Analyzer 不得增加 `QUERY_ALL_PACKAGES`，包信息读取必须容忍系统限制与损坏 APK。
- Download Insights 必须保持 SAF 用户授权边界，不得增加全盘文件权限或自动删除。
- APK/Download 建议仍必须与 SafetyRuleEngine 分离，推荐优先级不得替代删除安全等级。

## 14. Git Diff 摘要

- 新增重复确认 Domain / Data / UI 分层与对应测试。
- 新增 2 个导航页面。
- 复用现有 Room Hash cache，无 Schema 变化。
- 无 Gradle 依赖、Manifest、SafetyRuleEngine 或 CleanupRepository 实现变化。
- 未实现任何 Phase 5 功能。

`READY FOR PHASE 5`
