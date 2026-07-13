# 对标卡片：OfficeRnD Flex 与 Nexudus（联合办公/灵活空间管理 SaaS）

**资料充分度**：高——两家官方帮助中心与开发者门户公开且详尽，菜单结构、预订/计费流程、API/Webhook 机制均有官方文档直接佐证并经 ≥2 来源交叉；仅后台界面细节（截图级 UX）依赖文档描述与二手评测，未能实际登录 demo，前端维度个别细节为中等置信。

## 定位与目标用户
两者均为面向联合办公/灵活办公空间运营商的一体化管理 SaaS，覆盖"招商线索 → 合同签约 → 会员/租约 → 周期账单 → 资源预订 → 社区运营 → 数据分析"全链路。OfficeRnD Flex 服务 2500+ 空间，按 Operations/Experience/Growth/Data/Visitor 五大 Hub 组织产品；Nexudus 服务 90+ 国家数千空间，主打 API-first、白标会员门户和深度可定制。目标用户为单点到多城市连锁的空间运营商及楼宇业主，体量与 zhyq 的园区运营场景高度可比。

## 功能菜单树 / 模块划分
**OfficeRnD Flex 后台主导航**
- Operations（运营）：Companies / Contacts / Activity Log / Email Activity / Calendar
- Billing（账单）：Invoices / Plans（计费方案）/ Resource Rates / Categories / Stores / Goods / Amenities / Discounts
- Space（空间）：Locations + 按 Resource Type 动态生成的资源页签（办公室/工位/会议室…）、Floor Plans
- Calendar（预订日历）：全资源占用总览，按时间/容量/设施筛选并直接下单
- Experience Hub（体验）：Events / Messages / Tickets（工单）/ Posts / Benefits / How-To Guides
- Growth Hub（增长）：电商引擎、增值服务、收入管理
- Visitor Hub（访客）：访客登记 / 签到 / 前台接待
- Data Hub（数据）：报表、自定义仪表板、occupancy/revenue 指标
- Integration Hub（集成）：门禁/财务/WiFi 等 50+ 集成的启用管理
- Settings：Billing Rules / Data & Extensibility（Developer Tools：API 应用、Webhooks）

**Nexudus Admin Panel 主导航**
- Dashboard：当日概况卡片 + 12 个月签约/营收趋势，可自建多 Tab 卡片式仪表板
- CRM：Opportunities / CRM Boards（拖拽式销售阶段看板）/ 线索与跟进自动化 / 报价与电子签
- Community：News feed / 活动与报名 / 问卷 Surveys / 讨论区 / Perks 福利
- Finance：Invoices / Payments / Ledgers（账户台账）/ 税费 / 催缴
- Operations：Customers / Teams / Contracts / Check-ins / 日常运营
- Inventory：Plans（套餐）/ Products / Resources（可订资源）/ Floor Plans / Credits 权益
- Tasks：任务中心（可筛选）
- Help Desk：客户服务请求（报修/工单）
- Reports / Nexudus Explore：现成报表 + 自助 BI 设计器
- Settings：场地级设置 / Integrations（Webhooks）/ Web Templates Editor（门户模板）

