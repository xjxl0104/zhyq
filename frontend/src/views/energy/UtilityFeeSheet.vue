<template>
  <div class="page-container">
    <!-- 账期与总量:对应线下表的「10月总度数 / 光伏 / 单价」几个格子 -->
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="账期">
          <el-date-picker v-model="month" type="month" format="YYYY年M月" value-format="YYYY-MM"
                          :clearable="false" style="width: 150px" />
        </el-form-item>
        <template v-if="period">
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
          </template>
          <el-form-item>
            <el-button type="primary" :disabled="locked" :loading="state.saving" @click="saveHeader">保存总量</el-button>
            <el-tag v-if="locked" type="success" style="margin-left: 8px">已确认出账</el-tag>
          </el-form-item>
        </template>
      </el-form>
    </div>

    <div v-if="sheet" class="summary-grid">
      <div class="summary-item"><span>{{ isElectric ? '总度数(南网+光伏)' : `总表度数(${sheet.totalSource})` }}</span><strong>{{ num(sheet.total) }}</strong></div>
      <div class="summary-item"><span>全部分表用量</span><strong>{{ num(sheet.submeterUsage) }}</strong></div>
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
        <el-button :disabled="locked" :loading="generating" @click="generate">重新生成通知单</el-button>
        <el-button link type="primary" @click="$router.push('/energy/summary')">去缴费汇总 →</el-button>
        <span class="toolbar-tip">
          {{ isElectric
            ? '公摊度数 = 南网 + 光伏 − 全部分表用量；按各参与公摊租户的用量占比摊回。室外充电桩、未租单元关掉「参与公摊」后只付自身用量。'
            : '公摊度数 = 总表度数 − 分表用量合计；按各租户用量占比摊回。总表度数不填时按发票总表当月抄表自动取。' }}
        </span>
      </div>
      <el-table :data="rows" v-loading="loading || sheetLoading" border stripe size="small"
                :row-class-name="rowClass" show-summary :summary-method="summary">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="area" label="区域" width="100" />
        <el-table-column prop="code" label="表计编号" min-width="110" show-overflow-tooltip />
        <el-table-column label="用户名 / 租户" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="cell-main">{{ row.customerName || row.tenantName || '-' }}</div>
            <div v-if="row.tenantName && row.tenantName !== row.customerName" class="cell-sub">{{ row.tenantName }}</div>
            <div v-else-if="!row.tenantRefId && row.meterRole === 'TENANT'" class="cell-sub warn">未关联在租合同</div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.meterRole === 'PROPERTY' ? 'warning' : 'success'" effect="plain">
              {{ row.meterRole === 'PROPERTY' ? '物业表' : '租户表' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参与公摊" width="90" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.sharePublic" size="small" :disabled="locked || row.meterRole !== 'TENANT'"
                       @change="(v) => toggleShare(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="ratio" label="倍率" width="60" align="right" />
        <el-table-column label="上期读数" width="110" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.prevReading" :precision="2" :controls="false" size="small" :disabled="locked"
                             style="width: 100px" @change="() => markDirty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="本期读数" width="120" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.currReading" :precision="2" :controls="false" size="small" :disabled="locked"
                             placeholder="填读数" style="width: 110px" @change="() => markDirty(row)" />
          </template>
        </el-table-column>
        <el-table-column label="本期用量" width="105" align="right">
          <template #default="{ row }"><strong>{{ num(liveUsage(row)) }}</strong></template>
        </el-table-column>
        <el-table-column label="公摊比例" width="95" align="right">
          <template #default="{ row }">{{ row.sharePublic && row.meterRole === 'TENANT' ? (Number(row.coefficient || 0) * 100).toFixed(2) + '%' : '-' }}</template>
        </el-table-column>
        <el-table-column label="公摊度数" width="100" align="right">
          <template #default="{ row }">{{ num(row.publicUsage) }}</template>
        </el-table-column>
        <el-table-column label="度数+公摊" width="105" align="right">
          <template #default="{ row }">{{ num(row.totalUsage) }}</template>
        </el-table-column>
        <el-table-column :label="isElectric ? '电费' : '水费'" width="105" align="right">
          <template #default="{ row }">¥{{ money(row.fee) }}</template>
        </el-table-column>
        <el-table-column label="公摊费" width="105" align="right">
          <template #default="{ row }">¥{{ money(row.publicFee) }}</template>
        </el-table-column>
        <el-table-column label="合计/元" width="115" align="right">
          <template #default="{ row }"><strong>¥{{ money(row.totalFee) }}</strong></template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && !sheetLoading && !rows.length" class="empty-tip">
        没有在用的{{ isElectric ? '电' : '水' }}表分表。请先到「智能表计」登记表计（角色选「租户分表」，区域填如 1层A区）。
      </div>
      <div v-if="dirtyCount" class="dirty-tip">有 {{ dirtyCount }} 块表的读数改了还没保存；保存后公摊与金额会按新读数重算。</div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num } from './feePeriod'

const route = useRoute()
// 一组件两路由:电费 / 水费 只差能源类型与几个录入格
const energyType = computed(() => route.meta.energyType || '电')
const isElectric = computed(() => energyType.value === '电')

const sheet = ref(null)
const rows = ref([])
const sheetLoading = ref(false)
const savingReadings = ref(false)
const generating = ref(false)
const dirty = reactive(new Set())
const dirtyCount = computed(() => dirty.size)

const form = reactive({ electricGridUsage: 0, electricSolarUsage: 0, electricPrice: 0, waterTotalUsage: null, waterPrice: 0 })
const { month, period, loading, state, save } = useFeePeriod(async (p) => {
  Object.assign(form, {
    electricGridUsage: p.electricGridUsage, electricSolarUsage: p.electricSolarUsage, electricPrice: p.electricPrice,
    waterTotalUsage: p.waterTotalUsage, waterPrice: p.waterPrice
  })
  await loadSheet()
})
const locked = computed(() => period.value?.status === 'CONFIRMED')

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
// 切换电/水路由时组件被复用,要重新拉表
watch(energyType, loadSheet)

function liveUsage(row) {
  if (row.currReading == null || row.prevReading == null) return row.usage
  const diff = Number(row.currReading) - Number(row.prevReading)
  return diff < 0 ? 0 : Math.round(diff * Number(row.ratio || 1) * 100) / 100
}
function markDirty(row) { dirty.add(row.meterId) }
function rowClass({ row }) { return dirty.has(row.meterId) ? 'row-dirty' : (row.sharePublic ? '' : 'row-nonshare') }

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
    ElMessage.success(`已保存 ${r.saved} 块表的读数`)
    await loadSheet()
  } finally {
    savingReadings.value = false
  }
}

async function toggleShare(row, value) {
  await feePeriodApi.meterFlags({ meterId: row.meterId, sharePublic: value ? 1 : 0 })
  row.sharePublic = value
  ElMessage.success(value ? '已加入公摊' : '已设为不参与公摊（只付自身用量）')
  await loadSheet()
}

async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单，到「缴费汇总」核对与打印`)
    if (r.warnings?.length) ElMessage.warning({ message: r.warnings.join('；'), duration: 8000 })
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
      case '本期用量': sums[i] = num(sum(liveUsage)); break
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
.dirty-tip { margin-top: 8px; font-size: 12px; color: var(--el-color-warning); }
:deep(.row-dirty) td { background: var(--el-color-warning-light-9) !important; }
:deep(.row-nonshare) td { color: var(--el-text-color-secondary); }
</style>
