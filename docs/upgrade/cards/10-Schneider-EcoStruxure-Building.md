# 对标卡片：Schneider EcoStruxure Building（核心为 EcoStruxure Building Operation, EBO）

**资料充分度**：高——告警体系与能耗流程均有官方 WebHelp 多篇文档+spec sheet+社区知识库交叉佐证；不足处：无公开在线 demo，帮助站部分页面 403 无法直读原文，Enterprise Central 与 Building Advisor 门户界面细节主要依据 spec sheet 与案例二手描述。

## 定位与目标用户
施耐德面向商业楼宇/园区/校园的开放式楼宇管理平台（BMS），以 EcoStruxure Building Operation 为核心软件套件，整合环境控制、能耗管理与安防。目标用户是楼宇业主、设施管理团队与系统集成商（EcoXpert 伙伴），从单栋楼到 2500+ 服务器的多站点企业级部署均可覆盖。上层叠加 Building Advisor（云端故障诊断服务）与 Energy Expert/PME（电能与租户计费）构成"互联产品-边缘控制-应用分析服务"三层体系。

## 功能菜单树 / 模块划分
- EcoStruxure Building 产品族
  - EcoStruxure Building Operation（BMS 核心）
    - WorkStation（厚客户端，工程+运维全功能）
      - System Tree（系统树，主导航/对象树）
      - Alarms 告警窗格 / Alarm View（可过滤自定义视图）
      - Events 事件窗格 / Event View（全量事件日志）
      - Graphics（TGML 矢量组态图，Graphics Editor）
      - Schedules / Calendars（时间表/日历）
      - Trend Logs / Trend Charts（趋势记录与图表）
      - Programs（Script 脚本 + Function Block 功能块编程）
      - Reports（含能耗报表，Reports Server/WebReports）
      - Users / Domains（账户、权限、AD 集成、Workspace 定义）
      - Interfaces（BACnet / LonWorks / Modbus / EWS 接口对象）
      - Semantic Model（Brick Schema 数字孪生建模工具）
    - WebStation（浏览器端，日常运维）
      - Dashboards（可配置仪表盘：公共/个人两级）+ Slide Show 轮播
      - Graphics / Alarms / Schedules / Trend Logs / 用户管理
    - 服务器层级：Enterprise Central → Enterprise Server → Automation Server(AS-P)/Edge Server
  - EcoStruxure Building Advisor（云服务）
    - Asset Health / BMS Health / Alarm Health 三类健康报告
    - 自动故障检测与诊断 FDD、任务分派与 CMMS 工单联动
  - EcoStruxure Energy Expert（PME 嵌入 EBO）
    - 电能 Dashboards / Reports / Diagrams
    - Energy Billing Module（租户计费、影子账单、费率/TOU）
  - SmartConnector 框架（第三方系统集成扩展，Processor 插件）

## 核心业务流程编排
【告警全生命周期】触发→进入告警列表（按过滤器分发到不同 Alarm View）→自动分派（可按过滤条件自动 assign 给最合适的用户/组，分派状态全员可见）→确认 acknowledge（表示"已看到并负责"）→复位→恢复 Normal。确认策略按告警对象可配三档：无需确认/单次确认/扩展确认（必须在 reset 态确认才归零），每次状态迁移写入事件日志。优先级 0-999 数值化，且同一告警在触发态与复位态可配不同优先级，引导值班员处理顺序。可强制"用户操作"（处理时必须填写原因/措施，可设为必填），告警可挂附件（指向系统对象的链接）。通知机制：同一套告警过滤器同时驱动弹窗、Email、SNMP、写文件通知与 Sum Alarm（聚合告警——把满足过滤条件的一批告警聚合成一个上层告警，用于点亮警灯或跨服务器汇总，Enterprise Server 上的 Sum Alarm 可容纳下级各 AS 的告警）。云端延伸：Building Advisor 出具 Alarm Health Report 分析告警行为质量，FDD 发现的故障生成任务、分派团队成员并对接客户 CMMS 工单系统（爱荷华大学案例：分析响应小组按 FDD 优先级派单，首年省 90 万美元）。【能耗管理流程】表计经 Modbus/BACnet 接入→为每个表计关联扩展趋势日志采数→在专用 Energy 文件夹中建"站点-表计"层级树并打标签（tag 支持按区域+用途双维分类）→报表引擎按层级自动汇总同类能源并统一单位（电 kWh、气 m3），输出日历消耗报表、分项消耗报表→高级功能：归一化（用于对标）与"能耗签名"（回归分析+工作日/节假日时段分类建基线，用量异常高/低自动告警，可早期发现设备停摆）。租户计费走 Energy Expert/PME 的 Energy Billing Module：层级+费率文件+TOU 时段电价→出租户账单、影子账单核对电费、导出财务系统。

