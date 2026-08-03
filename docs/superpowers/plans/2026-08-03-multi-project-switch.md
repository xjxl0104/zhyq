# 多项目切换与数据隔离 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 顶栏加项目切换器,前端维护"当前项目"全局状态,所有业务请求自动按当前项目过滤,实现项目间数据隔离。

**Architecture:** 纯前端轻量方案,后端零改动。Pinia store 持有 `currentProjectId`(持久化 localStorage);axios 请求拦截器按排除清单自动注入 `projectId`;切换项目时通过 keep-alive 区域 `v-if` 拨断重挂 + 清 TagsView 标签,强制当前页与所有缓存页重新加载。

**Tech Stack:** Vue 3.4 · Pinia · Vue Router 4 · Element Plus 2.7 · Axios · Vite 5(pnpm 无锁,用 `npm`/`pnpm` 均可;开发跑 `npm run dev` 于 `frontend/`)。

## Global Constraints

- 后端**零改动**;仅改/增 `frontend/src/` 下文件。
- localStorage key 沿用 `zhyq_` 前缀:当前项目用 `zhyq_project_id`。
- 排除清单前缀:`/auth`、`/building/project`(项目列表接口自身不可被 projectId 过滤)。
- 页面已显式传入的 `projectId` 优先,拦截器不得覆盖。
- FormData(文件上传)请求不注入 body。
- 项目现状**无自动化测试**;每个 Task 以**手动验证**收尾(启动 `npm run dev`,浏览器 + Network 面板核对),不引入测试框架。
- 提交信息用中文 conventional commits(参考现有 git log:`feat(xxx): ...`)。
- 关键共享文件(`router/index.js`、`Layout.vue`、`request.js`)由主脑独占改,勿并行冲突。

---

## File Structure

- `frontend/src/stores/project.js` — **新增**。当前项目全局状态(id/列表/init/switchTo/reset)。
- `frontend/src/utils/request.js` — **改**。请求拦截器注入 projectId。
- `frontend/src/layout/ProjectSwitcher.vue` — **新增**。顶栏切换器组件。
- `frontend/src/layout/Layout.vue` — **改**。挂载切换器、init 门闸、切换重载(响应 `@switched` 事件)、登出清理。
- `frontend/src/api/building.js` — 已有 `projectApi.list()`,复用,不改。

---

### Task 1: projectStore 全局状态

**Files:**
- Create: `frontend/src/stores/project.js`

**Interfaces:**
- Consumes: `projectApi.list` from `@/api/building`(已存在:`list: () => request.get('/building/project/list')`,返回项目数组)。
- Produces(供后续 Task 使用的精确签名):
  - `useProjectStore()` — Pinia store(`defineStore('project', ...)`)。
  - state:`currentProjectId: Ref<number|null>`、`projects: Ref<Array>`、`loaded: Ref<boolean>`。
  - getter/computed:`currentProject: ComputedRef<object|null>`。
  - `async init(): Promise<void>` — 幂等,拉列表并确定当前项目。
  - `switchTo(id: number): boolean` — 更新+持久化;同 id 返回 `false`(表示未切换),否则 `true`。
  - `reset(): void` — 清空状态并移除 localStorage。
  - 导出常量 `PROJECT_STORAGE_KEY = 'zhyq_project_id'`。

- [ ] **Step 1: 创建 store 文件**

