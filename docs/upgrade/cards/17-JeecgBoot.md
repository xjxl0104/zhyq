# 对标卡片：JeecgBoot（开源低代码平台）

**资料充分度**：高——功能与架构均有官方 README、文档中心（help.jeecg.com/doc.jeecg.com）、仓库目录结构、演示站多来源交叉验证；唯一需留意的边界是流程设计器/表单设计器/OA 等属商业版增值功能（官方与多个来源均提及），开源版仅含 Flowable 集成基础，卡片中已标注。

## 定位与目标用户
JeecgBoot 是国内头部的企业级 AI 低代码开发平台（GitHub 约 47k Star，Apache-2.0），面向 Java 技术栈的企业信息化开发团队。定位"低代码+零代码"双驱动：简单功能用 Online 在线配置零代码实现，复杂功能用代码生成器生成后手工 Merge，宣称消除 Java 项目 70-80% 重复工作。典型场景为 MIS/OA/ERP/CRM/SaaS 多租户系统，即 zhyq 这类管理后台的"底座平台"，而非某个垂直业务产品。

## 功能菜单树 / 模块划分
- AI 应用平台
  - AI 模型管理 / AI 应用管理 / AI 知识库（RAG）
  - AI 流程编排 / AI 聊天助手 / MCP 插件管理 / 提示词管理
- Online 在线开发（低代码核心）
  - Online 表单开发（在线建表、23 类控件、单表/树/一对多/一对一）
  - 代码生成器（4 套模板、自定义模板机制、AI 建表）
  - Online 报表 / 仪表盘设计器
  - 编码规则 / 校验规则 / APP 版本管理
- 流程管理（BPM，部分能力在商业版）
  - 流程设计（BPMN 在线画流程）/ 流程发起与审批
  - 表单挂靠（Online 表单/编码表单/设计器表单）
  - 流程监控（基于 Flowable 历史表）
- 数据可视化（JimuReport/JimuBI 插件，GPL 双协议）
  - 报表设计器（类 Excel、打印设计）/ 大屏设计 / 门户设计
- 系统管理
  - 用户/角色/菜单/部门/职务/通讯录
  - 按钮权限 / 数据权限 / 表单权限（字段禁用与隐藏）
  - 多租户管理（租户、租户角色、套餐）/ 首页配置
  - 字典 / 公告 / 多数据源 / 第三方配置（钉钉/企业微信）
- OpenAPI
  - 接口管理 / 接口授权（AK/SK）/ 接口文档
- 系统监控
  - 定时任务 / Redis/JVM/服务器监控 / 请求追踪
  - 系统日志 / 数据日志（快照对比）/ 消息中心（短信/邮件/微信推送）/ 在线用户
- 示例与组件库（demo 模块）
  - JVxeTable 行编辑表格 / 图表示例 / 大屏模板（作战指挥、物流中心）

## 核心业务流程编排
D2 重点。1）流程引擎：集成开源 Flowable，官方封装 RuntimeService/TaskService/RepositoryService/HistoryService 四大服务；在线 BPMN 流程设计器（前端位于 jeecgboot-vue3/src/views/sys/flow），支持 StartEvent/UserTask/ExclusiveGateway 等标准节点，节点属性可配负责人、优先级、关联表单；社区二开生态成熟（会签、Online 表单线上审批发布等）。注意：完整的流程设计/审批/监控属商业版功能，开源版提供 Flowable 依赖与集成基础。2）核心设计思想是"流程与表单分离（松耦合）+ 表单挂靠"：流程节点不绑死表单，可挂靠三类表单——Online 配置表单、表单设计器表单、手写编码表单，同一引擎服务不同开发模式；业务逻辑通过扩展任务接口注入，而非写死在流程里。3）表单侧的"三级增强"机制是其流程外自动化编排的关键：JS 增强（表单渲染/联动/默认值，loaded 钩子）、SQL 增强（action 类按钮触发 /online/cgform/api/doButton，执行带 #{id}、#{sys_user_code}、#{sys_org_code}、#{sys_date} 等系统变量的关联更新 SQL）、Java 增强（类 AOP，在表单增/删/改时挂业务类，导入增强独立接口）；自定义按钮以"按钮编码=函数名/SQL键"的约定把 UI 按钮与增强逻辑绑定，button/link 两种样式 × action/js 两种动作类型组合出行级与批量操作。4）AI 流程编排：类 Dify 的 LLM 应用编排（RAG 管道、模型管理、实时可观察），与 BPM 审批流是两套引擎。5）未见 SLA/超时催办等在开源文档中的明确佐证，不作事实陈述。

