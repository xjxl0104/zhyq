# 多项目切换与数据隔离 — 设计文档

- 日期:2026-08-03
- 分支基线:ver4.2(GitHub),本地开发基线 ver4.0
- 方案定位:**轻量前端方案**,后端零改动

## 1. 背景与目标

系统中"空间与资产 → 建筑 → 项目管理"里的**项目(`biz_project`)**是数据的一级归属维度。
现状:

- 数据层:绝大多数业务表已带 `project_id` 列(楼宇/楼层/房源/租户/合同/账单/工单/会议室/电表/IoT设备/资产/巡更计划等)。
- 查询层:多数 Controller(如 `BuildingController`)已支持可选 `?projectId=` 过滤。
- 缺失:前端**没有全局"当前项目"上下文**,顶栏无切换器,`projectId` 靠各接口零散手传。

> 注意:`BaseEntity` 另有 `tenant_id`(运营租户),由 `MyMetaObjectHandler` 硬编码填 `1`,属于**另一个隔离维度**,本次**不涉及**。本设计只处理 `project_id` 这一层。

**目标**:引入前端"当前项目"全局上下文 → 顶栏可切换 → 所有业务请求自动按当前项目过滤,实现不同项目间数据隔离。当前只有一个项目,但按多项目集团场景设计,接口已预留。

## 2. 关键决策(已与用户确认)

| # | 决策点 | 选定方案 |
|---|--------|---------|
| 1 | 隔离强度 | **A 轻量前端**:前端自动带 `projectId`,后端不改,不做强制隔离 |
| 2 | 是否有"全部项目"档 | **A 否**:切换器永远选中且只选一个具体项目 |
| 3 | 默认与持久化 | **A**:选择存 `localStorage`;首次/失效时默认列表第一个 |
| 4 | 切换后已开页面 | **C**:清 TagsView 标签 + 清 keep-alive 缓存 + 路由层重载当前页 |
| 5 | projectId 注入方式 | **A**:`request.js` 请求拦截器统一注入 + 排除清单 |

testing:项目现状无自动化测试,本次走**手动验证清单**,不额外铺测试框架。

## 3. 整体架构与数据流

```
[顶栏项目切换器 ProjectSwitcher.vue]
        | 选择 projectId
        v
[Pinia store: project.js  currentProjectId]
   ├─ 持久化 localStorage: zhyq_project_id
   └─ 切换时: 清 TagsView + 清 keep-alive + /redirect 中转重载当前页
        |
[所有业务请求]
        v
[utils/request.js 请求拦截器]  --按排除清单自动附加 projectId-->
        v
[后端现有可选 projectId 参数]  --据此过滤(零改动)
```

改动清单(全部前端):

1. **新增** `frontend/src/stores/project.js` — 当前项目全局状态。
2. **改** `frontend/src/utils/request.js` — 请求拦截器注入 `projectId`。
3. **新增** `frontend/src/layout/ProjectSwitcher.vue` — 顶栏切换器,置于 `Layout.vue` 的 `nav-left`。
4. **改** `frontend/src/layout/Layout.vue` — 挂载切换器 + 登出清理项目状态。
5. **改/新增** 切换重载机制:`/redirect` 中转路由 + 清 TagsView(`stores/tags.js`)/ keep-alive。
6. **改** `frontend/src/router/index.js` — 注册 `/redirect/:path(.*)` 中转路由;应用启动/登录后初始化 projectStore。

后端零改动。

## 4. projectStore(`stores/project.js`)

职责单一:管"当前是哪个项目"。

```
state:
  currentProjectId: null    # 当前选中项目 id
  projects: []              # [{id, name, code, ...}]
  loaded: false             # 列表是否已加载

getters:
  currentProject            # 由 currentProjectId 在 projects 中查出的完整对象

actions:
  async init()              # 登录后调用,幂等
  switchTo(id)              # 切换当前项目
  reset()                   # 登出时清空
```

**`init()` 流程**:

1. `GET /building/project/list` → 写入 `projects`,置 `loaded=true`。
2. 读 `localStorage['zhyq_project_id']`。
3. 若该 id **仍存在于** `projects`(防项目被删)→ 采用;否则取 `projects[0].id`;列表为空 → `null`。
4. 回写 `localStorage['zhyq_project_id']`(为 null 时移除该 key)。

**`switchTo(id)`**:更新 `currentProjectId` + 写 localStorage → 触发清缓存重载(见 §6)。同项目重复选择直接忽略(不重载)。

**边界**:

- **零项目**:`currentProjectId=null`,切换器显示"暂无项目",引导去项目管理新建;拦截器此时不注入(见 §5)。
- **持久化 key**:`zhyq_project_id`(沿用 `zhyq_` 前缀)。
- **登出**:`Layout.vue` 的 logout 调 `reset()` 并移除 `zhyq_project_id`,避免换账号串项目。

## 5. 请求拦截器注入(`utils/request.js`)

