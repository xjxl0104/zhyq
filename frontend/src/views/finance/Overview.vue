<template>
  <div class="finance-overview-host">
  <Teleport to="body" :disabled="!expanded">
  <div class="commerce-page finance-overview" :class="{ 'is-expanded': expanded }" :aria-busy="loading">
    <header class="commerce-page__header overview-header">
      <div>
        <div class="commerce-page__eyebrow">Finance overview</div>
        <h1 class="commerce-page__title">财务总览</h1>
        <div class="scope-line">
          <span class="scope-chip"><OfficeBuilding />{{ data?.scopeLabel || '全园区 · 全部项目' }}</span>
          <span v-if="data" class="scope-date">更新于 {{ updatedAt }}</span>
        </div>
      </div>
      <div class="overview-controls">
        <label class="month-control"><Calendar /><span>账期</span><input v-model="month" type="month" min="2000-01" max="2100-12" aria-label="选择账期月份" /></label>
        <button class="plain-button icon-button" type="button" :disabled="loading" aria-label="刷新财务数据" @click="refreshAll"><Refresh :class="{ spinning: loading }" /></button>
        <details class="definitions">
          <summary class="plain-button icon-button" aria-label="统计口径说明"><InfoFilled /></summary>
          <div class="definitions-pop">
            <p><strong>账期收缴</strong>按账单账期起始月统计，补收上月会更新上月已收。</p>
            <p><strong>逾期</strong>应收日与登记表滞纳起算日取较晚者，早于今天即逾期。</p>
            <p><strong>合同归属</strong>按账单关联的合同；没有时按应收登记的合同；仍没有则单列“未关联合同”。</p>
            <p><strong>范围</strong>财务账单中的应收费用，不随顶部项目切换；云仓服务费、佣金在各自台账。</p>
          </div>
        </details>
        <button class="plain-button expand-button" type="button" :aria-label="expanded ? '退出大屏' : '展开大屏'" :aria-pressed="expanded" @click="expanded = !expanded"><FullScreen /><span>{{ expanded ? '退出大屏' : '展开大屏' }}</span></button>
      </div>
    </header>

    <div v-if="error" class="state-panel error-panel" role="alert">
      <span class="state-icon"><Warning /></span><div><h2>财务数据暂时没有加载成功</h2><p>请检查网络后重试。</p></div>
      <button type="button" class="primary-button" @click="load">重新加载</button>
    </div>
    <div v-else-if="!data" class="state-panel" role="status"><Loading class="spinning" />正在汇总账单与收款数据…</div>

    <section v-if="data && !error" class="kpi-grid" :class="{ refreshing: loading }" aria-label="关键指标">
      <div class="kpi-tile">
        <span class="kpi-label"><i class="dot due"></i>{{ periodLabel }}应收</span>
        <strong class="kpi-value">{{ money(data.periodReceivable) }}<small>元</small></strong>
        <svg class="sparkline" viewBox="0 0 100 28" preserveAspectRatio="none" aria-hidden="true"><polyline :points="sparkPoints" /></svg>
      </div>
      <div class="kpi-tile">
        <span class="kpi-label"><i class="dot paid"></i>{{ periodLabel }}已收</span>
        <strong class="kpi-value paid">{{ money(data.periodReceived) }}<small>元</small></strong>
        <div class="kpi-foot"><span class="rate-track" role="progressbar" aria-label="所选账期收缴率" :aria-valuenow="rate" aria-valuemin="0" aria-valuemax="100"><span :style="{ width: `${rate}%` }"></span></span><b>{{ hasReceivable ? `${rate}%` : '—' }}</b></div>
      </div>
      <button class="kpi-tile is-link" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { onlyOutstanding: 'true', direction: '1' })">
        <span class="kpi-label"><i class="dot wait"></i>待收合计</span>
        <strong class="kpi-value">{{ money(data.totalOutstanding) }}<small>元</small></strong>
        <div class="kpi-foot"><span class="split-track" aria-hidden="true"><span v-for="part in outstandingParts" :key="part.key" :class="`seg-${part.key}`" :style="{ width: `${part.percent}%` }"></span></span><b>{{ wanLabel(data.dueOutstanding) }}万到期</b></div>
      </button>
      <button class="kpi-tile is-link is-risk" type="button" :disabled="!canGo('/finance/overdue')" @click="go('/finance/overdue')">
        <span class="kpi-label"><i class="dot risk"></i>逾期</span>
        <strong class="kpi-value risk">{{ money(data.overdueOutstanding) }}<small>元</small></strong>
        <div class="kpi-foot"><span class="kpi-count">{{ data.overdueBillCount }} 张账单</span><b v-if="canGo('/finance/overdue')">去催收<ArrowRight /></b></div>
      </button>
    </section>

    <ContractCollectionPanel :data="collection" :error="collectionError" :can-bill="canGo('/finance/bill')" :can-contract="canGo('/contract/list')" @retry="loadCollection" @open-bills="openContractBills" @open-contract="openContract" />

    <div v-if="data && !error" class="chart-grid" :class="{ refreshing: loading }">
      <section class="commerce-card chart-card" aria-labelledby="trend-title">
        <div class="card-head"><h2 id="trend-title">近六个月收缴</h2><span class="hint">点柱子切换账期</span></div>
        <div v-if="trendMax > 0" class="chart-box"><OverviewChart :option="trendChart" label="近六个月应收与已收柱状图" @select="onTrendSelect" /></div>
        <div v-else class="empty-chart"><TrendCharts /><p>近六个月没有应收账单</p></div>
        <details class="chart-details"><summary>精确金额</summary><table><caption class="sr-only">近六个月账期金额，单位元</caption><thead><tr><th>账期</th><th>应收 / 元</th><th>已收 / 元</th></tr></thead><tbody><tr v-for="point in data.trend" :key="point.month"><th>{{ point.month }}</th><td>{{ money(point.receivable) }}</td><td>{{ money(point.received) }}</td></tr></tbody></table></details>
      </section>

      <section class="commerce-card chart-card" aria-labelledby="fees-title">
        <div class="card-head"><h2 id="fees-title">{{ periodLabel }}应收构成</h2><span class="hint">{{ data.month }}</span></div>
        <div v-if="positiveFees.length" class="fee-body">
          <div class="donut-box"><OverviewChart :option="feeChart" label="本账期应收构成环形图" @select="onFeeSelect" /></div>
          <ul class="fee-legend">
            <li v-for="(fee, index) in positiveFees" :key="fee.feeType">
              <button class="fee-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { feeType: fee.feeType, direction: '1' })">
                <i :style="{ background: FEE_COLORS[index % FEE_COLORS.length] }"></i><span>{{ fee.feeType }}</span><small>{{ percent(fee.receivable, data.periodReceivable) }}%</small><b>{{ wanLabel(fee.receivable) }}万</b>
              </button>
            </li>
          </ul>
        </div>
        <div v-else class="empty-chart"><Document /><p>本账期暂无费用</p></div>
      </section>

      <section class="commerce-card chart-card" aria-labelledby="aging-title">
        <div class="card-head"><h2 id="aging-title">欠款账龄</h2><span class="hint">全部未收</span></div>
        <div v-if="agingTotal > 0" class="chart-box"><OverviewChart :option="agingChart" label="欠款账龄条形图" /></div>
        <div v-else-if="collection" class="empty-chart"><CircleCheck /><p>当前没有待收款</p></div>
        <div v-else class="empty-chart"><Loading :class="{ spinning: !collectionError }" /><p>{{ collectionError ? '账龄数据未加载' : '正在计算账龄…' }}</p></div>
      </section>
    </div>

    <section class="commerce-card guide-card" aria-labelledby="workflow-title">
      <div class="guide-main">
        <h2 id="workflow-title">一笔费用怎么走</h2>
        <div class="workflow-steps" aria-label="财务业务流程">
          <button v-for="(step, index) in steps" :key="step.key" class="workflow-step" :class="{ selected: selectedStep === index }" type="button" :aria-pressed="selectedStep === index" @click="selectedStep = index">
            <span class="step-icon"><component :is="step.icon" /></span><span class="step-title">{{ index + 1 }}. {{ step.title }}</span>
          </button>
        </div>
        <div class="step-detail" aria-live="polite">
          <p class="detail-copy">{{ activeStep.description }}</p>
          <button class="primary-button" type="button" :disabled="!canGo(activeStep.path)" @click="go(activeStep.path)">{{ canGo(activeStep.path) ? activeStep.action : '需开通此功能权限' }}<ArrowRight /></button>
        </div>
      </div>
      <div class="related-links" aria-label="其他财务台账">
        <button v-for="link in visibleRelated" :key="link.path" type="button" @click="go(link.path)"><component :is="link.icon" />{{ link.label }}</button>
        <span v-if="!visibleRelated.length" class="hint">其他入口按账号权限开放</span>
      </div>
    </section>
  </div>
  </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, Calendar, CircleCheck, Document, Files, FullScreen, InfoFilled, Loading, Money, OfficeBuilding, Refresh, Tickets, TrendCharts, Wallet, Warning } from '@element-plus/icons-vue'
