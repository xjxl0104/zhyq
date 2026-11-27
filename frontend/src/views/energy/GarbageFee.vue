<template>
  <div :class="embedded ? '' : 'page-container'">
    <FeeMonthPicker v-if="!embedded && !month" ref="picker" kind="garbage" @select="(m) => (month = m)" @year="(y) => (chartYear = y)" />
    <FeeCharts v-if="!embedded && !month" ref="charts" kind="garbage" :year="chartYear" />

    <template v-else-if="month">
      <div v-if="!embedded" class="detail-head">
        <el-button @click="back"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }} · 垃圾处理费明细</h3>
        <el-tag v-if="locked" type="success">已确认出账（冻结，只读）</el-tag>
        <el-tag v-else-if="period?.noticeStale" type="warning">总额或区域有改动，通知单需重新生成</el-tag>
      </div>

      <div v-if="period" class="search-bar">
        <el-form :inline="true">
          <el-form-item label="生活垃圾总额(元)">
            <el-input-number v-model="form.domesticGarbageAmount" :min="0" :precision="2" :controls="false"
                             :disabled="locked" style="width: 130px" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.domesticGarbageNote" placeholder="如 6桶*单价13.35元/桶" :disabled="locked" style="width: 190px" />
          </el-form-item>
          <el-form-item label="工业垃圾总额(元)">
            <el-input-number v-model="form.industrialGarbageAmount" :min="0" :precision="2" :controls="false"
                             :disabled="locked" style="width: 130px" />
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.industrialGarbageNote" placeholder="如 1车/月" :disabled="locked" style="width: 150px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="locked" :loading="state.saving" @click="saveHeader">保存</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="sheet" class="summary-grid">
        <div class="summary-item"><span>生活垃圾总额</span><strong>¥{{ money(period?.domesticGarbageAmount) }}</strong></div>
        <div class="summary-item"><span>工业垃圾总额</span><strong>¥{{ money(period?.industrialGarbageAmount) }}</strong></div>
        <div class="summary-item hl"><span>参与分摊计租面积</span><strong>{{ num(sheet.totalLeaseArea) }} ㎡</strong></div>
        <div class="summary-item"><span>参与分摊单元</span><strong>{{ sheet.rows.filter(r => r.sharePublic).length }} / {{ sheet.rows.length }}</strong></div>
        <div class="summary-item total"><span>已分摊合计</span><strong>¥{{ money(allocatedTotal) }}</strong></div>
      </div>

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" :disabled="locked" @click="meterDialog.visible = true"><el-icon><Plus /></el-icon>新增区域/用户</el-button>
          <el-button link type="primary" @click="$router.push('/energy/summary')">去缴费汇总生成通知单 →</el-button>
          <span class="toolbar-tip">两笔总额按各单元「计租面积 ÷ 参与分摊的计租面积合计」分摊到分；保存后前往「缴费汇总」统一生成并打印通知单。</span>
        </div>
        <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
          检索结果 {{ viewRows.length }} / {{ allRows.length }} 个区域 · 垃圾费合计 ¥{{ money(viewRows.reduce((s, r) => s + Number(r.domesticFee || 0) + Number(r.industrialFee || 0), 0)) }}（表格底部合计行同步汇总）
        </FeeFilterBar>
        <el-table :data="viewRows" v-loading="loading || sheetLoading" border size="small"
                  :row-class-name="({ row }) => bandClass(row)" show-summary :summary-method="summary">
          <el-table-column type="index" label="序号" width="55" />
          <el-table-column prop="unit" label="区域 / 单元" width="120" />
          <el-table-column prop="tenantName" label="用户名 / 租户" min-width="160" show-overflow-tooltip />
          <el-table-column label="计租面积(㎡)" width="150" align="right">
            <template #default="{ row }">
              <el-input-number :model-value="row.leaseArea" :min="0" :precision="2" :controls="false" size="small"
                               :disabled="locked" style="width: 120px" @change="(v) => saveArea(row, v)" />
            </template>
          </el-table-column>
          <!-- 计租/总计租面积 = 本单元面积 ÷ 参与分摊的面积合计 -->
          <el-table-column label="计租/总计租面积" width="140" align="right">
            <template #default="{ row }">
              <span v-if="row.sharePublic && Number(row.leaseArea) > 0" class="ratio"
                    :title="`${row.leaseArea} ÷ ${sheet?.totalLeaseArea} = ${Number(row.ratio || 0).toFixed(6)}`">{{ (Number(row.ratio || 0) * 100).toFixed(2) }}%</span>
              <span v-else-if="row.sharePublic" class="warn">未填面积</span>
              <span v-else class="muted">不参与</span>
            </template>
          </el-table-column>
          <el-table-column label="生活垃圾清运费" width="130" align="right">
            <template #default="{ row }">¥{{ money(row.domesticFee) }}</template>
          </el-table-column>
          <el-table-column label="工业垃圾清运费" width="130" align="right">
            <template #default="{ row }">¥{{ money(row.industrialFee) }}</template>
          </el-table-column>
          <el-table-column label="合计/元" width="120" align="right">
            <template #default="{ row }"><strong>¥{{ money(Number(row.domesticFee || 0) + Number(row.industrialFee || 0)) }}</strong></template>
          </el-table-column>
          <el-table-column label="参与分摊" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.sharePublic ? 'success' : 'info'" effect="plain">{{ row.sharePublic ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="danger" :disabled="locked" @click="removeRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!loading && !sheetLoading && !(sheet?.rows || []).length" class="empty-tip">
          这个月还没有区域。点「新增区域/用户」添加。
        </div>
      </div>
    </template>

    <FeeMeterDialog v-model="meterDialog.visible" :default-types="['电', '水']" :areas="(sheet?.rows || []).map(r => r.unit)"
                    @saved="afterMeterChange" />
  </div>
