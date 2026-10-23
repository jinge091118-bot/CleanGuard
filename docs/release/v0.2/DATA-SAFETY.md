# CleanGuard V0.2 Google Play Data Safety 声明

**状态**：草案，待项目所有者批准后方可在 Play Console 填写
**应用**：`com.cleanguard.app` / version code 2 / version name 0.2.0
**事实依据**：`PRIVACY-POLICY.md` 与 2026-10-23 完成的代码审计（下文「审计依据」段）

---

## 填写指引

Play Console 的 Data Safety 表单分为两大部分。本表逐项给出**应选答案**与**理由**。

> 关键判定：Play 的 Data Safety 表单问的是数据是否**离开设备**（传输到开发者或第三方可访问的位置）。CleanGuard 没有网络能力，数据从未离开设备，因此绝大多数项目应选「不收集 / 不共享」。

---

## 第一部分：数据是否收集或共享

### 总体答案

> **CleanGuard 不收集、不共享任何用户数据。**

### 数据类型逐项对照

| Play 表单项 | 应选 | 理由 |
|---|---|---|
| 位置信息 | 不收集 | 无 `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`，代码无 `LocationManager` 调用 |
| 个人信息 | 不收集 | 无姓名、邮箱、身份证件、地址等字段；无账号体系 |
| 财务信息 | 不收集 | 无支付相关代码 |
| 健康与健身 | 不收集 | 无相关权限与实现 |
| 消息 | 不收集 | 无 `SmsManager`、无 `MmsProvider`、无通知读取 |
| 通讯录 | 不收集 | 无 `ContactsContract` 调用 |
| 应用活动 | 不收集 | 无 `AppActivity` 类事件上报；无埋点 SDK |
| 网页浏览 | 不收集 | 无 `WebView`，无 `loadUrl` |
| 应用信息与性能 | **不收集**（见下方专项说明） | 读取本机应用占用的**汇总字节数**用于向用户展示，**不传输、不离开设备** |
| 文件与文档 | **不收集**（见下方专项说明） | 读取媒体索引元数据、计算本机哈希用于本地展示，**不传输、不离开设备** |
| 日历 | 不收集 | 无 `CalendarContract` 调用 |
| 联系人 | 不收集 | 同通讯录 |
| 应用安装与卸载 | 不收集 | 无此行为；包可见性查询结果不传输 |
| 用户互动 | 不收集 | 无此行为 |
| 设备或其他 ID | 不收集 | 无设备标识符采集；无广告 ID |
| 诊断信息 | 不收集 | 无崩溃上报 SDK、无日志上传 |
| 其他 | 不收集 | 无其他采集 |

### 专项说明：文件与文档

**为什么选「不收集」**

「收集」在 Play 的定义中指数据被**传输离开设备**，且开发者或第三方可访问。CleanGuard 的情况：

- 常规扫描只查询 MediaStore 索引元数据（文件名、类型、大小、修改时间、目录）
- 重复文件核对在本机流式计算 SHA-256，哈希值与元数据存入本机 Room 数据库
- 下载目录仅在用户通过系统选择器授权后只读遍历
- 这些数据**从未被传输**，且应用**没有传输通道**（未声明 `INTERNET`）

**审核问询时的准备回答**

> CleanGuard 没有网络权限，无法传输任何数据。应用仅在本机读取 MediaStore 元数据并在本地数据库中保存分析结果，用于在应用内展示存储占用。所有分析结果均不离开设备。

### 专项说明：应用信息与性能

**为什么选「不收集」**

- 仅枚举**有桌面图标的应用**（`<queries>` 声明 MAIN/LAUNCHER）
- 每个应用只取系统提供的汇总字节数（app/data/cache bytes）
- 不读取应用内部文件，不读取已安装应用列表用于上报
- 数值仅在应用内「空间洞察」页展示，不传输

---

## 第二部分：数据的安全与删除

| Play 表单项 | 应选 | 理由 |
|---|---|---|
| 数据在传输过程中是否加密 | **不适用 / 无数据传输** | 应用无 `INTERNET` 权限，不存在传输行为 |
| 您是否要求删除数据 | 无需（无服务器端数据） | 所有数据保存在应用私有目录；卸载应用即全部删除；用户也可在应用内清除哈希缓存与忽略规则 |
| 数据是否加密静态存储 | 说明：依赖应用私有目录的系统级保护 | 应用私有目录由 Android 基于应用 UID 隔离；未申请 `MANAGE_EXTERNAL_STORAGE`，无法读取其他应用的私有数据 |
| 是否提供删除数据的途径 | 说明：无收集即无删除义务 | 同上 |

---

## 第三部分：附加声明

