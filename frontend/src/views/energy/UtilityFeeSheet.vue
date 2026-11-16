<template>
  <div class="page-container">
    <!-- 先看年月表,点某个月才进入当月明细 -->
    <FeeMonthPicker v-if="!month" ref="picker" :kind="isElectric ? 'electric' : 'water'" @select="(m) => (month = m)" />

    <template v-else>
      <div class="detail-head">
        <el-button @click="back"><el-icon><ArrowLeft /></el-icon>返回年月表</el-button>
        <h3>{{ monthLabel }} · {{ isElectric ? '电费' : '水费' }}明细</h3>
        <el-tag v-if="locked" type="success">已确认出账（冻结，只读）</el-tag>
        <el-tag v-else-if="period?.noticeStale" type="warning">读数或总量有改动，通知单需重新生成</el-tag>
      </div>

      <FeeBillFiles v-if="period" :period-id="period.id" :kinds="[billKind]" />

      <!-- 账期与总量:对应线下表的「10月总度数 / 光伏 / 单价」几个格子 -->
      <div v-if="period" class="search-bar">
        <el-form :inline="true">
          <template v-if="isElectric">
            <el-form-item label="南网用电(度)">
              <el-input-number v-model="form.electricGridUsage" :min="0" :precision="2" :controls="false"
                               :disabled="locked" style="width: 140px" />
            </el-form-item>
            <el-form-item label="光伏用电(度)">
              <el-input-number v-model="form.electricSolarUsage" :min="0" :precision="2" :controls="false"
                               :disabled="locked" style="width: 130px" />
            </el-form-item>
            <el-form-item label="单价(元/度)">
              <el-input-number v-model="form.electricPrice" :min="0" :precision="4" :controls="false"
                               :disabled="locked" style="width: 110px" />
            </el-form-item>
            <el-form-item label="公摊度数">
              <el-input-number v-model="form.electricPublicManual" :min="0" :precision="2" :controls="false"
                               :disabled="locked" placeholder="空=自动算" style="width: 130px" />
            </el-form-item>
          </template>
          <template v-else>
            <el-form-item label="总表度数(m³)">
              <el-input-number v-model="form.waterTotalUsage" :min="0" :precision="2" :controls="false"
                               :disabled="locked" placeholder="空=按总表抄表" style="width: 150px" />
            </el-form-item>
            <el-form-item label="单价(元/m³)">
              <el-input-number v-model="form.waterPrice" :min="0" :precision="4" :controls="false"
                               :disabled="locked" style="width: 110px" />
            </el-form-item>
            <el-form-item label="公摊度数">
              <el-input-number v-model="form.waterPublicManual" :min="0" :precision="2" :controls="false"
                               :disabled="locked" placeholder="空=自动算" style="width: 130px" />
            </el-form-item>
          </template>
          <el-form-item>
            <el-button type="primary" :disabled="locked" :loading="state.saving" @click="saveHeader">保存总量</el-button>
          </el-form-item>
        </el-form>
      </div>

      <div v-if="sheet" class="summary-grid">
        <div class="summary-item"><span>{{ isElectric ? '总度数(南网+光伏)' : `总表度数(${sheet.totalSource})` }}</span><strong>{{ num(sheet.total) }}</strong></div>
        <div class="summary-item"><span>扣除用量(参与公摊租户+物业表)</span><strong>{{ num(sheet.submeterUsage) }}</strong></div>
        <div class="summary-item hl"><span>公摊度数</span><strong>{{ num(sheet.publicUsage) }}</strong></div>
        <div class="summary-item"><span>参与公摊租户用量</span><strong>{{ num(sheet.shareBase) }}</strong></div>
        <div class="summary-item"><span>单价</span><strong>¥{{ num(sheet.price, 4) }}</strong></div>
        <div class="summary-item total"><span>租户应收合计</span><strong>¥{{ money(sheet.totalFee) }}</strong></div>
      </div>
      <el-alert v-for="(w, i) in (sheet?.warnings || [])" :key="i" :title="w" type="warning" :closable="false" show-icon
                style="margin-bottom: 8px" />

      <div class="table-card">
        <div class="toolbar">
          <el-button type="primary" :disabled="locked || !dirtyCount" :loading="savingReadings" @click="saveReadings">
            保存读数<span v-if="dirtyCount">（{{ dirtyCount }} 块表）</span>
          </el-button>
          <el-button :disabled="locked" @click="openMeter(null)"><el-icon><Plus /></el-icon>新增区域/用户</el-button>
          <el-button :disabled="locked" :loading="generating" @click="generate">重新生成通知单</el-button>
          <el-button link type="primary" @click="$router.push('/energy/summary')">去缴费汇总 →</el-button>
          <span class="toolbar-tip">
            {{ isElectric
              ? '公摊度数 = 南网 + 光伏 − 参与公摊的租户用量(室外充电桩、未租单元不参与也不扣，各自另收)；按各户实际用量占比摊回；线下表里的公摊度数是手填的，也可直接在上面填。本月读数自动成为下个月的上期读数；所有数保留两位小数，各户之和与合计逐分一致。'
              : '公摊度数 = 总表度数 − 参与公摊的租户用量；按各户实际用量占比摊回；也可直接手填公摊度数。总表度数不填时按发票总表当月抄表自动取。本月读数自动成为下个月的上期读数；所有数保留两位小数。' }}
          </span>
        </div>
        <FeeFilterBar :model="filter" :companies="opts.companies" :floors="opts.floors">
          检索结果 {{ viewRows.length }} / {{ rows.length }} 块表 · 合计 ¥{{ money(viewRows.reduce((s, r) => s + Number(r.totalFee || 0), 0)) }}（表格底部合计行同步汇总）
        </FeeFilterBar>
        <el-table :data="viewRows" v-loading="loading || sheetLoading" border size="small"
                  :row-class-name="rowClass" show-summary :summary-method="summary">
          <el-table-column type="index" label="序号" width="50" />
          <el-table-column prop="area" label="区域" width="85" />
          <el-table-column label="用户名 / 租户" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="cell-main">{{ row.customerName || row.tenantName || '-' }}</div>
              <div v-if="row.tenantName && row.tenantName !== row.customerName" class="cell-sub">{{ row.tenantName }}</div>
              <div v-else-if="!row.tenantRefId && row.meterRole === 'TENANT'" class="cell-sub warn">未关联在租合同</div>
            </template>
          </el-table-column>
          <el-table-column prop="ratio" label="倍率" width="55" align="right" />
          <el-table-column label="上期读数" width="105" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.prevReading" :precision="2" :controls="false" size="small" :disabled="locked"
                               style="width: 95px" @change="() => markDirty(row)" />
            </template>
          </el-table-column>
          <el-table-column label="本期读数" width="110" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.currReading" :precision="2" :controls="false" size="small" :disabled="locked"
                               placeholder="填读数" style="width: 100px" @change="() => markDirty(row)" />
            </template>
          </el-table-column>
          <el-table-column label="本期用量" width="95" align="right">
            <template #default="{ row }"><strong>{{ num(liveUsage(row)) }}</strong></template>
          </el-table-column>
          <!-- 公摊比例 = 本户用量 ÷ 参与公摊租户用量合计;不参与公摊的写「不参与」 -->
          <el-table-column label="公摊比例" width="95" align="right">
            <template #default="{ row }">
              <span v-if="row.sharePublic && row.meterRole === 'TENANT'" class="ratio" :title="`本户用量 ÷ 参与公摊租户用量 = ${Number(row.coefficient || 0).toFixed(6)}`">{{ ratioText(row.coefficient) }}</span>
              <span v-else class="muted">不参与</span>
            </template>
          </el-table-column>
          <el-table-column label="公摊度数" width="95" align="right">
            <template #default="{ row }">{{ num(row.publicUsage) }}</template>
          </el-table-column>
          <el-table-column label="度数+公摊" width="100" align="right">
            <template #default="{ row }">{{ num(row.totalUsage) }}</template>
          </el-table-column>
          <el-table-column :label="isElectric ? '电费' : '水费'" width="100" align="right">
            <template #default="{ row }">¥{{ money(row.fee) }}</template>
          </el-table-column>
          <el-table-column label="公摊费" width="100" align="right">
            <template #default="{ row }">¥{{ money(row.publicFee) }}</template>
          </el-table-column>
          <el-table-column label="合计/元" width="110" align="right">
            <template #default="{ row }"><strong>¥{{ money(row.totalFee) }}</strong></template>
          </el-table-column>
          <el-table-column label="参与公摊" width="85" align="center">
            <template #default="{ row }">
              <el-switch :model-value="row.sharePublic" size="small" :disabled="locked || row.meterRole !== 'TENANT'"
                         @change="(v) => toggleShare(row, v)" />
            </template>
          </el-table-column>
          <el-table-column label="表计编号" min-width="105" show-overflow-tooltip>
            <template #default="{ row }">
              <div>{{ row.code }}</div>
              <div v-if="row.meterRole === 'PROPERTY'" class="cell-sub">物业公司表</div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :disabled="locked" @click="openMeter(row)">修改</el-button>
              <el-button link type="danger" :disabled="locked" @click="removeRow(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-if="!loading && !sheetLoading && !rows.length" class="empty-tip">
          这个月还没有{{ isElectric ? '电' : '水' }}表。点「新增区域/用户」添加。
        </div>
        <div v-if="dirtyCount" class="dirty-tip">有 {{ dirtyCount }} 块表的读数改了还没保存；保存后公摊与金额会按新读数重算。</div>
      </div>
    </template>

    <FeeMeterDialog v-model="meterDialog.visible" :meter="meterDialog.meter" :default-types="[energyType]"
                    :areas="rows.map(r => r.area)" @saved="afterMeterChange" />
  </div>
