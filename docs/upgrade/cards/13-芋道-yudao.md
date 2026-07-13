# 对标卡片：芋道 yudao（ruoyi-vue-pro）

**资料充分度**：高——GitHub README、官方文档站（功能列表/项目结构/BPM 各专章）、前端仓库、GoView 仓库多源交叉印证；唯一保留：演示站仅 GET 只读，未实际走通一次完整审批流，BPM 细节以官方文档为准。

## 定位与目标用户
RuoYi-Vue 的全面重构增强版（Pro），Java 生态最流行的开源快速开发平台之一（GitHub 38k+ Star），基于 Spring Boot + MyBatis Plus + Vue3 & Element Plus，提供后台管理系统 + uni-app 移动端。目标用户是需要快速搭建企业级中后台/SaaS 业务系统的开发团队：内置 RBAC/数据权限、SaaS 多租户、Flowable 工作流，并附带商城/CRM/ERP/MES/WMS/IM/AI/IoT 等可插拔业务系统。MIT 协议，作者声明永无商业版，个人与企业可 100% 免费商用、无需保留版权信息。

## 功能菜单树 / 模块划分
- 系统管理
  - 用户/角色/菜单/部门/岗位、在线用户
  - 租户管理、租户套餐（SaaS 多租户）
  - 字典、短信、邮件、站内信、通知公告
  - 操作日志、登录日志、错误码、敏感词、SSO 应用管理、地区管理
- 基础设施
  - 代码生成（Java/Vue/SQL/单测）、系统接口 Swagger、数据库文档
  - 表单构建、配置管理、文件服务、WebSocket、API 日志
  - 定时任务、消息队列、MySQL/Redis/Java 监控、链路追踪
- 工作流程（BPM，Flowable）
  - 流程模型（SIMPLE 仿钉钉设计器 / BPMN 设计器）、流程表单、流程分类
  - 审批中心：发起流程、我的流程、待办/已办任务、抄送我的
- 支付系统：应用信息、支付订单、退款订单、回调通知
- 数据报表：报表设计器（集成积木报表）、大屏设计器（GoView）
- 会员中心：会员管理、标签、等级、分组、积分签到
- 商城系统 Mall
  - 商品（分类/属性/SPU-SKU/评价）
  - 交易（购物车/订单/售后/发货/自提/分销返佣）
  - 营销（优惠券/秒杀/拼团/砍价/满减/积分商城/商城装修）
- CRM：线索、客户（公海）、商机、合同、回款、产品、跟进记录
- ERP：产品、库存（出入库/调拨/盘点）、采购、销售、财务收付款
- WMS：仓库、商品、入库/出库/移库/盘库、库存流水
- MES：工序工艺、生产工单、排产报工、质检（IQC/OQC）、设备点检
- 微信公众号：账号、粉丝、消息、菜单、素材、自动回复
- IM 即时通讯：好友、群聊、消息、音视频通话
- AI 大模型：对话、绘画、知识库 RAG、AI 工作流、MCP
- IoT 物联网：产品/设备、物模型、协议接入、场景联动、告警、OTA

## 核心业务流程编排
BPM 是全项目的流程编排中枢（yudao-module-bpm，基于 Flowable 6.8）。核心机制：1）双设计器——SIMPLE 仿钉钉/飞书设计器（拖拽 JSON 定义，后端 SimpleModelUtils 转成 BPMN XML 存入引擎）覆盖 80% 审批场景，复杂场景切 BPMN 设计器，两者同源；2）节点体系——发起人/审批人/办理人（不审批只执行，如财务打款、盖章）/抄送人四类操作节点，条件/并行/包容/路由四类分支（分别映射排它、恒真包容、包容网关，路由分支可直接跳转指定节点），加子流程（callActivity，同步/异步）、延迟器（定时事件）、触发器（receiveTask，可执行 HTTP 请求/回调、更新/删除表单数据）；3）审批能力——会签/或签/依次审批、驳回、转办、委派、加签/减签、撤销/终止、超时审批与自动提醒、表单字段级权限（只读/编辑/隐藏）；4）审批人分配支持指定用户/角色/部门/岗位、发起人自选、发起人主管（多级）、表单字段、流程表达式；节点监听器采用类 Postman 的 HTTP 请求配置，前置/后置通知外部系统。业务集成模式（对 zhyq 最关键）：区分"流程表单"（在线拖拽、数据存引擎内，适合简单审批）与"业务表单"（独立业务表，审批只是业务一环，官方推荐）。业务表单接入四步：业务表加 process_instance_id + status 字段；Service 定义 PROCESS_KEY 调 BpmProcessInstanceApi#createProcessInstance 发起（API 屏蔽底层引擎）；流程模型里配置该业务的提交/查看 Vue 路由；继承 BpmProcessInstanceStatusEventListener 监听最终通过/不通过/取消，回调更新业务状态并触发后续动作。CRM 合同审批、回款审批、OA 请假均按此模式接入，形成"业务发起 → 流程引擎审批 → 事件回调驱动业务落库"的标准联动范式。

