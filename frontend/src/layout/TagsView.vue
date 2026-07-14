<template>
  <div class="tags-view" @contextmenu.prevent>
    <el-scrollbar>
      <div class="tags-scroll">
        <router-link
          v-for="tag in tags.tags"
          :key="tag.fullPath"
          :to="tag.fullPath"
          custom
          v-slot="{ navigate }"
        >
          <span
            class="tag"
            :class="{ active: tag.fullPath === route.fullPath }"
            @click="navigate"
            @contextmenu.prevent.stop="openMenu(tag, $event)"
          >
            <el-icon v-if="iconOf(tag.path)" class="tag-icon">
              <component :is="iconOf(tag.path)" />
            </el-icon>
            {{ tag.title }}
            <el-icon v-if="!tag.affix" class="tag-close" @click.prevent.stop="onClose(tag.fullPath)">
              <Close />
            </el-icon>
          </span>
        </router-link>
      </div>
    </el-scrollbar>

    <ul v-if="menu.visible" class="ctx-menu" :style="{ left: menu.x + 'px', top: menu.y + 'px' }">
      <li @click="refresh">刷新</li>
      <li v-if="!menu.tag?.affix" @click="onClose(menu.tag.fullPath)">关闭</li>
      <li @click="onCloseOthers">关闭其他</li>
      <li @click="onCloseAll">关闭全部</li>
    </ul>
  </div>
</template>

<script setup>
import { reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsStore } from '@/stores/tags'
import { menuTree } from './menu'

const route = useRoute()
const router = useRouter()
const tags = useTagsStore()

// path → icon 反查表（子项继承父级 icon）
const iconMap = {}
for (const m of menuTree) {
  if (m.path) iconMap[m.path] = m.icon
  if (m.children) for (const c of m.children) iconMap[c.path] = m.icon
}
const iconOf = (path) => iconMap[path]

// 登记当前路由为标签
watch(
  () => route.fullPath,
  () => tags.addTag(route),
  { immediate: true }
)

const menu = reactive({ visible: false, x: 0, y: 0, tag: null })
function openMenu(tag, e) {
  menu.tag = tag
  menu.x = e.clientX
  menu.y = e.clientY
  menu.visible = true
}
function closeMenu() {
  menu.visible = false
}
onMounted(() => document.addEventListener('click', closeMenu))
onBeforeUnmount(() => document.removeEventListener('click', closeMenu))

function goto(target) {
  if (target && target.fullPath !== route.fullPath) router.push(target.fullPath)
}
function onClose(fullPath) {
  const next = tags.closeTag(fullPath)
  if (fullPath === route.fullPath) goto(next)
}
function onCloseOthers() {
  tags.closeOthers(menu.tag.fullPath)
  goto(menu.tag)
}
function onCloseAll() {
  const next = tags.closeAll()
  goto(next || { fullPath: '/dashboard' })
}
function refresh() {
  const p = menu.tag?.fullPath || route.fullPath
  if (p !== route.fullPath) router.push(p).then(() => router.go(0))
  else router.go(0)
}
</script>

<style scoped>
.tags-view {
  height: 40px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border);
  padding: 0 10px;
  display: flex;
  align-items: center;
}
.tags-scroll { display: flex; align-items: center; gap: 6px; padding: 6px 0; white-space: nowrap; }
.tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 28px;
  padding: 0 10px;
  font-size: 12.5px;
  color: var(--text-secondary);
  background: var(--bg-subtle);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  transition: all .15s;
  user-select: none;
}
.tag:hover { color: var(--brand); border-color: var(--border-strong); }
.tag.active {
  color: #fff;
  background: var(--brand);
  border-color: var(--brand);
}
.tag-icon { font-size: 13px; }
.tag-close {
  font-size: 12px;
  border-radius: 50%;
  padding: 1px;
  transition: background .12s;
}
.tag-close:hover { background: rgba(0, 0, 0, 0.15); }
.tag.active .tag-close:hover { background: rgba(255, 255, 255, 0.25); }

.ctx-menu {
  position: fixed;
  z-index: 3000;
  margin: 0;
  padding: 5px 0;
  list-style: none;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-pop);
  min-width: 108px;
}
.ctx-menu li {
  padding: 7px 16px;
  font-size: 13px;
  color: var(--text-body);
  cursor: pointer;
}
.ctx-menu li:hover { background: var(--bg-hover); color: var(--brand); }
</style>
