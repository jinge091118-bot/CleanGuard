# CleanGuard V0.2 Phase 6 Report

## 1. Phase / 完成状态

- Phase：V0.2 Phase 6 — App Storage History
- 完成状态：完成
- 验收结论：`READY FOR PHASE 7`
- 当前分支：`feature/v0.2`
- Phase 6 起始提交：`159d923`
- V0.1 基线标签：`v0.1.0-rc1`

## 2. 实现范围

- 继续使用 V0.1 的 `StorageStatsManager` 读取能力和 Usage Access 显式授权流程。
- 每次成功读取 StorageStats 后，将所有可查询应用作为同一批 `AppStorageSnapshot` 持久化。
- 写入当前批次前读取最近一次成功批次，并按 `packageName` 比较：
  - 总占用增加；
  - 总占用减少；
  - 总占用不变；
  - 缓存占用增加或减少；
  - 上一批没有该应用时不伪造增长值。
- 首次成功读取只保存基线；从第二次成功读取开始显示变化。
- UI 明确使用“占用增加/减少”和“缓存占用增加/减少”，没有把变化描述为可清理内容。
- 应用操作仍只有“打开系统应用设置”；没有清理、删除或自动清缓存能力。
- 没有实现 Phase 7 的 Scan History、趋势图、首页增量信息或历史 pruning。

## 3. 持久化设计

- Room Database Version：仍为 `1`。
- `AppStorageSnapshotEntity`：结构不变，继续保存 `packageName`、`appName`、`timestamp`、`appBytes`、`dataBytes`、`cacheBytes`、`totalBytes`。
- `AppStorageHistoryDao` 新增：
  - 批量插入；
  - 查询最新批次时间戳；
  - 查询指定时间戳的完整批次。
- `RoomAppStorageHistoryRepository.recordSuccessfulScan()` 使用 `database.withTransaction`：
  1. 读取上一批时间戳和快照；
  2. 规范化本批时间戳；
  3. 去除同批重复 package；
  4. 批量写入本批快照；
  5. 返回上一批用于比较。
- 如果数据库写入失败，事务回滚，本次不会成为成功历史。
- 为连续快速扫描使用单调递增批次时间戳，避免同毫秒批次混合。
- Schema 文件无变化，无 Migration，无 destructive migration。

## 4. 修改文件

- `app/src/main/java/com/cleanguard/app/data/local/dao/AppStorageHistoryDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/repository/RoomPersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/di/AppContainer.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/AppStorageItem.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/persistence/PersistenceModels.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/PersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/domain/usecase/LoadAppStorageUseCase.kt`
- `app/src/main/java/com/cleanguard/app/feature/insights/StorageInsightsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/insights/StorageInsightsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/insights/StorageInsightsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardViewModelFactory.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/domain/usecase/LoadAppStorageUseCaseTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/data/local/CleanGuardDatabaseTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/Phase10UiTest.kt`

## 5. 新增文件

- `docs/CleanGuard-V0.2-Phase-6-Report.md`
- 本阶段没有新增生产源文件；在已有 App Storage / Room 架构上做最小扩展。

## 6. 数据库变化

- 数据库版本：无变化，仍为 Version 1。
- Entity / 表 / 索引 /唯一约束：无变化。
- Schema export：无变化。
- DAO 与 Repository 接口增加批次读取和事务化写入能力。
- 未启用 `fallbackToDestructiveMigration()`。

## 7. 权限变化

- `AndroidManifest.xml`：Phase 6 无 diff。
- 未新增 runtime permission。
- 继续使用已有 `PACKAGE_USAGE_STATS` 特殊访问入口，由用户在系统设置中明确授权。
- 不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku、ADB/Shell/`Runtime.exec()`。

## 8. 测试结果

- 新增测试用例：5。
  - JVM：成功扫描持久化、上一批增长/减少比较、失败不写历史、取消继续抛出等覆盖增加 3 个测试。
  - Room instrumented：完整批次写入、上一批返回、连续时间戳规范化增加 1 个测试。
  - Compose instrumented：增长、减少、缓存文案、无清理承诺、系统设置入口增加 1 个测试。
- Debug JVM 单元测试：93/93 通过，0 failed，0 errors，0 skipped。
- Release JVM 单元测试：93/93 通过，0 failed，0 errors，0 skipped。
- API 36 instrumented tests：25/25 通过，0 failed，0 errors，0 skipped。
- V0.1 SafetyRuleEngine 与既有回归测试全部继续通过。

## 9. Build 结果

- `test`：成功。
- `lintDebug`：成功。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（API 36）：成功。
- Debug APK 安装：成功。
- Debug APK 冷启动：成功，Activity 为 `com.cleanguard.app/.MainActivity`。
- 冷启动后进程存活，AndroidRuntime fatal entries：0。
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- APK 大小：33,557,227 bytes。
- APK SHA-256：`909A1054FE417388126580114C14620B5F655A588ACB61F4D23D9AD71104D912`

## 10. Warnings

- Android Lint：0 Error，0 Warning。
- 构建环境仍提示 `android.overridePathCheck=true` 为实验选项。
- Android Studio 与 command-line tools 存在 SDK XML 版本提示。
- 工作区沙箱不允许 Kotlin daemon 写入用户 AppData 标记文件；Gradle 自动回退为非 daemon 编译并成功，不影响测试和产物。

## 11. Safety 检查

- `SafetyRuleEngine`：无 diff。
- `CleanupRepository` / MediaStore Trash：无 diff。
- DANGER 与 UNKNOWN 仍不可清理。
- REVIEW 仍默认不选中。
- Recommendation 不改变 RiskLevel。
- 没有新增删除、自动清缓存、自动卸载或后台自动扫描。
- App 占用变化不被解释为清理资格。
- 缓存占用增加只显示事实变化，不显示“可安全清理”。
- CancellationException 继续抛出，不吞掉协程取消。

## 12. 已知问题

- 受 Android 包可见性约束，只统计系统允许查询且可从桌面启动的应用；单个应用统计失败时沿用既有跳过计数。
- 需要用户授予 Usage Access；未授权时不读取或保存 App Storage 历史。
- 当前历史在用户进入空间洞察并成功完成 StorageStats 读取时记录，不增加后台自动扫描。
- 成功结果为空时没有可持久化的 App 快照，因此不会建立空批次。

## 13. 下一 Phase 风险

- Phase 7 若实现 Scan History，应使用现有 `ScanSnapshot`，不得把 App Storage 批次误当作媒体扫描历史。
- 趋势图必须使用 Compose 自绘，不引入大型图表 SDK。
- 历史 pruning 应遵守现有 10/30/90/Unlimited 策略并保持事务安全。
- 首页增量信息必须基于成功快照，FAILED/CANCELLED 不得覆盖上一次成功结果。
- Phase 7 不得扩大权限或改变清理安全策略。

## 14. Git diff 摘要

- 15 个已有源文件/测试文件修改。
- 1 个 Phase 报告新增。
- Room Schema、Manifest、SafetyRuleEngine、CleanupRepository 无变化。
- 无 Gradle dependency 变化。

`READY FOR PHASE 7`
