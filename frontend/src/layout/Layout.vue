<template>
  <el-container class="app-wrapper">
    <el-aside :width="collapsed ? '68px' : '224px'" class="sidebar">
      <div class="logo" :class="{ mini: collapsed }">
        <StrokeBrand :compact="collapsed" />
      </div>
      <el-scrollbar ref="menuScroll" class="menu-scroll"
                    @pointermove="menuProximity.onPointerMove" @pointerleave="menuProximity.onPointerLeave">
        <GooeyNav :active-path="activePath" :collapsed="collapsed">
          <el-menu :default-active="activePath" router unique-opened :collapse="collapsed"
                   :collapse-transition="false" class="side-menu">
            <MenuItem
              v-for="(item, i) in menuTree"
              :key="item.title"
              :item="item"
              :top-index="i"
              @leaf="onClick"
            />
          </el-menu>
        </GooeyNav>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <el-header class="navbar">
        <div ref="navbarInner" class="navbar-inner"
             @pointermove="proximity.onPointerMove" @pointerleave="proximity.onPointerLeave">
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
          <svg class="navbar-burst__filter" aria-hidden="true" focusable="false">
            <defs>
              <filter :id="burstFilterId" x="-150%" y="-300%" width="400%" height="700%"
                      color-interpolation-filters="sRGB">
                <feGaussianBlur in="SourceGraphic" stdDeviation="7" result="blur" />
                <feColorMatrix in="blur" type="matrix"
                               values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 100 -49.5" result="goo" />
                <feComposite in="SourceGraphic" in2="goo" operator="atop" />
              </filter>
            </defs>
          </svg>
          <div v-if="burst" :key="burst.seq" class="navbar-burst" :style="burstStyle" aria-hidden="true">
            <span class="navbar-burst__pulse" />
            <span v-for="p in burst.particles" :key="p.id" class="navbar-burst__particle" :style="p.style">
              <span class="navbar-burst__point" />
            </span>
          </div>
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
import { computed, ref, onMounted, onBeforeUnmount, nextTick, useId } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { menuTree } from './menu'
import request from '@/utils/request'
import { useProximity } from '@/composables/useProximity'
import { useReducedMotion } from '@/composables/useReducedMotion'
import { useProjectStore } from '@/stores/project'
import GooeyNav from './GooeyNav.vue'
import StrokeBrand from './StrokeBrand.vue'
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

// 侧栏菜单的纵向 proximity(LineSidebar 原生形态);折叠或 reduced motion 时停用
const menuScroll = ref(null)
const menuProximity = useProximity({
  container: menuScroll, itemSelector: '.el-menu-item, .el-sub-menu__title',
  axis: 'y', radius: 90, smoothing: 90,
  disabled: () => reducedMotion.value || collapsed.value,
})

// —— 监控大屏点击粒子(GooeyNav 的 burst,轻量版) ——
const BURST_COUNT = 10
const BURST_COLORS = ['#c7d2fe', '#a5b4fc', '#818cf8', '#f1b66a']
const burstFilterId = `navbar-goo-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`
const burst = ref(null)
let burstTimer
const noise = (n = 1) => n / 2 - Math.random() * n
function getXY(distance, pointIndex, totalPoints) {
  const angle = ((360 + noise(8)) / totalPoints) * pointIndex * (Math.PI / 180)
  return [distance * Math.cos(angle), distance * Math.sin(angle)]
}
function fireBurst(el) {
  if (reducedMotion.value || !navbarInner.value || !el) return
  const frame = navbarInner.value.getBoundingClientRect()
  const rect = el.getBoundingClientRect()
  const animationTime = 500
  let longest = 0
  const particles = Array.from({ length: BURST_COUNT }, (_, i) => {
    const time = animationTime * 2 + noise(500)
    const start = getXY(60, BURST_COUNT - i, BURST_COUNT)
    const end = getXY(8 + noise(6), BURST_COUNT - i, BURST_COUNT)
    const rotate = noise(10)
    longest = Math.max(longest, time)
    return {
      id: i,
      style: {
        '--start-x': `${start[0]}px`, '--start-y': `${start[1]}px`,
        '--end-x': `${end[0]}px`, '--end-y': `${end[1]}px`,
        '--time': `${time}ms`, '--scale': 1 + noise(0.2),
        '--rotate': `${(rotate > 0 ? rotate + 5 : rotate - 5) * 10}deg`,
        '--color': BURST_COLORS[Math.floor(Math.random() * BURST_COLORS.length)],
      },
    }
  })
  burst.value = {
    seq: (burst.value?.seq || 0) + 1,
    x: rect.left - frame.left, y: rect.top - frame.top,
    width: rect.width, height: rect.height,
    particles,
  }
  clearTimeout(burstTimer)
  burstTimer = window.setTimeout(() => { burst.value = null }, longest + 50)
}
const burstStyle = computed(() => burst.value ? {
  left: `${burst.value.x}px`, top: `${burst.value.y}px`,
  width: `${burst.value.width}px`, height: `${burst.value.height}px`,
  filter: `url(#${burstFilterId})`,
} : null)

