# CleanGuard V0.2 Phase 8 Report

## 1. Phase / 完成状态

- Phase：V0.2 Phase 8 — Cleanup History & Recovery Center
- 完成状态：完成
- 验收结论：`READY FOR PHASE 9`
- 当前分支：`feature/v0.2`
- Phase 8 起始提交：`ba51c75`
- V0.1 基线标签：`v0.1.0-rc1`

## 2. 实现范围

- 新增 `CleanupHistoryScreen`，展示 CleanGuard 发起的处理记录。
- 每次 MediaStore Trash 完成、取消或失败后，持久化处理 ID、时间、请求/处理/失败数量、请求/实际变化空间、处理类型、Trash 标记、恢复状态和规范化 URI 子表。
- 新增 `RecoveryCenterScreen`，只展示已成功移入 Android 系统垃圾箱的媒体记录。
- 恢复继续调用 `MediaStore.createTrashRequest(..., false)`，必须通过 Android 系统确认。
- URI 失效、权限撤销、系统请求创建失败或系统确认启动失败时，持久化为 `FAILED`，UI 显示“当前无法恢复”并禁用再次请求。
- 用户取消系统恢复确认时保持 `AVAILABLE`，不会误标为失败或已恢复。
- 即时 Undo 成功后同步把原清理记录标为 `RESTORED`。
- 未保存任何文件内容，UI 不展示原始 URI。
- 未新增永久删除、自动重试、后台任务或 Phase 9 Settings / Privacy UX。

## 3. 持久化设计

- Room Database Version：仍为 `1`。
- Entity / 表 / 字段 / 索引 / 唯一约束：无变化。
- Schema export：无变化，不需要 Migration。
- 复用既有 `cleanup_history` 与规范化 `cleanup_history_items` 子表。
- `CleanupHistoryDao.updateRestoreStatus()`：按 `cleanupId` 原位更新恢复状态，返回受影响行数。
- `CleanupHistoryRepository`：新增领域状态更新接口；ViewModel 不直接依赖 DAO。
- `MediaStoreCleanupRepository`：系统操作完成后写入历史；本地历史写入失败不会反向改变已经完成的系统操作。
- 未启用 `fallbackToDestructiveMigration()`。

## 4. Recovery 安全设计

- 恢复只允许 `MEDIA_STORE_TRASH`、`usedMediaStoreTrash = true`、`processedItemCount > 0` 且 `restoreStatus = AVAILABLE` 的记录。
- URI 必须为 `content://media/...`，缺失、非 MediaStore 或空列表均拒绝。
- 恢复调用固定使用 `trash = false`；不存在 `ContentResolver.delete()` 或其他永久删除路径。
- 仍由 Android 系统 UI 完成最终用户确认；应用无法跳过确认。
- `RESTORED`、`FAILED` 和 `NOT_AVAILABLE` 的按钮均禁用。
- 页面明确显示：“该项目已移动到系统垃圾箱；实际保留时间由系统决定。”
- 不承诺固定 30 天或任何设备无关的保留时长。

## 5. UI / Navigation

- 新增 `cleanup-history` 与 `recovery-center` 两个 Navigation Compose destination。
- 首页新增“清理记录与恢复”入口卡，可分别进入清理历史和恢复中心。
- 清理历史包含空状态、错误状态、时间、数量、空间与恢复状态。
- 恢复中心包含空状态、错误状态、处理中状态、系统确认结果和不可恢复状态。
- 恢复中心同一时刻只允许准备一个系统请求。
- Phase 8 新增用户可见文案全部放入 `strings.xml`。
- 处理结果页的保留期文案改为系统决定，不再使用可能被理解为固定保留期的表述。

## 6. 新增文件

- `app/src/main/java/com/cleanguard/app/domain/repository/MediaRecoveryRepository.kt`
- `app/src/main/java/com/cleanguard/app/data/cleanup/MediaStoreRecoveryRepository.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanuphistory/CleanupHistoryScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanuphistory/CleanupHistoryViewModel.kt`
- `app/src/main/java/com/cleanguard/app/feature/recovery/RecoveryCenterScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/recovery/RecoveryCenterViewModel.kt`
- `app/src/test/java/com/cleanguard/app/data/cleanup/CleanupHistoryEntryFactoryTest.kt`
- `app/src/test/java/com/cleanguard/app/data/cleanup/MediaStoreRecoveryRepositoryTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/Phase8UiTest.kt`
- `docs/CleanGuard-V0.2-Phase-8-Report.md`

## 7. 修改文件

- `app/src/main/java/com/cleanguard/app/data/cleanup/MediaStoreCleanupRepository.kt`
- `app/src/main/java/com/cleanguard/app/data/local/dao/CleanupHistoryDao.kt`
- `app/src/main/java/com/cleanguard/app/data/local/repository/RoomPersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/di/AppContainer.kt`
- `app/src/main/java/com/cleanguard/app/domain/model/CleanupResult.kt`
- `app/src/main/java/com/cleanguard/app/domain/repository/PersistenceRepositories.kt`
- `app/src/main/java/com/cleanguard/app/feature/cleanupresult/CleanupResultScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeScreen.kt`
- `app/src/main/java/com/cleanguard/app/feature/home/HomeUiEvent.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardDestination.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardNavHost.kt`
- `app/src/main/java/com/cleanguard/app/navigation/CleanGuardViewModelFactory.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/cleanguard/app/navigation/CleanGuardDestinationTest.kt`
- `app/src/androidTest/java/com/cleanguard/app/data/local/CleanGuardDatabaseTest.kt`

