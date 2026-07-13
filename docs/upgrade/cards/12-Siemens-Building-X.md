# 对标卡片：Siemens Building X

**资料充分度**：中高：功能清单、API 体系、架构分层、规则/工单编排均有官方产品页+数据表+开发者门户多源印证；短板是无公开交互 demo，前端体验依赖官方截图与发布说明描述，且"Building X 前端基于 iX 设计系统"为合理推断而非官方明文。

## 定位与目标用户
西门子智能基础设施旗下的云原生智慧楼宇 SaaS 套件，是 Siemens Xcelerator 体系首个按其设计原则打造的产品。定位为楼宇数字化的"单一数据底座 + 用例化应用商店"：把楼宇自控、能源、消防、安防等系统数据汇入统一云端数据池，再以订阅制模块化应用（Energy/Operations/Security Manager 等）和订阅制开放 API 消费这些数据。目标用户为楼宇业主、地产/资管公司、设施管理者及第三方应用开发者，主打脱碳（净零楼宇）与远程运维两大价值。

## 功能菜单树 / 模块划分
- 平台入口（统一登录 + App Launcher，所有应用共享同一数据仓库）
  - Energy Manager（能源管理：能耗/成本/CO2 追踪、历史对比、能耗预测，ISO 50001 认证）
  - Sustainability Manager（可持续管理：合规目标追踪、AI 发票数据提取、预算规划 actual vs plan、可再生能源占比、废弃物+CO2e 核算）
  - Operations Manager（远程运维：多站点总览、事件/告警总览、实时点位监控与远程指令、FDD 故障检测诊断、交互式楼层平面图/3D 房间视图、自定义仪表板、工单集成）
  - Security Manager（安防/门禁：门禁远程开门与时间计划、身份与权限集中管理、手机开门/Apple Wallet 凭证、复用 SiPass/SIPORT 存量硬件）
  - Fire Manager（消防：消防主机接入、报警事件近实时推送）
  - Occupancy Manager（空间占用：桌位级占用追踪、占用预测、AI Eco/Comfort 排程建议）
  - Comfort AI（HVAC 的 AI 自动调优，舒适度约束下节能）
  - Data Visualizer（跨域 KPI 仪表板搭建器，可独立订阅，产出被 Operations Manager 引用）
  - Rules（低代码规则引擎：可视化 flow editor、规则库、定时调度、结果供 API/其他应用消费）
  - 360° Viewer（实景数字孪生浏览，集成 NavVis）
  - Lifecycle Twin（资产全生命周期孪生：组合/资产/任务/文档）
- API Manager（API 产品目录、Machine User 管理、配额与文档）
  - API 产品：Operations 运维 / Point Value Ingest 数据回灌 / Energy 能源 / Structure 空间结构 / Security 身份权限 / Security 告警事件 / Fire 消防 / Lifecycle Twin
- 平台管理：Partition 分区、User Group 用户组、设备接入与远程设备管理

## 核心业务流程编排
1) 告警→诊断→工单闭环（核心流程）：边缘网关把设备/消防/安防事件送入云端 → Operations Manager 的 Event Overview 汇总单楼或多楼宇 pending 事件 → FDD 引擎持续对设备参数跑规则（内置 400+ 条基于 ASHRAE 标准的预置规则，覆盖空调/供热/冷冻水/计量），支持故障分诊、记录发现、管理故障状态 → 与 Brightly AE 等 CMMS 集成，事件触发时自动创建工单（或分析后手动创建），并根据工单状态反向驱动事件生命周期。这是"检测-诊断-派单-闭环"的完整编排，zhyq 的 IoT 告警与物业工单目前是两个孤立模块。
2) 通知规则编排：用户自定义通知规则（事件类型/FDD 故障 + 范围 + 接收个人或群组），经 email/SMS 分发；通知策略是用户可配置的数据，而非写死的代码。
3) Rules 低代码自动化：可视化 flow editor 编排"数据条件→分析→动作/建议"，支持定时循环执行，规则可入分区库跨项目复用，结果可被 Data Visualizer 展示或经 API 提取——相当于平台级自动化中枢，官方口径"数小时而非数周交付定制方案"。
4) 数据一次接入、多域复用：onboard 一次（打标、挂到空间结构树），能源/运维/安防/可持续应用共享同一数据池，避免每个模块各自建模。
5) 远程控制回路：Operations Manager 中可命令点位（设定值、阀门开关）直接下发到自动化层，形成云端到现场的双向流程。注意：平台没有传统 OA 式审批流引擎，编排以"事件/规则驱动自动化"为主，这与 zhyq 缺审批流是不同的补法。

