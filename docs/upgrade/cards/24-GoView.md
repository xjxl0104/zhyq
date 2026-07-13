# 对标卡片：GoView（dromara/go-view）与 DataV（DataV-Team/DataV，jiaminghi）开源大屏

**资料充分度**：高：GoView 的 GitHub 仓库、官方文档 FAQ、二开源码分析文章三方交叉验证；DataV 官方文档站直连抓取失败（http 站），组件清单由 GitHub README + 文档页搜索快照双重佐证；GoViewPro 商业版能力仅官方宣传口径，已在正文标注未逐项验证。

## 定位与目标用户
两个互补的开源大屏标杆：GoView 是 Vue3 + TypeScript 的低代码数据可视化「搭建平台」，提供拖拽画布、组件配置、数据接入与发布预览，目标用户是不想手写大屏页面的前端/业务团队，属 Dromara 开源社区，另有商业版 GoViewPro。DataV（jiaminghi 版，非阿里云 DataV）是纯 Vue「大屏组件库」，提供 SVG 边框、装饰、飞线图、水位图、轮播表等科技感组件，目标用户是手写大屏页面的前端开发者，已有 React 版（DataV-React）。二者分别代表「平台搭建范式」与「组件库嵌入范式」，正好对应 zhyq /screen 的两条升级路线。

## 功能菜单树 / 模块划分
- GoView 组件体系（50+，src/packages/components 六大类）
  - Charts 图表：柱状图/横向柱状图、折线图、单/多折线面积图、饼图/环形图、水球图、进度组件（ECharts5 + VChart 双引擎）
  - Informations 信息：文字、图片、控件类（下拉选择器等联动控件）
  - Tables 列表：滚动排名列表、滚动表格
  - Decorates 小组件：边框 01~13、装饰 01~05、数字翻牌
  - Icons 图标类
  - Photos 图片类
- GoView 编辑器能力
  - 拖拽画布 + 右侧配置面板（属性/样式/数据/事件/动画/高级自定义CSS）
  - 请求配置：静态 JSON/CSV/XML、动态 API、定时刷新间隔、数据过滤器（JS 函数处理响应）
  - 事件配置：基础事件 / 高级事件 / 「新增交互」联动（控件值注入请求参数）
  - 图层与历史记录（有限步数撤销）、本地记忆（storage）
  - 主题：明/暗主题切换、主题色变更；渲染方式 svg/canvas 可选
  - 适配：等比缩放 / X 轴滚动 / Y 轴滚动多种自适应模式；预览、发布
  - 工程化：PlopJS 组件脚手架生成、组件动态注册、页面懒加载
- DataV 组件清单（@jiaminghi/data-view）
  - 全屏容器 fullScreenContainer（transform:scale 自适应）
  - SVG 边框 borderBox（10+ 款）、装饰 decoration（10+ 款）
  - 图表 charts（自研轻量图表）、动态环图 activeRingChart
  - 水位图 waterLevelPond、进度池 percentPond
  - 飞线图 flylineChart / 飞线图增强版（点位+光晕+图标+文本）
  - 锥形柱图 conicalColumnChart、胶囊柱图 capsuleChart
  - 轮播表 scrollBoard（updateRows 局部更新）、排名轮播 scrollRankingBoard
  - 数字翻牌器 digitalFlop（千分位/小数/formatter）、加载动画 loading
  - React 版：@jiaminghi/data-view-react 同组件集

## 核心业务流程编排
不适用：二者均为纯前端可视化工具，无业务流程/审批流概念。唯一近似「流程」的机制是 GoView 的联动链路：控件组件（如下拉选择器）触发事件 → 值写入动态请求参数/请求头 → 目标组件按刷新间隔重新拉数渲染，属组件间数据联动而非业务编排。

## 前端形态
【搭建范式（GoView）】经典三区编辑器：左侧组件列表（分类陈列、拖拽入画布）、中间无限画布（自由布局、图层叠放、快捷键、历史记录回退——官方提示支持的操作与步数有限）、右侧配置面板按 Tab 分为属性/样式/数据/事件/动画/高级（自定义 CSS）。配置完成后右上角一键「预览/发布」，编辑态与预览态共用同一套 JSON 驱动渲染，所见即所得。工作台底部可查看历史记录明细。明/暗双主题 + 自定义主题色，界面用 NaiveUI。纯前端 Demo：https://www.mtruning.club；带后端 Demo：http://1.117.240.165:8080/goview/#/login。
【数据接入】组件粒度的数据源配置：静态数据支持 JSON/CSV/XML；动态数据配置 API 地址 + 毫秒级「刷新间隔」实现每卡片独立轮询；响应结构不合约定时用「过滤器」写 JS 函数从 res 中提取；请求值支持 `javascript:` 表达式和 `window.route.params` 取路由参数，还预留 SQL 请求类型（需后端配套接口）。
【图表联动】依赖「控件组件 + 动态请求组件」：将下拉选择器等控件拖入画布，在其「事件→新增交互」中绑定目标组件，预览时选项变更即把最新值注入目标组件请求参数并触发重新拉数——即「筛选控件驱动多图表刷新」的下钻模式；组件基础事件亦可做 A 触发 B 的联动（经全局变量关联）。商业版 GoViewPro 更进一步：内置 Vue 在线编辑器一行代码触发联动、生命周期钩子细粒度联动、ThreeJS 加载 OBJ/GLTF 模型并与组件互动（商业版能力未逐项验证）。
【自适应】提供多种适配模式：屏幕比例与设计比例一致时等比缩放最佳；比例不定且用滤镜会模糊，官方建议改用 X/Y 轴滚动适配；渲染引擎可选 svg/canvas，默认 svg 更清晰。
【组件库范式（DataV）】config 驱动的即插即用组件：设宽高 + 传 config 即可，组件默认宽高 100% 随父容器自适应；props 无 deep 监听，更新须传新对象（或绑 key 强刷）。fullScreenContainer 按设计稿基准（如 1920×1080）用 transform:scale 随窗口 resize 缩放整屏，已知坑：系统 DPI 150% 缩放下像素布局错乱、body 需 margin:0。轮播表提供 ref.updateRows 做行级热更新不打断轮播。官方 Demo：http://datav.jiaminghi.com（需 F11 全屏观看）。GoView 因 DataV 对 Vue3 支持不佳，已基于其 MIT 协议重写了部分边框/装饰组件——说明二者组件视觉体系可平滑互通。

