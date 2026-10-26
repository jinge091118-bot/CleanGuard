# CleanGuard 红米真机一次到位执行计划

**状态**：待设备连接后执行
**日期**：2026-10-26
**设备**：Redmi Note 12 Turbo / `23049RAD8C` (`marble`) / Android 15 / API 35 / HyperOS 3.0
**序列号**：`be250aeb`（USB 连接）

---

## 0. 这台设备的关键价值

这台机器**不是新设备，而是已在册的验证机**。2026-10-10 它已经跑过 41/41 的完整 instrumented 套件。

它一次性解锁了三项此前无法推进的工作：

| # | 解锁的工作 | 依据 |
|---|---|---|
| 1 | **R3 中端机档位** —— R3 计划要求「主流中端安卓真机」+「旗舰机」两档，Note 12 Turbo（骁龙 7+ Gen 2 / 11GB RAM）正是中端机定位 | `R3-PERFORMANCE-TEST-PLAN.md` §Device matrix 第 1 项 |
| 2 | **R2 的 HyperOS 行补齐** —— 该行当前是 Partial pass，缺手测场景 | `DEVICE-MATRIX.md` 第 27 行 |
| 3 | **商店素材拍摄** —— R4 最后一项遗留工作 | `STORE-ASSET-CAPTURE-SCRIPT.md` |

**它不能解锁的**：R3 的旗舰机档位、vivo/iQOO、Honor、Samsung、物理 Google Pixel。这些仍需另外的设备。

---

## 1. 设备准备

### 1.1 连接

```bash
# USB 连接后开启开发者选项 → USB 调试
adb devices -l
# 期望看到：be250aeb  device usb:...
```

**若显示 `unauthorized`** —— 在手机弹出「允许 USB 调试吗？」时点允许。

### 1.2 设备身份核实（必做，不可跳过）

上次就是因为跳过了这一步，把一台虚拟设备误当成了物理 Pixel。R2/R3 的所有数据都建立在物理性之上。

```bash
adb -s be250aeb shell getprop ro.product.model      # 期望 Redmi Note 12 Turbo / 23049RAD8C
adb -s be250aeb shell getprop ro.kernel.qemu        # 期望空
adb -s be250aeb shell uname -a                      # 主机名不应为 localhost
adb -s be250aeb shell cat /proc/cpuinfo | grep Hardware   # 物理机应有 Hardware 行
adb -s be250aeb shell dumpsys sensorservice | grep -i "Sensor |" | head -5
adb -s be250aeb shell dumpsys thermalservice | grep "Thermal Status"
```

**通过标准**：
- `ro.kernel.qemu` 为空
- 主机名不是 `localhost` 或通用名
- `/proc/cpuinfo` 有 `Hardware` 行
- 传感器厂商与小米一致，**且存在陀螺仪/重力/旋转向量传感器**（虚拟设备不会有）
- `Thermal Status` 为 0

### 1.3 环境状态

| 项目 | 要求 | 命令 |
|---|---|---|
| 电量 | ≥ 50% | `adb shell dumpsys battery \| grep level` |
| 温度 | 无节流 | `adb shell dumpsys thermalservice \| grep "Thermal Status"` |
| 存储 | ≥ 5 GB 可用 | `adb shell df /data` |
| 系统更新 | 无进行中 | 设置 → 关于手机 |

### 1.4 HyperOS 已知事项

| 事项 | 影响 | 处理 |
|---|---|---|
| 「后台弹出界面」权限 | **只影响 instrumented 测试宿主**（Compose 测试 Activity 被 MIUILOG 拦截）。Release 应用正常启动不需要它 | 跑 instrumented 前开启：`adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow` 或手动在系统设置中开 |
| 包可见性 | 无 `QUERY_ALL_PACKAGES`，仅能枚举有桌面图标的应用 | 预期行为，非缺陷 |
| 已安装应用占用 | 需在系统设置手动开启「使用情况访问」 | `adb shell appops set com.cleanguard.app GET_USAGE_STATS allow` 可脚本化 |

---

## 2. 执行顺序

**顺序有讲究**：拍摄放最前（不依赖数据集，做完彻底解锁 R4），性能采集放最后（最耗时，且需要设备处于稳定温度状态）。

