<template>
  <div class="app-wrapper">
    <el-container class="body-row">
      <el-aside width="232px" class="sidebar">
        <div class="brand-zone">
          <img class="brand-logo" src="@/assets/brand/dipark.svg" alt="DIPARK" />
          <StrokeBrand />
        </div>
        <div class="switcher-zone">
          <ProjectSwitcher @switched="onProjectSwitched" />
        </div>
        <el-scrollbar class="menu-scroll">
          <el-menu :default-active="activePath" router unique-opened class="side-menu">
            <MenuItem
              v-for="(item, i) in menuTree"
              :key="item.title"
              :item="item"
              :top-index="i"
              @leaf="onClick"
            />
          </el-menu>
        </el-scrollbar>
        <div class="user-zone">
          <el-dropdown @command="onUserCmd" trigger="click">
            <div class="user">
              <el-avatar :size="32" class="user-avatar">{{ uname.charAt(0) }}</el-avatar>
              <span class="uname">{{ uname }}</span>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-aside>
      <el-main>
        <span class="sr-only" aria-live="polite">{{ currentTitle }}</span>
        <router-view v-if="ready" v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive v-if="alive"><component :is="Component" :key="route.fullPath" /></keep-alive>
          </transition>
        </router-view>
        <div v-else class="layout-loading" v-loading="true" style="height: 60vh"></div>
        <FeedbackFab />
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from './menu'
import request from '@/utils/request'
import { useProjectStore } from '@/stores/project'
import StrokeBrand from './StrokeBrand.vue'
import MenuItem from './MenuItem.vue'
import ProjectSwitcher from './ProjectSwitcher.vue'
import FeedbackFab from '@/views/suggestion/FeedbackFab.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const ready = ref(false) // init 门闸
const alive = ref(true)  // keep-alive 拨断开关

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

// 切换项目：拨断 keep-alive，重挂当前页并刷新项目上下文数据。
async function onProjectSwitched() {
  alive.value = false
  await nextTick()
  alive.value = true
}
const activePath = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')
const uname = ref(localStorage.getItem('zhyq_user') || '管理员')


async function onUserCmd(cmd) {
  if (cmd === 'logout') {
    try { await request.post('/auth/logout') } catch (e) { /* token 已失效也照常退 */ }
    localStorage.removeItem('zhyq_token')
    localStorage.removeItem('zhyq_user')
    projectStore.reset()
    router.push('/login')
  }
}

function onClick(c) {
  if (c.target === '_blank') {
    window.open(router.resolve(c.path).href, '_blank')
  }
}
</script>

