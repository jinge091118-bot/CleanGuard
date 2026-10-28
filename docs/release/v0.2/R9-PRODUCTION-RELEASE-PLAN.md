# CleanGuard V0.2 R9 正式发布方案

**状态**：方案就绪，待 R2 / R3 / R5 / R6 / R7 / R8 全部关闭后执行
**前置条件**：见 §1 的门禁清单
**目标**：以一次可回滚、可核验的方式把 V0.2 推到生产轨道

---

## 1. 前置门禁

R9 执行前，以下每一项都必须已关闭：

| 门禁 | 状态来源 |
|---|---|
| R0 代码冻结 | `RELEASE-CHECKLIST.md` |
| R1 生产签名 | `RELEASE-CHECKLIST.md` |
| R2 设备矩阵 | `DEVICE-MATRIX.md` |
| R3 性能验证 | `PERFORMANCE-REPORT.md` |
| R4 合规材料 | `PRIVACY-POLICY.md`、`DATA-SAFETY.md`、权限声明 |
| R5 内部测试 | `R5-INTERNAL-TEST-PLAN.md` 的退出条件 |
| R6 封闭测试 | `R6-CLOSED-TEST-PLAN.md` 的退出条件 |
| R7 反馈评审 | `R7-BETA-FEEDBACK-REVIEW-PLAN.md` 的退出条件 |
| R8 缺陷稳定化 | `R8-STABILIZATION-PLAN.md` 的退出条件 |

**另外两项硬性前置**：

- Google Play 正式版权限已批准（个人账号需完成 12 人 × 14 天封闭测试）
- 若走国内渠道，对应商店的主体资质与软著登记已就位

---

## 2. 发布物

| 项 | 值 |
|---|---|
| Application ID | `com.cleanguard.app` |
| 版本名 | `0.2.0` |
| 版本号 | version code `2`（V0.2 相对 V0.1 的增量） |
| 格式 | Android App Bundle（`.aab`） |
| 签名 | 上传密钥（`key.properties`），**不是**调试密钥 |
| 最低 / 目标 SDK | minSdk 29 / targetSdk 36 |

### 2.1 构建

```bash
./gradlew.bat clean :app:bundleRelease
```

### 2.2 提交前必须校验

`aapt2` 与 `apksigner` **都不认 AAB**（只认 APK），因此校验必须直接解析
`base/manifest/AndroidManifest.xml` 的 protobuf。项目已提供工具：

```bash
python3 tools/release/verify-aab.py <路径>/app-release.aab
```

它会核对 versionCode、minSdk / targetSdk、versionName，以及签名证书主体。

> ⚠️ 解析 versionName 时，长度字节是二进制，正则要用 `(.)` 而非 `[0-9]{1,2}`。
> 证书主体是 DER，需用 `keytool -printcert` 读。

**权限也必须校验**，因为本版本的承诺是「免费 + 无网络」：

```bash
python3 tools/release/verify-no-network-permission.py
```

它读 release 构建的合并清单，报告任何网络、广告或分析权限。
**依赖库会自己把 `AD_ID` 和 `ACCESS_ADSERVICES_*` 合并进来**，
所以这一步不能靠「我没写 INTERNET」来代替。

**校验不通过就不要上传。** versionCode 传错会导致无法覆盖旧版本，且线上无法降级。

---

## 3. 发布步骤

### 3.1 上传

1. Play Console → 生产 → 创建版本
2. 上传 `app-release.aab`
3. 填写版本说明（可直接用 `PLAY-CONSOLE-FILL-IN-PACKAGE.md` 里的文案）
4. 上传商店素材（`store-assets/` 已备好 11 个文件）
5. 填写 Data Safety（答案已备好，照抄）
6. 提交审核

**本版本为完全免费版本，不含任何应用内商品、广告或分析代码。** 上传前须确认
合并后的清单里没有 `INTERNET`、`AD_ID` 或任何 `ACCESS_ADSERVICES_*` 权限 ——
若出现了，说明有依赖把它们合并进来了，必须先移除。

### 3.2 分阶段放量

| 阶段 | 比例 | 观察时长 | 放行条件 |
|---|---|---|---|
| 第一阶段 | 5% | 24 小时 | 崩溃率与 ANR 率不高于封闭测试期 |
| 第二阶段 | 20% | 48 小时 | 同上，且无新增 P0/P1 反馈 |
| 全量 | 100% | — | — |

**每一阶段之间必须手动确认**，不设自动推进。发布是不可逆动作，自动推进会让一次异常扩散到全部用户。

---

## 4. 发布后核验

### 4.1 技术核验

- [ ] Play Console 的版本状态为「已发布」
- [ ] 从生产渠道下载安装（非测试轨道），冷启动正常
- [ ] 权限申请时机与 R0 基线一致
- [ ] 隐私政策链接可访问，内容与 `PRIVACY-POLICY.md` 一致
- [ ] 商店页素材显示正常，无裁切或拉伸

### 4.2 功能核验

用生产渠道安装包跑一遍核心路径：

- [ ] 首页容量总览正常
- [ ] 一次安全体检可完成，结果合理
- [ ] 空间洞察、扫描历史可打开
- [ ] 清理流程仍需逐项确认，系统回收站确认正常出现
- [ ] 撤销权限后功能降级而非崩溃

### 4.3 反馈监控

发布后 72 小时内，每日查看一次：

- Play Console 评论与评分
- 支持邮箱
- 崩溃与 ANR 面板

任一渠道出现 P0 级问题（数据丢失、错误删除、隐私泄露）→ 立即执行 §5。

---

## 5. 回滚预案

### 5.1 可回滚的条件

Google Play 的版本**不能真正回滚**，只能「停止向新用户分发」并把最新版本下架。
已安装用户不会自动降级。因此回滚的实际含义是：

1. 在 Play Console 暂停该版本的分发
2. 用 Hotfix 方式发布一个修复版本（**只有 P0 允许走这条路**）
3. 通知受影响用户手动更新

### 5.2 触发条件

出现以下任一情况即触发：

- DANGER / UNKNOWN 项目被处理（安全铁律被破坏）
- 用户文件被误删或无法从回收站恢复
- 隐私数据被上传（本应用无网络权限，出现即说明有严重缺陷）
- 崩溃率超过封闭测试期的两倍

### 5.3 回滚后

- [ ] 在 `BUG-TRACKER.md` 登记为 P0
- [ ] 按 R8 的流程走（先写复现测试，再改）
- [ ] 修复版本走 §3.1 的完整校验
- [ ] 若涉及国内渠道，同步处理各商店的下架或更新

---

## 6. 退出条件

- [ ] Play Console 生产轨道版本状态为「已发布」
- [ ] §4 的技术与功能核验全部通过
- [ ] 商店页素材、隐私政策链接、Data Safety 均已上线
- [ ] 72 小时观察期内无 P0/P1 反馈
- [ ] `RELEASE-CHECKLIST.md` 的 R9 已勾选
- [ ] `FINAL-RELEASE-REPORT.md` 已更新为已发布状态
- [ ] 版本号已记录，V0.3 的分支基线已确定