### 阶段 1：商店素材拍摄（1–2 小时）

| 步 | 事项 | 参考 |
|---|---|---|
| 1.1 | 准备无隐私演示数据（`Pictures/DemoMedia/` 等） | `STORE-ASSET-CAPTURE-SCRIPT.md` §0 |
| 1.2 | 采集 7 张截图 | 同上 §1 |
| 1.3 | 制作 1024×500 特色图 | 同上 §2 |
| 1.4 | 导出 512×512 图标 | 同上 §3 |
| 1.5 | 拍摄后自检（无隐私内容、无「一键清理」观感） | 同上 §5 |
| 1.6 | 更新 `STORE-LISTING.md` §5 素材状态 | — |

> **中国大陆市场的额外注意**：商店页需要符合 Google Play 中国区（大陆）的政策环境。若同时上架国内安卓渠道（华为、小米、OPPO 等），商店文案需避免「唯一」「最」等绝对化用语 —— 本项目的文案本身已避免，可直接复用。

### 阶段 2：R2 手测场景补齐（半天）

执行 `MANUAL-SCENARIO-SCRIPTS.md` 的 12 个场景，重点是这台机器当前缺失的部分：

| 场景 | 说明 | 对应 R2 缺口 |
|---|---|---|
| M-01 部分照片授权 | Android 14+ 专有路径 | ✓ |
| M-02 SAF 授权与撤销 | | ✓ |
| M-03 回收站确认与取消 | | ✓ |
| M-04 风险规则生效 | | ✓ |
| M-05 进程回收后恢复 | HyperOS 后台策略敏感 | ✓ |
| M-06 低内存与后台回收 | | ✓ |
| M-07 长时运行稳定性 | 30 分钟连续使用 | ✓ |
| M-08 权限撤销降级 | | ✓ |
| M-09 APK 识别 | | ✓ |
| M-10 安装包与升级 | | ✓ |
| M-11 Android 10 回收站不可用 | **本机不适用**（Android 15） | — |
| M-12 ROM 定制行为 | **HyperOS 专项** | ✓ |

**Android 15 的额外检查**（超出 M-01 范围）：

| 检查项 | 说明 |
|---|---|
| 部分照片授权（Photo Picker 路径） | Android 15 的 Photo Picker 有新权限模型，注意区分 `READ_MEDIA_VISUAL_USER_SELECTED` 与 Photo Picker 的选择 |
| 前台服务限制 | Android 14+ 对前台服务有限制。应用不应使用前台服务，确认没有 |
| 通知权限 | 应用无通知，确认未申请 `POST_NOTIFICATIONS` |
| 精确闹钟限制 | 不适用（应用无闹钟） |
| 非可安装应用 | Android 14+ 限制非默认可安装应用，确认未请求 `REQUEST_INSTALL_PACKAGES` |

### 阶段 3：R3 性能采集（半天到一天）

**设备档位：中端机**（第 1 档）。

| 步 | 事项 | 参考 |
|---|---|---|
| 3.1 | 生成 10k 数据集 | `tools/r3/generate-fixture.sh be250aeb 10000` |
| 3.2 | 采集 10k 档：全量扫描、无变更增量扫描、100 增改删 | `R3-PERFORMANCE-TEST-PLAN.md` |
| 3.3 | 生成并采集 50k 档（含 100 增改删） | 同上 |
| 3.4 | 生成并采集 100k 档（含内存与滚动） | 同上 |
| 3.5 | 冷启 10 次 + 热启 10 次 | `StartupBenchmark` |
| 3.6 | 各页面滚动 5 次 | `HomeScrollBenchmark` |
| 3.7 | 内存快照（扫描前/峰值/完成/静置 5 分钟） | 性能报告规程 |
| 3.8 | 清理 fixture，恢复 signed Release | 同上 §Measurement procedure |

**⚠️ Macrobenchmark 在这台机器上的可行性未验证。** 上次在虚拟 Android 12 平台上 Macrobenchmark 无法完成任何一次迭代。Android 15 真机上的表现需要实测。如果仍然跑不动，用已验证的直驱路径：

