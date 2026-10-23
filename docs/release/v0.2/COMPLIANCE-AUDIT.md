# CleanGuard V0.2 合规事实审计

**审计日期**：2026-10-23
**审计对象**：`com.cleanguard.app` version code 2 / version name 0.2.0
**审计方式**：对 `app/src/main/` 的全量静态代码审计
**用途**：为 `PRIVACY-POLICY.md`、`DATA-SAFETY.md`、`PHOTO-VIDEO-PERMISSION-DECLARATION.md`、`REVIEWER-NOTES.md`、`STORE-LISTING.md` 提供事实依据

---

## 审计方法

1. 对 `app/src/main/` 全量文本检索敏感 API 与权限关键字
2. 逐文件精读 `platform/`、`data/media/`、`data/saf/`、`data/apps/`、`data/apk/`、`data/duplicate/`、`data/cleanup/`、`domain/rules/`
3. 核对合并后的 Manifest 实际权限（debug / benchmark / release 三个变体）
4. 核对 Gradle 依赖表，确认无第三方 SDK

---

## 1. 网络能力

**结论**：`app/src/main/` 中零网络访问代码，合并后 Manifest 无 `INTERNET` 权限。

| 检查 | 关键字 | 命中 |
|---|---|---|
| 网络代码 | `java.net`、`HttpURLConnection`、`OkHttp`、`Retrofit`、`Socket`、`URLConnection`、`ktor`、`WebView`、`loadUrl` | 3 处，全部为 XML 命名空间声明（`AndroidManifest.xml:2-3`、`ic_launcher_foreground.xml:1`） |
| 网络权限 | `permission.INTERNET` | 合并 Manifest 0 处（三个变体实测） |
| 第三方 SDK | `app/build.gradle.kts:180-213`、`gradle/libs.versions.toml:22-51` | 全部 `androidx.*` / `org.jetbrains.kotlinx` / `junit` / `org.json` |

合并 Manifest 中唯一的额外权限是 AndroidX 自动注入的 `com.cleanguard.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（signature 级自用权限）。

安全规则为打包在 assets 的本地 JSON（`AssetSafetyRuleRepository.kt:18,32`），不依赖远程更新。

---

## 2. 数据是否离开设备

**结论**：没有任何数据被写入外部存储公共目录；无埋点、日志上报、崩溃上报；唯一文件写入是应用私有 cacheDir 中的临时 APK 文件（用后即删）；存在用户主动触发的系统分享与预览。

| 检查 | 结果 | 证据 |
|---|---|---|
| 外部公共目录写入 | 0 | `getExternalStoragePublicDirectory`、`MediaStore.Downloads` 插入、`DIRECTORY_*` 均无命中 |
| `ContentResolver` 写入/删除 | 0 | `ContentValues`、`resolver.delete`、`deleteDocument` 均无命中；仅 Room DAO 的 `dao.insert`（`RoomPersistenceRepositories.kt:92`） |
| 日志/埋点/崩溃上报 | 0 | `android.util.Log`、`println`、`Timber`、`Firebase`、`Crashlytics`、`Sentry`、`Bugsnag` 均无命中 |
| 后台任务 | 0 | `WorkManager`、`Service`、`BroadcastReceiver`、`ContentObserver`、`JobScheduler` 均无命中 |
| 备份 | 已关闭 | `allowBackup="false"`；`data_extraction_rules.xml` 与 `backup_rules.xml` 对所有域全量 exclude |

**唯一的文件写入**：
```
app/src/main/java/com/cleanguard/app/data/apk/AndroidApkArchiveInspector.kt:24,28,63
  File.createTempFile(TEMP_PREFIX, APK_SUFFIX, context.cacheDir)
  temporaryApk.outputStream().buffered().use { ... }
  temporaryApk.delete()   // finally
```

**分享与预览**（用户显式触发）：
```
app/src/main/java/com/cleanguard/app/platform/intents/FileActionIntentFactory.kt:8-19
  createPreview → Intent(ACTION_VIEW) + FLAG_GRANT_READ_URI_PERMISSION
  createShare   → Intent(ACTION_SEND) + EXTRA_STREAM + FLAG_GRANT_READ_URI_PERMISSION
