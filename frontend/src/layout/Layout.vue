<template>
  <el-container class="app-wrapper">
    <el-aside :width="collapsed ? '68px' : '224px'" class="sidebar">
      <div class="logo" :class="{ mini: collapsed }">
        <el-icon class="logo-icon"><OfficeBuilding /></el-icon>
        <span v-show="!collapsed" class="logo-text">澳乐智慧园区系统</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <GooeyNav :active-path="activePath">
          <el-menu :default-active="activePath" router unique-opened :collapse="collapsed"
                   :collapse-transition="false" class="side-menu">
            <MenuItem
              v-for="item in menuTree"
              :key="item.title"
              :item="item"
              @leaf="onClick"
            />
          </el-menu>
        </GooeyNav>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div class="nav-left">
          <span class="crumb">{{ currentTitle }}</span>
        </div>
        <div class="actions">
          <ProjectSwitcher @switched="onProjectSwitched" />
          <button class="screen-btn" @click="openScreen">
            <el-icon><Monitor /></el-icon><span>监控大屏</span>
          </button>
          <el-dropdown @command="onUserCmd">
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
      </el-header>
      <el-main>
        <router-view v-if="ready" v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive v-if="alive"><component :is="Component" :key="route.fullPath" /></keep-alive>
          </transition>
        </router-view>
        <div v-else class="layout-loading" v-loading="true" style="height: 60vh"></div>
        <FeedbackFab />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from './menu'
import request from '@/utils/request'
import { useProjectStore } from '@/stores/project'
import GooeyNav from './GooeyNav.vue'
import MenuItem from './MenuItem.vue'
import ProjectSwitcher from './ProjectSwitcher.vue'
import FeedbackFab from '@/views/suggestion/FeedbackFab.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const collapsed = ref(false)

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
function openScreen() {
  window.open(router.resolve('/screen').href, '_blank')
}
</script>

<style scoped>
.app-wrapper { height: 100vh; }

/* —— 侧栏:浅色高级风 —— */
.sidebar {
  background: #fff;
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 20px;
  border-bottom: 1px solid var(--border);
  overflow: hidden;
}
.logo.mini { padding: 0; justify-content: center; }
.logo-icon {
  font-size: 26px; color: var(--brand); flex-shrink: 0;
}
.logo-text {
  font-size: 18px; font-weight: 700; color: var(--text-title);
  letter-spacing: 1px; white-space: nowrap;
}
.menu-scroll { flex: 1; }
.menu-scroll :deep(.el-scrollbar__view) { min-height: 100%; }

.side-menu {
  border-right: none;
  padding: 10px 12px;
  --el-menu-item-height: 44px;
  --el-menu-sub-item-height: 40px;
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: var(--bg-hover);
  --el-menu-active-color: var(--brand);
}
.side-menu:not(.el-menu--collapse) { width: 200px; }

/* 菜单项圆角化 + 悬浮 */
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  height: 44px;
  border-radius: 9px;
  margin-bottom: 3px;
  color: var(--text-secondary);
  font-weight: 500;
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background: var(--bg-hover);
  color: var(--text-title);
}
/* 选中态:靛蓝浅底 + 主色文字图标 */
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
  color: var(--brand);
  font-weight: 600;
}
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: var(--brand); }
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-active) {
  background: transparent;
  color: #fff;
  text-shadow: 0 1px 1px rgb(49 46 129 / 24%);
}
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-active .el-icon) { color: #fff; }
.side-menu :deep(.el-menu-item:focus-visible),
.side-menu :deep(.el-sub-menu__title:focus-visible) {
  outline: 2px solid #818cf8;
  outline-offset: -2px;
}
.side-menu :deep(.el-sub-menu .el-menu) { padding-left: 0; }
.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 42px !important;
}
.side-menu :deep(.el-icon) { font-size: 17px; }

/* —— 顶栏 —— */
.navbar {
  height: 60px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(8px);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--border);
  padding: 0 22px;
}
.nav-left { display: flex; align-items: center; gap: 14px; }
.crumb { font-size: 16px; font-weight: 600; color: var(--text-title); }

.actions { display: flex; align-items: center; gap: 18px; }
.screen-btn {
  display: flex; align-items: center; gap: 6px;
  border: none; cursor: pointer;
  background: var(--el-color-primary-light-9);
  color: var(--brand);
  font-size: 13px; font-weight: 600;
  padding: 7px 14px; border-radius: 8px;
  transition: all .15s;
}
.screen-btn:hover { background: var(--el-color-primary-light-8); }
.user { display: flex; align-items: center; gap: 9px; }
.user-avatar {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-size: 14px; font-weight: 600;
}
.uname { font-size: 14px; color: var(--text-body); font-weight: 500; }

.el-main { background: var(--bg-body); padding: 0; overflow-y: auto; }

/* 路由切换微动效 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all .2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