<style scoped>
/* 底层氛围光:给透明玻璃侧栏/顶栏提供可透衬的内容 */
.app-wrapper {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-body);
}
/* chrome:原顶栏的蓝紫渐变铺满整个画框,白色内容纸包裹嵌入 */
.body-row {
  height: 100%;
  background: linear-gradient(160deg, #1e1b4b 0%, #312e81 42%, #4c42d9 100%);
}

/* —— 侧栏:唯一 chrome,自上而下 品牌/项目切换/菜单/用户 —— */
.sidebar {
  --line-text: #c5c7ea;
  --menu-hover: #ffffff;   /* 悬停提亮为白 */
  --menu-active: #c4b5fd;  /* 选中亮紫 */
  background: transparent;
  display: flex;
  flex-direction: column;
  padding: 16px 0 10px;
}
.brand-zone {
  padding: 6px 16px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.brand-logo { height: 20px; width: auto; max-width: 104px; object-fit: contain; flex-shrink: 0; }
.brand-zone :deep(.stroke-brand) { flex: 1; min-width: 0; }
.switcher-zone { padding: 0 14px 12px; }
.switcher-zone :deep(.project-switcher) { width: 100%; }
.user-zone {
  padding: 10px 12px 4px;
  border-top: 1px solid rgba(255, 255, 255, .09);
}
.user {
  display: flex; align-items: center; gap: 10px;
  cursor: pointer;
  padding: 8px 10px;
  border-radius: 10px;
  transition: background .18s ease;
}
.user:hover { background: rgba(255, 255, 255, .07); }
.user-avatar {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-size: 14px; font-weight: 600;
  flex-shrink: 0;
}
.uname { font-size: 14px; font-weight: 500; color: #e0e2ff; }
.menu-scroll { flex: 1; position: relative; z-index: 1; }
.menu-scroll :deep(.el-scrollbar__view) { min-height: 100%; }
.menu-scroll :deep(.el-scrollbar__thumb) { background: rgba(255, 255, 255, .22); }

.side-menu {
  border-right: none;
  width: 100%;
  padding: 12px 8px;
  --el-menu-item-height: 42px;
  --el-menu-sub-item-height: 38px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--line-text);
  --el-menu-hover-bg-color: transparent;
  --el-menu-hover-text-color: var(--menu-hover);
  --el-menu-active-color: var(--menu-active);
}
.menu-scroll :deep(.side-menu > .el-sub-menu) { margin-bottom: 8px; }

/* 菜单项:纯文字行,reactbits 式颜色 hover(蓝紫)/选中(深紫) */
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 42px;
  border-radius: 8px;
  margin-bottom: 2px;
  background: transparent;
  font-weight: 500;
  color: var(--line-text);
  transition: color .18s ease;
}
.side-menu :deep(.menu-index) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  letter-spacing: .08em;
  color: inherit;
  opacity: .72;
  margin-right: 8px;
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background: transparent;
  color: var(--menu-hover);
}
/* 选中态:白色舌头与右侧内容纸连通(PinHome 式),上下反向圆角"通气" */
.side-menu :deep(.el-menu-item.is-active) {
  position: relative;
  background: #fff;
  color: var(--brand);
  font-weight: 600;
  margin-right: -8px;              /* 探出菜单右内边距,贴上白纸 */
  border-radius: 12px 0 0 12px;
}
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: var(--brand); }
.side-menu :deep(.el-menu-item.is-active)::before,
.side-menu :deep(.el-menu-item.is-active)::after {
  content: '';
  position: absolute;
  right: 0;
  width: 14px;
  height: 14px;
  pointer-events: none;
}
.side-menu :deep(.el-menu-item.is-active)::before {
  top: -14px;
  background: radial-gradient(circle at 0 0, transparent 13.5px, #fff 14px);
}
.side-menu :deep(.el-menu-item.is-active)::after {
  bottom: -14px;
  background: radial-gradient(circle at 0 100%, transparent 13.5px, #fff 14px);
}
.side-menu :deep(.el-menu-item:focus-visible),
.side-menu :deep(.el-sub-menu__title:focus-visible) {
  outline: 2px solid rgba(255, 255, 255, .55);
  outline-offset: -2px;
}
.side-menu :deep(.el-sub-menu .el-menu) { padding-left: 0; }
.side-menu :deep(.el-sub-menu .el-menu-item) { min-width: auto; }
.side-menu :deep(.el-icon) { font-size: 17px; }

.sr-only {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* 项目切换器玻璃化(不改组件,el-select 外壳覆盖) */
.switcher-zone :deep(.project-switcher .el-select__wrapper) {
  background: rgba(255, 255, 255, .08);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .22) inset;
}
.switcher-zone :deep(.project-switcher .el-select__wrapper.is-hovering:not(.is-focused)) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .38) inset;
}
.switcher-zone :deep(.project-switcher .el-select__selected-item),
.switcher-zone :deep(.project-switcher .el-select__placeholder) { color: #e8e8ee; }
.switcher-zone :deep(.project-switcher .el-select__caret),
.switcher-zone :deep(.project-switcher .el-select__prefix) { color: #c3c8f5; }
.switcher-zone :deep(.project-empty) { color: #c3c8f5; }
.switcher-zone :deep(.project-empty .el-icon) { font-size: 16px; }

/* 白色内容"纸":上下右留缝,四角圆角,被 chrome 包裹 */
.el-main {
  background: #fff;
  margin: 12px 12px 12px 0;
  height: calc(100% - 24px);
  border-radius: 22px;
  padding: 0;
  overflow-y: auto;
}

/* 路由切换微动效 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all .2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
