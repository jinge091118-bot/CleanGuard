# CleanGuard V0.2 Phase 10 Compatibility Matrix

## 测试边界

- 本轮可用设备只有 `CleanGuard_API_36` Android Emulator。
- 已执行环境：Google `sdk_gphone64_x86_64`、Android 16 / API 36、x86_64、Google SwiftShader。
- 未获得 Samsung、Xiaomi、OPPO / OnePlus、vivo / iQOO、Honor 真机或设备云访问，因此不虚构 OEM 结果。
- 未安装 API 29–35 模拟器；这些版本仅完成 `minSdk = 29` 编译兼容检查，没有执行设备级验证。

## 已执行矩阵

| 平台 | Android / API | 方式 | 结果 | 证据 |
| --- | --- | --- | --- | --- |
| Google AVD | Android 16 / API 36 | 完整 instrumented tests | PASS | 38/38，0 failed，0 skipped |
| Google AVD | Android 16 / API 36 | Room 100k 事务写入 | PASS | 100,000 条，12.163 秒，事务完成后计数正确 |
| Google AVD | Android 16 / API 36 | Debug APK 安装 / 冷启动 | PASS | `LaunchState: COLD`，3.717 秒 |
| Google AVD | Android 16 / API 36 | 启动后内存快照 | PASS | TOTAL PSS 86,443 KB；TOTAL RSS 194,196 KB |
| Google AVD | Android 16 / API 36 | ANR / 崩溃检查 | PASS | 本次启动后无 ANR、OOM、FATAL EXCEPTION 或目标异常 |
| JVM / JDK 17 | 非设备 | 10k / 50k / 100k 汇总 | PASS | 8 / 31 / 63 ms；100k 结果正确 |
| JVM / JDK 17 | 非设备 | 100k 首次与无变化增量扫描 | PASS | 第二次使用 Generation 基线，changedItemCount = 0，无全局索引重读 |
| JVM / JDK 17 | 非设备 | 10k 重复候选 Full Hash | PASS | 固定 2 个 Worker，最大并发 = 2 |

## 尚未执行矩阵

| 厂商 / 平台 | 建议 Android 版本 | 状态 | 发布前重点 |
| --- | --- | --- | --- |
| Google Pixel 真机 | Android 16 | NOT EXECUTED | Partial Media、MediaStore Trash / Restore、冷启动与滚动帧率 |
| Samsung | Android 15 / 16 | NOT EXECUTED | One UI 权限回收、系统回收站确认页、SAF Provider |
| Xiaomi / Redmi | Android 15 / 16 | NOT EXECUTED | HyperOS 权限管理、后台恢复、系统确认页 |
| OPPO / OnePlus | Android 15 / 16 | NOT EXECUTED | ColorOS / OxygenOS 权限与 SAF 行为 |
| vivo / iQOO | Android 15 / 16 | NOT EXECUTED | OriginOS 权限页、Usage Access 返回路径 |
| Honor | Android 15 / 16 | NOT EXECUTED | MagicOS 权限与 MediaStore 回收站流程 |
| AOSP / Google API 29–35 | Android 10–15 | NOT EXECUTED | API 29 全量扫描回退、各代媒体权限、旋转与恢复 |

## 图形性能说明

- 无界面 AVD 使用 SwiftShader；10 次往返滚动得到 274 帧、55.84% janky、P50 48 ms、P90 81 ms。
- 该数据受软件渲染和宿主机负载显著影响，不能代表真机 Compose 性能，也不能据此宣称 OEM 流畅度。
- 本轮代码侧已确认大列表使用 `LazyColumn` 和稳定 key，推荐、排序与汇总移出主线程；真机 Macrobenchmark / Perfetto 仍需在发布验收前完成。

## 结论

Phase 10 的代码稳定性、API 36 核心回归、100k Room 写入、内存快照与 ANR 检查已完成。当前设备条件不足以关闭 OEM 真机与 Android 10–15 设备级兼容风险；该限制不阻止进入 Phase 11 发布验收，但在正式对外发布前必须完成并记录。
