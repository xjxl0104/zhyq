<template>
  <div class="page-container mkt-dashboard">
    <header class="dash-head">
      <div>
        <h1 class="dash-title">全民营销看板</h1>
        <p class="dash-sub">{{ rangeLabel }}的推荐转化、出库与佣金概况</p>
      </div>
      <el-radio-group v-model="range" @change="load" aria-label="时间范围">
        <el-radio-button value="7d">近 7 天</el-radio-button>
        <el-radio-button value="30d">近 30 天</el-radio-button>
        <el-radio-button value="90d">近 90 天</el-radio-button>
      </el-radio-group>
    </header>

    <section class="kpi-grid" aria-label="核心指标">
      <div class="kpi kpi--lead">
        <div class="kpi-label">服务费收入</div>
        <div class="kpi-value"><span class="kpi-unit">¥</span>{{ money(summary.serviceFee) }}</div>
        <div class="kpi-foot">
          <span>佣金支出 ¥{{ money(summary.commissionPaid) }}</span>
          <span v-if="commissionRate != null" class="kpi-chip">占收入 {{ commissionRate }}%</span>
        </div>
      </div>
      <component :is="c.to ? 'button' : 'div'" v-for="c in cards" :key="c.key" class="kpi" :class="{ 'kpi--link': c.to, 'kpi--alert': c.alert && summary[c.key] > 0 }"
                 :type="c.to ? 'button' : undefined" @click="c.to && $router.push(c.to)">
        <div class="kpi-label">{{ c.label }}<el-icon v-if="c.to" class="kpi-arrow"><ArrowRight /></el-icon></div>
        <div class="kpi-value">{{ fmt(summary[c.key]) }}<span v-if="c.unit" class="kpi-suffix">{{ c.unit }}</span></div>
        <div class="kpi-foot">{{ c.alert && summary[c.key] > 0 ? '需要处理' : c.hint }}</div>
      </component>
    </section>

    <section class="dash-panels">
      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">转化漏斗</h2>
          <span class="panel-note">推荐到履约，各环节相对上一环节的转化率</span>
        </div>
        <ol v-if="funnel.length" class="funnel">
          <li v-for="(f, i) in funnel" :key="f.stage" class="funnel-row">
            <span class="funnel-stage">{{ f.stage }}</span>
            <span class="funnel-track"><span class="funnel-bar" :style="{ width: f.width + '%', opacity: 1 - i * 0.17 }" /></span>
            <span class="funnel-count">{{ fmt(f.count) }}</span>
            <span class="funnel-rate">{{ i === 0 ? '—' : f.rate + '%' }}</span>
          </li>
        </ol>
        <el-empty v-else description="该时间范围内暂无推荐记录" :image-size="72" />
      </div>
      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">出库与收支趋势</h2>
          <span class="panel-note">柱：出库单量（单）　线：金额（元）</span>
        </div>
        <div ref="trendEl" class="chart" role="img" aria-label="出库单量、服务费收入与佣金支出趋势图" />
      </div>
    </section>

    <details class="dash-def">
      <summary>指标口径</summary>
      <ul>
        <li><b>服务费收入</b>：园区按合同单价表算出的服务费合计。</li>
        <li><b>佣金支出</b>：含负向扣回。</li>
        <li><b>园区毛利</b>：服务费收入 − 应付云仓 − 佣金支出。</li>
        <li><b>活跃伙伴</b>：近 30 天有推荐或有佣金入账。</li>
      </ul>
      <p>依据 PARK-MKT-001 §6.3。</p>
    </details>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { ArrowRight } from '@element-plus/icons-vue'
import { mktDashboardApi } from '@/api/marketing'

const RANGE_LABELS = { '7d': '近 7 天', '30d': '近 30 天', '90d': '近 90 天' }
const BRAND = '#4f46e5'
const BRAND_SOFT = '#dcdafa'
const NEUTRAL_LINE = '#94a3b8'
const AXIS_TEXT = '#6b7280'
const GRID_LINE = '#eef0f4'

