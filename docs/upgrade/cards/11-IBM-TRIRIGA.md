# 对标卡片：IBM TRIRIGA（主）/ Planon（简要对比）

**资料充分度**：高——TRIRIGA 侧有 IBM 官方文档 PDF、IBM Docs 流程页、GitHub 官方仓库与多所大学培训指南交叉印证（部分 ibm.com/docs 页面 403 无法直读，靠多来源摘要互证）；Planon 侧仅官网+第三方评测，菜单树与流程细节公开度低，故只作简要对比，其结论置信为中。

## 定位与目标用户
IBM TRIRIGA 是国际最成熟的 IWMS（集成工作场所管理系统）之一，面向大型企业/高校/政府的 CRE 与设施团队，覆盖不动产组合、租约会计（ASC 842/IFRS 16）、空间、资本项目、运维、预订与可持续性全链条；现已演进为 TRIRIGA Application Suite 并向 Maximo Real Estate and Facilities 品牌收敛。Planon 定位类似（荷兰厂商，1982 年创立，自称 CPIP：Connected Portfolio Intelligence Platform），主打 IWMS + 设施服务商业务系统 + 校园管理，面向大型组织，由认证伙伴实施（周期 9-18 个月）。两者公开资料中 TRIRIGA 的官方文档（ibm.com/docs 全套 User Guide PDF）和 GitHub 仓库远比 Planon 丰富，故以 TRIRIGA 为主。

## 功能菜单树 / 模块划分
- Home 角色化首页门户（我的门户 / 流程门户，按角色配置 portal sections）
- Requests 请求中心（Request Central / Workplace Services 自助报修、搬迁、餐饮、预订等请求）
- Tasks 我的任务（工作任务、审批待办、通知）
- Portfolio 组合台账（基础主数据）
  - Locations：Property 物业 / Building 楼宇 / Floor 楼层 / Space 空间
  - Organizations 组织、People 人员、Assets 资产、Geography 地理
- Contracts 合同
  - Lease Abstract 租约摘要 / Real Estate Lease 不动产租约 / Owned Property 自有物业协议
  - Payment Schedules 付款计划、Accounting 租约会计（ASC 842/IFRS 16）、OPEX 对账
- Projects 资本项目（立项、资金、进度）
- Space 空间管理
  - 空间分配 / Chargeback Allocations 部门计费分摊（房间级/楼层级）
  - Move Management 搬迁管理（请求→行项目→搬迁项目）
  - CAD Integrator / 楼层平面、Stacking 堆叠规划
- Maintenance 运维
  - Service Management：Service Request → Work Task、Service Plan、SLA
  - Preventive Maintenance 预防性维护、资源/服务商管理
- Reservations 预订（会议室、工位、设备、餐饮）
- Sustainability / Environmental 能源与环境（能耗、水、废弃物、排放）
- Reports 报表（Report Manager、我的报表）
- Tools 平台工具
  - Builder Tools：Data Modeler / Form Builder / Workflow Builder / Navigation Builder
  - Approvals & Notifications 审批与通知配置、系统管理
（注：菜单按角色与许可裁剪，各租户可用 Navigation Builder 自定义。Planon 模块划分类似：Real Estate & Lease Accounting / Space & Workplace Services / Asset & Maintenance / Energy & Sustainability + Workplace App 移动自助端）

