<template>
  <div class="page-container">
    <!-- 数字概览 -->
    <div class="stat-cards">
      <div class="stat-card"><div class="num" style="color:#2563eb">¥{{ fmtMoney(fin.dueReceivable) }}</div><div class="label">到期应收</div></div>
      <div class="stat-card"><div class="num" style="color:#10b981">¥{{ fmtMoney(fin.future30) }}</div><div class="label">未来30天应收</div></div>
      <div class="stat-card"><div class="num" style="color:#ef4444">¥{{ fmtMoney(fin.overdue) }}</div><div class="label">逾期欠款</div></div>
      <div class="stat-card"><div class="num" style="color:#8b5cf6">¥{{ fmtMoney(fin.received) }}</div><div class="label">累计实收</div></div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never" header="房源状态分布">
          <div ref="roomRef" style="height: 300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" header="近6月应收/实收趋势">
          <div ref="trendRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" header="经营收入来源" style="margin-top: 16px">
      <div class="income-layout">
        <div class="income-metrics">
          <div class="income-item"><span>租金及物业应收</span><strong>¥{{ fmtMoney(incomeSources.rentPropertyBilled) }}</strong></div>
          <div class="income-item"><span>租金及物业实收</span><strong>¥{{ fmtMoney(incomeSources.rentPropertyReceived) }}</strong></div>
          <div class="income-item vending"><span>售货机销售</span><strong>¥{{ fmtMoney(incomeSources.vendingSales) }}</strong></div>
        </div>
        <div ref="sourceRef" class="income-chart"></div>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="8">
        <el-card shadow="never" header="合同执行">
          <div class="mini-grid">
            <div class="mini"><span class="mv">{{ contract.total }}</span><span class="ml">合同总数</span></div>
            <div class="mini"><span class="mv" style="color:#10b981">{{ contract.executing }}</span><span class="ml">执行中</span></div>
            <div class="mini"><span class="mv" style="color:#6b7280">{{ contract.terminated }}</span><span class="ml">已退租</span></div>
            <div class="mini"><span class="mv" style="color:#f59e0b">{{ contract.expired }}</span><span class="ml">已到期</span></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="设备在线">
          <div class="mini-grid">
            <div class="mini"><span class="mv">{{ device.total }}</span><span class="ml">设备总数</span></div>
            <div class="mini"><span class="mv" style="color:#10b981">{{ device.online }}</span><span class="ml">在线</span></div>
            <div class="mini"><span class="mv" style="color:#ef4444">{{ device.offline }}</span><span class="ml">离线</span></div>
            <div class="mini"><span class="mv" style="color:#f59e0b">{{ device.alarm }}</span><span class="ml">告警</span></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" header="工单分类">
          <div ref="woRef" style="height: 200px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '@/api/dashboard'

const fin = reactive({}), contract = reactive({}), device = reactive({}), incomeSources = reactive({})
const roomRef = ref(), trendRef = ref(), woRef = ref(), sourceRef = ref()
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

onMounted(async () => {
  const ov = await dashboardApi.overview()
  Object.assign(fin, ov.finance); Object.assign(contract, ov.contract); Object.assign(device, ov.device)
  Object.assign(incomeSources, ov.incomeSources)
  await nextTick()

  const roomData = await dashboardApi.roomStatus()
  echarts.init(roomRef.value).setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#4f46e5', '#818cf8', '#f59e0b', '#16a34a', '#e5484d', '#94a3b8', '#06b6d4'],
    series: [{
      type: 'pie', radius: ['42%', '66%'], data: roomData,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}: {c}' }
    }]
  })

  const trend = await dashboardApi.revenueTrend()
  echarts.init(trendRef.value).setOption({
    tooltip: { trigger: 'axis' }, legend: { data: ['应收', '实收'] },
    grid: { left: 60, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.months, axisLine: { lineStyle: { color: '#e0e3e9' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    series: [
      { name: '应收', type: 'line', data: trend.receivable, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(79,70,229,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#4f46e5' } },
      { name: '实收', type: 'line', data: trend.received, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(22,163,74,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#16a34a' } }
    ]
  })

  echarts.init(sourceRef.value).setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 30, top: 12, bottom: 20 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'category', data: ['售货机销售', '租费实收', '租费应收'], axisLabel: { color: '#606266' } },
    series: [{
      type: 'bar', barWidth: 22,
      data: [incomeSources.vendingSales || 0, incomeSources.rentPropertyReceived || 0, incomeSources.rentPropertyBilled || 0],
      itemStyle: { color: (params) => ['#10b981', '#8b5cf6', '#2563eb'][params.dataIndex], borderRadius: [0, 5, 5, 0] }
    }]
  })

  const wo = await dashboardApi.workOrderCategory()
  echarts.init(woRef.value).setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: wo.map(x => x.name), axisLine: { lineStyle: { color: '#e0e3e9' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    series: [{ type: 'bar', data: wo.map(x => x.value), itemStyle: { color: '#4f46e5', borderRadius: [4, 4, 0, 0] }, barWidth: '40%' }]
  })
})
</script>

<style scoped>
.mini-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.mini { display: flex; flex-direction: column; align-items: center; padding: 12px 0; background: #f8fafc; border-radius: 8px; }
.mv { font-size: 24px; font-weight: 700; color: #2563eb; }
.ml { font-size: 13px; color: #6b7280; margin-top: 4px; }
.income-layout { display:grid; grid-template-columns:minmax(360px, 1fr) minmax(420px, 1.4fr); gap:20px; align-items:center; }
.income-metrics { display:grid; gap:10px; }
.income-item { display:flex; justify-content:space-between; align-items:center; padding:14px 16px; border-radius:8px; background:#f5f7ff; color:#606266; }
.income-item strong { color:#2563eb; font-size:20px; }.income-item.vending { background:#f0f9eb; }.income-item.vending strong { color:#10b981; }
.income-chart { height:220px; }
@media (max-width: 900px) { .income-layout { grid-template-columns:1fr; }.income-chart { height:200px; } }
</style>
