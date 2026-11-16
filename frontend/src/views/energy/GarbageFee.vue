<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="账期">
          <el-date-picker v-model="month" type="month" format="YYYY年M月" value-format="YYYY-MM"
                          :clearable="false" style="width: 150px" />
        </el-form-item>
        <template v-if="period">
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
            <el-tag v-if="locked" type="success" style="margin-left: 8px">已确认出账</el-tag>
          </el-form-item>
        </template>
      </el-form>
    </div>

    <div v-if="sheet" class="summary-grid">
      <div class="summary-item"><span>生活垃圾总额</span><strong>¥{{ money(period?.domesticGarbageAmount) }}</strong></div>
      <div class="summary-item"><span>工业垃圾总额</span><strong>¥{{ money(period?.industrialGarbageAmount) }}</strong></div>
      <div class="summary-item hl"><span>参与分摊计租面积</span><strong>{{ num(sheet.totalLeaseArea) }} ㎡</strong></div>
      <div class="summary-item"><span>参与分摊单元</span><strong>{{ sheet.rows.filter(r => r.sharePublic).length }} / {{ sheet.rows.length }}</strong></div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <el-button :disabled="locked" :loading="generating" @click="generate">重新生成通知单</el-button>
        <el-button link type="primary" @click="$router.push('/energy/summary')">去缴费汇总 →</el-button>
        <span class="toolbar-tip">两笔总额按各单元「计租面积 ÷ 参与分摊的计租面积合计」分摊，只有园区租户（参与公摊的单元）分摊；充电桩、未租单元不摊。计租面积在此处直接改，同区域所有表计一起生效。</span>
      </div>
      <el-table :data="sheet?.rows || []" v-loading="loading || sheetLoading" border stripe size="small"
                show-summary :summary-method="summary">
        <el-table-column type="index" label="序号" width="55" />
        <el-table-column prop="unit" label="区域 / 单元" width="120" />
        <el-table-column prop="tenantName" label="用户名 / 租户" min-width="160" show-overflow-tooltip />
        <el-table-column label="参与分摊" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.sharePublic ? 'success' : 'info'" effect="plain">{{ row.sharePublic ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="计租面积(㎡)" width="150" align="right">
          <template #default="{ row }">
            <el-input-number :model-value="row.leaseArea" :min="0" :precision="2" :controls="false" size="small"
                             :disabled="locked" style="width: 120px" @change="(v) => saveArea(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="计租/总计租面积" width="130" align="right">
          <template #default="{ row }">{{ row.sharePublic ? (Number(row.ratio || 0) * 100).toFixed(2) + '%' : '-' }}</template>
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
      </el-table>
      <div v-if="!loading && !sheetLoading && !(sheet?.rows || []).length" class="empty-tip">
        没有在用的租户表计。单元来自表计的「区域」字段，请先到「智能表计」登记。
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { feePeriodApi } from '@/api/energy'
import { useFeePeriod, money, num } from './feePeriod'

const sheet = ref(null)
const sheetLoading = ref(false)
const generating = ref(false)
const form = reactive({ domesticGarbageAmount: 0, domesticGarbageNote: '', industrialGarbageAmount: 0, industrialGarbageNote: '' })
const { month, period, loading, state, save } = useFeePeriod(async (p) => {
  Object.assign(form, {
    domesticGarbageAmount: p.domesticGarbageAmount, domesticGarbageNote: p.domesticGarbageNote,
    industrialGarbageAmount: p.industrialGarbageAmount, industrialGarbageNote: p.industrialGarbageNote
  })
  await loadSheet()
})
const locked = computed(() => period.value?.status === 'CONFIRMED')

async function loadSheet() {
  if (!period.value) return
  sheetLoading.value = true
  try {
    sheet.value = await feePeriodApi.garbageSheet(period.value.id)
  } finally {
    sheetLoading.value = false
  }
}
async function saveHeader() {
  await save(form)
  ElMessage.success('已保存，分摊已重算')
  await loadSheet()
}
async function saveArea(row, value) {
  if (value == null) return
  // 一个单元可能有几块表(30倍/80倍电表、水表),面积挂在同区域全部表上,后端按区域一起改
  await feePeriodApi.meterFlags({ meterId: row.meterIds[0], leaseArea: value, applyAreaToUnit: true })
  ElMessage.success(`「${row.unit}」计租面积已更新`)
  await loadSheet()
}
async function generate() {
  generating.value = true
  try {
    const r = await feePeriodApi.generate(period.value.id)
    ElMessage.success(`已生成 ${r.notices?.length || 0} 张通知单`)
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
      case '计租面积(㎡)': sums[i] = num(sum(r => r.sharePublic ? r.leaseArea : 0)); break
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
.toolbar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.toolbar-tip { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 10px; margin-bottom: 12px; }
.summary-item { padding: 10px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; display: flex; flex-direction: column; gap: 4px; background: var(--el-fill-color-blank); }
.summary-item span { font-size: 12px; color: var(--el-text-color-secondary); }
.summary-item strong { font-size: 17px; font-variant-numeric: tabular-nums; }
.summary-item.hl strong { color: var(--el-color-primary); }
.empty-tip { text-align: center; color: var(--el-text-color-secondary); padding: 20px 0; font-size: 13px; }
</style>
