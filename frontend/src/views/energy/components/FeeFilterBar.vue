<template>
  <div class="ffb">
    <el-select v-model="model.company" filterable clearable placeholder="按公司检索" style="width: 280px">
      <el-option v-for="c in companies" :key="c" :label="c" :value="c" />
    </el-select>
    <el-select v-model="model.floor" clearable placeholder="按楼层检索" style="width: 140px">
      <el-option v-for="f in floors" :key="f" :label="f" :value="f" />
    </el-select>
    <el-button v-if="active" text type="primary" @click="reset">清除检索</el-button>
    <span v-if="active" class="ffb-result"><slot /></span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

// model = reactive({ company, floor });检索后表格底部的合计行就是检索结果的汇总
const props = defineProps({
  model: { type: Object, required: true },
  companies: { type: Array, default: () => [] },
  floors: { type: Array, default: () => [] }
})
const active = computed(() => !!(props.model.company || props.model.floor))
function reset() {
  props.model.company = ''
  props.model.floor = ''
}
</script>

<style scoped>
.ffb { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.ffb-result { font-size: 13px; color: var(--el-color-primary); font-weight: 600; font-variant-numeric: tabular-nums; }
</style>
