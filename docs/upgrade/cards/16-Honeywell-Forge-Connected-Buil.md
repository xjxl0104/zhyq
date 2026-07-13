# 对标卡片：Honeywell Forge / Connected Buildings（Forge Performance+ / Sustainability+ for Buildings）

**资料充分度**：高——运维流程与仪表盘两大重点均有官方 153 页用户指南全文 + 产品页 + Microsoft 连接器文档三重独立佐证；局限：整体套件菜单为多个独立 offering 拼合（官方无统一菜单树），Connected Solutions(2025) 细节目前仅有新闻稿层面信息，产品门户需账号无法实测。

## 定位与目标用户
霍尼韦尔面向商业地产/园区/校园/数据中心的楼宇数字化运营 SaaS 平台，定位是"跨品牌、跨系统的组合级（portfolio）楼宇运营与预测性维护层"：不替换既有 BMS，而是在其上做数据汇聚、分析规则、服务工单与能碳优化。目标用户为设施经理、多站点运营方、系统集成商（SI）与总部运营中心。2025 年 6 月又推出建于 Forge 之上的 Honeywell Connected Solutions，把软件、系统、设备收敛到单一界面。

## 功能菜单树 / 模块划分
- Honeywell Forge for Buildings（套件层，各 offering 独立订阅）
  - Performance+（运营绩效线）
    - Predictive Maintenance（预测性维护，核心应用）
    - Digitized Maintenance（数字化维保，边缘故障识别）
    - Energy Optimization（HVAC 闭环自动寻优）
    - Site Performance（现场绩效：移动端 + 集中控制中心附加包）
    - Visitor & Contractor Management（访客与承包商管理）
  - Sustainability+（能碳线：Monitor / Control / Optimize 三档套餐）
  - Connected Solutions（2025，统一界面 + 远程诊断 + 网络安全）
- Predictive Maintenance 应用内左侧导航（据官方用户指南还原）
  - Portfolio Summary（多站点组合总览，≥2 站点权限才显示）
  - Facility Summary（单站点首页：舒适度/资产绩效/工单概览/能耗 4 类 KPI widget）
  - Asset Performance / Asset Availability（按资产类型或单资产分析）
  - Comfort Performance（分区舒适度：温/湿/CO2 达标率）
  - Zones Out of Temperature Range（超温区域色块图）
  - Fault Summary（故障类型统计）
  - Rule Summary（分析规则管理：启停/编辑/批量操作）
  - Service Cases（服务工单：Kanban/列表双视图）
  - Value Impact（价值量化：能源/资产寿命/维保三维度财务化）
  - 可选仪表盘：Energy/Gas/Water Performance、Central Plant（新加坡）、NABERS（澳洲）、Space Utilization/Financial/Healthy Buildings（预留未开放）
  - Settings（工单配置/SLA/自动关单/派工组/仪表盘配置/Insight Builder）
- Forge Building Mobile（移动端）：Asset Availability、Asset 360、Comfort Performance、通知、Service Cases
- 平台配套：Partner Portal（SI 配置门户）、API Marketplace、Developer Sandbox

