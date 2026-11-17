<template>
  <section class="fee-charts">
    <!-- 全园区按月的曲线 -->
    <div class="chart-card">
      <div class="chart-head">
        <strong>{{ cfg.lineTitle }}</strong>
        <span class="chart-sub">{{ year }} 年 · 跟随上面年月表翻到的年份</span>
      </div>
      <div class="chart-wrap" v-loading="loading">
        <div ref="lineEl" class="chart chart-line" />
        <div v-if="!loading && !hasYearData" class="chart-empty">{{ year }} 年还没有{{ cfg.label }}数据</div>
      </div>
    </div>

    <!-- 每个租户当月的柱状图 -->
    <div class="chart-card">
      <div class="chart-head">
        <strong>各租户{{ monthLabel }}{{ cfg.tenantTitle }}</strong>
        <el-select v-model="selMonth" size="small" style="width: 130px" placeholder="选择月份"
                   :disabled="!months.length" @change="loadTenants">
          <el-option v-for="m in months" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <span class="chart-sub">数据来自该月生成的缴费通知单</span>
      </div>
      <div class="chart-wrap chart-pair" v-loading="tenantLoading">
        <div class="chart-half">
          <div class="half-title">{{ cfg.leftTitle }}</div>
          <div ref="leftEl" class="chart" />
        </div>
        <div class="chart-half">
          <div class="half-title">{{ cfg.rightTitle }}</div>
          <div ref="rightEl" class="chart" />
        </div>
        <div v-if="!tenantLoading && !tenants.length" class="chart-empty">
          {{ selMonth ? '这个月还没有生成缴费通知单' : `${year} 年还没有录入的月份` }}
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { feePeriodApi } from '@/api/energy'
import { useChart } from '@/composables/useChart'
import { shortName } from '../feePeriod'

// kind: electric 电费 | water 水费 | garbage 垃圾处理费
const props = defineProps({
  kind: { type: String, required: true },
  year: { type: Number, required: true }
})

const COLORS = { primary: '#4f46e5', primarySoft: '#a5b4fc', amber: '#f59e0b', amberSoft: '#fcd34d', cyan: '#06b6d4', green: '#10b981' }

const CONFIGS = {
  electric: {
    label: '用电', lineTitle: '园区总用电量 · 月度用量与电费', tenantTitle: '用电度数与电费',
    leftTitle: '用电度数（度）', rightTitle: '电费（元）',
    usageName: '总用电量', usageUnit: '度', usageKey: 'electricUsage', feeName: '电费', feeKey: 'electricFee',
    left: [['自用', 'electricUsage', COLORS.primary], ['公摊', 'electricPublicUsage', COLORS.primarySoft]],
    right: [['电费', 'electricFee', COLORS.amber], ['公摊电费', 'electricPublicFee', COLORS.amberSoft]],
    unit: '度'
  },
  water: {
    label: '用水', lineTitle: '园区总用水量 · 月度用量与水费', tenantTitle: '用水量与水费',
    leftTitle: '用水量（m³）', rightTitle: '水费（元）',
    usageName: '总用水量', usageUnit: 'm³', usageKey: 'waterUsage', feeName: '水费', feeKey: 'waterFee',
    left: [['自用', 'waterUsage', COLORS.cyan], ['公摊', 'waterPublicUsage', '#a5e8f2']],
    right: [['水费', 'waterFee', COLORS.amber], ['公摊水费', 'waterPublicFee', COLORS.amberSoft]],
    unit: 'm³'
  },
  garbage: {
    label: '垃圾处理费', lineTitle: '园区垃圾处理费 · 月度总额与分摊', tenantTitle: '计租面积与垃圾处理费',
    leftTitle: '计租面积（㎡）', rightTitle: '垃圾处理费（元）',
    usageName: '垃圾总额', usageUnit: '元', usageKey: 'garbageTotal', feeName: '已分摊到租户', feeKey: 'garbageFee',
    left: [['计租面积', 'leaseArea', COLORS.cyan]],
    right: [['生活垃圾', 'domesticGarbageFee', COLORS.green], ['工业垃圾', 'industrialGarbageFee', COLORS.primary]],
    unit: '㎡'
  }
}
const cfg = computed(() => CONFIGS[props.kind] || CONFIGS.electric)

const lineEl = ref(null)
const leftEl = ref(null)
const rightEl = ref(null)
const loading = ref(false)
const tenantLoading = ref(false)
const overview = ref([])
const selMonth = ref('')
const tenants = ref([])

const fmt = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const hasYearData = computed(() => overview.value.some((c) => c.periodId && (Number(c[cfg.value.usageKey]) || Number(c[cfg.value.feeKey]))))
const months = computed(() => overview.value.filter((c) => c.periodId).map((c) => ({
  value: c.period, label: `${props.year}年${Number(c.period.slice(5))}月`, periodId: c.periodId, noticeCount: c.noticeCount || 0
})))
const monthLabel = computed(() => (selMonth.value ? ` ${selMonth.value.slice(0, 4)}年${Number(selMonth.value.slice(5))}月 ` : ' '))

