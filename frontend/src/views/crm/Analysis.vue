<template>
  <div class="page-container">
    <!-- 统计卡 -->
    <div class="stat-cards">
      <div class="stat-card"><div class="num" style="color:#4f46e5">{{ summary.customerTotal || 0 }}</div><div class="label">客户总数</div></div>
      <div class="stat-card"><div class="num" style="color:#e5484d">{{ summary.levelA || 0 }}</div><div class="label">A级客户</div></div>
      <div class="stat-card"><div class="num" style="color:#16a34a">{{ summary.signed || 0 }}</div><div class="label">签约客户</div></div>
      <div class="stat-card"><div class="num" style="color:#f59e0b">¥{{ fmtMoney(summary.commissionPending) }}</div><div class="label">佣金待结算</div></div>
      <div class="stat-card"><div class="num" style="color:#06b6d4">{{ summary.monthLead || 0 }}</div><div class="label">本月新增线索</div></div>
    </div>

    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never" header="招商漏斗">
          <div ref="funnelRef" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="16">
        <el-card shadow="never" header="近6月线索与转化趋势">
          <div ref="trendRef" style="height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never" header="线索来源分布">
          <div ref="sourceRef" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { analysisApi } from '@/api/crm'

const summary = reactive({})
const funnelRef = ref(), trendRef = ref(), sourceRef = ref()
const fmtMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { maximumFractionDigits: 0 })

onMounted(async () => {
  Object.assign(summary, await analysisApi.summary())
  await nextTick()

  const funnel = await analysisApi.funnel()
  echarts.init(funnelRef.value).setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    color: ['#4f46e5', '#818cf8', '#f59e0b', '#16a34a'],
    series: [{
      type: 'funnel',
      left: '5%', right: '5%', top: 20, bottom: 20,
      sort: 'descending', gap: 2,
      label: { show: true, position: 'inside', formatter: '{b}\n{c}' },
      itemStyle: { borderColor: '#fff', borderWidth: 1 },
      data: funnel
    }]
  })

  const trend = await analysisApi.trend()
  echarts.init(trendRef.value).setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['新增线索', '转化'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: trend.months, axisLine: { lineStyle: { color: '#e0e3e9' } }, axisLabel: { color: '#9aa1ac' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#f0f2f5' } }, axisLabel: { color: '#9aa1ac' } },
    series: [
      { name: '新增线索', type: 'line', data: trend.leads, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(79,70,229,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#4f46e5' } },
      { name: '转化', type: 'line', data: trend.converted, smooth: true, symbolSize: 7, areaStyle: { color: 'rgba(22,163,74,0.10)' }, lineStyle: { width: 3 }, itemStyle: { color: '#16a34a' } }
    ]
  })

  const source = await analysisApi.source()
  echarts.init(sourceRef.value).setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    color: ['#4f46e5', '#818cf8', '#f59e0b', '#16a34a', '#e5484d', '#06b6d4', '#94a3b8'],
    series: [{
      type: 'pie', radius: ['42%', '66%'], data: source,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}: {c}' }
    }]
  })
})
</script>

<style scoped>
.stat-cards { display: flex; gap: 16px; margin-bottom: 16px; }
.stat-card { flex: 1; background: #fff; border-radius: 8px; padding: 18px 20px; box-shadow: 0 1px 2px rgba(0,0,0,0.04); }
.stat-card .num { font-size: 26px; font-weight: 700; }
.stat-card .label { font-size: 13px; color: #6b7280; margin-top: 6px; }
</style>
