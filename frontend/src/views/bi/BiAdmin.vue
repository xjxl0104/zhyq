<template>
  <div class="page-container" style="padding:20px">
    <el-alert type="info" :closable="false" style="margin-bottom:20px">
      本页数据仅用于推广参考，不作为绩效考核依据。
    </el-alert>

    <!-- 北极星指标 -->
    <el-row :gutter="16" style="margin-bottom:20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header><span>北极星指标（本周活跃且完成流程用户数）</span></template>
          <div style="text-align:center">
            <span style="font-size:36px;font-weight:700;color:var(--el-color-primary)">{{ northStar.current_week ?? '-' }}</span>
            <div style="margin-top:8px;color:var(--el-text-color-secondary)">
              环比上周: {{ northStar.prev_week ?? '-' }}
              <span v-if="northStar.current_week && northStar.prev_week" :style="{color: ratio >= 0 ? '#67c23a' : '#f56c6c'}">
                ({{ ratio >= 0 ? '+' : '' }}{{ ratio }}%)
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom:20px">
      <!-- 部门五维雷达 -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span>部门五维能力雷达</span></template>
          <div ref="radarChartRef" style="height:340px"></div>
        </el-card>
      </el-col>
      <!-- 综合得分排行(条形) -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><span>部门综合得分排行</span></template>
          <div ref="rankChartRef" style="height:340px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 部门明细表(点击下钻) -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>部门评分明细（点击行查看成员）</span></template>
      <el-table :data="deptList" border stripe @row-click="drillDown" style="cursor:pointer">
        <el-table-column prop="dept_name" label="部门" />
        <el-table-column prop="avg_coverage" label="覆盖广度" width="100" />
        <el-table-column prop="avg_flow" label="流程闭环" width="100" />
        <el-table-column prop="avg_frequency" label="使用频率" width="100" />
        <el-table-column prop="avg_data" label="数据贡献" width="100" />
        <el-table-column prop="avg_feedback" label="反馈贡献" width="100" />
        <el-table-column prop="avg_score" label="综合得分" width="100" sortable />
      </el-table>
    </el-card>

    <!-- 趋势图 -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>活跃用户趋势</span></template>
      <div ref="trendChartRef" style="height:300px"></div>
    </el-card>

    <!-- 部门下钻弹窗 -->
    <el-dialog v-model="drillVisible" :title="`${drillDept} — 成员评分`" width="700px">
      <el-table :data="drillList" border>
        <el-table-column prop="nickname" label="姓名" />
        <el-table-column prop="dim_coverage" label="覆盖" width="70" />
        <el-table-column prop="dim_flow" label="流程" width="70" />
        <el-table-column prop="dim_frequency" label="频率" width="70" />
        <el-table-column prop="dim_data" label="数据" width="70" />
        <el-table-column prop="dim_feedback" label="反馈" width="70" />
        <el-table-column prop="total_score" label="综合" width="70" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { biApi } from '@/api/bi'
import { useChart } from '@/composables/useChart'

const northStar = ref({})
const deptList = ref([])
const trendData = ref([])
const drillVisible = ref(false)
const drillDept = ref('')
const drillList = ref([])

const radarChartRef = ref(null)
const rankChartRef = ref(null)
const trendChartRef = ref(null)

const ratio = computed(() => {
  const c = northStar.value.current_week
  const p = northStar.value.prev_week
  if (!p) return 0
  return Math.round((c - p) / p * 100)
})

const DIMS = ['覆盖广度', '流程闭环', '使用频率', '数据贡献', '反馈贡献']
const COLORS = ['#5b8ff9', '#5ad8a6', '#f6bd16', '#e8684a', '#6dc8ec', '#945fb9']

// 部门五维雷达
const radar = useChart(radarChartRef, (t) => ({
  tooltip: {},
  legend: { bottom: 0, textStyle: { color: t.textColor }, type: 'scroll' },
  radar: {
    indicator: DIMS.map(name => ({ name, max: 100 })),
    axisName: { color: t.axisLabel },
    splitLine: { lineStyle: { color: t.splitLine } },
    splitArea: { areaStyle: { color: ['transparent'] } },
    axisLine: { lineStyle: { color: t.splitLine } }
  },
  series: [{
    type: 'radar', areaStyle: { opacity: 0.12 },
    data: deptList.value.map((d, i) => ({
      name: d.dept_name, symbolSize: 4,
      lineStyle: { color: COLORS[i % COLORS.length] }, itemStyle: { color: COLORS[i % COLORS.length] },
      value: [d.avg_coverage, d.avg_flow, d.avg_frequency, d.avg_data, d.avg_feedback]
    }))
  }]
}))

// 综合得分排行(横向条形)
const rank = useChart(rankChartRef, (t) => {
  const rows = [...deptList.value].sort((a, b) => a.avg_score - b.avg_score)
  return {
    grid: { left: 90, right: 30, top: 16, bottom: 16 },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', max: 100, axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    yAxis: { type: 'category', data: rows.map(d => d.dept_name), axisLabel: { color: t.axisLabel }, axisLine: { lineStyle: { color: t.axisLine } } },
    series: [{
      type: 'bar', data: rows.map(d => d.avg_score), barWidth: '52%',
      label: { show: true, position: 'right', color: t.textColor },
      itemStyle: { borderRadius: [0, 6, 6, 0], color: '#5b8ff9' }
    }]
  }
})

// 活跃用户趋势
const trend = useChart(trendChartRef, (t) => {
  const data = [...trendData.value].reverse()
  return {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: t.textColor } },
    grid: { left: 50, right: 24, top: 36, bottom: 30 },
    xAxis: { type: 'category', data: data.map(d => d.period_start), axisLabel: { color: t.axisLabel }, axisLine: { lineStyle: { color: t.axisLine } } },
    yAxis: { type: 'value', axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    series: [
      { name: '活跃用户', type: 'line', smooth: true, data: data.map(d => d.active_users), itemStyle: { color: '#5b8ff9' }, areaStyle: { opacity: 0.1 } },
      { name: '核心操作用户', type: 'line', smooth: true, data: data.map(d => d.core_users), itemStyle: { color: '#5ad8a6' } }
    ]
  }
})

async function drillDown(row) {
  drillDept.value = row.dept_name
  drillList.value = await biApi.deptDetail(row.dept_id) || []
  drillVisible.value = true
}

onMounted(async () => {
  const [ns, dept, tr] = await Promise.all([biApi.northStar(), biApi.deptRadar(), biApi.trend(1)])
  northStar.value = ns || {}
  deptList.value = dept || []
  trendData.value = tr || []
  radar.refresh(); rank.refresh(); trend.refresh()
})
</script>
