<template>
  <div class="analytics-wrap" v-loading="loading">
    <!-- 维度切换栏：按日 / 按月 / 按年 + 跨条线对比 -->
    <div class="ana-bar">
      <div class="bar-left">
        <el-radio-group v-model="granularity" size="small" @change="load">
          <el-radio-button value="DAY">按日汇总</el-radio-button>
          <el-radio-button value="MONTH">按月汇总</el-radio-button>
          <el-radio-button value="YEAR">按年汇总</el-radio-button>
        </el-radio-group>

        <el-date-picker
          v-if="granularity === 'DAY'"
          v-model="anchorDate"
          type="date"
          size="small"
          value-format="YYYY-MM-DD"
          :clearable="false"
          style="width: 140px"
          @change="load"
        />
        <el-date-picker
          v-else-if="granularity === 'MONTH'"
          v-model="anchorMonth"
          type="month"
          size="small"
          value-format="YYYY-MM"
          :clearable="false"
          style="width: 130px"
          @change="load"
        />
        <el-date-picker
          v-else
          v-model="anchorYear"
          type="year"
          size="small"
          value-format="YYYY"
          :clearable="false"
          style="width: 110px"
          @change="load"
        />

        <el-radio-group v-model="scopeMode" size="small" @change="load">
          <el-radio-button value="CURRENT">仅当前模块</el-radio-button>
          <el-radio-button value="ALL">四条线总览</el-radio-button>
        </el-radio-group>
      </div>
      <div class="bar-right">
        <span class="range-txt">统计区间：{{ summary.startDate || '-' }} ~ {{ summary.endDate || '-' }}</span>
        <el-button size="small" @click="load">刷新统计</el-button>
      </div>
    </div>

    <!-- 核心 KPI 指标卡 -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span>应检任务总数</span>
        <strong>{{ summary.totalTasks || 0 }} <small>次</small></strong>
        <em class="sub">检查项 {{ summary.completedItems || 0 }} / {{ summary.totalItems || 0 }}</em>
      </div>
      <div class="kpi-card ok">
        <span>已自动完成</span>
        <strong>{{ summary.completedTasks || 0 }} <small>次</small></strong>
        <em class="sub">达成率 {{ summary.completionRate ?? 0 }}%</em>
      </div>
      <div class="kpi-card" :class="{ danger: (summary.incompleteTasks || 0) > 0 }">
        <span>未完成 / 标红逾期</span>
        <strong>{{ summary.incompleteTasks || 0 }} <small>次</small></strong>
        <em class="sub">其中已逾期 {{ summary.overdueTasks || 0 }} 次</em>
      </div>
      <div class="kpi-card" :class="{ warn: (summary.issueCount || 0) > 0 }">
        <span>巡检发现异常</span>
        <strong>{{ summary.issueCount || 0 }} <small>项</small></strong>
        <em class="sub">已转报修工单 {{ summary.workOrderCount || 0 }} 项</em>
      </div>
    </div>

    <!-- 图表 1：日/月/年巡检完成情况趋势 -->
    <div class="chart-card">
      <div class="card-head">
        <strong>{{ trendTitle }}</strong>
        <span>绿色=已自动完成，红色=未完成/逾期，折线=完成率(%)</span>
      </div>
      <div ref="trendEl" class="chart-box" />
    </div>

    <!-- 图表 2 + 3：巡检人绩效对比 + 问题分类/区域分布 -->
    <div class="chart-two-col">
      <div class="chart-card">
        <div class="card-head">
          <strong>巡检人执行情况汇总</strong>
          <span>按巡检完成率排序，未完成自动标红</span>
        </div>
        <div ref="inspectorEl" class="chart-box-sm" />
        <el-table :data="byInspector" border size="small" style="margin-top: 10px" max-height="240">
          <el-table-column prop="inspectorName" label="巡检人" min-width="95" />
          <el-table-column prop="position" label="职位" min-width="95" show-overflow-tooltip />
          <el-table-column prop="total" label="应检" width="60" align="right" />
          <el-table-column prop="completed" label="已完成" width="66" align="right" />
          <el-table-column label="未完成" width="68" align="right">
            <template #default="{ row }">
              <span :class="{ 'red-bold': row.incomplete > 0 }">{{ row.incomplete }}</span>
            </template>
          </el-table-column>
          <el-table-column label="完成率" width="76" align="right">
            <template #default="{ row }">
              <el-tag size="small" :type="row.rate >= 95 ? 'success' : (row.rate >= 70 ? 'warning' : 'danger')" effect="plain">
                {{ row.rate }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="issues" label="发现异常" width="74" align="right" />
        </el-table>
      </div>

      <div class="chart-card">
        <div class="card-head">
          <strong>巡检问题分析（分类占比 & 高频异常区域）</strong>
          <span>辅助定位薄弱环节与高频隐患点</span>
        </div>
        <div class="issue-charts">
          <div ref="catPieEl" class="chart-box-sm" />
          <div ref="areaBarEl" class="chart-box-sm" />
        </div>
      </div>
    </div>

    <!-- 异常问题明细台账（支持一键转报修工单） -->
    <div class="chart-card">
      <div class="card-head">
        <strong>本期发现异常问题明细（共 {{ recentIssues.length }} 条）</strong>
        <span>点击可一键转派物业报修工单，形成闭环整改</span>
      </div>
      <el-table :data="recentIssues" border size="small" max-height="300">
        <el-table-column prop="taskDate" label="日期" width="105" />
        <el-table-column prop="slotLabel" label="班次" width="110" show-overflow-tooltip />
        <el-table-column prop="areaPoint" label="异常区域" width="130" show-overflow-tooltip />
        <el-table-column prop="itemName" label="检查项" min-width="160" show-overflow-tooltip />
        <el-table-column label="异常分类" width="100">
          <template #default="{ row }"><el-tag size="small" type="danger" effect="plain">{{ row.issueCategory || '异常' }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="issueDesc" label="问题描述" min-width="190" show-overflow-tooltip />
        <el-table-column prop="capturedBy" label="上报巡检人" width="95" />
        <el-table-column label="整改联动" width="135" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.workOrderCode" size="small" type="success">{{ row.workOrderCode }}</el-tag>
            <el-button v-else link type="warning" size="small" @click="toOrder(row)">转报修工单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!recentIssues.length" class="empty-txt">本统计周期内暂无异常问题记录</div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { routineInspectionApi } from '@/api/property'
import { useChart } from '@/composables/useChart'

const props = defineProps({
  moduleType: { type: String, required: true }
})

const todayStr = new Date().toISOString().slice(0, 10)
const granularity = ref('MONTH')
const scopeMode = ref('CURRENT')
const anchorDate = ref(todayStr)
const anchorMonth = ref(todayStr.slice(0, 7))
const anchorYear = ref(todayStr.slice(0, 4))

const loading = ref(false)
const summary = ref({})
const trend = ref([])
const byInspector = ref([])
const issuesByCategory = ref([])
const issuesByArea = ref([])
const recentIssues = ref([])

const trendEl = ref(null)
const inspectorEl = ref(null)
const catPieEl = ref(null)
const areaBarEl = ref(null)

const trendTitle = computed(() => {
  if (granularity.value === 'DAY') return `${anchorDate.value} · 各班次巡检完成情况`
  if (granularity.value === 'YEAR') return `${anchorYear.value} 年 · 月度巡检完成情况汇总`
  return `${anchorMonth.value} · 每日巡检完成情况汇总`
})

const queryDate = computed(() => {
  if (granularity.value === 'DAY') return anchorDate.value
  if (granularity.value === 'YEAR') return `${anchorYear.value}-01-01`
  return `${anchorMonth.value}-01`
})

const trendChart = useChart(trendEl, (t) => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 2, textStyle: { color: t.textColor } },
  grid: { left: 48, right: 52, top: 40, bottom: 28 },
  xAxis: {
    type: 'category',
    data: trend.value.map((x) => x.label),
    axisLine: { lineStyle: { color: t.axisLine } },
    axisLabel: { color: t.axisLabel }
  },
  yAxis: [
    { type: 'value', name: '任务数', minInterval: 1, axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
    { type: 'value', name: '完成率%', min: 0, max: 100, axisLabel: { color: t.axisLabel, formatter: '{value}%' }, splitLine: { show: false } }
  ],
  series: [
    { name: '已自动完成', type: 'bar', stack: 'tasks', barMaxWidth: 24, itemStyle: { color: '#16a34a' }, data: trend.value.map((x) => x.completed) },
    { name: '未完成/逾期', type: 'bar', stack: 'tasks', barMaxWidth: 24, itemStyle: { color: '#ef4444' }, data: trend.value.map((x) => x.incomplete) },
    { name: '完成率%', type: 'line', yAxisIndex: 1, smooth: true, symbolSize: 6, itemStyle: { color: '#4f46e5' }, data: trend.value.map((x) => x.rate) }
  ]
}))