import { reportApi } from '@/api/finance'
import { useAccessStore } from '@/stores/access'
import { canAccessNavigation } from '@/utils/navigationAccess'
import { money } from '@/utils/format'
import ContractCollectionPanel from './components/overview/ContractCollectionPanel.vue'
import OverviewChart from './components/overview/OverviewChart.vue'
import { FEE_COLORS, agingOption, feeDonutOption, trendOption, wanLabel } from './components/overview/overviewCharts'

const router = useRouter()
const access = useAccessStore()
const expanded = ref(false)
const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const data = ref(null)
const loading = ref(false)
const error = ref(false)
const updatedAt = ref('')
const collection = ref(null)
const collectionError = ref(false)
const selectedStep = ref(0)
let requestVersion = 0
let collectionVersion = 0

const periodLabel = computed(() => `${Number((data.value?.month || month.value).slice(5))}月`)
const rate = computed(() => Math.min(100, Math.max(0, Math.round(Number(data.value?.collectionRate || 0) * 1000) / 10)))
const hasReceivable = computed(() => Number(data.value?.periodReceivable) > 0)
const positiveFees = computed(() => (data.value?.feeStructure || []).filter(fee => Number(fee.receivable) > 0))
const trendMax = computed(() => Math.max(0, ...(data.value?.trend || []).flatMap(point => [Number(point.receivable), Number(point.received)])))
const trendChart = computed(() => trendOption(data.value?.trend || [], month.value))
const feeChart = computed(() => feeDonutOption(positiveFees.value, data.value?.periodReceivable))
const agingChart = computed(() => agingOption(collection.value?.aging || []))
const agingTotal = computed(() => (collection.value?.aging || []).reduce((sum, bucket) => sum + Number(bucket.amount || 0), 0))
const sparkPoints = computed(() => {
  const values = (data.value?.trend || []).map(point => Number(point.receivable) || 0)
  const max = Math.max(...values, 0)
  if (values.length < 2 || max <= 0) return '0,26 100,26'
  return values.map((value, index) => `${(index / (values.length - 1) * 100).toFixed(1)},${(26 - value / max * 22).toFixed(1)}`).join(' ')
})
const outstandingParts = computed(() => {
  const overview = data.value || {}
  const overdue = Math.max(0, Number(overview.overdueOutstanding) || 0)
  const due = Math.max(0, (Number(overview.dueOutstanding) || 0) - overdue)
  const notDue = Math.max(0, (Number(overview.upcomingOutstanding) || 0) + (Number(overview.undatedOutstanding) || 0))
  const total = overdue + due + notDue
  if (total <= 0) return []
  return [['overdue', overdue], ['due', due], ['notDue', notDue]].filter(([, value]) => value > 0)
    .map(([key, value]) => ({ key, percent: Math.round(value / total * 1000) / 10 }))
})

