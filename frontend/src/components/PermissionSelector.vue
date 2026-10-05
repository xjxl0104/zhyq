<template>
  <div class="permission-selector">
    <div class="permission-tools">
      <el-input v-model="keyword" placeholder="搜索目录、页面或操作" clearable aria-label="搜索权限" />
      <span class="selection-count">已选 {{ selectedCount }} / {{ allIds.length }} 项</span>
      <el-button link type="primary" @click="selectAll">全部权限</el-button>
      <el-button link @click="clearAll">清空</el-button>
    </div>
    <div class="permission-body">
      <nav class="permission-categories" aria-label="权限业务目录">
        <button v-for="(module, index) in tree" :key="module.id" type="button"
                :class="{ active: activeIndex === index && !keyword }"
                :aria-pressed="activeIndex === index && !keyword" @click="chooseModule(index)">
          <span>{{ module.name }}</span>
          <small>{{ countSelected(module) }}/{{ permissionIds([module]).length }}</small>
        </button>
      </nav>
      <section class="permission-content">
        <div class="module-tools">
          <strong>{{ keyword ? '搜索结果' : tree[activeIndex]?.name }}</strong>
          <div>
            <el-button link type="primary" @click="selectVisible">{{ keyword ? '选中结果' : '本组全选' }}</el-button>
            <el-button link @click="clearVisible">{{ keyword ? '清空结果' : '清空本组' }}</el-button>
          </div>
        </div>
        <p class="permission-hint">勾选目录或页面可批量授权，展开后可逐项调整。</p>
        <div class="permission-scroll">
          <el-tree v-if="visibleIds.length" ref="treeRef" :data="visibleTree" node-key="id" show-checkbox
                   :default-expanded-keys="expandedKeys" :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
                   @check="onCheck">
            <template #default="{ data }">
              <span class="permission-label">{{ data.name }}</span>
              <span v-if="!data.kind" class="permission-kind">{{ countSelected(data) }}/{{ permissionIds([data]).length }}</span>
              <el-tag v-if="lockedIds.includes(data.id)" size="small" type="info">默认</el-tag>
              <el-tag v-else-if="inheritedIds.includes(data.id)" size="small" type="success">角色已授予</el-tag>
              <span v-else-if="data.kind" class="permission-kind">{{ data.kind === 'page' ? '页面' : '操作' }}</span>
            </template>
          </el-tree>
          <el-empty v-else :description="keyword ? '没有匹配的权限' : '本组暂无单独配置的权限'" :image-size="60" />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { buildPermissionTree, filterPermission, normalizePermissionSelection, permissionIds } from '@/utils/permissionTree'

const props = defineProps({
  menus: { type: Array, default: () => [] },
  modelValue: { type: Array, default: () => [] },
  lockedIds: { type: Array, default: () => [] },
  inheritedIds: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])
const keyword = ref(''), activeIndex = ref(0), treeRef = ref()
const tree = computed(() => buildPermissionTree(props.menus))
const allIds = computed(() => permissionIds(tree.value))
const selected = computed(() => new Set([...props.modelValue, ...props.lockedIds]))
const selectedCount = computed(() => allIds.value.filter(id => selected.value.has(id)).length)
const countSelected = module => permissionIds([module]).filter(id => selected.value.has(id)).length
const visibleTree = computed(() => {
  const filter = nodes => nodes.filter(node => filterPermission(keyword.value, node)).map(node => {
    const children = node.children ? filter(node.children) : undefined
    return { ...node, children, disabled: children?.length ? children.every(child => child.disabled) : props.lockedIds.includes(node.id) }
  })
  return filter(keyword.value.trim() ? tree.value : tree.value[activeIndex.value]?.children || [])
})
const visibleIds = computed(() => permissionIds(visibleTree.value))
const expandedKeys = computed(() => {
  const folders = nodes => nodes.flatMap(node => node.children?.length ? [node.id, ...folders(node.children)] : [])
  return keyword.value.trim() ? folders(visibleTree.value)
    : visibleTree.value.filter(node => node.children?.some(child => child.children?.length)).map(node => node.id)
})
watch([visibleTree, selected], async () => {
  await nextTick()
  treeRef.value?.setCheckedKeys([...selected.value], false)
}, { immediate: true })

function update(ids) {
  emit('update:modelValue', normalizePermissionSelection(props.menus, [...ids, ...props.lockedIds]))
}
function chooseModule(index) { activeIndex.value = index; keyword.value = '' }
function selectAll() { update([...props.modelValue, ...allIds.value]) }
function clearAll() { update([]) }
function selectVisible() { update([...props.modelValue, ...visibleIds.value]) }
function clearVisible() { update(props.modelValue.filter(id => !visibleIds.value.includes(id))) }
function onCheck() {
  const shown = new Set(visibleIds.value)
  update([...props.modelValue.filter(id => !shown.has(id)),
    ...(treeRef.value?.getCheckedKeys(true) || []).filter(id => shown.has(id))])
}
</script>

<style scoped>
.permission-selector { width: 100%; line-height: 1.5; border: 1px solid var(--el-border-color); border-radius: 8px; overflow: hidden; }
.permission-tools { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; padding: 12px; border-bottom: 1px solid var(--el-border-color-lighter); }
.permission-tools .el-input { flex: 1; min-width: 180px; }
.permission-tools .el-button + .el-button { margin-left: 0; }
.selection-count, .permission-hint, .permission-kind { font-size: 12px; color: var(--el-text-color-secondary); }
.permission-body { display: flex; min-height: 420px; }
.permission-categories { width: 182px; flex-shrink: 0; padding: 8px; background: var(--el-fill-color-light); }
.permission-categories button { display: flex; width: 100%; justify-content: space-between; align-items: center; gap: 6px; border: 0; border-radius: 5px; background: transparent; padding: 11px 8px; color: var(--el-text-color-regular); text-align: left; cursor: pointer; }
.permission-categories button.active { background: var(--el-color-primary-light-9); color: var(--el-color-primary); font-weight: 600; }
.permission-categories button:focus-visible { outline: 2px solid var(--el-color-primary); }
.permission-categories small { font-size: 11px; font-weight: normal; }
.permission-content { flex: 1; min-width: 0; padding: 12px; }
.module-tools { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.module-tools strong { font-size: 14px; }
.permission-hint { margin: 6px 0 12px; }
.permission-scroll { height: 370px; overflow: auto; }
.permission-scroll :deep(.el-tree-node__content) { min-height: 32px; height: auto; padding-top: 3px; padding-bottom: 3px; }
.permission-label { white-space: normal; margin-right: 8px; }
.permission-kind { margin-left: auto; padding-right: 8px; flex-shrink: 0; }
@media (max-width: 600px) {
  .permission-body { flex-direction: column; }
  .permission-categories { display: grid; grid-template-columns: repeat(2, 1fr); width: auto; }
  .permission-categories button { padding: 8px; }
}
</style>