```bash
# 直驱扫描器测量（绕开 Macrobenchmark）
./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cleanguard.app.data.media.R3ScanPerformanceTest
./gradlew.bat :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.cleanguard.app.data.media.R3ScanMutationScenarioTest
```

**先跑一次 2 迭代的小规模验证**（约 5 分钟），确认可行再跑完整套件。

---

## 3. 需要采购的设备（这台机覆盖不到的）

| 缺口 | 为什么这台机不行 | 建议 |
|---|---|---|
| R3 旗舰机档位 | Note 12 Turbo 是中端定位 | 借一台旗舰机，或用云真机 |
| vivo / iQOO | 不同 ROM | 云真机或借机 |
| Honor / MagicOS | 不同 ROM | 云真机或借机 |
| Samsung / One UI | 不同 ROM | 云真机或借机 |
| 物理 Google Pixel | 不同 ROM | 云真机或借机 |

**在中国大陆市场做，vivo/OPPO/荣耀/小米四家是主要玩家。** 这台红米覆盖了小米，OPPO A58 5G 已在库（ColorOS 13）。剩下 vivo 和荣耀建议优先借/租。

---

## 4. 中国大陆市场的适配影响

这个信息很重要 —— 意味着发布策略可以调整。

### Google Play 中国区（大陆）的现状

Google Play 在中国大陆的可用性受限，且 2023 年后国内实体店与 OEM 商店是主要分发渠道。因此：

| 判断 | 影响 |
|---|---|
| Google Play 中国区可用性有限 | **14 天封闭测试门槛的实际影响变小**，但不能因此跳过 —— 若计划同时上架国内渠道，Google Play 仍可作为海外分发的补充 |
| **国内 OEM 渠道是主战场** | 小米、华为、OPPO、vivo、荣耀的应用商店需要各自的开发者账号与审核 |
| 应用宝、华为市场等第三方渠道 | 需要软著、ICP 等资质要求（个人开发者通常无法通过） |

### 因此建议调整优先级

| 原优先级 | 调整后 | 理由 |
|---|---|---|
| 1. Play Console 全部填完 | **2** | Play 中国区可用性有限，优先级下调 |
| — | **1. 国内渠道评估** | 先摸清各渠道的开发者资质要求（尤其是否需要企业主体），这决定了整个商业模式的可行性 |
| 2. 采购真机 | **1（保留最高）** | 一台设备同时服务 R2/R3/素材/Play 验证，无替代 |

### 国内渠道的额外合规要求（与 Play 不同）

| 项目 | 说明 |
|---|---|
| **软件著作权登记证书** | 多数国内商店强制要求。个人可申请，周期约 30–60 天。**这是比 Play 更长的前置项** |
| ICP 备案 / 网络文化服务许可 | 若应用含"清理加速"等可能被判定为网络工具类功能的描述，视平台而定 |
| 隐私合规独立评估 | 部分商店要求提供《隐私政策》与《用户协议》并做独立隐私检测报告 |
| 应用分类与资质 | 「工具」类通常无特殊资质，但若宣传含"加速手机""优化内存"可能触及特定分类 |
| 实名认证 | 国内渠道要求开发者实名认证（个人身份证或企业营业执照） |

> **软件著作权登记值得立刻评估。** 它是多数国内商店上架的硬性前置，周期长（30–60 天），而且与 Play 无关。如果 CleanGuard 要走国内渠道，这比 14 天测试窗口更早启动更好。

### 文案层面

CleanGuard 的定位（不联网、不上传、只处理你确认的内容）在国内的「隐私」叙事上比海外更受欢迎，这是优势。商店文案可保持现状。

但需注意：**避免任何暗示"清理手机内存/加速手机"的表述**。国内审核对这类宣称较敏感，而 CleanGuard 实际上也不做这些。

---

## 5. 执行清单

### 设备到位当天

- [ ] USB 连接，确认 `adb devices` 显示 `be250aeb`
- [ ] 执行 §1.2 设备身份核实（**必须全部通过**）
- [ ] 记录环境快照（电量/温度/存储/构建指纹）
- [ ] 开启 HyperOS「后台弹出界面」（仅测试需要）

