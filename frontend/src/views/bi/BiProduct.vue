<template>
  <div class="page-container" style="padding:20px">
    <!-- 模块使用率 -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>模块使用率（近30天）</span></template>
      <div ref="moduleChartRef" style="height:320px"></div>
    </el-card>

    <el-row :gutter="16" style="margin-bottom:20px">
      <!-- 流程闭环仪表盘 -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><span>流程闭环率（本月）</span></template>
          <div ref="gaugeChartRef" style="height:300px"></div>
          <div style="text-align:center;color:var(--el-text-color-secondary);margin-top:-8px">
            发起 {{ flow.total_started ?? '-' }} · 完成 {{ flow.total_completed ?? '-' }}
          </div>
        </el-card>
      </el-col>
      <!-- 反馈状态分布 -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><span>反馈状态分布</span></template>
          <div ref="feedbackChartRef" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 反馈明细 -->
    <el-card shadow="hover">
      <template #header><span>反馈看板（按模块/状态）</span></template>
      <el-table :data="feedbackData" border stripe>
        <el-table-column prop="module" label="模块" />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">{{ statusMap[row.status] || row.status }}</template>
        </el-table-column>
        <el-table-column prop="cnt" label="数量" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { biApi } from '@/api/bi'
import { useChart } from '@/composables/useChart'

const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const STATUS_COLOR = { 1: '#f6bd16', 2: '#5b8ff9', 3: '#6dc8ec', 4: '#5ad8a6', 5: '#5ad8a6', 6: '#b0b8c4' }

const moduleData = ref([])
const flow = ref({})
const feedbackData = ref([])

const moduleChartRef = ref(null)
const gaugeChartRef = ref(null)
const feedbackChartRef = ref(null)

const closeRate = computed(() => {
  const s = Number(flow.value.total_started || 0)
  const c = Number(flow.value.total_completed || 0)
  return s ? Math.round(c / s * 100) : 0
})

// 模块使用率:请求数柱 + 独立用户折线(双轴)
const moduleChart = useChart(moduleChartRef, (t) => {
  const d = moduleData.value
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: t.textColor } },
    grid: { left: 50, right: 50, top: 36, bottom: 60 },
    xAxis: { type: 'category', data: d.map(x => x.module), axisLabel: { rotate: 30, color: t.axisLabel }, axisLine: { lineStyle: { color: t.axisLine } } },
    yAxis: [
      { type: 'value', name: '请求数', axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
      { type: 'value', name: '用户数', axisLabel: { color: t.axisLabel }, splitLine: { show: false } }
    ],
    series: [
      { name: '请求数', type: 'bar', data: d.map(x => x.request_count), barWidth: '46%', itemStyle: { borderRadius: [4,4,0,0], color: '#5b8ff9' } },
      { name: '独立用户数', type: 'line', yAxisIndex: 1, smooth: true, data: d.map(x => x.user_count), itemStyle: { color: '#f6bd16' } }
    ]
  }
})

// 闭环率仪表盘
const gauge = useChart(gaugeChartRef, () => ({
  series: [{
    type: 'gauge', min: 0, max: 100, radius: '92%',
    progress: { show: true, width: 14 },
    axisLine: { lineStyle: { width: 14 } },
    axisTick: { show: false }, splitLine: { length: 12 }, axisLabel: { distance: 18, fontSize: 10 },
    pointer: { width: 5 },
    detail: { valueAnimation: true, formatter: '{value}%', fontSize: 30, offsetCenter: [0, '62%'] },
    data: [{ value: closeRate.value, name: '闭环率', title: { offsetCenter: [0, '86%'] } }]
  }]
}))

// 反馈状态分布(环形)
const feedbackChart = useChart(feedbackChartRef, (t) => {
  const agg = {}
  feedbackData.value.forEach(r => { agg[r.status] = (agg[r.status] || 0) + Number(r.cnt || 0) })
  const data = Object.keys(agg).map(k => ({ name: statusMap[k] || k, value: agg[k], itemStyle: { color: STATUS_COLOR[k] } }))
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: t.textColor }, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['42%', '66%'], center: ['50%', '44%'],
      itemStyle: { borderColor: 'var(--el-bg-color)', borderWidth: 2, borderRadius: 4 },
      label: { color: t.textColor, formatter: '{b}\n{c}' }, data
    }]
  }
})

onMounted(async () => {
  const [mod, fl, fb] = await Promise.all([biApi.moduleUsage(), biApi.flowAnalysis(), biApi.feedbackBoard()])
  moduleData.value = mod || []
  flow.value = fl || {}
  feedbackData.value = fb || []
  moduleChart.refresh(); gauge.refresh(); feedbackChart.refresh()
})
</script>
