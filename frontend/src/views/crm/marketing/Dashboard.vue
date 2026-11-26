<template>
  <div class="page-container mkt-dashboard">
    <header class="dash-head">
      <div>
        <h1 class="dash-title">全民营销看板</h1>
        <p class="dash-sub">{{ rangeLabel }}的推荐转化、出库、双轨对账与佣金概况</p>
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

    <section class="sea-collab-strip" aria-label="内部专属公海直营概览" data-testid="dash-public-sea-strip">
      <div class="sea-collab-left">
        <span class="sea-collab-badge">内部直营 0 外部佣金</span>
        <span class="sea-collab-title">广东主战区 · 专属公海池协同转化</span>
      </div>
      <div class="sea-collab-metrics">
        <span>活跃池 <b>{{ fmt(summary.drilldown?.publicSeaActiveTotal ?? 0) }}</b> 户</span>
        <span class="sea-dot">·</span>
        <span>专员/云仓在跟 <b>{{ fmt(summary.drilldown?.publicSeaClaimedCount ?? 0) }}</b> 户</span>
        <span class="sea-dot">·</span>
        <span>已签约沉淀 <b class="sea-signed">{{ fmt(summary.drilldown?.publicSeaSignedCount ?? 0) }}</b> 户</span>
      </div>
      <el-button type="primary" plain size="small" @click="$router.push('/crm/marketing/public-sea')">进入公海作战台 ›</el-button>
    </section>

    <!-- 4 张核心经营分析结论卡（L1 结论卡面，右上角固定挂「下钻 ›」） -->
    <section class="analysis-grid" aria-label="核心经营分析结论">
      <article v-for="item in analysisCards" :key="item.key" class="analysis-card" :data-analysis-key="item.key">
        <div class="analysis-head">
          <h2 class="analysis-title">{{ item.title }}</h2>
          <button
            type="button"
            class="drilldown-trigger"
            :aria-label="`${item.title} 下钻查看论证与物证`"
            @click="openDrilldown(item.key)"
          >
            下钻 ›
          </button>
        </div>
        <div class="analysis-main">
          <span class="analysis-value">{{ item.primaryText }}</span>
          <el-tag :type="item.badgeType" size="small" effect="light">{{ item.badgeText }}</el-tag>
        </div>
        <div class="analysis-foot">{{ item.footText }}</div>
      </article>
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
        <li><b>园区毛利</b>：服务费收入 + 直签实收平台费 − 应付云仓 − 佣金支出。</li>
        <li><b>活跃伙伴</b>：近 30 天有推荐或有佣金入账。</li>
      </ul>
      <p>依据 PARK-MKT-001 §6.3 · 四张分析结论卡支持 L1→L2→L3 全链路物证追溯。</p>
    </details>

    <!-- 三层下钻抽屉（L1 核心问题 + L2 判定逻辑与判据行 + 点击展开 L3 物证与站内跳转） -->
    <el-drawer
      v-model="drilldown.visible"
      :title="activeSpec ? `下钻溯源 · ${activeSpec.title}` : '下钻溯源'"
      size="min(540px, 94vw)"
      append-to-body
    >
      <div v-if="activeSpec" class="drill-body">
        <div class="drill-l1-box">
          <div class="drill-tag-row">
            <span class="drill-layer-badge">L1 核心问题</span>
            <el-tag :type="activeSpec.badgeType" size="small">{{ activeSpec.badgeText }}</el-tag>
          </div>
          <p class="drill-question">{{ activeSpec.question }}</p>
          <div class="drill-l1-conclusion">当前结论值：<b>{{ activeSpec.primaryText }}</b></div>
        </div>

        <div class="drill-logic-box">
          <div class="drill-layer-badge">L2 判定逻辑说明（同源搬运 · 零二次重算）</div>
          <p class="drill-logic-text">{{ activeSpec.logicNote }}</p>
        </div>

        <div class="drill-rows-head">
          <span>L2 逐条判据列表</span>
          <span class="drill-rows-tip">点击任意判据行展开 L3 原始物证</span>
        </div>

        <div class="drill-rows">
          <div
            v-for="row in activeSpec.rows"
            :key="row.key"
            class="drill-row"
            :class="{ 'is-open': expandedRowKeys.includes(row.key) }"
          >
            <button
              type="button"
              class="drill-row-summary"
              :aria-expanded="expandedRowKeys.includes(row.key)"
              @click="toggleEvidenceRow(row.key)"
            >
              <div class="drill-row-left">
                <span class="drill-row-label">{{ row.label }}</span>
                <span v-if="row.verdict" class="drill-row-verdict">{{ row.verdict }}</span>
              </div>
              <div class="drill-row-right">
                <span class="drill-row-val">{{ row.value }}</span>
                <span class="drill-row-toggle">{{ expandedRowKeys.includes(row.key) ? '收起 L3 ▴' : 'L3 物证 ▾' }}</span>
              </div>
            </button>

            <div v-if="expandedRowKeys.includes(row.key)" class="drill-l3" :data-l3-key="row.key">
              <div class="l3-item">
                <div class="l3-label">① 原始接口返回数值</div>
                <pre class="l3-code">{{ row.evidence.raw }}</pre>
              </div>
              <div class="l3-item">
                <div class="l3-label">② 数据来源表与实时聚合说明</div>
                <div class="l3-text">{{ row.evidence.source }}</div>
              </div>
              <div v-if="row.evidence.formula" class="l3-item">
                <div class="l3-label">③ 带真实参数的计算公式</div>
                <div class="l3-formula">{{ row.evidence.formula }}</div>
              </div>
              <div v-if="row.evidence.to" class="l3-item l3-action">
                <div class="l3-label">④ 站内对应台账直达</div>
                <el-button type="primary" plain size="small" @click="goLedger(row.evidence.to)">
                  {{ row.evidence.linkLabel || '前往对应业务台账' }} ›
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ArrowRight } from '@element-plus/icons-vue'
import { mktDashboardApi } from '@/api/marketing'
import { buildDashboardDrilldownCards } from './dashboardDrilldown'