## 核心业务流程编排
1）租约全生命周期（国际范式）：Lease Abstract（可从 Excel/OSCRE XML 导入，或 AI 抽取）→ 摘要审批完成后自动生成 Real Estate Lease 草稿 → 激活后自动生成付款计划与摊销表 → 付款处理（AP 集成 ERP）→ OPEX 对账（预估费用 vs 实际费用比对，差额自动生成一次性补款/退款）→ 会计假设复核（Review Assumption：初始采用/利率变化重算，状态 Processing→Active）→ 条款/期权/关键日期（clauses、options）触发到期提醒与续约评估。整条链是"合同→财务→提醒"闭环。
2）服务请求→工单（Request Central）：状态机 Draft →（Submit）Review In Progress（分发列表全员审批通过）→ Issued；由 Request Class（请求分类）挂接的 Service Plan 决定自动路由到对应服务提供方并生成 Work Task（含 SLA）；工单完成后若勾选 Response Required 则进入 Routing In Progress 回访环节（自动生成满意度调查 Response Log），否则直接 Completed 并关闭请求。
3）空间分摊与搬迁：Chargeback Allocation 支持房间级/楼层级把空间按面积/百分比分摊给部门并计费；搬迁请求提交后走标准审批 → 移交 Move Planner 汇总为 move line items / 搬迁项目 → 分派执行 → 完工后系统自动更新人员位置与空间占用台账并发调查问卷；战略搬迁上游接空间规划场景比选。
4）流程引擎机制：所有上述流程由平台级 Workflow Builder 驱动 —— 每个业务对象有 State Transition Family（状态转移族）定义生命周期，表单按钮/系统事件触发同步或异步工作流（校验、复制字段、生成关联记录、发通知），审批用分发列表会签，通知在 Tools > Approvals & Notifications 集中配置，交付的默认流程可被客户改造。Planon 对应做法：Accelerator 预置最佳实践（开箱工作流+报表+仪表盘），工单覆盖纠正性/预防性维护、技师派工、SLA 跟踪与移动端执行。

## 前端形态
TRIRIGA 前端分两代：经典层是"门户 + 表单"范式 —— 角色化 Home Portal（由 portal sections 组成：待办、提醒、常用查询），业务表单为 Tab > Section > Field 三级结构，动作按钮绑定工作流；菜单/门户均按角色和许可裁剪。新一代是 UX Framework 的 Perceptive Apps：早期基于 Polymer Web Components，3.8+ 平台转向 ReactJS + IBM Carbon Design System（官方提供 create-react-app 模板 @tririga/cra-template 和 tri-carbon-style-pack 皮肤包，https://github.com/IBM/tri-carbon-style-pack），代表应用有 Space Assessment / Space Management / Stacking 堆叠规划（拖拽调整楼层空间供需）、Dynamic Space Planning（楼层平面按 Charge to Org 等维度着色筛选）、室内定位占用看板（https://github.com/IBM/tririga-occupancy）。整体是"重表单后台 + 少量图形化专项 App"的组合，而非统一大屏。Planon 前端亮点在自助端：Planon Workplace App（有公开 14 天试用 demo：https://planonsoftware.com/us/resources/demos/planon-workplace-app/）支持交互式楼层平面订工位/会议室、扫二维码报修、查看同事到岗；空间模块有免 AutoCAD 技能的图形化占用可视化界面；评测反馈其主后台 UI 现代化偏慢。

## 架构与功能设计要点
TRIRIGA 最核心的架构特征是"元数据驱动的低代码平台 + 应用套件"分层：TRIRIGA Application Platform 提供一组浏览器内的 Builder 工具 —— Data Modeler（定义 Module/Business Object/字段/关联/State Transition Family 状态机）、Form Builder（同一业务对象可挂多张表单，Tab/Section/字段级配置，动作绑定工作流）、Workflow Builder（按模块组织、任务面板拖拽编排，支持同步/异步、事件触发）、Report Manager（跨对象查询报表）、Navigation Builder（自定义菜单与导航集合）。上层交付的 IWMS 应用（Real Estate/Space/Projects/Operations/Reserve/Environment 六大模块）本身就是用这套工具构建的，客户可用同一工具改造交付流程而无需写 Java —— 这是"产品即平台"的开放扩展点范式。UX Framework 提供 MVC 结构的现代前端扩展点（React+Carbon Web 应用可注册进平台）。集成侧：CAD Integrator/Publisher 对接 BIM/CAD，租约会计对接 ERP/AP，IoT 数据可馈入条件性维护。部署形态：TRIRIGA Application Suite 运行在 Red Hat OpenShift（Operator 化安装，含 Suite Licensing Service），许可用 AppPoints 点数池按用户档位消耗；托管服务为单租户实例（每客户独立环境），非 SaaS 多租户。开源可看仓库：IBM/tri-carbon-style-pack、IBM/tririga-occupancy、IBM/IBM-TRIRIGA-PolyReact（均为前端扩展样例，目录为标准 npm/React 工程结构，非产品源码）。Planon 架构对应物：Planon Universe 平台 + Open Application Platform 低代码 + Planon Marketplace 应用市场（第三方插件生态）+ Accelerator 预配置包。