```
触发点：Downloads 洞察页「分享」按钮（`CleanGuardNavHost.kt:152-163`、`DownloadInsightsScreen.kt:192-193`）、重复文件详情页「预览」（`DuplicateDetailScreen.kt:49-55`）。

---

## 3. 权限申请时机与拒绝行为

**结论**：所有权限均为用户主动点击后才申请；应用启动时不请求任何权限；拒绝后不崩溃，全部走降级路径并给出 UI 提示。

### 申请时机

| 权限 | 请求代码 | 触发点 |
|---|---|---|
| `READ_MEDIA_IMAGES` / `VIDEO` / `AUDIO` | `PermissionManager.kt:41-43` | 首页「选择媒体访问范围」按钮（`HomeScreen.kt:111-118`、`512-517`）；权限中心按钮（`PermissionCenterScreen.kt:62`、`119-121`） |
| `READ_MEDIA_VISUAL_USER_SELECTED` | `PermissionManager.kt:44` | Android 14+ 随媒体权限一并请求 |
| `READ_EXTERNAL_STORAGE` | `PermissionManager.kt:53` | API ≤ 32 时随媒体权限请求 |
| `PACKAGE_USAGE_STATS` | `AppSettingsIntentFactory.kt:8` | 跳转系统设置页，用户手动开启 |

```
app/src/main/java/com/cleanguard/app/platform/permissions/PermissionManager.kt:39-45
  fun permissionsForRequest(sdkInt: Int): List<String> = when {
    sdkInt >= UPSIDE_DOWN_CAKE -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO, READ_MEDIA_VISUAL_USER_SELECTED)
    sdkInt >= TIRAMISU -> listOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_AUDIO)
    else -> listOf(READ_EXTERNAL_STORAGE)
  }
```

`MainActivity.kt:11-20` 只做 `setContent`，无权限请求。`HomeViewModel` 初始化只做 `checkSelfPermission` 查询。

### 拒绝后行为

```
app/src/main/java/com/cleanguard/app/feature/scanning/ScanningViewModel.kt:69-79
  if (!hasMediaAccess && treeUri == null) {
    _uiState.value = ScanningUiState(
      stage = "需要扫描范围授权",
      statusMessage = "返回首页授权媒体范围，或主动选择 Download 目录后再开始扫描。",
      requiresPermission = true,
    )
  }
```

MediaStore 查询被拒时逐集合捕获并继续（`MediaStoreScanner.kt:72-79`，错误文案 `245-256`）；SAF 查询被拒时同样降级（`SafScannerImpl.kt:64-74`、`124-131`）。

### 部分授权判定

```
app/src/main/java/com/cleanguard/app/platform/permissions/PermissionManager.kt:74-83
  val hasSelectedVisualAccess = sdkInt >= UPSIDE_DOWN_CAKE && isGranted(READ_MEDIA_VISUAL_USER_SELECTED)
  val hasAnyMediaAccess = fullAccessPermissions.any(isGranted)
  return if (hasSelectedVisualAccess || hasAnyMediaAccess) PARTIAL else NONE
```
`READ_MEDIA_VISUAL_USER_SELECTED` 仅用于**状态判定**，不用于扩大访问范围。

### 使用情况访问引导

```
app/src/main/java/com/cleanguard/app/platform/intents/AppSettingsIntentFactory.kt:8
  fun createUsageAccessSettings(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
```
状态检测用 `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS)`（`AndroidAppStorageRepository.kt:31-42`），非运行时请求。引导文案（`StorageInsightsScreen.kt:253-257`）：「Android 仅在你明确授权后提供各应用的存储统计。CleanGuard 只读取应用、数据与缓存大小，不读取应用内容。」

---

## 4. 实际访问的数据范围

### 4.1 MediaStore 扫描：仅元数据

```
app/src/main/java/com/cleanguard/app/data/media/MediaStoreGateway.kt:140-150
  LEGACY_PROJECTION = [_ID, DISPLAY_NAME, MIME_TYPE, SIZE, DATE_MODIFIED, RELATIVE_PATH]
  GENERATION_PROJECTION = LEGACY_PROJECTION + [GENERATION_MODIFIED]
```
旧版扫描器投影一致（`MediaStoreScanner.kt:201-208`）。扫描路径无 `openInputStream`（全库仅 `AndroidApkArchiveInspector.kt:26` 与 `StreamHashCalculator.kt:24` 两处，均不在扫描路径）。不读取 EXIF、位置、缩略图。

### 4.2 Hash 计算：流式，不整体载入内存

```
app/src/main/java/com/cleanguard/app/data/duplicate/StreamHashCalculator.kt:32-45
  digest.update(ByteBuffer.allocate(8).putLong(file.sizeBytes).array())
  digest.update(stream.readAtMost(64 * 1024))   // 头 64KB
  digest.update(stream.readAtMost(64 * 1024))   // 尾 64KB
app/src/main/java/com/cleanguard/app/data/duplicate/StreamHashCalculator.kt:47-59
  val buffer = ByteArray(64 * 1024)   // 固定缓冲流式读取