const router = useRouter()

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

// 同源分析结论卡与三层下钻状态
const analysisCards = computed(() => buildDashboardDrilldownCards(summary, rangeLabel.value))
const drilldown = reactive({ visible: false, activeKey: '' })
const expandedRowKeys = ref([])
const activeSpec = computed(() => analysisCards.value.find(c => c.key === drilldown.activeKey) || null)

function openDrilldown(key) {
  drilldown.activeKey = key
  drilldown.visible = true
  const spec = analysisCards.value.find(c => c.key === key)
  expandedRowKeys.value = spec?.rows?.[0] ? [spec.rows[0].key] : []
}

function toggleEvidenceRow(rowKey) {
  const idx = expandedRowKeys.value.indexOf(rowKey)
  if (idx >= 0) expandedRowKeys.value.splice(idx, 1)
  else expandedRowKeys.value.push(rowKey)
}

function goLedger(target) {
  drilldown.visible = false
  router.push(target)
}

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
  for (const k of Object.keys(summary)) delete summary[k]
  Object.assign(summary, s || {})
  funnel.value = toFunnelRows(f || [])
  trendChart?.setOption(trendOption(t || []), true)
}
const resize = () => { trendChart?.resize() }
onMounted(() => {
  if (trendEl.value) {
    trendChart = echarts.init(trendEl.value)
  }
  window.addEventListener('resize', resize)
  load()
})
onBeforeUnmount(() => { window.removeEventListener('resize', resize); trendChart?.dispose() })

defineExpose({
  range,
  summary,
  analysisCards,
  drilldown,
  expandedRowKeys,
  activeSpec,
  load,
  openDrilldown,
  toggleEvidenceRow,
  goLedger
})
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

