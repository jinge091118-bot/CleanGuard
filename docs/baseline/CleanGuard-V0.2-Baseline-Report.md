# CleanGuard V0.2 Baseline Report

> 文档版本：1.0  
> 基线日期：2026-09-22  
> 执行范围：V0.2 Phase 0（只读审计、构建与测试验证）  
> 任务书来源：`C:\Users\Administrator\Desktop\cleanguard v0.2.docx`

> Git 门禁后续状态（2026-09-22）：已按项目负责人指令完成 V0.1 基线提交 `5511ddc`，创建标签 `v0.1.0-rc1`，并建立、切换到 `feature/v0.2` 分支。第 11 节保留 Phase 0 审计当时的原始 Git 快照；该门禁现已解除，Phase 1 仍未开始。

## 0. 结论与阶段门禁

| 项目 | 结论 |
|---|---|
| Phase 0 | **已完成** |
| V0.1 代码基线 | **技术验证通过**：本地单元测试、Lint、Debug APK、AndroidTest APK、API 36 仪器化测试均通过 |
| Phase 1 | **尚未开始** |
| Phase 1 门禁 | **已解除**：V0.1 基线提交、`v0.1.0-rc1` 标签和 `feature/v0.2` 分支均已建立 |
| 业务逻辑变更 | 无 |
| 数据库变更 | 无 |
| 权限变更 | 无 |

当前源码能够编译、安装、启动和通过现有测试；Git 基线也已在 Phase 0 报告首次生成后补齐。工程已具备进入 Phase 1 的版本控制前提，但仍须等待项目负责人明确下达 Phase 1 指令。

## 1. 当前实际项目结构

项目为单模块 Android 应用，包名/命名空间为 `com.cleanguard.app`。

```text
CleanGuard/
├─ app/
│  ├─ build.gradle.kts
│  ├─ proguard-rules.pro
│  └─ src/
│     ├─ main/
│     │  ├─ AndroidManifest.xml
│     │  ├─ assets/safety_rules_v1.json
│     │  ├─ java/com/cleanguard/app/
│     │  │  ├─ CleanGuardApplication.kt
│     │  │  ├─ MainActivity.kt
│     │  │  ├─ data/
│     │  │  │  ├─ apps/
│     │  │  │  ├─ cleanup/
│     │  │  │  ├─ fake/
│     │  │  │  ├─ media/
│     │  │  │  ├─ rules/
│     │  │  │  ├─ saf/
│     │  │  │  └─ storage/
│     │  │  ├─ di/AppContainer.kt
│     │  │  ├─ domain/
│     │  │  │  ├─ model/
│     │  │  │  ├─ repository/
│     │  │  │  ├─ rules/
│     │  │  │  └─ usecase/
│     │  │  ├─ feature/
│     │  │  │  ├─ home/
│     │  │  │  ├─ scanning/
│     │  │  │  ├─ results/
│     │  │  │  ├─ cleanupdetail/
│     │  │  │  ├─ insights/
│     │  │  │  └─ cleanupresult/
│     │  │  ├─ navigation/
│     │  │  ├─ platform/
│     │  │  └─ ui/
│     │  └─ res/
│     ├─ test/          # 16 个本地测试文件
│     └─ androidTest/   # 1 个仪器化测试文件
├─ docs/releases/
├─ gradle/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ README.md
└─ CHANGELOG.md
```

架构为 Compose 单 Activity + Navigation Compose。表现层按功能拆分为 Screen/UiState/UiEvent/ViewModel，业务层使用模型、仓库接口和 UseCase，数据与平台实现置于 `data`、`platform`。依赖注入采用手工 `AppContainer` 和 `CleanGuardViewModelFactory`，未使用 Hilt。

## 2. 当前版本信息

| 配置项 | 当前值 |
|---|---|
| applicationId / namespace | `com.cleanguard.app` |
| versionCode | `1` |
| versionName | `0.1.0-rc1` |
| minSdk | `29` |
| targetSdk | `36` |
| compileSdk | `36` |
| Java / Kotlin JVM target | `17` |

Phase 0 未修改版本号。V0.2 的 `versionName=0.2.0` 与 `versionCode=2` 应在进入实施阶段后按任务书统一调整。

## 3. Gradle、Kotlin 与 Compose 基线

