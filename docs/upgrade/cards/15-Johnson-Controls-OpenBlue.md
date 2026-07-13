# 对标卡片：Johnson Controls OpenBlue

**资料充分度**：高（偏中高）：功能模块树、FDD→工单流程、CMMS 集成框架均有官方文档（docs.johnsoncontrols.com User Guide/Product Bulletin）直接佐证并与官网/微软来源交叉验证；数字孪生前端仅有官方描述与 JCI 高管署名文章，缺公开截图/可注册 demo，3D 视图具体交互细节为推断；多租户内部实现无公开资料，仅能确认空间级权限与多实例托管。

## 定位与目标用户
OpenBlue 是江森自控 2020 年推出的智慧建筑数字化平台+应用套件，定位"建筑全生命周期的单一数据层 + AI 应用"，覆盖能源/可持续、设备性能、空间与工作场所、租户体验。目标用户为商业地产、医疗、高校、政府、制造等大型楼宇组合（portfolio）的业主与设施运营团队，而非单体项目；商业模式为 SaaS（Azure 托管）+ 边缘网关硬件 + 服务。2023 年收购 FM:Systems 后补齐 IWMS（设施/空间/搬迁/租约管理），形成"数据平台—运营分析—工作场所管理—员工体验"完整栈。

## 功能菜单树 / 模块划分
- OpenBlue 套件层（openblue.johnsoncontrols.com 官方分类）
  - Workplace Planning & Management：OpenBlue Insights（空间分析/预订/环境监测/传感器）、OpenBlue Workplace（原FM:Systems IWMS：设施管理、维保、资产、空间、不动产优化、战略规划、搬迁管理）
  - Equipment Performance & Operations：设备性能（FDD、2D/3D数字孪生、远程指挥控制）
  - Energy Efficiency & Sustainability：Net Zero、中央冷站优化、手术室优化
  - Workplace Experience & Productivity：OpenBlue Employee（会议室/工位预订）、Visitor、Companion（员工App）
  - Cybersecurity（Airwall 零信任）、Artificial Intelligence
- OpenBlue Enterprise Manager（OBEM 6.x 应用内模块，据官方 User Guide）
  - Home Page：Observations & Recommendations（可直接建工单）、Energy Savings、个性化首页、角色+空间级权限
  - Utility Bill Manager（公用事业账单）
  - Sustainability Manager：Energy（portfolio→location→building→meter→floor→wing→room 分层 widgets）、Emissions（对接 Energy Star）、Goals & Targets、Improvement Measures、Grid Interactive Optimization（需求响应事件）
  - Asset Manager：故障管理、设备级 widgets（冷机/锅炉/冷却塔/AHU）、Fault Analysis、Command & Control（舒适控制、时间表、FDD 触发自主控制）
  - Space Performance：楼/层/区/会议室空气质量与利用率
  - Tenant Manager / Tenant Billing / Tenant Portal：费率卡、多表计分摊、账单审核与批量发送、租户加班时段申请（楼层平面图选择）
  - Performance Advisor（AI 持续建议）、Data Auditor、Compliance Manager
  - Service Manager：Summary / Work Order（自动工单、FDD 静音、导出）/ 服务报告
  - Alarm Manager、Reports（含 Power BI 报告）、Green Hub、Plant Simulator、Vendor Integrations、Setup