const steps = [
  { key: 'register', title: '登记依据', icon: Document, description: '核对租客、空间、合同期限与计费规则；租金和物业费以应收登记为准，水电费由抄表分摊。', path: '/finance/receivable-register', action: '查看应收登记' },
  { key: 'bill', title: '生成账单', icon: Tickets, description: '系统按月出账，保证金单独出账；账期说明费用属于哪个月，应收日说明什么时候该交。', path: '/finance/bill', action: '查看所有账单' },
  { key: 'receive', title: '确认收款', icon: Wallet, description: '钱到账后在收银台选租客和账单登记，可分次收；零元账单走核销，收错款用红冲留痕。', path: '/finance/cashier', action: '进入收银台' },
  { key: 'record', title: '留存凭据', icon: Files, description: '收款后自动生成流水和收据；需要发票时另行申请、审核与登记。', path: '/finance/receipt', action: '查看收据记录' }
]
const activeStep = computed(() => steps[selectedStep.value])
const related = [
  { label: '收支流水', path: '/finance/flow', icon: Wallet },
  { label: '发票记录', path: '/finance/invoice', icon: Tickets },
  { label: '财务报表', path: '/finance/report', icon: TrendCharts },
  { label: '水电结算', path: '/energy/settlement', icon: Document },
  { label: '云仓服务费', path: '/crm/marketing/bill', icon: OfficeBuilding },
  { label: '佣金与提现', path: '/crm/marketing/withdrawal', icon: Money }
]
const visibleRelated = computed(() => related.filter(link => canGo(link.path)))

