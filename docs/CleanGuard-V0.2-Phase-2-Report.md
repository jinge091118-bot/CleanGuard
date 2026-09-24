# 《CleanGuard V0.2 Phase 2 Report》

## 1. Phase / Status

- Phase：V0.2 Phase 2 — Incremental Scan Engine
- 状态：完成
- 当前分支：`feature/v0.2`
- Phase 1 基线提交：`fbd4b6a1f9f0727bebaa72eaf2c7fcc9b7422ee3`
- V0.1 基线标签：`v0.1.0-rc1`
- 结论：`READY FOR PHASE 3`

## 2. 实现范围

- Android 11（API 30）及以上：按 volume 保存 `MediaStore` version / generation。
- 首次扫描：执行完整扫描并建立 `IndexedItem` 索引。
- 后续扫描：version 未变化时仅查询 `GENERATION_MODIFIED > lastGeneration` 的新增或变更项。
- Version 变化：执行完整重新同步（`FULL_RESYNC`）。
- 删除识别：使用仅包含 `_ID` 的轻量查询与本地索引进行 reconciliation，不重新读取全部文件元数据。
- 多卷支持：不同 volume 独立维护同步状态；新增 SD volume 建立索引，移除 volume 清除对应索引和同步状态。
- Android 10（API 29）或设备不支持 Generation API：继续调用原有 `MediaStoreScanner` 完整扫描路径。
- 扫描状态：持久化 `STARTED / COMPLETED / CANCELLED / FAILED`；只有 `COMPLETED` 会在同一 Room 事务中推进成功基线。
- UI：扫描页与结果页显示完整/增量/重新同步类型、耗时、变化数量、移除数量和卷数量。

未实现 Phase 3 及后续内容：RecommendationEngine、重复文件检测、Hash 计算、App 增长分析、扫描历史 UI、Recovery Center UI、新的删除能力。

## 3. 修改文件

### 新增

- `app/src/main/java/com/cleanguard/app/data/media/IncrementalMediaStoreScanner.kt`
- `app/src/main/java/com/cleanguard/app/data/media/MediaStoreGateway.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/ScanRunInfo.kt`
- `app/src/main/java/com/cleanguard/app/ui/util/ScanFormatters.kt`
- `app/src/test/java/com/cleanguard/app/data/media/IncrementalMediaStoreScannerTest.kt`
- `docs/CleanGuard-V0.2-Phase-2-Report.md`

### 修改

- `app/src/androidTest/java/com/cleanguard/app/data/local/CleanGuardDatabaseTest.kt`
- `app/src/main/java/com/cleanguard/app/data/local/dao/IndexedItemDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/dao/MediaSyncStateDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/repository/RoomPersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/data/media/CurrentScanRepository.kt`
- `app/src/main/java/com/cleanguard/app/data/media/MediaStoreScanner.kt`
- `app/src/main/java/com/cleanguard/app/di/AppContainer.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/MediaScanProgress.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/MediaStoreItem.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/persistence/PersistenceModels.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/PersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/ScanSessionRepository.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanning/ScanningScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanning/ScanningUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanning/ScanningViewModel.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/feature/scanning/ScanningViewModelTest.kt`

## 4. 数据库与事务

- Database version：仍为 `1`。
- Schema：无结构变化；`app/schemas/com.cleanguard.app.data.local.CleanGuardDatabase/1.json` 与 Phase 1 完全一致。
- Schema Git blob：工作区与 `HEAD` 均为 `e22f9a64554b3a485c3552d6b7283a7f849fd603`。
- 未加入 `fallbackToDestructiveMigration()`。
- 新增 DAO 能力：按 volume / 全量读取索引、批量删除 stale ID、按 volume 删除、读取/删除 volume sync state。
- 批处理：索引 upsert 每批 250 条，SQLite ID 删除每批 500 条。
- 原子提交：索引 upsert、stale/removed volume 清理、sync state 更新与 `COMPLETED` snapshot 在同一 `withTransaction` 中完成。
- 失败或取消：只写入终态 snapshot，不推进 `lastGeneration` 或 version 基线；协程取消会继续向上抛出。

## 5. 权限与安全

- `AndroidManifest.xml` 相对 Phase 1：无差异。
- 未新增 runtime permission。
- 确认不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root / Shizuku 配置。
- 原有 `MediaStoreScanner` 未重写，仅为其结果补充 `contentUri` 与 `volumeName`，以便 API 29 fallback 建立持久索引。
- 原有 `CleanupRepository` 与删除/Trash/Undo 流程未修改。
- `SafetyRuleEngine` 行为未修改；DANGER、UNKNOWN 不可清理和 REVIEW 默认不选中的既有测试继续通过。

## 6. 测试

### 新增测试

- JVM 单元测试：10 个。
  - 首次完整扫描与无变化增量扫描
  - 新增媒体
  - 元数据修改与过期 Hash 失效
  - `_ID` reconciliation 删除识别
  - MediaStore version 变化完整重同步
  - API 29 fallback
  - 新增 SD volume / volume 间 ID 隔离
  - 移除 SD volume
  - 失败不推进基线
  - 取消不推进基线
- Room 仪器测试：1 个，验证多 volume reconciliation 与完成态原子提交。

### 全量结果

- `testDebugUnitTest`：52 passed，0 failed，0 skipped。
- `testReleaseUnitTest`：52 passed，0 failed，0 skipped。
- API 36 `connectedDebugAndroidTest`：19 passed，0 failed，0 skipped。
- Debug APK 冷启动：成功，`MainActivity` 启动状态为 `ok`，无 AndroidRuntime crash。

## 7. Lint / Build

- `lintDebug`：成功，0 errors，13 warnings。
- 13 个 Lint warning 均为既有工具链/依赖版本建议：7 个 `GradleDependency`、5 个 `NewerVersionAvailable`、1 个 `AndroidGradlePluginVersion`；无阻塞项。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（API 36）：成功。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

## 8. 已知 Warning

- `android.overridePathCheck=true` 为实验性 Gradle 选项，用于兼容当前含中文的工作区路径。
- 已安装 Android SDK command-line tools 与 AGP 的 SDK XML 解析版本存在提示差异，但不影响构建或测试。
- 当前受限执行环境无法写入用户 AppData 下的 Kotlin daemon 临时文件；Gradle 自动回退为非 daemon 编译并成功完成。
- 模拟器首次执行测试时需下载 UTP 依赖；依赖缓存后离线回归通过。

## 9. V0.1 行为变化

- 未改变清理安全判定、MediaStore Trash、Undo、系统取消处理或权限范围。
- 生产扫描入口由 `AppContainer` 组合为增量扫描器；API 29 与 Generation 不可用设备仍委托给原有完整扫描器。
- 扫描页面新增透明的扫描类型与变更统计展示，不改变清理选择规则。

## 10. Git Diff Summary

- 实现代码与测试：20 个已跟踪文件修改、5 个实现/测试文件新增。
- 报告：1 个 Markdown 文件新增。
- 未修改 Gradle 依赖或版本；沿用 Phase 1 的 Room / KSP 配置。
- 未修改 Manifest、Room schema、清理仓库或安全规则文件。

## 11. 问题与下一阶段风险

- Phase 2 未完成问题：无。
- 进入 Phase 3 的阻塞问题：无。
- Phase 3 风险：RecommendationEngine 应只消费稳定的 Domain/index 数据，不直接依赖 Room Entity；必须保留 UNKNOWN/DANGER 不可清理及 REVIEW 默认不选中约束。Hash 仍应留在任务书指定的后续 Phase，不应在 Phase 3 越界实现。

`READY FOR PHASE 3`
