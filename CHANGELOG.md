# Changelog

本项目遵循语义化版本的发布命名方式。Release Candidate 用 `-rcN` 后缀标识。

## 未发布

### 验证工具

- 新增 `tools/r3/generate-fixture.sh` 与 `tools/r3/remove-fixture.sh`，在专用 `/sdcard/CleanGuardR3` 目录内生成与回收 R3 合成媒体数据集，不触碰该目录之外的任何路径。
- 新增 `R3ScanPerformanceTest` 与 `R3ScanMutationScenarioTest`，直接驱动生产扫描器采集执行类型、耗时、变化与移除计数、索引行数与堆内存，并覆盖 100 增 / 100 改 / 100 删序列。
- `macrobenchmark` 模块支持 `cleanguardIterations*` 与 `cleanguardCompilation` instrumentation 参数，默认值不变。

### 合规材料

- 新增 R4 全套合规材料：隐私政策、Data Safety 声明、照片/视频权限声明、审核备注、商店上架文案、代码级合规事实审计。
- 新增 R5 内部测试方案、R6 封闭测试方案、R7 反馈评审流程。
- 新增 R2 手测场景执行脚本，覆盖部分照片授权、SAF 授权与撤销、回收站确认与取消、风险规则生效、进程回收恢复、内存与长跑稳定性等 12 个场景。
- 新增 R10 V0.3 立项评估文档。

### 隐私政策发布

- 项目所有者已于 2026-10-26 批准隐私政策文本，R4 门禁关闭。
- 隐私政策已公开托管于 <https://jinge091118-bot.github.io/CleanGuard/privacy-policy.html>，托管在仓库 `gh-pages` 分支，HTTPS 强制启用。
- 支持邮箱定为 jinge091118@gmail.com。
- 新增 `tools/privacy-page/render.py`：仅依赖 Python 标准库的 Markdown → HTML 渲染器，把 `docs/release/v0.2/PRIVACY-POLICY.md` 转为公开页面，确保线上文本不会与仓库内的审核源脱节。

### 源码公开与版权

- 确定「源码公开 + 专有许可」模式：源码可读可审阅，但未授予任何开源许可证，不是开源项目。
- 新增仓库根目录 `LICENSE`：专有许可，授予查看与审计许可，保留使用、修改、再分发、衍生作品的全部权利，并保留 CleanGuard 名称与标识的商标权。
- `README.md` 顶部新增中英双语版权声明；GitHub 仓库描述与标签标注 `proprietary`、`not-open-source`。
- 新增 `docs/release/v0.2/SOURCE-AND-LICENSE-POLICY.md`，记录法律与技术依据、已公开范围、密钥审计结果与未来加强保护的手段。
- `feature/v0.2` 分支已推送至远端。推送前完成密钥审计：无 keystore、`key.properties`、`local.properties` 或硬编码凭据被跟踪。
- 隐私政策托管所需的 `gh-pages` 分支保持公开可访问。

### 应用内文案修正

合规审计发现两处表述需要修正，均为事实性纠正，未触及任何扫描、清理、权限申请逻辑或安全规则：

- 设置页隐私说明补充披露了唯一的两条对外授权路径：移入 Android 系统回收站，以及用户主动使用系统分享/预览。
- 首页「不会修改任何文件」改为「本步骤只读取索引和你明确授权的目录，不会修改文件」，把陈述范围收敛到扫描步骤。

同时新增两条隐私说明：常规扫描只读取索引字段不读取媒体内容；重复文件核对在本机流式计算 SHA-256 且不保存副本。

### 验证结论

- Debug 与 Release 单元测试通过，Android Lint 无 issue，Debug AndroidTest APK 构建成功。
- `Phase9UiTest` 的隐私文案断言已同步更新，并新增两条隐私说明的显示断言。
- 设备端 instrumented 套件需在设备恢复后补跑：2026-10-23 该虚拟 Android 12 设备在会话中途离线，疑为云手机实例被回收。
- R3 门禁未关闭。唯一可用设备经核实为虚拟化 Android 12 平台而非物理 Pixel，其数据仅作增量引擎功能性证据记录，不作为性能证据。
- R4 各文档已就绪，待项目所有者批准与隐私政策公开托管。

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