## 核心业务流程编排
楼宇运营流程成熟度是 OpenBlue 的核心卖点，主线是"检测→诊断→定价→派单→闭环验证"的自动化链条：1) FDD 自动闭环：Asset Manager 的故障检测诊断引擎按可配置规则（含自定义规则）识别故障，按严重度定优先级并通知；"故障货币化"给每条故障标注财务影响金额，用于排序维修优先级；Service Manager 在应用内直接基于 FDD 输出自动创建工单，工单自动附带设备、故障、诊断、空间上下文，并统计响应时长/状态/设备类型分析；修复后用"校准节能"验证故障消除带来的节省。2) CMMS 集成框架（OBEM CMMS Integration Framework，官方文档 LIT-12012497）：同一套 FDD 工单可外推到第三方 CMMS（Corrigo/Maximo/ServiceNow 等），即"分析平台产生工单、执行系统各选各的"的开放编排。3) 告警治理：Alarm Manager 聚合多系统告警，ML 过滤扰动告警（nuisance alarms），支持静音/确认/邮件确认，减少告警风暴。4) 自主控制回路：FDD 触发自主控制（autonomous control）、Energy and Comfort Intelligence 在能耗与舒适间自动调优，可一键关闭 AI 模式回到人工。5) 租户计费闭环：表计数据→费率卡定义→多表计按租户分摊→账单生成→人工审核→批量发送→租户门户自助查账/提工单/反馈；租户可在楼层平面图上发起"加班时段空调申请"，审批后自动执行设备时间表并计费——跨"空间+设备控制+财务"三域的编排。6) 需求响应：Grid Interactive Optimization 配置电网节点与需求响应计划，按事件自动执行削峰。7) IWMS 侧（OpenBlue Workplace/FM:Systems）：可配置工作流与审批、预防性维护计划、按 SLA 度量技师绩效并识别不达标区域、搬迁管理全流程（宣称自动化搬迁流程降低 83% 搬迁成本）、租约管理配置化流转。整体特点：流程不是通用审批流引擎，而是"规则+事件驱动"的领域编排，审批类流程放在 IWMS 产品里做成可配置工作流。

## 前端形态
整体理念是"single pane of glass"单屏统揽。1) OBEM 主应用为 Web 端（要求 Chrome/Edge），信息架构为：登录后落地"Home Page"，首屏不是菜单而是"Observations & Recommendations（观察与建议流，可直接转工单）+ Energy Savings（节省金额）"，即行动导向首页，且支持用户个性化重组首页；左侧为空间层级导航树（portfolio→location→building→floor→wing→room→asset），支持收藏定位，权限做到角色+空间级。2) 仪表板体系是 widget 化的：各模块由可自定义/可分享的 widgets 与 scorecard 组成，全局日期范围选择器统一联动；重报表场景直接嵌 Power BI（冷冻机房报告、工位/会议室利用率报告）。3) 数字孪生前端形态：官方明确"2D/3D digital twins"，OBEM 的 3D 视图基于 3D BIM（与 Autodesk 等合作），在建筑三维模型上叠加热力图与告警/事件，为告警提供空间上下文，实现"沉浸式"定位；数字孪生底座为 OpenBlue Twin + Azure Digital Twins，Brick 语义标准贯穿界面数据结构。3D 不是全局皮肤，而是嵌在设备性能/告警分析等场景中的上下文视图。4) 多端矩阵：OBEM Web（运营者）、Tenant Portal（租户 Web）、Companion App（员工移动端，NLP 交互订工位/调温/报修/访客/寻路）、kiosk 与会议室门牌 panel 共用一套体验。5) 官方提供在线交互式产品导览 openbluetour.johnsoncontrols.com（公开 demo 入口）；生产环境入口如 jemprod-ui.myenterprisemanagement.com。第三方评价（Verdantix/Capterra）称其对普通用户直观易学，但报表生成偏复杂、混合厂商组合需较多前期数据规范化。

## 架构与功能设计要点
闭源商业平台，无公开仓库。架构上"平台与应用分层"清晰：底层 OpenBlue Data Platform 分 Edge 与 Cloud 两块——Edge 核心是 OpenBlue Bridge 物理边缘网关（连接 BMS/消防/安防等本地系统，内嵌 Airwall 零信任虚拟隔离，边缘侧做规则动作与数据过滤归一）；Cloud 建在 Azure 上，负责数据接入、转换、富化、下发控制指令，统一身份服务（SSO）。语义层是关键设计：所有数据归一到 BRICK 兼容的 OpenBlue 标准本体，ROBOT 工具用 AI 自动把原始点位分类映射到该本体——这使上层应用（OBEM、Workplace、Companion 及第三方应用）与设备厂商解耦，号称 130+ 数据源、1000+ 集成、每秒百万数据点。扩展点/开放接口：a) API-driven 的 OpenBlue Twin（与 Azure Digital Twins 集成），供内外部应用消费；b) CMMS Integration Framework 作为工单外发的标准化集成框架（Corrigo/Maximo/ServiceNow）；c) Vendor Integrations 模块 + 合作伙伴第三方扩展市场；d) IWMS 侧提供 API 并可集成 HR/财务/日历数据。流程引擎位置：不存在统一 BPM 引擎——分析侧用"规则+事件"编排（FDD 规则、告警优先级、自主控制、需求响应事件），审批/工作流放在 FM:Systems IWMS 内做成可配置工作流（面向非技术管理员配置）。多租户组织：SaaS 全球多实例托管+高可用 SLA，应用内以 portfolio/location 空间树为租户隔离与授权粒度（角色+空间级访问控制），"租户"作为业务对象在 Tenant Manager 中独立建模（表计归属、费率卡、门户账号）。功能设计特点：每个模块 = "分层 widgets + 专属分析页 + 与工单/告警的联动出口"，模块间靠统一空间树和统一告警/工单对象衔接，而非页面级跳转拼接。

