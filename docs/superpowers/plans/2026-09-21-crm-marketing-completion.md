# 全民营销全链路补齐实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. 每个任务都必须先写失败测试，再实现，再运行对应验证。

**Goal:** 在现有园区伙伴端能力之上，补齐云仓商家小程序、真实微信登录、ERP 可运营闭环和财务结算闭环，使“云仓申请 → ERP 接入 → 订单/客户 → 对账 → 结算/直签平台费”的链路可验收、可隔离、可重试，并保持现有伙伴端和后台权限边界。

**Architecture:** 伙伴端继续使用 `mp:{promoterId}` JWT；云仓端使用独立的 `wh:{warehouseId}` JWT 和 `/wh/v1/**` 路径。所有云仓查询从 token 推导 `warehouse_id`，不信任请求体中的归属字段。ERP 入站先进入事件 Inbox，再按事件语义幂等处理；不可映射事件进入补映射队列，失败事件进入重试/死信队列。财务侧以不可变月度账单和结算快照为主，直签合同平台费通过独立支付到账事件解冻关联佣金。所有状态变更沿用现有审计、租户和事务边界。

**Tech Stack:** Java 17、Spring Boot、Spring Security、MyBatis-Plus、Flyway、MySQL、JUnit 5/Mockito、Vue 3 + uni-app、pnpm。

**Spec:** `docs/superpowers/specs/2026-09-21-crm-marketing-completion-design.md`

## Global Constraints

- 继续在当前 `feat/crm-marketing` 工作树实现；不提交、不推送、不调用生产接口，不覆盖已有未提交改动。
- 保留 `zhyq.mp.mock-login=true` 开发模式；真实微信模式只在 AppID/AppSecret 完整配置且显式关闭 mock 后启用。
- 后台 token、伙伴 token、云仓 token 三者互斥；`/open/v1/erp/**` 仍只允许 HMAC 验签，不允许 JWT 绕过验签。
- 云仓端只读/修改当前 token 对应仓库；沙箱测试事件不得生成正式订单、佣金或财务账单。
- 所有新表必须带 `project_id`、软删除字段和必要的唯一键；所有金额使用 `DECIMAL` 和 `BigDecimal`，禁止浮点计算。
- 所有新接口返回现有 `Result`/`PageResult` 形状或既有开放接口契约；错误码必须稳定且写入审计/同步日志。
- 每个任务只修改自己声明的文件范围；共享文件由主代理在集成阶段处理，避免代理互相覆盖。
- 完成前必须运行后端全量离线测试、前端营销测试与构建、miniapp H5/微信构建、`git diff --check`，并把结果写入双份接手报告。

## Review Focus

- 鉴权：subject 前缀、路径匹配、角色权限和仓库 A/B 隔离是否能被测试证明。
- 事件：重复、乱序、退款先到、批量部分失败、不可映射、可重试异常和死信重放是否幂等。
- 财务：账单/结算快照是否可重复生成而不重复记账；2% 对账阈值的边界是否明确；直签平台费到账是否只解冻正确来源。
- 数据安全：云仓响应不得泄漏伙伴手机号、佣金比例、伙伴收益或其他仓库数据。
- 兼容性：旧伙伴端接口、旧 ERP webhook、旧迁移和现有 500+ 测试不能回归。

## Task 1: 伙伴端真实登录和手机号授权

**Files**

- Modify: `backend/src/main/java/com/zhyq/park/marketing/mp/MpAuthService.java`
- Modify: `backend/src/main/java/com/zhyq/park/marketing/mp/MpAuthController.java`
- Modify: `miniapp/src/pages/login/index.vue`
- Modify: `miniapp/src/utils/request.js`
- Modify: `miniapp/README.md`
- Tests: `backend/src/test/java/com/zhyq/park/marketing/mp/MpAuthServiceTest.java`, `backend/src/test/java/com/zhyq/park/marketing/mp/MpAuthControllerTest.java`

