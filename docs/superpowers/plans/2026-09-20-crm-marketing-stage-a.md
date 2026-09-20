# 全民营销 · 阶段 A（后台模块）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
> 本项目实际执行者：sell1（主脑，Task 1–6、13）与 sell2（执行手，Task 7–12、14），分工与状态见 `~/Documents/cc/协作看板.md`。

**Goal:** 在 zhyq 后台新增「全民营销」模块：园区伙伴推荐客户 → 评级 → 合同生效/出库单 → 佣金级差拆分 → 冻结/解冻 → 结算 → 提现，全链路可在浏览器走通。

**Architecture:** 独立包 `com.zhyq.park.marketing`，复用现有 `DomainEvent`（订阅 `ContractApproved` / `PaymentReceived`）、`BizSettings`、`FieldEncryptionService`、`NotificationService`。佣金引擎是纯函数（`CommissionEngine.split`），池子计算与引擎分离（`PoolCalculator`）；三个状态机（服务合同 / 云仓加盟 / 锁客）全部条件更新。前端在 `views/crm/marketing/` 下 14 页，路由与菜单集中一次改。

**Tech Stack:** Java 17 · Spring Boot 3.2.5 · MyBatis-Plus · MySQL 8 · Flyway · JUnit5 + Mockito + AssertJ · Vue 3.4 · Element Plus · Vite · vitest

**Spec:** `docs/superpowers/specs/2026-09-20-crm-marketing-design.md`（落地差异）+ `docs/marketing/PARK-MKT-001-开发方案-V1.2.md`（业务规范，§ 号均指它）

## Global Constraints

- Flyway 新迁移从 **V57** 起（生产库 2026-09-20 查实 = V56）；已执行迁移一字不改。
- 状态流转一律条件更新 `update … where id=? and status in (前态)`，`updated==0` 抛 `BizException`。
- 金额 `DECIMAL(14,2)` / `BigDecimal` HALF_UP；比例 `DECIMAL(6,2)`。
- 新表带基座列 `id, tenant_id, create_by, create_time, update_by, update_time, version, deleted` + `project_id BIGINT NULL`。
- 类名前缀 `Mkt`；`Commission*` 已被老渠道佣金占用不得复用；新类名先跑 `find backend -name '*.java' | sed 's|.*/||' | sort | uniq -d` 查重。
- 权限点 `crm:marketing:<资源>:<query|edit|audit|config|pay>`，Controller 全覆盖 `@PreAuthorize("hasAuthority('…')")`。
- 共享文件（`router/index.js`、`layout/menu.js`、`DomainEvent.java`、`SecurityConfig.java`、迁移）只有 sell1 改。
- 不引入 Redis；不 push GitHub；commit 等用户检查后再提。
- 编译：`export JAVA_HOME=/opt/homebrew/opt/openjdk@17; MVN=/opt/homebrew/var/homebrew/tmp/.cellar/maven/3.9.16/libexec/bin/mvn; cd backend && "$MVN" -q -o compile`；单测 `"$MVN" -o test -Dtest=XxxTest`；前端 `cd frontend && pnpm build && pnpm test`。

---

## 文件结构

