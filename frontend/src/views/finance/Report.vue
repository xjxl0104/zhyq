<template>
  <div class="page-container" v-loading="loading">
    <!-- 收缴率 -->
    <div class="table-card block">
      <div class="block-title">收缴情况</div>
      <div class="rate-row">
        <div class="rate-info">
          <div class="rate-item">
            <span class="label">应收</span>
            <span class="value">¥{{ money(data.receivable) }}</span>
          </div>
          <div class="rate-item">
            <span class="label">实收</span>
            <span class="value success">¥{{ money(data.received) }}</span>
          </div>
        </div>
        <div class="rate-progress">
          <div class="label">收缴率</div>
          <el-progress :percentage="ratePercent" :stroke-width="18"
                       :color="ratePercent >= 80 ? '#16a34a' : ratePercent >= 50 ? '#ea9a13' : '#e5484d'" />
        </div>
      </div>
    </div>

    <!-- 收入结构 -->
    <div class="table-card block">
      <div class="block-title">收入结构(按费用类型)</div>
      <el-table :data="data.list" border stripe>
        <el-table-column prop="feeType" label="费用类型" min-width="140" />
        <el-table-column label="应收金额" min-width="160" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="占比" min-width="240">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.amount, data.receivable)" :stroke-width="14" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 账龄分布 -->
    <div class="table-card block">
      <div class="block-title">账龄分布(未结清欠款)</div>
      <el-table :data="agingRows" border stripe>
        <el-table-column prop="label" label="账龄区间" min-width="140" />
        <el-table-column label="金额" min-width="160" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="占比" min-width="240">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.amount, agingTotal)" :stroke-width="14"
                         :color="row.color" />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { reportApi } from '@/api/finance'

function money(v) {
  if (v == null) return '0.00'
  return Number(v).toFixed(2)
}
function pct(part, whole) {
  const w = Number(whole || 0)
  if (w === 0) return 0
  return Math.round((Number(part || 0) / w) * 100)
}

const loading = ref(false)
const data = reactive({ receivable: 0, received: 0, collectRate: 0, list: [], aging: {} })

const ratePercent = computed(() => Math.round(Number(data.collectRate || 0) * 100))

const agingRows = computed(() => {
  const a = data.aging || {}
  return [
    { label: '未逾期', amount: a.notOverdue || 0, color: '#16a34a' },
    { label: '30天内', amount: a.within30 || 0, color: '#ea9a13' },
    { label: '30-90天', amount: a.days30to90 || 0, color: '#e5484d' },
    { label: '90天以上', amount: a.over90 || 0, color: '#c0392b' }
  ]
})
const agingTotal = computed(() =>
  agingRows.value.reduce((s, r) => s + Number(r.amount || 0), 0))

async function load() {
  loading.value = true
  try {
    const res = await reportApi.summary()
    Object.assign(data, res)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.block { margin-bottom: 16px; }
.block-title { font-size: 15px; font-weight: 600; margin-bottom: 16px; }
.rate-row { display: flex; align-items: center; gap: 48px; }
.rate-info { display: flex; gap: 48px; }
.rate-item { display: flex; flex-direction: column; gap: 6px; }
.rate-item .label { color: #909399; font-size: 13px; }
.rate-item .value { font-size: 22px; font-weight: 600; }
.rate-item .value.success { color: #16a34a; }
.rate-progress { flex: 1; }
.rate-progress .label { color: #909399; font-size: 13px; margin-bottom: 8px; }
</style>
