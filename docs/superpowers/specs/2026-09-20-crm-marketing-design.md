# 全民营销（园区伙伴 · 云仓生态）· 落地设计

> 业务规范以 `docs/marketing/PARK-MKT-001-开发方案-V1.2.md` 为准；本文只记录它与仓库的差异、拍板结果和调整后的范围。由两部分拼成：先是计划 v1（拍板与范围），后是计划 v0 的技术设计要点。


> 取代 v0。v0 §1「文档 vs 仓库 6 处不一致」和 §3「技术设计要点」仍然有效，本文只写拍板结果与调整后的范围。
> 工作区：sell1 `~/Documents/cc/zhyq`（`feat/crm-marketing`）；sell2 `~/Documents/cc/zhyq-sell2`（`feat/crm-marketing-sell2`）。协作看板：`~/Documents/cc/协作看板.md`。

## 0. 拍板结果

| # | 问题 | 结论 |
|---|---|---|
| 1 | 范围 | 后台模块 + 小程序链路优先；云仓 ERP 侧**只留接口**（见 §2） |
| 2 | 生产库 Flyway | 已查（2026-09-20，只读）：生产库 = `origin/test` = `origin/main` = **V56**。新迁移 **V57** 起 |
| 3 | Redis | 不引入，用 MySQL 唯一约束 + nonce 表 |
| 4 | sys_audit | 新建通用表 |
| 5 | 页面风格 | 照仓库现有风格（`views/crm/Channel.vue` 那套：查询栏 + 表格 + 弹窗/Drawer） |
| 6 | 待拍板默认值 | 岗位数 2、打款线下、税务=个税代扣（字段先落） |
| 7 | 分工 | sell1 主脑、sell2 执行手（原"子代理"角色） |
| 8 | 审阅节奏 | 每个阶段有可见界面时本地起服务给链接；不等审阅继续做；**不 push GitHub、不擅自 commit** |
| 9 | 生产库事实 | `crm_customer` 空表 → 可加 `phone` 唯一约束；`crm_lead` 27 条、`crm_channel` 34 条、`crm_commission` 2 条 |

## 1. 这套系统的四个"端口"（接口面）

| 端口 | 路径前缀 | 谁调 | 鉴权 | 本次 |
|---|---|---|---|---|
| ① 后台管理 API | `/api/crm/marketing/**` | 园区员工（招商专员/运营/财务/管理员）在 zhyq 后台 | 现有 JWT + `@PreAuthorize('crm:marketing:*')` | **做** |
| ② 伙伴小程序 API | `/api/mp/v1/**` | 园区伙伴（中间商）在微信小程序 | 新增 `MpJwtAuthFilter`，subject = promoter_id | **做** |
| ③ 云仓 ERP 开放接口 | `/api/open/v1/erp/**` | 加盟云仓自己的 ERP 系统（机器对机器） | App-Id + HMAC-SHA256 + nonce | **留接口**：契约文档 + 验签骨架 + 落库，不做适配器/心跳/对账 |
| ④ 云仓端小程序 API | `/api/wh/v1/**` | 云仓的人在同一个小程序里（v7 新增） | 新增 `WhJwtAuthFilter`，subject = warehouse_id | **暂缓**，云仓侧有真实加盟商再做 |

你"忘了的那个"多半是 ③（云仓 ERP 机器接口）或 ④（云仓端小程序）——这两个都是云仓商家那一侧，本次都按"留接口"处理。

## 2. 调整后的三个阶段（取代原 P1–P4 说法）

原文档的 P1 = 后台基础，P2 = ERP 打通，P3 = 小程序，P4 = 电子签/自动打款。按你的方向重排：

### 阶段 A · 后台模块（≈ 原 P1，全做）
- **V57** 业务表（P1 的 18 张 + `sys_audit` + 老表加列 + 岗位/评级种子）；**V58** 权限点 + **四个角色种子**（招商专员 / 运营 / 财务 / 管理员，按 §6.4 权限矩阵直接挂好）+ `route_module_mapping`。
- 后台 14 页：看板、伙伴管理、岗位与份额、客户评级、客户管理（含签约方式/锁定页签）、合同管理、合同模板、云仓管理、加盟申请、计佣订单（文件导入的出库单在这里看）、佣金结算、提现审核、规则参数/消息模板、审计日志。
- 佣金引擎（租赁一次性池 / 入仓园区签 / 直签三种池 + 级差拆分）、锁客状态机、合同状态机、云仓加盟状态机（ERP 那一步 P1 允许运营"人工标记已联通"）。
- 出库单**文件导入**（Excel 模板）→ 走同一条 Ingest → 计佣，这样没有真实 ERP 也能把整条链路跑到提现。
- Job：解冻 / 锁定到期 / 晋升复核 / 合同提醒 / 服务费月账单。
- 事件：订阅 `ContractApproved`（租赁一次性佣金，与老渠道佣金互斥）、`PaymentReceived`（解冻）；新增 `ServiceContractEffective` 等。