</template>

<script setup>
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num, BILL_KINDS, applyFilter, filterOptions, floorBandClass } from './feePeriod'
import FeeFilterBar from './components/FeeFilterBar.vue'
import FeeMonthPicker from './components/FeeMonthPicker.vue'
import FeeCharts from './components/FeeCharts.vue'
import FeeBillFiles from './components/FeeBillFiles.vue'
import FeeMeterDialog from './components/FeeMeterDialog.vue'

const props = defineProps({
  embedded: { type: Boolean, default: false },
  embeddedMonth: { type: String, default: '' }
})
const emit = defineEmits(['period-change'])

const sheet = ref(null)
const sheetLoading = ref(false)
const generating = ref(false)
const picker = ref(null)
const charts = ref(null)
const chartYear = ref(new Date().getFullYear())
const meterDialog = reactive({ visible: false })
const form = reactive({ domesticGarbageAmount: 0, domesticGarbageNote: '', industrialGarbageAmount: 0, industrialGarbageNote: '' })
const { month, period, loading, state, save, reload, back } = useFeePeriod(async (p) => {
  Object.assign(form, {
    domesticGarbageAmount: p.domesticGarbageAmount, domesticGarbageNote: p.domesticGarbageNote,
    industrialGarbageAmount: p.industrialGarbageAmount, industrialGarbageNote: p.industrialGarbageNote
  })
  await loadSheet()
})
const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))
const allocatedTotal = computed(() => (sheet.value?.rows || [])
  .reduce((s, r) => s + Number(r.domesticFee || 0) + Number(r.industrialFee || 0), 0))

// 检索:按公司、楼层筛表格;每一层交替深浅色。A、B、C 区各自按自己的计租面积分摊,不合并
const filter = reactive({ company: '', floor: '' })
const companyOf = (r) => r.tenantName
const unitOf = (r) => r.unit
const allRows = computed(() => sheet.value?.rows || [])
const opts = computed(() => filterOptions(allRows.value, companyOf, unitOf))
const viewRows = computed(() => applyFilter(allRows.value, filter, companyOf, unitOf))
const bandClass = computed(() => floorBandClass(allRows.value, unitOf))

async function loadSheet() {
  if (!period.value) return
  sheetLoading.value = true
  try {
    sheet.value = await feePeriodApi.garbageSheet(period.value.id)
  } finally {
    sheetLoading.value = false
  }
}

watch(() => props.embeddedMonth, (m) => {
  if (props.embedded) month.value = m || null
}, { immediate: true })
watch(period, (p) => { emit('period-change', p) }, { immediate: true })

let firstActivation = true
onActivated(async () => {
  if (props.embedded) return
  if (firstActivation) { firstActivation = false; return }
  if (!month.value) { picker.value?.refresh(); charts.value?.refresh() }
  else { await reload(); await loadSheet() }
})
watch(month, (v) => { if (!v) sheet.value = null })

defineExpose({
  refresh: async () => { if (month.value) { await reload(); await loadSheet() } }
})

async function saveHeader() {
  await save(form)
  ElMessage.success('已保存，分摊已重算')
  await loadSheet()
}
async function saveArea(row, value) {
  if (value == null) return
  // 一个单元可能有几块表(水表、30倍/80倍电表),面积挂在同区域全部表上,后端按区域一起改
  await feePeriodApi.meterFlags({ meterId: row.meterIds[0], leaseArea: value, applyAreaToUnit: true })
  ElMessage.success(`「${row.unit}」计租面积已更新`)
  await reload()
  await loadSheet()
}
async function afterMeterChange() {
  await reload()
  await loadSheet()
}
async function removeRow(row) {
  await ElMessageBox.confirm(
    `确定删除区域「${row.unit}」${row.tenantName && row.tenantName !== '-' ? `（${row.tenantName}）` : ''}？该区域下的 ${row.meterIds.length} 块表都会被移除，不再出现在未出账的月份和以后各月；已确认出账的月份不受影响。`,
    '删除区域/用户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await feePeriodApi.deleteMeters(row.meterIds)
  ElMessage.success('已删除，本月的通知单需要重新生成')
  await afterMeterChange()
}
async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单`)
    if (r.warnings?.length) ElMessage.warning({ message: r.warnings.join('；'), duration: 8000 })
    await reload()
  } finally {
    generating.value = false
  }
}
function summary({ columns, data }) {
  const sums = []
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    switch (col.label) {
      case '计租面积(㎡)': sums[i] = num(sum(r => r.sharePublic ? r.leaseArea : 0)); break
      // 参与分摊单元的面积占比之和,正常就是 100.00%
      case '计租/总计租面积': sums[i] = (sum(r => (r.sharePublic && Number(r.leaseArea) > 0 ? r.ratio : 0)) * 100).toFixed(2) + '%'; break
      case '生活垃圾清运费': sums[i] = '¥' + money(sum(r => r.domesticFee)); break
      case '工业垃圾清运费': sums[i] = '¥' + money(sum(r => r.industrialFee)); break
      case '合计/元': sums[i] = '¥' + money(sum(r => Number(r.domesticFee || 0) + Number(r.industrialFee || 0))); break
      default: sums[i] = ''
    }
  })
  return sums
}
</script>

<style scoped>
.detail-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.detail-head h3 { margin: 0; font-size: 16px; }
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.total strong { color: var(--el-color-danger); }
.ratio { font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.warn { color: var(--el-color-warning); font-size: 12px; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
</style>
