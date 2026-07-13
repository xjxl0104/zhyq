# 对标卡片：Snipe-IT（开源 IT 资产管理，Grokability，参考对象含 ERPNext 资产模块思路）

**资料充分度**：高：官方文档站、产品页、GitHub 仓库（README/目录/Models/package.json 实测）三方互证，功能与技术栈均有仓库实体佐证；仅多公司 scoping 细节文档页 404，依据文档索引+社区 issue 交叉确认；ERPNext 资产模块仅作对照一笔带过，未深入调研。

## 定位与目标用户
Snipe-IT 是一个免费开源（AGPL-3.0）的 IT 资产/许可证管理系统，基于 Laravel 12，定位是"比电子表格好得多"的中小组织 ITAM 工具：谁拿了哪台设备、何时采购、如何折旧、许可证何时到期。目标用户是 IT 运维/行政资产管理员，支持自托管（免费无限资产/用户）与官方云托管（$39.99/月起），官方数据称管理超 1590 万资产、5600+ 活跃客户。对 zhyq 而言它是"资产管理空白模块"的最直接功能面参照。

## 功能菜单树 / 模块划分
- 仪表板 Dashboard（近期活动流 + 资产/配件/耗材/组件统计卡）
- 资产 Assets
  - 全部资产 / 按状态标签视图（Deployed / Ready to Deploy / Pending / Archived）
  - 可申请资产 Requestable Assets（用户自助申请）
  - 待审计 / 逾期审计 Audit Due / Overdue
  - 维护记录 Maintenances（维修/升级/保养工单式记录）
  - 已删除资产
- 许可证 Licenses（席位 Seats、到期提醒、多套餐）
- 配件 Accessories（键鼠等，无资产标签，可借人或借给资产）
- 耗材 Consumables（纸/墨粉，只出不还）
- 组件 Components（内存/硬盘，装配到资产）
- 预定义套件 Predefined Kits（新员工入职一键打包签出）
- 人员 People（用户档案 + 名下资产/配件/许可证清单）
- 导入 Import（CSV 导入器：资产/用户/许可证/配件/地点…）
- 报表 Reports
  - 活动报表（全量操作审计日志）
  - 折旧报表 / 许可证报表 / 未签收资产报表 / 审计日志
  - 自定义报表模板 ReportTemplate
- 设置 Settings
  - 基础数据：公司 Companies / 地点 Locations / 分类 Categories / 资产型号 Asset Models / 制造商 / 供应商 / 部门 / 折旧规则 Depreciations / 状态标签 Status Labels
  - 自定义字段与字段集 Custom Fields / Fieldsets
  - 品牌/标签打印/条码、通知与告警阈值、群组权限、LDAP/SAML/SCIM、备份、Webhook(Slack)

## 核心业务流程编排
核心编排全部围绕「签出/签入（Checkout/Checkin）状态机」展开：1) 资产状态由 Status Label 驱动，仅四种元类型（Deployable/Undeployable/Pending/Archived），Deployable 资产被签出后自动获得派生态 Deployed；签出对象可为人员、地点或另一资产，签出期间锁定不可再借（防"重复预订"，与 zhyq 合同锁房源同构）；签入时才允许切换到维修/报废等任意状态。2) 签收合规链：签出时可强制 EULA/服务条款确认 + 数字签名（CheckoutAcceptance 模型），未签收资产进入"Unaccepted assets"报表，全程写入 Actionlog 审计流水。3) 自助申请流：资产或资产型号可标记 requestable，普通用户提交申请（CheckoutRequest），管理员在"Requested"列表人工签出——注意：没有内置多级审批流引擎，"经理审批→资产管理员发放"是社区多年 Top 呼声（issue #3565/#6453/#8476），与 zhyq 同病。4) 审计盘点循环：设置审计间隔后系统反规范化维护 next_audit_date，提供到期/逾期清单与 API，支持扫码快速盘点。5) 通知自动化：靠 cron 触发一组邮件/Slack Webhook 告警——预期归还到期、许可证到期、低库存（配件/耗材阈值）、审计到期，用户侧另有签出/签入/催还邮件。6) 折旧：折旧规则（月数+残值下限）挂在资产型号上，线性折旧，输出折旧报表（对比 ERPNext 资产模块的自动折旧分录记账，Snipe-IT 只算不记账，无财务凭证联动）。

