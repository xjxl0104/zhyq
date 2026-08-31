<template>
  <div class="app-wrapper">
    <header class="navbar">
      <div ref="navbarInner" class="navbar-inner"
           @pointermove="proximity.onPointerMove" @pointerleave="proximity.onPointerLeave">
        <div class="brand-zone">
          <StrokeBrand />
        </div>
        <div class="nav-left">
            <span class="sr-only" aria-live="polite">{{ currentTitle }}</span>
            <transition name="crumb-swap" mode="out-in">
              <div class="crumb-block" :key="route.path">
                <span class="crumb-marker" aria-hidden="true"></span>
                <span v-if="moduleIndex" class="crumb-index" aria-hidden="true">{{ moduleIndex }}</span>
                <span class="crumb">{{ currentTitle }}</span>
              </div>
            </transition>
          </div>
          <div class="actions">
            <span class="action-slot">
              <ProjectSwitcher @switched="onProjectSwitched" />
            </span>
            <span class="action-slot">
              <button class="screen-btn" @click="openScreen">
                <el-icon><Monitor /></el-icon><span>监控大屏</span>
              </button>
            </span>
            <span class="action-slot">
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
            </span>
          </div>
      </div>
    </header>

    <el-container class="body-row">
      <el-aside width="224px" class="sidebar">
        <el-scrollbar ref="menuScroll" class="menu-scroll"
                      @pointermove="menuProximity.onPointerMove" @pointerleave="menuProximity.onPointerLeave"
                      @scroll="menuProximity.refresh">
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
      </el-aside>
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
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from './menu'
import request from '@/utils/request'
import { useProximity } from '@/composables/useProximity'
import { useReducedMotion } from '@/composables/useReducedMotion'
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

// 当前路由所属一级模块的序号(01..10),LineSidebar 式标题前缀;不在菜单里则隐藏
const moduleIndex = computed(() => {
  const inTree = node => node.path === route.path || (node.children?.some(inTree) ?? false)
  const idx = menuTree.findIndex(inTree)
  return idx >= 0 ? String(idx + 1).padStart(2, '0') : ''
})

// 右侧动作区的横向 proximity 动效(LineSidebar 的接近效果转 90°);reduced motion 时整体停用
const navbarInner = ref(null)
const reducedMotion = useReducedMotion()
const proximity = useProximity({
  container: navbarInner, itemSelector: '.action-slot',
  radius: 150, smoothing: 100,
  disabled: () => reducedMotion.value,
})

// 侧栏菜单的纵向 proximity(LineSidebar 原生形态);reduced motion 时停用
const menuScroll = ref(null)
const menuProximity = useProximity({
  container: menuScroll, itemSelector: '.el-menu-item, .el-sub-menu__title',
  axis: 'y', radius: 90, smoothing: 90,
  disabled: () => reducedMotion.value,
})

// reduced-motion 中途切换时踢一帧,让残留的 --effect 衰减归零
watch(reducedMotion, () => {
  proximity.refresh()
  menuProximity.refresh()
})

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
/* 底层氛围光:给透明玻璃侧栏/顶栏提供可透衬的内容 */
.app-wrapper {
  position: relative;
  height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(520px 420px at 8% 10%, rgba(99, 102, 241, .18), transparent 70%),
    radial-gradient(480px 540px at 3% 80%, rgba(139, 92, 246, .13), transparent 70%),
    radial-gradient(640px 420px at 45% -12%, rgba(67, 56, 202, .10), transparent 70%),
    #eef0f4;
}
.body-row { height: 100%; }

/* —— 侧栏:透明液态玻璃(浅磨砂) + LineSidebar 形态(无彩色) —— */
.sidebar {
  --line-hi: #17171c;
  --line-text: #6e6e78;
  --line-marker-rest: rgba(23, 23, 28, .16);
  padding-top: 60px;
  background: rgba(255, 255, 255, .55);
  backdrop-filter: blur(30px) saturate(160%);
  -webkit-backdrop-filter: blur(30px) saturate(160%);
  border-right: 1px solid rgba(23, 23, 28, .08);
  display: flex;
  flex-direction: column;
}
.menu-scroll { flex: 1; }
.menu-scroll :deep(.el-scrollbar__view) { min-height: 100%; }
.menu-scroll :deep(.el-scrollbar__thumb) { background: rgba(23, 23, 28, .18); }

