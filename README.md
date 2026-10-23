# CleanGuard V0.2.0

CleanGuard 是一个以可解释判断、最小权限和用户确认为原则的 Android 本地空间分析工具。项目使用 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Room 与 DataStore，支持 Android 10（API 29）及以上设备，`compileSdk` 和 `targetSdk` 均为 36。

CleanGuard 不使用恐吓式文案，不把“建议优先级”等同于“风险等级”，也不提供全局一键清理、后台静默扫描或自动永久删除。扫描、分类、Hash 与历史数据全部在设备本地处理。

## V0.2 新功能

- Room 本地索引、扫描快照、应用占用历史、Hash 缓存、Ignore 规则与清理历史。
- Android 11+ 基于 MediaStore Generation 的增量扫描，并通过 ID reconciliation 识别删除项。
- 扫描历史与设备空间趋势。
- App Storage History，对比连续成功扫描中的应用占用增长或减少。
- Recommendation Engine，为大文件、旧下载、录屏、截图、旧 APK 和已确认重复项提供可解释建议。
- 精确重复文件确认：文件大小 → Quick Hash → Full SHA-256。
- APK archive 信息解析及与已安装版本比较。
- Downloads 时间分组与文件类型洞察。
- 截图、录屏与相机媒体分类。
- Cleanup History 与 Media Recovery Center。
- 大文件阈值、历史保留、Ignore 管理、权限中心与隐私设置。
- Android 14+ Partial Media 状态和授权范围说明。

## 技术要求

- 支持 Android Gradle Plugin 8.13.2 的 Android Studio 稳定版本
- JDK 17
- Android SDK Platform 36 与相应 Build Tools
- `minSdk = 29`
- `compileSdk = 36`
- `targetSdk = 36`

## 架构变化

项目保持单 Activity、Compose 单向数据流和手写 `AppContainer` 依赖注入，不引入 Hilt。

- `domain/model`：扫描、风险、推荐、重复文件、历史与清理模型。
- `domain/rules`：SafetyRuleEngine 及版本化本地规则。
- `domain/usecase`：扫描分析、阈值更新、历史观察与清理计划。
- `domain/repository`：UI 与数据实现之间的抽象边界。
- `data/local`：Room Entity、DAO、Mapper、Transaction Repository。
- `data/media`：MediaStore 完整/增量扫描与当前扫描会话。
- `data/duplicate`：流式 Hash、缓存与重复文件确认。
- `data/recommendation`：可解释推荐实现。
- `data/preferences`：DataStore 设置持久化。
- `feature`：页面、UI state、事件和 ViewModel。
- `navigation`：Navigation Compose 路由和 ViewModel factory。
- `platform`：权限与系统 Intent 封装。

数据访问保持以下方向：

```text
UI → ViewModel → Domain Repository / UseCase → Data Repository → DAO / Android API
```

UI 不直接访问 DAO、`ContentResolver`、`PackageManager` 或 `StorageStatsManager`。

## Room 持久化

数据库为 `CleanGuardDatabase`，当前版本为 1，不使用 `fallbackToDestructiveMigration()`。Schema 位于：

```text
app/schemas/com.cleanguard.app.data.local.CleanGuardDatabase/1.json
```

主要表：

- `indexed_items`：MediaStore 元数据索引，不保存文件内容。
- `media_sync_states`：每个 volume 的 MediaStore version / Generation 基线。
- `scan_snapshots`：扫描状态、耗时、容量与分类快照。
- `app_storage_snapshots`：应用占用历史。
- `hash_cache`：Quick / Full Hash 缓存。
- `ignored_items`：30 天或永久 Ignore 规则。
- `cleanup_history` / `cleanup_history_items`：规范化清理批次及关联项。

索引写入按 250 条分批，删除按 500 条分批，并在事务内更新 volume 状态、扫描完成记录、历史保留和旧 Hash 孤儿裁剪。

## 增量扫描原理

Android 11 / API 30 及以上：

1. 按 MediaStore volume 读取版本和 Generation。
2. 首次扫描或版本变化时执行完整元数据同步。
3. 基线有效时只查询上一 Generation 之后发生变化的元数据。
4. 读取当前 MediaStore ID 集合，与本地索引 reconciliation，移除已删除项。
5. 只有 `COMPLETED` 扫描会提交新的成功基线；`CANCELLED` 或 `FAILED` 不推进 Generation。

Android 10 / API 29 不支持 Generation，继续使用完整 MediaStore 元数据扫描，不伪装为增量扫描。所有扫描只读取索引字段，不读取媒体内容。

## Recommendation Engine 与 SafetyRuleEngine

两套引擎职责严格分离：

- `SafetyRuleEngine` 决定 `SAFE / REVIEW / DANGER / UNKNOWN`、允许动作和删除影响，是清理资格的安全边界。
- `RecommendationEngine` 只生成 `HIGH / MEDIUM / LOW / NONE` 建议优先级和解释，用于帮助用户排序复核。

Recommendation Engine 不修改 `RiskLevel`，不能为 DANGER 或 UNKNOWN 增加处理权限，也不能使 REVIEW 项目默认选中。

## Duplicate 算法与 Hash 缓存

精确重复确认使用三阶段流程：

1. 文件大小相同才进入候选组。
2. Quick Hash 读取文件头尾各最多 64 KiB，并混入文件大小。
3. Quick Hash 相同后执行流式 Full SHA-256。

Full Hash 使用 64 KiB buffer，最大并发严格限制为 2；不会使用 `readBytes()` 或把完整视频载入内存。Hash cache 以 URI、大小和修改时间标识文件版本，元数据变化时失效；超过保留期且已无索引对应项的缓存会自动裁剪。

