<template>
  <div class="app-wrapper">
    <el-container class="body-row">
      <GrainientBg class="chrome-aurora" />
      <!-- 侧边栏可收起:财务几张宽表(所有账单、应收明细登记表 28 列)在 232px 侧边栏下
           右侧列会被挤出可视区,收起后表格独占整宽。
           用 v-show 整块摘出布局流,而不是把宽度动画到 0 —— el-aside 是 flex 项,
           改 --el-aside-width 会被内容的 min-content 宽度顶住,收放两头都不干净 -->
      <!-- 收起 = 64px 图标栏,不是整块消失:宽表要横向空间时收窄,但品牌条与图标导航还在,
           视觉上不塌。折叠态 el-menu 走官方 collapse,子菜单变悬浮弹层 -->
      <el-aside :width="collapsed ? '64px' : '232px'" class="sidebar" :class="{ collapsed }">
        <div class="brand-zone">
          <img class="brand-logo" src="@/assets/brand/dipark.svg" alt="DIPARK" />
          <StrokeBrand v-show="!collapsed" />
          <button class="rail-toggle" type="button"
                  :title="collapsed ? '展开菜单' : '收起菜单（表格可用更宽）'"
                  :aria-label="collapsed ? '展开菜单' : '收起菜单'"
                  :aria-expanded="!collapsed" @click="toggleSidebar">
            <el-icon><component :is="collapsed ? 'Expand' : 'Fold'" /></el-icon>
          </button>
        </div>
        <div v-show="!collapsed" class="switcher-zone">
          <ProjectSwitcher @switched="onProjectSwitched" />
        </div>
        <el-scrollbar class="menu-scroll">
          <el-menu :default-active="activePath" router unique-opened class="side-menu"
                   :collapse="collapsed" :collapse-transition="false">
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
              <span v-show="!collapsed" class="uname">{{ uname }}</span>
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
import GrainientBg from '@/components/GrainientBg.vue'
import StrokeBrand from './StrokeBrand.vue'
import MenuItem from './MenuItem.vue'
import ProjectSwitcher from './ProjectSwitcher.vue'
import FeedbackFab from '@/views/suggestion/FeedbackFab.vue'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

// 收起状态存 localStorage:切页面/刷新后保持,不然每次进宽表页都要重按一次
const COLLAPSE_KEY = 'zhyq_sidebar_collapsed'
const collapsed = ref(localStorage.getItem(COLLAPSE_KEY) === '1')
function toggleSidebar() {
  collapsed.value = !collapsed.value
  localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
  // el-table 的固定列/横向滚动条按挂载时的宽度算,容器变宽后要让它重算
  nextTick(() => window.dispatchEvent(new Event('resize')))
}

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
  position: relative;
  height: 100%;
  background: linear-gradient(160deg, #1e1b4b 0%, #312e81 42%, #4c42d9 100%);
  /* 画框几何:侧栏宽 / 内容纸留缝 / 圆角,纸与玻璃层共用同一组数 */
  --aside-w: 232px;
  --paper-gap: 12px;
  --paper-radius: 22px;
  /* 毛玻璃配方:选中舌头与内容纸同一材质,拼接处才看不出缝 */
  --glass-bg: rgba(255, 255, 255, .84);
  --glass-filter: blur(22px) saturate(1.35);
}
/* 内容纸的玻璃层:独立于 .el-main 铺在其正下方。
   不直接把 backdrop-filter 放在 .el-main 上:它是滚动容器,滤镜会让
   position:fixed 的后代(弹窗遮罩 / 反馈悬浮钮)被锁进纸内随内容滚动 */
.body-row::after {
  content: '';
  position: absolute;
  top: var(--paper-gap);
  right: var(--paper-gap);
  bottom: var(--paper-gap);
  left: var(--aside-w);
  z-index: 0;
  border-radius: var(--paper-radius);
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-filter);
  backdrop-filter: var(--glass-filter);
  pointer-events: none;
}
@supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
  .body-row { --glass-bg: rgba(255, 255, 255, .96); }
}
@media (prefers-reduced-transparency: reduce) {
  .body-row { --glass-bg: rgba(255, 255, 255, .97); }
}
/* 极光只铺 chrome:垫在最底层,白色内容纸不透明,自然只透出侧栏与外框区域 */
.chrome-aurora {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: .75;
}
/* 暗色纱罩:钳制装饰层有效亮度,保证浅色文字在任何配色相位下可读 */
.chrome-aurora::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(16, 14, 48, .38), rgba(16, 14, 48, .18) 45%, rgba(16, 14, 48, .42));
}