```
backend/src/main/resources/db/migration/
  V57__marketing_base.sql            表 + 老表加列 + 种子（岗位/评级/规则参数）
  V58__marketing_perms_roles.sql     权限点 + 4 角色 + 角色-权限 + route_module_mapping
backend/src/main/java/com/zhyq/park/marketing/
  entity/    MktPromoter MktPromoterAccount MktPosition MktPositionOverride MktPositionHistory
             MktCustomerGrade MktCommissionRule MktReferralOrder MktPromoterCommission MktSettleBatch
             MktWithdrawal MktWarehouse MktWarehouseOnboarding MktCustomerLock MktContractWarehouse
             MktServiceContract MktServiceContractVersion MktContractTemplate SysAudit
  mapper/    与 entity 一一对应（BaseMapper 空接口）
  engine/    CommissionEngine SplitRequest ChainNode Split PoolCalculator LadderResolver
  service/   MktAuditService MktPromoterService MktReferralService MktLockService
             MktServiceContractService MktWarehouseOnboardingService MktCommissionService
             MktSettlementService MktWithdrawalService MktPositionReviewService MktReferralOrderImportService
  listener/  MktLeaseCommissionListener（ContractApproved / PaymentReceived）
  job/       MktUnfreezeJob MktLockExpireJob MktPositionReviewJob MktContractReminderJob
  controller/ MktDashboardController MktPromoterController MktPositionController MktGradeController
             MktCustomerController MktServiceContractController MktContractTemplateController
             MktWarehouseController MktOnboardingController MktReferralOrderController
             MktCommissionController MktWithdrawalController MktSettingController MktAuditController
backend/src/test/java/com/zhyq/park/marketing/
  V57MigrationContractTest  engine/CommissionEngineTest  engine/PoolCalculatorTest
  service/*ServiceTest（状态机每条迁移一个 case）
frontend/src/api/marketing.js
frontend/src/views/crm/marketing/  Dashboard Promoter Position Grade Customer ServiceContract
             ContractTemplate Warehouse Onboarding ReferralOrder Commission Withdrawal Setting AuditLog (.vue)
frontend/src/router/index.js  layout/menu.js（各加一段）
```

---

## 批次 B0（sell1）

### Task 1: V57 迁移 + 迁移契约测试

**Files:**
- Create: `backend/src/main/resources/db/migration/V57__marketing_base.sql`
- Test: `backend/src/test/java/com/zhyq/park/marketing/V57MigrationContractTest.java`

**Interfaces:** Produces 19 张表（§4.2 的 P1 子集 + `sys_audit`），老表加列 `crm_lead.referrer_id/referral_code`、`crm_customer.grade/referrer_id/service_type/biz_line/sign_mode/attribution_note`（`crm_customer.phone` 加 `uk_customer_phone_active`，生产表为空可加）、`biz_contract.grade`。种子：`crm_position` P1–P4（50/70/85/100，min 40/60/80/100，lock_cap 50/100/200/500）、`crm_customer_grade` A–D（lease_months 1/1/0.5/0.5，erp_rate 8/6/5/3，contract_bonus 0）、`biz_setting(module='marketing')` 全部 §3 参数默认值。

- [ ] Step 1 写测试 `V57MigrationContractTest`（照 `crm/V52MigrationContractTest` 风格读 SQL 文本断言）：19 个 `CREATE TABLE` 名、`uk_promoter_openid/uk_promoter_phone/uk_promoter_invite`、`uk_commission_order_payee`、`uk_withdrawal_pay_no`、`uk_service_contract_no`、`uk_lock_active`、`ADD COLUMN referrer_id`、种子 `'P1'…'P4'`、`'A'…'D'`、`'ladder_depth'`。
- [ ] Step 2 跑：`"$MVN" -o test -Dtest=V57MigrationContractTest` → 失败（文件不存在）。
- [ ] Step 3 写 SQL（每表照 V55 的列风格；锁客表用生成列 `active_key = IF(status IN (1,2), customer_id, NULL)` + `UNIQUE KEY uk_lock_active(active_key)` 实现"一个客户同时只有一个有效锁"；`crm_promoter_commission` 加 `sign TINYINT NOT NULL DEFAULT 1 COMMENT '1正向 -1扣回'`，唯一键 `(referral_order_id, promoter_id, sign)`）。
- [ ] Step 4 跑测试 → 通过；本地 MySQL 起后端跑 Flyway：`docker compose up -d mysql`（或本地 mysql）后 `"$MVN" -o spring-boot:run` 看日志 `Successfully applied 2 migrations`。

### Task 2: V58 权限点 + 角色种子

**Files:**
- Create: `backend/src/main/resources/db/migration/V58__marketing_perms_roles.sql`
- Modify: `V57MigrationContractTest.java`（加一个 `v58SeedsPermsAndRoles` case）

