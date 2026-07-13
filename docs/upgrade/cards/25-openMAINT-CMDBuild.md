# 对标卡片：openMAINT 与 CMDBuild READY2USE（开源 CMMS/IWMS 与 ITIL 资产管理）

**资料充分度**：中偏高：功能分类、工单流程、架构组件均有官网产品页+官方手册页+SourceForge 版本库交叉印证；但 openmaint.org 域名当前解析异常（HTTP 返回过期停放页），openMAINT 各页面内容取自 Wayback 官方存档；菜单树按官方模块页还原，未逐屏实测在线 demo。

## 定位与目标用户
两者同源同厂：CMDBuild 是开源"资产管理应用配置平台"（Java Web 环境，可自建数据模型/工作流/报表/仪表盘），在其上官方做了两个"垂直即用版"——**openMAINT** 面向不动产与设施管理（Property & Facility Management，CMMS 产品，管理楼宇/机电设备/家具及其维保、后勤、经济活动），**CMDBuild READY2USE** 面向 IT 治理（ITIL 兼容的 CMDB+服务台）。原维护方为 Tecnoteca srl（Zucchetti 集团），2026 年起项目维护方变更为同集团 PAT srl；最新版本 CMDBuild 4.2（2026-03）、READY2USE 2.4、openMAINT 2.4。目标用户：公共机构、银行、医院、学校、物业/设施服务公司、有厂房的工业企业——强调"低投入、可渐进启用、开箱即带流程/报表/仪表盘"。

## 功能菜单树 / 模块划分
- openMAINT（6 大功能域，官方 Modules 页）
  - 空间与资产台账 Space & Asset Inventory
    - 空间层级：建筑群 → 楼栋 → 楼层 → 房间
    - 设施系统：电气、给排水、暖通空调、消防安防、数据传输等"装置(installations)"及所属技术资产
    - 家具与建筑构件（门窗等）、外部基础设施（道路/照明）与绿地
    - 每对象五类登记卡：标识、技术、功能、行政（成本/合同/折旧）、文档（按类目+元数据归档）
  - 设施维护 Facility Maintenance
    - 预防性维护：维护手册、检查清单(checklist)、周期计划、自动生成维保日历与工单
    - 纠正性维护：故障申报、呼叫中心审核、工单派发、执行回单、核算审批
    - SLA 配置与延误邮件告警；活动/核算/SLA/统计等 8 类报表；团队负荷、任务状态仪表盘
  - 后勤管理 Logistic Management：仓库、库存物料、维保领料与库存联动
  - 经济管理 Economic Management：预算、维保成本、供应商、采购合同/订单、价格表、物业行政事务
  - 能源与环境 Energy & Environment：表计读数登记、能耗记录与分析
  - GIS 与 BIM：GIS 地图定位（OpenStreetMap）、DWG 导入 2D 矢量平面、IFC 同步 3D BIM 模型浏览
- CMDBuild READY2USE（6 大功能域）
  - 配置管理：客户端/服务器/网络/软件 CI 台账，依赖关系、文档、报表、地理定位
  - 服务台与 ITIL 流程:事件管理、请求履行、变更管理、问题管理、资产管理
  - 资产生命周期：订单登记→到货入库→分配→人员/位置间转移→报废
  - 服务管理：多级服务目录、标准/非标工单、SLA 与 KPI、控制与通知
  - 数据对账 Data Reconciliation：经连接器与外部系统同步（人员、计算机与软件、虚拟机、网络）
  - GIS & BIM 支持
- CMDBuild 平台层能力（两垂直版共同继承）
  - 数据建模器、工作流引擎+编辑器、报表引擎(JasperReports)、仪表盘配置
  - 关系范式与可视化关系图(Visual Graph)、数据卡历史版本(History)
  - 文档库(Alfresco/CMIS)、邮件管理、条码/二维码、任务调度器(Task Manager)
  - 用户画像/多租户、管理模块与数据管理模块、文件导入导出、REST/SOAP Webservice 与连接器
- 用户界面四形态：Web 主界面、Mobile APP、自助服务门户、Webservice（后两者及 APP 为订阅制非开源）

## 核心业务流程编排
D2 重点，官方 Facility Maintenance 页描述的两条主流程：
1) **预防性维护**："维护手册"为编排源头——登记涉及资产、诊断/检查清单、活动类型与频率后，系统**后台按可配置时间间隔自动生成维保日历与对应工单**，工单直接推送给配置好的内外部团队；执行时按维护计划里定义的 checklist 逐项记录结果（界面优化为"一键完成"）；支持多种排程准则；且可由监测系统（烟感、故障、错误等经标准协议上报）**自动触发维护活动**——即"计划驱动+事件驱动"双入口。
2) **纠正性维护（故障工单）**：多渠道报障（含自助门户，订阅版）→ 呼叫中心/服务台**审核签报** → 系统**按问题类型与在约合同自动建议**团队/供应商并转发工单 → 计划、执行、登记干预报告（可用平板/手机）→ 支持向多家供应商询价、保密沟通后择优 → 一次维修可拆**多张子工单**（各有优先级、排期，按技能指派团队）→ **最终审批与核算**环节：登记各工种工时与采购材料，按价格表自动算价或手工定价，**计入对应预算科目**；领用仓库备件自动扣减库存；全程受 SLA 准则监控，延误自动邮件通知。
3) **流程技术底座**：所有流程由 CMDBuild 工作流引擎（Tecnoteca RIVER，旧版 Shark TWS+TWE 编辑器）驱动，流程即"过程类"数据卡，可视化编辑器可改流程图与步骤表单；READY2USE 的事件/请求/变更/问题流程同一引擎实现——CMMS 与 ITSM 只是同一编排机制上的不同流程包。

