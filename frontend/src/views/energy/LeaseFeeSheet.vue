<template>
  <div class="page-container">
    <!-- 合并年月表 + 图表切换 -->
    <template v-if="!month">
      <LeaseMonthPicker ref="picker" kind="combined" @select="(m) => (month = m)" @year="(y) => (chartYear = y)" />
      <div class="chart-switch-bar">
        <el-radio-group v-model="chartKind" size="small">
          <el-radio-button value="rent">租金趋势</el-radio-button>
          <el-radio-button value="property">物业费趋势</el-radio-button>
        </el-radio-group>
      </div>
      <LeaseFeeCharts ref="charts" :kind="chartKind" :year="chartYear" />
    </template>

    <!-- 选定月份的租金/物业费明细工作表（顶部一键切换租金 / 物业费） -->
    <template v-else>
      <div class="detail-head">
        <el-button @click="backToBoard"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }}</h3>
        <el-radio-group :model-value="activeFeeType" class="fee-tab-switch" @update:model-value="switchFeeType">
          <el-radio-button value="RENT">租金</el-radio-button>
          <el-radio-button value="PROPERTY">物业费</el-radio-button>
        </el-radio-group>
        <el-tag v-if="locked" type="success">已确认出账（冻结，只读）</el-tag>
        <el-tag v-else-if="period?.noticeStale" type="warning">参数或减免系数有改动，通知单需重新生成</el-tag>
        <el-tag type="info" effect="plain">
          收取单位：{{ (isRent ? period?.rentPayeeName : period?.propertyPayeeName) || '待设置（点击通知单抬头维护）' }}
        </el-tag>
      </div>

      <div v-if="sheet" class="summary-grid">
        <div class="summary-item"><span>在租合同数</span><strong>{{ rows.length }} 份</strong></div>
        <div class="summary-item"><span>计租面积合计</span><strong>{{ num(sheet.totalArea) }} ㎡</strong></div>
        <div class="summary-item"><span>{{ isRent ? '标准租金合计' : '标准物业费合计' }}</span><strong>¥{{ money(isRent ? sheet.standardRentTotal : sheet.standardPropertyTotal) }}</strong></div>
        <div class="summary-item hl"><span>本月减免/免租</span><strong>¥{{ money(discountTotal) }}</strong></div>
        <div class="summary-item total"><span>{{ isRent ? '本月应付租金合计' : '本月应付物业费合计' }}</span><strong>¥{{ money(isRent ? sheet.payableRentTotal : sheet.payablePropertyTotal) }}</strong></div>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" :disabled="locked || !dirtyCount" :loading="savingEntries" @click="saveEntries">
            保存本月调整<span v-if="dirtyCount">（{{ dirtyCount }} 条）</span>
          </el-button>
          <el-button link type="primary" @click="$router.push('/energy/summary')">去缴费汇总生成通知单 →</el-button>
          <span class="toolbar-tip">
            应付金额 = 计租面积 × 当期单价 × 减免系数（1=全额，0.5=5折，0.7=7折，0=全免）；修改保存后请前往「缴费汇总」统一生成并打印通知单。
          </span>
        </div>

        <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
          检索结果 {{ viewRows.length }} / {{ rows.length }} 份合同 · 应付合计 ¥{{ money(viewPayableTotal) }}
        </FeeFilterBar>

        <el-table :data="viewRows" v-loading="loading" border size="small" :row-class-name="rowClass"
                  show-summary :summary-method="summary">
          <el-table-column prop="seqNo" label="序号" width="55" align="center" />
          <el-table-column prop="tenantName" label="租户/公司名称" min-width="190" show-overflow-tooltip />
          <el-table-column prop="unitName" label="楼层/区域" min-width="180" show-overflow-tooltip />
          <el-table-column label="计租面积(㎡)" width="105" align="right">
            <template #default="{ row }">{{ num(row.leaseArea) }}</template>
          </el-table-column>
          <el-table-column :label="isRent ? '当期租金单价' : '当期物业单价'" width="135" align="right">
            <template #default="{ row }">
              <span>{{ num(isRent ? row.rentUnitPrice : row.propertyUnitPrice, 4) }}</span>
              <el-tag v-if="isRent && Number(row.fixedMonthlyRent) > 0" size="small" type="primary" effect="plain" style="margin-left: 4px">固定总价</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="isRent ? '标准租金(元)' : '标准物业费(元)'" width="125" align="right">
            <template #default="{ row }">¥{{ money(isRent ? row.standardRent : row.standardProperty) }}</template>
          </el-table-column>
          <el-table-column label="减免系数" width="125" align="center">
            <template #default="{ row }">
              <el-input-number v-if="isRent" v-model="row.rentFactor" :min="0" :max="1" :step="0.1" :precision="6"
                               :controls="false" size="small" :disabled="locked" style="width: 100px"
                               @change="() => markDirty(row)" />
              <el-input-number v-else v-model="row.propertyFactor" :min="0" :max="1" :step="0.1" :precision="6"
                               :controls="false" size="small" :disabled="locked" style="width: 100px"
                               @change="() => markDirty(row)" />
            </template>
          </el-table-column>
          <el-table-column :label="isRent ? '应付租金(元)' : '应付物业费(元)'" width="130" align="right">
            <template #default="{ row }">
              <strong>¥{{ money(livePayable(row)) }}</strong>
            </template>
          </el-table-column>
          <el-table-column prop="collectionTiming" label="约定收款时点" min-width="145" show-overflow-tooltip />
          <el-table-column label="备注" min-width="220">
            <template #default="{ row }">
              <el-input v-model="row.remark" size="small" :disabled="locked" placeholder="免租/优惠/递增说明"
                        @input="() => markDirty(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="85" align="center" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.adjusted && !locked" link type="warning" size="small" @click="resetRow(row)">恢复默认</el-button>
              <span v-else class="muted">自动</span>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!loading && !rows.length" class="empty-tip">本月没有在租合同，请前往「缴费管理 → 合同基础台账」查看或同步正式合同。</div>
        <div v-if="dirtyCount" class="dirty-tip">有 {{ dirtyCount }} 条合同调整尚未保存；保存后通知单可按最新金额重新生成。</div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { leaseFeeApi } from '@/api/energy'