| 组件 | 当前版本/状态 |
|---|---|
| Gradle Wrapper | `8.13` |
| Android Gradle Plugin | `8.13.2` |
| Kotlin / Compose Compiler Plugin | `2.3.21` |
| Compose BOM | `2025.10.01` |
| Material 3 | 由 Compose BOM 管理 |
| Navigation Compose | `2.9.5` |
| AndroidX Core KTX | `1.17.0` |
| Lifecycle | `2.9.4` |
| Activity Compose | `1.11.0` |
| Kotlin Coroutines | `1.10.2` |
| DataStore Preferences | `1.1.7` |
| JUnit | `4.13.2` |

当前未配置 Room、KSP 或 KAPT。Release 构建已配置但 `isMinifyEnabled=false`，并且没有正式发布签名配置。

## 4. 数据库现状

- 当前不存在 Room、SQLite、Entity、DAO、Database 或 Migration。
- `DataStore Preferences` 仅持久化用户授权的 Download SAF tree URI，名称为 `cleanguard_preferences`。
- 扫描会话、扫描结果、应用空间数据、清理结果及撤销状态都只保存在内存中。
- 应用退出或进程被回收后，上述内存状态不可恢复。
- V0.2 任务书要求的扫描索引、历史、应用空间历史、哈希缓存、忽略项和清理历史尚未落地。

## 5. 权限基线

Manifest 当前声明：

- `READ_EXTERNAL_STORAGE`，仅适用于 `maxSdkVersion=32`。
- `READ_MEDIA_IMAGES`。
- `READ_MEDIA_VIDEO`。
- `READ_MEDIA_AUDIO`。
- `READ_MEDIA_VISUAL_USER_SELECTED`。
- `PACKAGE_USAGE_STATS`（特殊访问权限，由用户在系统设置中授权）。
- `<queries>` 只查询带 Launcher intent 的应用。

Manifest 当前未声明：

- `INTERNET`。
- `MANAGE_EXTERNAL_STORAGE`。
- `QUERY_ALL_PACKAGES`。
- `WRITE_EXTERNAL_STORAGE`。
- `MANAGE_MEDIA`。

应用设置 `allowBackup=false`，备份规则排除所有应用数据。SAF 只对用户明确选择的 Download 目录保存持久化读取授权。Phase 0 未新增、删除或扩大任何权限。

## 6. SafetyRuleEngine 基线

- 安全规则从 `assets/safety_rules_v1.json` 加载，规则格式版本为 `1`，当前共 11 条规则。
- 加载及解析在 IO dispatcher 上执行，并保持协程取消语义。
- 规则要求 ID 唯一且关键字段非空。
- 风险优先级为 `DANGER > UNKNOWN > REVIEW > SAFE`。
- 未命中规则时默认返回 `UNKNOWN`，并阻止清理。
- 只有 CleanGuard 自己创建、可重建且明确白名单的缓存才允许判定为 `SAFE`。
- `DANGER` 与 `UNKNOWN` 不能提供 `TRASH` 或 `PERMANENT_DELETE` 动作。
- 清理计划层再次阻止 `DANGER` 与 `UNKNOWN`，构成双层安全约束。

现有 7 个 SafetyRuleEngine 必测场景均有本地测试覆盖并通过。

## 7. Scanner 基线

### MediaStore

- 扫描外部 Images、Video、Audio collection。
- 读取 ID、名称、MIME、大小、时间和相对路径。
- 每 200 个文件或 250 ms 节流一次进度更新。
- 支持 CancellationSignal 和协程取消。
- 当前每次执行全量扫描并一次性在内存保存完整列表。
- 尚未使用 MediaStore version/generation、volume、增删差异同步或持久化索引。
- 尚未覆盖多 volume 增量同步，模型中也没有完整的 generation/lastSeen/hash 等字段。

### SAF

- 只扫描用户明确授权的 Document Tree。
- 使用 BFS 递归目录，通过 `DocumentsContract` 读取元数据。
- 支持 APK/MIME 分类、进度节流和取消。
- 不通过 SAF 删除文件，符合当前安全边界。

## 8. Cleanup 基线

- Android 11+ 使用 `MediaStore.createTrashRequest` 请求系统确认移入回收站。
- 清理前校验数据源、动作与风险等级；只允许 `SAFE`/`REVIEW` 且动作是 `TRASH`。
- `DANGER`/`UNKNOWN` 在模型、UseCase 和 Repository 多层被拒绝。
- 撤销通过 `createTrashRequest(..., false)` 请求系统恢复。
- Android 10 无 MediaStore trash API 时安全失败，不绕过系统保护。
- 当前待确认请求、清理结果与撤销信息仅保存在进程内存中，没有清理历史或崩溃恢复。