## 前端形态
双端形态：WorkStation（Windows 厚客户端，工程/组态/运维全功能）+ WebStation（每台服务器内置的纯浏览器端，无插件，PC/Mac/平板/手机通用）。设计体系核心是"可组合工作区"（Workspace）：面板式界面，用户可自由选摆 Alarms、Graphics、编辑器等组件并保存多套工作区，管理员可按账户下发默认工作区——即"按角色定制的桌面"而非固定导航。导航范式以 System Tree 对象树为主轴，对象拖拽即可生成告警/趋势；每类对象（告警、图形、趋势、时间表、日历、Watch 等）有专属视图，无专属视图的对象直接弹属性框。组态图采用 TGML 可缩放矢量图形，"一次绘制、任意分辨率复用"，两端共用同一图形格式；图形上支持超动态实时刷新与点击直控（改设定值、启停设备、调阀门）。大屏/驾驶舱形态：WebStation Dashboard 由 widget 拼装（实时+历史数据、告警/事件统计交互组件），分公共/个人两级（个人仪表盘需授权），并有 Slide Show 幻灯片模式自动轮播多个仪表盘供公共显示屏使用。告警界面支持颜色编码、分组、过滤、多选批量确认，移动端 Technician Tool 可现场确认告警。无完全公开的在线 demo，官方帮助站（ecostruxure-building-help.se.com）有全量界面级操作文档；架构交互图见 Alpha Controls 发布的 Interactive EBO Architecture PDF（alphaacs.com）。

## 架构与功能设计要点
闭源商业产品，无公开仓库。宏观为 EcoStruxure 三层架构：Connected Products（表计/传感器/SmartX IP 控制器）→ Edge Control（EBO 服务器族）→ Apps, Analytics & Services（Building Advisor、Energy Expert 等云端应用）。模块边界体现在服务器分级：Automation Server/AS-P 或纯软件 Edge Server 做现场控制逻辑、趋势记录、告警监督（每台都是完整"BMS 服务器"，自带 WebStation），Enterprise Server 汇聚全站数据并作为统一管理入口，Enterprise Central 再向上聚合 200~2500+ 台服务器支撑多站点企业——同构对象模型逐级上卷，Sum Alarm、报表都天然跨级聚合。开放接口三条路：(1) 原生开放协议 BACnet（B-BC+B-OWS 认证）/LonWorks/Modbus；(2) EcoStruxure Web Services（EWS，内置 Web 服务协议，REST 消费，按 Consume/Serve+Consume/含历史趋势三档许可）；(3) SmartConnector 扩展框架——通过可插拔 Processor 把第三方系统（SOAP/REST/JSON 均可）映射为 EWS 接口对象，在 EBO 中"New > Interface"注册后即可把外部点位、告警当本系统对象托管。业务逻辑扩展点是双引擎编程：Script 脚本 + Function Block 功能块（支持层级化封装块、程序库导入导出、内置调试器单步仿真、在线看运行值），时间表/日历对象承担排程自动化。语义层用 Brick Schema 建楼宇数字孪生，为告警和点位提供空间/设备上下文。多租户组织：按用户账户+域划分权限，可绑 AD、按时间段和客户端 IP 限制访问，Workspace 按账户定制；租户级能耗隔离靠 Energy/PME 的层级树（tenant-device 层级+虚拟表计）实现。