## 架构与功能设计要点
【GoView，MIT License】仓库：github.com/dromara/go-view 与 gitee.com/dromara/go-view（Gitee 更新最快）。技术栈 Vue3.2 + TS4.6 + Vite2.9 + NaiveUI + ECharts5 + VChart + Pinia2 + Axios + PlopJS，pnpm 管理。顶层目录：src/（源码）、types/（全局类型）、build/、plop/（组件代码生成模板）、public/、readme/、.husky/，外加 eslint/prettier/commitlint 配置。核心模块组织：src/packages/components 下按 Charts/Decorates/Icons/Informations/Photos/Tables 六类组织组件，每个组件带 config.ts（数据源必须用 dataset 键名）实现「配置即组件」；src/views/chart 是编辑器主体——ContentBox（列布局容器）、ContentCharts（左侧组件区，含拖拽起点与组件动态注册）、ContentConfigurations（右侧配置面板）。渲染架构是其精华：整张大屏是一份 JSON（组件列表 groupList + 每组件的 option/样式/请求/事件配置），运行时按 JSON 动态全局注册 Vue 组件并渲染，属性修改触发 style 变更即重渲染，编辑/预览/导入导出（FileReader 读 JSON 初始化画布）同一机制——天然的「大屏即配置文件」。扩展点：PlopJS 脚手架生成新组件骨架，按目录约定放入即出现在组件面板。分支：master 纯前端、master-fetch 带后端请求逻辑，官方后端 gitee.com/MTrun/go-view-serve（前后端解耦，纯前端可独立嵌入现有 Vue 工程作子应用）。
【DataV，MIT License】仓库：github.com/DataV-Team/DataV（9.7k star）。目录：src/（组件源码，Vue 占 86%）、lib/（按需引入产物，支持 ES module tree-shaking）、dist/（UMD 产物，script 标签直引自动全局注册）、build/、demoImg/、umdExample.html。支持全量 Vue.use(DataV) 或按组件引入；React 版独立仓库 DataV-Team/DataV-React。官方仅保障 Chrome，未兼容 IE。TODO 中列有地图组件与 TS 重构。多租户/权限等平台能力二者均不涉及（GoView 开源版无后台系统）。

## 可借鉴点 TOP5
- **[D3前端体验]** /screen 直接引入 DataV 系 SVG 边框/装饰/数字翻牌/轮播表组件（Vue3 可用 GoView 重写版或社区 datav-vue3，MIT 协议），替换手写卡片容器，低成本统一科技感视觉，轮播表用 updateRows 行级热更新展示 IoT 告警滚动
- **[D3前端体验]** /screen 自适应改为「1920×1080 设计稿 + transform:scale 等比缩放」标准方案：取宽/高缩放比较小值、transform-origin 左上、resize 防抖，并预判 DPI 150% 与超宽拼接屏留白问题；比例不定的场景参照 GoView 提供 X/Y 轴滚动降级模式
- **[D4架构设计]** 借鉴 GoView「JSON 配置驱动 + 组件动态注册」：把 /screen 各卡片抽象为 {组件名, 布局, 样式, 数据源} 的 JSON schema 统一渲染，大屏即一份可导入导出的配置文件，后续加拖拽编辑器或多屏（园区/楼宇/能耗专题屏）无需重写页面
- **[D3前端体验]** 借鉴 GoView 组件粒度数据源配置：每个卡片自带 API + 刷新间隔（毫秒）+ 过滤器函数，告警卡片 5s 轮询、能耗卡片 5min 轮询各自独立，替代 zhyq 现在整页统一拉数的做法
- **[D3前端体验]** 借鉴 GoView「控件→新增交互→注入请求参数」联动模式：在 /screen 顶部加园区/楼宇下拉控件，选中值注入各图表卡片请求参数触发联动下钻（楼宇维度看告警/能耗/租控），实现全屏一键切换视角

## AI 备注（一行）
GoView 商业版以 ai.goviewlink.com 提供 SaaS，主打 AI 相关卖点（未验证细节）——「AI 生成大屏布局/组件配色」是 zhyq /screen 可预留的嵌入位。

## 来源
- https://github.com/dromara/go-view
- https://gitee.com/dromara/go-view
- https://www.mtruning.club/
- https://www.mtruning.club/guide/start/more.html
- https://github.com/DataV-Team/DataV
- http://datav.jiaminghi.com/guide/
- https://zhuanlan.zhihu.com/p/694959516
- https://blog.csdn.net/m0_57307213/article/details/136623890
- https://github.com/DataV-Team/DataV-React/issues/14