```js
// frontend/src/stores/project.js
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { projectApi } from '@/api/building'

export const PROJECT_STORAGE_KEY = 'zhyq_project_id'

export const useProjectStore = defineStore('project', () => {
  const currentProjectId = ref(null)
  const projects = ref([])
  const loaded = ref(false)

  const currentProject = computed(
    () => projects.value.find((p) => p.id === currentProjectId.value) || null
  )

  async function init() {
    const list = (await projectApi.list()) || []
    projects.value = Array.isArray(list) ? list : []
    loaded.value = true
    // 确定当前项目:优先 localStorage,校验仍在列表内,否则取第一个
    const savedRaw = localStorage.getItem(PROJECT_STORAGE_KEY)
    const saved = savedRaw != null ? Number(savedRaw) : null
    const exists = saved != null && projects.value.some((p) => p.id === saved)
    if (exists) {
      currentProjectId.value = saved
    } else {
      currentProjectId.value = projects.value.length ? projects.value[0].id : null
    }
    persist()
  }

  function switchTo(id) {
    if (id === currentProjectId.value) return false
    currentProjectId.value = id
    persist()
    return true
  }

  function reset() {
    currentProjectId.value = null
    projects.value = []
    loaded.value = false
    localStorage.removeItem(PROJECT_STORAGE_KEY)
  }

  function persist() {
    if (currentProjectId.value == null) localStorage.removeItem(PROJECT_STORAGE_KEY)
    else localStorage.setItem(PROJECT_STORAGE_KEY, String(currentProjectId.value))
  }

  return { currentProjectId, projects, loaded, currentProject, init, switchTo, reset }
})
```

- [ ] **Step 2: 手动验证(浏览器控制台)**

启动 `cd frontend && npm run dev`,登录后在浏览器控制台执行:

```js
// 需在应用已加载 Pinia 的上下文;或临时在 Layout 里 window.__ps = useProjectStore()
// 简易验证:确认 /building/project/list 有返回、localStorage 写入
localStorage.getItem('zhyq_project_id')  // 首次 null,init 后应为某 id
```

Expected: `init()` 后 `currentProjectId` 为列表首个项目 id,`localStorage['zhyq_project_id']` 被写入。此步在 Task 4(Layout 接入 init)后可端到端复验,当前仅确认文件无语法错误、`npm run dev` 能编译通过。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/stores/project.js
git commit -m "feat(project): 新增当前项目全局 store(持久化 localStorage)"
```

---

### Task 2: 请求拦截器注入 projectId

**Files:**
- Modify: `frontend/src/utils/request.js`(现有请求拦截器 `request.interceptors.request.use` 内追加)

**Interfaces:**
- Consumes: `useProjectStore` from `@/stores/project`(Task 1)。**惰性调用**——在拦截器函数体内 `useProjectStore()`,避免 Pinia 未就绪的时序问题。
- Produces: 无新导出;副作用是所有非排除请求自动带 `projectId`。

- [ ] **Step 1: 改写请求拦截器**

将现有:

```js
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('zhyq_token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})
```

替换为:

```js
import { useProjectStore } from '@/stores/project'  // 顶部 import 区新增

const NO_PROJECT_PREFIXES = ['/auth', '/building/project']

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('zhyq_token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const url = config.url || ''
  const excluded = NO_PROJECT_PREFIXES.some((p) => url.startsWith(p))
  if (!excluded) {
    const pid = useProjectStore().currentProjectId
    if (pid != null) {
      const method = (config.method || 'get').toLowerCase()
      if (method === 'get') {
        config.params = { projectId: pid, ...config.params }
      } else if (
        config.data &&
        typeof config.data === 'object' &&
        !(config.data instanceof FormData)
      ) {
        config.data = { projectId: pid, ...config.data }
      }
    }
  }
  return config
})
```

- [ ] **Step 2: 手动验证**

`npm run dev`,登录后打开任意业务列表页(如"建筑管理" `/building/building`),看 Network:

- 该页 `/building/building/page` 请求 query 应带 `projectId=<当前项目>`。
- 登录请求 `/auth/login`、项目列表 `/building/project/list` **不带** `projectId`。
- 页面已显式传 projectId 的接口不被覆盖(值仍为页面传入的)。

Expected: 业务接口带 projectId,排除清单接口不带。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/utils/request.js
git commit -m "feat(project): 请求拦截器按排除清单自动注入 projectId"
```

---

### Task 3: 顶栏切换器组件 ProjectSwitcher.vue

**Files:**
- Create: `frontend/src/layout/ProjectSwitcher.vue`