## 8. 测试结果

- 新增测试用例：7。
  - JVM：历史工厂成功/取消 2 个；恢复 URI 去重、无效 URI、确认/取消状态 3 个。
  - Compose instrumented：历史不显示原始 URI、不可恢复按钮与系统保留期文案 2 个。
- Room instrumented 既有清理历史用例增加 `AVAILABLE → RESTORED` 更新和缺失记录断言。
- Debug JVM 单元测试：101/101 通过，0 failed，0 errors，0 skipped。
- Release JVM 单元测试：101/101 通过，0 failed，0 errors，0 skipped。
- API 36 instrumented tests：31/31 通过，0 failed，0 errors，0 skipped。
- V0.1 SafetyRuleEngine 与既有回归测试全部继续通过。

## 9. Build 结果

- `compileDebugKotlin`：成功。
- `compileDebugAndroidTestKotlin`：成功。
- `testDebugUnitTest`：成功。
- `testReleaseUnitTest`：成功。
- `lintDebug`：成功。
- `assembleDebug`：成功。
- `assembleDebugAndroidTest`：成功。
- `connectedDebugAndroidTest`（API 36）：成功。
- Debug APK 安装：成功。
- Debug APK 冷启动：成功，`LaunchState: COLD`，Activity 为 `com.cleanguard.app/.MainActivity`。
- 冷启动后进程存活，AndroidRuntime fatal entries：0。
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Debug APK 大小：33,611,393 bytes。
- Debug APK SHA-256：`938425944813E8BDFAD21856B2861C4DDB94D78AC10A86618B4CCCC5FA83A15D`
- AndroidTest APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- AndroidTest APK 大小：2,761,002 bytes。

## 10. Lint / Warnings

- Android Lint：`No issues found`，0 Error，0 Warning。
- 构建环境提示 `android.overridePathCheck=true` 为实验选项。
- Android Studio 与 command-line tools 存在 SDK XML 版本提示。
- Debug 打包已知提示两个依赖原生库无法 strip，按原样打包；不影响测试、安装或启动。
- Windows JUnit 对中文工作区 classpath 存在兼容问题；最终测试从指向同一工作区的 `Q:\` ASCII 映射执行。
- 未修改仓库依赖源或 Gradle / Kotlin / Compose / Room 版本。

## 11. 权限变化

- `AndroidManifest.xml`：Phase 8 无 diff。
- 未新增 runtime permission。
- 不存在：`INTERNET`、`MANAGE_EXTERNAL_STORAGE`、`QUERY_ALL_PACKAGES`、`WRITE_EXTERNAL_STORAGE`、`MANAGE_MEDIA`、Accessibility Service、Root、Shizuku。
- 继续沿用既有媒体读取与 Usage Access 声明。

## 12. Safety / 回归检查

- `MediaStoreScanner` / `IncrementalMediaStoreScanner`：无 diff。
- `SafetyRuleEngine`：无 diff。
- 原 MediaStore Trash 仍调用 `MediaStore.createTrashRequest(..., true)`。
- 原即时 Undo 与新增 Recovery 均调用系统 Trash API 的 `false` 恢复语义。
- DANGER 与 UNKNOWN 仍不可进入 `CleanupPlan`。
- REVIEW 仍默认不选中。
- 系统取消不会崩溃，也不会产生虚假已恢复状态。
- 没有新增永久删除、自动选择、自动恢复或绕过系统确认的能力。
- V0.1 行为变化仅为：成功/取消/失败的 Trash 结果现在会追加本地历史；成功恢复会更新对应恢复状态。既有扫描、选择、安全判断和 Trash 执行语义不变。

## 13. Git diff 摘要

- 15 个已有源文件/测试文件修改。
- 10 个新文件（含本报告）。
- Manifest、Room Schema、Gradle dependency、SafetyRuleEngine、Scanner 无变化。
- 无临时下载脚本、测试产物或本机配置进入版本控制。

## 14. 已知问题 / 下一 Phase 风险

- Android 系统垃圾箱的实际保留时间、URI 生命周期和最终可恢复性由系统及设备决定；应用不作固定时长保证。
- 如果系统已永久移除媒体、URI 失效或权限被撤销，记录会显示“当前无法恢复”，不会自动重试。
- 当前恢复中心以一次 Cleanup 为恢复单元，不提供逐 URI 拆分恢复。
- Phase 9 若实现 Settings / Privacy UX，应继续保持本机处理、权限透明和历史保留策略可解释；不得借设置项引入后台自动删除或扩大权限。

`READY FOR PHASE 9`