```
触发时机：`DuplicateGroupsViewModel.kt:26,60`（进入重复文件页时）。**输入包含 SAF 源文件**，因此用户授权目录中的文件内容也会被读取以算 SHA-256。

### 4.3 SAF 扫描：仅元数据，只读授权

```
app/src/main/java/com/cleanguard/app/data/saf/SafScannerImpl.kt:199-205
  PROJECTION = [DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, SIZE, LAST_MODIFIED]
app/src/main/java/com/cleanguard/app/data/saf/SafDownloadAccessRepository.kt:58-61
  val readFlag = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
  require(readFlag != 0)
  contentResolver.takePersistableUriPermission(treeUri, readFlag)
```
`DownloadTreeIntentFactory.kt:9-13` 只请求 READ flag，无 WRITE。SAF 路径中无任何 delete 调用。

### 4.4 应用占用：仅可启动应用，仅汇总字节

```
app/src/main/java/com/cleanguard/app/data/apps/AndroidAppStorageRepository.kt:50-66
  val launcherIntent = Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER)
  packageManager.queryIntentActivities(launcherIntent, MATCH_ALL)
  storageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, Process.myUserHandle())
```

### 4.5 APK 洞察：4 项元数据，按包名单点查询

```
app/src/main/java/com/cleanguard/app/data/apk/AndroidApkArchiveInspector.kt:38-61
  packageManager.getPackageArchiveInfo(path, PackageInfoFlags.of(GET_META_DATA))
  → packageName, versionName, longVersionCode, applicationLabel
app/src/main/java/com/cleanguard/app/data/apk/AndroidInstalledPackageVersionProvider.kt:11-25
  packageManager.getPackageInfo(packageName, PackageInfoFlags.of(0))   // 单个包名
  → 不可见时返回 Unknown 而非误判
```
包可见性：Manifest 仅声明 `<queries>` MAIN/LAUNCHER（`AndroidManifest.xml:16-21`），无 `QUERY_ALL_PACKAGES`。

---

## 5. 清理与删除

**结论**：清理 100% 走 `MediaStore.createTrashRequest`（系统回收站），无 `createDeleteRequest`，SAF 文件不可删除，无绕过系统确认的路径。

```
app/src/main/java/com/cleanguard/app/data/cleanup/MediaStoreCleanupRepository.kt:120-146
  require(item.source == ScanSource.MEDIA_STORE)
  require(item.action == CleanupAction.TRASH)
  require(item.riskLevel == SAFE || riskLevel == REVIEW)
  MediaStore.createTrashRequest(contentResolver, uris, trash).intentSender
```
全库 `createDeleteRequest` / `createWriteRequest` / `createRequest` → 0 命中。

**SAF 排除**（三重保障）：
```
app/src/main/java/com/cleanguard/app/domain/usecase/CreateCleanupPlanUseCase.kt:17-22
  item.source == ScanSource.MEDIA_STORE && ...
app/src/main/java/com/cleanguard/app/feature/cleanupdetail/CleanupDetailViewModel.kt:190-193
  canEnterMediaTrashPlan() = source == MEDIA_STORE && ...
app/src/main/java/com/cleanguard/app/data/cleanup/MediaStoreCleanupRepository.kt:131-133
  require(item.source == ScanSource.MEDIA_STORE)
```

**双重确认**：应用内 `AlertDialog`（`CleanupDetailScreen.kt:161-186`）→ 系统 `StartIntentSenderForResult`（`CleanupDetailScreen.kt:59-67`）→ 仅 `resultCode == RESULT_OK` 才计成功（`MediaStoreCleanupRepository.kt:175`）。

**引擎层禁止**：
```
app/src/main/java/com/cleanguard/app/domain/rules/SafetyRuleEngine.kt:23-31
  require(rules.filter { DANGER || UNKNOWN }.none { TRASH in allowedActions || PERMANENT_DELETE in allowedActions })