import { applyFilter, filterOptions, floorBandClass, money, num } from './feePeriod'
import FeeFilterBar from './components/FeeFilterBar.vue'
import LeaseMonthPicker from './components/LeaseMonthPicker.vue'
import LeaseFeeCharts from './components/LeaseFeeCharts.vue'

const route = useRoute()
const initialFeeType = () => {
  const q = String(route.query.tab || '').toUpperCase()
  if (q === 'PROPERTY' || q === 'RENT') return q
  return route.meta.leaseFeeType || 'RENT'
}
const activeFeeType = ref(initialFeeType())
const feeType = computed(() => activeFeeType.value)
const isRent = computed(() => feeType.value === 'RENT')
const chartKind = ref(activeFeeType.value === 'PROPERTY' ? 'property' : 'rent')

const month = ref(null)
const chartYear = ref(new Date().getFullYear())
const period = ref(null)
const sheet = ref(null)
const rows = ref([])
const loading = ref(false)
const savingEntries = ref(false)
const generating = ref(false)
const picker = ref(null)
const charts = ref(null)
const dirty = reactive(new Set())
const dirtyCount = computed(() => dirty.size)

const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))

const filter = reactive({ company: '', floor: '' })
const opts = computed(() => filterOptions(rows.value, (r) => r.tenantName, (r) => r.unitName))
const viewRows = computed(() => applyFilter(rows.value, filter, (r) => r.tenantName, (r) => r.unitName))
const bandClass = computed(() => floorBandClass(rows.value, (r) => r.unitName))

function livePayable(row) {
  const std = Number(isRent.value ? row.standardRent : row.standardProperty || 0)
  const factor = Number(isRent.value ? row.rentFactor : row.propertyFactor)
  if (!Number.isFinite(factor)) return Number(isRent.value ? row.payableRent : row.payableProperty || 0)
  const defFactor = Number(isRent.value ? row.defaultRentFactor : row.defaultPropertyFactor)
  if (!dirty.has(row.contractId) && Math.abs(factor - defFactor) < 1e-6) {
    return Number(isRent.value ? row.payableRent : row.payableProperty || 0)
  }
  return Math.round(std * factor * 100) / 100
}

const discountTotal = computed(() => {
  if (!sheet.value) return 0
  const std = Number(isRent.value ? sheet.value.standardRentTotal : sheet.value.standardPropertyTotal || 0)
  const pay = Number(isRent.value ? sheet.value.payableRentTotal : sheet.value.payablePropertyTotal || 0)
  return Math.max(0, Math.round((std - pay) * 100) / 100)
})
const viewPayableTotal = computed(() => viewRows.value.reduce((s, r) => s + livePayable(r), 0))

function markDirty(row) { dirty.add(row.contractId) }
function rowClass({ row }) {
  const st = dirty.has(row.contractId) ? 'row-dirty' : ''
  return `${bandClass.value(row)} ${st}`.trim()
}

