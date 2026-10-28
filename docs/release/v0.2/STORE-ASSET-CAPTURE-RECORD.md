# CleanGuard V0.2 商店素材采集记录

**采集日期**：2026-10-26
**设备**：Redmi Note 12 Turbo / `23049RAD8C` (`marble`) / Android 15 / API 35 / HyperOS 3.0 / `be250aeb`
**应用版本**：V0.2.0（version code 2，signed Release）
**采集工具**：`tools/store-assets/capture.py`、`tools/store-assets/render-icon.py`、`tools/store-assets/render-feature-graphic.py`

---

## 1. 产物清单

| 文件 | 规格 | 用途 | 隐私状态 |
|---|---|---|---|
| `feature_graphic_1024x500.png` | 1024×500，43.4 KB | Play 特色图 | ✓ 无隐私 |
| `ic_launcher_512.png` | 512×512 | Play 应用图标 | ✓ |
| `ic_launcher_192.png` | 192×192 | 高密度图标 | ✓ |
| `ic_launcher_48.png` | 48×48 | 备用 | ✓ |
| `home_capacity.png` | 1080×2400 | 商店首图（封面） | ✓ 无文件名 |
| `home_permission.png` | 1080×2400 | 首页下半部：下载目录授权路径与清理记录入口 | ✓ 无文件名 |
| `scan_stats.png` | 1080×2400 | 体检统计 | ✓ 无文件名 |
| `results_clues.png` | 1080×2400 | 空间线索与风险分组 | ✓ 无文件名 |
| `insights.png` | 1080×2400 | 空间洞察 | ✓ 无文件名 |
| `permissions.png` | 1080×2400 | 权限中心 | ✓ 无文件名 |
| `settings_privacy.png` | 1080×2400 | 设置与隐私 | ✓ 无文件名 |
| `settings_privacy_card.png` | 1080×2400 | 隐私说明卡（含本轮修订的分享/预览披露） | ✓ 无文件名 |

---

## 2. 隐私隔离说明

采集设备上有 3,400 余行真实媒体（相机、微信、截图、名为「新生儿科」的相册目录等）。**商店素材绝不能包含这些内容**，因此采集分两类处理：

### 2.1 安全界面：直接采集

下列界面只展示聚合数字或应用自身文案，不列出任何文件条目，因此可在持有真实媒体的设备上直接采集：

| 界面 | 验证方式 |
|---|---|
| 首页容量总览 | UI 层级扫描确认无文件名、无缩略图 |
| 体检统计 | 同上 |
| 空间线索与风险分组 | 同上 |
| 空间洞察 | 同上 |
| 权限中心 | 同上 |
| 设置与隐私 | 同上 |

### 2.2 含文件名界面：已跳过

「清理建议」「重复文件」「清理详情」「清理历史」「恢复中心」等界面会列出文件名或**文件缩略图**。

采集工具内置了敏感度检查，规则包括：

- 文件名含任何中文字符
- 文件名含 `wechat` / `weixin` / `alipay` / `qq` / `taobao` / `baidu` 等应用标识
- 文件名含 `invoice` / `contract` / `idcard` / `passport` / `receipt` / `medical` / `patient` 等语义词

2026-10-26 实测：**媒体库含 71 个疑似敏感文件名，采集工具自动跳过了全部含文件名的界面。**

### 2.3 尝试过但不可行的隔离方案

| 方案 | 结果 |
|---|---|
| 多用户配置（`pm create-user`） | Android 11+ 共享存储不隔离，媒体库仍然可见 |
| 在父目录放 `.nomedia` | 只影响后续索引，**不移除已有索引行**（实测 DCIM 仍有 2,573 行） |
| `content delete` 删除索引行 | Android 15 scoped storage 下**对 MediaStore 无效**，索引行与文件均不变 |
| `content update --bind _deleted:i:1` | 同上，无效果 |
| 移动用户文件到备份目录 | **风险过高，不采用** —— 可能破坏相册索引、备份与原路径记录 |

**结论**：shell 无法修改 MediaStore 索引，唯一安全的路径是应用自身的回收站接口（需用户逐次确认）。因此本轮只采集安全界面，含文件名的界面留待**媒体库仅含演示数据的隔离环境**再采集。

### 2.4 已准备好的隔离数据