/* —— 侧栏:唯一 chrome,自上而下 品牌/项目切换/菜单/用户 —— */
.sidebar {
  position: relative;
  overflow: hidden;
  transition: width .2s ease;
  z-index: 1;
  --line-text: #c5c7ea;
  --menu-hover: #ffffff;   /* 悬停提亮为白 */
  --menu-active: #c4b5fd;  /* 选中亮紫 */
  background: transparent;
  display: flex;
  flex-direction: column;
  padding: 16px 0 10px;
}
/* 收起按钮放在品牌条右侧,跟着侧边栏走,不再是贴边浮动的一小片 */
.rail-toggle {
  margin-left: auto;
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--line-text);
  cursor: pointer;
  transition: background .18s ease, color .18s ease;
}
.rail-toggle:hover { background: rgba(255, 255, 255, .12); color: var(--menu-hover); }
.rail-toggle:focus-visible { outline: 2px solid var(--menu-active); outline-offset: 1px; }
/* 折叠态:品牌条只剩按钮,居中摆放 */
.sidebar.collapsed .brand-zone { justify-content: center; padding: 6px 0 12px; }
.sidebar.collapsed .brand-logo { display: none; }
.sidebar.collapsed .rail-toggle { margin-left: 0; }
/* el-menu collapse 下隐掉自定义的序号与文字,只留图标 */
.sidebar.collapsed .side-menu :deep(.menu-index),
.sidebar.collapsed .side-menu :deep(.menu-label) { display: none; }
.sidebar.collapsed .user-zone { display: flex; justify-content: center; }

.brand-zone {
  padding: 6px 14px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.brand-logo { height: 18px; width: auto; max-width: 94px; object-fit: contain; flex-shrink: 0; }
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
  transition: color .18s ease, transform .2s ease;
}
/* hover:向右小抖动后停在 3px,离开平滑归位;白舌头选中项不参与(避免破坏通气圆角) */
.side-menu :deep(.el-menu-item:not(.is-active):hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  transform: translateX(3px);
  animation: menu-jiggle .38s ease;
}
@keyframes menu-jiggle {
  0% { transform: translateX(0); }
  38% { transform: translateX(6px); }
  66% { transform: translateX(1.5px); }
  100% { transform: translateX(3px); }
}
@media (prefers-reduced-motion: reduce) {
  .side-menu :deep(.el-menu-item:not(.is-active):hover),
  .side-menu :deep(.el-sub-menu__title:hover) {
    transform: none;
    animation: none;
  }
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
/* 选中态:毛玻璃舌头与右侧内容纸连通(PinHome 式),上下反向圆角"通气"。
   玻璃分三块各自 backdrop-filter:舌身(.tongue-glass span)+ 上下两个圆角 fillet(伪元素)。
   三块必须同为 li 的直接子级、互不嵌套:带 backdrop-filter 的元素会成为其后代的
   backdrop root,fillet 若嵌在舌身里就只能取到空背景、糊不出来 */
.side-menu :deep(.el-menu-item.is-active) {
  position: relative;
  z-index: 0;                      /* 建立层叠上下文:z-index:-1 的舌身垫在文字下、仍留在 li 内 */
  background: transparent;
  color: var(--brand);
  font-weight: 600;
  margin-right: -8px;              /* 探出菜单右内边距,贴上内容纸 */
  border-radius: 12px 0 0 12px;
}
.side-menu :deep(.el-menu-item.is-active .el-icon) { color: var(--brand); }
.side-menu :deep(.tongue-glass) { display: none; }
.side-menu :deep(.el-menu-item.is-active > .tongue-glass) {
  display: block;
  position: absolute;
  inset: 0;
  z-index: -1;
  border-radius: inherit;
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-filter);
  backdrop-filter: var(--glass-filter);
  pointer-events: none;
}
.side-menu :deep(.el-menu-item.is-active)::before,
.side-menu :deep(.el-menu-item.is-active)::after {
  content: '';
  position: absolute;
  right: 0;
  width: 14px;
  height: 14px;
  background: var(--glass-bg);
  -webkit-backdrop-filter: var(--glass-filter);
  backdrop-filter: var(--glass-filter);
  pointer-events: none;
}
/* fillet = 14px 方块挖掉一个 1/4 圆(圆心在方块贴舌头的那个角),剩下的月牙就是反向圆角 */
.side-menu :deep(.el-menu-item.is-active)::before {
  top: -14px;
  clip-path: path('M14 0 V14 H0 A14 14 0 0 0 14 0 Z');
}
.side-menu :deep(.el-menu-item.is-active)::after {
  bottom: -14px;
  clip-path: path('M14 14 V0 H0 A14 14 0 0 1 14 14 Z');
}
.side-menu :deep(.el-menu-item:focus-visible),
.side-menu :deep(.el-sub-menu__title:focus-visible) {
  outline: 2px solid rgba(255, 255, 255, .55);
  outline-offset: -2px;
}
/* 嵌套子菜单 ul 的盒子延伸到侧栏右缘。Element Plus 展开/收起动画全程会给这个 ul
   写 overflow:hidden,舌头靠 margin-right:-8px 伸出 ul 的那 8px 会在第一帧被裁掉、
   与内容纸硬生生断开;让 ul 自带 8px 右内边距,舌头就落在它的 padding 盒内,动画全程贴纸 */
