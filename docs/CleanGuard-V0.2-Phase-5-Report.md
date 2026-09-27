# CleanGuard V0.2 Phase 5 Report

## 1. Phase / 状态

- Phase：V0.2 Phase 5 — APK Analyzer 与 Downloads Insights
- 状态：完成
- 验收结论：`READY FOR PHASE 6`
- 当前分支：`feature/v0.2`
- Phase 5 起始提交：`8905c3e23dbd3855edac83020850f8f9cbab8053`
- V0.1 基线标签：`v0.1.0-rc1`

## 2. 本阶段实现

### APK Analyzer

- 使用 `PackageManager.getPackageArchiveInfo()` 读取用户扫描结果中的 APK。
- 读取 `packageName`、`versionName`、`longVersionCode`、`applicationLabel`。
- 对可见的已安装应用执行版本比较，支持：
  - `OLDER_THAN_INSTALLED`
  - `SAME_AS_INSTALLED`
  - `NEWER_THAN_INSTALLED`
  - `NOT_INSTALLED`
  - `UNKNOWN`
- Android 包可见性不足、权限受限或无法确认时保守返回 `UNKNOWN`，未添加 `QUERY_ALL_PACKAGES`。
- 旧版本或长期存放的 APK 可显示 HIGH 推荐，但原始安全等级仍保持 REVIEW；推荐优先级不等于安全等级。
- APK 只在本地缓存目录生成随机临时副本供 PackageManager 解析，流式复制并在 `finally` 中删除；不上传、不保存到数据库、不读取到整块内存。

### Downloads Insights

- 只分析用户明确授权的 SAF 扫描结果，过滤非 `ScanSource.SAF` 数据。
- 按最近 30 天、30–90 天、90–180 天、超过 180 天、时间未知分组。
- 每组显示文件数、占用空间、最大文件和文件类型。
- 超过 180 天显示 HIGH 推荐，但仍明确要求用户 REVIEW。
- 支持通过系统 Intent 预览、分享和打开系统文件选择位置。
- 未新增删除入口，不执行 SAF 永久删除或其他不可逆文件操作。

## 3. 新增文件

- `app/src/main/java/com/cleanguard/app/domain/apk/ApkModels.kt`
- `app/src/main/java/com/cleanguard/app/domain/apk/ApkAnalyzer.kt`
- `app/src/main/java/com/cleanguard/app/data/apk/ApkAnalyzerImpl.kt`
- `app/src/main/java/com/cleanguard/app/data/apk/AndroidApkArchiveInspector.kt`
- `app/src/main/java/com/cleanguard/app/data/apk/AndroidInstalledPackageVersionProvider.kt`
- `app/src/main/java/com/cleanguard/app/domain/download/DownloadInsights.kt`
- `app/src/main/java/com/cleanguard/app/data/download/DownloadInsightsEngineImpl.kt`
- `app/src/main/java/com/cleanguard/app/feature/apkinsights/ApkInsightsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/apkinsights/ApkInsightsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/apkinsights/ApkInsightsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/downloadinsights/DownloadInsightsUiState.kt`
- `app/src/main/java/com/cleanguard/app/feature/downloadinsights/DownloadInsightsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/downloadinsights/DownloadInsightsScreen.kt`
- `app/src/main/java/com/cleanguard/app/platform/intents/FileActionIntentFactory.kt`
- `app/src/test/java/com/cleanguard/app/data/apk/ApkAnalyzerImplTest.kt`
- `app/src/test/java/com/cleanguard/app/data/download/DownloadInsightsEngineImplTest.kt`

## 4. 修改文件

- `app/src/main/java/com/cleanguard/app/di/AppContainer.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardDestination.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardNavHost.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardViewModelFactory.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/results/ScanResultsUiEvent.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/navigation/CleanGuardDestinationTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/Phase10UiTest.kt`

## 5. 数据库与依赖

- Room Database Version：仍为 `1`。
- Schema：无变化，未新增迁移。
- Gradle dependencies：无新增、无升级。
- 现有扫描索引、重复文件 Hash 缓存和历史数据结构未改写。

## 6. 权限与安全检查

- `AndroidManifest.xml`：Phase 5 无 diff。
- 未新增任何 runtime permission。
- 明确不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku、ADB/Shell/`Runtime.exec()`。
- `SafetyRuleEngine`：无 diff。
- `MediaStoreCleanupRepository`：无 diff。
- DANGER / UNKNOWN 不可清理、REVIEW 默认不选中、Trash/Undo/系统取消流程均未改变。
- 未实现自动删除 APK、Downloads、照片、视频或重复文件。

## 7. 测试结果

- 新增测试源用例：12（10 个 JVM 单元测试，2 个 Compose 仪器测试）。
- Debug JVM 单元测试：90/90 通过，0 failed，0 skipped。
- Release JVM 单元测试：90/90 通过，0 failed，0 skipped。
- API 36 instrumented tests：23/23 通过，0 failed，0 skipped。
- APK Analyzer 覆盖：旧版/相同/新版、显式未安装、包可见性 UNKNOWN、无效 APK、I/O 失败、协程取消。
- Downloads Insights 覆盖：30/90/180 天边界、仅 SAF 数据、汇总数据、未知时间。
- UI 覆盖：APK HIGH 推荐与 REVIEW 风险分离、Downloads SAF/不删除边界。

## 8. Lint / Build / 运行验证

- `lintDebug`：成功，0 Error，5 Warning。
- `test`：成功。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（API 36）：成功。
- Debug APK 冷启动：成功，`LaunchState: COLD`，Activity 为 `com.cleanguard.app/.MainActivity`，进程存活，未发现 AndroidRuntime 崩溃。
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- APK SHA-256：`213B48B848F298C24E456E27FFEF64DC76EB68674DD6F33937CBDCED81C62997`

## 9. Warning

- Lint 的 5 个 Warning 均为非阻塞的可用新版本提示：Kotlin Android、Kotlin Compose、Coroutines Android、Coroutines Test、JSON。
- 按任务书要求，本阶段没有为 APK/Downloads 功能升级整个 Gradle/Kotlin/Compose 工具链。
- 构建环境仍会报告 Android Studio 与 command-line tools 的 SDK XML 版本提示。
- 受工作区沙箱限制，Kotlin daemon 无法写入用户 AppData 临时标记；Gradle 自动回退到非 daemon 编译并成功完成，不影响产物或测试结论。

## 10. 已知问题与下一阶段风险

- 在 Android 11+ 包可见性限制下，`NameNotFoundException` 无法可靠区分“未安装”和“不可见”，生产实现按任务书要求显示 `UNKNOWN`；没有用扩大权限来猜测。
- 系统预览、分享和定位动作依赖设备上是否存在可处理对应 Intent 的系统应用；当前实现安全吞吐启动失败，不影响主流程。
- Downloads Insights 依赖用户先完成扫描并保留 SAF 授权；未授权时只显示说明，不回退到广泛存储访问。
- 下一阶段若进入 App Storage History，应继续使用现有 Room/AppContainer，保持 Usage Access 的显式状态与历史采样边界，并避免扩大包查询权限。
- 本阶段未进入 Phase 6，也未实现 App Storage History。

## 11. Git diff summary

- 应用与测试代码：9 个已跟踪文件被修改，16 个源文件/测试文件新增。
- 报告：新增本文件。
- 数据库 Schema、Manifest、SafetyRuleEngine、CleanupRepository：无变化。

`READY FOR PHASE 6`