## 核心业务流程编排
1) 合同→会员→账单自动化（两家共同的核心编排）：OfficeRnD 中 Billing Plan 定义价格、Membership 把方案绑定到人/公司；合同（可经 Dropbox Sign 电子签）一旦双方签署，系统自动生成 memberships、一次性费用和资源占用（assignments）并锁定平面图上的工位/办公室——与 zhyq"合同审批后自动生成周期账单+锁房源"同构，但多了电子签闭环和"资源可用性冲突阻止合同生效"的校验。Nexudus 同样：分配合同即承诺自动开票，合同起始日为当天/过去时 15 分钟内自动生成首张发票。
2) 周期账单编排：按每个客户合同里的 billing day 自动跑 Bill Run，归集会员费+预订费+期间散单为一张发票；支持按方案配置首/末期 proration、预付 N 个周期、"prorated 发票需审批（保留草稿）"；到期后自动催缴提醒、逾期费（OfficeRnD Late Fees）、超期自动暂停服务（Nexudus 可配 10-30 天暂停规则），并向 Xero/QuickBooks 同步。
3) 会议室/工位预订编排（OfficeRnD 最细）：Booking Policy 绑定在 Resource Rate 上，可配最长时长、缓冲时间、提前预订窗口（最长24个月）、是否允许循环预订、"仅剩余 credits 才可订"、需管理员审批（Tentative 状态+超时未批自动取消）、未按时签到自动取消。计费用 Booking Credits：1 credit=1 小时，套餐每月自动发放（月度过期）或一次性发放（不过期），预订时优先扣 credits、不足自动转现金费用并进下期账单。
4) 招商 CRM 流程（Nexudus）：CRM Boards 定义阶段（stage），Opportunity 拖拽推进，阶段可挂自动动作（跟进提醒、带看/续约提醒、自动邮件），报价+电子签在线完成后转为合同。
5) 跨模块联动：预订/合同/发票/会员变更均可触发 Webhook 事件（member created、booking updated、contract signed、invoice generated），供门禁、WiFi、打印等外部系统实时联动；访客登记（Visitor Hub）与会议预订打通。两家均无通用审批流引擎，审批以"预订审批、prorated 发票审批"等场景化开关实现。

## 前端形态
1) 双端分离是最大特征：管理端（Admin Panel）+ 白标会员端（Member Portal + 白标移动 App）。OfficeRnD 提供 Members App（白标）、Rooms App（会议室门口屏排期）、Visitor Hub App（前台签到平板）；Nexudus 提供 Passport 会员 App、会员门户含目录/动态/活动报名/福利市场/帮助台。zhyq 目前只有管理后台，缺"租户自助端"。
2) 管理端导航范式：左侧一级模块 + 业务对象列表页，但重交互组件多——Nexudus 有 Quick Add 全局快速创建（客户/发票/预订/任务）、拖拽式 CRM 看板、登录即见的"智能首页"（系统巡检各场地后把需处理事项直接推到首屏，点击卡片直达对应页面）、可自建多 Tab 的卡片式 Home Dashboard 并可分享给其他管理员；V3 版列表页响应式（行→卡片）适配移动端。OfficeRnD 导航支持区块折叠、常用功能收藏（书签）、全局搜索。
3) 可视化交互：两家都以"交互式平面图（Floor Plan）"为空间管理核心视图——上传底图后描摹出工位/办公室，直接在图上看占用/价格/剩余，会员可在图上点选订位；Nexudus 支持挂接合同自动标记占用、Archilogic 3D 平面图。预订用统一 Calendar 视图（按资源/时间轴筛选+直接下单）。
4) 报表形态：非大屏而是自助 BI——Nexudus Explore 用 measures/dimensions/segments 组查询、自动选图表类型，Pro 版可保存自定义仪表板并按角色（报表查看者/编辑者）授权；OfficeRnD Data Hub 提供自定义仪表板与行业 benchmark。
5) 定制体系：Nexudus 会员门户提供后台内置 Web Templates Editor，支持模板版本管理（副本编辑→测试→一键切生产→可回滚）、组件级替换、自定义页面，甚至可自托管门户。公开演示：官网可预约 demo（nexudus.com、officernd.com），G2 有界面截图。