### 阶段 B · 伙伴小程序链路（≈ 原 P3 伙伴端）
- 后端 `/api/mp/v1/**`：微信登录（`code2Session`）、手机号授权注册绑上级、推荐/报备、我的客户、岗位/团队/团队分配、收益、提现、海报、消息。**开发期加 `zhyq.mp.mock-login` 开关**，用假 openid 走通全流程；你拿到小程序 AppID/AppSecret 后填进环境变量即切真。
- 小程序：新建 uni-app（Vue3）工程 `miniapp/`，伙伴端 11 页（§6.2）。云仓端 9 页暂缓。
- 订阅消息模板、微信商家转账、电子签都不做（原 P4）。

### 阶段 C · 云仓 ERP 留接口（原 P2 的最小子集）
- **V59**：`crm_warehouse_erp`、`crm_customer_erp_map`、`crm_erp_sync_log`、`crm_erp_nonce`。
- `POST /api/open/v1/erp/order-event` + `GET /contract`：HMAC 验签、时间戳 ±300s、nonce 防重放、按货主编码归属 → 复用阶段 A 的 Ingest。附一份给云仓的《接入契约》文档（就是 §5.3 整理成 md）。
- 后台「ERP 对接」页只做：签发/重置凭证、看同步日志。
- 不做：webhook/拉取双适配器、心跳断连、四关验收、对账、云仓结算单、云仓端小程序。这些留到有真实云仓接入时按原 P2 补。

顺序：A → B → C。A 做完就能给你看后台全链路；B 做完能在微信开发者工具里看小程序。

## 3. 审阅节点（不等你，做完继续）
1. 阶段 A 第 2 批结束（岗位/评级/伙伴/客户/云仓页可点）——起本地后端 + 前端，给链接。
2. 阶段 A 收尾（全链路：录伙伴 → 推荐 → 评级 → 合同生效 → 佣金冻结 → 收款解冻 → 结算 → 提现打款）——给链接 + 截图。
3. 阶段 B 小程序首批页面——微信开发者工具打开 `miniapp/` 的方法。
4. 阶段 C 完成——给云仓接入契约文档 + Postman 用例。

## 4. 与 sell2 的分工（看板任务队列为准）
- sell1：V57/V58/V59 迁移、`sys_audit`、DomainEvent 扩展、包骨架、路由/菜单、佣金引擎、三个状态机 Service、事件监听、Job、`/mp/v1` 鉴权与登录、开放接口验签、合并分支、本地起服务给你审阅。
- sell2：引擎测试套件（TDD）、各页 Controller + 前端页 + 单测、小程序页面（阶段 B）、代码审查。

---

# 附：计划 v0 的技术核对与设计要点

## 1. 方案文档 vs 仓库现状：必须先纠正的 6 处

| # | 方案文档写的 | 仓库实际（origin/test，2026-09-20） | 影响 / 处理 |
|---|---|---|---|
| 1 | 迁移编号 **V41 / V42** | 本地迁移已到 **V56**（V52 crm_lead_registry、V55 crm_agency、V56 责任单位权限）；运维手册记录生产库 09-04 已到 V51，09-01 曾因 V42 撞号回滚两轮 | 新迁移从 **V57** 起编；开工前必须查生产库 `flyway_schema_history` 最大版本（我不擅自连生产库，请你跑或授权） |
| 2 | 并发锁 / nonce 防重放用 **Redis** | 项目**没有 Redis**（pom 0 依赖）；服务器是共用机，跑 36 个容器 | 不加 Redis。改用 MySQL：手机号唯一约束兜底 + `crm_erp_nonce(nonce PK, expire_at)` 表防重放（每日清理） |
| 3 | 审计写 **`sys_audit`** | **没有 sys_audit 表**，只有访问日志（`common/accesslog`，按路由记请求） | 新建通用 `sys_audit`（module/action/biz_id/before/after/operator/reason），本模块所有写操作落表；其他模块以后可复用 |
| 4 | 分支 `ver6.4`，push `ver*` 自动部署 | CLAUDE.md 已改：日常进 `test`，合 `main` 才自动部署；`ver*` 不再触发 | 走 `feat/crm-marketing → PR 到 test → 自查后合 main` |
| 5 | 后台挂 `招商租赁 › 招商 › 全民营销`（sys_menu parent=20） | 前端菜单是**静态** `layout/menu.js`，`sys_menu` 只存权限点（type=3, parent_id=0）；招商目录 id=20 确实存在但只用于种子 | 菜单改 `menu.js`（主脑独占），权限点照 V41 幂等模板写 `crm:marketing:*` |
| 6 | 已有"老渠道佣金"要互斥 | 确认存在：`crm_channel`（中介，含 commission_rate）+ `crm_commission`（首年租金 × 比例，1待结算/2已结算/3作废），由租赁合同触发 | 引擎在 `ContractApproved` 里先查 `crm_commission` 是否已有该合同记录，有则跳过并写日志（互斥开关默认开） |