const range = ref('30d')
const rangeLabel = computed(() => RANGE_LABELS[range.value])
const summary = reactive({})
const funnel = ref([])
const cards = [
  { key: 'outboundOrders', label: '出库单量', unit: '单', hint: '计佣订单', to: '/crm/marketing/order' },
  { key: 'referrals', label: '推荐客户', unit: '家', hint: '客户管理', to: '/crm/marketing/customer' },
  { key: 'activePromoters', label: '活跃伙伴', unit: '人', hint: '伙伴管理', to: '/crm/marketing/promoter' },
  { key: 'pendingWithdrawals', label: '待审提现', unit: '笔', hint: '暂无待审', to: '/crm/marketing/withdrawal', alert: true }
]
const fmt = (v) => (v == null ? '-' : typeof v === 'number' ? v.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : v)
const money = (v) => (typeof v === 'number' ? v.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '-')
const commissionRate = computed(() => {
  const fee = Number(summary.serviceFee), paid = Number(summary.commissionPaid)
  return fee > 0 && Number.isFinite(paid) ? (paid / fee * 100).toFixed(1) : null
})

const trendEl = ref()
let trendChart

function toFunnelRows(stages) {
  const top = Math.max(...stages.map(x => Number(x.count) || 0), 0)
  return stages.map((x, i) => {
    const count = Number(x.count) || 0
    const previous = i > 0 ? Number(stages[i - 1].count) || 0 : 0
    return { stage: x.stage, count, width: top > 0 ? Math.max(count / top * 100, 2) : 0, rate: previous > 0 ? (count / previous * 100).toFixed(1) : '0.0' }
  })
}

function trendOption(t) {
  const axisLabel = { color: AXIS_TEXT, fontSize: 12 }
  const line = (name, color, key, dashed) => ({
    name, type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, data: t.map(x => x[key]),
    lineStyle: { width: 2, color, type: dashed ? 'dashed' : 'solid' }, itemStyle: { color }
  })
  return {
    tooltip: { trigger: 'axis', borderColor: GRID_LINE, textStyle: { color: '#111827', fontSize: 12 } },
    legend: { right: 0, top: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 4, textStyle: axisLabel },
    grid: { left: 8, right: 8, top: 40, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: t.map(x => x.day), axisTick: { show: false }, axisLine: { lineStyle: { color: GRID_LINE } }, axisLabel },
    yAxis: [
      { type: 'value', axisLabel, splitLine: { lineStyle: { color: GRID_LINE } } },
      { type: 'value', axisLabel, splitLine: { show: false } }
    ],
    series: [
      { name: '出库单量', type: 'bar', barMaxWidth: 14, data: t.map(x => x.orders), itemStyle: { color: BRAND_SOFT, borderRadius: [3, 3, 0, 0] } },
      line('服务费收入', BRAND, 'serviceFee', false),
      line('佣金支出', NEUTRAL_LINE, 'commission', true)
    ]
  }
}

async function load() {
  const [s, f, t] = await Promise.all([
    mktDashboardApi.summary({ range: range.value }),
    mktDashboardApi.funnel({ range: range.value }),
    mktDashboardApi.trend({ range: range.value })
  ])
  Object.assign(summary, s || {})
  funnel.value = toFunnelRows(f || [])
  trendChart?.setOption(trendOption(t || []), true)
}
const resize = () => { trendChart?.resize() }
onMounted(() => {
  trendChart = echarts.init(trendEl.value)
  window.addEventListener('resize', resize)
  load()
})
onBeforeUnmount(() => { window.removeEventListener('resize', resize); trendChart?.dispose() })
</script>

<style scoped>
.mkt-dashboard { display: flex; flex-direction: column; gap: 16px; }
.dash-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; padding: 4px 4px 0; }
.dash-title { margin: 0; color: var(--text-title); font-size: 22px; font-weight: 600; letter-spacing: -0.01em; }
.dash-sub { margin: 4px 0 0; color: var(--text-secondary); font-size: 13px; }

