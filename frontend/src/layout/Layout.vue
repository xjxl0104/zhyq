<template>
  <el-container class="app-wrapper">
    <el-aside :width="collapsed ? '68px' : '224px'" class="sidebar">
      <div class="logo" :class="{ mini: collapsed }">
        <div class="logo-mark"><el-icon><OfficeBuilding /></el-icon></div>
        <span v-show="!collapsed" class="logo-text">智慧园区</span>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu :default-active="activePath" router unique-opened :collapse="collapsed"
                 :collapse-transition="false" class="side-menu">
          <template v-for="item in menuTree" :key="item.title">
            <el-menu-item v-if="!item.children" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <template #title>{{ item.title }}</template>
            </el-menu-item>
            <el-sub-menu v-else :index="item.title">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.title }}</span>
              </template>
              <el-menu-item v-for="c in item.children" :key="c.path"
                            :index="c.path" @click="onClick(c)">
                {{ c.title }}
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div class="nav-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Fold v-if="!collapsed" /><Expand v-else />
          </el-icon>
          <span class="crumb">{{ currentTitle }}</span>
        </div>
        <div class="actions">
          <el-icon class="theme-btn" @click="openAssistant" title="AI 助手">
            <MagicStick />
          </el-icon>
          <el-icon class="theme-btn" @click="theme.toggle()" :title="theme.isDark ? '切换亮色' : '切换暗色'">
            <Moon v-if="!theme.isDark" /><Sunny v-else />
          </el-icon>
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
      <TagsView />
      <el-main>
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <keep-alive><component :is="Component" :key="route.fullPath" /></keep-alive>
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from './menu'
import request from '@/utils/request'
import { useThemeStore } from '@/stores/theme'
import TagsView from './TagsView.vue'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
const collapsed = ref(false)
const activePath = computed(() => route.path)
const currentTitle = computed(() => route.meta.title || '')
const uname = ref(localStorage.getItem('zhyq_user') || '管理员')

async function onUserCmd(cmd) {
  if (cmd === 'logout') {
    try { await request.post('/auth/logout') } catch (e) { /* token 已失效也照常退 */ }
    localStorage.removeItem('zhyq_token')
    localStorage.removeItem('zhyq_user')
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
// AI 助手扩展点位:后端 /api/agent/chat 已预留,本轮仅占位入口,接 agent 时在此挂载对话面板
function openAssistant() {
  ElMessage.info('AI 助手入口已预留,功能开发中')
}
</script>

<style scoped>
.app-wrapper { height: 100vh; }

/* —— 侧栏:浅色高级风 —— */
.sidebar {
  background: #fff;
  border-right: 1px solid var(--border);
  transition: width .22s ease;
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
.logo-mark {
  width: 32px; height: 32px; flex-shrink: 0;
  border-radius: 9px;
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 18px;
  box-shadow: 0 3px 8px rgba(79, 70, 229, 0.3);
}
.logo-text {
  font-size: 18px; font-weight: 700; color: var(--text-title);
  letter-spacing: 1px; white-space: nowrap;
}
.menu-scroll { flex: 1; }

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
.collapse-btn {
  font-size: 19px; color: var(--text-secondary); cursor: pointer;
  padding: 6px; border-radius: 7px; transition: all .15s;
}
.collapse-btn:hover { background: var(--bg-hover); color: var(--brand); }
.crumb { font-size: 16px; font-weight: 600; color: var(--text-title); }

.actions { display: flex; align-items: center; gap: 18px; }
.theme-btn {
  font-size: 19px; color: var(--text-secondary); cursor: pointer;
  padding: 6px; border-radius: 7px; transition: all .15s;
}
.theme-btn:hover { background: var(--bg-hover); color: var(--brand); }
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