function canGo(path) { return canAccessNavigation(path, access.paths, access.admin) }
function go(path, query = {}) { if (canGo(path)) { expanded.value = false; router.push({ path, query }) } }
function percent(part, total) { return Number(total) > 0 ? Math.min(100, Math.max(0, Math.round(Number(part) / Number(total) * 100))) : 0 }
function onTrendSelect(params) { const point = data.value?.trend?.[params?.dataIndex]; if (point) month.value = point.month }
function onFeeSelect(params) { if (params?.name) go('/finance/bill', { feeType: params.name, direction: '1' }) }
function openContractBills(row) {
  if (row.contractId) go('/finance/bill', { contractId: String(row.contractId), contractCode: row.contractCode || '', direction: '1' })
  else if (row.tenantRefId) go('/finance/bill', { tenantRefId: String(row.tenantRefId), direction: '1' })
}
function openContract(row) {
  if (row.contractId && canGo('/contract/list')) { expanded.value = false; router.push({ path: `/contract/detail/${row.contractId}` }) }
}

async function load() {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month.value)) return
  const version = ++requestVersion
  loading.value = true
  error.value = false
  try {
    const result = await reportApi.overview({ month: month.value })
    if (version !== requestVersion) return
    data.value = result
    updatedAt.value = new Date().toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' })
  } catch {
    if (version === requestVersion) { error.value = true; data.value = null }
  } finally { if (version === requestVersion) loading.value = false }
}
async function loadCollection() {
  const version = ++collectionVersion
  collectionError.value = false
  try {
    const result = await reportApi.contractCollection()
    if (version === collectionVersion) collection.value = result
  } catch {
    if (version === collectionVersion) { collectionError.value = true; collection.value = null }
  }
}
function refreshAll() { load(); loadCollection() }
function onKey(event) { if (event.key === 'Escape') expanded.value = false }
watch(month, load)
onMounted(() => { refreshAll(); window.addEventListener('keydown', onKey) })
onDeactivated(() => { expanded.value = false })
onBeforeUnmount(() => { requestVersion++; collectionVersion++; window.removeEventListener('keydown', onKey) })
</script>

