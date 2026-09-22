# Changelog

本项目遵循语义化版本的发布命名方式。Release Candidate 用 `-rcN` 后缀标识。

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
