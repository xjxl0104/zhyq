# 对标卡片：Odoo（Rental 租赁 / Property Management 行业方案 / Maintenance 维保 + Subscriptions/OCA contract）

**资料充分度**：高——功能与流程均有官方文档+GitHub 仓库实测（addons 目录、LICENSE、OCA 模块清单）双重佐证；仅企业版 Rental 甘特细节与租户门户依赖文档描述未能实机验证（该两点为中等置信）。

## 定位与目标用户
Odoo 是全球最大的开源模块化 ERP 套件（GitHub 4 万+ star 量级、约 400 个官方社区模块），"物业/租赁管理"不是一个单体产品，而是由 CRM、Rental、Subscriptions、Accounting、Documents、Maintenance 等标准应用组合出的行业模板（industry_real_estate）。目标用户覆盖中小企业到大型集团的租赁运营商、物业公司与设备租赁商；社区版 LGPLv3 免费自部署，Rental/Studio/Sign 等属企业版（OEEL-1 商业协议）。对 zhyq 的对标价值在于其国际开源 ERP 的"合同-账单-收款"范式与"一套数据模型多视图"的前端体系。

## 功能菜单树 / 模块划分
- Rental 租赁应用（企业版）
  - Orders/Dashboard：租赁订单看板，左侧按 Rental Status（报价/预订/取货/归还/逾期）与 Invoice Status 过滤
  - Schedule：甘特排期视图（产品×时间轴，可视化占用与空置）
  - Products：可租产品，按时长阶梯定价（时/天/周/月）
  - Reporting：租赁分析（透视/图表）
  - Configuration：逾期罚金、Padding/Security Time、数字签署、线上租赁
- Property Management 行业模板（应用组合）
  - CRM：租赁线索/询价管理（网站表单自动生成线索）
  - Properties：房源档案（描述、定价、可用性）
  - Rental：租约合同（模板化生成、Gantt 可用性视图直接建约、入住抄表字段）
  - Subscriptions：循环账单计划（Billing Period、Automatic Closing）
  - Accounting：账单/收款/催收/财务与分析会计
  - Documents：合同文档归档
- Maintenance 维保应用（社区版）
  - Dashboard：维保团队看板
  - Maintenance Requests：工单（Corrective 故障/Preventive 预防，星级优先级，可拖拽看板阶段）
  - Maintenance Calendar：日历/看板/列表/透视/图表/活动 6 种视图
  - Equipment：设备台账（MTBF/MTTR/预计下次故障自动统计）
  - Teams / Reporting / Configuration
- 横向支撑
  - Sign：合同电子签署
  - Approvals / Studio Approval Rules：审批
  - Settings→Technical→Scheduled Actions / Automation Rules：定时与自动化引擎

## 核心业务流程编排
合同-账单-收款主链路（国际开源 ERP 范式）：1) CRM 线索→报价单（Quotation）→确认为租赁订单，确认时出现 Sign Documents 按钮走电子签（需开启 Digital Documents，自动联动 Sign 应用）；2) 短租走"取货-归还"物流状态机：Pickup→Validate 打 Picked-up 横幅，Return→Validate 打 Returned 横幅，可打印取还货回执，Rental Transfers 可联动库存出入库单；逾期自动按 Extra Hours/Extra Days 规则计罚金（=zhyq 滞纳金），Padding/Security Time 在两次租赁间锁定资源用于保洁维保（=锁房源思想）；3) 长租/物业走 Subscriptions：租约确认即产生订阅，系统不一次性生成全部周期账单，而是靠每日 cron"generate recurring invoices and payments"，当天=Next Invoice 日期即生成并过账一张账单，再按 Recurring Plan 推进下一开票日；4) 收款自动化：客户门户/电商结账时支付网关保存 Payment Token，续费日自动扣款——成功则开票过账，失败则周期性发提醒邮件，连续失败超 14 天自动关闭订阅；异常订单打"Contract in exception"标记，阻止定时任务重复扣款；Automatic Closing 配置 N 天未付自动关约。5) 审批流：Studio Approval Rules 把多级审批直接挂在表单按钮上（条件过滤、顺序步骤、Exclusive Approval 互斥、未授权点击自动给审批人生成待办活动、审批记录写入 chatter 审计留痕、支持审批权委托）；Approvals 应用提供按金额分级的串行审批链；base_automation 提供字段变更/时间/邮件触发的自动化规则。6) 维保：工单区分 Corrective/Preventive，Preventive 按频率自动生成，看板阶段可配置"Request Confirmed 自动锁定工作中心""Request Done 关单"，制造工位可直接发起维保请求（跨模块联动范式）。

## 前端形态
前端是自研 OWL 框架（github.com/odoo/owl，TypeScript，类 Vue/React 声明式组件+hooks 响应式）驱动的 SPA：NavBar+ActionContainer+面包屑动作栈，URL 与状态同步。核心范式是"一套数据模型 × 多视图渲染"：同一模型一键切换 List（行内编辑）/Kanban（拖拽阶段流转）/Form/Calendar/Gantt（租赁排期与租约空置期）/Pivot（透视钻取）/Graph/Activity 视图，视图由服务端 XML 定义、前端 registry 组件渲染，字段级 widget 可插拔。导航范式为"应用宫格首页 + 每应用顶部菜单"，非固定左侧树。所有业务单据右侧/下方带 chatter（消息、审计日志、关注者、计划活动），是贯穿全系统的协作层。工作台形态：各应用 Dashboard + board 模块自定义仪表盘 + Spreadsheet(BI) 报表；无深色监控大屏形态。Studio（企业版）支持拖拽改表单/加字段/配审批/配自动化，是"低代码层盖在 ERP 上"的体验。公开体验：odoo.com 免费试用实例、runbot.odoo.com 可进各版本演示库；文档站 odoo.com/documentation 带截图。整体设计体系为自有 Odoo UI（基于 Bootstrap 定制），信息密度中等、移动端自适应。

