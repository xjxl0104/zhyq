# 全民营销：阶段 A/B/C 一律「单园区」数据范围（已知边界，非 bug）

**结论**：全民营销（`com.zhyq.park.marketing.*`）**没有租户/project 级数据隔离**。JWT 里不带 `projectId`，后端无 `TenantLineInnerInterceptor`，`dataScope` 字段无人消费。所有 `/{id}` 形式的接口（合同、云仓、客户、伙伴、锁、账单、结算…）**可以跨项目读写**。

**为什么这样**：这是项目基线本身如此（不止营销模块），不是营销代码引入的缺口。T14 盲审把它列为 C1，评估后确立为「阶段 A 单园区」的**有意边界**，而非待修的越权 bug。

**版本**：2026-09-21 核实（当时分支 `feat/crm-marketing`，后端 555 测试）。

**为什么记这条**：后续会话/审计看到「营销所有 /{id} 不校验 projectId」时，容易误判为高危越权、重复提为阻塞项。先来这里确认这是一致的设计边界。

**How to apply**：
- 做真实多项目/多园区前，**不要**只给营销模块补 project 过滤——那会造成全项目最不一致的口子。正确做法是全后端统一引入租户/project 数据范围（`TenantLineInnerInterceptor` + JWT 带 projectId + 消费 dataScope），作为一个独立立项。
- 在此之前，营销接口的越权防护靠**令牌隔离**（后台 / `mp:` / `wh:` 三类 subject 互斥）与**仓库归属校验**（`WhAuthContext` 从 token 推 warehouse_id），这些已实现并测试。
- 相关：`T14-盲审报告.md` §C1；公开面隔离见 `docs/marketing/ERP接入契约.md`。
