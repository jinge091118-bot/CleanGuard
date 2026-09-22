# CleanGuard V0.1.0-rc1

CleanGuard 是一个以可解释、安全确认和最小权限为原则的 Android 本地空间分析工具。V0.1 使用 Kotlin、Jetpack Compose、Material 3 与 Navigation Compose，支持 Android 10（API 29）及以上设备，编译和目标版本均为 API 36。

应用不会使用恐吓式判断，也不提供全局“一键清理”。扫描结果会先经过本地安全规则分类；用户选择的媒体仍需经过应用内确认和 Android 系统确认，才能移入系统回收站。

发布资料：[`CHANGELOG.md`](CHANGELOG.md)、[`0.1.0-rc1 发布清单`](docs/releases/0.1.0-rc1-checklist.md)、[`0.1.0-rc1 产物清单`](docs/releases/0.1.0-rc1-manifest.md)。

## 技术要求

- Android Studio（使用支持 Android Gradle Plugin 8.13.2 的稳定版本）
- JDK 17
- Android SDK Platform 36 与相应 Build Tools
- `minSdk = 29`、`compileSdk = 36`、`targetSdk = 36`

## 架构

项目采用单 Activity、Compose 单向数据流结构：

- `domain/model`：扫描、空间、安全判断、清理计划与结果模型
- `domain/repository`：平台数据访问边界
- `domain/rules`：安全规则引擎与匹配器
- `domain/usecase`：扫描分析、阈值更新、应用占用和清理计划用例
- `data`：MediaStore、Storage Access Framework、StorageStatsManager、设备容量与清理仓库实现
- `feature`：Home、Scanning、ScanResults、CleanupDetail、StorageInsights、CleanupResult 六个页面及其 ViewModel/UI state
- `navigation`：Navigation Compose 路由与 ViewModel 工厂
- `platform`：权限和系统 Intent 封装
- `ui`：Material 3 主题、通用组件与格式化工具

UI 不直接访问 `ContentResolver`、`PackageManager` 或 `StorageStatsManager`。平台 API 均位于仓库或扫描器实现中，并通过 `AppContainer` 注入。

## 构建与运行

在 Android Studio 中打开项目根目录，确认 Gradle JDK 为 17，等待 Sync 完成后运行 `app` 配置即可。

也可以在 Windows PowerShell 中执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat assembleRelease
```

运行设备应为 Android 10 或更高版本。完整验收建议使用 Android 16 / API 36 模拟器：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

构建产物：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release-unsigned.apk`

Release APK 默认未签名。正式分发前必须在本机或 CI 的安全密钥环境中配置签名，密钥不得提交到仓库。

## 权限与隐私边界

CleanGuard 不声明网络权限，扫描和规则判断均在本机执行。

- Android 13+：按系统版本请求照片、视频、音频读取权限；Android 14+ 支持用户选择部分照片和视频。
- Android 12 及以下：仅在系统允许的版本范围内使用 `READ_EXTERNAL_STORAGE`。
- Download：通过 Storage Access Framework 由用户主动选择目录，只遍历已授权目录及其子目录。
- 应用占用：通过 Usage Access 特殊访问读取可启动应用的汇总占用，用户需在系统设置中手动授权。
- 清理：Android 11+ 使用 MediaStore 系统回收站确认；应用本身不会绕过系统对话框静默删除。

项目不申请 `INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE` 或 `MANAGE_MEDIA`，也不使用 root、Shell、Shizuku、无障碍服务或隐藏 API。

## 扫描与分析算法

### 设备容量

首页通过系统文件系统统计读取总容量和可用容量，已用容量由两者的安全差值计算。异常值会被限制在有效范围内。

### MediaStore

扫描器分别查询图片、视频和音频集合，仅读取索引字段（ID、名称、MIME、大小、修改时间和相对路径），不读取媒体内容。扫描过程按文件数或时间间隔节流更新进度，单项读取异常会记录并跳过。

### Download / APK

用户通过系统目录选择器授予 SAF URI 后，扫描器只递归访问该授权树。APK 通过 MIME 类型和扩展名识别；未知下载项保留为普通下载类别。

### 大文件

大文件阈值可在 50 MB、100 MB 和 500 MB 之间切换。达到阈值的项目会被归为大文件，并重新经过完整安全规则判断。大文件仅代表空间线索，不代表应当处理。

### 应用占用

获得 Usage Access 后，系统会按总占用降序展示可启动应用的应用数据、缓存和总占用。此功能只提供观察和系统设置入口，不直接清理其他应用的私有目录。

## 安全策略

安全规则保存在版本化的 `assets/safety_rules_v1.json` 中。每个扫描项目都必须得到 `SafetyDecision`，优先级固定为：

`DANGER > UNKNOWN > REVIEW > SAFE`

- `DANGER`：数据库、受保护路径等高影响项目，禁止进入处理计划。
- `UNKNOWN`：没有明确规则可判断的项目，保守禁止处理。
- `REVIEW`：媒体、APK 等需要用户逐项确认的项目，默认不选中。
- `SAFE`：仅允许显式白名单中的 CleanGuard 自有、可重建临时缓存；不能由宽泛规则推断。

规则冲突时采用最高风险结果。`DANGER` 和 `UNKNOWN` 无法获得回收站或永久删除动作；清理计划还会再次校验来源、风险和动作，避免仅依赖 UI 状态。

## 清理、结果与撤销

V0.1 仅把合格的 MediaStore 项目提交给 Android 系统回收站。流程为逐项选择、应用内计划确认、系统确认、记录实际结果。取消系统确认会生成取消结果且不会修改媒体。

成功移入回收站后可发起恢复，恢复同样必须经过系统确认。结果页显示请求数、成功数、失败数、取消数及处理前后的实际设备占用差值。移入回收站通常不会立即释放物理空间。

## 测试

本地单元测试覆盖安全规则优先级与兜底、权限状态、MediaStore 进度、大文件重分类、SAF 分类、扫描状态、清理计划和清理结果。Android UI 测试覆盖：

- 首页 → 扫描 → 结果，以及结果 → 清理建议导航
- REVIEW 默认不选中
- DANGER 不可选择
- 系统确认取消后的结果状态

## 已知限制

- V0.1 尚未实现重复文件内容哈希或相似图片判断，界面仅明确标注该能力暂未提供。
- MediaStore 回收站需要 Android 11（API 30）或以上；Android 10 不执行绕过系统保护的替代删除。
- 撤销上下文当前只保存在进程内；应用进程被系统终止后，结果页不会恢复上一批撤销请求。
- SAF 能访问的范围由系统目录选择器和文档提供方决定；Android 11+ 通常不允许直接授权 Download 根目录。
- Usage Access 是用户手动授予的特殊访问，未授权时不会查询应用占用。
- 首页空间分类不会推测系统不可见的分类；分类为空时会如实说明当前可用的索引分析能力。
- 扫描在前台进程内运行，V0.1 没有后台任务、云同步或遥测。
- Release 构建默认未签名，不能直接作为商店发布包。

## V0.2 建议

- 在隐私与性能预算明确后，引入分块哈希的重复文件候选流程，并继续保持先候选、后人工确认。
- 将可撤销批次持久化，并处理进程重建和系统回收站保留期变化。
- 增加更大规模数据集的基准测试、旋转/进程重建测试和更多设备厂商兼容性测试。
- 建立受保护的 Release 签名与 CI 流程，并输出可发布的 App Bundle。