- [ ] 写红测：mock 模式 `jsCode` 仍生成 `mock:` openid；真实模式缺 AppID/Secret、微信返回错误、缺 `openid/session_key` 均返回稳定业务错误；同一手机号不能绑定两个 openid；后台 token 不能作为伙伴 token。
- [ ] 写红测：`bind-phone` 在真实模式只接受 `encryptedData`、`iv` 和已登录 openid，明文 `phone` 请求必须拒绝；解密出的 `phoneNumber` 必须通过手机号校验，并且解密失败不得落库。
- [ ] 实现可注入的 `WxSessionClient` 和 `WxPhoneDecryptor`（默认 JDK `HttpClient`/AES 解密实现，测试用 fake client），保留 `mock-login` 开关并把真实配置读取改为环境变量优先。
- [ ] 调整 `MpAuthController` 请求 DTO，兼容开发 mock 的明文测试字段但在真实模式强制加密字段；统一返回 `registered/openid/token/me`，不返回 `session_key`。
- [ ] 更新伙伴登录页：调用 `uni.login`，真实模式调用 `getPhoneNumber` 返回的加密字段，mock 模式保留手机号输入；token 只写入现有请求拦截器使用的存储键。
- [ ] 运行两类认证测试并确认现有 marketing/mp 测试通过；将真实配置、mock 配置和验收步骤写入 `miniapp/README.md`。

## Task 2: 云仓身份、申请和隔离 API

**Files**

- Add: `backend/src/main/java/com/zhyq/park/marketing/wh/WhAuthService.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/wh/WhAuthController.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/wh/WhAuthContext.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/wh/WhOnboardingController.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/wh/WhDashboardController.java`
- Modify: `backend/src/main/java/com/zhyq/park/auth/JwtAuthFilter.java`, `backend/src/main/java/com/zhyq/park/auth/SecurityConfig.java`
- Reuse: `MktWarehouse`, `MktWarehouseOnboarding`, `MktWarehouseOnboardingService`, `MktCustomerErpMap`, `MktReferralOrder`
- Tests: `backend/src/test/java/com/zhyq/park/marketing/wh/WhAuthIsolationTest.java`, `WhOnboardingControllerTest.java`, `WhDashboardControllerTest.java`

- [ ] 写红测：`POST /wh/v1/auth/wx-login` 在 mock/真实模式下都只产生 `wh:{warehouseId}` subject；未绑定联系人返回 `registered=false`；`POST /wh/v1/auth/bind-phone` 只能绑定当前 openid 对应的单联系人策略，已绑定伙伴手机号必须返回身份冲突错误而不能覆盖。
- [ ] 写红测：云仓 token 可以访问 `/wh/v1/**`，不能访问 `/mp/v1/**`、后台 `/crm/**`；伙伴/后台 token 访问 `/wh/v1/**` 得到 401/403；无 token 的登录和健康检查仍可用。
- [ ] 写红测：仓库 A token 读/改申请、ERP 状态、客户映射、订单分页时永远看不到仓库 B 数据；请求体中的 `warehouseId` 被忽略或拒绝；跨项目数据返回空集/403。
- [ ] 实现 `WhAuthContext.currentWarehouseId()` 和 `requireWarehouse()`，在 controller 层统一注入；登录支持 `wx-login`、加密手机号绑定、已存在 `contact_openid` 的回登，并审计绑定/解绑。
- [ ] 实现 `/wh/v1/apply`、`/wh/v1/onboarding`：申请只写当前仓库，附件只保存文件引用和审计信息，状态流转复用现有 onboarding service，云仓端不能自行推进到正式上线。
- [ ] 实现 `/wh/v1/dashboard`、`/wh/v1/customers`、`/wh/v1/orders/page` 的只读查询，响应过滤伙伴收益、佣金比例、手机号等敏感字段，订单查询只按当前 `warehouse_id`。
- [ ] 在 SecurityConfig 中增加 `/wh/v1/auth/**` permitAll、其余 `/wh/v1/**` authenticated，并让 JwtAuthFilter 对 subject 前缀建立对应 authorities；补充过滤器集成测试后运行全量 auth/marketing 测试。

## Task 3: 云仓商家小程序页面和 API 客户端