## 可借鉴点 TOP5
- **[D2流程编排]** 照搬 TRIRIGA“状态转移族+分发列表会签”做通用审批引擎：为合同/工单/OA 等业务对象统一定义状态机（Draft→Review In Progress→Issued/Active→Completed），提交时按可配置审批人列表逐级/会签流转，全员通过自动推进状态——先做成一个可挂到任意单据的通用组件，填补 zhyq 无审批流引擎的空白
- **[D2流程编排]** 物业工单引入 Request Class + Service Plan 路由模型：维护“请求分类字典→服务方/班组→SLA 时限→是否回访”映射表，租户提交报修后按分类自动派单生成工作任务并挂 SLA 倒计时，完工时若配置了回访则自动推送满意度问卷，替代 zhyq 目前的手工派单简单流转
- **[D1功能分类]** 合同模块补齐 TRIRIGA 式租约生命周期件套：合同上增加条款/期权/关键日期（免租期、续约选择权、递增条款）结构化字段，到期前 N 天自动生成提醒待办；财务增加“物业费/公摊 OPEX 对账”流程——预估 vs 实际比对后差额自动生成一次性补款或退款账单，串进现有周期账单链
- **[D3前端体验]** 房源租控从表格升级为楼层平面图交互：参照 TRIRIGA Dynamic Space Planning/Stacking，用 SVG 楼层平面按租户/租控状态/欠费状态着色，点击房间弹出详情，并支持按面积百分比把空间分摊到部门/租户出计费报表（chargeback），也为后续会议室/工位图上预订复用
- **[D3前端体验]** 把统一 14 导航后台改造成角色化工作台：登录后按角色渲染不同 portal sections（招商看跟进漏斗、财务看待收/逾期、物业看待派工单+SLA 预警），并参照 Planon Workplace App 把报修、会议室预订、账单查询做成租户自助 H5/小程序入口，与管理后台分离

## AI 备注（一行）
IBM 已在 Maximo Real Estate and Facilities 中内置 AI 租约抽取（从合同 PDF 自动提取关键日期/金额/条款生成 lease abstract 供人工确认）——zhyq 合同录入环节是同款嵌入位。

## 来源
- https://www.ibm.com/docs/en/SSFCZ3_11.6/pdf/pdf_tri_contract_mgmt.pdf
- https://www.ibm.com/docs/en/tap/5.0.0?topic=applications-application-building-tririga-application-platform
- https://www.ibm.com/docs/en/tririga/11.5?topic=moves-space-move-management-process-flows
- https://www.ibm.com/docs/en/tap/4.4.0?topic=building-ux-web-applications
- https://github.com/IBM/tri-carbon-style-pack
- https://github.com/IBM/tririga-occupancy
- https://www.ibm.com/products/tutorials/running-ibm-tririga-application-suite-on-aws-with-red-hat-openshift
- https://studylib.net/doc/18782126/ibm-tririga-10-request-central-user-guide
- https://facilities.rice.edu/ibm-tririga/ibm-tririga-faqs
- https://planonsoftware.com/us/software/iwms/
- https://planonsoftware.com/us/modules/workplace-app/
- https://planonsoftware.com/us/resources/demos/planon-workplace-app/
- https://apps.planonsoftware.com/
- https://intuitionlabs.ai/software/facilities-real-estate/integrated-workplace-management-iwms/planon
