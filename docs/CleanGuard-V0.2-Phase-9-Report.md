# CleanGuard V0.2 Phase 9 Report

## 1. Phase / 完成状态

- Phase：V0.2 Phase 9 — Settings & Privacy UX
- 完成状态：完成
- 验收结论：`READY FOR PHASE 10`
- 当前分支：`feature/v0.2`
- Phase 9 起始提交：`c02799f`
- V0.1 基线标签：`v0.1.0-rc1`

## 2. 实现范围

- 新增 `SettingsScreen`，集中提供大文件阈值、历史保留、重复检测说明、权限中心入口、Ignore 管理与隐私说明。
- 新增 `PermissionCenterScreen`，展示照片、视频、音频、Downloads SAF 和 Usage Access 的授权状态、用途与缺失影响。
- 大文件阈值支持 50 MB、100 MB、500 MB、1 GB，默认 100 MB。
- 扫描历史支持保留 10、30、90 次或全部，默认 30 次。
- 支持对单个推荐项“暂时忽略 30 天”或“不再提示”，并可从设置页恢复全部提示。
- 未引入自动清理重复文件、后台扫描、权限扩张或 Phase 10 性能专项。

## 3. DataStore / 设置持久化

- 复用已有 `androidx.datastore:datastore-preferences:1.1.7`，未新增 Gradle dependency，未升级 Gradle / Kotlin / Compose 工具链。
- 统一使用 `cleanguard_preferences` DataStore，SAF 授权与 Phase 9 设置共享同一个安全实例，避免重复 DataStore 创建。
- `CleanGuardSettings` 与 `SettingsRepository` 保持 Domain / Data 分层；UI 不直接读写 Preferences。
- DataStore 损坏或无值时安全回退到 100 MB / 30 次。
- 新扫描从 DataStore 读取大文件阈值；结果页修改阈值时同步持久化。
- 完整扫描与增量扫描的事务提交都从同一设置源读取历史保留策略；`UNLIMITED` 时不执行裁剪。

## 4. Ignore 设计与安全隔离

- 复用 Room `ignored_items` 表，以稳定 `itemKey` 唯一约束防止重复记录。
- 30 天 Ignore 写入 `expiresAt`；永久 Ignore 使用 `expiresAt = null`。
- 设置页启动维护会删除已过期 Ignore 记录；支持一次清除全部规则。
- Ignore 统一应用于首页推荐摘要、扫描结果和清理详情的 `RecommendationEngine` 输入。
- 被忽略项的推荐等级为 `NONE`，原 `ScanItem.safetyDecision.riskLevel` 和 `allowedActions` 不变。
- `SafetyRuleEngine`、`CleanupPlan` 和 MediaStore Trash 语义无修改。

## 5. 权限与隐私 UX

- 媒体权限支持 `AUTHORIZED / PARTIAL / NOT_AUTHORIZED`，照片、视频、音频独立展示。
- Android 14+ Partial Media 明确显示：“当前仅分析你允许 CleanGuard 访问的照片和视频。”
- Partial Media 容量说明标注“基于当前授权范围”，不暗示已覆盖全部设备。
- 权限中心明确说明权限不会阻止进入应用，所有授权仍通过 Android 系统 UI 或 SAF 选择器。
- 隐私页明确声明本机扫描、不上传文件、不读聊天内容、不访问其他 App 私有目录。
- Phase 9 新增用户可见文案均放入 `strings.xml`。

## 6. UI / Navigation

- 新增 `settings` 与 `permission-center` 两个 Navigation Compose destination。
- 首页新增“设置与隐私”入口；不重写现有导航，不新增底部导航。
- 权限中心在 `ON_RESUME` 时重新读取系统权限、SAF 与 Usage Access 状态。
- 重复文件设置仅告知“扫描阶段快速检测、详情页完整确认”，不提供自动清理开关。

## 7. 新增文件