</template>

<script setup>
import { computed, onActivated, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus } from '@element-plus/icons-vue'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num, BILL_KINDS, applyFilter, filterOptions, floorBandClass } from './feePeriod'
import FeeFilterBar from './components/FeeFilterBar.vue'
import FeeMonthPicker from './components/FeeMonthPicker.vue'
import FeeBillFiles from './components/FeeBillFiles.vue'
import FeeMeterDialog from './components/FeeMeterDialog.vue'

const route = useRoute()
// 一组件两路由:电费 / 水费 只差能源类型与几个录入格
const energyType = computed(() => route.meta.energyType || '电')
const isElectric = computed(() => energyType.value === '电')
const billKind = computed(() => (isElectric.value ? BILL_KINDS.electric : BILL_KINDS.water))

const sheet = ref(null)
const rows = ref([])
const sheetLoading = ref(false)
const savingReadings = ref(false)
const generating = ref(false)
const picker = ref(null)
const dirty = reactive(new Set())
const dirtyCount = computed(() => dirty.size)

const form = reactive({ electricGridUsage: 0, electricSolarUsage: 0, electricPrice: 0, electricPublicManual: null, waterTotalUsage: null, waterPrice: 0, waterPublicManual: null })
const { month, period, loading, state, save, reload, back } = useFeePeriod(async (p) => {
  Object.assign(form, {
    electricGridUsage: p.electricGridUsage, electricSolarUsage: p.electricSolarUsage, electricPrice: p.electricPrice,
    electricPublicManual: p.electricPublicManual, waterPublicManual: p.waterPublicManual,
    waterTotalUsage: p.waterTotalUsage, waterPrice: p.waterPrice
  })
  await loadSheet()
})
const locked = computed(() => period.value?.status === 'CONFIRMED')
const monthLabel = computed(() => (month.value ? `${month.value.slice(0, 4)}年${Number(month.value.slice(5))}月` : ''))