## 前端形态
经典企业级"重后台"形态：桌面 Web 主客户端基于 **Sencha Ext JS**（Ajax 单页富客户端），左侧树形导航（菜单可按角色/租户配置）+ 数据卡列表 + 卡片详情多标签页（明细/备注/关系/历史/附件），管理端(Administration Module)与业务端分离；内嵌 GIS 地图视图（OpenLayers/GeoServer）、DWG 2D 平面图与 **3D IFC/BIM 交互查看器**（可在房间间漫游、拖拽移动资产）、关系可视化图谱、JasperReports 报表与图表仪表盘。另有三个轻端：Mobile APP（iOS/Android，现场执行工单/扫码盘点，订阅制）、自助服务门户（简化界面报障与进度跟踪，建议以 Liferay+GUI Framework 发布，订阅制）、REST/SOAP API。整体 UI 风格偏传统 ERP，密度高、配置能力强，非现代轻量 SaaS 风。

## 架构与功能设计要点（含 License）
- **License：GNU AGPL v3**（CMDBuild 与 openMAINT 相同），附加条款：依据 GPLv3 第 7(b) 条，修改版界面必须保留可点击回官网的 "CMDBuild"/"openMAINT" Logo（商标保护）；Mobile APP 与自助门户不开源，走年度订阅。商用二次开发需注意 AGPL 传染性与 Logo 条款。
- **技术栈**：Java + SOA + Ajax（Ext JS），PostgreSQL（重度使用其对象化特性做数据建模）+ PostGIS，Tomcat 容器；工作流 Tecnoteca RIVER/Shark，报表 JasperReports（JasperStudio 设计），调度 Quartz，文档 Alfresco/CMIS，GIS OpenLayers+GeoServer，BIM BIMServer，认证 OpenLDAP，自动盘点建议 OCS Inventory 连接器。
- **平台+垂直包**架构是最大特色：底层是通用"元模型"平台（类/属性/关系/流程/报表/菜单全部数据库驱动可配置），openMAINT 与 READY2USE 只是官方预置的数据模型+流程+报表包，客户可渐进启用、也可继续改模型——"产品即配置"。
- 数据卡统一范式：任何实体（房间、水泵、合同、工单）都是"类+数据卡"，天然带历史版本、关系、附件、地理/BIM 定位，跨模块打通（工单↔资产↔合同↔预算↔库存）。
- 版本演进可查：SourceForge 上 CMDBuild 3.0→4.2、ready2use-2.0→2.4、openMAINT 0.8→2.4 独立发行线，手册分 Overview/User/Administrator/Workflow/Technical/Webservices 六册（4.2 起在线化）。

## 可借鉴点 TOP5
- **[D1]** 借鉴其 6 域功能分类骨架：空间资产台账 / 维护 / 后勤(库存) / 经济(预算合同) / 能源 / GIS&BIM——尤其"空间(建筑群-楼-层-房间)与设施系统(installations)分离、技术资产挂在系统下"的三层台账结构，比扁平设备列表更适合园区。
- **[D1]** 每个资产对象的"五类登记卡"（标识/技术/功能/行政/文档）+ 关系/历史/附件标签页范式，可直接套用为我们设备详情页的信息架构。
- **[D2]** 预防性维护"维护手册→checklist→周期→后台自动生成日历+工单"的编排方式，并保留"监测告警自动触发维护流程"的事件驱动入口，实现计划+事件双通道。
- **[D2]** 纠正性维护全链路值得照抄：多渠道报障→服务台审核→按故障类型+合同自动推荐承修方→一修多工单拆分（各自优先级/技能团队）→工时材料计价入预算科目→领料自动扣库存→SLA 超时邮件告警→审批核算关单。
- **[D4]** "通用可配置平台+官方垂直预置包"的产品形态：数据模型、工作流、报表、菜单全配置化，垂直行业包只是配置集——我们的园区平台可用同思路把物业/资产/能源做成可渐进启用的预置包；同时注意其 AGPL+Logo 条款，若引用代码需隔离或仅借鉴设计。

## 来源
- https://www.openmaint.org/en/product/project （What is openMAINT，经 Wayback 存档访问）
- https://www.openmaint.org/en/product/modules 及 /modules/facility-maintenance、/modules/space-asset-inventory（模块与维保流程，经 Wayback 存档）
- https://www.openmaint.org/en/download/license （openMAINT AGPL 许可条款，经 Wayback 存档）
- https://www.cmdbuild.org/en （平台能力清单、4.2 版本动态、维护方变更公告）
- https://www.cmdbuild.org/en/products/cmdbuild-ready2use （READY2USE 模块与 ITIL 流程）
- https://www.cmdbuild.org/en/project/license （CMDBuild AGPL 许可条款）
- https://www.cmdbuild.org/en/project/characteristics/higher-technology （技术组件栈）
- https://www.cmdbuild.org/en/documentation/manuals （六册官方手册体系）
- https://sourceforge.net/projects/openmaint/ 与 https://sourceforge.net/projects/cmdbuild/ （版本发行结构）