在现有请求拦截器(加 token 处)追加注入逻辑:

```
NO_PROJECT_PREFIXES = ['/auth', '/building/project']

request.interceptors.request.use((config) => {
  # ...原 token 逻辑...
  pid = useProjectStore().currentProjectId
  excluded = NO_PROJECT_PREFIXES.some(p => config.url.startsWith(p))
  if (pid && !excluded) {
    if ((config.method || 'get') === 'get') {
      config.params = { projectId: pid, ...config.params }   # 页面已传的优先
    } else if (config.data 是普通对象 且 非 FormData) {
      config.data = { projectId: pid, ...config.data }        # 页面已传的优先
    }
  }
  return config
})
```

**护栏**:

1. **页面已传优先**:`...config.params` / `...config.data` 放在 `projectId` 之后,不覆盖页面显式传入的值(兼容楼宇页等已带参接口)。
2. **FormData 不碰**:文件上传请求跳过 body 注入。
3. **排除清单**:
   - `/auth` — 登录/登出不需要项目。
   - `/building/project` — 项目自身增删改查;否则拉项目列表会被自己过滤,自锁死。
4. 字典/全局配置类接口后端本就忽略多余参数,注入无害,**暂不进清单**;若发现异常再按前缀补入。
5. store 在拦截器内**惰性获取**(`useProjectStore()` 在函数体内调用),避免 Pinia 未初始化时的时序问题。

## 6. 切换重载机制(决策 C)

`switchTo` 更新 id 后依次:

1. **清 TagsView**:调用 `stores/tags.js` 清空标签(保留或重置到当前页)。
2. **清 keep-alive 缓存**:清空缓存组件名集合,使后台缓存页下次访问重新渲染重查。
3. **重载当前页**:跳转 `/redirect` 中转路由再跳回原路由(Vue 后台模板通用做法),强制当前页重新走一遍数据加载。**不用 `location.reload()`**,以保住登录态、避免整页白屏闪烁。

**`/redirect` 中转路由**(`router/index.js`):

```
{ path: '/redirect/:path(.*)', component: RedirectView }
# RedirectView: onMounted 读取 params.path 后 router.replace 回该路径
```

**为什么这样兜底**:77 个页面无需各自加"监听 projectId 变化"的逻辑,统一机制保证切换后一定重查,不串项目、不遗漏。

## 7. 顶栏切换器(`layout/ProjectSwitcher.vue`)

- 位置:`Layout.vue` 的 `.nav-left`,面包屑左侧。
- 形态:`el-dropdown` 或 `el-select`,显示 `currentProject.name`,下拉列出 `projects`。
- 交互:选择即调 `projectStore.switchTo(id)`。
- 零项目态:禁用并显示"暂无项目",点击引导跳 `/building/project`(项目管理页)。
- 样式:与现有 navbar 视觉一致(参考 `theme-btn`/`user` 现有类),暗色模式适配。

## 8. 应用初始化时序

- 登录成功后(或 `Layout` 挂载时,若已持有 token)调用 `projectStore.init()`。
- `init()` 完成前切换器显示加载态,避免拦截器读到 `null` 导致首屏请求不带 projectId(首屏页面若在 init 完成前发请求,会拿到全量数据;通过在 `Layout` 挂载时 `await init()` 再渲染 `<router-view>` 规避,或首屏页面数据加载晚于 init)。**采用:`Layout` 中用 `ready` ref 作渲染门闸——`onMounted` 里 `await projectStore.init()` 后置 `ready=true`,`<router-view>` 外层 `v-if="ready"`,init 期间显示轻量加载态。**

## 9. 手动验证清单

- [ ] 登录后顶栏出现切换器,默认选中列表第一个(或上次选择)。
- [ ] 切换项目后,楼宇/房源/合同/工单等列表数据随之变化,只显示当前项目数据。
- [ ] 切换后 TagsView 标签清理、当前页重载,无残留旧项目数据。
- [ ] 在某项目下新增一条数据(如楼宇),该数据 `project_id` 落为当前项目(拦截器 body 注入生效)。
- [ ] 刷新页面后仍停留在上次选中的项目(localStorage 生效)。
- [ ] 删除当前选中项目后重新 init,自动回退到列表第一个,不报错。
- [ ] 登出再登录,项目状态正确;换账号不串上一账号项目。
- [ ] 文件上传(FormData)功能正常,未被注入破坏。
- [ ] 项目列表接口 `/building/project/list` 自身未被 projectId 过滤(排除清单生效)。
- [ ] 零项目场景:切换器显示"暂无项目",不报错。

## 10. 非目标(YAGNI)

- 不做后端强制隔离 / MyBatis-Plus 项目插件。
- 不做"全部项目"汇总档(驾驶舱/大屏本次也限定在当前项目)。
- 不做默认项目跟用户档案绑定(不动后端用户表)。
- 不改 `tenant_id` 多租户逻辑。
- 不为本功能新铺自动化测试框架(遵循项目现状)。