**Files**

- Add: `miniapp/src/api/warehouse.js`
- Add: `miniapp/src/pages/warehouse-login/index.vue`
- Add: `miniapp/src/pages/warehouse-apply/index.vue`
- Add: `miniapp/src/pages/warehouse-onboarding/index.vue`
- Add: `miniapp/src/pages/warehouse-erp/index.vue`
- Add: `miniapp/src/pages/warehouse-dashboard/index.vue`
- Add: `miniapp/src/pages/warehouse-customers/index.vue`
- Add: `miniapp/src/pages/warehouse-orders/index.vue`
- Add: `miniapp/src/pages/warehouse-settlement/index.vue`
- Add: `miniapp/src/pages/warehouse-contracts/index.vue`
- Add: `miniapp/src/pages/warehouse-notice/index.vue`
- Modify: `miniapp/src/pages.json`, `miniapp/src/utils/request.js`, `miniapp/src/App.vue`
- Tests/build: `miniapp` H5 and mp-weixin builds; `frontend/src/api/__tests__/marketing.spec.js` only if shared API helper changes.

- [ ] 写客户端红测/手工验收脚本：登录 token 使用独立存储键；401 自动清除云仓 token并回登录；每个 API 方法只拼接 `/wh/v1/**`，不能误用 `/mp/v1/**`。
- [ ] 实现登录、申请、进度五步时间线、ERP 自助（凭证状态/沙箱测试/日志/心跳）、看板、客户、出库单、结算、合同/协议、通知页面；页面只展示服务端返回字段，不自行计算佣金或结算金额。
- [ ] 在 `pages.json` 增加非伙伴 tab/stack 路由，保持已有伙伴 tabBar 不变；登录后根据 token subject 进入对应首页，不能把云仓 token 注入伙伴页面。
- [ ] 为网络错误、空列表、断连、结算争议和附件上传失败补齐页面状态；所有金额和时间使用既有格式化工具。
- [ ] 运行 `pnpm build:h5` 和 `pnpm build:mp-weixin`，确认页面路由、SFC 编译和请求拦截器无回归。

## Task 4: ERP 事件 Inbox、严格契约和映射队列

**Files**