.kpi-grid { display: grid; grid-template-columns: minmax(280px, 1.6fr) repeat(4, minmax(0, 1fr)); gap: 12px; }
.kpi { display: flex; flex-direction: column; min-width: 0; padding: 18px 20px; text-align: left; font: inherit; color: inherit; background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); }
.kpi--link { cursor: pointer; transition: border-color .2s ease, transform .2s ease, box-shadow .2s ease; }
.kpi--link:hover { border-color: var(--el-color-primary-light-5); box-shadow: 0 6px 18px rgba(79, 70, 229, .08); transform: translateY(-1px); }
.kpi--link:active { transform: translateY(0); }
.kpi--link:focus-visible { outline: 2px solid var(--brand); outline-offset: 2px; }
.kpi-label { display: flex; align-items: center; justify-content: space-between; color: var(--text-secondary); font-size: 13px; }
.kpi-arrow { color: var(--text-muted); transition: transform .2s ease, color .2s ease; }
.kpi--link:hover .kpi-arrow { color: var(--brand); transform: translateX(2px); }
.kpi-value { margin-top: 10px; color: var(--text-title); font-size: 28px; font-weight: 600; line-height: 1.15; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
.kpi-suffix { margin-left: 4px; color: var(--text-secondary); font-size: 13px; font-weight: 400; letter-spacing: 0; }
.kpi-foot { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: auto; padding-top: 12px; color: var(--text-muted); font-size: 12px; font-variant-numeric: tabular-nums; }
.kpi--lead { color: #fff; background: #1e1b4b; border-color: #1e1b4b; }
.kpi--lead .kpi-label { color: rgba(255, 255, 255, .7); }
.kpi--lead .kpi-value { color: #fff; font-size: 36px; }
.kpi-unit { margin-right: 4px; font-size: 20px; font-weight: 500; opacity: .7; }
.kpi--lead .kpi-foot { color: rgba(255, 255, 255, .72); font-size: 13px; }
.kpi-chip { padding: 2px 8px; border-radius: 6px; background: rgba(255, 255, 255, .12); }
.kpi--alert { border-color: #f3d19e; background: #fffaf0; }
.kpi--alert .kpi-value { color: #9a5b00; }
.kpi--alert .kpi-foot { color: #9a5b00; font-weight: 500; }

.dash-panels { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 8fr); gap: 12px; }
.panel { min-width: 0; padding: 20px; background: var(--bg-card); border: 1px solid var(--border); border-radius: var(--radius-lg); }
.panel-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 16px; }
.panel-title { margin: 0; color: var(--text-title); font-size: 15px; font-weight: 600; }
.panel-note { color: var(--text-muted); font-size: 12px; }
.chart { height: 300px; }

.funnel { display: flex; flex-direction: column; gap: 18px; margin: 0; padding: 8px 0 0; list-style: none; }
.funnel-row { display: grid; grid-template-columns: 40px minmax(0, 1fr) 56px 56px; align-items: center; gap: 12px; font-variant-numeric: tabular-nums; }
.funnel-stage { color: var(--text-body); font-size: 13px; }
.funnel-track { height: 28px; border-radius: 6px; background: var(--bg-subtle); overflow: hidden; }
.funnel-bar { display: block; height: 100%; border-radius: 6px; background: var(--brand); transition: width .4s ease; }
.funnel-count { color: var(--text-title); font-size: 15px; font-weight: 600; text-align: right; }
.funnel-rate { color: var(--text-secondary); font-size: 12px; text-align: right; }

.dash-def { padding: 0 4px; color: var(--text-secondary); font-size: 12px; line-height: 1.8; }
.dash-def summary { width: fit-content; cursor: pointer; color: var(--text-secondary); }
.dash-def summary:hover { color: var(--brand); }
.dash-def ul { margin: 8px 0 4px; padding-left: 18px; }
.dash-def b { color: var(--text-body); font-weight: 500; }
.dash-def p { margin: 0; color: var(--text-muted); }

@media (max-width: 1100px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .kpi--lead { grid-column: 1 / -1; }
  .dash-panels { grid-template-columns: minmax(0, 1fr); }
}
@media (prefers-reduced-motion: reduce) {
  .kpi--link, .kpi-arrow, .funnel-bar { transition: none; }
}
</style>
