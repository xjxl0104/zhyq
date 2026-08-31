<template>
  <!-- 有子项:渲染可展开的 sub-menu,并对每个子项递归 -->
  <el-sub-menu v-if="item.children && item.children.length" :index="item.title">
    <template #title>
      <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
      <span v-if="formattedIndex" class="menu-index" aria-hidden="true">{{ formattedIndex }}</span>
      <span class="menu-label">{{ item.title }}</span>
    </template>
    <MenuItem
      v-for="child in item.children"
      :key="child.path || child.title"
      :item="child"
      @leaf="$emit('leaf', $event)"
    />
  </el-sub-menu>

  <!-- 叶子项:实际路由项 -->
  <el-menu-item v-else :index="item.path" @click="$emit('leaf', item)">
    <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
    <template #title>
      <span v-if="formattedIndex" class="menu-index" aria-hidden="true">{{ formattedIndex }}</span>
      <span class="menu-label">{{ item.title }}</span>
    </template>
  </el-menu-item>
</template>

<script setup>
import { computed } from 'vue'

// 递归菜单项:支持任意层级嵌套(业务域 → 中间模块 → 叶子)。
// leaf 事件把被点击的叶子项上抛给 Layout,用于处理 target=_blank(如监控大屏)。
// topIndex 仅一级分组传入,渲染 LineSidebar 式零填充序号;子层不编号。
const props = defineProps({
  item: { type: Object, required: true },
  topIndex: { type: Number, default: null },
})
defineEmits(['leaf'])

const formattedIndex = computed(() =>
  props.topIndex === null ? '' : String(props.topIndex + 1).padStart(2, '0')
)
</script>