**Interfaces:** Produces 权限点（每资源 query + 各自 edit/audit/config/pay，共约 45 个，清单写在 SQL 注释里且与 Controller 一一对应）、角色 `mkt_sales(招商专员) / mkt_ops(运营) / mkt_finance(财务)`（管理员用现有 `admin`），按 §6.4 矩阵关联；`route_module_mapping('/crm/marketing/**','crm','招商管理',0,1)`。

- [ ] Step 1 测试：断言 SQL 含每个 `crm:marketing:*` 权限点字符串（列表来自 §6.4 页 × op）。
- [ ] Step 2 照 V41 三件套写 SQL（`INSERT … SELECT … WHERE NOT EXISTS`）；角色照 V8 `sys_role` 列。
- [ ] Step 3 测试通过；Flyway 本地跑通。

### Task 3: 实体 + Mapper + `sys_audit` 服务 + `DomainEvent` 扩展 + 包骨架

**Files:**
- Create: `marketing/entity/*.java`（19 个，字段照 V57 列，`@TableName`，继承 `BaseEntity`）、`marketing/mapper/*.java`
- Create: `marketing/service/MktAuditService.java`：`void log(String action, String bizType, Long bizId, String reason, Object before, Object after)`（operator 取 `SecurityContextHolder`，JSON 用 Jackson，截断 4000）
- Modify: `common/event/DomainEvent.java`：加 `record ServiceContractEffective(Long contractId, Long customerId, Long promoterId, Integer signMode, LocalDateTime occurredAt)`、`record CommissionUnfrozen(Long commissionId, Long promoterId, BigDecimal amount, LocalDateTime occurredAt)`、`record PositionChanged(Long promoterId, String fromCode, String toCode, String reason, LocalDateTime occurredAt)`、`record CustomerLockChanged(Long lockId, Long customerId, Long promoterId, Integer status, LocalDateTime occurredAt)`
- Create: `marketing/engine/CommissionEngine.java` 空壳（方法体 `throw new UnsupportedOperationException()`，让 sell2 的测试能编译）

- [ ] Step 1 查重类名（Global Constraints 里的命令）。
- [ ] Step 2 写全部文件；`"$MVN" -q -o compile` 通过。
- [ ] Step 3 看板「消息」区喊 sell2：T3–T5 解锁。

### Task 4: 佣金引擎 + 池子计算（TDD，测试由 sell2 的 T1 提供）

**Files:**
- Create: `marketing/engine/SplitRequest.java` `ChainNode.java` `Split.java` `CommissionEngine.java`（替换空壳）`PoolCalculator.java` `LadderResolver.java`
- Test: `engine/CommissionEngineTest.java`（sell2）、`engine/PoolCalculatorTest.java`（sell1）

**Interfaces:**
```java
public record SplitRequest(BigDecimal poolAmount, List<ChainNode> chain, Map<String, Integer> ladder) {}
public record ChainNode(Long promoterId, String positionCode, int status, boolean internal) {}
public record Split(Long promoterId, String positionCode, int sharePct, int diffPct, BigDecimal amount) {}
public final class CommissionEngine { public static List<Split> split(SplitRequest req); }
public final class PoolCalculator {
    /** 租赁：单价 × 面积 × 佣金月数 */
    public static BigDecimal leasePool(BigDecimal rentPrice, BigDecimal rentArea, BigDecimal commissionMonths);
    /** 入仓/直签：基数 × 总比例% */
    public static BigDecimal ratePool(BigDecimal base, BigDecimal totalRatePct);
}
@Component public class LadderResolver {
    /** 按 biz_setting marketing.ladder_depth(2/4) 取岗位份额表，并叠加成交人 path 上最近钻石合伙人的 override */
    public Map<String,Integer> resolve(MktPromoter seller);
    /** 从成交人沿 parent_id 向上取链（最多 ladder 大小 + 1 层） */
    public List<ChainNode> chainOf(MktPromoter seller);
}
```
规则（§2.7 / §2.9 / §2.13）：`amount = pool × diff / 100` HALF_UP 2 位；`share ≤ taken` 跳过；`share == 100` 结束；status=4 退出或 internal=true 的节点：`taken = share` 但不产生 Split（园区留存）；status=2 冻结照常产生；ladder 缺 code → `IllegalArgumentException`；空链 → `IllegalArgumentException`；pool ≤ 0 → 空列表。