## 前端形态
技术栈 Vue3 + TypeScript + Vite6 + Ant Design Vue4 + Pinia + UnoCSS/WindiCSS + vxe-table，基于 Vben Admin 体系（与 zhyq 的 Element Plus 不同系）。导航范式：后端权限数据驱动的动态侧边菜单 + 多标签页，按钮级/数据级/表单字段级三层权限控制（字段可配禁用/隐藏），二级管理员机制。首页形态："四套首页 + 工作台"满足不同场景，支持租户/用户维度的首页配置与门户自定义（门户设计器）。大屏：JimuBI 拖拽式大屏设计器 + 内置模板（作战指挥中心、物流服务中心），与 zhyq 手写 /screen 的思路不同——大屏是可配置资产而非硬编码页面。代表性交互组件：JVxeTable 行编辑表格（面向 ERP 复杂排版）、字典组件、选人/选部门组件、popup 弹出选择、在线 code 编辑器、高级查询器（查询条件由配置自动组合生成，免编码）。支持 qiankun 微前端、Electron 桌面打包、WebSocket 消息、CAS/第三方登录；配套 JeecgUniapp 覆盖 H5/小程序/APP/鸿蒙 Next。公开 demo：https://boot3.jeecg.com（admin/123456）。

## 架构与功能设计要点
License：主仓库与 Vue3 前端均为 Apache-2.0；但配套报表 JimuReport 为 GPL-3.0+附加条款双许可（商用需注意），流程设计器/表单设计器/OA 等部分高级功能在商业版。仓库组织（github.com/jeecgboot/JeecgBoot，monorepo 前后端同仓）：根目录 = jeecg-boot（后端）+ jeecgboot-vue3（前端）+ docker-compose 脚本。后端 Maven 多模块：jeecg-boot-base-core（共通核心：工具类、config、权限、查询过滤器、注解）、jeecg-module-system（拆 jeecg-system-biz / jeecg-system-start 单体启动 / jeecg-system-api，api 再分 local-api 与 cloud-api 两个实现——这是单体/微服务一键切换的关键抽象）、jeecg-boot-module（业务模块目录）、jeecg-module-demo（示例）、jeecg-server-cloud（gateway/nacos/monitor/xxljob 等微服务启动件）。扩展点设计（D4 重点）：a) jeecg-boot-starter 独立仓库，把 AI(LangChain4j)/job/lock/mq/seata 等做成按需引入的 starter，业务工程零侵入；b) 插件化集成——积木报表以 Maven 依赖+SQL+菜单挂载方式"可插拔"接入，官方称插件能力（可插拔），另有社区插件集成站 jeecgboot.com 收录第三方插件；c) 代码生成器模板机制支持自定义模板，生成物直接落入 module 与前端 views，与手写代码同构；d) Online 表单的 JS/SQL/Java 三级增强 = 零代码产物的受控逃生舱；e) OpenAPI 模块（AK/SK）作为对外集成边界。多租户：内置租户管理/租户角色/租户套餐，租户级首页配置，SaaS 模式官方文档专章。

## 可借鉴点 TOP5
- **[D2流程编排]** 引入 Flowable + 在线 BPMN 设计器补 zhyq 审批流空白，并采用其"流程与表单分离+表单挂靠"设计：合同审批、工单、OA 请示共用一套引擎，节点通过挂靠指向各业务表单，业务逻辑经任务扩展接口注入而非写死在状态字段里
- **[D2流程编排]** 借鉴 Online 表单"按钮编码=函数名/SQL键"的三级增强约定（JS增强做表单联动、SQL增强带 #{sys_user_code} 等系统变量做关联更新、Java增强类AOP挂业务类），为 zhyq 工单/合同表单提供不改核心代码的受控自定义口子
- **[D4架构设计]** 参照 jeecg-system-api 拆 local-api/cloud-api 与 jeecg-boot-starter 的组织方式：zhyq 把第三方对接（IoT、门禁、支付、短信）抽成接口+starter 实现，模拟实现与真实对接同接口可替换，报表/大屏也可像积木报表一样做成"依赖+SQL+菜单"可插拔插件
- **[D3前端体验]** 把 zhyq 单一 /screen 硬编码大屏升级为"仪表盘/大屏设计器 + 角色工作台"模式：直接集成 JimuReport/JimuBI（注意 GPL 双许可）或自建轻量配置化首页，为招商/财务/物业角色提供各自可配置的工作台首页
- **[D1功能分类]** 补齐 JeecgBoot 式平台层菜单：消息中心（短信/邮件/微信统一推送，替代各模块各自通知）、数据日志（修改前后快照对比，合同/账单审计刚需）、OpenAPI（AK/SK 接口授权，为惠企/政务对接留标准出口）、表单字段级权限（财务字段对物业角色隐藏）

## AI 备注（一行）
AI Skills 支持一句话生成流程图/表单/报表/大屏（Chat2BI 图表智能体、AI 建表），且积木报表已内置 Claude Code skill 从自然语言/截图生成报表配置——zhyq 报表与大屏模块是同款嵌入位。

## 来源
- https://github.com/jeecgboot/JeecgBoot
- https://github.com/jeecgboot/JeecgBoot/tree/main/jeecg-boot
- https://github.com/jeecgboot/jeecgboot-vue3
- https://github.com/jeecgboot/jeecg-boot-starter
- https://help.jeecg.com/
- http://doc.jeecg.com/2044111
- https://doc.jeecg.com/2044102
- http://doc.jeecg.com/2044164
- https://boot3.jeecg.com/
- https://help.jimureport.com/projectJoin/jeecgboot/
- https://blog.csdn.net/gitblog_00213/article/details/152351783
- https://www.oschina.net/news/270424/jeecgboot-3-6-1-released