async function loadSheet() {
  if (!period.value) return
  sheetLoading.value = true
  try {
    sheet.value = await feePeriodApi.sheet(period.value.id, energyType.value)
    rows.value = sheet.value.rows || []
    dirty.clear()
  } finally {
    sheetLoading.value = false
  }
}
// 电费/水费各有自己的路由实例;万一组件被复用,切类型时回到年月表
watch(energyType, () => { month.value = null })

// keep-alive 回到本页时刷新:年月表上的合计、当月明细都可能被别的页面改过
let firstActivation = true
onActivated(async () => {
  if (firstActivation) { firstActivation = false; return }
  if (!month.value) picker.value?.refresh()
  else { await reload(); await loadSheet() }
})

// 比例用百分比显示,保留两位;鼠标放上去看 6 位小数
const ratioText = (v) => `${(Number(v || 0) * 100).toFixed(2)}%`

function liveUsage(row) {
  if (row.currReading == null || row.prevReading == null) return row.usage
  const diff = Number(row.currReading) - Number(row.prevReading)
  return diff < 0 ? 0 : Math.round(diff * Number(row.ratio || 1) * 100) / 100
}
function markDirty(row) { dirty.add(row.meterId) }
// 检索:按公司、楼层筛表格;每一层交替深浅色,一眼分清楼层
const filter = reactive({ company: '', floor: '' })
const companyOf = (r) => r.customerName || r.tenantName
const unitOf = (r) => r.area
const opts = computed(() => filterOptions(rows.value, companyOf, unitOf))
const viewRows = computed(() => applyFilter(rows.value, filter, companyOf, unitOf))
const bandClass = computed(() => floorBandClass(rows.value, unitOf))
function rowClass({ row }) {
  const state = dirty.has(row.meterId) ? 'row-dirty' : (row.sharePublic ? '' : 'row-nonshare')
  return `${bandClass.value(row)} ${state}`.trim()
}