未发现 Root、Shizuku、Shell 删除、`ContentResolver.delete`、全盘存储管理或静默删除实现。文本检索中出现的 `root` 仅属于备份 XML domain、JSON 根节点变量或 SAF 根目录变量，不是提权代码。

## 9. 测试数量与结果

### 本地单元测试

- 测试文件：16。
- 逻辑测试方法：42。
- Debug 变体：42/42 通过。
- Release 变体：42/42 通过。
- Gradle 本次总执行次数：84，失败 0，错误 0，跳过 0。

覆盖范围包括安全规则、规则 JSON、清理计划与结果、MediaStore 清理仓库、扫描仓库、MediaStore/SAF 分类、权限、存储概览、ViewModel 和导航目的地。

### Android 仪器化测试

- 测试文件：1。
- 测试方法：3。
- 设备：Android API 36 模拟器 `CleanGuard_API_36`。
- 结果：3/3 通过，耗时 19.933 秒。

已验证：

1. 首页 → 扫描 → 结果 → 清理详情导航可达。
2. `REVIEW` 默认不选中，`DANGER` 不可选中。
3. 用户取消系统确认后显示取消结果。

仪器化测试完成后还执行了应用启动 smoke test，`com.cleanguard.app` 进程成功启动。

## 10. 构建与 APK 结果

执行的基线命令等价于：

```powershell
.\gradlew.bat --no-daemon --max-workers=2 test lintDebug assembleDebug assembleDebugAndroidTest
```

结果：`BUILD SUCCESSFUL`，105 个任务全部执行完成，耗时 2 分 9 秒。

| 产物 | 大小 | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 31,650,734 bytes | `52BFE995B385D11C7FCFA9DF08A4D594FF521CAD063AFCA330F6F5F0791E7172` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,223,620 bytes | `35A902D33BE135EAE348C406F0DC6DF975887DC3949BD0B13627E2752E861076` |

Debug APK 已在 API 36 模拟器上成功安装并启动。

## 11. Git 状态

- 当前分支：`master`。
- 当前仓库：没有任何提交。
- 当前标签：无。
- `git status --short` 显示 `.gitattributes`、`.gitignore`、`README.md`、`CHANGELOG.md`、`app/`、`docs/`、Gradle 配置和 Wrapper 等全部项目内容均为未跟踪文件。
- 因不存在 `HEAD`，不能生成有意义的 Git diff，也不能创建指向 V0.1 的标签。
- 按任务书“只有工作区干净时才创建 `feature/v0.2`”的约束，本次未创建功能分支。
- 未执行 `git reset --hard`、`git clean` 或其他破坏性操作。

进入 Phase 1 前的必要动作：配置 Git 作者信息；审阅并提交完整 V0.1 基线；创建 V0.1 标签（建议与实际 RC 状态一致，例如 `v0.1.0-rc1`，最终标签名称由项目负责人确认）；然后从该基线创建 `feature/v0.2`。

## 12. V0.2 目标与当前架构冲突

| V0.2 需求 | 当前现状 | 风险/影响 |
|---|---|---|
| Room 持久化基础 | 无数据库，状态主要在内存 | 无法提供索引、历史、缓存、崩溃恢复 |
| 增量扫描 | Scanner 只返回全量列表 | 需要引入 volume/version/generation 和事务化 reconcile 流程 |
| 10 万级文件规模 | 完整列表常驻内存并多次映射 | 内存与 UI 流式分页风险，需要 DAO 查询/Paging 或窗口化输出 |
| 多卷 MediaStore | 当前使用固定外部 collection | 需要显式 volume 标识及独立同步状态 |
| 重复文件检测 | 无 quick/full hash、缓存或分组模型 | 需要独立哈希流水线，避免主线程 IO 与重复计算 |
| 推荐系统与安全等级分离 | 部分流程会按“大文件”重分类 | 推荐维度必须独立，不能覆盖安全决策或原始分类 |
| 清理历史与恢复 | pending/result/undo 仅在内存 | 系统确认中断或进程死亡后不可恢复 |
| App storage 历史 | 查询结果仅在 ViewModel；session 字段未实际填充 | 需要时间戳、快照表和明确的数据所有权 |
| 截图/录屏分类 | `FileCategory` 尚无专用枚举 | 需扩展模型及规则映射，保持向后兼容 |
| 设置与国际化 | 大量界面文案硬编码在 Kotlin | 需逐步迁移到 `strings.xml`，避免一次性重写 UI |
| DI 生命周期 | 手工 AppContainer | 可继续使用，但 Room 必须是 Application 级单例 |
| 导航扩展 | 当前只有 6 个 V0.1 页面 | 可增量添加历史、重复文件、设置等目的地，无需重写现有导航 |

