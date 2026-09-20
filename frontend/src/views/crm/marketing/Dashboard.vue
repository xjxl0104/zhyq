<template>
  <div class="page-container">
    <div class="search-bar">
      <el-form :inline="true">
        <el-form-item label="时间范围">
          <el-radio-group v-model="range" size="small" @change="load">
            <el-radio-button value="7d">近 7 天</el-radio-button>
            <el-radio-button value="30d">近 30 天</el-radio-button>
            <el-radio-button value="90d">近 90 天</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>

    <el-row :gutter="12" class="cards">
      <el-col :span="4" v-for="c in cards" :key="c.key">
        <div class="stat" @click="c.to && $router.push(c.to)" :class="{ link: c.to }">
          <div class="label">{{ c.label }}</div>
          <div class="value">{{ fmt(summary[c.key]) }}</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col :span="10">
        <div class="table-card">
          <div class="toolbar"><span class="title">漏斗:推荐 → 到访 → 签约 → 履约</span></div>
          <div ref="funnelEl" class="chart" />
        </div>
      </el-col>
      <el-col :span="14">
        <div class="table-card">
          <div class="toolbar"><span class="title">趋势:出库单量 / 服务费收入 / 佣金支出</span></div>
          <div ref="trendEl" class="chart" />
        </div>
      </el-col>
    </el-row>
    <p class="hint">口径(PARK-MKT-001 §6.3):服务费收入 = 园区按合同单价表算出的 service_fee 合计;佣金支出含负向扣回;园区毛利 = 服务费收入 − 应付云仓 − 佣金支出;活跃伙伴 = 近 30 天有推荐或有佣金入账。</p>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { mktDashboardApi } from '@/api/marketing'

const range = ref('30d')
const summary = reactive({})
const cards = [
  { key: 'activePromoters', label: '活跃伙伴', to: '/crm/marketing/promoter' },
  { key: 'referrals', label: '推荐客户' , to: '/crm/marketing/customer' },
  { key: 'outboundOrders', label: '出库单量', to: '/crm/marketing/order' },
  { key: 'serviceFee', label: '服务费收入' },
  { key: 'commissionPaid', label: '佣金支出', to: '/crm/marketing/commission' },
  { key: 'pendingWithdrawals', label: '待审提现', to: '/crm/marketing/withdrawal' }
]
const fmt = (v) => (v == null ? '-' : typeof v === 'number' ? v.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : v)

const funnelEl = ref()
const trendEl = ref()
let funnelChart, trendChart

async function load() {
  const [s, f, t] = await Promise.all([
    mktDashboardApi.summary({ range: range.value }),
    mktDashboardApi.funnel({ range: range.value }),
    mktDashboardApi.trend({ range: range.value })
  ])
  Object.assign(summary, s || {})
  funnelChart?.setOption({
    tooltip: {},
    series: [{ type: 'funnel', left: '10%', width: '80%', label: { formatter: '{b}: {c}' },
      data: (f || []).map(x => ({ name: x.stage, value: x.count })) }]
  })
  const days = (t || []).map(x => x.day)
  trendChart?.setOption({
    tooltip: { trigger: 'axis' }, legend: {}, grid: { left: 48, right: 48, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: days },
    yAxis: [{ type: 'value', name: '单' }, { type: 'value', name: '元' }],
    series: [
      { name: '出库单量', type: 'bar', data: (t || []).map(x => x.orders) },
      { name: '服务费收入', type: 'line', yAxisIndex: 1, data: (t || []).map(x => x.serviceFee) },
      { name: '佣金支出', type: 'line', yAxisIndex: 1, data: (t || []).map(x => x.commission) }
    ]
  })
}
const resize = () => { funnelChart?.resize(); trendChart?.resize() }
onMounted(() => {
  funnelChart = echarts.init(funnelEl.value)
  trendChart = echarts.init(trendEl.value)
  window.addEventListener('resize', resize)
  load()
})
onBeforeUnmount(() => { window.removeEventListener('resize', resize); funnelChart?.dispose(); trendChart?.dispose() })
</script>

<style scoped>
.cards { margin-bottom: 12px; }
.stat { background: var(--el-bg-color); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 14px 16px; }
.stat.link { cursor: pointer; }
.stat.link:hover { border-color: var(--el-color-primary); }
.label { color: var(--el-text-color-secondary); font-size: 12px; }
.value { font-size: 22px; font-weight: 600; margin-top: 4px; }
.title { font-weight: 600; }
.chart { height: 300px; }
.hint { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
</style>
