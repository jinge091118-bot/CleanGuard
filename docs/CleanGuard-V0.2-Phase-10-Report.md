# CleanGuard V0.2 Phase 10 Report

## 1. Phase / 完成状态

- Phase：V0.2 Phase 10 — Performance + Stability
- 完成状态：工程实现与当前可用环境验收完成
- 验收结论：`READY FOR PHASE 11`
- 当前分支：`feature/v0.2`
- Phase 10 起始提交：`91d05f4`
- V0.1 基线标签：`v0.1.0-rc1`
- 兼容矩阵：`docs/CleanGuard-V0.2-Phase-10-Compatibility-Matrix.md`

## 2. 实现范围

- 将扫描结果汇总改为单次线性遍历，避免按风险与类别重复扫描 100k 列表。
- 增量扫描只按 volume 读取已有索引一次，不再在事务提交前后重复全库装载。
- 结果页推荐项建立 O(1) ID 映射，消除逐项线性查找造成的最坏 O(n²)。
- 首页、结果页和清理详情的推荐 / 排序 / 汇总工作移出主线程。
- Full SHA-256 改为固定最多 2 个 Worker 消费候选，避免为大候选组一次创建海量协程。
- 安全规则、增量索引与重复检测长循环定期检查取消；`CancellationException` 继续向上传播。
- Room 索引写入按 250 条分批，删除按 500 条分批，仍在单一事务内完成。
- 完整与增量扫描成功事务自动裁剪超过 30 天且已无索引对应项的 Hash cache 孤儿。
- 未新增后台扫描、WorkManager、自动删除、权限或 V0.3 功能。

## 3. 100k / 内存 / 数据库结果

- JVM 10k / 50k / 100k 汇总：8 / 31 / 63 ms。
- JVM 100k 测试进程堆：176,685,080 bytes / 最大 536,870,912 bytes。
- 100k 首次扫描：`FULL`，100,000 条 metadata；无变化二次扫描：`INCREMENTAL`，0 条 changed metadata，使用上一代 Generation。
- API 36 Room 100k：12.163 秒，100,000 条全部写入并在同一事务内完成 sync state、snapshot 与维护动作。
- Debug 冷启动后：TOTAL PSS 86,443 KB，TOTAL RSS 194,196 KB。
- 启动后 `dumpsys activity lastanr`：无 ANR；目标进程日志无 OOM / FATAL / SQLite / Security / DeadObject 异常。

## 4. Hash / Duplicate

- `StreamHashCalculator` 保持流式读取；Full Hash 使用 64 KiB buffer，不使用 `readBytes()`，不把完整视频载入内存。
- 16 MiB 生成式输入验证读取请求不超过 64 KiB。
- 10,000 个同尺寸、同 Quick Hash 候选全部完成 Full Hash；最大并发严格为 2。
- 固定 Worker 数量最多 2，不再按候选数量创建 `Deferred`。
- Hash cache 唯一文件版本约束与失效逻辑不变；新增旧孤儿裁剪能力，数据库不会因已删除文件无限增长。

## 5. 取消 / 异常 / 状态

- 10,000 条索引 reconciliation 中取消可被检测，扫描记录为 `CANCELLED`。
- 取消时不会提交 completed transaction，也不会推进上一成功 Generation 基线。
- `STARTED / COMPLETED / CANCELLED / FAILED` 语义保持不变，只有 `COMPLETED` 替换成功基线。
- Hash 与扫描路径继续显式重抛 `CancellationException`；可恢复文件异常只跳过目标项。
- ViewModel + StateFlow 架构保持，耗时计算位于后台 dispatcher；未增加 Activity 持有或主线程 DAO。

## 6. UI / 可访问性 / Jank

- 大列表继续使用 `LazyColumn` 与稳定 key；重复组、文件、历史、应用、下载和清理项均未退化为一次性 Column。
- 风险标签继续同时显示文本，不仅依赖颜色；REVIEW 默认不选中，DANGER / UNKNOWN 不可清理。
- 设置 500 MB 阈值交互、Partial Media 文案、Trash / Undo 取消、导航和安全边界均有 instrumented 覆盖。
- SwiftShader AVD 滚动数据受软件渲染影响，不作为真机发布指标；详见兼容矩阵。