// ---- 月度曲线:电/水 = 用量(左轴) + 费用(右轴);垃圾 = 总额 + 已分摊(同一个元轴)
function lineOption(t) {
  const c = cfg.value
  const byMonth = {}
  overview.value.forEach((x) => { if (x.periodId) byMonth[Number(x.period.slice(5))] = x })
  const pick = (key) => Array.from({ length: 12 }, (_, i) => (byMonth[i + 1] ? Number(byMonth[i + 1][key] || 0) : null))
  const garbage = props.kind === 'garbage'
  const axis = (name, color) => ({
    type: 'value', name, nameTextStyle: { color }, axisLabel: { color: t.axisLabel }, axisLine: { show: false },
    splitLine: { lineStyle: { color: t.splitLine } }
  })
  return {
    color: [COLORS.primary, COLORS.amber],
    grid: { left: 64, right: garbage ? 24 : 64, top: 48, bottom: 32 },
    legend: { top: 4, textStyle: { color: t.textColor } },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => (v == null ? '-' : fmt(v))
    },
    xAxis: { type: 'category', data: Array.from({ length: 12 }, (_, i) => `${i + 1}月`), axisLine: { lineStyle: { color: t.axisLine } }, axisLabel: { color: t.axisLabel } },
    yAxis: garbage ? [axis('元', t.axisLabel)] : [axis(c.usageUnit, COLORS.primary), { ...axis('元', COLORS.amber), splitLine: { show: false } }],
    series: [
      { name: `${c.usageName}（${c.usageUnit}）`, type: 'line', smooth: true, symbolSize: 7, data: pick(c.usageKey), areaStyle: { opacity: 0.08 } },
      { name: `${c.feeName}（元）`, type: 'line', smooth: true, symbolSize: 7, yAxisIndex: garbage ? 0 : 1, data: pick(c.feeKey) }
    ]
  }
}

// ---- 租户柱状图:每个租户一根柱子(自用/公摊 堆叠),按总量从大到小
function barOption(t, defs, unit) {
  const rows = tenants.value
  return {
    grid: { left: 64, right: 16, top: 36, bottom: 70 },
    legend: { top: 4, textStyle: { color: t.textColor } },
    tooltip: {
      trigger: 'axis', axisPointer: { type: 'shadow' },
      formatter: (items) => {
        const total = items.reduce((s, i) => s + Number(i.value || 0), 0)
        const lines = items.map((i) => `${i.marker}${i.seriesName}：${fmt(i.value)} ${unit}`).join('<br/>')
        return `${items[0].axisValueLabel}<br/>${lines}${items.length > 1 ? `<br/>合计：${fmt(total)} ${unit}` : ''}`
      }
    },
    xAxis: { type: 'category', data: rows.map((r) => r.label), axisLabel: { color: t.axisLabel, interval: 0, rotate: rows.length > 5 ? 28 : 0 }, axisLine: { lineStyle: { color: t.axisLine } } },
    yAxis: { type: 'value', axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    series: defs.map(([name, key, color], i) => ({
      name, type: 'bar', stack: 'total', barMaxWidth: 38, itemStyle: { color, borderRadius: i === defs.length - 1 ? [4, 4, 0, 0] : 0 },
      data: rows.map((r) => Number(r.raw[key] || 0))
    }))
  }
}

const line = useChart(lineEl, lineOption)
const left = useChart(leftEl, (t) => barOption(t, cfg.value.left, cfg.value.unit))
const right = useChart(rightEl, (t) => barOption(t, cfg.value.right, '元'))
const redraw = () => { line.refresh(); left.refresh(); right.refresh() }

let seq = 0
async function load() {
  const mine = ++seq
  loading.value = true
  try {
    const list = (await feePeriodApi.overview(props.year)) || []
    if (mine !== seq) return
    overview.value = list
    // 默认看最近一个生成过通知单的月份,没有就看最后一个录入的月份
    const keep = months.value.find((m) => m.value === selMonth.value)
    const withNotices = [...months.value].reverse().find((m) => m.noticeCount > 0)
    selMonth.value = keep ? keep.value : (withNotices || months.value[months.value.length - 1] || {}).value || ''
    line.refresh()
    await loadTenants()
  } finally {
    if (mine === seq) loading.value = false
  }
}

async function loadTenants() {
  const mine = seq
  const m = months.value.find((x) => x.value === selMonth.value)
  if (!m) {
    tenants.value = []
    redraw()
    return
  }
  tenantLoading.value = true
  try {
    const list = (await feePeriodApi.notices(m.periodId)) || []
    if (mine !== seq) return
    const key = cfg.value.right[0][1]
    const sizeKey = cfg.value.left[0][1]
    tenants.value = list
      .map((n) => ({ label: shortName(n.tenantName), raw: n }))
      // 垃圾页只看参与分摊的租户;电/水页把当月没有用量也没有费用的租户去掉
      .filter((r) => (props.kind === 'garbage' ? Number(r.raw.sharePublic) === 1 : Number(r.raw[key]) || Number(r.raw[sizeKey])))
      .sort((a, b) => (Number(b.raw[sizeKey]) + Number(b.raw[key])) - (Number(a.raw[sizeKey]) + Number(a.raw[key])))
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
.chart { width: 100%; height: 320px; }
.chart-line { height: 300px; }
.chart-pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.half-title { font-size: 13px; font-weight: 600; text-align: center; color: var(--el-text-color-regular); margin-top: 4px; }
.chart-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; color: var(--el-text-color-secondary); background: color-mix(in srgb, var(--el-fill-color-blank) 85%, transparent); z-index: 2; }
@media (max-width: 1100px) {
  .chart-pair { grid-template-columns: 1fr; }
}
</style>
