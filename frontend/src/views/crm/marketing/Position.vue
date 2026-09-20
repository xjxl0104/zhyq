<template>
  <div class="page-container">
    <div class="table-card">
      <div class="toolbar">
        <div class="depth">
          <span>岗位数:</span>
          <el-radio-group v-model="depth" size="small" @change="onDepthChange">
            <el-radio-button :value="2">2 级(园区伙伴 → 合伙人)</el-radio-button>
            <el-radio-button :value="4">4 级(园区伙伴 → 银牌 → 金牌 → 钻石)</el-radio-button>
          </el-radio-group>
          <span v-if="depth === 4" class="warn">4 级模式请确认合规:门槛只能含成交额/单数,不得含人数;无入门费。</span>
        </div>
        <el-button type="primary" @click="save" :loading="saving">保存份额</el-button>
        <el-button @click="reviewNow">立即执行晋升复核</el-button>
      </div>
      <el-table :data="list" v-loading="loading" border>
        <el-table-column prop="code" label="岗位" width="80" />
        <el-table-column label="名称" min-width="140">
          <template #default="{ row }"><el-input v-model="row.name" size="small" /></template>
        </el-table-column>
        <el-table-column label="份额 %" width="130">
          <template #default="{ row }"><el-input-number v-model="row.sharePct" :min="1" :max="100" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="可下调下限 %" width="140">
          <template #default="{ row }"><el-input-number v-model="row.shareMinPct" :min="0" :max="row.sharePct" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="锁定上限" width="120">
          <template #default="{ row }"><el-input-number v-model="row.lockCap" :min="0" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="晋升门槛:成交额" width="160">
          <template #default="{ row }"><el-input-number v-model="row.promoteAmount" :min="0" :step="10000" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="晋升门槛:单数" width="130">
          <template #default="{ row }"><el-input-number v-model="row.promoteOrders" :min="0" size="small" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="计团队成交" width="110" align="center">
          <template #default="{ row }"><el-switch v-model="row.teamCounted" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
        <el-table-column label="允许自动降级" width="120" align="center">
          <template #default="{ row }"><el-switch v-model="row.demoteEnabled" :active-value="1" :inactive-value="0" /></template>
        </el-table-column>
      </el-table>
      <p class="hint">规则:份额沿岗位严格递增、顶格 100;上级只拿「自己份额 − 下级已拿份额」的级差。改份额只影响之后的计佣,已生成流水不变。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { mktPositionApi } from '@/api/marketing'

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const depth = ref(2)

async function load() {
  loading.value = true
  try {
    list.value = await mktPositionApi.list()
    const d = await mktPositionApi.depth()
    depth.value = Number(d) || 2
  } finally {
    loading.value = false
  }
}

function validate() {
  let prev = 0
  for (const p of list.value) {
    if (p.sharePct <= prev) return `${p.code} 的份额必须大于上一级(${prev})`
    if (p.shareMinPct > p.sharePct) return `${p.code} 的下限不能高于份额`
    prev = p.sharePct
  }
  if (prev !== 100) return '最高岗位份额必须为 100'
  return null
}

async function save() {
  const err = validate()
  if (err) return ElMessage.error(err)
  saving.value = true
  try {
    await mktPositionApi.update(list.value)
    ElMessage.success('已保存')
    load()
  } finally {
    saving.value = false
  }
}

async function onDepthChange(v) {
  try {
    await ElMessageBox.confirm(
      v === 4 ? '切换为 4 级会启用银牌/金牌两个中间岗位,并常驻合规提示。确认切换?' : '切换为 2 级后中间岗位按合伙人(顶格)计算。确认切换?',
      '切换岗位数', { type: 'warning' })
    await mktPositionApi.setDepth({ depth: v })
    ElMessage.success('已切换')
  } catch (e) {
    load()
  }
}

async function reviewNow() {
  const n = await mktPositionApi.reviewNow()
  ElMessage.success(`复核完成,调整 ${n ?? 0} 人`)
}

onMounted(load)
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.depth { display: flex; align-items: center; gap: 8px; margin-right: auto; }
.warn { color: var(--el-color-danger); font-size: 12px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
</style>
