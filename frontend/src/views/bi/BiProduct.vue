<template>
  <div class="page-container" style="padding:20px">
    <!-- 模块使用率 -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>模块使用率（近30天）</span></template>
      <div ref="moduleChartRef" style="height:300px"></div>
    </el-card>

    <!-- 流程卡点 -->
    <el-card shadow="hover" style="margin-bottom:20px">
      <template #header><span>流程闭环概览（本月）</span></template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="发起总数">{{ flow.total_started ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="完成总数">{{ flow.total_completed ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="闭环率">
          {{ flow.total_started ? Math.round(flow.total_completed / flow.total_started * 100) + '%' : '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 反馈看板 -->
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
import { ref, onMounted, nextTick } from 'vue'
import { biApi } from '@/api/bi'
import { useChart } from '@/composables/useChart'

const statusMap = { 1: '待处理', 2: '已确认', 3: '处理中', 4: '已解决', 5: '已采纳', 6: '已关闭' }
const moduleChartRef = ref(null)
const flow = ref({})
const feedbackData = ref([])

onMounted(async () => {
  const [mod, fl, fb] = await Promise.all([
    biApi.moduleUsage(), biApi.flowAnalysis(), biApi.feedbackBoard()
  ])

  flow.value = fl.data || {}
  feedbackData.value = fb.data || []

  await nextTick()
  if (moduleChartRef.value && mod.data?.length) {
    const { setOption } = useChart(moduleChartRef.value)
    const data = mod.data
    setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.map(d => d.module), axisLabel: { rotate: 30 } },
      yAxis: [{ type: 'value', name: '请求数' }, { type: 'value', name: '用户数' }],
      series: [
        { name: '请求数', type: 'bar', data: data.map(d => d.request_count) },
        { name: '独立用户数', type: 'line', yAxisIndex: 1, data: data.map(d => d.user_count) }
      ]
    })
  }
})
</script>
