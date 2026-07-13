# 对标卡片：Ant Design Pro 与现代后台设计趋势（含 Salesforce Lightning/SLDS 要点）

**资料充分度**：高——Ant Design Pro 页面清单直接读取仓库 routes.ts 源码并与 README 互证；ProLayout/主题 token 有官方文档多源；SLDS 2 与暗色模式有 Trailhead + Salesforce 官方博客 + 版本 Release Notes 三源。小瑕疵：preview.pro.ant.design 与 procomponents 文档页为 JS 渲染无法直接抓取正文，ProTable 交互细节部分依赖官方文档搜索摘要与训练期知识交叉印证。

## 定位与目标用户
Ant Design Pro 是蚂蚁集团开源的企业级中后台前端解决方案（React 脚手架，38.5k stars，MIT），定位"开箱即用的最佳实践模板"，目标用户是需要快速搭建标准中后台的前端团队。Salesforce Lightning Design System（SLDS/SLDS 2）则是全球最大 SaaS CRM 的官方设计体系，定义了企业级"对象—记录—相关列表"的页面范式与主题化架构。两者共同代表"设计体系 + 页面模板 + 配置驱动"的现代后台标杆，是 zhyq 前端体验升级的直接参照。

## 功能菜单树 / 模块划分
- Ant Design Pro v6 页面/布局模板清单（源自 config/routes.ts 与 README，双源印证）
  - 用户流：登录 / 注册 / 注册结果（独立无壳布局）
  - 欢迎页 welcome
  - Dashboard：分析页 analysis / 监控页 monitor / 工作台 workplace
  - 表单页：基础表单 / 分步表单 step-form / 高级表单（多卡片分组长表单）
  - 列表页：查询表格 table-list / 标准列表 / 卡片列表 / 搜索列表（文章·项目·应用三 Tab）
  - 详情页 profile：基础详情 / 高级详情（头部操作区+Tab+多区块）
  - 结果页：成功 / 失败
  - 异常页：403 / 404 / 500
  - 账户页：个人中心 center / 个人设置 settings
  - AI 助手 chatbot（基于 Ant Design X，v6 新增）
  - 管理页 admin（演示 access 权限路由）
- ProLayout 布局形态：side 侧边 / top 顶部 / mix 混合（splitMenus 一级菜单进顶栏）；固定 Header/Sider、Fluid/Fixed 内容宽度、水印 waterMarkProps、SettingDrawer 可视化调参
- ProComponents 页面级组件：ProLayout / PageContainer / ProTable / ProForm（Modal·Drawer·Step 变体）/ ProDescriptions / ProCard / ProList / ProSkeleton / ProField
- Salesforce Lightning 页面模板类型（Lightning App Builder）
  - App Page 应用主页 / Home Page 主页 / Record Page 记录页
  - Record Page 标准构件：Highlights Panel（紧凑布局前 7 字段+操作按钮）/ Path 阶段条 / Related 相关列表卡片 / Activity+Chatter Tab
  - 导航：顶部导航栏（按 App 切换项目集）+ App Launcher 全局应用/对象搜索 + Console 工作区标签/子标签 + 拆分视图（列表+详情并排）

## 核心业务流程编排
不适用——两者均为前端设计体系/脚手架标杆，不含业务流程引擎；仅有的"流程感"体现在 UI 范式层：Ant Design Pro 的分步表单（StepForm）与结果页承接提交流程，Salesforce 的 Path 组件把记录生命周期阶段（如商机阶段）可视化为顶部阶段条并配合"关键字段+指导语"推进，这属于 D3 交互而非流程编排。

## 前端形态
1）设计体系：antd v5 采用三层 Design Token（Seed→Map→Alias 派生），ConfigProvider 传入 theme JSON 即可运行时换肤，支持组件级 token 覆盖与多主题共存；v6/Pro v6 转向 Tailwind v4 + antd-style 与 CSS 变量（--ant-color-primary）、zeroRuntime。SLDS 2（Winter'26 GA）同样"结构与视觉解耦"，以 --slds-g-* 全局 styling hooks（CSS 自定义属性）取代旧 design tokens，配套 SLDS Linter 自动检查硬编码颜色，默认主题 Salesforce Cosmos。两大体系殊途同归：语义化 token + 算法/变量派生，而非手改样式。2）导航范式：ProLayout 三模式 side/top/mix，mix+splitMenus 把一级菜单放顶栏、二级放侧栏，菜单由路由配置自动生成并自动选中、自动推面包屑（PageContainer 联动）；Lightning 为"App 分组导航"——顶部导航栏随当前 App 切换项目集，App Launcher 提供全局应用/对象搜索直达，Console 应用用工作区标签+子标签+拆分视图处理多记录并行操作。3）工作台形态：Pro 提供三种驾驶舱模板——分析页（指标卡+图表）、监控页（实时大屏风格）、工作台 workplace（待办/进行中项目/动态/快捷导航的个人门户）；Lightning Home Page 可按角色用 App Builder 拖拽装配。4）表格/表单：ProTable 以 columns schema 一份配置同时驱动查询表单+表格+详情（ProDescriptions 复用），内置工具栏、列设置、密度切换、批量操作、可编辑表格；ProForm 提供 Modal/Drawer/Step 三种容器变体。2025 趋势印证：密度切换（compact/default/comfortable 且按用户持久化）、数字右对齐、批量操作悬浮显现、25-50 行分页已成企业表格标配，Cmd+K 命令面板成为新模板标配。5）暗色模式：antd 一行 theme.darkAlgorithm 即得全套暗色（可与 compactAlgorithm 叠加）；Salesforce 暗色模式（Spring'26 Beta 扩展到主流版本）仅在 SLDS 2 主题下可用，靠语义 styling hooks 自动解析明暗值。趋势层面"dark mode 是标配而非特性"，监控/夜班场景视为功能需求。公开 demo：https://preview.pro.ant.design（含右侧 SettingDrawer 实时切换布局/主题/色弱模式）。