## 7. 测试结果

- Debug JVM：109/109 通过，0 failed，0 skipped。
- Release JVM：109/109 通过，0 failed，0 skipped。
- API 36 instrumented：38/38 通过，0 failed，0 skipped。
- Phase 10 新增测试：8 项。
  - JVM：100k 增量扫描、扫描取消、10k Hash Worker、16 MiB 流式 Hash、10k/50k/100k 汇总，共 5 项。
  - Instrumented：Hash cache 孤儿裁剪、Room 100k 写入、500 MB 设置交互，共 3 项。
- V0.1 / V0.2 安全回归继续通过：SafetyRuleEngine、DANGER / UNKNOWN、REVIEW 默认选择、Trash / Undo、系统取消、导航与 Partial Media。

## 8. Build / APK

- `test`：成功。
- `lint`：成功，No Issues Found。
- `assembleDebug`：成功。
- `assembleRelease`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`：成功。
- Debug APK 安装：成功。
- Debug APK 冷启动：成功，`LaunchState: COLD`，3,717 ms。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
  - 34,177,531 bytes
  - SHA-256 `501D0B4B4DD2E601A01D4F9811FD65DE0C584AC4B8A275349383A1A276363ADD`
- Release APK（未签名）：`app/build/outputs/apk/release/app-release-unsigned.apk`
  - 25,265,169 bytes
  - SHA-256 `CF4B242840664E59B6E49353E147564BB3B49069130C4D05147B08FBC20D5F70`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - 2,865,565 bytes
  - SHA-256 `81CD51BF17C8FCB6243E696083452E7251F237CD473DA8F44590510817C43C49`

## 9. 权限 / 隐私 / 行为回归

- `AndroidManifest.xml` 相对 `91d05f4` 无 diff，Phase 10 未新增权限。
- 不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku、WorkManager 或开机扫描。
- 生产源码不存在 `readBytes()`；不存在 `Log.*` 或 `println` 输出 URI、路径、Hash、真实文件名或应用列表。
- MediaStore Scanner 的增量语义得到优化但未改为伪增量；现有 Cleanup / Trash / Undo 安全策略无行为变化。
- 未新增永久删除能力、自动清理、后台自动扫描或网络能力。

## 10. Warnings / 已知限制

- `android.overridePathCheck=true` 为实验选项。
- Android Studio 与 command-line tools 存在 SDK XML 版本提示；不阻塞编译、Lint 或测试。
- Windows 中文工作区的 JVM 测试仍通过 `Q:\` ASCII 映射执行。
- Release APK 为未签名产物，不能直接用于正式分发。
- 仅有 API 36 Google AVD；Samsung、Xiaomi、OPPO / OnePlus、vivo / iQOO、Honor 与 Android 10–15 设备未执行。
- SwiftShader 帧数据不代表真机；正式发布前仍需真机 / 设备云 Macrobenchmark 与 OEM 权限、SAF、Trash / Restore 验收。

## 11. Git diff 摘要

- 16 个已有文件修改，3 个新文件（规模测试、兼容矩阵、本报告）。
- 生产修改集中在扫描、重复验证、Room 维护、汇总与 ViewModel 调度；测试新增规模、取消、流式 Hash、Room 与设置交互覆盖。
- Gradle dependency、Room Database Version / Schema、Manifest、SafetyRuleEngine、Cleanup Repository 均无 Phase 10 diff。
- 无 APK、构建缓存、本机 SDK 配置或临时脚本进入版本控制。

## 12. Phase 10 边界确认

- 未进入 Phase 11。
- 未实现 V0.3 功能。
- 未引入新权限、后台任务、自动删除或新的清理能力。
- 当前工程可以进入 Phase 11 发布验收；兼容矩阵中未执行项必须在正式发布前关闭或作为明确发布风险签署。

`READY FOR PHASE 11`
