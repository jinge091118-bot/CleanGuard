# CleanGuard V0.2 Beta Feedback

Status: **Not started - reserved for R5-R7**

Do not request private photos, private videos, full system logs, or private filesystem paths from testers.

处理流程见 `R7-BETA-FEEDBACK-REVIEW.md`。

## 反馈记录

| ID | Track | App version | Device / ROM | Category | Reproduction | Severity | Status |
|---|---|---|---|---|---|---|---|
| - | - | - | - | - | - | - | No feedback recorded |

## 允许的类别

- `BUG-P0`
- `BUG-P1`
- `BUG-P2`
- `UX`
- `PERFORMANCE`
- `POLICY`
- `FEATURE-REQUEST`

Feature requests must be placed in the V0.3 candidate backlog and must not be added to frozen V0.2.

## 严重级别定义

与 `BUG-TRACKER.md` 保持一致：

| 级别 | 定义 |
|---|---|
| P0 | 数据丢失、错误删除、崩溃循环、DANGER/UNKNOWN 被删除、权限被绕过、数据库损坏 |
| P1 | 扫描失败、回收站/恢复失败、严重 ANR、主界面不可用 |
| P2 | 可复现的 OEM 兼容问题、明显性能回退、严重 UI 错误 |
| P3 | 轻微 UI、文案或非核心体验问题 |

## 反馈质量要求

一条可处理的反馈需要包含：

1. 设备型号、系统版本、ROM（如适用）
2. 应用版本（version name + version code）
3. 复现步骤
4. 期望结果与实际结果
5. 截图（若涉及界面问题）

**不接受的输入**：无法复现且无证据的描述。此类记录但不进入修复决策。

**不收集**：私人照片、私人视频、完整系统日志、私人文件路径。描述问题本身即可。

## 需优先处理的类别

`POLICY` 类反馈必须在 24 小时内核实并回复。核实步骤见 `R7-BETA-FEEDBACK-REVIEW.md` §4。

## 常见误解类反馈的记录

以下误解若反复出现，属 P2，需要通过文案改进解决，不得归为「用户理解错误」：

| 误解 | 需检查的界面位置 |
|---|---|
| 「我把文件删了怎么找不回来」 | 清理确认页、清理结果页的系统回收站说明 |
| 「它怎么知道我的文件是什么」 | 首页扫描说明、扫描页的索引读取说明 |
| 「为什么它要相册权限」 | 权限中心的用途说明、权限申请前的说明 |
| 「相似照片呢」 | 重复文件页的说明文案 |
| 「它能不能清理其他应用缓存」 | 空间洞察页关于不清理其他应用缓存的声明 |

## 归档要求

每条反馈的最终处理结论记录在 `R7-BETA-FEEDBACK-REVIEW.md` 产出的反馈处理结论表中。未处理完的反馈不得在 R7 退出时标记为已归档。