.sea-collab-strip {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap;
  padding: 12px 16px; background: linear-gradient(90deg, #eff6ff 0%, #f8fafc 100%);
  border: 1px solid #bfdbfe; border-radius: var(--radius-lg); font-size: 13px; color: #1e293b;
}
.sea-collab-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sea-collab-badge { background: #1d4ed8; color: #fff; font-size: 11.5px; font-weight: 600; padding: 2px 8px; border-radius: 999px; }
.sea-collab-title { font-weight: 600; color: #0f172a; }
.sea-collab-metrics { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; color: #334155; font-variant-numeric: tabular-nums; }
.sea-dot { color: #94a3b8; }
.sea-signed { color: #16a34a; }

/* 4 张经营分析结论卡 */
.analysis-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.analysis-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 18px;
  background: var(--bg-card);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}
.analysis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.analysis-title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-title);
  line-height: 1.4;
}
.drilldown-trigger {
  flex: none;
  padding: 2px 8px;
  border: 1px solid rgba(79, 70, 229, .22);
  border-radius: 999px;
  background: rgba(79, 70, 229, .05);
  color: var(--brand, #4f46e5);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color .18s ease, border-color .18s ease;
}
.drilldown-trigger:hover {
  background: rgba(79, 70, 229, .12);
  border-color: rgba(79, 70, 229, .45);
}
.analysis-main {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}
.analysis-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-title);
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.02em;
}
.analysis-foot {
  margin-top: auto;
  padding-top: 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}

/* 三层下钻抽屉样式 */
.drill-body { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
.drill-l1-box {
  padding: 14px 16px;
  border-radius: 10px;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
}
.drill-tag-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.drill-layer-badge {
  display: inline-block;
  font-size: 11.5px;
  font-weight: 600;
  color: #4338ca;
}
.drill-question { margin: 8px 0 6px; font-size: 13.5px; line-height: 1.6; color: #1e1b4b; font-weight: 500; }
.drill-l1-conclusion { font-size: 13px; color: #312e81; font-variant-numeric: tabular-nums; }
.drill-logic-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--bg-subtle, #f8fafc);
  border: 1px solid var(--el-border-color-lighter);
}
.drill-logic-text { margin: 6px 0 0; font-size: 12.5px; line-height: 1.65; color: var(--text-secondary); }
.drill-rows-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-title);
}
.drill-rows-tip { font-size: 12px; font-weight: 400; color: var(--text-muted); }
.drill-rows { display: flex; flex-direction: column; gap: 10px; }
.drill-row {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}
.drill-row.is-open { border-color: #a5b4fc; }
.drill-row-summary {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  font: inherit;
}
.drill-row-summary:hover { background: #f8fafc; }
.drill-row-left { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.drill-row-label { font-size: 13.5px; font-weight: 500; color: var(--text-title); }
.drill-row-verdict { font-size: 12px; color: var(--text-secondary); }
.drill-row-right { display: flex; flex-direction: column; align-items: flex-end; gap: 3px; flex: none; }
.drill-row-val { font-size: 14px; font-weight: 600; color: var(--text-title); font-variant-numeric: tabular-nums; }
.drill-row-toggle { font-size: 11.5px; color: #4f46e5; }
.drill-l3 {
  padding: 12px 14px;
  border-top: 1px dashed var(--el-border-color-light);
  background: #fcfcff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.l3-item { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.l3-label { font-size: 12px; font-weight: 600; color: #475569; }
.l3-code {
  margin: 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #0f172a;
  color: #e2e8f0;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.l3-text, .l3-formula {
  font-size: 12.5px;
  line-height: 1.6;
  color: #334155;
  overflow-wrap: anywhere;
}
.l3-formula {
  padding: 6px 10px;
  border-radius: 6px;
  background: #eef2ff;
  color: #312e81;
  font-variant-numeric: tabular-nums;
}
.l3-action { align-items: flex-start; }

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

@media (max-width: 1180px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .kpi--lead { grid-column: 1 / -1; }
  .analysis-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .dash-panels { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 640px) {
  .analysis-grid { grid-template-columns: minmax(0, 1fr); }
}
@media (prefers-reduced-motion: reduce) {
  .kpi--link, .kpi-arrow, .funnel-bar, .drilldown-trigger { transition: none; }
}
</style>
