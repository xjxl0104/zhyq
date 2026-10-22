<template>
  <li class="oc-item">
    <div class="oc-card" :class="{ 'is-selected': selectedId != null && String(selectedId) === String(node.id), 'is-off': node.status !== 1, 'is-hit': hit }"
         role="button" tabindex="0" @click="$emit('select', node)" @keydown.enter="$emit('select', node)">
      <div class="oc-top">
        <el-tag size="small" :type="tagType(node.positionCode)">{{ node.positionCode }}</el-tag>
        <span class="oc-name">{{ node.name }}</span>
      </div>
      <div class="oc-sub">客户 {{ node.customers }} · 已签约 {{ node.signedCustomers }}</div>
      <button v-if="hasChildren" class="oc-toggle" type="button" :title="closed ? '展开下级' : '收起下级'"
              @click.stop="$emit('toggle', node.id)">{{ closed ? `+${node.children.length}` : '−' }}</button>
    </div>
    <ul v-if="hasChildren && !closed" class="oc-children">
      <OrgChartNode v-for="child in node.children" :key="child.id" :node="child" :selected-id="selectedId"
                    :collapsed="collapsed" :keyword="keyword" @select="$emit('select', $event)" @toggle="$emit('toggle', $event)" />
    </ul>
  </li>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  node: { type: Object, required: true },
  selectedId: { type: [Number, String], default: null },
  collapsed: { type: Object, default: () => ({}) },
  keyword: { type: String, default: '' }
})
defineEmits(['select', 'toggle'])

const tagType = code => ({ P4: 'danger', P3: 'warning', P2: 'success' }[code] || 'info')
const hasChildren = computed(() => (props.node.children?.length || 0) > 0)
// 搜索时展开全部，方便看到命中的成员
const closed = computed(() => !!props.collapsed[props.node.id] && !props.keyword)
const hit = computed(() => !!props.keyword && String(props.node.name || '').includes(props.keyword))
</script>

<style scoped>
.oc-item { position: relative; display: flex; flex-direction: column; align-items: center; padding: 22px 8px 0; list-style: none; }
/* 兄弟节点之间的横线和竖线 */
.oc-item::before, .oc-item::after { content: ''; position: absolute; top: 0; right: 50%; width: 50%; height: 22px; border-top: 1px solid var(--el-text-color-placeholder, #a8abb2); }
.oc-item::after { right: auto; left: 50%; border-left: 1px solid var(--el-text-color-placeholder, #a8abb2); }
.oc-item:only-child::before, .oc-item:only-child::after { display: none; }
.oc-item:only-child { padding-top: 0; }
.oc-item:first-child::before, .oc-item:last-child::after { border: 0 none; }
.oc-item:last-child::before { border-right: 1px solid var(--el-text-color-placeholder, #a8abb2); border-radius: 0 6px 0 0; }
.oc-item:first-child::after { border-radius: 6px 0 0 0; }
/* 根节点没有上级连线 */
.oc-root > .oc-item { padding-top: 0; }
.oc-root > .oc-item::before, .oc-root > .oc-item::after { display: none; }

.oc-children { position: relative; display: flex; justify-content: center; margin: 0; padding: 22px 0 0; list-style: none; }
.oc-children::before { content: ''; position: absolute; top: 0; left: 50%; height: 22px; border-left: 1px solid var(--el-text-color-placeholder, #a8abb2); }

.oc-card { position: relative; min-width: 132px; max-width: 190px; padding: 8px 12px 10px; text-align: center; cursor: pointer;
  background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 8px; transition: box-shadow .15s, border-color .15s; }
.oc-card:hover { border-color: var(--el-color-primary-light-3); box-shadow: 0 2px 8px rgba(0, 0, 0, .08); }
.oc-card:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.oc-card.is-selected { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 2px var(--el-color-primary-light-7); }
.oc-card.is-off { opacity: .6; border-style: dashed; }
.oc-card.is-hit { border-color: var(--el-color-warning); box-shadow: 0 0 0 2px var(--el-color-warning-light-7); }
.oc-top { display: flex; align-items: center; justify-content: center; gap: 6px; }
.oc-name { font-weight: 600; word-break: break-all; }
.oc-sub { margin-top: 4px; color: var(--el-text-color-secondary); font-size: 12px; white-space: nowrap; }
.oc-toggle { position: absolute; left: 50%; bottom: -10px; transform: translateX(-50%); min-width: 22px; height: 20px; padding: 0 6px; line-height: 18px;
  font-size: 12px; color: var(--el-text-color-regular); background: var(--el-bg-color); border: 1px solid var(--el-border-color); border-radius: 10px; cursor: pointer; }
.oc-toggle:hover { color: var(--el-color-primary); border-color: var(--el-color-primary); }
</style>
