# CleanGuard V0.2 Phase 1 Report

报告日期：2026-09-22

范围：V0.2 Phase 1 — Room Persistence Foundation

结论：Phase 1 已完成；未进入 Phase 2。

## 1. Git baseline commit hash

- V0.1 baseline commit：`5511ddc1aebb998d66efebe903bf29d535b11aca`
- Phase 0 报告 commit：`3d5d9388f20dae6f3d3c0b818ebe84367002a256`

## 2. V0.1 tag

- Tag：`v0.1.0-rc1`
- 该标签指向 V0.1 baseline commit。

## 3. 当前 branch

- `feature/v0.2`
- 本阶段未推送该功能分支，未修改 `master`。

## 4. 新增 Gradle dependencies

- `androidx.room:room-runtime`
- `androidx.room:room-ktx`
- `androidx.room:room-compiler`（KSP）
- `androidx.room:room-testing`（androidTest）
- `org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1`

最后一项用于统一 `kotlinx-serialization-core/json` 的测试运行时版本，避免 Room Migration Test Framework 与既有传递依赖产生版本错配。没有升级项目的 AGP、Kotlin 或 Compose 工具链。

## 5. Room / KSP 版本

- Room：`2.8.5`
- KSP Gradle Plugin：`2.3.12`
- 既有环境保持：AGP `8.13.2`、Kotlin `2.3.21`、JVM `17`、compileSdk/targetSdk `36`

## 6. Database Version

- Database：`CleanGuardDatabase`
- 文件名：`cleanguard.db`
- Version：`1`
- `exportSchema = true`
- Application Context Singleton，由现有 `AppContainer` 提供
- 未使用 `fallbackToDestructiveMigration()`
- 未启用主线程 DAO 查询

## 7. Schema 文件位置

- `app/schemas/com.cleanguard.app.data.local.CleanGuardDatabase/1.json`
- Schema format version：`1`
- Identity hash：`8916526ccc02cb47ba158eb985b63505`
- `app/schemas` 已作为稳定项目目录加入版本控制，并作为 androidTest assets 提供给 Migration Test Framework。

## 8. Entity 清单

1. `IndexedItemEntity`
2. `MediaSyncStateEntity`
3. `ScanSnapshotEntity`
4. `AppStorageSnapshotEntity`
5. `HashCacheEntity`
6. `IgnoredItemEntity`
7. `CleanupHistoryEntity`
8. `CleanupHistoryItemEntity`

`CleanupHistoryItemEntity` 是一次清理与多个 item key / URI 的规范化子表，通过外键关联 `CleanupHistoryEntity`，没有把无限长度 URI 列表塞入单个字符串字段。所有表只保存元数据，不保存文件实际内容。

## 9. DAO 清单

1. `IndexedItemDao`
2. `MediaSyncStateDao`
3. `ScanHistoryDao`
4. `AppStorageHistoryDao`
5. `HashCacheDao`
6. `IgnoreRuleDao`
7. `CleanupHistoryDao`

DAO 根据场景使用 `suspend`、`Flow` 与 `@Transaction`；没有 `allowMainThreadQueries()`。

## 10. Repository 清单

Domain abstraction：

1. `IndexedItemPersistenceRepository`
2. `MediaSyncStateRepository`
3. `ScanHistoryRepository`
4. `AppStorageHistoryRepository`
5. `HashCacheRepository`
6. `IgnoreRuleRepository`
7. `CleanupHistoryRepository`
8. `ScanPersistenceTransactionRepository`

Data implementation：

1. `RoomIndexedItemRepository`
2. `RoomMediaSyncStateRepository`
3. `RoomScanHistoryRepository`
4. `RoomAppStorageHistoryRepository`
5. `RoomHashCacheRepository`
6. `RoomIgnoreRuleRepository`
7. `RoomCleanupHistoryRepository`
8. `RoomScanPersistenceTransactionRepository`

依赖方向保持为 `UI → Domain Repository → Data Repository → DAO`。Room Entity 与 Domain Model 通过 `PersistenceMappers.kt` 双向映射，UI 不使用 Room Entity。

## 11. 数据库 index / unique constraint 设计

| 表 | 唯一约束 / 主键 | 查询索引 |
|---|---|---|
| `indexed_items` | PK `id`；UNIQUE `contentUri` | `volumeName`、`lastSeenScanId`、复合 `(sizeBytes, modifiedAt)`、`quickHash`、`fullHash` |
| `media_sync_states` | PK `volumeName` | 主键已保证 volume 唯一，无冗余索引 |
| `scan_snapshots` | PK `scanId` | `startedAt` |
| `app_storage_snapshots` | 自增 PK `id` | 复合 `(packageName, timestamp)`、`timestamp` |
| `hash_cache` | PK `id`；UNIQUE `(contentUri, sizeBytes, modifiedAt)` | `quickHash`、`fullHash` |
| `ignored_items` | PK `id`；UNIQUE `itemKey` | `expiresAt` |
| `cleanup_history` | PK `cleanupId` | `timestamp` |
| `cleanup_history_items` | 复合 PK `(cleanupId, itemKey)` | `cleanupId`；外键删除级联 |

该设计防止同一 URI 重复扫描无限新增、保证 volume 唯一，并避免同一文件版本产生无意义 HashCache 重复记录。

## 12. 新增测试数量