<style scoped>
.finance-overview { --fo-paid: #2f4bf0; --fo-due: #c3cbfa; --fo-wait: #eaa23c; --fo-risk: #e0473d; --fo-risk-ink: #c2413a; --fo-notdue: #dfe3ec; --fo-ink2: #68708a; color: var(--text-body); font-size: 13px; font-variant-numeric: tabular-nums; }
.finance-overview.is-expanded { position: fixed; inset: 0; z-index: 2100; overflow: auto; background: #f3f5fa; padding: 24px max(28px, calc((100vw - 1760px) / 2)); border-radius: 0; }
.finance-overview :is(button, input, summary):focus-visible { outline: 3px solid var(--fo-paid); outline-offset: 3px; }
.finance-overview svg { width: 18px; height: 18px; flex-shrink: 0; }
.finance-overview button { font: inherit; cursor: pointer; }
.finance-overview button:disabled { cursor: not-allowed; }
h2 { margin: 0; color: var(--text-title); font-size: 15px; font-weight: 700; letter-spacing: -.015em; }
.overview-header { align-items: flex-end; margin-bottom: 16px; }
.scope-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px; margin-top: 8px; color: var(--fo-ink2); font-size: 12px; }
.scope-chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; background: rgba(255, 255, 255, .72); border: 1px solid var(--border); border-radius: 999px; }
.scope-chip svg { width: 13px; height: 13px; color: var(--brand); }
.overview-controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.month-control { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 6px 0 12px; color: var(--fo-ink2); font-size: 12px; font-weight: 560; background: #fff; border: 1px solid var(--border); border-radius: 11px; }
.month-control svg { width: 15px; height: 15px; color: var(--brand); }
.month-control input { border: 0; background: transparent; color: var(--text-title); font: inherit; font-size: 13px; font-weight: 650; max-width: 150px; min-height: 30px; }
.plain-button, .primary-button { display: inline-flex; justify-content: center; align-items: center; gap: 7px; min-height: 40px; padding: 8px 14px; border-radius: 11px; border: 1px solid var(--border); background: #fff; color: var(--text-body); font-size: 13px; font-weight: 560; white-space: nowrap; transition: background .16s, border-color .16s, color .16s; }
.plain-button:hover:not(:disabled) { border-color: #c9cffb; color: var(--brand); background: #eef0ff; }
.plain-button svg { width: 16px; height: 16px; }
.plain-button:disabled { opacity: .55; }
.icon-button { width: 40px; padding: 0; }
.primary-button { background: var(--brand); border-color: var(--brand); color: #fff; }
.primary-button:hover:not(:disabled) { background: var(--brand-hover); }
.primary-button:disabled { opacity: .55; }
.primary-button svg { width: 14px; height: 14px; }
.definitions { position: relative; }
.definitions > summary { list-style: none; cursor: pointer; }
.definitions > summary::-webkit-details-marker { display: none; }
.definitions-pop { position: absolute; z-index: 30; top: calc(100% + 8px); right: 0; width: 320px; padding: 14px 16px; color: var(--fo-ink2); background: #fff; border: 1px solid var(--border); border-radius: 12px; box-shadow: 0 18px 40px rgba(41, 49, 93, .16); font-size: 12px; line-height: 1.6; }
.definitions-pop p { margin: 0 0 8px; }
.definitions-pop p:last-child { margin: 0; }
.definitions-pop strong { display: block; color: var(--text-title); font-size: 13px; }

/* 指标卡 */
.kpi-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.kpi-tile { position: relative; display: flex; flex-direction: column; gap: 8px; min-width: 0; padding: 16px 18px; text-align: left; color: var(--text-body); background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-card); }
.kpi-tile.is-link { transition: transform .16s, border-color .16s, box-shadow .16s; }
.kpi-tile.is-link:hover:not(:disabled) { transform: translateY(-2px); border-color: #c9cffb; box-shadow: 0 12px 26px rgba(41, 49, 93, .08); }
.kpi-tile.is-risk { background: linear-gradient(180deg, #fff7f6, #fff 60%); border-color: #fbe0dd; }
.kpi-label { display: flex; align-items: center; gap: 7px; color: var(--fo-ink2); font-size: 13px; font-weight: 600; }
.dot { width: 9px; height: 9px; border-radius: 3px; }
.dot.due { background: var(--fo-due); }
.dot.paid { background: var(--fo-paid); }
.dot.wait { background: var(--fo-wait); }
.dot.risk { background: var(--fo-risk); }
.kpi-value { color: var(--text-title); font-size: clamp(22px, 1.9vw, 30px); font-weight: 750; letter-spacing: -.04em; line-height: 1.15; white-space: nowrap; }
.kpi-value small { margin-left: 4px; color: var(--fo-ink2); font-size: 12px; font-weight: 500; letter-spacing: 0; }
.kpi-value.paid { color: var(--fo-paid); }
.kpi-value.risk { color: var(--fo-risk-ink); }
.finance-overview .sparkline { width: 100%; height: 28px; overflow: visible; }
.sparkline polyline { fill: none; stroke: #8f9cf6; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; vector-effect: non-scaling-stroke; }
.kpi-foot { display: flex; align-items: center; gap: 10px; min-height: 28px; color: var(--fo-ink2); font-size: 12px; }
.kpi-foot b { display: inline-flex; align-items: center; gap: 3px; color: var(--text-title); font-size: 12px; font-weight: 700; white-space: nowrap; }
.kpi-foot b svg { width: 12px; height: 12px; }
.is-risk .kpi-foot b { color: var(--fo-risk-ink); }
.rate-track, .split-track { flex: 1; display: flex; gap: 2px; height: 8px; overflow: hidden; background: #f9e4c4; border-radius: 999px; }
.split-track { background: #f1f2f6; }
.rate-track > span { display: block; background: var(--fo-paid); border-radius: inherit; transition: width .5s cubic-bezier(.2, .7, .2, 1); }
.seg-overdue { background: var(--fo-risk); }
.seg-due { background: var(--fo-wait); }
.seg-notDue { background: var(--fo-notdue); }
.kpi-count { flex: 1; }

/* 图表 */
.chart-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr) minmax(0, 1fr); gap: 16px; margin-bottom: 16px; }
.chart-card { display: flex; flex-direction: column; min-width: 0; padding: 18px 20px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.hint { padding: 2px 9px; color: var(--fo-ink2); background: #f4f5f9; border-radius: 999px; font-size: 12px; white-space: nowrap; }
.chart-box { height: 230px; }
.fee-body { display: grid; grid-template-columns: 132px minmax(0, 1fr); align-items: center; gap: 10px; min-height: 230px; }
.donut-box { height: 132px; }
.fee-legend { display: grid; gap: 2px; margin: 0; padding: 0; list-style: none; }
.fee-row { display: grid; grid-template-columns: 10px max-content minmax(0, 1fr) max-content; align-items: center; gap: 8px; width: 100%; padding: 7px 8px; color: var(--text-body); text-align: left; background: transparent; border: 0; border-radius: 9px; font-size: 13px; }
.fee-row:hover:not(:disabled) { background: #f4f6fd; }
.fee-row i { width: 10px; height: 10px; border-radius: 3px; }
.fee-row span { overflow: hidden; color: var(--text-title); font-weight: 600; white-space: nowrap; text-overflow: ellipsis; }
.fee-row small { color: var(--fo-ink2); font-size: 12px; text-align: right; }
.fee-row b { color: var(--text-title); font-weight: 700; white-space: nowrap; }
.empty-chart { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; min-height: 230px; color: var(--fo-ink2); font-size: 13px; }
.empty-chart svg { width: 36px; height: 36px; padding: 9px; color: var(--brand); background: #eef0ff; border-radius: 11px; }
.empty-chart p { margin: 0; }
.chart-details { margin-top: 6px; color: var(--fo-ink2); font-size: 12px; }
.chart-details summary { width: fit-content; cursor: pointer; }
.chart-details summary:hover { color: var(--brand); }
.chart-details table { width: 100%; margin-top: 8px; border-collapse: collapse; }
.chart-details :is(th, td) { padding: 6px 4px; text-align: right; font-weight: 400; border-bottom: 1px solid var(--border); }
.chart-details th:first-child { text-align: left; }

/* 上手区 */
.guide-card { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: start; gap: 16px 28px; padding: 16px 20px; }
.guide-main { display: grid; grid-template-columns: auto minmax(0, 1fr); align-items: center; gap: 10px 18px; min-width: 0; }
.workflow-steps { display: flex; flex-wrap: wrap; gap: 6px; }
.workflow-step { display: inline-flex; align-items: center; gap: 7px; padding: 6px 12px 6px 6px; color: var(--text-body); background: #f6f7fb; border: 1px solid transparent; border-radius: 999px; font-size: 13px; font-weight: 600; transition: background .16s, border-color .16s; }
.workflow-step:hover { background: #fff; border-color: #c9cffb; }
.workflow-step.selected { color: var(--brand-active); background: #eef0ff; border-color: #c3cbfa; }
.step-icon { display: grid; place-items: center; width: 26px; height: 26px; color: var(--brand); background: #fff; border-radius: 50%; }
.step-icon svg { width: 14px; height: 14px; }
.selected .step-icon { color: #fff; background: var(--brand); }
.step-detail { grid-column: 1 / -1; display: flex; align-items: center; gap: 14px; padding: 10px 12px; background: #f8f9fc; border-radius: 11px; }
.detail-copy { flex: 1; margin: 0; color: var(--fo-ink2); font-size: 13px; line-height: 1.6; }
.step-detail .primary-button { min-height: 34px; padding: 6px 12px; }
.related-links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.related-links button { display: inline-flex; align-items: center; gap: 7px; padding: 7px 11px; color: var(--text-body); background: #f6f7fb; border: 1px solid transparent; border-radius: 10px; font-size: 13px; font-weight: 600; white-space: nowrap; transition: background .16s, border-color .16s, color .16s; }
.related-links button:hover { color: var(--brand); background: #eef0ff; border-color: #d7dbfb; }
.related-links svg { width: 15px; height: 15px; color: var(--brand); }

/* 状态 */
.refreshing { opacity: .6; transition: opacity .15s; }
.state-panel { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 14px; min-height: 180px; margin-bottom: 16px; padding: 24px; color: var(--fo-ink2); background: #fff; border: 1px solid var(--border); border-radius: var(--radius); }
.state-panel h2 { font-size: 16px; }
.state-panel p { margin: 4px 0 0; }
.state-icon { display: grid; place-items: center; width: 46px; height: 46px; color: var(--fo-risk-ink); background: #fff3f2; border-radius: 14px; }
.state-icon svg { width: 22px; height: 22px; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.spinning { animation: finance-spin 1s linear infinite; }
@keyframes finance-spin { to { transform: rotate(360deg); } }

@media (min-width: 1700px) { .chart-box, .empty-chart { min-height: 260px; height: 260px; } }
@media (max-width: 1280px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .chart-grid { grid-template-columns: 1fr 1fr; }
  .chart-grid > :first-child { grid-column: 1 / -1; }
  .guide-card { grid-template-columns: 1fr; }
  .related-links { grid-template-columns: repeat(auto-fill, minmax(130px, 1fr)); }
}
@media (max-width: 900px) {
  .overview-header { align-items: flex-start; }
  .expand-button span { display: none; }
  .expand-button { width: 40px; padding: 0; }
  .chart-grid { grid-template-columns: 1fr; }
  .guide-main { grid-template-columns: 1fr; }
}
@media (max-width: 680px) {
  .finance-overview, .finance-overview.is-expanded { padding: 16px 12px; }
  .overview-controls { width: 100%; flex-wrap: nowrap; }
  .month-control { flex: 1; min-width: 0; justify-content: space-between; }
  .month-control input { flex: 1; min-width: 0; max-width: none; text-align: right; }
  .definitions-pop { width: min(320px, calc(100vw - 24px)); right: -48px; }
  .kpi-grid { gap: 10px; }
  .kpi-tile { padding: 14px 12px; }
  .kpi-value { font-size: 19px; }
  .kpi-foot b { font-size: 11px; }
  .fee-body { grid-template-columns: 1fr; }
  .donut-box { height: 170px; }
  .step-detail { flex-direction: column; align-items: stretch; }
  .related-links { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 380px) { .kpi-grid { grid-template-columns: 1fr; } }
@media (prefers-reduced-motion: reduce) { .finance-overview *, .finance-overview *::before, .finance-overview *::after { animation: none !important; transition: none !important; } }
</style>
