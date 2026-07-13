# 对标卡片：ThingsBoard（开源 IoT 平台，Community Edition）

**资料充分度**：高——官方文档站与 GitHub 仓库内容互相印证，告警模型/告警规则/规则链/仪表盘四块均取自官方文档原文；menuTree 依据官方文档模块划分 + 社区 Vue3 复刻项目目录双源交叉，个别菜单项归属层级可能随 4.x 版本微调；demo 站预置仪表盘清单未逐一实测。

## 定位与目标用户
ThingsBoard 是全球最主流的开源 IoT 平台之一（GitHub 22k star），定位为"设备管理 + 数据采集 + 处理 + 可视化"一体化平台，强调私有化部署、避免云厂商锁定和"Developer-First 低代码"。目标用户从初创试点（单体部署）到百万级设备企业（同一代码库切微服务），以及有合规/私有化诉求的组织。商业模式为开源 CE + 付费 PE（集成、白标、报表、调度器）+ Cloud PaaS。对 zhyq 而言，它是 IoT 设备-遥测-告警功能模型和 IoT 仪表盘前端的最直接对标物。

## 功能菜单树 / 模块划分
- 首页 Home
- 告警 Alarms
  - 告警列表（按状态/级别/时间过滤、确认/清除/受理人/评论）
  - 告警规则 Alarm rules（挂在 Device/Asset Profile 或单实体上）
- 仪表盘 Dashboards（多布局、状态钻取、公开链接、客户分配）
- 实体 Entities
  - 设备 Devices（凭证、连接状态、遥测/属性/告警/事件标签页）
  - 资产 Assets（楼宇/区域等抽象分组）
  - 实体视图 Entity Views（设备的只读投影）
  - 网关 Gateways（Modbus/BACnet/OPC-UA 等遗留协议接入）
- 配置文件 Profiles
  - 设备配置 Device profiles（传输协议、默认规则链/队列、告警规则）
  - 资产配置 Asset profiles
- 客户 Customers（多租户：Tenant → Customer → 用户）
- 规则链 Rule chains（可视化规则引擎编排）
- 边缘管理 Edge instances
- 高级功能 Advanced features
  - OTA 升级（固件/软件包管理）
  - 版本控制 Version control（Git 同步仪表盘/规则链）
  - 计算字段 Calculated fields
- 资源库 Resources（Widget 库、图片库、SCADA 符号库、JS 库）
- 通知中心 Notification center（规则/模板/接收人/发送记录）
- API 使用量 API usage、审计日志 Audit logs
- 系统设置 Settings（邮件/短信通道、安全、OAuth2、白标[PE]）

## 核心业务流程编排
核心编排围绕"消息 → 规则链 → 动作"展开：1) 遥测/属性更新/设备上下线/RPC 全部转为不可变消息（含 Originator、类型、JSON 载荷、元数据），进入租户唯一的根规则链（Root Rule Chain）；Device Profile 可覆盖路由到专属规则链，实现按设备类型隔离处理逻辑。2) 规则链是有向图，节点分 Filter/Enrichment/Transformation/Action/External/Flow 六七大类，节点间用关系标签（Success/Failure/True/False/自定义）决定流向；Create Alarm / Clear Alarm 是规则链中的标准动作节点，还可接 Send Email、REST 调用、RPC 下发形成"遥测→判断→告警→通知→反控"闭环。3) 告警生命周期：告警由 originator+type 唯一，五级 severity（Critical→Indeterminate），双维状态 active/cleared × acknowledged/unacknowledged 组合四态；同一告警条件再触发时升级 severity 而非新建重复告警；清除不删除，保留历史可查；支持受理人（Assignee）指派与告警评论（用户评论+系统自动评论），形成轻量处置闭环。4) 告警规则声明式配置：触发条件支持 Simple/Duration（持续 N 分钟）/Repeating（连续 N 次）三种去抖类型，支持时间表（如仅工作日 9:00-18:00 生效）和动态阈值（用另一实体 attribute 做每设备阈值）；可配自动清除条件。5) 告警传播（Propagation）：沿实体关系链上卷（设备→楼层→楼宇均可见），或直传 owner/租户。6) 告警产生/清除触发通知中心，经 Web/Email/SMS/Slack/Teams 多通道分发。注意：平台没有工单/审批流概念，处置只到"确认/清除/指派/评论"为止。

