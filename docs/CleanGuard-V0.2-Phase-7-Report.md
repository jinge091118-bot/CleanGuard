# CleanGuard V0.2 Phase 7 Report

## 1. Phase / 完成状态

- Phase：V0.2 Phase 7 — Scan History & Storage Trend
- 完成状态：完成
- 验收结论：`READY FOR PHASE 8`
- 当前分支：`feature/v0.2`
- Phase 7 起始提交：`b9c2d1b`
- V0.1 基线标签：`v0.1.0-rc1`

## 2. 实现范围

- 新增 `ScanHistoryScreen`，只展示 `COMPLETED` 成功扫描。
- 新增 Compose Canvas 自绘轻量折线趋势，不引入第三方图表 SDK。
- 趋势维度：已使用、剩余、视频、图片、Downloads、应用占用。
- 首页新增“空间趋势 / 相比上次扫描”，展示已使用空间、视频和应用占用变化。
- 首页新增“值得看看”候选卡；只展示当前扫描会话中的查看建议，不自动选择或处理文件。
- 导航以最小范围增加 `scan-history`，没有重写现有 Navigation Compose。
- 默认保留最近 30 次成功扫描；既有策略调整为 10 / 30 / 90 / Unlimited 次成功扫描。
- 第 31 次成功扫描在同一 Room transaction 中自动移除最旧成功记录。
- FAILED / CANCELLED 记录不会进入成功历史、不会覆盖上次成功基线，也不会被成功记录裁剪误删。
- 未实现 Phase 8 的 Cleanup History UI、Recovery Center 或任何新删除能力。
- 未增加后台扫描、WorkManager 或自动调度。

## 3. 持久化设计

- Room Database Version：仍为 `1`。
- Entity / 表 /字段 /索引 /唯一约束：无变化。
- Schema export：无变化，不需要 Migration。
- `ScanHistoryDao.observeRecent()`：仅查询 `scanStatus = 'COMPLETED'`，按完成时间倒序。
- `ScanHistoryDao.pruneCompleted()`：按成功扫描数量保留最近 N 条。
- `RoomScanPersistenceTransactionRepository` 与 `RoomIncrementalScanPersistenceRepository`：完成快照写入后，在同一事务内执行默认 30 条裁剪。
- 两个完成事务入口均要求快照状态确实为 `COMPLETED`。
- `AppStorageHistoryDao.observeRecentTotals()`：按成功应用统计批次聚合总占用，用于应用趋势。
- `ObserveScanHistoryUseCase`：合并成功扫描历史和应用占用批次，并对成功状态做领域层防御性过滤。
- 未启用 `fallbackToDestructiveMigration()`。

## 4. UI / Navigation

- 新增 `ScanHistoryScreen`、`ScanHistoryViewModel`、`ScanHistoryUiState`。
- 空状态、读取失败、趋势指标、最新值、最近扫描列表均有明确界面状态。
- Canvas 提供语义描述；历史列表显示扫描日期、扫描类型、已用/剩余、媒体分类和项目数。
- 首页只在存在两次成功扫描时显示差值；首次成功扫描仅保存基线，不伪造变化。
- 应用占用没有两批可比较数据时显示“暂无对比”。
- “值得看看”明确说明候选不会被自动选择或处理，按钮仍进入既有安全建议流程。
- Phase 7 新增用户可见文案全部放入 `strings.xml`。

## 5. 新增文件

- `app/src/main/java/com/cleanguard/app/domain/usecase/ObserveScanHistoryUseCase.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanhistory/ScanHistoryScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanhistory/ScanHistoryUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/scanhistory/ScanHistoryViewModel.kt`
- `app/src/test/java/com/cleanguard/app/domain/usecase/ObserveScanHistoryUseCaseTest.kt`
- `app/src/test/java/com/cleanguard/app/feature/home/HomeHistoryDeltaTest.kt`
- `docs/CleanGuard-V0.2-Phase-7-Report.md`

## 6. 修改文件

- `app/src/main/java/com/cleanguard/app/data/local/dao/ScanHistoryDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/dao/AppStorageHistoryDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/repository/RoomPersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/persistence/PersistenceModels.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/PersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeUiEvent.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeViewModel.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardDestination.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardNavHost.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardViewModelFactory.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/domain/usecase/LoadAppStorageUseCaseTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/data/local/CleanGuardDatabaseTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/Phase10UiTest.kt`