| 项目 | 答案 | 说明 |
|---|---|---|
| 应用是否包含广告 | 否 | 无广告 SDK |
| 应用是否内嵌第三方 SDK | 否 | 依赖全部为 AndroidX / Kotlin 官方库，无第三方 SDK |
| 是否使用应用信标 | 否 | 无 `com.google.android.gms.permission.AD_ID`，无广告 ID 采集 |
| 应用是否基于 Play 安装需迁移 | 不适用 | 无 Play 迁移逻辑 |
| 是否使用 AR 核心 | 否 | — |
| 应用是否使用 Wear OS | 否 | — |
| 应用是否面向儿童 | 否 | — |

---

## 审核问询准备

### 可能被问到的敏感权限

**`PACKAGE_USAGE_STATS`（使用情况访问）**

这是本应用唯一被 Play 敏感权限政策覆盖的权限，需要走 Play Console 的受限权限申请流程并填写使用理由。

**建议提交的使用理由**

> CleanGuard 是一款本地存储分析工具。其「空间洞察」功能需要向用户展示各应用的汇总存储占用，以帮助用户判断哪些应用占用了过多空间。Android 系统仅在用户于系统设置中手动授予「使用情况访问」后才向应用提供此数据。CleanGuard 不会尝试绕过该限制，不会在应用启动时请求，且仅读取汇总字节数（应用占用、数据占用、缓存占用），不读取任何应用内部文件，也不会清理其他应用的缓存。所有数值仅在应用内展示，不离开设备。

**`READ_MEDIA_VISUAL_USER_SELECTED`**

这是 Android 14 新增的权限，Play 政策将其视为照片/视频权限的一部分。CleanGuard 仅用其判定「部分授权」状态，不用于扩大访问范围。

---

## 填写时容易出错的地方

1. **不要勾选「数据已加密传输」**。应用没有传输行为，勾选此项等于暗示存在数据传输。
2. **不要勾选任何「收集」项**，即使应用读取了照片或应用信息。判据是「是否离开设备」，不是「是否被读取」。
3. **不要声明「收集用于分析/改进产品」**。CleanGuard 无任何分析或上报实现。
4. **「文件与文档」与「应用信息」是审核最可能追问的两项**，两个专项说明已备好，可直接引用。
5. **敏感权限声明与 Data Safety 是两套表单**。Data Safety 填完后仍需在 Play Console 的「应用内容」区域完成使用情况访问权限的单独申请与声明。
6. **隐私政策 URL 需为公开可访问地址**。`docs/release/v0.2/PRIVACY-POLICY.md` 是源文件，托管为公开 URL 前不得提交审核。

---

## 一致性检查清单

提交前逐项确认：

- [ ] 隐私政策 URL 可公开访问，内容与 `PRIVACY-POLICY.md` 一致
- [ ] Data Safety 答案与本文档完全一致
- [ ] 权限声明表（`PHOTO-VIDEO-PERMISSION-DECLARATION.md`）与 Manifest 声明一致
- [ ] 商店文案中的隐私相关表述不超出本文档范围
- [ ] 应用内隐私说明与 `PRIVACY-POLICY.md` 无矛盾
- [ ] 已确认发布版本的代码与审计时的代码一致（审计基于 V0.2.0 / version code 2）
- [ ] 已完成 `PACKAGE_USAGE_STATS` 敏感权限的 Play Console 申请准备

---

## 审计依据摘要

以下结论来自对 `app/src/main/` 的全量静态审计（2026-10-23）：

| 检查项 | 关键字 | 结果 |
|---|---|---|
| 网络代码 | `java.net`、`HttpURLConnection`、`OkHttp`、`Socket`、`WebView` | 0 处（仅 XML 命名空间） |
| 网络权限 | `permission.INTERNET` | 合并后 Manifest 0 处 |
| 第三方 SDK | `app/build.gradle.kts` dependencies | 全部 AndroidX / Kotlin |
| 外部存储写入 | `getExternalStoragePublicDirectory`、`MediaStore.Downloads` | 0 处 |
| ContentResolver 写入/删除 | `insert(`、`ContentValues`、`resolver.delete` | 0 处（仅 Room DAO 写入本机库） |
| 埋点/日志/崩溃上报 | `Log.`、`Timber`、`Firebase`、`Crashlytics`、`Sentry` | 0 处 |
| 后台任务 | `WorkManager`、`Service`、`BroadcastReceiver`、`ContentObserver` | 0 处 |
| 敏感 API | `SmsManager`、`ContactsContract`、`CallLog`、`AccessibilityService`、`ClipboardManager` | 0 处 |
| 包可见性 | `QUERY_ALL_PACKAGES` | 0 处；仅 `<queries>` MAIN/LAUNCHER |
| 永久删除 | `createDeleteRequest` | 0 处；仅 `createTrashRequest`（系统回收站） |

**审计边界**：本次为静态代码审计，未做运行时网络抓包。若存在通过 JNI、反射或动态下载引入网络能力的代码，本审计无法覆盖 —— 项目内未发现任何反射加载或动态代码下载实现。

完整的逐条证据（含文件路径与行号）见 `PRIVACY-POLICY.md` 与本目录下的 `COMPLIANCE-AUDIT.md`。