- `app/src/main/java/com/cleanguard/app/data/preferences/CleanGuardPreferences.kt`
- `app/src/main/java/com/cleanguard/app/data/preferences/DataStoreSettingsRepository.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/CleanGuardSettings.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/PermissionOverview.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/SettingsRepository.kt`
- `app/src/main/java/com/cleanguard/app/domain/usecase/IgnoreRecommendationUseCase.kt`
- `app/src/main/java/com/cleanguard/app/feature/settings/SettingsScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/settings/SettingsViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/permissions/PermissionCenterScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/permissions/PermissionCenterViewModel.kt`
- `app/src/test/java/com/cleanguard/app/domain/usecase/IgnoreRecommendationUseCaseTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/data/preferences/DataStoreSettingsRepositoryTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/Phase9UiTest.kt`
- `docs/CleanGuard-V0.2-Phase-9-Report.md`

## 8. 测试结果

- Phase 9 新增测试用例：7。
  - JVM：Ignore 不改变 RiskLevel、30 天/永久 Ignore 语义、Android 14 Partial Media 状态 3 个。
  - Instrumented：DataStore 默认值/持久化、Room 过期裁剪/清空、Settings 隐私文案、Partial Media 范围文案 4 个。
- Debug JVM 单元测试：104/104 通过，0 failed，0 errors，0 skipped。
- Release JVM 单元测试：104/104 通过，0 failed，0 errors，0 skipped。
- API 36 instrumented tests：35/35 通过，0 failed，0 errors，0 skipped。
- V0.1 SafetyRuleEngine、DANGER / UNKNOWN 不可清理、REVIEW 默认不选中、Trash / Undo / 取消流程回归测试全部继续通过。

## 9. Build 结果

- `testDebugUnitTest`：成功。
- `testReleaseUnitTest`：成功。
- `lint`：成功。
- `assembleDebug`：成功。
- `assembleRelease`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest` (API 36)：成功。
- Debug APK 安装：成功。
- Debug APK 冷启动：成功，`LaunchState: COLD`，Activity 为 `com.cleanguard.app/.MainActivity`，启动用时 3387 ms。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
  - 大小：34,581,403 bytes
  - SHA-256：`9D9CB4C7A24D5EDB3A63A03254328F10EB752A10058D05822FABC1041A49C4A7`
- Release APK（未签名）：`app/build/outputs/apk/release/app-release-unsigned.apk`
  - 大小：25,265,169 bytes
  - SHA-256：`342839D6204E86152688FE64E26CB00E7EB5497650985DC84F7F67A877D82365`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - 大小：2,921,305 bytes
  - SHA-256：`A3FF858BB98F53A08FF26CF8B509159E93026A6257B4406314559462D1D8C650`

## 10. Lint / Warnings

- Android Lint：`No issues found`，0 Error，0 Warning。
- 构建环境提示 `android.overridePathCheck=true` 为实验选项。
- Android Studio 与 command-line tools 存在 SDK XML 版本提示。
- Release 打包提示 `libandroidx.graphics.path.so` 与 `libdatastore_shared_counter.so` 无法 strip，已按原样打包；不影响测试、安装或启动。
- Windows JUnit 对中文工作区 classpath 存在兼容问题；最终测试从指向同一工作区的 `Q:\` ASCII 映射执行。

## 11. 权限 / 安全回归

- `AndroidManifest.xml` 相对 `c02799f` 无 diff，未新增 runtime permission。
- 不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku。
- `MediaStoreScanner` / `IncrementalMediaStoreScanner`、`SafetyRuleEngine`、`MediaStoreCleanupRepository`、`AndroidManifest.xml` 均无 Phase 9 diff。
- 不读取文件内容、不上传数据、不新增网络能力、不新增永久删除路径。
- Ignore 只会使推荐输出降为 `NONE`，不能降低 `DANGER` / `UNKNOWN` 风险或为其添加处理权限。

## 12. Git diff 摘要

- 27 个已有文件修改。
- 14 个新文件（含本报告）。
- Gradle dependency、Room Schema / Database Version、Manifest、Scanner、Cleanup Repository 和 Safety Rules 无变化。
- 无构建产物、本机配置或临时脚本进入版本控制。

## 13. 已知问题 / Phase 10 风险

- 永久 Ignore 会保留至用户在设置页恢复提示；30 天 Ignore 在过期后由维护流程清理。
- 系统权限状态仍可受 OEM 设置页和 Android 版本差异影响；页面会在返回前台时重新读取。
- Release APK 仍为未签名产物，不用于正式分发。
- Phase 10 建议严格局限于大数据量、ANR、内存、数据库查询与 OEM 兼容验证，不扩大权限或引入后台自动删除。

`READY FOR PHASE 10`