- 新增 Instrumented Room / Migration 测试：`15`
- 原有 Instrumented UI 测试：`3`
- API 36 Instrumented Tests 总数：`18`
- 既有 JVM Unit Test 方法：`42`；Debug 与 Release 变体各执行一次，共 `84` 次执行

新增测试覆盖：

- Database create / version / table create
- Entity insert / update / delete
- IndexedItem URI unique constraint 与 batch upsert
- MediaSyncState volume uniqueness
- ScanSnapshot insert/query
- AppStorage history 排序
- HashCache 文件版本唯一性与 invalidation query
- IgnoreRule insert/delete
- CleanupHistory 与规范化子表持久化
- Transaction rollback 与成功提交
- 30 天 / Unlimited history retention prune
- AppContainer database singleton
- Migration Test Framework version 1 schema 验证

## 13. 全部测试结果

- `testDebugUnitTest`：42 passed，0 failed，0 skipped
- `testReleaseUnitTest`：42 passed，0 failed，0 skipped
- API 36 AVD `CleanGuard_API_36` Instrumented Tests：18 passed，0 failed
- V0.1 SafetyRuleEngine 与清理安全回归测试全部通过
- `DANGER` 不可清理、`UNKNOWN` 不可清理、`REVIEW` 默认不选中行为未改变
- MediaStore Trash、Undo、系统取消清理流程的原有测试全部通过

## 14. Lint 结果

- `lintDebug`：成功
- Error：`0`
- Warning：`13`
  - `AndroidGradlePluginVersion`：1
  - `GradleDependency`：7
  - `NewerVersionAvailable`：5

全部是已有工具链/依赖存在更新版本的非阻塞提示。根据 Phase 1 约束，没有为 Room 顺带升级 AGP、Kotlin、Compose 或其他既有依赖。

## 15. Build 结果

最终统一命令：

```text
gradlew test lintDebug assembleDebug assembleDebugAndroidTest
```

结果：`BUILD SUCCESSFUL`，110 tasks，12 executed / 98 up-to-date。

产物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
  - Size：32,589,202 bytes
  - SHA-256：`63877C9311728F988EFE92DE558FB2D89F86C5471AB50D590D07F89931903033`
- Instrumented Test APK：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
  - Size：2,691,338 bytes
  - SHA-256：`1374E7BE61AFB0B28B2A3489EB2DD959769FA63973CB9207B7A465A9259ACB86`

Debug APK 已在 API 36 模拟器安装并成功启动。

## 16. Manifest 权限 diff

- `app/src/main/AndroidManifest.xml` 相对 `v0.1.0-rc1`：无差异
- Room 未新增任何 Android runtime permission
- 已确认不存在：
  - `INTERNET`
  - `MANAGE_EXTERNAL_STORAGE`
  - `QUERY_ALL_PACKAGES`
  - `WRITE_EXTERNAL_STORAGE`
  - `MANAGE_MEDIA`
  - Accessibility Service
  - Root / Shizuku 配置

Merged manifest 仅保留 V0.1 既有媒体/使用情况访问声明，以及 AndroidX 自动生成的应用内部 signature permission；不存在权限扩大。

## 17. Git diff summary

Phase 1 的代码、Schema、测试与本报告均位于 `feature/v0.2`。主要变化为：

- 修改 Gradle version catalog、root/app Gradle 配置
- 扩展现有 `AppContainer`
- 新增 Room database、8 个 Entity、7 个 DAO、relation、mapper
- 新增 8 组 Domain Repository abstraction 与 8 个 Room implementation
- 新增持久化 Domain Model 与 retention policy
- 新增 15 个 Room / Migration instrumented tests
- 新增并纳入版本控制的 version 1 Schema

最终提交后工作区应保持 clean；Phase 1 本地提交 hash 记录在本报告提交后的 Git 历史中。

Phase 1 暂存差异统计：`30 files changed, 2600 insertions(+), 3 deletions(-)`。

## 18. 已知 Warning

1. Lint 的 13 个依赖/工具链更新提示，均为非阻塞。
2. `android.overridePathCheck=true` 为实验选项提示；该配置用于当前含中文路径的 Windows 工作区。
3. Android SDK command-line tools 提示其 XML 解析版本比已安装 Android Studio SDK metadata 旧一代；未影响编译、测试或 APK。
4. Kotlin daemon 在受限环境下无法写入用户 AppData 时会回退到非 daemon 编译；最终构建成功。
5. Android 打包曾提示两个 AndroidX native library 无法 strip，已按原样打包；不影响 Debug APK。
6. Migration test 初次运行暴露 `kotlinx-serialization` 传递依赖版本错配，已通过 BOM 对齐修复，最终 18/18 通过。

## 19. 是否发生任何 V0.1 行为变化

否。

- `MediaStoreScanner`：无代码差异
- `MediaStoreCleanupRepository`：无代码差异
- `SafetyRuleEngine`：无代码差异
- 原有 `ViewModelFactory` 工作方式未改变
- Manifest 权限未扩大
- 版本号从 V0.1 发布候选更新为 V0.2 开发版本（versionCode 2 / versionName 0.2.0），不改变业务行为

## 20. 是否存在进入 Phase 2 的阻塞问题

否。Phase 1 Definition of Done 全部满足；未实现增量扫描、MediaStore Generation、重复文件检测、Hash 计算、RecommendationEngine、App 增长分析、历史 UI、Recovery Center UI 或新的删除能力。

# READY FOR PHASE 2

本报告只表示技术条件已具备。根据指令，当前停止在 Phase 1，等待明确指令后才能进入 Phase 2。