const inspectorChart = useChart(inspectorEl, (t) => ({
  tooltip: { trigger: 'axis' },
  legend: { top: 0, textStyle: { color: t.textColor } },
  grid: { left: 68, right: 20, top: 34, bottom: 26 },
  xAxis: { type: 'value', minInterval: 1, axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
  yAxis: { type: 'category', data: byInspector.value.map((x) => x.inspectorName), axisLabel: { color: t.axisLabel }, axisLine: { lineStyle: { color: t.axisLine } } },
  series: [
    { name: '已完成', type: 'bar', stack: 'i', barMaxWidth: 18, itemStyle: { color: '#16a34a' }, data: byInspector.value.map((x) => x.completed) },
    { name: '未完成', type: 'bar', stack: 'i', barMaxWidth: 18, itemStyle: { color: '#ef4444' }, data: byInspector.value.map((x) => x.incomplete) }
  ]
}))

const catPieChart = useChart(catPieEl, (t) => ({
  title: { text: '异常分类占比', left: 'center', top: 0, textStyle: { fontSize: 12, color: t.textColor } },
  tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
  series: [{
    type: 'pie',
    radius: ['38%', '68%'],
    center: ['50%', '56%'],
    label: { fontSize: 11 },
    data: issuesByCategory.value.length
      ? issuesByCategory.value.map((x) => ({ name: x.category, value: x.count }))
      : [{ name: '暂无异常', value: 0 }]
  }]
}))

const areaBarChart = useChart(areaBarEl, (t) => ({
  title: { text: '高频异常区域 Top', left: 'center', top: 0, textStyle: { fontSize: 12, color: t.textColor } },
  tooltip: { trigger: 'axis' },
  grid: { left: 82, right: 16, top: 30, bottom: 24 },
  xAxis: { type: 'value', minInterval: 1, axisLabel: { color: t.axisLabel }, splitLine: { lineStyle: { color: t.splitLine } } },
  yAxis: { type: 'category', data: issuesByArea.value.map((x) => x.area), axisLabel: { color: t.axisLabel, width: 74, overflow: 'truncate' } },
  series: [{ type: 'bar', barMaxWidth: 16, itemStyle: { color: '#f59e0b' }, data: issuesByArea.value.map((x) => x.count) }]
}))

async function load() {
  loading.value = true
  try {
    const res = await routineInspectionApi.analytics({
      moduleType: scopeMode.value === 'ALL' ? 'ALL' : props.moduleType,
      granularity: granularity.value,
      date: queryDate.value
    })
    summary.value = res?.summary || {}
    trend.value = res?.trend || []
    byInspector.value = res?.byInspector || []
    issuesByCategory.value = res?.issuesByCategory || []
    issuesByArea.value = res?.issuesByArea || []
    recentIssues.value = res?.recentIssues || []
    trendChart.refresh()
    inspectorChart.refresh()
    catPieChart.refresh()
    areaBarChart.refresh()
  } finally {
    loading.value = false
  }
}

async function toOrder(row) {
  const res = await routineInspectionApi.toWorkOrder(row.itemId)
  ElMessage.success(`已生成物业报修工单：${res.workOrderCode}`)
  await load()
}

watch(() => props.moduleType, load, { immediate: true })
defineExpose({ refresh: load })
</script>

<style scoped>
.analytics-wrap { display: flex; flex-direction: column; gap: 12px; }
.ana-bar {
  display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap;
  padding: 10px 14px; border-radius: 10px; background: var(--el-fill-color-blank); border: 1px solid var(--el-border-color-lighter);
}
.bar-left, .bar-right { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.range-txt { font-size: 12px; color: var(--el-text-color-secondary); }
.kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.kpi-card {
  padding: 12px 16px; border-radius: 10px; background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter); display: flex; flex-direction: column; gap: 4px;
}
.kpi-card span { font-size: 12px; color: var(--el-text-color-secondary); }
.kpi-card strong { font-size: 24px; font-variant-numeric: tabular-nums; }
.kpi-card strong small { font-size: 12px; font-weight: 400; color: var(--el-text-color-secondary); }
.kpi-card .sub { font-style: normal; font-size: 12px; color: var(--el-text-color-secondary); }
.kpi-card.ok strong { color: #16a34a; }
.kpi-card.danger { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.kpi-card.danger strong { color: #dc2626; }
.kpi-card.warn strong { color: #d97706; }
.chart-card {
  padding: 12px 14px; border-radius: 10px; background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
}
.card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.card-head strong { font-size: 14px; }
.card-head span { font-size: 12px; color: var(--el-text-color-secondary); }
.chart-box { width: 100%; height: 270px; }
.chart-box-sm { width: 100%; height: 220px; }
.chart-two-col { display: grid; grid-template-columns: repeat(auto-fit, minmax(420px, 1fr)); gap: 12px; }
.issue-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.red-bold { color: #dc2626; font-weight: 700; }
.empty-txt { text-align: center; padding: 18px 0; font-size: 12px; color: var(--el-text-color-placeholder); }
@media (max-width: 900px) {
  .chart-two-col, .issue-charts { grid-template-columns: 1fr; }
}
</style>
