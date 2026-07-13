# 对标卡片：Yardi（Voyager Commercial / Breeze，参考 MRI Software）

**资料充分度**：中高：功能模块、CAM 对账流程、Deal Manager→Voyager 联动、审批工作流均有官方页面+第三方实施商/客户操作手册双重佐证；但 Voyager 详细配置文档在 Client Central 需登录，UI 细节主要依赖第三方指南与客户 PDF，界面还原度有限。

## 定位与目标用户
Yardi 是全球商业/住宅地产管理软件龙头，Voyager Commercial 定位为大中型商业地产（办公/工业/零售/园区级设施）的一体化 ERP：租约管理、物业运营与财务会计共用单一数据库；Breeze/Breeze Premier 是面向中小业主的简化云版本。目标用户为持有型商业地产业主、资管公司与物业管理公司。参考对象 MRI Software 定位类似但主打"开放生态"，其租约管理与费用回收（recoveries）引擎在复杂商业租约上被公认最强。

## 功能菜单树 / 模块划分
- Voyager Commercial（角色化左侧导航，按角色呈现不同菜单）
  - Commercial（商业租约）
    - Lease Administration：租约条款库（递增/CPI 调整、百分比租金、期权、免租期）
    - Recovery/CAM：Recovery Pool Setup（费用池）、Recovery Reconciliation（年度对账）、Recovery Estimate（次年预估）
    - Retail：零售租户销售额申报、百分比租金计算
  - Financials（财务）
    - GL 总账（多主体、分摊、循环凭证）
    - AR 应收（租金账单、收款、账龄、滞纳金）
    - AP 应付（PayScan 发票扫描+多级审批、Bill Pay 付款）
  - Dashboards：Role-based Dashboard、Workflow Dashboard（审批待办）、PO Dashboard
  - Reports：Rent Roll、Delinquency、Recovery Reconciliation Statement、财务三表
  - Setup / Admin：角色权限、工作流配置、Charge Code / Recovery Code
- Elevate Suite（同一数据库的运营层模块）
  - Deal Manager（招商/交易管道 CRM + Legal 租约生成）
  - Facility Manager / Construction Manager / Forecast Manager / Floorplan Manager
- 门户与配套
  - CommercialCafe（租户门户：账单/账期/在线支付/报修）
  - Smart Lease（AI 租约摘要）、Procure to Pay、Energy Suite、Data Connect、YSR 报表
- Yardi Breeze 商业版（8 大功能分区）
  - Property Management / Accounting / Accounts Payable / CAM Recovery
  - Rent Collection / Owner Tools / Maintenance / Setup & Support

## 核心业务流程编排
1) 招商到租约执行（Deal Manager→Voyager 双向同步）：线索→Deal 建档→报价/LOI→里程碑管道（proposal sent / LOI executed / approval received）→Legal 模块用模板+条款库自动生成租约文件→透明审批流→电子签→已签租约实时同步写入 Voyager 租约记录，并自动通知租约管理、工程、物业团队；Deal Manager 反向读取 Voyager 的到期租约、租户期权、已占用空间做续租提醒。2) 租约到账单：租约条款（基础租金+递增规则+CAM 预估+百分比租金）录入后自动生成周期性 charge，租赁与会计模块同库，租户业务活动直接反映到财务报表；租金递增、押金到期、租约到期可配置邮件/弹窗告警。3) CAM 年度对账闭环（最有特色）：GL 费用科目挂入 Recovery Pool→按租户 pro-rata（含 cap、排除项、gross-up 空置调整）月度预估计费→年终跑 Recovery Reconciliation 自动算差额→补收开票/多退款或挂账抵扣→据实重算次年月度预估。租户搬入/搬出/扩租自动影响分摊比例。4) 收款与逾期：CommercialCafe 收款（ACH/卡，PWIO 全额或按费用行支付）自动过账至 Voyager AR；滞纳金按规则自动评估；逾期报表可定时跑并设阈值告警（组合或单项目逾期率越界即通知）。5) 应付审批流：PayScan 按"角色+金额限额"路由发票，Workflow Dashboard 集中待办，支持退回（必填原因备注）、转呈上级、审批历史留痕，最终 POST 过账，满足 SOX 电子审批记录要求。