- Add: `backend/src/main/resources/db/migration/V61__marketing_erp_reliability.sql`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktErpEventInbox.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktErpDeadLetter.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktErpUnmapped.java`
- Add: corresponding Mapper files under `marketing/mapper/`
- Add: `backend/src/main/java/com/zhyq/park/marketing/open/ErpEventContract.java`
- Modify: `ErpOpenApiController.java`, `ErpOrderIngestService.java`, `ErpSignatureService.java`
- Tests: `backend/src/test/java/com/zhyq/park/marketing/open/ErpEventContractTest.java`, `ErpInboxIdempotencyTest.java`, `V61MigrationContractTest.java`

- [ ] 写红测：`event/order_no/occurred_at/warehouse_code/customer_code` 缺一即 `BAD_PAYLOAD`；`qty/packages` 必须为正整数；未知事件、错误时间格式、错误仓库编码均不落正式订单；`shipped` 必须有 tracking_no。
- [ ] 写红测：Inbox 唯一键按 `warehouse_id + app_id + event_id`（没有 event_id 时按 payload digest + order_no + event）幂等；同一事件并发两次最多产生一个订单/佣金；退款/取消先到会写 tombstone，后到 shipped 不得入账。
- [ ] 写红测：未映射 `customer_code` 进入 unmapped 队列且响应仍为可重放的 202/稳定结果；补映射后 replay 只生成一次正式订单；沙箱凭证事件只能落测试状态。
- [ ] 设计 V61 表：Inbox 保存原文摘要、事件状态、attempts、next_retry_at、occurred_at、processed_at、last_error、project_id；死信保存最终错误和重放审计；unmapped 保存仓库、客户编码、订单号、payload、状态和唯一键。
- [ ] 实现 `ErpEventContract.parseAndValidate(JsonNode)`、Inbox 持久化和事件路由；保留旧 `/order-event` 响应字段，新增稳定 `event_id/status`，批量接口逐条返回状态并在解析失败时写日志。
- [ ] 将拒绝日志和事件 Inbox 状态写入独立事务，避免业务回滚时审计丢失；同步日志清理保留最近 180 天并以配置驱动。
- [ ] 运行 open 包全部测试、V61 migration contract test，并用现有 webhook 样例做一次离线回归。

## Task 5: ERP 重试、死信、心跳、重放和对账

**Files**

- Add: `backend/src/main/java/com/zhyq/park/marketing/open/ErpRetryJob.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/open/ErpHeartbeatJob.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/open/ErpReconcileJob.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/open/ErpReliabilityService.java`
- Modify: `MktErpController.java`, `MktWarehouse.java`, `MktWarehouseErp.java`, `MktWarehouseService.java`/分配校验处
- Tests: `ErpRetryJobTest.java`, `ErpHeartbeatJobTest.java`, `ErpReconcileJobTest.java`, `ErpReplayIsolationTest.java`

- [ ] 写红测：业务可重试异常按 1m/5m/30m/2h 指数退避，达到 5 次进入死信；验签、字段错误、仓库不匹配等 4xx 直接失败不重试；重放带审计人和原因。
- [ ] 写红测：heartbeat 每 10 分钟扫描；合法事件/主动 ping 更新 `last_sync_at`；超过 6 小时标记 `erp_status=3` 并阻止新的客户分配；恢复事件后恢复可分配状态并审计。
- [ ] 写红测：对账按仓库和结算周期生成不可变快照，订单集合/件数/服务费差异低于 2% 可结算，达到或超过 2% 则冻结相关佣金/结算批次；1.99%、2.00%、2.01% 三个边界都覆盖。
- [ ] 实现后台 `/crm/marketing/erp` 的死信、unmapped 列表、单条/批量 replay、heartbeat 状态和 reconcile 结果查询；所有操作强制项目权限并写 `MktAuditService`。
- [ ] 将 ERP 断连状态接入云仓承接/分配校验；只阻止受影响仓库的新分配，不影响历史查询和已生成的结算快照。
- [ ] 运行 job/服务测试，并通过手工构造 6 小时边界和 2% 差异样例核验状态变更。

## Task 6: 服务费月度账单

**Files**

- Add: `backend/src/main/resources/db/migration/V62__marketing_service_fee_bills.sql`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktServiceFeeBill.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktServiceFeeBillLine.java`
- Add: Mapper/Service/Job/Controller under `marketing/finance/`
- Modify: `MktServiceContractService.java`, `MktReferralOrderImportService.java` only for billable source metadata
- Tests: `MktServiceFeeBillServiceTest.java`, `MktServiceFeeBillJobTest.java`, `V62MigrationContractTest.java`

- [ ] 写红测：每月按已生效服务合同和首期/续期规则生成园区应收服务费账单；同一 `billing_key=contract:{id}:service:{yyyy-MM}` 重跑不重复生成；合同失效、零金额和沙箱订单不出账。
- [ ] 写红测：账单行引用订单/服务合同、服务费、税率/币种和来源快照；订单退款/取消在账单未结算时可冲正，已结算账单只生成调整行，不篡改原账单。
- [ ] 设计 V62 表和唯一键：账单头包括合同、周期、状态、总额、冻结原因、project_id；账单行包括 referral_order_id/source、金额和不可变 JSON 快照；状态至少草稿/待确认/已确认/已结算/争议。
- [ ] 实现 `MktServiceFeeBillService.generate(LocalDate period)`、`confirm(Long billId, Long warehouseId)`、`dispute(Long billId, String reason)`、`snapshot(Long billId)` 和月度 job；云仓端只能操作自己的账单。
- [ ] 增加后台和云仓分页接口，响应过滤伙伴佣金字段；确认/争议为幂等状态机，越权和重复操作返回稳定错误码。
- [ ] 运行 finance/marketing 测试和 V62 migration contract test，验证重跑、冲正、越权三类场景。