其他核对结果（可以直接复用）：
- **领域事件**：`common/event/DomainEvent` 是 sealed interface + record，已有 `ContractApproved`、`PaymentReceived`、`LeadCreated`；`ContractService.approve()` 已"先抢状态再发事件"。租赁一次性佣金直接订阅 `ContractApproved`，解冻订阅 `PaymentReceived`。
- **租赁合同**：`Contract.rentPrice`、`rentArea` 存在 → 月租金 = 两者相乘，符合 v7.3 拍板算法。
- **幂等范本**：`finance/service/PaymentService.收款()` —— payNo 先查 → 唯一键撞了回查 → 条件更新 `updated==0` 抛异常。提现打款、云仓结算付款照抄。
- **鉴权**：`auth/SecurityConfig` permitAll 白名单 + `JwtAuthFilter`；权限点 `hasAuthority('模块:资源:操作')`。小程序端 / 开放接口需各加一条 filter 与白名单。
- **加密**：`receivable/service/FieldEncryptionService`（AES-GCM，`ZHYQ_FIELD_ENCRYPTION_KEY`，Base64 32 字节）→ 身份证、收款账号、ERP secret 直接用。
- **通知**：`common/notify/NotificationService` + `NotifyChannel`（短信 Mock）。
- **定时任务**：`@Scheduled` 单机（无 ShedLock），照 `contract/service/ContractExpiryJob` 写。
- **测试**：68 个后端测试全是 JUnit5 + Mockito 纯单测（无 SpringBootTest）；前端 vitest。佣金引擎单测按这个风格写。
- **crm_lead** 已经面向这门生意：`customer_type` 含"云仓服务商 / 货主·电商卖家"，`coop_mode` 含"云仓仓储外包 / 一件代发"，已有 `channel_id`。
- 类名：只有 `Commission / CommissionMapper / CommissionController` 与新模块撞名 → 新模块统一前缀 `Promoter*` / `Mkt*`，放独立包 `com.zhyq.park.marketing`。
- **小程序**：仓库里没有任何 uni-app / 移动端目录，P3 是一个全新工程。
- 界面稿：PARK-MKT-004 只是指向 `2026-09-18-crm-marketing-prototype.html` 的链接，该 HTML **不在仓库里**（也不在 Downloads），后台页面布局目前只能按 §6 页面清单 + 现有 crm 页面风格做。

---

## 2. 建议范围：先做 P1，P2 紧随，P3/P4 另立项

| 期 | 内容 | 外部依赖 | 建议 |
|---|---|---|---|
| **P1 后台基础** | 迁移 + 权限；伙伴/岗位/评级/客户/合同/合同模板/云仓/加盟申请 8 页；锁客状态机；佣金引擎（租赁一次性 + 入仓园区签/直签 + 出库单**文件导入**）；解冻/晋升/提醒/锁定到期 Job；结算/提现（线下，税前税后）；规则参数/消息模板/审计日志 | 无（协议文本可先占位） | **本次做** |
| **P2 ERP 打通** | 开放接口（HMAC 验签 + nonce + 限流）、webhook/拉取适配器、货主编码映射、沙箱/正式凭证、四关验收、心跳/断连/重试、结算单双方向、对账、服务费账单 Job；4 个后台页 | 公网域名已有 | P1 验收通过后接着做 |
| P3 小程序 | uni-app 新工程，伙伴端 11 页 + 云仓端 9 页 | 小程序 AppID/Secret、手机号组件、订阅消息模板 | 等你拿到凭据后另起计划 |
| P4 自动化 | 电子签、微信商家转账、灵工平台 | 商户号、电子签服务商、灵工平台 | 同上 |