## 前端形态
Voyager 传统 UI 为经典 Web 后台（左侧角色导航列 + 顶部蓝色工具条 + Dashboard 切换），以"角色"为第一导航层级——用户先选角色（如 P2P Finance），进入该角色的仪表板，再切 Workflow/PO 等专项仪表板；数据支持逐级 drilldown。Voyager 8 与 Elevate 套件采用统一的现代 UI 框架，提供角色化仪表板、交互式财务分析、交易过滤器、一键邮件报表和关键数据通知，全设备（桌面/平板/手机）访问。Breeze 走"Refreshingly Simple"路线：仪表板日历直接标注当月到期租约，内置屏幕教程和实时聊天支持（官方宣称平均响应 17 秒）。租户端 CommercialCafe 门户（公开登录页 commercialcafe.securecafe3.com）提供完整台账（所有 charge/收款/余额）、当期与历史账单、未来收费计划表、在线支付与报修，且余额与 Voyager AR 完全同源。报表体验分三层：标准报表、YSR（Excel 模板+标记映射 Voyager 字段）、Data Connect+Power BI 钻取分析。无完全公开 demo，需预约演示；租户门户界面可参考 Boxer Property 公开 PDF 指南（boxerproperty.com）。

## 架构与功能设计要点
核心设计是"单一数据库 + 分层套件"：Voyager 8 为核心 ERP（GL/AP/AR/租约管理/物业管理），是数据库、会计引擎和运营底座；Elevate 套件（Deal Manager、Facility Manager 等）是运营层模块，独立授权但与 Voyager 共库共 UI 框架，实时访问物业/租约/租户/会计数据而无需导入导出；再上层是门户（CommercialCafe）与 Add-on（Smart Lease、Procure to Pay、Data Connect、Fixed Assets Manager 等）。多租户/多主体：单库可管理数百物业、多法律实体与基金结构，直接出合并报表。扩展点：对外集成走 SIPP（Standard Interface Partnership Program，450+ 伙伴，SOA 服务接口，商业数据接口基于 OSCRE 标准），辅以 ETL 工具做表/字段级导入导出，整体偏"围墙花园"——与 MRI 的开放 API 生态（MIX 平台 150+ 第三方应用）形成对照。流程引擎位置：审批工作流内建于核心（按角色+金额限额路由、可配置审批链、审批历史留痕），而非独立 BPM 产品；报表扩展走 YSR（Excel 模板标记，支持自定义 SQL 数据源）。闭源商业 SaaS，无公开仓库；详细配置文档在 Client Central（需客户登录）。

## 可借鉴点 TOP5
- **[D2流程编排]** 借鉴 CAM 对账闭环：给 zhyq 财务加"费用池→租户按面积 pro-rata 月度预估计费→年终实际费用对账→差额自动生成补收账单或退款/抵扣→据实重算次年预估"的公摊/物业费 true-up 流程，费用池直接挂财务科目
- **[D2流程编排]** 借鉴 PayScan 审批模式补审批流空白：按"角色+金额限额"配置审批路由，做一个 Workflow Dashboard 集中待办页（列出我的待审+下一步动作+退回必填原因+审批历史），先覆盖合同审批与付款审批两个场景
- **[D2流程编排]** 借鉴 Deal Manager→Voyager 联动：zhyq 招商CRM 增加交易里程碑管道（报价已发/意向书签署/审批通过），成交后一键将客户+房源+商务条款自动带入合同起草页，合同生效自动通知财务与物业，续租则由到期租约反向生成商机
- **[D1功能分类]** 借鉴 CommercialCafe"门户余额=应收余额"：为 zhyq 租户端做对账自助页（完整台账、历史账单、未来收费计划表），支持 PWIO 一键全额支付或按账单行支付，收款自动核销回财务模块
- **[D3前端体验]** 借鉴角色化仪表板+阈值告警：zhyq 工作台按角色（招商/财务/物业）呈现不同待办与KPI，仪表板日历标注当月到期合同，逾期率等指标设阈值越界自动站内信/邮件告警，把驾驶舱从展示型升级为行动型

## AI 备注（一行）
AI 嵌入位：Smart Lease 用 AI 做租约摘要并将结构化条款直接写入 Voyager 租约记录（加速入驻与计费）；Voyager 8 内嵌 Virtuoso 助手在当前界面内导航工作流并推送相关文档。

## 来源
- https://www.yardi.com/product/voyager-commercial/
- https://www.yardibreeze.com/commercial-features/
- https://www.yardi.com/product/deal-manager/
- https://www.yardi.com/product/commercialcafe-tenant-portal/
- https://www.yardi.com/suite/elevate-suite-commercial/
- https://www.capveri.com/resources/software/yardi-voyager/cam-setup
- https://www.jmco.com/articles/real-estate/cam-reconciliation-in-yardi-a-guide-for-commercial-property-managers/
- https://www.bcsolut.com/resources/yardi-voyager-8-upgrade-guide
- https://www.bcsolut.com/post/yardi-vs-mri
- https://www.yardi.com/news/press-releases/yardi-adds-commercial-data-interface-to-voyager-standard-interface-partnership-program/
- https://www.boxerproperty.com/wp-content/uploads/2023/02/CommercialCafe-Tenant-Portal-Guide.pdf
- https://www.mrisoftware.com/solutions/lease-management-software/