- [ ] Step 1 `PoolCalculatorTest`：`leasePool(300,100,1)=30000.00`、`leasePool(300,100,0.5)=15000.00`、`ratePool(12.5,5)=0.63`（12.5×5%=0.625→HALF_UP 0.63）、`ratePool(12300,6)=738.00`、null 参数抛 `IllegalArgumentException`。跑 → 失败。
- [ ] Step 2 实现 `PoolCalculator`；跑 → 通过。
- [ ] Step 3 等 sell2 的 `CommissionEngineTest` 合进来（从 `feat/crm-marketing-sell2` cherry-pick 测试文件）；跑 → 全红。
- [ ] Step 4 实现 `CommissionEngine.split`（while 循环，`taken` 用 int，`LinkedHashSet` 防重复收款人）；跑 → 全绿。
- [ ] Step 5 实现 `LadderResolver`（Mockito 测 3 个 case：2 级默认、4 级默认、有 override 时 P2 份额被下调到 override 值但不低于 `share_min_pct`）。

### Task 5: 计佣服务 + 租赁事件监听 + 解冻 Job

**Files:**
- Create: `service/MktCommissionService.java`、`listener/MktLeaseCommissionListener.java`、`job/MktUnfreezeJob.java`
- Test: `service/MktCommissionServiceTest.java`、`listener/MktLeaseCommissionListenerTest.java`

**Interfaces:**
```java
public class MktCommissionService {
    /** 一个事务：写 crm_referral_order + 若干 crm_promoter_commission(冻结)。幂等：source_type+source_no 已存在直接返回已有 order。 */
    @Transactional public MktReferralOrder createAndSplit(int sourceType, String sourceNo, Long sourceId, Long customerId, Long sellerPromoterId, String grade, BigDecimal poolFactor, BigDecimal baseAmount, BigDecimal poolAmount, LocalDateTime unfreezeAt);
    /** 冻结→可结算（条件更新 status=1→2），按 order 或按 promoter */
    public int unfreezeByOrder(Long referralOrderId);
    /** 作废未结算 / 已结算生成负向扣回行（sign=-1, status=2） */
    @Transactional public void clawback(Long referralOrderId, String reason);
    @Transactional public String settle(List<Long> commissionIds, String operator);   // 返回 batch_no
}
```
监听（`@TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)` 照 `TodoEventListener`）：
- `ContractApproved` → 查 `biz_contract` → 找租客 `biz_tenant` → 按 `phone` 匹配 `crm_customer.referrer_id`（无推荐人则不计）→ 互斥：`crm_commission` 里已有该 `contract_id` 且 `marketing.old_channel_exclusive=true` 则写日志跳过 → 评级取 `biz_contract.grade` 或 `crm_customer.grade`（缺省 D）→ `pool = leasePool(rentPrice, rentArea, grade.lease_commission_months)` → `createAndSplit(1, "LEASE-"+contractId, …, unfreezeAt=null)`。
- `PaymentReceived` → 该 `contractId` 的 `source_type=1` 订单 → `unfreezeByOrder`；同时若为服务合同首期款（`crm_service_contract.id` 与 `fin_bill.contract_id` 关联方式：`fin_bill.source='mkt_service'` 且 `contract_id` 指向服务合同）→ 解冻 `source_type=4` 订单。
- `MktUnfreezeJob` 每小时：`status=1 AND unfreeze_at <= now()` 且订单 `status<>3` → 2。

- [ ] Step 1 Mockito 测试：①正常租赁 A 级 4 级链拆出 4 行合计 30000；②无推荐人不写；③老渠道已有佣金跳过；④重复事件幂等；⑤PaymentReceived 解冻只动 status=1。
- [ ] Step 2 实现；测试通过；`compile` 通过。

### Task 6: 三个状态机 Service + 锁定到期 / 晋升 / 提醒 Job