重复文件只是 `REVIEW` 建议，默认全部不选中。V0.2 不自动决定保留哪一个文件。

## 扫描历史与应用历史

- 成功扫描快照可保留最近 10、30、90 次或全部，默认 30 次。
- `STARTED / CANCELLED / FAILED` 状态用于恢复与诊断，不会替代成功趋势基线。
- 首页和扫描历史页展示设备已用空间变化与分类趋势。
- App Storage History 只比较相邻成功采集，展示增长或减少，不把缓存大小描述为“可安全清理”。

## 清理历史与恢复

V0.2 仍只对合格 MediaStore 项目请求 Android 系统回收站确认。处理批次及 URI 关联项持久化到 Room；Recovery Center 可对仍处于可恢复状态的项目发起系统恢复确认。

- DANGER 和 UNKNOWN 无法进入清理计划。
- REVIEW 默认不选中。
- 系统取消不会崩溃，也不会伪造已释放空间。
- SAF 文件只提供预览、分享或定位，不由 CleanGuard 主动永久删除。
- 移入系统回收站通常不会立即释放物理空间。

## 设置与 Ignore

- 大文件阈值：50 MB、100 MB、500 MB、1 GB；默认 100 MB。
- 扫描历史：10、30、90 次或全部；默认 30 次。
- 单项推荐可忽略 30 天或永久忽略，并可从设置恢复。
- Ignore 只隐藏建议，不改变原始风险与允许动作。

## 权限与隐私设计

CleanGuard 不声明网络权限，文件元数据、Hash、规则、推荐和历史均在本机处理。

- Android 13+：按系统版本请求照片、视频和音频读取权限。
- Android 14+：支持用户选择部分照片和视频，并明确标注统计只基于授权范围。
- Android 12 及以下：仅在系统允许的范围内使用 `READ_EXTERNAL_STORAGE`。
- Downloads：通过 Storage Access Framework 由用户主动选择目录。
- App Storage：通过 Usage Access 读取可启动应用的汇总占用，用户需在系统设置手动授权。
- 清理与恢复：Android 11+ 使用 MediaStore 系统确认界面。

项目不申请或使用：

- `INTERNET`
- `MANAGE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- `WRITE_EXTERNAL_STORAGE`
- `MANAGE_MEDIA`
- Root / Shell / Shizuku
- Accessibility Service
- Analytics SDK / 广告 SDK

CleanGuard 不上传文件、完整路径、Hash 或已安装应用列表，也不读取聊天内容或其他应用私有目录。

## 构建与验收

在 Android Studio 中打开项目根目录，Gradle JDK 选择 17，完成 Sync 后运行 `app`。

Windows PowerShell：

```powershell
.\gradlew.bat test lint assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

主要产物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release-unsigned.apk`
- Release AAB：`app/build/outputs/bundle/release/app-release.aab`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

Release APK/AAB 默认未配置正式发布签名。密钥和密码必须由本机或 CI 的受保护环境提供，不得提交到仓库。

## Android 版本兼容

- 最低支持 Android 10 / API 29。
- Android 10 使用完整扫描，且不提供绕过系统保护的删除替代方案。
- Android 11+ 支持 Generation 增量扫描和 MediaStore Trash / Restore。
- Android 13+ 使用细分媒体权限。
- Android 14+ 支持 Partial Media。
- 当前自动化设备验收覆盖 Android 16 / API 36 Google AVD。

详细兼容状态见 [`Phase 10 Compatibility Matrix`](docs/CleanGuard-V0.2-Phase-10-Compatibility-Matrix.md)。

## 已知限制

- Release APK/AAB 尚未配置正式签名，不能直接用于商店发布。
- Samsung、Xiaomi、OPPO / OnePlus、vivo / iQOO、Honor 真机及 Android 10–15 设备矩阵仍需在正式发布前完成。
- 当前重复检测只确认内容完全相同的文件，不提供相似图片或近似视频判断。
- SAF 的能力受系统目录选择器和文档提供方限制；CleanGuard 不主动永久删除 SAF 文件。
- 扫描在前台进程内运行，不提供后台自动扫描、开机扫描、云同步或遥测。
- 软件渲染模拟器的帧率不能代表真机性能，正式发布前仍需真机 Macrobenchmark / Perfetto 验证。

## V0.3 规划入口

V0.3 尚未开始。候选方向仅作为后续评审入口：

- 受保护的 Release 签名与 CI/CD 发布流程。
- Android 10–16 与主要 OEM 设备云回归矩阵。
- Baseline Profile、Macrobenchmark 与 Perfetto 性能门禁。
- 在明确隐私、性能和误判边界后评估相似图片能力。
- 更完整的备份、导出或可移植设置方案。

任何 V0.3 功能都必须重新立项，不属于 V0.2 Release Acceptance。

上述候选方向的价值、成本、风险与建议已逐项评估，见
[`R10 V0.3 Project Evaluation`](docs/release/v0.2/R10-V03-PROJECT-EVALUATION.md)。

## 发布资料

- [`CHANGELOG.md`](CHANGELOG.md)
- [`V0.2 Acceptance Report`](docs/releases/CleanGuard-V0.2-Acceptance-Report.md)
- [`V0.2 Phase 10 Report`](docs/CleanGuard-V0.2-Phase-10-Report.md)
- [`V0.2 Compatibility Matrix`](docs/CleanGuard-V0.2-Phase-10-Compatibility-Matrix.md)
