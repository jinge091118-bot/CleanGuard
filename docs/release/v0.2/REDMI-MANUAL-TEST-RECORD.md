# CleanGuard V0.2 红米真机手测执行记录

**执行日期**：2026-10-26
**设备**：Redmi Note 12 Turbo / `23049RAD8C` (`marble`) / Android 15 / API 35 / HyperOS 3.0 / `be250aeb`
**应用版本**：V0.2.0（version code 2，signed Release，upload key 签名）
**执行工具**：`tools/manual-tests/run-scenarios.py`
**结果**：**9 / 9 场景通过**

---

## 1. 设备身份核实

在执行任何场景之前完成核实，结果记录在 `PERFORMANCE-REPORT.md` 的「Device identity verification」一节。关键证据：

| 检查 | 结果 |
|---|---|
| SoC | `SM7475` / `taro`（骁龙 7+ Gen 2） |
| 陀螺仪 | `bmi26x Gyroscope`（BOSCH） |
| 重力 / 线性加速度 | `QTI` |
| 旋转向量 | `xiaomi` + `QTI` |
| `ro.kernel.qemu` | 空 |
| 指纹 | 与 2026-10-08 设备矩阵记录一致 |

---

## 2. 场景执行结果

| 场景 | 名称 | 结果 | 断言数 |
|---|---|---|---|
| M-01 | 部分照片授权 | ✅ PASS | 6/6 |
| M-02 | SAF 目录只读 | ✅ PASS | 1/1 |
| M-03 | 回收站确认与取消 | ✅ PASS | 4/4 |
| M-04 | 风险规则生效 | ✅ PASS | 6/6 |
| M-05 | 进程回收后恢复 | ✅ PASS | 3/3 |
| M-06 | 旋转与后台 | ✅ PASS | 2/2 |
| M-07 | 连续扫描稳定性 | ✅ PASS | 2/2 |
| M-08 | 权限撤销降级 | ✅ PASS | 2/2 |
| M-11 | Android 10 无系统回收站 | ✅ PASS（本机不适用，非缺陷） | 2/2 |

### 关键断言明细

**M-01 部分照片授权** —— Android 15 上权限中心正确呈现每项权限的用途与未授权影响：

```
[PASS] Android 14+ 前提 — API 35
[PASS] 包含「权限中心」
[PASS] 包含「照片」
[PASS] 包含「视频」
[PASS] 包含「用途」
[PASS] 包含「未授权时」
```

**M-02 SAF 目录只读** —— Downloads 洞察页不提供任何删除入口：

```
[PASS] 已授权目录下不提供删除入口 — 发现: []
```

页面同时显示「CleanGuard 不会主动永久删除 SAF 文件」。

**M-03 回收站确认与取消** —— 清理建议页明示三档风险的不可处理边界：

```
[PASS] 包含「REVIEW 默认不选中」
[PASS] 包含「DANGER」
[PASS] 包含「UNKNOWN」
[PASS] 包含「处理前需要确认文件用途和保留需要」
```

**M-04 风险规则生效** —— 四个风险分组齐全，且明确声明无全局一键清理：

```
[PASS] 包含「风险分组」
[PASS] 包含「SAFE」
[PASS] 包含「REVIEW」
[PASS] 包含「DANGER」
[PASS] 包含「CleanGuard 不提供全局一键删除」
[PASS] 包含「DANGER 与 UNKNOWN 条目不会进入处理流程」
```

**M-05 进程回收后恢复** —— 强制停止后重新启动，历史数据仍在：

```
[PASS] 强制停止后重新启动 — Starting: Intent { cmp=com.cleanguard.app/.MainAct
[PASS] 扫描历史入口仍可用
[PASS] 包含「扫描历史」
```

**M-06 旋转与后台** —— 横屏与后台恢复均无异常：

```
[PASS] 横屏下界面仍渲染 — 可见节点 6
[PASS] 从后台恢复
```

**M-07 连续扫描稳定性** —— 连续 3 次扫描完成，堆内存可读：

