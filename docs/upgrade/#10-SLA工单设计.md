# #10 工单 SLA 超时升级 + 回访评价设计 · 待实施（本轮自主）

> 版本 v0.1（2026-07-15）· 批次② P0-3 · 无依赖
> 不碰财务。复用既有 slaRespondMin/slaResolveMin 列。

## 1. 目标与非目标

**目标**：① SLA 双档（响应/解决分钟阈值，已有列）；② 超时升级——定时任务扫活动工单，超响应/解决 SLA 未处理则打超时态 + 通知；③ 回访评价——工单完成后满意度回访（复用 score，补回访动作）；④ 标准化解决代码（resolution code）。

**非目标**：不做多级 SLA 策略矩阵（按类型/优先级差异化阈值）——本轮用工单自带的两个阈值列；不做自动重派。

## 2. 现状锚点（探查确认）

- `WorkOrder`（pm_work_order）已有 `slaRespondMin`/`slaResolveMin`（DDL V6，注释"响应/解决SLA分钟"），**零代码读取**——直接复用加逻辑，无需加这两列。
- 状态机 `1待派单 2待接单 3处理中 4待验收 5已完成 6已关闭`；DDL 注释有"7已超时"但**无 ST_TIMEOUT 常量、无打超时逻辑**——本轮加。
- 有单一 `score`(1-5)，**无 resolution code 字段，无回访动作/状态**。
- 六步流转 `WorkOrderService`（dispatch/accept/arrive/finish/verify/close），每步写 pm_work_order_log，`verify`/`close` 发 `WorkOrderClosed`。
- @EnableScheduling 已开（#3）。已有通知 `NotificationService`（批次② 轨道A）。

## 3. 表结构（Flyway V24）

`pm_work_order` 加列：
| 列 | 类型 | 说明 |
|---|---|---|
| resolution_code | varchar(32) NULL | 标准化解决代码（见 §4 码表） |
| sla_state | tinyint NULL | SLA 状态：NULL正常 1响应超时 2解决超时（与主 status 解耦，不污染流转态） |
| escalated | tinyint NOT NULL default 0 | 是否已升级通知（防重复通知） |
| revisit_time | datetime NULL | 回访时间 |
| revisit_remark | varchar(255) NULL | 回访备注 |

> **不新增"7已超时"主状态**——改用独立 `sla_state` 标记，避免超时把工单踢出正常流转（工单超时了仍要继续处理）。设计明确采用 sla_state 方案。

解决代码码表（枚举常量，不建表，YAGNI）：`REPAIRED`(已修复)/`REPLACED`(已更换)/`REBOOT`(重启恢复)/`NO_ISSUE`(未见异常)/`TRANSFERRED`(转外部)/`OTHER`(其他)。

## 4. 组件

- **WorkOrder 实体**加字段：resolutionCode, slaState, escalated, revisitTime, revisitRemark。
- **常量**：WorkOrderService 加 `SLA_RESP_TIMEOUT=1`/`SLA_RESOLVE_TIMEOUT=2`；解决代码常量集。
- **SlaEscalationJob**（`com.zhyq.park.property...`，新 @Component）：`@Scheduled(cron="0 3/10 * * * ?")`（每 10 分钟、避 :00）扫描活动工单（status in 1..4、escalated=0、sla_state IS NULL）：
  - 响应超时：status in (1待派单,2待接单) 且 slaRespondMin 非空 且 `now - createTime > slaRespondMin 分钟` → 置 sla_state=1、发通知给 assignee（占位）+ 写 pm_work_order_log。
  - 解决超时：status in (3,4) 且 slaResolveMin 非空 且 `now - createTime > slaResolveMin 分钟` → 置 sla_state=2、通知 + log。
  - 置 escalated=1 防重复通知。用条件 UPDATE 抢（防并发定时重复）。
- **WorkOrderService 扩展**：
  - `finish(id, operator, content, resolutionCode)`：完成时带解决代码（校验在码表内，可空兼容老调用）；或单独 `setResolution`。
  - `revisit(id, operator, score, remark)`：完成/验收后回访，记 revisitTime/revisitRemark/score。
- **端点**：WorkOrderController 补 `POST /{id}/revisit`；finish 端点加可选 resolutionCode 参数。

## 5. 零触碰 & 决策

- 零触碰：只加列（可空/默认）、加 Job/端点/可选参数；六步流转既有语义不改（finish 的 resolutionCode 是可选增量，verify/close 发事件不动）。既有工单查询/流转不受影响。
- D1 超时表达：独立 `sla_state` 标记，不新增主状态（工单超时仍继续流转）。
- D2 通知对象：assignee 占位（#7 后真实）；SLA 阈值取工单自带两列，为空则不判超时。
- D3 解决代码：枚举常量非码表（YAGNI）。
- D4 Job 频率：每 10 分钟（`0 3/10 * * * ?`，避 :00）。

## 6. 工作量

V24（加5列）+ WorkOrder 实体加字段 + SlaEscalationJob（条件UPDATE抢+通知）+ WorkOrderService finish带解决码/revisit + 端点 + 冒烟（造超 SLA 工单→Job 打 sla_state+通知；回访记录）。约 Opus 半批。SLA 时间比较是纯逻辑，加 JUnit（isTimedOut(createTime, now, thresholdMin)）。
