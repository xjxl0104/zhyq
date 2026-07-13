# 对标卡片：soybean-admin 与 vue-pure-admin（Vue3 现代后台模板）

**资料充分度**：高——两者均为开源项目，特性清单、目录结构、主题/路由/标签页细节均直接来自 GitHub 仓库源码（app.d.ts、packages、views 目录）与官方文档站双重佐证；仅 pure-admin.cn 文档站部分页面 403，相关内容以搜索摘录+仓库源码交叉验证。

## 定位与目标用户
两者均为开源免费（MIT）、开箱即用的 Vue3 中后台管理系统模板，目标用户是需要快速搭建管理后台的前端团队。soybean-admin（14.6k star）主打「清新优雅、高颜值」，技术栈 Vue3+Vite+TS+Pinia+UnoCSS，提供 NaiveUI/AntDesignVue/ElementPlus 三套 UI 版本；vue-pure-admin（20.4k star）技术栈 Vue3+Vite+TS+Pinia+Element Plus+Tailwindcss，与 zhyq 完全同栈（Element Plus），并提供官方精简版（pure-admin-thin）供实际项目开发。两者都是「前端形态标杆」而非业务系统。

## 功能菜单树 / 模块划分
**soybean-admin 功能特性清单**（example 分支演示 = 首页/系统管理/功能示例/多级菜单/插件示例/用户中心/关于）
- 布局：垂直/水平/混合等多布局模式 + 滚动模式可切换，移动端自适应
- 主题：明暗主题、主题色/圆角、灰色模式、色弱模式、推荐色、主题 Tokens→CSS 变量
- 标签页：可显隐、持久化缓存、多样式模式、中键关闭、multiTab 同路径多开、fixedIndexInTab 固定标签
- 路由：Elegant Router 文件即路由（自动生成导入/声明/类型）、静态+后端动态双权限模式、keepAlive、activeMenu 详情页高亮
- 页头：面包屑（带图标）、全局搜索、多语言切换
- 水印：文本/用户名/时间水印
- 其他：i18n、403/404/500 内置页、命令行工具（git 提交/发布）、pnpm monorepo、三 UI 版本

**vue-pure-admin 功能特性清单**（完整版 views 共 26 类示例）
- 布局：左侧/顶部/混合/双栏（max 版）四种菜单布局，可隐藏侧栏只留标签页+内容区
- 主题：高度可配置 + 暗黑主题适配，配置持久化缓存
- 标签页：持久化 MultiTagsCache、KeepAlive 组件缓存、右键菜单（刷新/关闭当前/关闭其他等）、可隐藏
- 权限：RBAC（用户→角色→权限），页面级动态路由 + 按钮权限三种模式（组件/函数/指令）
- 组件封装：@pureadmin/table（Element Plus Table 二封）、@pureadmin/descriptions、schema-form、@pureadmin/utils 工具库
- 演示页：table、editor、flow-chart、vue-flow、ganttastic 甘特图、markdown、codemirror、guide 引导页、system 系统管理、monitor 监控、chatai、账户设置、结果页/异常页
- 生态：@pureadmin/cli 脚手架、Tauri/Electron 桌面版、Docker 部署、i18n、mock、配套 node+mysql 后端示例

## 核心业务流程编排
不适用——两者为前端模板项目，不含业务流程编排/审批流/SLA 等能力；最接近「流程」的内容仅是路由守卫驱动的登录→拉取用户角色→生成动态路由→渲染菜单这一前端权限初始化链路，以及 flow-chart/vue-flow 等流程图组件演示（可作为 zhyq 未来审批流可视化的前端选型参考）。

## 前端形态
这是两个项目的核心价值所在。**布局系统**：pure-admin 提供 vertical（左侧）/horizontal（顶部）/mix（混合）/double（双栏，max 版）四种布局模式运行时切换并缓存；soybean 同样多布局+滚动模式切换，且移动端自适应。**主题/暗色**：两者均内置「主题配置抽屉」，用户可实时切换暗黑模式、主题色、圆角、灰色/色弱模式（soybean）、固定页头标签页、页面切换动画等；soybean 的实现最系统——所有配置收敛为 App.Theme.ThemeSetting 类型（src/typings/app.d.ts），主题 Tokens 转 CSS 变量，生产环境持久化到 localStorage，并用 overrideThemeSettings 机制在发版时覆盖用户旧缓存。**标签页路由**：两者标签页均支持持久化缓存（刷新恢复）、KeepAlive 页面缓存、右键菜单（刷新/关闭当前/关闭其他/关闭全部）；soybean 额外支持中键关闭、同路径多标签（multiTab）、固定标签（fixedIndexInTab）。**权限路由**：均支持前端静态路由与后端动态路由双模式；pure-admin 采用 RBAC，按钮权限提供组件/函数/指令三种判断方式；soybean 用路由 meta.roles 数组控权，meta 同时驱动菜单（icon/order/hideInMenu/activeMenu 详情页反显高亮）。**组件封装**：pure-admin 将 Element Plus Table/Descriptions 二封为独立 npm 包 @pureadmin/table、@pureadmin/descriptions，另有 schema-form（JSON 描述生成表单）；soybean 内置主题配置组件、标签组件、全局搜索（页头 Ctrl+K 式菜单搜索）。公开 demo：https://pure-admin.github.io/vue-pure-admin/ 、https://elp.soybeanjs.cn （ElementPlus 版，与 zhyq 同 UI 库）、https://naive.soybeanjs.cn 。