**Interfaces:**
- Consumes: `useProjectStore`(Task 1)的 `projects`、`currentProjectId`、`currentProject`、`switchTo`。
- Produces: 默认导出 SFC 组件 `<ProjectSwitcher />`,无 props。切换成功(`switchTo` 返回 true)时向父级发出 `emit('switched', id)`,由 Layout 决定重载(Task 4)。

- [ ] **Step 1: 创建组件**

```vue
<!-- frontend/src/layout/ProjectSwitcher.vue -->
<template>
  <el-select
    v-if="store.projects.length"
    :model-value="store.currentProjectId"
    class="project-switcher"
    size="default"
    placeholder="选择项目"
    @change="onChange"
  >
    <template #prefix><el-icon><OfficeBuilding /></el-icon></template>
    <el-option
      v-for="p in store.projects"
      :key="p.id"
      :label="p.name"
      :value="p.id"
    />
  </el-select>
  <span v-else class="project-empty" @click="goCreate">暂无项目</span>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'

const store = useProjectStore()
const router = useRouter()
const emit = defineEmits(['switched'])

function onChange(id) {
  if (store.switchTo(id)) emit('switched', id)
}
function goCreate() {
  router.push('/building/project')
}
</script>

<style scoped>
.project-switcher { width: 180px; margin-right: 12px; }
.project-empty { color: var(--el-text-color-secondary); cursor: pointer; margin-right: 12px; }
</style>
```

- [ ] **Step 2: 手动验证(接入 Layout 后复验)**

组件此步单独无法挂载展示,随 Task 4 一起在页面顶栏核对。当前仅确认 `npm run dev` 编译通过、无语法/引用错误。

- [ ] **Step 3: 提交**

```bash
git add frontend/src/layout/ProjectSwitcher.vue
git commit -m "feat(project): 顶栏项目切换器组件"
```

---

### Task 4: Layout 接入(挂载 + init 门闸 + 切换重载 + 登出清理)

**Files:**
- Modify: `frontend/src/layout/Layout.vue`

**Interfaces:**
- Consumes: `<ProjectSwitcher>`(Task 3,含 `@switched` 事件)、`useProjectStore`(Task 1)、`useTagsStore`(现有 `@/stores/tags`,含 `closeAll()`)。
- Produces: 无对外导出。行为:首屏 `await store.init()` 后再渲染子路由;切换项目时清标签 + 拨断 keep-alive 重挂当前页;登出时 `store.reset()`。

**背景(实现者必读):** `Layout.vue` 现有 `<keep-alive>` **无 `:include` 列表**,按 `route.fullPath` 缓存所有页面。因此单纯路由跳转无法刷新缓存页——必须用 `v-if` 拨断整个 keep-alive 区域(卸载 → nextTick → 重挂),一次性清空所有缓存实例并重挂当前页。这满足决策 C(清缓存 + 重载当前页,不整页 reload)。

- [ ] **Step 1: 顶栏挂载切换器**

在模板 `.nav-left` 内,面包屑前加入切换器:

```html
<div class="nav-left">
  <ProjectSwitcher @switched="onProjectSwitched" />
  <span class="crumb">{{ currentTitle }}</span>
</div>
```

- [ ] **Step 2: keep-alive 区域加 v-if 门闸 + init 门闸**

将模板中 `<el-main>` 段改为:

```html
<el-main>
  <router-view v-if="ready" v-slot="{ Component }">
    <transition name="fade-slide" mode="out-in">
      <keep-alive v-if="alive">
        <component :is="Component" :key="route.fullPath" />
      </keep-alive>
    </transition>
  </router-view>
  <div v-else class="layout-loading" v-loading="true" style="height: 60vh"></div>
</el-main>
```

- [ ] **Step 3: script 接入 store、门闸、重载、登出清理**

在 `<script setup>` 中新增 import 与逻辑(与现有代码合并):