## 前端形态
传统服务端渲染后台，非 SPA：Blade 模板 + AdminLTE 2.4 主题 + Bootstrap 3 + jQuery（package.json 实证），近年局部引入 Laravel Livewire 做交互组件。导航范式为经典左侧深色 sidebar + 顶栏全局搜索/快捷创建，与 zhyq 的 Element Plus 后台三段式同一范式。仪表板用 chart.js/morris.js 图表 + 四色统计卡 + 近期活动流，无监控大屏形态。代表性交互：bootstrap-table 提供全站表格的列显隐自定义、多格式导出（含 jsPDF）；select2 搜索下拉；signature_pad 手写签名板（资产签收）；条码/二维码标签生成与打印（Labels 模块）、兼容手持扫码枪录入；实体可加颜色标记（Color Tags）区分地点/部门；管理员可配置品牌色/皮肤（AdminLTE skins）与自定义 Logo。支持 55+ 语言、按用户切换语言；有 pa11y 无障碍 CI。公开演示站：https://snipeitapp.com/demo/。整体观感偏老（Bootstrap 3），体验优势在"表格生产力"细节而非视觉。

## 架构与功能设计要点
单体 Laravel 12 应用（PHP 92%+Blade 7%），License：AGPL-3.0（zhyq 借鉴设计无碍，直接抄代码需注意传染性）。仓库 grokability/snipe-it（14k stars，291 releases，最新 v8.6.3）。顶层结构为标准 Laravel：app/ config/ database/ routes/ resources/ public/ tests/，另有 docker/ ansible/ sample_csvs/ install.sh。app/ 内按职责分层：Models（约 40 个模型：Asset/AssetModel/Accessory/Consumable/Component/License/LicenseSeat/PredefinedKit/Maintenance/Depreciation/Statuslabel/CustomField/CustomFieldset/CheckoutAcceptance/CheckoutRequest/Actionlog/ReportTemplate…）、Http（Web+API 双控制器）、Livewire、Notifications、Policies（权限）、Presenters（表格列定义/展示层）、Observers、Actions。功能设计要点：a) 模块边界即"六类可签出物"（Asset/License/Accessory/Consumable/Component + Kit 聚合），共享 Checkoutable 抽象与统一 Actionlog；b) 扩展点没有插件体系，官方口径是 JSON REST API（OpenAPI 文档齐全）+ Webhook + 导入器，生态集成（Jamf/Kandji/Intune/Jira/Zapier 类低代码）全部走 API 外挂，这一"API 优先、生态外置"策略值得注意；c) 多租户为"Multi-Tenancy(ish)"：Company 字段 + CompanyableScope 全局查询作用域按公司隔离非超管视图，但制造商等基础数据全局共享，官方自认非真正多租户（MSP 通常一客户一实例）；d) 自定义字段通过 CustomFieldset 绑定到 Asset Model 实现按型号动态表单。无流程引擎、无消息编排，均为同步状态流转 + cron 告警。

## 可借鉴点 TOP5
- **[D1功能分类]** zhyq 新建资产模块直接采用 Snipe-IT 的六类对象模型：固定资产(有唯一标签)/许可证/配件/耗材/组件/预定义套件，配 Model→Category→Manufacturer/Supplier/Depreciation 的元数据分层，避免把所有东西塞进一张'设备表'
- **[D2流程编排]** 复用 zhyq 已有的'合同锁房源'思路实现资产 Checkout/Checkin 状态机：Status Label 四元类型 + 签出即锁定 + 签入才可改维修/报废状态，再加 requestable 自助申请→管理员发放，一并成为未来审批流引擎的第一个落地场景
- **[D2流程编排]** 照搬其审计盘点闭环：资产设审计间隔字段并反规范化 next_audit_date，生成到期/逾期盘点清单 + 扫码盘点页，并入 zhyq 物业巡更 App 的扫码能力，同时补一组阈值型告警（催还/低库存/到期）挂到现有消息中心
- **[D3前端体验]** 资产/物品交接引入'EULA 确认 + signature_pad 手写签名 + 未签收清单'的合规留痕交互，zhyq 的工单完工确认、会议室物资借用、合同交房单均可复用同一签收组件
- **[D4架构设计]** 借鉴 CustomFieldset 机制：自定义字段集绑定到资产型号（而非全局），不同类型资产渲染不同动态表单——zhyq 房源/设备/资产三处的自定义属性可统一成一个字段集引擎

## AI 备注（一行）
仓库自带 CLAUDE.md、文档站提供 llms.txt/OpenAPI 索引、社区已有 Snipe-IT MCP Server——"为 AI 代理准备好文档与 API 入口"是 zhyq 低成本可抄的 AI 嵌入位。

## 来源
- https://github.com/grokability/snipe-it
- https://snipeitapp.com/product
- https://snipe-it.readme.io/docs/overview
- https://snipe-it.readme.io/docs/requestable-assets
- https://snipe-it.readme.io/docs/email-alerts
- https://snipeitapp.com/demo/
- https://github.com/snipe/snipe-it/issues/3565
- https://snipe-it.readme.io/docs/general-settings