### 阶段 1：素材

- [ ] 准备演示数据
- [ ] 采集 7 张截图
- [ ] 制作特色图与图标
- [ ] 自检
- [ ] 更新 `STORE-LISTING.md`

### 阶段 2：手测

- [ ] 执行 12 个场景 + Android 15 额外检查
- [ ] 更新 `DEVICE-MATRIX.md` 的 HyperOS 行
- [ ] 记录到 `OEM-DEVICE-VALIDATION-REPORT.md`

### 阶段 3：性能

- [ ] 小规模验证 Macrobenchmark 可行性（2 迭代）
- [ ] 10k / 50k / 100k 三档采集
- [ ] 冷启/热启/滚动
- [ ] 内存快照
- [ ] 清理 fixture，恢复 Release
- [ ] 更新 `PERFORMANCE-REPORT.md`

### 与 Play 并行（不依赖设备）

- [ ] 确认 Play 账号是否已注册（见 §6）
- [ ] 若未注册 → 决定个人/组织 → 注册
- [ ] 若选组织 → **立刻申请 D-U-N-S**
- [ ] 用 Play Console 移动端 App 完成设备验证（**用这台红米**）
- [ ] 填 5 份表单（`PLAY-CONSOLE-FILL-IN-PACKAGE.md`）
- [ ] 上传 AAB 到内部测试轨道
- [ ] 招募 20 名封闭测试者

---

## 6. 怎么确认 Play 账号是否已注册

**30 秒判断法**：

1. 浏览器打开 <https://play.google.com/console>
2. 用 `jinge091118@gmail.com` 登录（或你注册时用的那个 Google 账号）
3. 看落地页：

| 看到什么 | 结论 |
|---|---|
| 直接进控制台，能看到应用列表 | **已注册** |
| 进控制台但显示「您还没有应用」+「创建应用」 | **已注册**，只是没创建过应用 |
| 跳转到注册/付费页，或提示「您尚未注册 Play 开发者账号」 | **未注册** |
| 要求验证身份并显示开发者账号类型（个人/组织） | **已注册**，但身份验证未完成 |

**其他线索**：

- 检查 `play.google.com/console` 的浏览器书签
- 检查是否收到过来自 `googleplay-developer-notifications@google.com` 的邮件（搜索这个发件人）
- 检查是否付过 25 美元（Google Play 的收据邮件）
- 如果之前上传过应用，会收到过 "Your app has been uploaded" 通知

**如果你已经上传过应用**，务必检查 Console 首页的「版本代码」记录 —— 那个 version code **绝对不能复用**。当前 AAB 的 version code 是 2。

---

## 7. 时间线（这台机到位后）

| 阶段 | 时长 | 并行项 |
|---|---|---|
| 阶段 1 素材 | 1–2 小时 | Play 表单填写 |
| 阶段 2 手测 | 半天 | Play 上传 + 内部测试招募 |
| 阶段 3 性能 | 半天–1 天 | 封闭测试 14 天窗口 |
| **合计** | **2–3 天** | — |

与 Play 的 14 天窗口叠加后，从设备到位到上架约 **3–4 周**（个人账号）。

---

## 8. 相关文档

| 文档 | 用途 |
|---|---|
| `R3-PERFORMANCE-TEST-PLAN.md` | 性能采集规程与档位要求 |
| `MANUAL-SCENARIO-SCRIPTS.md` | 12 个手测场景 |
| `STORE-ASSET-CAPTURE-SCRIPT.md` | 商店素材拍摄 |
| `DEVICE-MATRIX.md` | 设备矩阵（需更新 HyperOS 行） |
| `OEM-DEVICE-VALIDATION-REPORT.md` | OEM 验证记录（需更新） |
| `PERFORMANCE-REPORT.md` | 性能报告（需更新） |
| `PLAY-CONSOLE-FILL-IN-PACKAGE.md` | Play 表单逐题答案 |
| `PLAY-CONSOLE-SETUP-CHECKLIST.md` | Play 首次设置与账号类型决策 |
| `tools/r3/generate-fixture.sh` | 数据集生成 |
| `tools/release/verify-aab.py` | AAB 上传前校验 |