```

**永久删除动作在生产不可达**：`safety_rules_v1.json:110-116` 中的 `PERMANENT_DELETE` 规则限定 `sources: ["APP_OWNED"]`，而生产扫描器从不产生 `APP_OWNED` 源（该源仅存在于 `FakeCleanGuardData.kt:42` 的测试假数据）。

---

## 6. 应用内隐私文案与行为一致性

| 文案 | 位置 | 判定 |
|---|---|---|
| 所有扫描均在本机完成。 | `strings.xml:190` | 一致 |
| CleanGuard 不上传文件；除移入 Android 系统回收站与你主动使用系统分享/预览外，数据不会离开设备。 | `strings.xml:191`（本次修订） | 一致 |
| 常规扫描只读取文件名、类型、大小、修改时间与目录，不读取媒体内容。 | `strings.xml`（本次新增） | 一致 |
| 核对重复文件时会在本机流式计算 SHA-256，不保存文件副本。 | `strings.xml`（本次新增） | 一致 |
| CleanGuard V0.2 不读取聊天内容。 | `strings.xml:192` | 一致 |
| CleanGuard V0.2 不访问其他 App 私有目录。 | `strings.xml:193` | 一致 |
| 设备容量与扫描结果均在本机生成；本步骤只读取索引和你明确授权的目录，不会修改文件。 | `HomeScreen.kt:216`（本次修订） | 一致 |
| CleanGuard 不会主动永久删除 SAF 文件。 | `strings.xml:85` | 一致 |
| 仅在本机读取 APK 元数据。 | `strings.xml:67` | 可接受 —— 解析结果确为元数据；完整字节流行为已在隐私政策 §2.3 披露 |
| 这里只记录处理时间、数量、空间和关联媒体 URI，不保存文件内容。 | `strings.xml:146` | 一致 |
| 所选媒体已移入 Android 系统回收站；实际保留时间由系统决定。 | `strings.xml:168` | 一致 |
| 仅在进入本页后计算完整 SHA-256。 | `strings.xml:39` | 一致 |
| 推荐等级只用于排序，不代表可以安全删除 | `strings.xml:18` | 一致 |
| DANGER 与 UNKNOWN 条目不会进入处理流程。 | `ScanResultsScreen.kt:295` | 一致 |
| 忽略只影响推荐排序，不改变风险等级或可用处理方式。 | `strings.xml` | 一致 |
| 仅统计可从桌面正常启动的应用…不会自行清理其他应用的缓存。 | `StorageInsightsScreen.kt:224` | 一致 |

**本次修订 2 处、新增 2 条。详见 `IN-APP-PERMISSION-RATIONALE-REVIEW.md`。**

---

## 7. 不得声明的表述清单

以下说法听起来合理但代码不支持，写进任何对外文档即构成虚假声明。

| 不得这样声明 | 代码事实 |
|---|---|
| 「使用加密传输用户数据」 | 无网络能力，无传输行为；声明加密传输暗示存在传输 |
| 「收集匿名使用统计用于改进产品」 | 无任何日志/埋点/上报实现 |
| 「使用崩溃上报工具收集诊断信息」 | 无相关 SDK，依赖表无第三方 |
| 「请求网络权限以获取最新安全规则」 | 规则为打包的本地 JSON |
| 「用户文件会备份到云端以便跨设备恢复」 | `allowBackup=false` + 全量 exclude |
| 「在服务器端分析媒体以提供清理建议」 | 推荐引擎纯本地计算 |
| 「分析已安装的所有应用」 | 仅枚举 LAUNCHER 应用，只取汇总字节 |
| 「永久删除不需要的文件以释放空间」 | 只有 `createTrashRequest`；保留期由系统决定 |
| 「用户可一键清理所有空间」 | 明确不提供；DANGER/UNKNOWN 不可选 |
| 「读取照片/视频的画面内容以判断是否可删除」 | 常规扫描只读 6 个元数据字段；但重复文件页会读字节算哈希，声明须限定为「常规扫描」 |
| 「推荐基于 AI 或智能识别图片内容」 | 推荐只依据大小、修改时间、目录关键词、文件类型 |
| 「仅在用户主动点击时收集数据」 | 空间洞察页、APK 洞察页、重复文件页在**页面加载时自动**读取 |
| 「不读取任何文件内容」 | 4 处读取字节流：哈希、APK 解析、分享授权、预览授权 |
| 「识别并列出重复照片/相似图片」 | 只做字节完全相同的 SHA-256 判定，无相似度算法 |
| 「支持从回收站恢复您删除的文件」 | 恢复中心只覆盖 CleanGuard 自己移入的项目 |
| 「符合 Google Play 全部权限政策」 | `PACKAGE_USAGE_STATS` 属受限权限，需走 Play Console 申请流程 |
| 「用户授权后可访问整个文件系统」 | 无 `MANAGE_EXTERNAL_STORAGE`、无 `MANAGE_MEDIA`、SAF 仅 READ |

---

## 8. 审计边界

- 本次为**纯静态代码审计**，未做运行时网络抓包或真机行为验证。
- 上述「零命中」结论基于 `app/src/main/` 全量文本检索。
- 若存在通过 JNI、反射或动态下载引入网络能力的代码，本审计无法覆盖 —— 项目内未发现任何反射加载或动态代码下载实现（搜索 `Class.forName`、`DexClassLoader`、`PathClassLoader`、`loadClass` 均无命中）。
- 结论对应 version code 2。若代码变更，需重新审计。