.side-menu {
  border-right: none;
  padding: 12px 12px 12px 44px;
  --el-menu-item-height: 42px;
  --el-menu-sub-item-height: 38px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--line-text);
  --el-menu-hover-bg-color: transparent;
  --el-menu-hover-text-color: #fff;
  --el-menu-active-color: var(--line-hi);
}
.side-menu { width: 212px; }
/* 顶级分组之间:demo 的组间静态 tick 短线 */
.menu-scroll :deep(.side-menu > .el-sub-menu) { position: relative; margin-bottom: 14px; }
.menu-scroll :deep(.side-menu > .el-sub-menu:not(:last-child)::after) {
  content: '';
  position: absolute;
  bottom: -8px;
  left: -34px;
  width: 13px;
  height: 1px;
  background: var(--line-marker-rest);
}

/* 菜单项:LineSidebar 式纯文字行,无底色块 */
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  position: relative;
  height: 42px;
  border-radius: 0;
  margin-bottom: 2px;
  background: transparent;
  font-weight: 500;
  /* demo 公式:文字随接近度从静默色向 accent 渐变 */
  color: color-mix(in srgb, var(--line-hi) calc(var(--effect, 0) * 100%), var(--line-text));
}
/* LineSidebar:左缘 marker 线,随 proximity 伸长并点亮 */
.side-menu :deep(.el-menu-item)::before,
.side-menu :deep(.el-sub-menu__title)::before {
  content: '';
  position: absolute;
  left: -34px;
  top: 50%;
  width: 26px;
  height: 1.5px;
  border-radius: 1px;
  transform: translateY(-50%) scaleX(calc(.55 + var(--effect, 0) * .45));
  transform-origin: left center;
  background: color-mix(in srgb, var(--line-hi) calc(var(--effect, 0) * 100%), var(--line-marker-rest));
}
/* 序号/图标/标签:随 proximity 整体右移(demo 的 maxShift,收敛为 10px) */
.side-menu :deep(.menu-index) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  letter-spacing: .08em;
  color: color-mix(in srgb, var(--line-hi) calc(var(--effect, 0) * 100%), #74747e);
  margin-right: 8px;
  display: inline-block;
  transform: translateX(calc(var(--effect, 0) * 10px));
}
.side-menu :deep(.menu-label) {
  display: inline-block;
  transform: translateX(calc(var(--effect, 0) * 10px));
}
.side-menu :deep(.el-menu-item .el-icon),
.side-menu :deep(.el-sub-menu__title .el-icon) {
  transform: translateX(calc(var(--effect, 0) * 10px));
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) { background: transparent; }
/* 选中态:白色常亮 + 满长 marker,无底色 */
.side-menu :deep(.el-menu-item.is-active) {
  background: transparent;
  color: var(--line-hi);
  font-weight: 600;
}
.side-menu :deep(.el-menu-item.is-active .el-icon),
.side-menu :deep(.el-menu-item.is-active .menu-index) { color: var(--line-hi); }
.side-menu :deep(.el-menu-item.is-active)::before {
  transform: translateY(-50%) scaleX(1.15);
  background: var(--line-hi);
}
.side-menu :deep(.el-menu-item:focus-visible),
.side-menu :deep(.el-sub-menu__title:focus-visible) {
  outline: 2px solid #818cf8;
  outline-offset: -2px;
}
.side-menu :deep(.el-sub-menu .el-menu) { padding-left: 0; }
.side-menu :deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  padding-left: 32px !important;
}
.side-menu :deep(.el-icon) { font-size: 17px; }

