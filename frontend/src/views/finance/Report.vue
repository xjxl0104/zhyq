<template>
  <div class="page-container" v-loading="loading">
    <!-- 标题 + 年月筛选 -->
    <div class="report-header">
      <h2 class="report-title">DI PARK 财务报表</h2>
      <div class="report-filter">
        <el-select v-model="query.year" style="width: 110px">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y}年`" :value="y" />
        </el-select>
        <el-select v-model="query.month" style="width: 110px">
          <el-option v-for="m in monthOptions" :key="m.value ?? 'all'" :label="m.label" :value="m.value" />
        </el-select>
        <span class="filter-hint">{{ periodLabel }}</span>
      </div>
    </div>

    <!-- 经营提示:全局累计欠款,不随上方月份过滤(保证金是一次性、账期落在签约月,
         按月看多为 0,但欠款一直存在) -->
    <div class="table-card block tip-card">
      <div class="block-title">经营提示 · 全局累计欠款
        <span class="tip-sub">（不随上方月份变化；下方收缴/结构/账龄按所选月份）</span>
      </div>
      <div class="tip-row">
        <div class="tip-stat">
          <span class="label">经营欠款（租金+物业·未收）</span>
          <span class="value warn">¥{{ money(data.operating) }}</span>
        </div>
        <div class="tip-stat">
          <span class="label">保证金欠款（未收）</span>
          <span class="value warn">¥{{ money(data.deposit) }}</span>
        </div>
        <div class="tip-dunning">
          <span class="label">需催收用户（累计欠款）· {{ data.dunning.length }} 户</span>
          <div v-if="data.dunning.length" class="dunning-list">
            <div v-for="d in data.dunning" :key="d.tenant" class="dunning-item">
              <span class="dunning-name" :title="d.tenant">{{ d.tenant }}</span>
              <span class="dunning-amount">¥{{ money(d.amount) }}</span>
            </div>
          </div>
          <div v-else class="dunning-empty">全部应收已结清，无欠款 🎉</div>
        </div>
      </div>
    </div>

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
          <div class="rate-item">
            <span class="label">未收</span>
            <span class="value warning">¥{{ money(unreceived) }}</span>
          </div>
        </div>
        <div class="rate-progress">
          <div class="label">收缴率</div>
          <el-progress :percentage="ratePercent" :stroke-width="18"
                       :color="ratePercent >= 80 ? '#16a34a' : ratePercent >= 50 ? '#ea9a13' : '#e5484d'" />
        </div>
      </div>
    </div>

    <!-- 应收结构 -->
    <div class="table-card block">
      <div class="block-title">应收结构（按租户 / 费用类型）</div>
      <el-table :data="data.list" border stripe :span-method="spanMethod">
        <el-table-column prop="tenant" label="租户" min-width="220" show-overflow-tooltip />
        <el-table-column prop="feeType" label="费用类型" min-width="120" />
        <el-table-column label="应收金额" min-width="150" align="right">
          <template #default="{ row }">¥{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="占比" min-width="220">
          <template #default="{ row }">
            <el-progress :percentage="pct(row.amount, data.receivable)" :stroke-width="14" />
          </template>
        </el-table-column>
        <template #empty>该期间暂无应收账单</template>
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
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { reportApi } from '@/api/finance'
import { money } from '@/utils/format'

function pct(part, whole) {
  const w = Number(whole || 0)
  if (w === 0) return 0
  return Math.round((Number(part || 0) / w) * 100)
}

const now = new Date()
const query = reactive({ year: now.getFullYear(), month: now.getMonth() + 1 })

// 年:当前年往前 3 年、往后 1 年,够覆盖在租合同的账期范围
const yearOptions = computed(() => {
  const arr = []
  for (let y = now.getFullYear() + 1; y >= now.getFullYear() - 3; y--) arr.push(y)
  return arr
})
const monthOptions = [
  { label: '全年', value: null },
  ...Array.from({ length: 12 }, (_, i) => ({ label: `${i + 1}月`, value: i + 1 })),
]
const periodLabel = computed(() =>
  query.month ? `统计 ${query.year} 年 ${query.month} 月账期` : `统计 ${query.year} 年全年账期`)

const loading = ref(false)
const data = reactive({
  receivable: 0, received: 0, collectRate: 0,
  operating: 0, deposit: 0, list: [], dunning: [], aging: {},
})

const ratePercent = computed(() => Math.round(Number(data.collectRate || 0) * 100))
// 未收 = 应收 - 实收:让收缴情况里"还差多少没收"一眼可见,不必去别处对
const unreceived = computed(() => Number(data.receivable || 0) - Number(data.received || 0))

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

// 应收结构按租户分组(后端已按租户聚在一起):合并同租户的「租户」单元格
const spanMethod = ({ rowIndex, columnIndex }) => {
  if (columnIndex !== 0) return
  const list = data.list
  if (rowIndex > 0 && list[rowIndex].tenant === list[rowIndex - 1].tenant) {
    return { rowspan: 0, colspan: 0 }
  }
  let span = 1
  for (let i = rowIndex + 1; i < list.length && list[i].tenant === list[rowIndex].tenant; i++) span++
  return { rowspan: span, colspan: 1 }
}

async function load() {
  loading.value = true
  try {
    const params = { year: query.year }
    if (query.month) params.month = query.month
    const res = await reportApi.summary(params)
    Object.assign(data, res)
  } finally {
    loading.value = false
  }
}

watch([() => query.year, () => query.month], load)
onMounted(load)
</script>

<style scoped>
.report-header {
  display: flex; align-items: center; justify-content: space-between;
  flex-wrap: wrap; gap: 12px; margin-bottom: 16px;
}
.report-title { font-size: 20px; font-weight: 700; letter-spacing: 0.5px; margin: 0; }
.report-filter { display: flex; align-items: center; gap: 10px; }
.filter-hint { color: #909399; font-size: 13px; }

.block { margin-bottom: 16px; }
.block-title { font-size: 15px; font-weight: 600; margin-bottom: 16px; }

.tip-card { background: linear-gradient(180deg, #f6f8ff 0%, #ffffff 60%); }
.tip-row { display: flex; align-items: stretch; gap: 32px; flex-wrap: wrap; }
.tip-stat { display: flex; flex-direction: column; gap: 6px; min-width: 200px; }
.tip-stat .label { color: #909399; font-size: 13px; }
.tip-stat .value { font-size: 22px; font-weight: 700; }
.tip-stat .value.warn { color: #ea9a13; }
.tip-sub { font-size: 12px; font-weight: 400; color: #909399; margin-left: 6px; }
.tip-dunning { flex: 1; min-width: 260px; display: flex; flex-direction: column; gap: 8px; }
.tip-dunning .label { color: #909399; font-size: 13px; }
.dunning-list { display: flex; flex-direction: column; gap: 6px; max-height: 160px; overflow-y: auto; }
.dunning-item {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 6px 12px; background: #fff5f5; border: 1px solid #fde2e2; border-radius: 6px;
}
.dunning-name { color: #303133; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dunning-amount { color: #e5484d; font-weight: 600; font-size: 14px; white-space: nowrap; }
.dunning-empty { color: #16a34a; font-size: 13px; padding: 6px 0; }

.rate-row { display: flex; align-items: center; gap: 48px; }
.rate-info { display: flex; gap: 48px; }
.rate-item { display: flex; flex-direction: column; gap: 6px; }
.rate-item .label { color: #909399; font-size: 13px; }
.rate-item .value { font-size: 22px; font-weight: 600; }
.rate-item .value.success { color: #16a34a; }
.rate-item .value.warning { color: #ea9a13; }
.rate-progress { flex: 1; }
.rate-progress .label { color: #909399; font-size: 13px; margin-bottom: 8px; }
</style>