## 核心业务流程编排
核心是"分析规则 → 服务工单（Service Case）→ 财务价值量化"的全闭环，官方用户指南（31-00866-3 Rev3）有完整定义：1) 生成：每个资产挂分析规则（rule），规则可启用/暂停(snooze)/禁用、可编辑参数并单独控制是否生成工单，故障触发即自动生成 Service Case，并带优先级与预估故障成本(cost of fault)。2) 动态优先级：用 Normative Decision Theory 算法每 8 小时重算 Priority Score，输入分动态项（案件状态、故障最后出现时间、持续时长、复发次数）与固定项（预估成本、规则优先级、资产优先级、受影响空间优先级、父/下游资产关系、规则目标[能耗/舒适/运营]），久未复发的故障分数自动衰减，保证队列始终"最新最重要在前"。3) 处理：选中工单弹出右侧 Triage Pane，可改状态（Identified→In Progress→Done）、设优先级（Critical/High/Medium/Low）、派给用户组、写处置建议、生成分享链接（无权限者点链接也看不到）；被派组收到通知。4) SLA：按优先级分别配置 Resolution Target（应结天数）与 At Risk（风险预警天数），管理者据此考核团队，仪表盘跟踪 mean-time-to-close 与在办工单状态。5) 自动关单：故障恢复正常（Fault Last Active）超过 N 天自动关闭，自动关闭的工单从价值核算中剔除；第三方集成工单不自动关。6) 关单治理：关闭必须填标准化 Resolution Code（30+ 项：REPAIRED/REPLACED/SETPOINTS/NO_FAULT…）与 Root Cause Code（22 项：AGING/INSTALLATION/OPERATOR…），沉淀结构化维保知识。7) 外部编排：派工组可绑定第三方服务管理系统，工单生成时自动创建外部 work order，被外部拒绝的工单回流到指定"重派组"；官方 Power Automate/Logic Apps 连接器提供 webhook 订阅新工单 + 关单 API（含 workOrderID 字段），Asset Performance 还与 Dynamics 365 Field Service 打通把故障转工单。8) 价值闭环：Value Impact 框架把已处理工单折算成 realized/projected/identified/lost 四类美元价值（能耗、资产寿命、维保三个维度），并明确剔除无效关单（No Fault Found、批量关单等）——运维流程直接对账到财务结果。

## 前端形态
B 端 SaaS 深浅双主题（My Settings 内一键切换 Light/Dark，还可切换 ㎡/ft² 面积单位），左侧固定导航 + 内容区 widget 化仪表盘，无传统"查询栏+表格"后台味。代表性设计：a) 两级落地页——多站点用户先看 Portfolio Summary（站点数/总面积/组合级资产可用率），单站点落在 Facility Summary，四块 KPI widget（舒适度、资产绩效、工单增/结/积压+按优先级的在办分布、能耗对标基线）均可下钻到专属仪表盘；b) 三栏联动分析页——Fault Summary 左栏故障类型计数、点击行联动中栏受影响资产、右栏常驻相关工单列表，Comfort Performance 同样"区域表格+户外温度相关性曲线+过热/适中/过冷占比+右侧工单栏"，把"看数"与"派单"放在同屏；c) Zones Out of Temperature Range 用色块矩阵表示各区域温控状态，hover 显示当前温度/设定点/运行模式；d) 工单页 Kanban/列表双视图切换，选中即右滑 Triage 面板原地处置；e) 全局 spatial filter：有空间模型的客户可在各仪表盘按楼层/区域过滤资产与工单；f) 仪表盘可配置：Settings→Dashboard Configuration 里勾选启用哪些详情页和 widget、拖拽排序（Portfolio/Facility Summary 固定置顶），未建模数据源的仪表盘不出数并提示需要 Ops 补充建模，另有付费 Tailored Dashboard Service 做定制可视化；g) 移动端 App 提供资产可用率、Asset 360、舒适度、通知、工单五个场景，支持现场远程调设定点。公开可看：开发者沙箱 https://developer.buildings.honeywellforge.com/ 与门户 https://buildings.honeywellforge.com/（需账号），用户指南 PDF 内含大量界面截图。

## 架构与功能设计要点
闭源商业 SaaS，无公开仓库；从官方文档可见的架构/功能设计要点：1) 平台-应用分层：Forge 是 edge-to-cloud 底座（硬件无关，接 BACnet/Modbus/OPC UA/MQTT 等，不 rip-and-replace），Performance+/Sustainability+ 各 offering 是底座上可独立订阅的应用，2025 年 Connected Solutions 再加一层统一入口。2) 统一数据模型是扩展点核心：资产模型（asset modelling，含资产优先级、父子/上下游关系）、空间层级（spatial hierarchy，楼宇/楼层/区域，含空间优先级）、点位角色（point roles）——优先级算法、空间过滤、AI 预测全部消费这套模型，"先建模、后出数"是明确的产品约束（未建模的仪表盘置灰）。3) 开放接口：API Marketplace 输出归一化遥测、资产模型与 service cases；官方 Power Platform 连接器（Copilot Studio/Logic Apps/Power Apps/Power Automate）以 webhook 订阅工单 + 关单/回发事件两个 Action 对外编排，限流 100 次/60 秒；开发者沙箱供第三方试 API。4) 多租户组织：Project ID 代表客户租户，其下多 Site，站点级配置（SLA、自动关单天数可按站点分别设）；用户-权限组模型，工单读写、规则编辑、Settings 均按角色收口；面向 SI 的 Partner Portal 与面向客户的应用分离。5) 区域化功能开关：NABERS（澳）、Green Mark 中央机房效率（新加坡）等合规仪表盘按区域启用——同一产品用配置差异化。6) 流程引擎位置：没有通用审批流引擎，"流程"内嵌在 Service Case 状态机 + 可配置规则（SLA/自动关单/派工组/外部系统映射）里，复杂编排外溢给 Power Automate 等外部工具，这是"内置轻状态机 + 开放 webhook 让客户自己编排"的取舍。