onBeforeUnmount(() => clearTimeout(burstTimer))

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
function openScreen(event) {
  fireBurst(event?.currentTarget)
  window.open(router.resolve('/screen').href, '_blank')
}
</script>

<style scoped>
.app-wrapper { height: 100vh; }

/* —— 侧栏:深靛,与顶栏成套 —— */
.sidebar {
  background: linear-gradient(180deg, #1e1b4b 0%, #171438 45%, #131129 100%);
  border-right: 1px solid rgba(129, 140, 248, .2);
  display: flex;
  flex-direction: column;
}
.logo {
  height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-bottom: 1px solid rgba(129, 140, 248, .16);
  overflow: hidden;
}
.logo.mini { padding: 0 8px; justify-content: center; }
.menu-scroll { flex: 1; }
.menu-scroll :deep(.el-scrollbar__view) { min-height: 100%; }
.menu-scroll :deep(.el-scrollbar__thumb) { background: rgba(165, 180, 252, .28); }

.side-menu {
  border-right: none;
  padding: 10px 12px;
  --el-menu-item-height: 44px;
  --el-menu-sub-item-height: 40px;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #a5a3c2;
  --el-menu-hover-bg-color: rgba(255, 255, 255, .05);
  --el-menu-hover-text-color: #fff;
  --el-menu-active-color: #fff;
}
.side-menu:not(.el-menu--collapse) { width: 200px; }

/* 菜单项圆角化 + 悬浮 */
.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  position: relative;
  height: 44px;
  border-radius: 9px;
  margin-bottom: 3px;
  color: #a5a3c2;
  font-weight: 500;
}
/* LineSidebar:左缘 marker 刻度,随 proximity 伸长点亮 */
.side-menu:not(.el-menu--collapse) :deep(.el-menu-item)::before,
.side-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title)::before {
  content: '';
  position: absolute;
  left: -12px;
  top: 50%;
  width: 10px;
  height: 2px;
  border-radius: 1px;
  transform: translateY(-50%) scaleX(calc(.3 + var(--effect, 0) * .7));
  transform-origin: left center;
  background: color-mix(in srgb, #a5b4fc calc(var(--effect, 0) * 100%), rgba(165, 180, 252, .16));
}
/* 序号与标签:随 proximity 右移,--effect 自菜单项继承 */
.side-menu :deep(.menu-index) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  letter-spacing: .08em;
  color: #6d6a9e;
  margin-right: 8px;
  display: inline-block;
  transform: translateX(calc(var(--effect, 0) * 6px));
}
.side-menu :deep(.menu-label) {
  display: inline-block;
  transform: translateX(calc(var(--effect, 0) * 6px));
}
.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, .05);
  color: #fff;
}
/* 选中态(gooey 未就绪的回退):微白膜 + 白字 */
.side-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, .09);
  color: #fff;
  font-weight: 600;
}
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: #c7d2fe; }
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-active) {
  background: transparent;
  color: #e0e7ff;
}
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-active .el-icon) { color: #c7d2fe; }
.menu-scroll :deep(.gooey-nav--ready .el-menu-item:hover),
.menu-scroll :deep(.gooey-nav--ready .el-sub-menu__title:hover) {
  background: transparent;
  color: #fff;
}
/* 被白药丸压住的项:文字/图标翻转成深靛 */
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-gooey-target),
.menu-scroll :deep(.gooey-nav--ready .el-sub-menu__title.is-gooey-target),
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-gooey-target .el-icon),
.menu-scroll :deep(.gooey-nav--ready .el-sub-menu__title.is-gooey-target .el-icon),
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-gooey-burst),
.menu-scroll :deep(.gooey-nav--ready .el-sub-menu__title.is-gooey-burst),
.menu-scroll :deep(.gooey-nav--ready .el-menu-item.is-gooey-burst .el-icon),
.menu-scroll :deep(.gooey-nav--ready .el-sub-menu__title.is-gooey-burst .el-icon) {
  color: #1e1b4b;
  background: transparent;
  transition: none;
}
.menu-scroll :deep(.gooey-nav--ready .is-gooey-target .menu-index),
.menu-scroll :deep(.gooey-nav--ready .is-gooey-burst .menu-index) { color: #4f46e5; }
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

/* —— 顶栏:深靛玻璃 + 线条动效 —— */
.navbar {
  height: 60px;
  padding: 0;
  background: linear-gradient(120deg, rgba(30, 27, 75, .97), rgba(49, 46, 129, .93) 55%, rgba(67, 56, 202, .88));
  backdrop-filter: saturate(160%) blur(10px);
  border-bottom: 1px solid rgba(129, 140, 248, .35);
}
.navbar-inner {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  overflow: hidden;
}
.nav-left { display: flex; align-items: center; min-width: 0; }
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
  background: linear-gradient(90deg, #818cf8, rgba(129, 140, 248, .12));
  transform-origin: left center;
  animation: crumb-marker-pop .5s cubic-bezier(.22, 1, .36, 1) both;
}
.crumb-index {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px; font-weight: 600; letter-spacing: .1em;
  color: #a5b4fc;
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
  background: color-mix(in srgb, #a5b4fc calc(var(--effect) * 100%), rgba(199, 210, 254, .22));
  opacity: calc(.4 + var(--effect) * .6);
  transform: translateX(-50%) scaleX(calc(.45 + var(--effect) * .55));
}
.screen-btn {
  display: flex; align-items: center; gap: 6px;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, #a5b4fc calc(30% + var(--effect) * 45%), transparent);
  background: rgba(255, 255, 255, calc(.09 + var(--effect) * .07));
  color: color-mix(in srgb, #fff calc(var(--effect) * 100%), #dbe3ff);
  font-size: 13px; font-weight: 600;
  padding: 7px 14px; border-radius: 999px;
}
.user { display: flex; align-items: center; gap: 9px; cursor: pointer; }
.user-avatar {
  background: linear-gradient(135deg, #6366f1, #4f46e5);
  font-size: 14px; font-weight: 600;
  box-shadow: 0 0 0 calc(var(--effect) * 2px) rgba(165, 180, 252, .35);
}
.uname {
  font-size: 14px; font-weight: 500;
  color: color-mix(in srgb, #fff calc(var(--effect) * 100%), #c7d2fe);
}

/* 项目切换器深色化(不改组件,el-select 2.7 外壳覆盖) */
.action-slot :deep(.project-switcher .el-select__wrapper) {
  background: rgba(255, 255, 255, .09);
  box-shadow: 0 0 0 1px rgba(165, 180, 252, .35) inset;
}
.action-slot :deep(.project-switcher .el-select__wrapper.is-hovering:not(.is-focused)) {
  box-shadow: 0 0 0 1px rgba(165, 180, 252, .6) inset;
}
.action-slot :deep(.project-switcher .el-select__selected-item),
.action-slot :deep(.project-switcher .el-select__placeholder) { color: #e0e7ff; }
.action-slot :deep(.project-switcher .el-select__caret),
.action-slot :deep(.project-switcher .el-select__prefix) { color: #a5b4fc; }
.action-slot :deep(.project-empty) { color: #c7d2fe; }

/* 监控大屏点击粒子(gooey burst) */
.navbar-burst__filter { position: absolute; width: 0; height: 0; }
.navbar-burst { position: absolute; pointer-events: none; z-index: 5; }
.navbar-burst__pulse {
  position: absolute; inset: 0; border-radius: 999px;
  background: rgba(224, 231, 255, .9);
  transform: scale(0); opacity: 0;
  animation: navbar-burst-pill 450ms ease both;
}
.navbar-burst__particle, .navbar-burst__point {
  display: block; opacity: 0;
  width: 16px; height: 16px; border-radius: 100%;
  transform-origin: center;
}
.navbar-burst__particle {
  position: absolute;
  top: calc(50% - 8px); left: calc(50% - 8px);
  animation: navbar-burst-particle var(--time) ease 1 -350ms;
}
.navbar-burst__point { background: var(--color); opacity: 1; animation: navbar-burst-point var(--time) ease 1 -350ms; }
@keyframes navbar-burst-pill {
  40% { opacity: .55; }
  to { transform: scale(1.15); opacity: 0; }
}
@keyframes navbar-burst-particle {
  0% { transform: rotate(0deg) translate(var(--start-x), var(--start-y)); opacity: 1; animation-timing-function: cubic-bezier(.55, 0, 1, .45); }
  70% { transform: rotate(calc(var(--rotate) * .5)) translate(calc(var(--end-x) * 1.2), calc(var(--end-y) * 1.2)); opacity: 1; animation-timing-function: ease; }
  85% { transform: rotate(calc(var(--rotate) * .66)) translate(var(--end-x), var(--end-y)); opacity: 1; }
  100% { transform: rotate(calc(var(--rotate) * 1.2)) translate(calc(var(--end-x) * .5), calc(var(--end-y) * .5)); opacity: 1; }
}
@keyframes navbar-burst-point {
  0% { transform: scale(0); opacity: 0; animation-timing-function: cubic-bezier(.55, 0, 1, .45); }
  25% { transform: scale(calc(var(--scale) * .25)); }
  38% { opacity: 1; }
  65% { transform: scale(var(--scale)); opacity: 1; animation-timing-function: ease; }
  85% { transform: scale(var(--scale)); opacity: 1; }
  100% { transform: scale(0); opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .action-slot { transform: none; }
  .action-slot::after { transform: translateX(-50%) scaleX(.45); }
  .side-menu :deep(.menu-index),
  .side-menu :deep(.menu-label) { transform: none; }
  .side-menu:not(.el-menu--collapse) :deep(.el-menu-item)::before,
  .side-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title)::before {
    transform: translateY(-50%) scaleX(.3);
  }
  .crumb-marker { animation: none; }
  .crumb-swap-enter-active, .crumb-swap-leave-active { transition: opacity .15s ease; }
  .crumb-swap-enter-from, .crumb-swap-leave-to { transform: none; }
  .navbar-burst { display: none; }
}

.el-main { background: var(--bg-body); padding: 0; overflow-y: auto; }

/* 路由切换微动效 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all .2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
