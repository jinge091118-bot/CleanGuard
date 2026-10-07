# Changelog

本项目遵循语义化版本的发布命名方式。Release Candidate 用 `-rcN` 后缀标识。

## 0.2.0 - 2026-10-07

CleanGuard V0.2 持久化、增量分析、可解释建议与历史恢复版本。

### 新功能

- 引入 Room 本地索引、扫描历史、应用占用历史、Hash 缓存、Ignore 规则与清理历史。
- Android 11+ 使用 MediaStore Generation 与 ID reconciliation 完成真实增量扫描；Android 10 保持完整扫描回退。
- 增加 Recommendation Engine，且与 SafetyRuleEngine 风险判断严格隔离。
- 支持文件大小、Quick Hash 与流式 Full SHA-256 的精确重复文件确认。
- 支持 APK archive 与已安装版本比较、Downloads 时间分组、截图与录屏识别。
- 增加扫描趋势、App Storage History、Cleanup History、Recovery Center、Settings 与 Permission Center。
- 支持 30 天/永久 Ignore、历史保留策略和 Android 14+ Partial Media 状态。

### 性能与稳定性

- 100k 索引规模完成 JVM 与 API 36 Room 事务验证。
- Room 索引按 250 条分批写入，删除按 500 条分批，仍保持原子事务。
- Full Hash 采用 64 KiB 流式读取，最大并发为 2。
- 修复推荐结果 O(n²) 查找、重复 Hash 海量协程及增量扫描重复全库读取热点。
- 扫描取消不会推进成功 Generation 基线，Hash cache 旧孤儿会自动裁剪。

### 安全与隐私

- DANGER / UNKNOWN 继续无法进入清理计划，REVIEW 继续默认不选中。
- Recommendation Engine 不修改 RiskLevel 或允许动作。
- 不新增网络、所有文件访问、全量应用查询、外部存储写入或媒体管理权限。
- 不引入后台自动扫描、自动永久删除、Analytics SDK 或广告 SDK。
- 文件、路径、Hash 和应用列表不上传。

### 验证

- Debug 与 Release JVM 测试各 109 项通过。
- Android 16 / API 36 instrumented tests 38 项通过。
- Lint 无 issue，Debug / Release APK 与 AndroidTest APK 构建成功。
- API 36 上 100k Room 写入、冷启动、内存与 ANR 检查通过。

### 已知限制

- Release APK/AAB 未配置正式签名，不能直接用于商店发布。
- Android 10–15 与主要 OEM 真机矩阵仍需在正式发布前完成。
- 精确重复检测不等同于相似图片识别。
- 软件渲染模拟器的图形性能不代表真机结果。

## 0.1.0-rc1 - 2026-09-21

CleanGuard 首个 V0.1 发布候选版本。

### 功能

- 使用 Kotlin、Jetpack Compose、Material 3 与 Navigation Compose 构建六页面单 Activity 应用。
- 展示真实设备总容量、已用空间和可用空间。
- 支持 Android 分版本媒体权限状态与部分媒体授权。
- 通过 MediaStore 扫描图片、视频和音频索引。
- 通过 Storage Access Framework 扫描用户明确授权的下载目录并识别 APK。
- 支持 50 MB、100 MB 和 500 MB 大文件阈值及重新分类。
- 通过 Usage Access 展示可启动应用的汇总空间占用。
- 使用版本化本地规则将项目分类为 SAFE、REVIEW、DANGER 或 UNKNOWN。
- 对合格的 MediaStore 项目生成清理计划，并通过 Android 系统确认移入回收站。
- 展示实际处理结果，并支持在当前进程内通过系统确认恢复媒体。

### 安全与隐私

- DANGER 和 UNKNOWN 项目不能进入处理计划。
- REVIEW 项目默认不选中。
- 不申请网络、所有文件访问、全量应用查询、外部存储写入或媒体管理权限。
- 不使用 root、Shell、Shizuku、无障碍服务或隐藏 API。
- 扫描和规则判断均在设备本地完成。

### 验证

- 42 个本地单元测试通过。
- 3 个 Android UI 测试在 Android 16 / API 36 模拟器通过。
- Debug 与 Release Lint 均为 0 error。
- Debug APK 已在 API 36 模拟器完成安装和启动验证。
- Release APK 构建成功，正式分发签名尚未配置。

### 已知限制

- 重复文件内容哈希与相似图片判断尚未实现。
- Android 10 不支持 MediaStore 系统回收站，应用不会使用绕过系统保护的替代删除方式。
- 清理结果和撤销批次尚未跨进程持久化。
- Release 产物当前未签名，不能作为正式商店发布包。
- Gradle UTP 组件在当前主机上受 Google Maven TLS 握手问题影响；设备测试已通过 ADB 和 AndroidJUnitRunner 等价执行。