**Files:**
- Create: `service/MktServiceContractService.java`（§2.4 状态 1草稿 2待审核 3待客户签 4已生效 5履约中 6变更中 7到期 8终止 9作废；`submit/audit(pass,reason)/signOffline(fileId)/effect/terminate/void_/renew`；→4 时发 `ServiceContractEffective` 并按 `contract_bonus>0` 生成 `source_type=4` 冻结佣金；园区签生成首期 `fin_bill(source='mkt_service')`）
- Create: `service/MktWarehouseOnboardingService.java`（join_status 1申请 2资质审核 3ERP对接中 4待签协议 5已上线 6暂停 7退出；`passStep(n)/rejectStep(n,reason)/markErpConnected()`（P1 人工标记）`/pause/resume/exit`）
- Create: `service/MktLockService.java`（§2.2a：`prelock(customerId,promoterId)` 校验岗位 lock_cap 与 30 天冷却；`confirm/extend/release/transfer`；`MktLockExpireJob` 每日 02:00）
- Create: `service/MktPositionReviewService.java` + `job/MktPositionReviewJob.java`（每月 1 日 02:00，§2.11，手动调岗 90 天内不自动降）
- Create: `job/MktContractReminderJob.java`（每日 09:00：到期 30/7 天 → `NotificationService`）
- Test: 每条状态迁移一个 Mockito case（合法前态成功、非法前态抛 `BizException`）

- [ ] Step 1 先写全部迁移表格的测试（合同 12 条、加盟 8 条、锁客 6 条）；跑 → 失败。
- [ ] Step 2 实现，所有迁移用 `UpdateWrapper … .eq(id).in(status, 前态)`，`updated==0` 抛 `BizException("状态已变化，请刷新")`；每次迁移调 `MktAuditService.log`。
- [ ] Step 3 测试通过；`compile` 通过。

## 批次 B1–B3（sell2，Controller + 前端；后端 Service 由 B0 提供）

以下每个 Task 的 Controller 都：`@RestController @RequestMapping("/crm/marketing/<资源>") @RequiredArgsConstructor @Tag(name="全民营销-…")`；分页 `GET /page` 用 `PageQuery` + `LambdaQueryWrapper`，`projectId` 作可选 `@RequestParam` 过滤（照 `ContractController:73-79`）；返回 `Result<T>` / `Result<PageResult<T>>`；每个写端点 `@PreAuthorize` + `MktAuditService.log`。前端页照 `views/crm/Channel.vue`：`el-form inline` 查询栏 + `el-table` + `el-dialog`/`el-drawer`；接口封装追加到 `api/marketing.js`；每页至少一个 vitest（api 层）。

### Task 7: 岗位与份额 / 客户评级（T2+T3）
端点 §5.1 position/grade 组；校验：份额严格递增且 ≤100、`share_min_pct ≤ share_pct`、`PUT /position/depth` 写 `biz_setting marketing.ladder_depth` 并审计。页面 `Position.vue`（行内编辑 + 岗位数切换二次确认红字）、`Grade.vue`。

### Task 8: 伙伴管理（T4）
端点 §5.1 promoter 组。`POST /{id}/parent`：校验非自己、不成环（新上级的 path 不含自己）、事务内 `UPDATE crm_promoter SET path = REPLACE(path, oldPrefix, newPrefix) WHERE path LIKE oldPrefix%`。`POST /manual`（后台手工录伙伴，P1 没小程序时用；生成 8 位邀请码去掉 0/O/1/I）。页面 `Promoter.vue` 列表 + Drawer 页签：资料 / 团队（直属一层）/ 客户 / 订单 / 佣金 / 提现 / 岗位史 / 日志。

### Task 9: 云仓管理 + 加盟申请（T5）
端点 §5.1 warehouse / onboarding 组，调 `MktWarehouseOnboardingService`；步骤 3 页面按钮「标记 ERP 已联通（人工）」调 `markErpConnected`。页面 `Warehouse.vue`、`Onboarding.vue`（5 步时间线）。

