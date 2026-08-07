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

    <!-- 部门雷达 -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>部门评分排行</span></template>
      <el-table :data="deptList" border stripe @row-click="drillDown">
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
import { ref, computed, onMounted, nextTick } from 'vue'
import { biApi } from '@/api/bi'
import { useChart } from '@/composables/useChart'

const northStar = ref({})
const deptList = ref([])
const trendChartRef = ref(null)
const drillVisible = ref(false)
const drillDept = ref('')
const drillList = ref([])

const ratio = computed(() => {
  const c = northStar.value.current_week
  const p = northStar.value.prev_week
  if (!p) return 0
  return Math.round((c - p) / p * 100)
})

async function drillDown(row) {
  drillDept.value = row.dept_name
  const res = await biApi.deptDetail(row.dept_id)
  drillList.value = res.data
  drillVisible.value = true
}

onMounted(async () => {
  const [ns, dept, trend] = await Promise.all([
    biApi.northStar(), biApi.deptRadar(), biApi.trend(1)
  ])
  northStar.value = ns.data || {}
  deptList.value = dept.data || []

  await nextTick()
  if (trendChartRef.value && trend.data?.length) {
    const { setOption } = useChart(trendChartRef.value)
    const data = [...trend.data].reverse()
    setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map(d => d.period_start) },
      yAxis: { type: 'value' },
      series: [
        { name: '活跃用户', type: 'line', data: data.map(d => d.active_users), smooth: true },
        { name: '核心操作用户', type: 'line', data: data.map(d => d.core_users), smooth: true }
      ]
    })
  }
})
</script>