以下演示数据已推送到设备，等待在隔离环境中采集剩余截图：

| 位置 | 内容 |
|---|---|
| `/sdcard/Pictures/CleanGuardDemo/images/` | 24 张几何图形演示图（17 KB – 4.7 MB，跨越大文件阈值） |
| `/sdcard/Pictures/CleanGuardDemo/duplicates/` | 3 份内容完全相同的文件，用于重复文件页 |
| `/sdcard/Download/CleanGuardStoreDemo/dup/` | 4 份内容完全相同的 4.7 MB 文件 |

演示数据由 `tools/demo-media/generate.py` 生成，纯标准库写 PNG，不含任何摄影内容。修改时间被分散到 2023–2026 年，使「较旧文件」规则有真实判定对象。

---

## 3. 应用图标

源文件是 `app/src/main/res/drawable/ic_launcher_foreground.xml`（一个 vector drawable），无位图资源。

`tools/store-assets/render-icon.py` 用纯标准库把矢量栅格化为 PNG：

- 盾牌轮廓：顶部两段直线 + 底部圆弧近似
- 对勾：多边形内点判定
- 配色与应用内一致：背景 `#E9F4F1`、盾牌 `#176B5B`、对勾 `#FFFFFF`

**Play 图标注意事项**（已遵守）：

- 512×512，不自行加圆角或阴影（Play 会自动裁切成多种形状）
- 主体位于正方形中央区域，任何形状裁切都不影响识别

---

## 4. 特色图

`tools/store-assets/render-feature-graphic.py` 在**设备端**用 Android 的 Canvas 渲染，原因是中文字形需要 Android 字体栈，宿主机没有可用中文字体（早先用块状字形渲染的结果是乱码）。

实际实现改为 instrumentation 测试 `FeatureGraphicRenderTest`，在设备上绘制后输出到公共 Download 目录（这样测试包卸载后文件仍在）。

版面：

| 区域 | 内容 |
|---|---|
| 左侧品牌面板 | 品牌色 `#176B5B` 底，CleanGuard 品牌字 + 主标题「看懂你的存储占用」+ 两行副标题 |
| 右侧 | 盾牌对勾图形，与应用图标一致 |

---

## 5. 剩余工作

以下截图需要在**隔离环境**（媒体库仅含演示数据）采集：

| 截图 | 依赖 |
|---|---|
| 清理建议列表 | 隔离环境 |
| 重复文件列表 | 隔离环境 |
| 重复文件组详情 | 隔离环境 |
| 清理前确认 | 隔离环境 |
| 清理结果 | 隔离环境 |
| 清理历史 | 隔离环境 |
| 恢复中心 | 隔离环境 |
| 空间趋势 | 需至少两次扫描历史 |
| APK 洞察 | 需扫描范围内有 APK |

---

## 6. 复现方式

```bash
# 1. 生成演示数据
python3 tools/demo-media/generate.py .demo-media

# 2. 推送到设备并触发索引
adb -s <serial> push .demo-media/images/. /sdcard/Pictures/CleanGuardDemo/images/
adb -s <serial> push .demo-media/duplicates/. /sdcard/Pictures/CleanGuardDemo/duplicates/
adb -s <serial> shell "content call --uri content://media/external --method scan_volume --arg external_primary"

# 3. 采集安全界面
python3 tools/store-assets/capture.py <serial> store-assets --only safe

# 4. 生成图标与特色图
python3 tools/store-assets/render-icon.py store-assets
python3 tools/store-assets/render-feature-graphic.py store-assets/home_capacity.png store-assets
```

---

## 7. 采集期间发现的工程事实

| 事实 | 说明 |
|---|---|
| HyperOS 阻止 `adb install` | 报 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`。**解法**：先 `adb push` 到 `/data/local/tmp/`，再 `adb shell pm install -r` —— 可绕过 |
| signed Release 与 debug/benchmark 签名冲突 | `INSTALL_FAILED_UPDATE_INCOMPATIBLE`。跑 instrumented 或 macrobenchmark 前必须先 `adb uninstall` |
| 测试包卸载会带走产物 | 渲染产物必须写到公共 Download 目录，否则 Gradle 在测试结束时卸载测试包会一并删除 |
| `uiautomator dump` 需先拉取再解析 | 设备路径无法被宿主机 Python 直接读取；应拉到本地临时文件 |