最关键原则：V0.2 的“推荐”必须与 `SafetyDecision` 正交；任何性能或智能化改造都不能放宽 `DANGER/UNKNOWN` 禁止清理、`REVIEW` 默认不选中、系统确认不可绕过等安全不变量。

## 13. Phase 1 安全实施建议

在收到明确的 Phase 1 指令后，建议按以下顺序推进，且 Phase 1 只建设持久化基础，不提前实现增量扫描、重复文件推荐或新清理能力：

1. 先完成 Git 初始提交、V0.1 标签和 `feature/v0.2` 分支，确保可回退。
2. 在 Version Catalog 中锁定 Room 与 KSP 兼容版本，配置 Room schema 导出目录。
3. 创建数据库版本 `1`，禁止 `fallbackToDestructiveMigration`。
4. 先定义 Entity/DAO/Database 与实体—领域模型映射，不直接替换现有扫描和清理流程。
5. 由 `AppContainer` 提供 Application 级单例数据库与 Repository，避免改变现有 ViewModel 架构。
6. 为扫描索引、同步状态、应用空间快照、哈希缓存、忽略项和清理历史建立最小表结构；不保存用户文件内容。
7. 增加 DAO 测试、唯一约束测试、事务测试、schema 导出检查和迁移测试骨架。
8. 重新执行本地测试、Lint、Debug 构建及 API 36 仪器化回归，确认 V0.1 安全行为不变。

## 14. Warning、已知问题与风险

### Lint

`lintDebug` 成功，发现 10 个 Warning，全部是“存在较新依赖版本”：

- `AndroidGradlePluginVersion`：1。
- `GradleDependency`：4。
- `NewerVersionAvailable`：5。

没有 Lint Error。Phase 1 不应为了清除版本提示而一次性升级整个工具链；Room/KSP 版本应以与现有 AGP/Kotlin 的兼容性为准单独评估。

### 本机/路径环境

- 工程真实路径包含中文。直接从该路径执行 Release 单元测试时曾出现 Windows/Kotlin 测试类路径编码导致的 `ClassNotFoundException`；通过指向同一工作区的临时 ASCII 驱动器路径重新完整执行后，Debug/Release 全部测试通过。这是本机路径兼容性风险，不是测试断言失败。后续建议固定使用稳定的 ASCII 工程入口。
- Kotlin daemon 尝试写入用户级缓存时出现一次访问被拒绝，Gradle 自动回退到非 daemon 编译并成功完成。
- Android 工具链曾输出 SDK XML schema 版本兼容提示，不影响本次构建。
- `android.overridePathCheck=true` 为实验性配置，当前用于兼容工程路径；应保留记录并在路径迁移后重新评估。

### 任务书读取验证

已对 V0.2 DOCX 进行完整结构化文本提取，共读取 1,219 个正文块。当前环境未安装 LibreOffice/`soffice`，因此无法生成页面级渲染图进行视觉复核；Phase 0 的工程要求与阶段门禁已按完整文本核对。

## 15. 本阶段文件与变更摘要

Phase 0 未修改应用源码、Gradle 配置、Manifest、资源、测试或业务逻辑。

新增文件：

- `docs/baseline/CleanGuard-V0.2-Baseline-Report.md`（本报告）。

临时审计脚本及构建临时目录位于被 Git 忽略的 `.qa/`/临时路径，不属于应用产物。由于仓库尚无首个提交，无法用 `git diff` 区分历史文件；以上变更依据本阶段实际操作记录列出。

## 16. 最终判定

**Phase 0：完成。** 现有 V0.1.0-rc1 在 API 36 基线上通过编译、单元测试、Lint、APK 生成、安装启动和仪器化测试，核心安全边界未发现绕过。

**Phase 1：未开始。** Git 硬门禁现已解除；收到明确指令后，可按第 13 节的建议安全进入 Room 持久化基础建设。