## 可借鉴点 TOP5
- **[D2流程编排]** 借鉴 EBO 告警状态机：给 zhyq IoT 告警加"触发→自动分派到人/组→确认(认领)→复位→关闭"生命周期，确认策略按告警类型可配（无需确认/需确认/复位后确认），处理时强制填写"用户操作"备注，替代目前的简单状态字段
- **[D1功能分类]** 把"告警过滤器"抽象为可复用对象：同一条过滤规则（按优先级/类别/来源，支持通配符）同时驱动告警视图、消息通知（站内/邮件/短信）、聚合告警(Sum Alarm)和自动派单，zhyq 只需一张 alarm_filter 表即可统一告警视图配置与通知路由
- **[D2流程编排]** 能耗模块建"园区-楼-层-租户-表计"层级树并给表计打区域+用途双维标签，报表按层级自动上卷汇总；再加"能耗签名"基线（区分工作日/节假日的历史均值），超出阈值自动生成能耗异常告警并联动物业工单——把 zhyq 现有能耗、告警、工单三个孤立模块串成闭环
- **[D3前端体验]** 把 /screen 深色大屏改造为 widget 化可配置仪表盘：内置能耗、告警统计、环境概览等组件，支持公共/个人两级保存 + 幻灯片轮播模式（多屏自动切换），管理员可按角色下发默认布局，替代写死的单页大屏
- **[D4架构设计]** 仿 EWS/SmartConnector 设计统一"集成接口对象"层：第三方对接（电表、门禁、停车等）不再各写模拟代码，而是注册 Interface 记录（协议/URL/凭据），经适配器把外部点位和告警映射为 zhyq 内部统一的设备/告警模型，真实对接时只换适配器实现

## AI 备注（一行）
Building Advisor 是现成的 AI/分析嵌入位样板：BMS 数据脱敏上云做自动故障检测诊断(FDD)，输出带优先级的任务清单并联动 CMMS 工单——zhyq 可在能耗+设备告警之上做同款\"异常诊断→建议→一键转工单\"。

## 来源
- https://ecostruxure-building-help.se.com/bms/topics/show.castle?id=7939&locale=en-US&productversion=7.0
- https://iportal.se.com/Contents/docs/Enterprise%20Server%20-%20EcoStruxure%20Building%20Operation%20Specification%20Sheet.pdf
- https://ecostruxure-building-help.se.com/bms/topics/show.castle?id=4299&productversion=2024&locale=en-US
- https://ecostruxure-building-help.se.com/bms/topics/show.castle?id=4305&locale=en-US&productversion=2024
- https://community.se.com/t5/Building-Automation-Knowledge/Sum-Alarm-in-EcoStruxure-Building-Operation/ta-p/3110
- https://ecostruxure-building-help.se.com/bms/topics/show.castle?id=8216&locale=en-US&productversion=3.0
- https://ecostruxure-building-help.se.com/bms/Topics/show.castle?id=12436&productversion=7.1&locale=en-US
- https://ecostruxure-building-help.se.com/ba/Topics/show.castle?id=13413&locale=en-US&productversion=7.0
- https://betterbuildingssolutioncenter.energy.gov/showcase-projects/schneider-electric-ecostruxure-building-advisor-using-data-analytics-early
- https://product-help.se.com/docs/EcoStruxure/Power-Monitoring-Expert-2024/content/6_operating/modules/energybilling.htm
- https://www.alphaacs.com/images/documents/Interactive_EBO_Architecture_V2.pdf
- https://www.prnewswire.com/news-releases/schneider-electric-announces-next-generation-of-ecostruxure-building-the-open-ip-architecture-for-iot-devices-in-buildings-300688885.html
- https://ecostruxure-building-help.se.com/bms/topics/show.castle?id=8064&locale=en-US&productversion=2022
