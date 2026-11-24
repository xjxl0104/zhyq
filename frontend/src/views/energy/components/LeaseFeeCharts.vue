<template>
  <section class="fee-charts">
    <div class="chart-card">
      <div class="chart-head">
        <strong>{{ isRent ? '园区月度应付租金趋势' : '园区月度应付物业管理费趋势' }}</strong>
        <span class="chart-sub">{{ year }} 年 · 跟随上面年月表翻到的年份</span>
      </div>
      <div class="chart-wrap" v-loading="loading">
        <div ref="lineEl" class="chart chart-line" />
        <div v-if="!loading && !hasYearData" class="chart-empty">{{ year }} 年暂无在租合同数据</div>
      </div>
    </div>

    <div class="chart-card">
      <div class="chart-head">
        <strong>各租户 {{ monthLabel }} {{ isRent ? '计租面积与应付租金' : '计租面积与应付物业费' }}</strong>
        <el-select v-model="selMonth" size="small" style="width: 130px" placeholder="选择月份"
                   :disabled="!months.length" @change="loadTenants">
          <el-option v-for="m in months" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
      </div>
      <div class="chart-wrap chart-pair" v-loading="tenantLoading">
        <div class="chart-half">
          <div class="half-title">计租面积（㎡）</div>
          <div ref="leftEl" class="chart" />
        </div>
        <div class="chart-half">
          <div class="half-title">{{ isRent ? '应付租金（元）' : '应付物业管理费（元）' }}</div>
          <div ref="rightEl" class="chart" />
        </div>
        <div v-if="!tenantLoading && !tenants.length" class="chart-empty">
          {{ selMonth ? '本月暂无在租合同' : `${year} 年暂无在租月份` }}
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { leaseFeeApi } from '@/api/energy'
import { useChart } from '@/composables/useChart'
import { shortName } from '../feePeriod'

const props = defineProps({
  kind: { type: String, required: true }, // rent | property
  year: { type: Number, required: true }
})

const isRent = computed(() => props.kind === 'rent')
const lineEl = ref(null)
const leftEl = ref(null)
const rightEl = ref(null)
const loading = ref(false)
const tenantLoading = ref(false)
const overview = ref([])
const selMonth = ref('')
const tenants = ref([])

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const hasYearData = computed(() => overview.value.some((c) => Number(c.contractCount || 0) > 0))
const months = computed(() => overview.value.map((c) => ({
  value: c.period, label: `${props.year}年${Number(c.period.slice(5))}月`, periodId: c.periodId
})))
const monthLabel = computed(() => (selMonth.value ? `${selMonth.value.slice(0, 4)}年${Number(selMonth.value.slice(5))}月` : ''))

function lineOption(t) {
  const byMonth = {}
  overview.value.forEach((x) => { byMonth[Number(x.period.slice(5))] = x })
  const key = isRent.value ? 'rentTotal' : 'propertyTotal'
  const color = isRent.value ? '#4f46e5' : '#10b981'
  return {
    color: [color],
    grid: { left: 76, right: 24, top: 44, bottom: 32 },
    legend: { top: 4, textStyle: { color: t.textColor } },
    tooltip: { trigger: 'axis', valueFormatter: (v) => (v == null ? '-' : `¥${fmt(v)}`) },
    xAxis: { type: 'category', data: Array.from({ length: 12 }, (_, i) => `${i + 1}月`), axisLine: { lineStyle: { color: t.axisLine } }, axisLabel: { color: t.axisLabel } },
    yAxis: { type: 'value', name: '元', axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    series: [{
      name: isRent.value ? '月度应付租金（元）' : '月度应付物业管理费（元）',
      type: 'line', smooth: true, symbolSize: 7, areaStyle: { opacity: 0.1 },
      data: Array.from({ length: 12 }, (_, i) => (byMonth[i + 1] ? Number(byMonth[i + 1][key] || 0) : null))
    }]
  }
}

function barOption(t, field, unit, color) {
  const rows = tenants.value
  return {
    grid: { left: 76, right: 16, top: 28, bottom: 70 },
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, valueFormatter: (v) => `${fmt(v)} ${unit}` },
    xAxis: { type: 'category', data: rows.map((r) => r.label), axisLabel: { color: t.axisLabel, interval: 0, rotate: rows.length > 5 ? 24 : 0 }, axisLine: { lineStyle: { color: t.axisLine } } },
    yAxis: { type: 'value', axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    series: [{ type: 'bar', barMaxWidth: 38, itemStyle: { color, borderRadius: [4, 4, 0, 0] }, data: rows.map((r) => Number(r[field] || 0)) }]
  }
}

const line = useChart(lineEl, lineOption)
const left = useChart(leftEl, (t) => barOption(t, 'area', '㎡', '#06b6d4'))
const right = useChart(rightEl, (t) => barOption(t, 'amount', '元', isRent.value ? '#4f46e5' : '#10b981'))
const redraw = () => { line.refresh(); left.refresh(); right.refresh() }

let seq = 0
async function load() {
  const mine = ++seq
  loading.value = true
  try {
    const list = (await leaseFeeApi.overview(props.year)) || []
    if (mine !== seq) return
    overview.value = list
    const keep = months.value.find((m) => m.value === selMonth.value)
    const nonZero = [...list].reverse().find((c) => Number(isRent.value ? c.rentTotal : c.propertyTotal) > 0)
    selMonth.value = keep ? keep.value : (nonZero?.period || months.value[months.value.length - 1]?.value || '')
    line.refresh()
    await loadTenants()
  } finally {
    if (mine === seq) loading.value = false
  }
}

async function loadTenants() {
  const mine = seq
  if (!selMonth.value) { tenants.value = []; redraw(); return }
  tenantLoading.value = true
  try {
    const period = await leaseFeeApi.open(selMonth.value)
    const sheet = await leaseFeeApi.sheet(period.id, isRent.value ? 'RENT' : 'PROPERTY')
    if (mine !== seq) return
    const grouped = new Map()
    for (const r of (sheet?.rows || [])) {
      const k = r.tenantName || '-'
      const cur = grouped.get(k) || { label: shortName(k), area: 0, amount: 0 }
      cur.area += Number(r.leaseArea || 0)
      cur.amount += Number(isRent.value ? r.payableRent : r.payableProperty || 0)
      grouped.set(k, cur)
    }
    tenants.value = [...grouped.values()].sort((a, b) => b.amount - a.amount || b.area - a.area)
    redraw()
  } finally {
    tenantLoading.value = false
  }
}

watch(() => props.year, load, { immediate: true })
watch(() => props.kind, () => { selMonth.value = ''; load() })
defineExpose({ refresh: load })
</script>

<style scoped>
.fee-charts { display: grid; gap: 14px; margin-top: 18px; }
.chart-card { border: 1px solid var(--el-border-color-lighter); border-radius: 10px; padding: 14px 16px 10px; background: var(--el-fill-color-blank); }
.chart-head { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-bottom: 6px; }
.chart-head strong { font-size: 15px; }
.chart-sub { font-size: 12px; color: var(--el-text-color-secondary); }
.chart-wrap { position: relative; min-height: 120px; }
.chart { width: 100%; height: 300px; }
.chart-line { height: 280px; }
.chart-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.half-title { font-size: 13px; font-weight: 600; text-align: center; color: var(--el-text-color-regular); margin-top: 4px; }
.chart-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; color: var(--el-text-color-secondary); background: color-mix(in srgb, var(--el-fill-color-blank) 85%, transparent); z-index: 2; }
@media (max-width: 1100px) { .chart-pair { grid-template-columns: 1fr; } }
</style>
