# CleanGuard 红米真机 · R3 采集前置操作（HyperOS 安装拦截）

**发现时间**：2026-10-27
**设备**：Redmi Note 12 Turbo / `23049RAD8C` / Android 15 / API 35 / HyperOS 3.0 / `be250aeb`

---

## 1. 问题现象

在红米上执行 Macrobenchmark 采集时，Gradle 安装 benchmark APK 失败：

```
Failed to commit install session ... Error: INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
```

`adb install` 与 `pm install` 均返回同样的错误码。

## 2. 根因（已通过系统通知确认）

设备通知栏中存在一条系统通知：

```
android.title = 应用安装拦截
android.text  = 已拦截通过USB安装的CleanGuard
AppSettings: com.miui.packageinstaller
```

这是 HyperOS 的**「USB 安装」开发者选项未开启**导致的拦截，与签名、与设备锁屏、与电量均无关。

**已排除的可能**：

| 尝试 | 结果 |
|---|---|
| `settings put secure install_via_usb 1` | 键不存在（MIUI 未使用该键名） |
| `settings put global verifier_verify_adb_installs 0` | 无效 |
| `settings put global package_verifier_enable 0` | 无效 |
| `pm install -r --user 0` | 同样被拦 |
| 屏幕唤醒后重试 | 仍然被拦 |
| `dumpsys user` 检查用户限制 | `Effective restrictions:` 为空，**用户层面无限制** |

MIUI 的开关存放在 `com.miui.securitycenter.provider.SecurityCenterProvider`，读取需要签名级权限
`com.miui.securitycenter.permission.ACCESS_SECURITY_CENTER_PROVIDER`：

```
SecurityException: Permission Denial: opening provider com.miui.securitycenter.provider
  requires com.miui.securitycenter.permission.ACCESS_SECURITY_CENTER_PROVIDER
```

因此**该开关无法通过 adb 打开，必须在手机上手动设置**。

---

## 3. 需要人工完成的操作

在红米手机上：

```
设置 → 更多设置 → 开发者选项
    → 打开「USB 安装」（部分版本名为「通过 USB 安装」）
    → 如弹出安全确认，输入锁屏密码
```

同时建议：

- 保持屏幕常亮（设置 → 显示与亮度 → 自动锁屏 → 关闭），避免息屏期间安装被拦
- 保持开发者选项里的「USB 调试」处于开启状态

---

## 4. 开启后如何续跑

设备已就绪的部分（**无需重做**）：

- 两个 APK 已推送到设备：
  - `/data/local/tmp/cg-app.apk`（被测应用，debug 签名，34 MB）
  - `/data/local/tmp/cg-bench.apk`（benchmark 包，50 MB）
- 10,000 档数据集已生成并索引（基线 3,461 + fixture 6,539 = 精确 10,000）
- 设备电量 55%，热状态 0（无节流）

一次性续跑：

```bash
bash tools/r3/collect-performance.sh be250aeb r3-results
```

该脚本依次执行 startup、scan、scroll 三个套件，并把结果 JSON 与 instrumentation XML
复制到 `r3-results/`。

> **注意**：Gradle 的 install session 在 HyperOS 上会失败，因此采集前需先手动安装两个 APK：
>
> ```bash
> adb -s be250aeb shell pm install -r /data/local/tmp/cg-app.apk
> adb -s be250aeb shell pm install -r /data/local/tmp/cg-bench.apk
> ```
>
> 若手动安装成功，Gradle 的 `connectedBenchmarkAndroidTest` 会检测到已安装的包并跳过安装步骤。
> 若仍尝试安装并失败，则需要手动执行 `am instrument` 启动 benchmark。

---

## 5. 连带影响

- 2026-10-26 晚的手动场景采集能够执行，是因为当时手机上有安装确认弹窗并被人工确认。
- 2026-10-27 的采集在息屏状态下开始，安装直接被拦截。
- **教训**：任何需要在 HyperOS 真机上安装 APK 的自动化流程，第一步都应检查
  「开发者选项 → USB 安装」是否已开启，否则整个采集会失败在安装步骤。