## 可借鉴点 TOP5
- **[D2流程编排]** 给 zhyq 工单/IoT告警引入 PdM 式动态优先级评分：资产重要度×空间重要度×故障持续/复发次数×预估影响成本，定时重算并让久未复发的告警分数衰减，替代现在人工设的静态优先级字段
- **[D2流程编排]** 工单模块补 SLA 与自动关单：按优先级配置"应结天数+风险预警天数"两档并在列表标色统计 MTTC；设备告警恢复正常 N 天后自动关单；关单强制选标准化"解决码+根因码"码表（Honeywell 有 30+/22 项可直接参考），为报表沉淀结构化数据
- **[D3前端体验]** 把工单/告警页改成 Kanban/列表双视图 + 右侧滑出 Triage 面板（改状态、设优先级、派组、写处置意见、复制分享链接一屏完成），替代 zhyq 现在"点行→弹窗"打断式操作；分析页学 Fault Summary 三栏联动（统计→资产→相关工单同屏）
- **[D3前端体验]** 驾驶舱从写死大屏改为 widget 注册制：像 Dashboard Configuration 一样让管理员勾选/拖拽排序页面与 widget、按项目配置，数据源未接入的 widget 置灰并提示原因——正好消化 zhyq "第三方对接全模拟"的现状
- **[D4架构设计]** 把 zhyq 已有的楼宇-楼层-房源树升级为全局空间模型：给空间和设备加"优先级"属性并建立设备↔空间关系，让工单、告警、能耗、巡更各页面共用同一个空间过滤器（spatial filter），并作为优先级算法的输入

## AI 备注（一行）
PdM 的 AI 嵌入位很值得抄：工单 Triage 面板里一个 "Generate AI Insights" 按钮，基于历史工单生成复发模式、可能根因、历史解决方案及其成功率、同故障影响的其他资产——AI 内嵌在处置动作旁而非独立聊天窗。

## 来源
- https://prod-edam.honeywell.com/content/dam/honeywell-edam/hbt/en-us/documents/manuals-and-guides/user-manuals/hon-ba-hbs-honeywell-forge-for-buildings-pdm-user-guide-31-00866-3.pdf (官方PdM用户指南153页, Rev3 2025-12)
- https://buildings.honeywell.com/us/en/solutions/buildings/honeywell-forge-performance-plus-for-buildings-predictive-maintenance
- https://learn.microsoft.com/en-us/connectors/honeywellforge/ (官方Power Platform连接器文档, 含service case数据模型)
- https://www.honeywell.com/us/en/press/2023/05/honeywell-unveils-honeywell-forge-for-buildings-and-expands-sustainability-focused-applications
- https://www.honeywell.com/us/en/press/2025/06/honeywell-unveils-ai-powered-building-management-solution (Connected Solutions 2025)
- https://buildings.honeywell.com/us/en/solutions/buildings/honeywell-forge-sustainability-plus-for-buildings-carbon-and-energy-management
- https://developer.buildings.honeywellforge.com/ (Connected Buildings 开发者沙箱)
- https://www.honeywellforge.ai/content/dam/honeywellbt/en/documents/downloads/hon-HoneywellForge_ForBuildings_Brochure_042020.pdf (官方套件手册)