```js
import { computed, ref, onMounted, nextTick } from 'vue'
import ProjectSwitcher from './ProjectSwitcher.vue'
import { useProjectStore } from '@/stores/project'
import { useTagsStore } from '@/stores/tags'

const projectStore = useProjectStore()
const tagsStore = useTagsStore()

const ready = ref(false)   // init 门闸
const alive = ref(true)    // keep-alive 拨断开关

onMounted(async () => {
  try {
    await projectStore.init()
  } catch (e) {
    // 项目列表拉取失败不应卡死整个后台;放行渲染,切换器显示"暂无项目"
    console.error('项目初始化失败', e)
  } finally {
    ready.value = true
  }
})

// 切换项目:清标签 + 拨断 keep-alive 重挂当前页
async function onProjectSwitched() {
  tagsStore.closeAll()          // 清除非固定标签(保留 affix 首页)
  alive.value = false           // 卸载 keep-alive 区,丢弃所有缓存实例
  await nextTick()
  alive.value = true            // 重挂,当前页重新执行数据加载
}
```

- [ ] **Step 4: 登出时清理项目状态**

在现有 `onUserCmd` 的 logout 分支,清 token 后加:

```js
projectStore.reset()
```

（放在 `localStorage.removeItem('zhyq_token')` 附近,`router.push('/login')` 之前。）

- [ ] **Step 5: 手动验证(端到端主验收)**

`npm run dev`,完整走一遍:

1. 登录 → 顶栏出现项目切换器,默认选中列表首个(或上次选择)。
2. 打开"建筑管理" → Network 里 `page` 请求带 `projectId`;数据为当前项目。
3. 顶栏切到另一项目(需先在"项目管理"建第二个项目用于对比)→ 标签清理、当前页重载、数据变为新项目。
4. 刷新浏览器 → 仍停留在上次选中项目(localStorage 生效)。
5. 登出再登录 → 项目状态正确重建。

Expected: 全部通过,切换后无旧项目数据残留。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/layout/Layout.vue
git commit -m "feat(project): Layout 接入切换器+init门闸+切换重载+登出清理"
```

---

### Task 5: 边界场景验收(无代码改动,除非发现缺陷)

**Files:** 无(纯验收;若发现缺陷,回到对应 Task 修复并重新提交)

**Interfaces:** 无。

本 Task 逐条核对 spec §9 中 Task 4 happy-path 未覆盖的边界。每条在浏览器实测。

- [ ] **Step 1: 删除当前项目后回退**

在"项目管理"删掉当前选中的项目 → 登出再登录(触发 `init()`)。
Expected: `init()` 发现 localStorage 里的 id 已不在列表,自动回退到列表首个,不报错。

- [ ] **Step 2: 零项目场景**

(临时)让项目列表为空(或新库首次)→ 登录。
Expected: 切换器显示"暂无项目",点击跳 `/building/project`;业务请求不带 projectId(拦截器 `pid == null` 分支);后台不白屏(init 门闸 `finally` 放行)。

- [ ] **Step 3: 文件上传不被破坏**

在任一带附件上传的页面(如合同新增)上传文件。
Expected: FormData 请求正常,未被注入 body,上传成功。

- [ ] **Step 4: 新增数据归属当前项目**

在当前项目下新增一条楼宇(填必填项,不手填 projectId)→ 提交后查数据库或列表。
Expected: 该记录 `project_id` = 当前项目(拦截器 body 注入生效)。若后端该新增接口未映射 projectId 字段则不落库——记录此情况反馈,属后端字段问题不在本次范围。

- [ ] **Step 5: 排除清单接口未被污染**

Network 核对:`/building/project/list`、`/auth/*` 全程不带 `projectId`。
Expected: 通过。

- [ ] **Step 6: 收尾提交(仅当前面步骤产生修复)**

```bash
# 若无代码改动则跳过;有修复则:
git add -A && git commit -m "fix(project): 修复多项目切换边界问题"
```

---

## 附:执行注意

- 本地基线 ver4.0,线上 ver4.2。执行前确认 `Layout.vue`/`request.js`/`api/building.js` 与 ver4.2 无冲突(若已切到 ver4.2,以实际文件为准,接口签名一致即可套用本计划)。
- 全程 `mvn` 不涉及(后端零改动);前端在 `frontend/` 跑 `npm run dev` 验证。