## 架构与功能设计要点
开源协议：odoo/odoo 主仓 LGPLv3（LICENSE 文件明示）；企业版模块（Rental/sale_renting、Studio、Sign、Subscriptions 等）在私有 odoo/enterprise 仓库，OEEL-1 商业协议；OCA 社区仓库为 AGPL-3。主仓顶层结构（18.0 分支实测）：odoo/（框架内核：ORM、模块加载、Web 服务）、addons/（约 400 个官方模块，如 account/crm/maintenance/calendar/board/base_automation/web）、odoo-bin（启动器）、doc/、setup/、debian/。模块边界：每个 addon = __manifest__.py（声明依赖）+ models（Python 继承 _inherit 扩展任意模型）+ views XML（xpath 继承改界面）+ security 权限 CSV，装卸即插即用——"维保"社区版仅是 addons/maintenance 一个目录，hr_maintenance 等薄模块做跨域桥接。扩展点/流程引擎位置：无独立 BPMN 引擎，流程编排由四层组成——模型状态字段+看板阶段、ir.cron 定时任务（订阅开票即靠它）、base_automation 自动化规则（触发器+条件+动作，装 Studio 自动带上）、Studio Approval Rules 按钮级审批；前端扩展靠 registry（字段/视图/服务注册表）+ services 依赖注入。多租户：multi-company 机制（记录带 company_id，一实例多公司多分部），SaaS 版按数据库隔离租户。OCA 生态佐证模块组织方式：OCA/vertical-rental（16.0 实测含 rental_base、rental_offday、rental_pricelist、rental_product_pack、sale_rental——租赁品在仓库自动生成 Rental In/Rental Out 两个库位，租出=出库单+预定回库单）；OCA/contract（18.0 实测 30+ 细粒度模块：contract 主体+contract_forecast 账单预测、contract_payment_mode、contract_price_revision 调价、contract_invoice_auto_validate 等），体现"一个功能点一个小模块"的极致拆分。

## 可借鉴点 TOP5
- **[D2流程编排]** 账单改为滚动生成而非合同审批后一次性生成全部周期账单：给合同维护 next_invoice_date 字段+每日定时任务到期才生成账单并顺延日期，配合 Contract in exception 异常标记阻断自动动作，天然支持中途调租、退租截断和防重复扣款——zhyq 财务模块可直接改造现有账单生成器。
- **[D2流程编排]** 用 Odoo Studio 式"按钮级审批规则"填补审批流空白：不上重型 BPM，而是在合同提交/退款/免滞纳金等关键按钮上可配置多级审批（条件表达式决定是否触发、审批顺序、同人互斥、未授权点击自动生成待办、审批记录写入操作日志），zhyq 用一张 approval_rule 表+按钮拦截即可落地。
- **[D2流程编排]** 逾期与锁房源规则化：借鉴 Rental 的 Extra Days 罚金配置+Padding/Security Time（两次租赁间自动锁定资源留给保洁维保）+Automatic Closing（欠费 N 天自动关约），把 zhyq 硬编码的滞纳金和锁房源逻辑抽成园区级可配置参数。
- **[D3前端体验]** 租控升级为可交互甘特：Odoo 物业方案用 Gantt 一屏展示"房源×时间轴"上的合同与空置期，并支持从可用性视图直接点选空档新建租约——zhyq 租控图可从静态色块升级为时间轴甘特+框选建约入口，打通租控→合同。
- **[D4架构设计]** 给核心单据加统一 chatter 协作层：Odoo 每张单据（合同/工单/账单）自带消息、字段变更审计、关注者、计划活动（跟进提醒），一个 mixin 全系统复用——zhyq 可做一个通用"动态+操作日志+待办提醒"组件挂到合同/工单/招商跟进详情页，替代分散的备注字段。

## AI 备注（一行）
Odoo 的 AI 嵌入位主要在 IAP 服务：CRM 线索评分/信息补全（crm_iap_enrich）与票据 OCR 数字化自动生成账单分录，后者适合 zhyq 财务录入场景借鉴。

## 来源
- https://www.odoo.com/documentation/18.0/applications/sales/rental.html
- https://www.odoo.com/industries/property-management
- https://www.odoo.com/documentation/19.0/applications/sales/subscriptions/scheduled_actions.html
- https://www.odoo.com/documentation/19.0/applications/sales/subscriptions/automatic_payments.html
- https://www.odoo.com/documentation/18.0/applications/inventory_and_mrp/maintenance/maintenance_requests.html
- https://www.odoo.com/documentation/18.0/applications/studio/approval_rules.html
- https://www.odoo.com/documentation/18.0/developer/reference/frontend/framework_overview.html
- https://www.odoo.com/documentation/18.0/developer/reference/user_interface/view_architectures.html
- https://github.com/odoo/odoo
- https://github.com/OCA/vertical-rental
- https://github.com/OCA/contract
- https://github.com/odoo/owl