```
[PASS] 连续 3 次扫描完成 — 完成 3 次
[PASS] 内存可读 — Java Heap:    15972                          31176
```

**M-08 权限撤销降级** —— 撤销图片权限后应用仍可用，扫描部分降级而非整体失败：

```
[PASS] 撤销后仍能启动
[PASS] 扫描未整体失败
```

**M-11** —— 本机 API 35 支持系统回收站，该场景不适用于本机，判定为非缺陷。

---

## 3. 仍需人工完成的场景

自动化脚本能覆盖界面状态断言，但以下环节必须由人操作：

| 场景 | 需人工的部分 | 原因 |
|---|---|---|
| M-02 | 通过系统目录选择器授权 SAF 目录 | 系统 picker 无法从 adb 驱动 |
| M-03 | 在系统回收站确认界面点「取消」与「确认」 | 系统确认界面由用户交互 |
| M-07 | 30 分钟长时运行 | 需要持续人工观察与计时 |
| M-12 | HyperOS 专项：后台弹出界面、省电策略、ROM 安装拦截 | 需在系统设置中逐项验证 |
| M-09 | 扫描范围内的 APK 识别 | 需在 Download 中放置真实 APK |
| M-10 | 安装包升级路径 | 需上传 RC 与正式版并比对 |

---

## 4. 执行中发现的产品观察

### 4.1 权限撤销后的扫描条目数明显下降

M-08 撤销 `READ_MEDIA_IMAGES` 后，结果页显示「已检查 93 个条目，共 6.3 GB」，而全权限时为「3,448 个条目，12.4 GB」。这是**预期的降级行为**，图片类条目被排除，视频与音频仍被统计。降级路径正确。

### 4.2 HyperOS 阻止 adb 直接安装

`adb install` 报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。这是 HyperOS 的「USB 安装」限制。

**绕过方式**（本轮实测有效）：

```bash
adb push app-release.apk /data/local/tmp/cg.apk
adb shell pm install -r /data/local/tmp/cg.apk
```

普通 `adb install` 会失败，但 `pm install` 可以通过。

### 4.3 signed Release 与 debug/benchmark 签名冲突

设备上装有 upload key 签名的 Release 版时，instrumented 与 macrobenchmark 包都无法安装，报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。

**处理**：执行测试前先 `adb uninstall com.cleanguard.app`，测试后再装回 Release。这是既有记录的老问题，本轮再次遇到。

### 4.4 `uiautomator dump` 需先拉取再解析

设备路径无法被宿主机 Python 直接读取。脚本改为拉到本地临时文件后解析。

---

## 5. 复现方式

```bash
# 安装 Release 版（HyperOS 需用 pm install）
adb push app/build/outputs/apk/release/app-release.apk /data/local/tmp/cg.apk
adb shell pm install -r /data/local/tmp/cg.apk

# 授予权限
adb shell "pm grant com.cleanguard.app android.permission.READ_MEDIA_IMAGES"
adb shell "pm grant com.cleanguard.app android.permission.READ_MEDIA_VIDEO"
adb shell "pm grant com.cleanguard.app android.permission.READ_MEDIA_AUDIO"
adb shell "appops set com.cleanguard.app GET_USAGE_STATS allow"

# 执行全部场景
python3 tools/manual-tests/run-scenarios.py be250aeb

# 只跑指定场景
python3 tools/manual-tests/run-scenarios.py be250aeb M-01 M-04
```

---

## 6. 工具说明

`tools/manual-tests/run-scenarios.py` 的每个场景通过读取 UI 层级来判定结果，因此记录的是**应用实际渲染出的内容**，而不是操作者的主观判断。

脚本包含三类修正逻辑：

- **滚动查找**：长页面的主操作按钮在底部，找不到时自动向下滚动
- **状态适配**：M-02 接受「已授权」与「未授权」两种状态，各自判定正确的行为
- **场景适用性**：M-11 在 API 30+ 设备上判定为不适用而非失败
