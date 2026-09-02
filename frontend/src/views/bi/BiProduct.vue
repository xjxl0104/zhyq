<template>
  <div class="commerce-page product-view">
    <header class="commerce-page__header">
      <div>
        <div class="commerce-page__eyebrow">Product intelligence</div>
        <h1 class="commerce-page__title">产品团队视图</h1>
        <p class="commerce-page__subtitle">从功能使用、流程闭环与用户反馈判断下一轮产品优先级。</p>
      </div>
      <div class="commerce-page__actions">
        <span class="commerce-chip"><el-icon><Calendar /></el-icon>近 30 天</span>
        <el-button class="commerce-action" type="primary" :loading="loading" @click="load">
          <el-icon><Refresh /></el-icon><span>刷新分析</span>
        </el-button>
      </div>
    </header>

    <div class="product-kpis">
      <article v-for="item in summaryCards" :key="item.label" class="product-kpi commerce-card">
        <div class="product-kpi__top">
          <span class="commerce-icon" :style="{ '--icon-color': item.color, '--icon-bg': item.soft }">
            <el-icon><component :is="item.icon" /></el-icon>
          </span>
          <span class="commerce-badge" :style="{ '--badge-color': item.color, '--badge-bg': item.soft }">{{ item.tag }}</span>
        </div>
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </div>

    <section class="commerce-card module-card">
      <div class="commerce-card__head">
        <div>
          <h2 class="commerce-card__title">模块使用表现</h2>
          <p class="commerce-card__meta">请求量衡量使用强度，独立用户数衡量覆盖范围</p>
        </div>
        <span class="legend-pills"><i class="requests"></i>请求数 <i class="users"></i>独立用户</span>
      </div>
      <div ref="moduleChartRef" class="module-chart" aria-label="模块使用率图"></div>
    </section>

    <div class="insight-grid">
      <section class="commerce-card insight-card">
        <div class="commerce-card__head">
          <div><h2 class="commerce-card__title">流程闭环率</h2><p class="commerce-card__meta">本月流程完成效率</p></div>
          <span class="commerce-icon"><el-icon><CircleCheck /></el-icon></span>
        </div>
        <div class="gauge-wrap">
          <div ref="gaugeChartRef" class="gauge-chart" aria-label="流程闭环率仪表图"></div>
          <div class="flow-counts">
            <span><small>发起</small><strong>{{ flow.total_started ?? '-' }}</strong></span>
            <i></i>
            <span><small>完成</small><strong>{{ flow.total_completed ?? '-' }}</strong></span>
          </div>
        </div>
      </section>

      <section class="commerce-card insight-card feedback-chart-card">
        <div class="commerce-card__head">
          <div><h2 class="commerce-card__title">反馈状态分布</h2><p class="commerce-card__meta">当前反馈池的处置结构</p></div>
          <span class="commerce-icon"><el-icon><ChatDotSquare /></el-icon></span>
        </div>
        <div ref="feedbackChartRef" class="feedback-chart" aria-label="反馈状态分布图"></div>
      </section>
    </div>

    <section class="commerce-card feedback-table-card">
      <div class="commerce-card__head">
        <div>
          <h2 class="commerce-card__title">反馈看板</h2>
          <p class="commerce-card__meta">按模块与状态拆解，快速发现积压区域</p>
        </div>
        <span class="feedback-total">共 {{ feedbackTotal }} 条</span>
      </div>
      <div class="table-wrap">
        <el-table :data="feedbackData" stripe>
          <el-table-column prop="module" label="模块" min-width="180">
            <template #default="{ row }"><span class="module-name"><i></i>{{ row.module }}</span></template>
          </el-table-column>
          <el-table-column prop="status" label="状态" min-width="140">
            <template #default="{ row }">
              <span class="status-pill" :style="statusStyle(row.status)">{{ statusMap[row.status] || row.status }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="cnt" label="数量" width="120">
            <template #default="{ row }"><strong class="count-cell">{{ row.cnt }}</strong></template>
          </el-table-column>
        </el-table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { biApi } from '@/api/bi'
import { useChart } from '@/composables/useChart'

const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const STATUS_COLOR = { 1: '#d99022', 2: '#0a24e9', 3: '#277f9d', 4: '#5c9764', 5: '#7256d8', 6: '#8b95a5' }
const STATUS_SOFT = { 1: '#fff7e8', 2: '#eef0ff', 3: '#eaf8fb', 4: '#edf7ef', 5: '#f3efff', 6: '#f0f2f5' }

const moduleData = ref([])
const flow = ref({})
const feedbackData = ref([])
const loading = ref(false)
const moduleChartRef = ref(null)
const gaugeChartRef = ref(null)
const feedbackChartRef = ref(null)

const closeRate = computed(() => {
  const started = Number(flow.value.total_started || 0)
  const completed = Number(flow.value.total_completed || 0)
  return started ? Math.min(100, Math.round(completed / started * 100)) : 0
})
const totalRequests = computed(() => moduleData.value.reduce((sum, item) => sum + Number(item.request_count || 0), 0))
const totalUsers = computed(() => moduleData.value.reduce((sum, item) => sum + Number(item.user_count || 0), 0))
const feedbackTotal = computed(() => feedbackData.value.reduce((sum, item) => sum + Number(item.cnt || 0), 0))
const numberFmt = (value) => Number(value || 0).toLocaleString('zh-CN')

const summaryCards = computed(() => [
  { label: '活跃模块', value: moduleData.value.length, tag: '覆盖', icon: 'Grid', color: '#0a24e9', soft: '#eef0ff' },
  { label: '累计请求', value: numberFmt(totalRequests.value), tag: '使用强度', icon: 'DataLine', color: '#7256d8', soft: '#f3efff' },
  { label: '独立用户', value: numberFmt(totalUsers.value), tag: '触达', icon: 'UserFilled', color: '#277f9d', soft: '#eaf8fb' },
  { label: '流程闭环率', value: `${closeRate.value}%`, tag: '本月', icon: 'CircleCheck', color: '#5c9764', soft: '#edf7ef' }
])

function statusStyle(status) {
  return { color: STATUS_COLOR[status] || '#68708a', background: STATUS_SOFT[status] || '#f0f2f5' }
}

const moduleChart = useChart(moduleChartRef, (theme) => {
  const data = moduleData.value
  return {
    tooltip: { trigger: 'axis', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
    grid: { left: 18, right: 18, top: 34, bottom: 18, containLabel: true },
    xAxis: { type: 'category', data: data.map(x => x.module), axisTick: { show: false }, axisLabel: { color: theme.axisLabel, interval: 0, rotate: data.length > 7 ? 24 : 0 }, axisLine: { lineStyle: { color: theme.axisLine } } },
    yAxis: [
      { type: 'value', axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: theme.axisLabel }, splitLine: { lineStyle: { color: theme.splitLine, type: 'dashed' } } },
      { type: 'value', axisLine: { show: false }, axisTick: { show: false }, axisLabel: { color: theme.axisLabel }, splitLine: { show: false } }
    ],
    series: [
      { name: '请求数', type: 'bar', data: data.map(x => x.request_count), barMaxWidth: 30, itemStyle: { borderRadius: [8, 8, 0, 0], color: '#0a24e9' } },
      { name: '独立用户', type: 'line', yAxisIndex: 1, smooth: true, data: data.map(x => x.user_count), symbol: 'circle', symbolSize: 7, itemStyle: { color: '#7256d8', borderColor: '#fff', borderWidth: 2 }, lineStyle: { width: 3, color: '#7256d8' } }
    ]
  }
})

const gauge = useChart(gaugeChartRef, () => ({
  series: [{
    type: 'gauge',
    min: 0,
    max: 100,
    radius: '88%',
    startAngle: 205,
    endAngle: -25,
    progress: { show: true, width: 15, roundCap: true, itemStyle: { color: '#0a24e9' } },
    axisLine: { roundCap: true, lineStyle: { width: 15, color: [[1, '#eef0ff']] } },
    pointer: { show: false },
    axisTick: { show: false },
    splitLine: { show: false },
    axisLabel: { show: false },
    anchor: { show: false },
    title: { offsetCenter: [0, '28%'], color: '#a9b6c0', fontSize: 11 },
    detail: { valueAnimation: true, formatter: '{value}%', color: '#0b0d17', fontSize: 32, fontWeight: 700, offsetCenter: [0, '-6%'] },
    data: [{ value: closeRate.value, name: '闭环率' }]
  }]
}))

const feedbackChart = useChart(feedbackChartRef, (theme) => {
  const aggregate = {}
  feedbackData.value.forEach(row => { aggregate[row.status] = (aggregate[row.status] || 0) + Number(row.cnt || 0) })
  const data = Object.keys(aggregate).map(status => ({ name: statusMap[status] || status, value: aggregate[status], itemStyle: { color: STATUS_COLOR[status] } }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)', backgroundColor: '#0b0d17', borderWidth: 0, textStyle: { color: '#fff' } },
    legend: { right: 10, top: 'center', orient: 'vertical', icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { color: theme.textColor, fontSize: 10 } },
    series: [{
      type: 'pie',
      radius: ['52%', '72%'],
      center: ['37%', '48%'],
      itemStyle: { borderColor: '#fff', borderWidth: 4, borderRadius: 6 },
      label: { show: false },
      data
    }]
  }
})

async function load() {
  loading.value = true
  try {
    const [modules, flowData, feedback] = await Promise.allSettled([
      biApi.moduleUsage(),
      biApi.flowAnalysis(),
      biApi.feedbackBoard()
    ])
    if (modules.status === 'fulfilled') moduleData.value = modules.value || []
    if (flowData.status === 'fulfilled') flow.value = flowData.value || {}
    if (feedback.status === 'fulfilled') feedbackData.value = feedback.value || []
    moduleChart.refresh()
    gauge.refresh()
    feedbackChart.refresh()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.product-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 16px; }
.product-kpi { display: flex; flex-direction: column; gap: 7px; padding: 17px 18px; }
.product-kpi__top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.product-kpi > strong { color: var(--text-title); font-size: clamp(24px, 2vw, 30px); font-weight: 740; letter-spacing: -.045em; font-variant-numeric: tabular-nums; }
.product-kpi > span { color: var(--text-secondary); font-size: 11px; }

.module-card { margin-bottom: 16px; }
.module-chart { height: 350px; padding: 4px 14px 14px; }
.legend-pills { display: flex; align-items: center; gap: 6px; color: var(--text-secondary); font-size: 10px; }
.legend-pills i { width: 7px; height: 7px; margin-left: 5px; border-radius: 50%; }
.legend-pills .requests { background: #0a24e9; }
.legend-pills .users { background: #7256d8; }

.insight-grid { display: grid; grid-template-columns: minmax(300px, .75fr) minmax(0, 1.25fr); gap: 16px; margin-bottom: 16px; }
.insight-card { min-height: 350px; }
.gauge-wrap { display: grid; grid-template-rows: 236px auto; padding: 4px 20px 20px; }
.gauge-chart { width: 100%; height: 250px; }
.flow-counts { display: flex; align-items: center; justify-content: center; gap: 28px; margin-top: -4px; padding: 12px; background: #f8f9fb; border-radius: 12px; }
.flow-counts > span { display: grid; justify-items: center; gap: 3px; min-width: 64px; }
.flow-counts small { color: var(--text-muted); font-size: 9px; }
.flow-counts strong { color: var(--text-title); font-size: 17px; }
.flow-counts i { width: 1px; height: 26px; background: var(--border); }
.feedback-chart { width: 100%; height: 284px; padding: 0 12px 12px; }

.feedback-table-card { overflow: hidden; }
.feedback-total { color: var(--text-secondary); font-size: 11px; }
.table-wrap { padding: 14px 20px 20px; }
.table-wrap :deep(.el-table) { border-radius: 12px; overflow: hidden; }
.module-name { display: inline-flex; align-items: center; gap: 9px; color: var(--text-title); font-weight: 600; }
.module-name i { width: 7px; height: 7px; background: #0a24e9; border-radius: 50%; box-shadow: 0 0 0 4px #eef0ff; }
.status-pill { display: inline-flex; padding: 5px 9px; border-radius: 999px; font-size: 10px; font-weight: 650; }
.count-cell { color: var(--text-title); font-variant-numeric: tabular-nums; }

@media (max-width: 1000px) {
  .product-kpis { grid-template-columns: 1fr 1fr; }
  .insight-grid { grid-template-columns: 1fr; }
}
@media (max-width: 680px) {
  .product-kpis { grid-template-columns: 1fr; }
  .feedback-chart { height: 320px; }
}
</style>