## 架构与功能设计要点
**开源协议**：两者均 MIT（soybean 要求商用保留版权信息；pure-admin 完全免费）。
**soybean-admin 仓库组织**（github.com/soybeanjs/soybean-admin）：pnpm monorepo。顶层 `.github / .vscode / build / packages / public / src` + `pnpm-workspace.yaml / uno.config.ts / vite.config.ts / .env*`。packages 下 8 个子包实现关注点分离：`axios`（请求封装）、`alova`（备选请求策略）、`color`（主题色计算/色板）、`hooks`（组合式函数）、`materials`（布局/物料组件）、`scripts`(CLI：git 提交、发布)、`uno-preset`（UnoCSS 预设）、`utils`。src 内 `router/elegant` 目录由 Elegant Router 插件按 views 文件结构自动生成路由导入/定义/类型（RouteKey/RoutePath 联合类型），「文件即路由」，仅 component 与 meta 可手改。分支策略：main=精简核心框架，example=完整示例。扩展点：三套 UI 版本（NaiveUI/AntD/ElementPlus）证明其框架层与 UI 库解耦。
**vue-pure-admin 仓库组织**（github.com/pure-admin/vue-pure-admin）：单仓 + 外围 npm 包生态。顶层 `build / locales / mock / public / src / types` + 完整工程化配置（eslint/stylelint/commitlint/husky/多 .env/Dockerfile）。src 12 个目录：api / assets / components / config（平台配置 platform-config）/ directives（含权限指令）/ layout（含 hooks/useTag.ts 封装标签页增删切换与右键菜单）/ plugins / router / store / style / utils / views。生态化拆包：@pureadmin/table、@pureadmin/descriptions、@pureadmin/utils、@pureadmin/cli 均为独立 npm 包；完整版（学习/演示）与 pure-admin-thin 精简版（生产开发，永久同步完整版代码）双轨；另有 Tauri/Electron 桌面版与 node+mysql 后端示例。多租户：均无，非其定位。

## 可借鉴点 TOP5
- **[D3前端体验]** 引入「主题配置抽屉」：仿 soybean 把暗色模式、主题色、布局模式、固定页头、页面动画收敛为一个 ThemeSetting 类型对象 + CSS 变量，localStorage 持久化并用 override 机制发版覆盖——zhyq 已有深色大屏 /screen，可借此让主后台也支持一键暗色，视觉上与大屏统一
- **[D3前端体验]** 补齐标签页路由：zhyq 14 个一级导航跨模块操作频繁（招商→合同→账单），照抄 pure-admin 的 useTags 方案（MultiTagsCache 持久化 + KeepAlive + 右键刷新/关闭其他 + 首页固定标签），显著减少查询栏重复填写
- **[D3前端体验]** 按钮权限三模式：引入 pure-admin 的 RBAC 按钮权限（v-perms 指令 + hasPerms 函数 + Perms 组件三种用法），使合同审批、账单核销、锁房源等敏感按钮按角色显隐，弥补 zhyq 目前仅菜单级权限的粗粒度
- **[D4架构设计]** 表格二次封装：zhyq 满屏「查询栏+表格+弹窗」三段式，参照 @pureadmin/table 把 Element Plus Table 封成配置化组件（列配置、分页、loading、密度/列显隐工具栏），再配 schema-form 式查询栏，可把每个列表页代码量砍半
- **[D4架构设计]** 文件即路由 + meta 驱动菜单：接入 soybean 的 Elegant Router（支持 Element Plus 版），views 目录自动生成带类型的路由，meta 里统一管 icon/order/roles/keepAlive/activeMenu（房源详情页反显高亮左侧「房源列表」菜单），消除 zhyq 手工维护 14 个导航路由表的成本

## AI 备注（一行）
vue-pure-admin 完整版 views 中已内置 chatai 演示页（对话式 AI 页面模板），可作为 zhyq 未来嵌入园区 AI 助手的现成页面形态参考。

## 来源
- https://github.com/soybeanjs/soybean-admin
- https://github.com/pure-admin/vue-pure-admin
- https://docs.soybeanjs.cn/zh/guide/intro
- https://docs.soybeanjs.cn/zh/guide/theme/config.html
- https://docs.soybeanjs.cn/zh/guide/router/intro.html
- https://raw.githubusercontent.com/soybeanjs/soybean-admin/main/src/typings/app.d.ts
- https://pure-admin.cn/
- https://github.com/pure-admin/pure-admin-thin
- https://pure-admin.github.io/vue-pure-admin/
- https://elp.soybeanjs.cn