## 前端形态
前端为独立 Angular 单页应用（仓库 ui-ngx 模块，Angular + Angular Material 设计体系，TS 占仓库代码 25.9%），左侧折叠树导航 + 内容区，实体管理页为"表格 + 右侧滑出详情抽屉（多标签页：详情/属性/遥测/告警/事件/关系）"，与 zhyq 的三段式后台不同。核心亮点是仪表盘体系：24 列可配置网格布局，进入 Edit mode 后拖拽/缩放 widget 所见即所得；内置 300+ widget（告警表格/告警计数、模拟仪表、开关按钮、折线/柱状/时序图、地图轨迹、HTML/Markdown 卡片、视频流），并有整套 SCADA 符号库（管道、阀门、储罐、泵）做工业组态图。仪表盘支持：多布局（含独立移动端布局）、Dashboard States 分层钻取（根状态列设备清单 → 点击行跳转设备详情状态，状态名支持 ${entityName} 动态标题）、实体别名 Alias（静态/动态解析数据源，一套仪表盘模板复用到任意设备集合）、仪表盘级时间窗与过滤器、自定义 CSS/背景/Logo、JSON 导入导出与 Git 版本控制、公开链接免登录分享、分配给客户后只读。规则链编辑器为节点拖拽连线画布，脚本节点带 Test 调试按钮、节点级 Debug 模式。公开演示：https://demo.thingsboard.io（注册即用），官网 https://thingsboard.io/dashboards/ 有大量截图与在线交互示例；另有开源 Flutter 移动端。

## 架构与功能设计要点
开源协议 Apache-2.0（GitHub LICENSE 文件确认），Java + Maven 单仓多模块（monorepo），前后端同仓。顶层模块组织：application（主应用装配）、common（公共模型/消息）、dao（数据访问层）、rule-engine（规则引擎及全部规则节点实现）、transport（MQTT/HTTP/CoAP/LwM2M/SNMP 接入层，协议无关归一化）、ui-ngx（Angular 前端）、msa（微服务部署形态）、edqs（实体数据查询服务）、rest-client（Java REST SDK）、netty-mqtt、monitoring、docker、tools、packaging。功能设计要点：a) 领域模型以"实体+关系"为核心——Tenant→Customer→Asset→Device 层级，实体统一携带三类数据（Attributes 键值、Time-series 时序、Relations 有向关系），告警/仪表盘/规则链都是一等实体；b) Device Profile / Asset Profile 是关键抽象层：一类设备共享传输配置、默认规则链、队列和告警规则，避免逐设备配置；c) 扩展点清晰：自定义 widget（资源库内开发）、自定义规则节点（插件式）、自定义传输协议、REST/WebSocket API，Git 版本控制支持配置即代码；d) 多租户原生内建，租户间数据/规则链/仪表盘完全隔离，Customer 层再做二级隔离（客户用户只读被分配的仪表盘）；e) 单体与微服务同一代码库、API 不变，按规模平滑切换。生态子项目独立仓库：IoT Gateway（Python）、Edge、TBMQ、Trendz、Flutter App。

## 可借鉴点 TOP5
- **[D4架构设计]** 照搬其告警领域模型改造 zhyq IoT 告警表：originator+type 唯一约束（重复触发升级 severity 而非刷重复记录）、五级 severity、active/cleared × ack/unack 双维四态、受理人 + 评论字段，清除不删除保留历史报表
- **[D4架构设计]** 引入'设备配置文件（Device Profile）'抽象：告警阈值规则挂在设备类型上而非单设备上，支持 Duration/Repeating 去抖、生效时间表（如仅工作时段告警）、以及从设备属性读动态阈值——zhyq 现有 IoT 告警可直接按此三件套升级
- **[D2流程编排]** 借鉴规则链'条件节点→动作节点'的图式编排（Create Alarm/Clear Alarm/发通知/调 REST 均为节点），在 zhyq 落一个轻量触发器引擎：能耗超标→生成告警→自动派物业工单→站内信/短信，同时补上审批流空白的自动化部分
- **[D3前端体验]** 把 /screen 大屏从静态页改造为'Dashboard States + 实体别名'模式：根状态显示园区/楼宇总览，点击卡片钻取到楼宇态、再钻到设备态，同一套模板通过别名绑定不同楼宇数据源复用，标题用 ${entityName} 动态渲染
- **[D1功能分类]** 用'资产（楼宇/楼层）contains 设备'的关系模型 + 告警向上传播，让 IoT 告警自动上卷聚合到楼宇/园区级，数据驾驶舱按楼宇展示告警计数与最高级别；同时提供仪表盘公开链接（免登录只读）用于园区展厅/招商展示

## AI 备注（一行）
官方推出"AI Solution Creator"（宣称 10 分钟生成可用 IoT 原型），嵌入位在"按描述自动生成设备模型+仪表盘+规则配置"而非对话问答；另规则引擎可挂 AI 异常检测节点——zhyq 可参考"AI 生成配置"这一嵌入位。

## 来源
- https://github.com/thingsboard/thingsboard
- https://thingsboard.io/docs/getting-started-guides/what-is-thingsboard/
- https://thingsboard.io/docs/user-guide/dashboards/
- https://thingsboard.io/docs/user-guide/alarms/
- https://thingsboard.io/docs/user-guide/alarm-rules/
- https://thingsboard.io/docs/user-guide/rule-engine-2-0/re-getting-started/
- https://thingsboard.io/docs/user-guide/entities-and-relations/
- https://demo.thingsboard.io
- https://github.com/oliver225/thingsboard-ui-vue3