async function openMonth() {
  if (!month.value) { period.value = null; sheet.value = null; rows.value = []; dirty.clear(); return }
  loading.value = true
  try {
    period.value = await leaseFeeApi.open(month.value)
    await loadSheet()
  } finally {
    loading.value = false
  }
}

async function loadSheet() {
  if (!period.value) return
  loading.value = true
  try {
    sheet.value = await leaseFeeApi.sheet(period.value.id, feeType.value)
    rows.value = (sheet.value?.rows || []).map((r) => ({
      ...r,
      rentFactor: Number(r.rentFactor ?? 1),
      propertyFactor: Number(r.propertyFactor ?? 1),
      remark: r.remark || ''
    }))
    dirty.clear()
  } finally {
    loading.value = false
  }
}

watch(month, openMonth)
watch(() => route.query.tab, (q) => {
  const next = String(q || '').toUpperCase()
  if (next === 'RENT' || next === 'PROPERTY') {
    activeFeeType.value = next
    chartKind.value = next === 'PROPERTY' ? 'property' : 'rent'
    if (month.value && period.value) loadSheet()
  }
})

async function switchFeeType(next) {
  if (!next || next === activeFeeType.value) return
  if (dirty.size > 0) {
    try {
      await ElMessageBox.confirm('当前页签有未保存的减免系数或备注调整，切换会放弃未保存修改，是否继续？', '切换费用项', {
        type: 'warning', confirmButtonText: '放弃并切换', cancelButtonText: '留在本页'
      })
    } catch {
      return
    }
  }
  activeFeeType.value = next
  chartKind.value = next === 'PROPERTY' ? 'property' : 'rent'
  if (month.value && period.value) await loadSheet()
}

async function backToBoard() {
  if (dirty.size > 0) {
    try {
      await ElMessageBox.confirm('当前有未保存的调整，返回年月表会放弃未保存修改，是否继续？', '返回年月表', {
        type: 'warning', confirmButtonText: '放弃并返回', cancelButtonText: '继续编辑'
      })
    } catch {
      return
    }
  }
  month.value = null
}

let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  if (!month.value) { picker.value?.refresh(); charts.value?.refresh() }
  else { period.value = await leaseFeeApi.get(period.value.id); await loadSheet() }
})

async function saveEntries() {
  const inputs = rows.value.filter((r) => dirty.has(r.contractId)).map((r) => ({
    contractId: r.contractId,
    rentFactor: isRent.value ? r.rentFactor : null,
    propertyFactor: isRent.value ? null : r.propertyFactor,
    remark: r.remark
  }))
  if (!inputs.length) return
  savingEntries.value = true
  try {
    await leaseFeeApi.saveEntries(period.value.id, inputs)
    ElMessage.success(`已保存 ${inputs.length} 条调整`)
    period.value = await leaseFeeApi.get(period.value.id)
    await loadSheet()
  } finally {
    savingEntries.value = false
  }
}

async function resetRow(row) {
  await leaseFeeApi.saveEntries(period.value.id, [{ contractId: row.contractId, resetDefault: true }])
  ElMessage.success('已恢复按合同规则默认计算')
  period.value = await leaseFeeApi.get(period.value.id)
  await loadSheet()
}

async function generate() {
  generating.value = true
  try {
    const r = await leaseFeeApi.generate(period.value.id)
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张租金/物业费通知单，可到「缴费汇总」核对与打印`)
    period.value = r.period
  } finally {
    generating.value = false
  }
}

async function afterContractChanged() {
  if (!month.value) { picker.value?.refresh(); charts.value?.refresh() }
  else { period.value = await leaseFeeApi.get(period.value.id); await loadSheet() }
}

function summary({ columns, data }) {
  const sum = (fn) => data.reduce((s, r) => s + Number(fn(r) || 0), 0)
  return columns.map((col, i) => {
    if (i === 0) return '合计'
    switch (col.label) {
      case '计租面积(㎡)': return num(sum((r) => r.leaseArea))
      case '标准租金(元)': return '¥' + money(sum((r) => r.standardRent))
      case '标准物业费(元)': return '¥' + money(sum((r) => r.standardProperty))
      case '应付租金(元)':
      case '应付物业费(元)': return '¥' + money(sum(livePayable))
      default: return ''
    }
  })
}
</script>

<style scoped>
.chart-switch-bar { display: flex; justify-content: flex-end; margin: 12px 0 -4px; }
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.fee-tab-switch :deep(.el-radio-button__inner) { font-weight: 600; padding: 8px 18px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.total strong { color: var(--el-color-danger); }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.dirty-tip { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); }
:deep(.row-dirty) td { background: var(--el-color-warning-light-9) !important; }
</style>