## 7. 测试结果

- 新增测试用例：7。
  - JVM：成功历史过滤/排序 1 个；首页首次基线及三类差值 2 个。
  - Room instrumented：失败/取消不进入成功历史、应用批次总量聚合、第 31 次成功扫描自动裁剪 3 个。
  - Compose instrumented：历史列表和趋势展示 1 个。
- Debug JVM 单元测试：96/96 通过，0 failed，0 errors，0 skipped。
- Release JVM 单元测试：96/96 通过，0 failed，0 errors，0 skipped。
- API 36 instrumented tests：29/29 通过，0 failed，0 errors，0 skipped。
- V0.1 SafetyRuleEngine 与既有回归测试全部继续通过。

## 8. Build 结果

- `compileDebugKotlin`：成功。
- `compileDebugAndroidTestKotlin`：成功。
- `testDebugUnitTest`：成功。
- `testReleaseUnitTest`：成功。
- `lintDebug`：成功。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（API 36）：成功。
- Debug APK 安装：成功。
- Debug APK 冷启动：成功，Activity 为 `com.cleanguard.app/.MainActivity`。
- 冷启动后进程存活，AndroidRuntime fatal entries：0。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Debug APK 大小：33,435,197 bytes。
- Debug APK SHA-256：`527389DEF5357DF2F1DC959EC6B9782E0314445E9749CD3B24F54DF5D70E7781`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- AndroidTest APK 大小：2,750,466 bytes。

## 9. Lint / Warnings

- Android Lint：0 Error，11 Warning。
- 11 条均为工具链或依赖“存在更新版本”提示；按任务书要求没有升级 AGP、Kotlin、Compose、Room 或其他依赖。
- 构建环境提示 `android.overridePathCheck=true` 为实验选项。
- Android Studio 与 command-line tools 存在 SDK XML 版本提示。
- Debug 打包提示两个依赖原生库无法 strip，已按原样打包，不影响测试或启动。
- Windows JUnit 对中文工作区 classpath 存在兼容问题；测试使用指向同一工作区的 `Q:\` ASCII 映射执行。
- 官方依赖源网络在当前环境不稳定；验证阶段使用一次性镜像 init script，脚本和下载文件已删除，仓库依赖源未改变。

## 10. 权限变化

- `AndroidManifest.xml`：Phase 7 无 diff。
- 未新增 runtime permission。
- 不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku。
- 继续沿用 V0.1/V0.2 已有媒体读取与 Usage Access 声明。

## 11. Safety / 回归检查

- `MediaStoreScanner` / `IncrementalMediaStoreScanner` 扫描行为：无重写。
- `SafetyRuleEngine`：无 diff。
- `CleanupRepository` / MediaStore Trash / Undo：无 diff。
- DANGER 与 UNKNOWN 仍不可清理。
- REVIEW 仍默认不选中。
- Recommendation 不改变 RiskLevel。
- 历史、趋势和候选卡不提供“一键释放”或自动处理能力。
- FAILED / CANCELLED 不会成为成功历史基线。

## 12. Git diff 摘要

- 16 个已有源文件/测试文件修改。
- 7 个新文件（含本报告）。
- Manifest、Room Schema、Gradle dependency、SafetyRuleEngine、CleanupRepository 无变化。
- 临时 Gradle 下载文件和镜像脚本已删除，不进入版本控制。

## 13. 已知问题 / 下一 Phase 风险

- 应用占用趋势来源于用户成功打开并读取“应用占用”后的历史批次；没有 Usage Access 或没有两批成功记录时不会伪造差值。
- 当前历史保留策略默认 30 次；10 / 90 / Unlimited 已具备领域与 DAO 能力，但本阶段不新增 Settings UI。
- 扫描历史仍完全由用户触发扫描产生，不增加后台自动扫描。
- Phase 8 如实现 Cleanup History / Recovery Center，必须复用现有安全删除与系统回收站语义，不得把扫描历史候选转化为自动删除能力。

`READY FOR PHASE 8`