async function saveHeader() {
  await save(form)
  ElMessage.success('已保存，公摊已按新总量重算')
  await loadSheet()
}

async function saveReadings() {
  const inputs = rows.value.filter(r => dirty.has(r.meterId) && r.currReading != null)
    .map(r => ({ meterId: r.meterId, prevReading: r.prevReading, currReading: r.currReading }))
  if (!inputs.length) return ElMessage.warning('没有填了本期读数的行')
  savingReadings.value = true
  try {
    const r = await feePeriodApi.saveReadings(period.value.id, inputs)
    ElMessage.success(`已保存 ${r.saved} 块表的读数，下个月的上期读数已自动衔接`)
    await reload()
    await loadSheet()
  } finally {
    savingReadings.value = false
  }
}

async function toggleShare(row, value) {
  await feePeriodApi.meterFlags({ meterId: row.meterId, sharePublic: value ? 1 : 0 })
  row.sharePublic = value
  ElMessage.success(value ? '已加入公摊' : '已设为不参与公摊（只付自身用量）')
  await reload()
  await loadSheet()
}

async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单，到「缴费汇总」核对与打印`)
    if (r.warnings?.length) ElMessage.warning({ message: r.warnings.join('；'), duration: 8000 })
    await reload()
  } finally {
    generating.value = false
  }
}

// 增减区域 / 用户
const meterDialog = reactive({ visible: false, meter: null })
function openMeter(row) {
  meterDialog.meter = row ? { ...row, id: row.meterId, sharePublic: row.sharePublic ? 1 : 0 } : null
  meterDialog.visible = true
}
async function afterMeterChange() {
  await reload()
  await loadSheet()
}
async function removeRow(row) {
  await ElMessageBox.confirm(
    `确定删除「${row.area} ${row.customerName || row.tenantName || ''}」的${energyType.value}表（${row.code}）？\n删除后它不再出现在未出账的月份和以后各月；已确认出账的月份不受影响。`,
    '删除区域/用户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
  await feePeriodApi.deleteMeters([row.meterId])
  ElMessage.success('已删除，本月的通知单需要重新生成')
  await afterMeterChange()
}

function summary({ columns, data }) {
  const sums = []
  const sum = (f) => data.reduce((s, r) => s + Number(f(r) || 0), 0)
  columns.forEach((col, i) => {
    if (i === 0) { sums[i] = '合计'; return }
    switch (col.label) {
      case '本期用量': sums[i] = num(sum(liveUsage)); break
      // 参与公摊户的比例之和,正常就是 100.00%
      case '公摊比例': sums[i] = ratioText(sum(r => (r.sharePublic && r.meterRole === 'TENANT' ? r.coefficient : 0))); break
      case '公摊度数': sums[i] = num(sum(r => r.publicUsage)); break
      case '度数+公摊': sums[i] = num(sum(r => r.totalUsage)); break
      case '电费': case '水费': sums[i] = '¥' + money(sum(r => r.fee)); break
      case '公摊费': sums[i] = '¥' + money(sum(r => r.publicFee)); break
      case '合计/元': sums[i] = '¥' + money(sum(r => r.totalFee)); break
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
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.summary-item.total strong { color: var(--el-color-danger); }
.cell-main { line-height: 1.4; }
.cell-sub { margin-top: 2px; font-size: 12px; line-height: 1.3; color: var(--el-text-color-secondary); }
.cell-sub.warn { color: var(--el-color-warning); }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
.ratio { font-weight: 600; color: var(--el-color-primary); font-variant-numeric: tabular-nums; }
.muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.dirty-tip { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); }
:deep(.row-dirty) td { background: var(--el-color-warning-light-9) !important; }
:deep(.row-nonshare) td { color: var(--el-text-color-secondary); }
</style>