.side-menu :deep(.el-sub-menu .el-menu) {
  padding-left: 0;
  padding-right: 8px;
  margin-right: -8px;
}
/* 动画期间把 EP 写在 style 属性上的 overflow:hidden 换成 clip(不建滚动容器、不撑大侧栏滚动区),
   并只在顶部放开 14px:首项舌头的上反圆角落在父级标题行内、ul 盒子之外,否则同样第一帧就消失。
   底部不放开,折叠中的内容会盖到下一组标题上 */
.side-menu :deep(.el-sub-menu .el-menu.el-collapse-transition-leave-active),
.side-menu :deep(.el-sub-menu .el-menu.el-collapse-transition-enter-active) {
  overflow: clip !important;
  overflow-clip-margin: 14px;
  clip-path: inset(-14px 0 0 0);
}
/* 放开的那个角随折叠同步淡出/淡入,否则 ul 折到 0 高时会孤零零剩一个角、展开时又先冒出一个角 */
.side-menu :deep(.el-collapse-transition-leave-active > .el-menu-item.is-active:first-child)::before,
.side-menu :deep(.el-collapse-transition-enter-active > .el-menu-item.is-active:first-child)::before {
  transition: opacity var(--el-transition-duration) ease-in-out;
}
.side-menu :deep(.el-collapse-transition-leave-active > .el-menu-item.is-active:first-child)::before,
.side-menu :deep(.el-collapse-transition-enter-from > .el-menu-item.is-active:first-child)::before {
  opacity: 0;
}
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

/* 内容"纸":毛玻璃(玻璃本体在 .body-row::after),上下右留缝,四角圆角,被 chrome 包裹;
   纸内卡片保持不透明白,只有页面底色透出氛围光 */
.el-main {
  position: relative;
  z-index: 1;
  background: transparent;
  margin: var(--paper-gap) var(--paper-gap) var(--paper-gap) 0;
  height: calc(100% - var(--paper-gap) * 2);
  border-radius: var(--paper-radius);
  padding: 0;
  overflow-x: hidden;       /* 页面级横向滚动禁用:宽表格由 el-table 自己就近滚动 */
  overflow-y: auto;
  scrollbar-gutter: stable; /* 滚动条出现/消失不再引起内容抖动 */
  isolation: isolate;
  clip-path: inset(0 round var(--paper-radius));
}

/* 路由切换微动效 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all .2s ease; }
.fade-slide-enter-from { opacity: 0; transform: translateY(8px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
