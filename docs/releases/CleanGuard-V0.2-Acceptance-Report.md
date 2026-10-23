# CleanGuard V0.2 Acceptance Report

验收日期：2026-10-07
应用 ID：`com.cleanguard.app`
版本：`versionCode 2` / `versionName 0.2.0`
分支：`feature/v0.2`
Phase 11 起始提交：`ddf93fd`

## 1. Phase

- Phase：V0.2 Phase 11 — Release Acceptance
- 完成状态：完成
- 产品工程验收：PASS
- 对外商店分发：尚未配置正式签名，不能直接发布
- 最终结论：`CleanGuard V0.2 COMPLETE`

## 2. Phase 11 修改范围

- 将 README 从 V0.1 更新为 V0.2，补齐新功能、架构、Room、增量扫描、推荐与安全边界、重复算法、Hash 缓存、历史、隐私、权限、兼容性、限制和 V0.3 规划入口。
- 更新 CHANGELOG，记录 V0.2 功能、性能、安全、验证和已知限制。
- 新增本 Acceptance Report。
- 未修改生产代码、数据库 Schema、Gradle dependency、权限或清理能力。

## 3. 版本与工具链

- `applicationId = com.cleanguard.app`
- `versionCode = 2`
- `versionName = 0.2.0`
- `minSdk = 29`
- `compileSdk = 36`
- `targetSdk = 36`
- AGP 8.13.2
- Kotlin 2.3.21
- KSP 2.3.12
- JVM / JDK 17
- Room 2.8.5
- Compose BOM 2025.10.01

## 4. 干净构建

执行命令：

```powershell
.\gradlew.bat clean test lint assembleDebug assembleRelease bundleRelease assembleDebugAndroidTest --offline --stacktrace
```

结果：`BUILD SUCCESSFUL in 2m 53s`，147 个 actionable tasks 中 143 个执行、4 个为最新状态。

API 36 设备测试：

```powershell
.\gradlew.bat connectedDebugAndroidTest --offline --stacktrace
```

结果：`BUILD SUCCESSFUL in 2m 34s`，38/38 通过。

## 5. 自动化测试

- Debug JVM：109/109 通过，0 failed，0 skipped。
- Release JVM：109/109 通过，0 failed，0 skipped。
- Android 16 / API 36 instrumented：38/38 通过，0 failed，0 errors，0 skipped。
- Android Lint：`No Issues Found`。
- API 36 Room 100k transaction：6.666 秒，100,000 条写入与事务结果正确。

关键回归继续通过：

- SafetyRuleEngine 风险优先级与 UNKNOWN 兜底。
- DANGER / UNKNOWN 不可进入清理计划。
- REVIEW 默认不选中。
- Recommendation Engine 不改变 RiskLevel 或允许动作。
- MediaStore Trash / Undo / 系统取消。
- 首次完整扫描、无变化二次增量扫描、增加、修改、删除和 volume 变化。
- 扫描取消不推进成功 Generation 基线。
- 精确重复文件 Quick / Full Hash、最大并发 2、默认不选中。
- APK、Downloads 时间分类、截图 / 录屏。
- Scan History、App Storage History、Cleanup History、Recovery、Ignore、Settings、Partial Media。

## 6. APK / AAB 验收

| 产物 | 字节数 | SHA-256 | 状态 |
| --- | ---: | --- | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 33,796,505 | `2BD3EA447CAD10220BAB06C7EABAD606A8044DCB1856AC979F28398392FAD6E1` | Debug 证书，APK Signature Scheme v2 验证通过 |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 25,265,169 | `F64CAB33DD737444CCD3B40C24730BC3252327CD6470896EA2C6E63CF7DCF8DE` | 构建成功，未签名 |
| `app/build/outputs/bundle/release/app-release.aab` | 8,719,350 | `F9D1E8E0C4208C5F313DA4DD61E2D856DBDE91C66AC791BE334D3FC46677A632` | bundletool 1.18.1 validate 通过，未签名 |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,795,272 | `01F7F849C58192AF0DFAF87A73632C8005DBBD33D3716BAF161F08026AF1B40A` | 构建成功 |

`aapt2 dump badging` 结果：

- package：`com.cleanguard.app`
- versionCode：2
- versionName：0.2.0
- minSdk：29
- targetSdk：36
- compileSdk：36

## 7. 安装、启动与稳定性

- Debug APK 在 `CleanGuard_API_36` AVD 安装成功。
- `LaunchState: COLD`。
- 冷启动时间：2,129 ms。
- 前台 Activity：`com.cleanguard.app/.MainActivity`。
- 启动后 TOTAL PSS：89,812 KB。
- 启动后 TOTAL RSS：193,616 KB。
- `dumpsys activity lastanr`：本次开机后没有 ANR。
- 目标进程日志未发现 FATAL EXCEPTION、OOM、SQLiteException、SecurityException 或 DeadObjectException。

## 8. Manifest / 权限

V0.2 当前 Manifest 与 `v0.1.0-rc1` 标签相比没有权限 diff。

声明权限：

- `READ_EXTERNAL_STORAGE`，仅 `maxSdkVersion=32`
- `READ_MEDIA_IMAGES`
- `READ_MEDIA_VIDEO`
- `READ_MEDIA_AUDIO`
- `READ_MEDIA_VISUAL_USER_SELECTED`
- `PACKAGE_USAGE_STATS`