P1 里**不做**：前端界面稿还原（没有 HTML）、Redis、电子签、任何真实打款。

---

## 3. P1 技术设计要点（与方案文档一致处不重复）

### 3.1 数据库（V57 起，编号待生产库确认）
- **V57 `marketing_base`**：22 张新表中 P1 用到的 16 张（promoter / promoter_account / position / position_override / position_history / customer_grade / commission_rule / referral_order / promoter_commission / settle_batch / withdrawal / warehouse / warehouse_onboarding / customer_lock / contract_warehouse / service_contract / service_contract_version / contract_template）+ `sys_audit` + 老表加列（`crm_lead` + referrer_id/referral_code；`crm_customer` + grade/referrer_id/service_type/biz_line/sign_mode/attribution_note；`biz_contract` + grade）+ 岗位/评级种子。
- **V58 `marketing_perms`**：权限点 `crm:marketing:<页>:<op>` 幂等三件套（照 V41）+ `route_module_mapping` 加 `/crm/marketing/**`。
- P2 的 6 张（warehouse_erp / erp_sync_log / customer_erp_map / warehouse_settlement / erp_nonce / 对账）放 V59+。
- `crm_customer.phone` 唯一：老数据可能重复，迁移前先 `SELECT phone, COUNT(*) … HAVING COUNT(*)>1` 查生产库；有重复则改为"应用层校验 + 新数据唯一"，不加硬约束（避免迁移失败回滚）。
- 全部新表带基座 8 列（id/tenant_id/create_by/create_time/update_by/update_time/version/deleted）+ `project_id`。

### 3.2 后端包结构 `com.zhyq.park.marketing`
```
marketing/
  entity/  mapper/                       ← 16 表一一对应，前缀 Mkt 或 Promoter 避免撞名
  engine/  CommissionEngine, LadderResolver, PoolCalculator（租赁/入仓/直签三种池）
  service/ PromoterService, ReferralService(含归因+锁客), ServiceContractService(状态机),
           WarehouseOnboardingService, SettlementService, WithdrawalService, PositionReviewService
  listener/ MarketingEventListener（订阅 ContractApproved / PaymentReceived / 新增 ServiceContractEffective）
  job/     UnfreezeJob, LockExpireJob, PositionReviewJob, ContractReminderJob
  controller/ 按 §5.1 资源拆 12 个 Controller，全部 @PreAuthorize
  imports/ ReferralOrderFileImportService（P1 的路径 B 文件方式）
```
- 事件新增到 `DomainEvent`：`ServiceContractEffective`、`CommissionUnfrozen`、`PositionChanged`、`CustomerLocked/LockReleased`。
- 状态迁移一律条件更新（CLAUDE.md 硬约束）；佣金拆分一个事务；改上级整棵子树 path 一个事务。
- 引擎单测 ≥ 20 case：2/4 级、压缩、override、租赁池、直签池、扣回、幂等、老渠道互斥。

### 3.3 前端 `views/crm/marketing/` 8 页 + `api/marketing.js`
- 照 `views/crm/Channel.vue`（查询栏 + 表格 + 弹窗 + Drawer）风格；
- `router/index.js` 与 `layout/menu.js` 只由主脑改一次：`招商 › 全民营销` 子目录挂 8 个路径；
- 前端 vitest 至少覆盖 api 层与佣金演示计算器。

### 3.4 执行方式（照 PLAYBOOK §3 混合并行）
| 批次 | 主脑做 | 子代理并行做（各自只碰自己的新文件） |
|---|---|---|
| B0 | 生产库版本确认、V57/V58 迁移、`sys_audit`、DomainEvent 扩展、包骨架、`api/marketing.js` 空壳、路由/菜单 | — |
| B1 | **CommissionEngine + 单测**、ServiceContract 状态机 | 岗位与份额页、客户评级页、伙伴管理页（后端+前端）、云仓管理+加盟申请页 |
| B2 | 事件监听（租赁一次性佣金、解冻）、锁客状态机 + LockExpireJob | 客户管理页（含签约方式/锁定页签）、合同管理+模板页、规则参数/消息模板/审计日志页 |
| B3 | 提现/结算幂等、出库单文件导入、其余 Job | 佣金结算页、提现审核页、看板 |
| 收尾 | `mvn -B compile` + `mvn test` + `pnpm build`；code-reviewer 盲审；真浏览器走 §8 P1 全链路截图；对账表 | — |
每批结束编译 + 启动 + 冒烟，全绿再下一批。所有 commit 等你本地检查后再提交（不擅自 commit/push）。

---