## 架构与功能设计要点
Ant Design Pro：React 19 + Umi Max 4 + antd 6 + TypeScript（97.8%）。仓库结构：config/（routes.ts + defaultSettings.ts 集中配置）、src/pages、src/services、src/access.ts、mock/、tests/、.claude/skills（v6 内置 /pro-upgrade、/antd 两个 Claude Code skill）。核心架构思想：a) 路由即单一事实源——routes.ts 扩展 name/icon/hideInMenu/access 字段，一处配置同时生成菜单、面包屑、页面标题、权限控制；b) 分层抽象——antd 原子组件 → ProComponents 重型模板组件（schema/配置驱动，"默认即最佳实践"）→ Pro 页面模板/区块（Blocks），牺牲灵活性换 CRUD 开发效率；c) 扩展点在 render props：menuDataRender（菜单过滤=权限）、menuItemRender、access.ts 声明式权限函数；d) mock 目录约定实现前后端分离开发；npm run simple 可裁剪为精简版。多租户不在其范畴（脚手架层面无租户概念）。Salesforce Lightning 架构要点（限功能设计层）：a) 元数据驱动的页面组装——Lightning App Builder 让管理员零代码拖拽组件构建 App/Home/Record 页，按"App×记录类型×Profile×设备形态"四维分配页面版本，这是其最强扩展点；b) Compact Layout 作为独立配置对象驱动 Highlights Panel 与悬浮卡片；c) SLDS 2 结构/样式解耦 + 全局 styling hooks 使主题（含暗色）成为可配置资产（Themes and Branding 里建主题并激活）；d) 组件蓝图（blueprints）+ LWC 导航服务（standard__recordPage 等页面引用类型）把"页面类型"抽象为可编程目标。对 zhyq 的架构启示：把"菜单/权限/面包屑"收敛到一份路由配置、把"列定义"收敛到一份 schema，是低成本高回报的两个收敛点。

## 可借鉴点 TOP5
- **[D3前端体验]** 14 个一级导航改为 mix 混合导航：一级模块放顶栏、二级菜单放侧栏（参照 ProLayout layout:'mix'+splitMenus，每个一级配 redirect 防白屏），并给招商/物业/财务等角色做 Lightning 式'App 分组'——按角色只显示相关模块集
- **[D3前端体验]** 三段式列表页升级为 schema 驱动：仿 ProTable 在 Element Plus 上封装 useCrudPage，一份 columns 配置同时生成查询表单+表格列+详情描述，并补齐列设置、密度切换（按用户持久化）、批量操作工具栏三件套
- **[D3前端体验]** 合同/房源/租户详情从'弹窗查看'改为 Lightning 式记录页：顶部 Highlights Panel（关键字段+主操作按钮）+ Path 阶段条（合同：草拟→审批→生效→到期，联动账单生成状态）+ Related 标签页挂账单/工单/沟通记录相关列表
- **[D4架构设计]** 把菜单/面包屑/页面标题/权限收敛到一份路由配置（仿 routes.ts 的 name/icon/hideInMenu/access 扩展字段），替代现在分散维护的 14 个导航项，权限用 menuDataRender 式过滤统一实现
- **[D3前端体验]** 建 CSS 变量语义 token 层（仿 antd Seed→Alias 与 SLDS styling hooks，如 --zhyq-color-surface/-on-surface），一套 token 同时驱动后台亮色、一键暗色模式和 /screen 大屏深色主题，消除大屏与后台两套样式；再加 Cmd+K 命令面板做跨 14 模块的全局搜索直达

## AI 备注（一行）
Ant Design Pro v6 把基于 Ant Design X 的 AI 聊天页（/chatbot）做成标配一级页面模板，"AI 助手作为独立导航项"已成中后台脚手架默认嵌入位。

## 来源
- https://github.com/ant-design/ant-design-pro
- https://raw.githubusercontent.com/ant-design/ant-design-pro/master/config/routes.ts
- https://pro.ant.design/zh-CN/docs/layout/
- https://procomponents.ant.design/components/layout/
- https://ant.design/docs/react/customize-theme-cn/
- https://www.salesforce.com/blog/what-is-slds-2/
- https://trailhead.salesforce.com/content/learn/modules/dark-mode-ready-components-in-slds-2/activate-slds-2-and-preview-dark-mode
- https://trailhead.salesforce.com/content/learn/modules/lightning_app_builder/lightning_app_builder_recordpage
- https://trailhead.salesforce.com/content/learn/modules/lightning-experience-for-salesforce-classic-users/navigate-around
- https://www.pencilandpaper.io/articles/ux-pattern-analysis-enterprise-data-tables
- https://preview.pro.ant.design