## 可借鉴点 TOP5
- **[D2流程编排]** 把 IoT 告警/能耗异常与物业工单打通成自动闭环：告警规则配严重度与'是否自动建单'，工单自动携带设备、位置、故障诊断上下文，并仿照 OpenBlue 的'故障货币化'给每条告警估算损失金额用于排序——zhyq 的 IoT 告警模块与工单模块现在是两个孤岛，先做规则表+自动建单即可落地
- **[D1功能分类]** 补'租户能耗分摊账单'链路：表计绑定租户/房源 → 费率卡（分时段单价）→ 周期自动生成能耗账单 → 人工审核 → 并入现有财务账单收款流——OpenBlue Tenant Billing 的费率卡+多表分摊+账单审核三步模型可直接套在 zhyq 已有的合同自动账单机制上
- **[D3前端体验]** 首页从'菜单+统计卡'改为 OpenBlue 式'观察与建议流'：聚合待办（临期合同、超标能耗、未处理告警、逾期账单）为可点击卡片并可一键转工单/催办；监控大屏 /screen 增加楼层平面图热力层（告警/能耗/空置按房间上色），作为 3D 数字孪生的低成本替代形态
- **[D4架构设计]** 建统一空间资产树（园区→楼→层→房间→设备）作为全系统公共维度和权限粒度：能耗、工单、租控、告警全部挂到同一棵树上按层级下钻，权限做到'角色+空间节点'——对应 OpenBlue 的 Brick 式层级模型与空间级访问控制，也顺带补上资产管理空白
- **[D2流程编排]** 补审批流引擎时按 OpenBlue/FM:Systems 的取舍落地：不做通用 BPM，先做'可配置工作流+SLA'两件事——合同/工单审批节点可由管理员配置，工单加 SLA 计时与超时升级，报表按 SLA 达标率考核物业团队

## AI 备注（一行）
AI 嵌入位值得记一笔：Performance Advisor 以"持续建议流"常驻首页（建议可直接转工单），2024 年新增生成式 AI 能源顾问；另用 ML 过滤扰动告警、NLP 嵌入员工 App 完成订位/调温/报修。

## 来源
- https://www.johnsoncontrols.com/openblue
- https://docs.johnsoncontrols.com/bas/r/OpenBlue/en-US/OpenBlue-Enterprise-Manager-User-Guide/6.1/Getting-started-with-OpenBlue-Enterprise-Manager?contentId=aHm434aJn4AhDqkBMxyNNw
- https://openblue.johnsoncontrols.com/
- https://www.johnsoncontrols.com/openblue/openblue-data-platform
- https://www.johnsoncontrols.com/openblue/openblue-digital-twin
- https://docs.johnsoncontrols.com/bas/r/OpenBlue/en-US/OpenBlue-Enterprise-Manager-Catalog-Page/6.2/OBEM-CMMS-Integration-Framework/OpenBlue-FMS-Workplace-CMMS-workorder-integration
- https://fmsystems.com/products/fms-workplace/
- https://news.microsoft.com/source/2020/12/08/johnson-controls-and-microsoft-announce-global-collaboration-launch-integration-between-openblue-digital-twin-and-azure-digital-twins/
- https://facilityexecutive.com/optimize-with-digital-twin-technology
- https://iotusecase.com/en/solution-examples/intelligent-building-management-with-openblue-enterprise-manager-on-azure/
- https://openblue.johnsoncontrols.com/workplace-experience-and-productivity/companion