构建合并 Manifest 还包含 AndroidX 自动生成的应用专属 `com.cleanguard.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，不是用户数据访问权限。

确认不存在：

- `INTERNET`
- `MANAGE_EXTERNAL_STORAGE`
- `QUERY_ALL_PACKAGES`
- `WRITE_EXTERNAL_STORAGE`
- `MANAGE_MEDIA`
- Accessibility Service
- Root / Shell / Shizuku
- 后台开机扫描或 WorkManager 周期任务

## 9. 隐私 / SDK 审计

- Release runtime dependency tree 未发现 Analytics、广告或追踪 SDK。
- 未发现 Firebase Analytics、Google Ads、Appsflyer、Adjust、Amplitude、Facebook SDK、Sentry、New Relic 或 Datadog。
- 生产源码不存在 `Log.*` 或 `println` 输出。
- 不声明网络权限，不具备上传文件、URI、路径、Hash 或应用列表的网络能力。
- 生产源码不使用 `readBytes()`，Full Hash 保持 64 KiB 流式读取。
- 没有自动永久删除、后台自动扫描或用户无感知清理。

## 10. V0.2 Definition of Done

- [x] V0.1 功能与安全回归继续工作
- [x] Gradle 配置与干净构建成功
- [x] Debug APK 正常生成、安装和启动
- [x] Release APK 与 AAB 正常生成
- [x] `targetSdk = 36`、`minSdk = 29`
- [x] 首次完整扫描与第二次增量扫描正常
- [x] MediaStore 增加、修改、删除与 volume 变化可识别
- [x] Room 本地索引、Schema export 与事务正常
- [x] Scan History 与 App Storage History 正常
- [x] Recommendation Engine 正常且不修改 RiskLevel
- [x] 精确重复文件 Full Hash 正常，默认不选择删除
- [x] APK、Downloads、截图 / 录屏分析正常
- [x] Ignore、Cleanup History、Recovery、Settings、Partial Media 正常
- [x] 100k 测试没有不可接受的 ANR / OOM
- [x] 所有 Unit Tests 与关键 Instrumentation Tests 通过
- [x] Lint 无 issue
- [x] DANGER / UNKNOWN 不可清理，REVIEW 默认不选中
- [x] 禁止权限、Root、Shizuku、Accessibility、Analytics、广告 SDK 不存在
- [x] 文件和路径不上传，没有自动永久删除

## 11. Warnings

- `android.overridePathCheck=true` 是实验选项。
- Android Studio 与 command-line tools 存在 SDK XML v4 / v3 兼容提示。
- `libandroidx.graphics.path.so` 与 `libdatastore_shared_counter.so` 无法 strip，构建系统按原样打包；不阻塞构建、安装或测试。
- Windows 中文工作区通过映射到同一目录的 `Q:\` 路径执行 Gradle JVM 测试。
- Release APK/AAB 未配置正式签名。

## 12. 已知限制与发布风险

- 未签名 Release 产物不能直接提交商店或分发给最终用户。
- Samsung、Xiaomi、OPPO / OnePlus、vivo / iQOO、Honor 真机与 Android 10–15 设备矩阵尚未执行。
- SwiftShader AVD 图形性能不代表真机，需要发布前 Macrobenchmark / Perfetto 复核。
- 精确重复文件不等同于相似图片或近似视频识别。
- SAF 文件不会由 CleanGuard 主动永久删除。

这些项目不否定 V0.2 代码与产品工程验收，但会阻止未经签名和设备复核的公开商店发布。

## 13. Git diff 摘要

- Phase 11 修改 `README.md` 与 `CHANGELOG.md`。
- Phase 11 新增本 Acceptance Report。
- 生产代码、测试代码、Gradle dependency、Room Schema、Manifest 均无 Phase 11 diff。
- 构建产物、SDK 配置、签名密钥和临时文件不进入版本控制。

## 14. 下一阶段

- V0.2 Phase 11 后没有自动下一开发 Phase。
- 公开发布前先完成受保护签名、OEM / Android 版本矩阵和真机性能门禁。
- V0.3 必须由新的任务书或用户明确指令启动，不在本轮实施。

`CleanGuard V0.2 COMPLETE`

---

## 15. 后续追加说明（2026-10-23）

本报告以上内容为 2026-10-07 V0.2 冻结时的验收记录，未被改写。

2026-10-23 执行 R3 性能采集时，新增了以下内容，均不改变本报告的验收结论：

- 新增 R3 采集工具与测量路径：`tools/r3/generate-fixture.sh`、`tools/r3/remove-fixture.sh`、`app/src/androidTest/.../R3ScanPerformanceTest.kt`、`app/src/androidTest/.../R3ScanMutationScenarioTest.kt`，以及 `macrobenchmark` 模块的可配置迭代参数。
- 生产代码、Room Schema、Manifest 权限、清理能力、安全规则、推荐引擎职责分离均未改动。
- 回归验证：Debug 单元测试与 Lint 通过；设备端 instrumented 套件 43/43 通过（原 41 项基线无回归，新增 2 项 R3 测试通过）。
- R3 门禁仍未关闭。唯一可用设备被证实为虚拟化 Android 12 平台而非物理 Pixel，因此没有任何 R3 性能行被记为通过。详见 `docs/release/v0.2/PERFORMANCE-REPORT.md` 与 `RELEASE-CHECKLIST.md`。