## 架构与功能设计要点
1) 模块边界：两家都按业务域切模块（CRM/Finance/Operations/Inventory/Community/Reports），且把"商品化"做成一等公民——Nexudus 的 Inventory 统一管理 Plans（周期套餐）/Products（一次性商品）/Resources（可订资源）/Credits（权益），OfficeRnD 的 Billing 下统一 Plans/Resource Rates/Goods/Discounts。即"任何可收费的东西都是目录项"，账单引擎只消费目录，这是其功能设计核心。
2) 扩展点与开放接口：两家均为 API-first。OfficeRnD 有独立 developer.officernd.com（版本化 v1.0-Flex/v2.0-Flex），OAuth2 + 细粒度 scope（如 flex.settings.webhooks.read），后台 Settings > Data & Extensibility > Developer Tools 自助创建应用（Client ID/Secret，读/写权限）与 Webhook Endpoint；Webhook 载荷带 officernd-signature 头（t=时间戳 + HMAC-SHA256），官方明确"不建议轮询"。Nexudus 有 developers.nexudus.com 全端点 REST 文档、SDK、CLI、驱动会员门户的同一套 API（可完全自建前端）、门禁专用 API、Marketplace 应用机制（应用被客户启用后向 Notification URL 推送变更）、Webhook 共享密钥签名。
3) 多租户组织方式：Organization（品牌）→ Location（场地）多层级；每个 Location 可独立配置计费主体、税率、货币、支付网关和门户模板，报表跨场地汇总；管理员按角色+场地授权（含仅报表角色）。OfficeRnD 支持"每个地点一个独立开票主体"。
4) 流程引擎位置：没有通用 BPMN 式审批流引擎；自动化分散为三层——领域内置自动化（合同签署→自动生成会员/占用；billing day→自动 Bill Run）、场景化开关（预订需审批、prorated 发票需审批、超期自动暂停）、事件外溢（Webhook→外部系统）。CRM Boards 的"阶段+自动动作"承担轻量流程编排。
5) 均为闭源 SaaS，无公开仓库目录结构；架构信息来自官方开发者门户与帮助中心。

## 可借鉴点 TOP5
- **[D2流程编排]** 照搬 OfficeRnD 的合同签署闭环：合同状态置为已签（或电子签回调）时，一个事务内自动生成周期账单计划+资源占用记录+锁定房源，并前置校验资源可用性冲突（冲突则阻止合同生效）——zhyq 已有雏形，补上电子签集成位和冲突校验即可成型
- **[D2流程编排]** 给 zhyq 会议室加 Booking Policy 配置对象（挂在资源/费率上）：最长时长、缓冲时间、提前预订窗口、需审批（Tentative 状态+超时未批自动取消）、未签到自动释放，替代现在写死的简单状态流转，等于用配置化开关补审批流空白
- **[D1功能分类]** 引入 Booking Credits 权益体系：租户套餐/合同每月自动发放会议室时长额度（月度过期），预订优先扣额度、超出部分自动生成费用并并入下期周期账单——把 zhyq 的物业会议室模块和财务账单模块真正打通
- **[D3前端体验]** 把 zhyq 楼宇租控图升级为 Nexudus 式交互平面图：单元挂接合同自动标记占用/到期释放，图上直接看价格与剩余并发起招商/预订，替代纯表格租控；同时借鉴其'智能首页'——登录即展示系统巡检出的待办异常卡片，点击直达处理页
- **[D4架构设计]** 仿两家的开放能力最小集补'第三方对接全模拟'短板：定义 10 个左右领域事件（合同签署/账单生成/工单创建/告警触发…），实现带 HMAC 签名的 Webhook 推送 + 后台自助配置 Endpoint 页面，先让门禁/停车等外部系统能被真实驱动

## AI 备注（一行）
Nexudus 内置 NAI 助手（自然语言查发票/建预订/生成报表，会员端可对话式订会议室）及 AI 版 Churn 看板、OpenAI 帮助台自动应答——"对话式预订+NL 查报表"是 zhyq 值得留的 AI 嵌入位。

## 来源
- https://help.officernd.com/en/articles/292085-flex-main-menu-navigation
- https://help.officernd.com/en/articles/271498-welcome-to-officernd-flex
- https://help.officernd.com/en/articles/248555-flex-set-up-booking-policies
- https://help.officernd.com/en/articles/248557-flex-booking-credits-start-here
- https://help.officernd.com/en/articles/248617-flex-how-billing-works
- https://help.officernd.com/en/articles/421737-flex-e-sign-start-here
- https://help.officernd.com/en/articles/248567-flex-add-a-floor-plan
- https://developer.officernd.com/docs/webhooks-getting-started
- https://help.nexudus.com/docs/admin-panel
- https://help.nexudus.com/docs/prorating-contracts
- https://help.nexudus.com/docs/understanding-customer-invoicing
- https://help.nexudus.com/docs/floor-plans
- https://help.nexudus.com/docs/understanding-how-to-customize-the-members-portal
- https://developers.nexudus.com/reference/webhooks
- https://nexudus.com/api-first-coworking-platform/
- https://nexudus.com/crm-software/
- https://help.nexudus.com/v3/docs/nexudus-explore