### Task 10: 客户管理 + 合同管理 + 合同模板（T6+T7）
customer 组：`GET /page`（联 `crm_customer` 新列）、`POST /{id}/grade`（系统按 §3 门槛给建议 + 专员确认写依据）、`/assign-warehouse`（只列 join_status=5 的仓；写 `crm_contract_warehouse` 由合同起草时落）、`/lock/*` 转发 `MktLockService`、`sign_mode` 修改（有生效合同时拒绝）。contract 组转发 `MktServiceContractService`；`price_table JSON` 结构 `{"perOrder":x,"perItem":y,"storage":z}`。页面 `Customer.vue`（详情页签：资料/归因/锁定/合同/佣金）、`ServiceContract.vue`、`ContractTemplate.vue`。

### Task 11: 计佣订单（含出库单文件导入）+ 佣金结算 + 提现审核（T8 部分 + T9）
- `MktReferralOrderImportService`（sell1 写）：Excel 列 `出库单号, 货主客户ID或客户手机号, 件数, 包裹数, 发货时间, 物流单号`；按客户合同 `price_table` 算 `service_fee = perOrder×packages + perItem×qty`；`pool = ratePool(service_fee, grade.erp_total_rate)`；`createAndSplit(2, orderNo, …, unfreezeAt = 发货时间 + marketing.freeze_days)`；重复单号跳过并计数。
- order 组：`GET /page`、`POST /import`(multipart)、`POST /{id}/void`、`POST /{id}/recalc`。
- commission 组：`GET /page`、`POST /settle {ids}` → `MktCommissionService.settle`、`POST /{id}/void`、`GET /batches`。
- withdrawal 组：`POST /manual`（P1 后台代申请）、`/{id}/approve`、`/{id}/reject`、`/{id}/pay {payNo}`（条件更新 2→3，`pay_no` 唯一，重复同 payNo 返回原结果，照 `PaymentService.收款`）；税：`tax_amount = amount × marketing.tax_rate`（默认 0.20，代扣模式），`net_amount = amount − tax_amount`。
页面 `ReferralOrder.vue`（导入按钮 + 模板下载）、`Commission.vue`、`Withdrawal.vue`。

### Task 12: 规则参数 / 消息模板 / 审计日志 / 看板
setting 组：`GET /setting`、`PUT /setting`（只允许 §3 白名单 key）；msg-template 直接复用现有 `sys_msg_template` 接口，只在页面里筛 `code LIKE 'mkt_%'`；audit 组 `GET /audit/page`；dashboard 组 `GET /summary /funnel /trend`（口径 §6.3，直接 SQL 聚合，不建聚合表）。页面 `Setting.vue`、`AuditLog.vue`、`Dashboard.vue`（ECharts 漏斗 + 趋势）。

## 收尾

### Task 13: 路由 + 菜单 + 联调（sell1）
- Modify `frontend/src/router/index.js`：在 crm 段后加 14 条 `crm/marketing/<page>` 路由；`layout/menu.js` 在「招商」子目录里加 `{ title: '全民营销', icon: 'Share', children: [...14 项] }`。
- 合并 `feat/crm-marketing-sell2` → `feat/crm-marketing`，只解冲突。
- `"$MVN" -o test`（全量）、`pnpm build`、`pnpm test` 全绿。
- 本地起 MySQL + 后端 + 前端，浏览器走 §8 P1 全链路：录伙伴 → 录客户（推荐人=伙伴）→ 评级 A → 分配云仓（人工标记已联通）→ 起草服务合同 → 审核 → 上传签署 → 生效 → 佣金冻结 → 录首付款 → 解冻 → 批量结算 → 提现审核 → 标记打款；再走一条租赁线：租赁合同审批通过 → 一次性佣金冻结 → 收款解冻。截图存 `docs/marketing/acceptance/`。
- 给用户本地链接审阅（不等回复继续阶段 B）。

### Task 14: 盲审（sell2）
用 `ecc:java-reviewer` + `ecc:vue-reviewer` + `ecc:security-reviewer` 审 diff；问题写回看板汇报区；sell1 修。