/* —— 顶栏:通栏蓝紫渐变液态玻璃(内容从其下滚过,真实透衬) —— */
.navbar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  z-index: 30;
  background: linear-gradient(115deg, rgba(30, 27, 75, .82), rgba(49, 46, 129, .72) 55%, rgba(79, 70, 229, .58));
  backdrop-filter: blur(26px) saturate(180%);
  -webkit-backdrop-filter: blur(26px) saturate(180%);
  border-bottom: 1px solid rgba(129, 140, 248, .32);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, .14), 0 12px 32px rgba(20, 18, 60, .26);
}
.navbar-inner {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 20px 0 16px;
}
.brand-zone {
  width: 184px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.nav-left { flex: 1; display: flex; align-items: center; min-width: 0; }
.sr-only {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

/* 标题区:marker 线 + 等宽序号(LineSidebar 语言横置) */
.crumb-block { display: flex; align-items: center; gap: 10px; min-width: 0; }
.crumb-marker {
  width: 30px; height: 2px; border-radius: 1px; flex-shrink: 0;
  background: linear-gradient(90deg, rgba(255, 255, 255, .85), rgba(255, 255, 255, .08));
  transform-origin: left center;
  animation: crumb-marker-pop .5s cubic-bezier(.22, 1, .36, 1) both;
}
.crumb-index {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px; font-weight: 600; letter-spacing: .1em;
  color: #b3b9ea;
}
.crumb {
  font-size: 16px; font-weight: 600; color: #fff;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
@keyframes crumb-marker-pop { from { transform: scaleX(.55); } }
.crumb-swap-enter-active { transition: opacity .26s ease, transform .26s ease; }
.crumb-swap-leave-active { transition: opacity .16s ease, transform .16s ease; }
.crumb-swap-enter-from { opacity: 0; transform: translateY(7px); }
.crumb-swap-leave-to { opacity: 0; transform: translateY(-6px); }

/* 动作区:每项一个 slot,--effect 由 useProximity 按指针 X 距离驱动 */
.actions { display: flex; align-items: center; gap: 6px; }
.action-slot {
  --effect: 0;
  position: relative;
  display: flex; align-items: center;
  padding: 0 9px;
  transform: translateY(calc(var(--effect) * -2.5px));
}
.action-slot::after {
  content: '';
  position: absolute;
  left: 50%; bottom: 4px;
  width: min(60%, 46px); height: 2px; border-radius: 1px;
  background: color-mix(in srgb, #fff calc(var(--effect) * 100%), rgba(255, 255, 255, .2));
  opacity: calc(.4 + var(--effect) * .6);
  transform: translateX(-50%) scaleX(calc(.45 + var(--effect) * .55));
}
.screen-btn {
  display: flex; align-items: center; gap: 6px;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, #fff calc(18% + var(--effect) * 30%), transparent);
  background: rgba(255, 255, 255, calc(.08 + var(--effect) * .06));
  color: color-mix(in srgb, #fff calc(var(--effect) * 100%), #d6d6de);
  font-size: 13px; font-weight: 600;
  padding: 7px 14px; border-radius: 999px;
}
.user { display: flex; align-items: center; gap: 9px; cursor: pointer; }
.user-avatar {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-size: 14px; font-weight: 600;
  box-shadow: 0 0 0 calc(var(--effect) * 2px) rgba(255, 255, 255, .28);
}
.uname {
  font-size: 14px; font-weight: 500;
  color: color-mix(in srgb, #fff calc(var(--effect) * 100%), #c7d2fe);
}

/* 项目切换器玻璃化(不改组件,el-select 外壳覆盖) */
.action-slot :deep(.project-switcher .el-select__wrapper) {
  background: rgba(255, 255, 255, .08);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .22) inset;
}
.action-slot :deep(.project-switcher .el-select__wrapper.is-hovering:not(.is-focused)) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, .38) inset;
}
.action-slot :deep(.project-switcher .el-select__selected-item),
.action-slot :deep(.project-switcher .el-select__placeholder) { color: #e8e8ee; }
.action-slot :deep(.project-switcher .el-select__caret),
.action-slot :deep(.project-switcher .el-select__prefix) { color: #a2a2ae; }
.action-slot :deep(.project-empty) { color: #b9b9c4; }

@media (prefers-reduced-motion: reduce) {
  .action-slot { transform: none; }
  .action-slot::after { transform: translateX(-50%) scaleX(.45); }
  .side-menu :deep(.menu-index),
  .side-menu :deep(.menu-label),
  .side-menu :deep(.el-menu-item .el-icon),
  .side-menu :deep(.el-sub-menu__title .el-icon) { transform: none; }
  .side-menu :deep(.el-menu-item)::before,
  .side-menu :deep(.el-sub-menu__title)::before {
    transform: translateY(-50%) scaleX(.55);
  }
  .crumb-marker { animation: none; }
  .crumb-swap-enter-active, .crumb-swap-leave-active { transition: opacity .15s ease; }
  .crumb-swap-enter-from, .crumb-swap-leave-to { transform: none; }
}

/* 内容区从液态玻璃顶栏底下滚过:滚动容器上探到 y=0,顶部 60px 由 padding 让位 */
.el-main { background: var(--bg-body); padding: 60px 0 0; overflow-y: auto; height: 100%; }

/* 路由切换微动效 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all .2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
