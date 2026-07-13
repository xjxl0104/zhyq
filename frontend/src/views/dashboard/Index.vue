<template>
  <div class="dashboard">
    <!-- 待办指标卡 -->
    <div class="stat-grid">
      <div class="metric" v-for="m in metrics" :key="m.key" :style="{ '--m-color': m.color }">
        <div class="metric-num" :style="{ color: m.color }">{{ wb[m.key] ?? 0 }}</div>
        <div class="metric-label">{{ m.label }}</div>
      </div>
    </div>

    <el-row :gutter="16" style="margin-top: 16px">
      <!-- 经营概览 -->
      <el-col :span="16">
        <el-card shadow="never">
          <template #header><div class="card-head"><span>经营概览</span></div></template>
          <el-row :gutter="16">
            <el-col :span="6" v-for="o in overviewCards" :key="o.label">
              <div class="ov-card">
                <div class="ov-num">{{ o.value }}</div>
                <div class="ov-label">{{ o.label }}</div>
              </div>
            </el-col>
          </el-row>
          <div ref="trendRef" style="height: 300px; margin-top: 16px"></div>
        </el-card>
      </el-col>

      <!-- 待办任务 + 快捷入口 -->
      <el-col :span="8">
        <el-card shadow="never" style="margin-bottom: 16px">
          <template #header>
            <div class="card-head"><span>待办任务</span>
              <el-button link type="primary" @click="$router.push('/oa/task')">全部</el-button>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item v-for="t in todos" :key="t.id"
                              :timestamp="fmt(t.dueDate)" placement="top"
                              :type="t.priority === 3 ? 'danger' : 'primary'">
              {{ t.title }}
            </el-timeline-item>
            <el-empty v-if="!todos.length" description="暂无待办" :image-size="60" />
          </el-timeline>
        </el-card>

        <el-card shadow="never">
          <template #header><div class="card-head"><span>常用功能</span></div></template>
          <div class="quick-grid">
            <div class="quick" v-for="q in quicks" :key="q.path" @click="$router.push(q.path)">
              <el-icon :size="22"><component :is="q.icon" /></el-icon>
              <span>{{ q.name }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'
import { todoApi } from '@/api/todo'

const wb = reactive({})
const metrics = [
  { key: 'contractPending', label: '合同待审核', color: '#f59e0b' },
  { key: 'contractExpiring', label: '合同即将到期', color: '#ef4444' },
  { key: 'leadFollow', label: '线索待跟进', color: '#2563eb' },
  { key: 'approvalPending', label: '审批待处理', color: '#8b5cf6' },
  { key: 'todoCount', label: '待办任务', color: '#10b981' },
  { key: 'billUnpaid', label: '待催缴账单', color: '#f43f5e' },
  { key: 'workOrderPending', label: '待处理工单', color: '#06b6d4' }
]

const overviewCards = ref([])
const todos = ref([])
const trendRef = ref()

const quicks = [
  { name: '租控管理', path: '/building/room', icon: 'OfficeBuilding' },
  { name: '合同列表', path: '/contract/list', icon: 'Document' },
  { name: '账单管理', path: '/finance/bill', icon: 'Money' },
  { name: '物业报修', path: '/property/workorder', icon: 'Tools' },
  { name: '线索管理', path: '/crm/lead', icon: 'Promotion' },
  { name: '监控大屏', path: '/screen', icon: 'Monitor' }
]

function fmt(v) { return v ? v.substring(5, 16) : '' }

async function load() {
  const w = await dashboardApi.workbench()
  Object.assign(wb, w)
  const ov = await dashboardApi.overview()
  overviewCards.value = [
    { label: '在租房间', value: ov.room.rented + ' / ' + ov.room.total },
    { label: '出租率', value: ov.room.rentRate + '%' },
    { label: '执行中合同', value: ov.contract.executing },
    { label: '在租租客', value: ov.other.tenantTotal }
  ]
  todos.value = await todoApi.list()
  await nextTick()
  renderTrend()
}

async function renderTrend() {
  const data = await dashboardApi.revenueTrend()
  const chart = echarts.init(trendRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['应收', '实收'] },
    grid: { left: 60, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: data.months, axisLine: { lineStyle: { color: '#e0e3e9' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'value', name: '元', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    series: [
      { name: '应收', type: 'bar', data: data.receivable, barWidth: '42%', itemStyle: { color: '#c7d2fe', borderRadius: [4, 4, 0, 0] } },
      { name: '实收', type: 'line', data: data.received, smooth: true, symbolSize: 7, itemStyle: { color: '#4f46e5' }, lineStyle: { width: 3 }, areaStyle: { color: 'rgba(79,70,229,0.08)' } }
    ]
  })
  window.addEventListener('resize', () => chart.resize())
}

onMounted(load)
</script>

<style scoped>
.dashboard { padding: 20px; }
.stat-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 14px; }
.metric {
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 20px 14px; text-align: center;
  position: relative; overflow: hidden;
  transition: border-color .18s, transform .18s;
}
.metric::before {
  content: ""; position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: var(--m-color, var(--brand)); opacity: .9;
}
.metric:hover { border-color: var(--border-strong); transform: translateY(-2px); }
.metric-num {
  font-size: 28px; font-weight: 700; letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums; line-height: 1.1;
}
.metric-label { color: var(--text-secondary); font-size: 13px; margin-top: 7px; }
.card-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; color: var(--text-title); }
.ov-card {
  background: var(--bg-subtle); border: 1px solid var(--border);
  border-radius: var(--radius-sm); padding: 18px 16px; text-align: center;
}
.ov-num { font-size: 23px; font-weight: 700; color: var(--brand); letter-spacing: -0.5px; }
.ov-label { color: var(--text-secondary); font-size: 13px; margin-top: 5px; }
.quick-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.quick {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 16px 0; border-radius: var(--radius-sm); cursor: pointer;
  color: var(--text-body); border: 1px solid transparent;
  transition: all .18s;
}
.quick:hover {
  background: var(--el-color-primary-light-9);
  color: var(--brand); border-color: var(--el-color-primary-light-7);
  transform: translateY(-2px);
}
.quick span { font-size: 13px; font-weight: 500; }
</style>