## Task 7: 云仓结算快照、直签平台费和解冻

**Files**

- Add: `backend/src/main/resources/db/migration/V63__marketing_warehouse_settlement.sql`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktWarehouseSettlement.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktWarehouseSettlementLine.java`
- Add: `backend/src/main/java/com/zhyq/park/marketing/entity/MktDirectSignPayment.java`
- Add: Mapper/Service/Job/Controller under `marketing/settlement/`
- Modify: `MktServiceContractController.java`, `MktServiceContractService.java`, `MktCommissionService.java`, `MktLeaseCommissionListener.java`
- Tests: `MktWarehouseSettlementServiceTest.java`, `MktDirectSignPaymentTest.java`, `V63MigrationContractTest.java`

- [ ] 写红测：按 `MktWarehouse.settleCycle` 生成仓库结算快照；同一仓库/周期/版本只能生成一个活动快照；对账差异达到 2% 的周期必须为冻结状态；快照金额来自已确认账单/订单，不能实时漂移。
- [ ] 写红测：结算确认、争议、重新打开严格按状态机运行；仓库 A 不能读取/操作仓库 B；重复确认和重复争议幂等；结算行包含订单/账单引用和费率快照。
- [ ] 写红测：直签合同上传/生效时只允许当前仓库作为合同方；平台费模型支持 `perOrder` 和 `ratio`；平台费支付到账事件按 `contract_id + payment_no` 幂等，只有已到账且金额足额时解冻对应 `SOURCE_PLATFORM_FEE` 冻结佣金。
- [ ] 设计 V63 表：结算头、结算行、直签支付、状态/审计字段、唯一键和 project_id；文件只存引用，禁止将原始支付凭证内容写日志。
- [ ] 实现 `/wh/v1/settlement/**`、`/wh/v1/contracts/**`、`/wh/v1/agreement/**`、`/wh/v1/notice/**` API，后台保留审核/支付入口；合同方和 warehouse_id 均由服务端推导/校验。
- [ ] 实现平台费到账事件适配现有 DomainEvent，补齐冻结 → 到账 → 解冻/拒绝路径；支付失败或部分到账不得解冻，不得重复发放佣金。
- [ ] 运行 settlement/contract/commission 测试、V63 migration contract test，并用 A/B 仓库隔离样例验收。

## Task 8: 集成、文档和最终验证

**Files**

- Modify: `/Users/aole/Documents/cc/全民营销-接手报告-2026-09-21.md`
- Modify: `docs/marketing/接手报告-2026-09-21.md`
- Modify: `docs/marketing/ERP接入契约.md`
- Modify: `miniapp/README.md`
- Modify: `.superpowers/sdd/全民营销-接手报告-2026-09-21/progress.md`

- [ ] 合并各任务的接口清单、状态机、迁移版本、回滚说明和配置项；确认 V61/V62/V63 顺序连续且 Flyway 可重复启动。
- [ ] 增加一条离线验收脚本/测试链：云仓登录 → 申请 → 沙箱 ERP 测试 → 正式事件 → unmapped 补映射 replay → 对账 → 月账单 → 结算确认 → 直签平台费到账 → 佣金解冻；每一步断言身份和 warehouse_id。
- [ ] 运行后端：`export JAVA_HOME=/opt/homebrew/opt/openjdk@17; export PATH="$JAVA_HOME/bin:$PATH"; /Users/aole/tools/maven-current/bin/mvn -o -q test`；记录 surefire tests/failures/errors/skipped。
- [ ] 运行前端：在 `frontend` 执行营销 API 测试和 `pnpm build`；在 `miniapp` 执行 `pnpm build:h5` 与 `pnpm build:mp-weixin`；记录退出码和已知非阻塞 warning。
- [ ] 运行 `git diff --check`、迁移契约测试、权限隔离测试和接手报告 SHA256 对比；确认两份报告逐字一致。
- [ ] 由主代理做一次只读 review：检查越权查询、金额浮点、事务边界、事件重复、日志泄密和回归；把发现的问题修完后再更新进度账，不提交不推送。