## 前端形态
形态为现代多应用 SaaS：统一登录 + App Launcher，进入各订阅应用；"single pane of glass"按用户角色和工作流切分而非塞进一个巨型后台。代表性交互（据官方数据表/发布说明截图）：(a) Operations Manager 的空间层级树（building→floor→room→equipment→data point）作为主导航范式，配交互式楼层平面图——平面图上直接显示实时房间/设备数据，悬浮出详情卡，设备图形可挂指令、趋势视图等操作工具，另有 3D 房间视图模式；(b) 多站点总览页，聚合每个站点的连接状态与事件状态；(c) 仪表板不是硬编码大屏，而是由 Data Visualizer 这个独立"仪表板搭建器"应用配置，再嵌回 Operations Manager 使用；(d) Rules 的可视化 flow editor（节点连线式低代码）。设计体系方面，Siemens 开源了工业设计系统 iX（github.com/siemens/ix，MIT 协议，Web Components + React/Angular/Vue/Blazor 封装，500+ 工业图标，Figma 库，默认深色工业风），是 Siemens Xcelerator 产品线的统一 UI 层；Building X 界面风格与之一致（深色导航 + 卡片/图表），但官方未明文声明 Building X 由 iX 构建，此点为推断。未发现公开可注册的交互 demo；开发者可申请 API 沙箱（developer.siemens.com/building-x-openness），UI 截图见各应用 data sheet PDF。

## 架构与功能设计要点
三层架构（官方口径）：Connect 层——连接基座，厂商无关网关/边缘设备（如 Connect Box：预解码 150+ 厂商设备库、11 种协议、本地 BACnet IP/Modbus TCP/MQTT 输出、边缘预处理后上云）把数据送入云端数据湖；Digitalize 层——数据聚合分析、设备管理、开放 API 生成；Act 层——用例化应用与服务。云端整体托管于 AWS（爱尔兰区），部署由 Siemens 完成，客户纯 SaaS 订阅。模块边界 = 应用订阅边界：每个应用独立订阅（Operations Manager 按 100 点位包计价），共享中央数据仓库，可从单应用起步按需扩展；Data Visualizer、Rules 等横向能力也做成独立应用而非某应用内功能。开放接口是一等公民：API 与应用同级作为可售产品（8 类 API 产品，含配额制 600 万次/年等计量），基于 JSON:API 规范 + OpenAPI 文档 + 沙箱试用 + 3 个月弃用预告期 + 全链路 X-Request-Id；认证用 Machine User + OAuth2 Client Credentials；另有 Mendix 低代码 Connector（封装 Building X 核心实体域模型）和认证伙伴生态（伙伴可开发并销售应用）。多租户组织方式：Partition（分区）作为数据逻辑隔离单元，通过 User Group 授权访问，Machine User 绑定到特定分区与用户组（每分区上限 200 个 machine user）。扩展点/流程引擎位置：FDD 规则库与通知规则内嵌在 Operations Manager；跨域自动化由 Rules 应用（低代码 flow editor + 规则库）承担；工单流程外置给 Brightly AE 等 CMMS 通过集成完成——平台自身不做重型审批流。非开源，无公开仓库目录结构。

## 可借鉴点 TOP5
- **[D2流程编排]** 照搬"事件→FDD规则→自动建工单→工单状态回写关闭事件"闭环：在 zhyq 的 IoT 告警模块加一张可配置规则表（设备类型+条件+持续时长→自动生成物业工单并指派），工单完成后自动消警，打通目前孤立的告警与工单两模块
- **[D4架构设计]** 把模拟的第三方对接改造成 Building X 式"API 产品"：机器账号（client_credentials）+ 按园区/租户分区授权 + OpenAPI 文档页 + 全链路 requestId，先把账单、工单、能耗三个域各出一组只读 API 即可起步
- **[D3前端体验]** 借鉴 Operations Manager 的空间层级树+交互式平面图：zhyq 用统一的"楼宇→楼层→房间→设备"树作为楼宇、租控、工单、告警的公共导航，楼层平面图上叠加房态色块与设备告警点，悬浮卡直达合同/工单详情
- **[D2流程编排]** 建统一"通知规则中心"：一张用户可配置的规则表（事件类型：告警/工单超时/账单逾期/合同到期 + 范围 + 接收人或群组 + 渠道），替代各模块各写一套通知代码，即 Operations Manager 通知规则的做法
- **[D1功能分类]** 仪表板搭建器独立成模块（对标 Data Visualizer）：把 /screen 大屏从写死页面改为"卡片配置化"——指标卡/图表组件绑定数据源后可自由编排，同一套配置同时供驾驶舱大屏和各业务模块首页复用

## AI 备注（一行）
AI 嵌入位集中在"建议而非页面"：Comfort AI 自动调 HVAC、Sustainability Manager 的发票字段 AI 提取、Occupancy Manager 基于占用预测给出两周 Eco/Comfort 排程建议卡——均嵌在既有业务流内而非独立 AI 入口。

## 来源
- https://www.siemens.com/en-us/products/building-x/
- https://www.siemens.com/en-us/products/building-x/applications/
- https://www.siemens.com/en-us/products/building-x/apis/
- https://developer.siemens.com/building-x-openness/overview.html
- https://developer.siemens.com/building-x-openness/dev-guide/gettingstarted.html
- https://www.siemens.com/en-us/products/building-x/applications/rules/
- https://www.siemens.com/buildingx/data-sheet/operations-manager
- https://www.siemens.com/en-us/products/building-x/release-notes/
- https://www.siemens.com/en-us/products/building-x/buying-guide/
- https://github.com/siemens/ix
- https://www.siemens.com/en-us/products/desigo/connect-box/
- https://news.siemens.com/en-us/siemens-presents-building-x-an-ai-based-solution-for-transforming-buildings-into-climate-neutral-buildings/