## 前端形态
官方并行维护多套前端（均 MIT）：主推 yudao-ui-admin-vue3（Vue 3.3 + Element Plus 2.4 + Vite + TS + Pinia + UnoCSS，基于 vue-element-plus-admin 二次开发），另有 Vue3 + vben5（ant-design-vue）版、Vue2 + element-ui 版、uni-app 移动端管理后台与商城小程序、GoView 大屏。设计体系是典型 Element Plus 后台风：左侧多级菜单 + 顶栏 + 多标签页 tab，动态路由由后端菜单管理生成，支持权限指令、主题配置、国际化、暗黑模式；列表页为"查询栏 + 表格 + 弹窗表单"三段式，与 zhyq 现有形态同源，迁移认知成本低。特色交互：1）流程设计器——SIMPLE 设计器为钉钉式纵向节点卡片流（点击节点右侧抽屉配属性），BPMN 设计器为 bpmn.js 画布，是整个前端体验的亮点；2）审批中心统一聚合待办/已办/抄送，审批详情页通过 Vue3 异步组件按流程模型配置的路由动态加载业务表单组件；3）大屏走 GoView 低代码路线：Vue3 + NaiveUI + ECharts5 的拖拽式可视化设计平台，管理后台以 iFrame 嵌入（URL 携带 accessToken/refreshToken 透传登录态），运营可自行拖拽搭大屏而非硬编码；报表则 iFrame 集成积木报表 JimuReport（注意其为 LGPL/商业双授权，非 MIT）。公开 demo：Vue3+EP http://dashboard-vue3.yudao.iocoder.cn ，vben 版 http://dashboard-vben.yudao.iocoder.cn ，Vue2 版 http://dashboard.yudao.iocoder.cn（演示环境仅允许 GET）。

## 架构与功能设计要点
License：MIT（README 明示比 Apache 2.0 更宽松，可去除版权信息商用；但 BPM 的初始化 SQL 与集成的积木报表有各自授权限制需注意）。总体是"单体 + 强模块化"的 Maven 多模块架构，顶层目录：yudao-dependencies（依赖版本 BOM）、yudao-framework（框架封装，每个子 Module 是一个 starter 组件，分技术组件如 mybatis/redis/security/web，和业务组件如数据字典、操作日志、多租户，业务组件名含 biz）、yudao-server（空壳启动容器，按需引入业务模块聚合成 RESTful 服务）、yudao-module-*（业务模块：system/infra/member/bpm/pay/mall/erp/crm/wms/mes/im/ai/iot/mp/report）、sql、script。模块边界设计是最大亮点：每个业务模块拆 yudao-module-xxx-api 与 yudao-module-xxx-biz 两个子模块，模块间只允许依赖对方 api 模块（接口 + DTO），既明确契约又解决 Maven 循环依赖（如 trade 与 promotion 互依赖时抽 trade-api）；biz 内部按 controller.admin / controller.app（管理端与用户端 API 物理分包，VO 也分两套，DO 不出 Controller）/ service / dal.dataobject / dal.mysql 分层。可插拔机制：bpm、report、mall 等重模块默认在 pom.xml 中注释关闭，取消注释 + 导入对应 SQL 即启用，精简版 yudao-boot-mini 只保留 system + infra。扩展点：官方"新建模块"文档给出五步新增 yudao-module-demo 的标准流程；BpmProcessInstanceApi、PayClient 等 api 层即跨模块开放接口；同一套模块可平移到 yudao-cloud 微服务版。多租户为 SaaS 字段级隔离（tenant_id 自动拼接）+ 租户套餐控制菜单可见性，由 framework 层业务组件透明实现，业务代码基本无感。

## 可借鉴点 TOP5
- **[D2流程编排]** 直接引入 yudao-module-bpm（Flowable+双设计器）补 zhyq 审批流空白：合同审批按其"业务表单"模式接入——合同表加 process_instance_id/status 字段，审批经 BpmProcessInstanceApi 发起，继承 BpmProcessInstanceStatusEventListener 在终态回调里执行现有的"生成周期账单+锁房源"逻辑，业务与引擎解耦
- **[D2流程编排]** 借鉴其触发器节点（HTTP请求/更新数据）与类 Postman 的节点监听器配置：把 zhyq 里硬编码的跨模块联动（审批过→出账单、工单超时→升级、告警→派单）改为流程节点上可配置的动作，运营可改流程而不改代码
- **[D4架构设计]** 按 module-api/module-biz 拆分重构 zhyq 后端：14 个导航对应的模块间调用只走 -api 接口模块（如合同模块调财务出账走 finance-api），并用 pom 注释开关实现惠企/OA 等模块按项目裁剪，同时解决未来资产/停车/门禁模块的增量接入
- **[D3前端体验]** 将 /screen 深色大屏从手写页面升级为 GoView 式方案：iFrame 嵌入低代码大屏设计器并通过 URL 透传 accessToken，园区各驾驶舱由实施/运营拖拽配置，不再每块大屏都排开发
- **[D1功能分类]** 用其 IoT 模块的"产品-物模型-场景联动-告警配置"四层结构改造 zhyq 的 IoT 设备告警：设备类型抽象为物模型，告警规则做成场景联动配置；并参照 CRM 的"线索-客户公海(超期自动回收)-商机-合同-回款"链路给招商 CRM 补公海回收与回款计划

## AI 备注（一行）
其 yudao-module-ai 把 AI 做成独立可插拔模块（对话/知识库RAG/AI工作流/MCP，可接 Dify/FastGPT），是"后台系统内嵌 AI 模块"的现成参考位。

## 来源
- https://github.com/YunaiV/ruoyi-vue-pro
- https://doc.iocoder.cn/feature/
- https://doc.iocoder.cn/bpm/
- https://doc.iocoder.cn/bpm/use-business-form/
- https://doc.iocoder.cn/bpm/model-designer-dingding/
- https://doc.iocoder.cn/project-intro/
- https://doc.iocoder.cn/intro/
- https://github.com/yudaocode/yudao-ui-admin-vue3
- https://gitee.com/yudaocode/yudao-ui-go-view
- https://doc.iocoder.cn/report/screen/
- http://dashboard-vue3.yudao.iocoder.cn
