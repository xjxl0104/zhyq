<template>
  <div class="finance-overview-host">
  <Teleport to="body" :disabled="!expanded">
  <div ref="board" class="finance-overview" :class="{ 'is-expanded': expanded }" :aria-busy="loading">
    <header class="overview-header">
      <div>
        <h1>财务总览</h1>
        <p>看懂每一笔应收，找到下一步工作。</p>
      </div>
      <div class="overview-controls">
        <label class="month-control">账期 <input v-model="month" type="month" min="2000-01" max="2100-12" aria-label="选择账期月份" /></label>
        <button class="plain-button icon-button" type="button" :disabled="loading" aria-label="刷新财务数据" @click="load"><Refresh :class="{ spinning: loading }" /></button>
        <button class="plain-button expand-button" type="button" :aria-label="expanded ? '退出大屏' : '展开大屏'" :aria-pressed="expanded" @click="expanded = !expanded"><FullScreen /><span>{{ expanded ? '退出大屏' : '展开大屏' }}</span></button>
      </div>
    </header>

    <div class="scope-line">
      <span><OfficeBuilding />{{ data?.scopeLabel || '全园区 · 全部项目' }}</span>
      <span>财务账单口径 · 不随顶部项目切换</span>
      <span v-if="data" class="scope-date">更新于 {{ updatedAt }} · 累计待办截至 {{ data.asOfDate }}</span>
    </div>

    <div v-if="error" class="state-panel error-panel" role="alert">
      <Warning /><div><h2>财务数据暂时没有加载成功</h2><p>请检查网络后重试。业务流程说明仍可查看。</p></div>
      <button type="button" class="primary-button" @click="load">重新加载</button>
    </div>
    <div v-else-if="!data" class="state-panel loading-panel" role="status"><Loading class="spinning" />正在汇总账单与收款数据…</div>

    <div v-if="data && !error" class="summary-grid" :class="{ refreshing: loading }">
      <section class="surface collection-panel" aria-labelledby="collection-title">
        <div class="section-head"><div><h2 id="collection-title">{{ periodLabel }}账期，收到了哪一步</h2><p>看这个账期应收费用的累计收缴进度</p></div><span class="period-tag">{{ data.month }}</span></div>
        <div class="collection-amounts">
          <div><span class="amount-label">应收总额</span><strong class="amount">{{ money(data.periodReceivable) }}<small>元</small></strong><span class="amount-help">本金 + 滞纳金</span></div>
          <div class="received"><span class="amount-label"><i></i>已经收回</span><strong class="amount">{{ money(data.periodReceived) }}<small>元</small></strong><span class="amount-help">这些账单累计收到的钱</span></div>
          <div class="outstanding"><span class="amount-label"><i></i>还待收回</span><strong class="amount">{{ money(data.periodOutstanding) }}<small>元</small></strong><span class="amount-help">这些账单尚未结清的金额</span></div>
        </div>
        <div class="collection-progress">
          <div class="collection-track" role="progressbar" aria-label="所选账期收缴率" :aria-valuenow="rate" aria-valuemin="0" aria-valuemax="100"><span :style="{ transform: `scaleX(${rate / 100})` }"></span></div>
          <strong>{{ Number(data.periodReceivable) > 0 ? `已收 ${rate}%` : '本账期暂无应收' }}</strong>
        </div>
        <div class="cash-line"><span><Wallet />{{ periodLabel }}实际净收款 <strong>{{ money(data.cashReceived) }} 元</strong></span><span>按到账日统计，含跨期补收与红冲</span></div>
      </section>

      <section class="surface priorities" aria-labelledby="priorities-title">
        <div class="section-head"><h2 id="priorities-title">接下来处理什么</h2><span class="quiet-label">全部账期累计</span></div>
        <button class="priority-row" type="button" :disabled="!canGo('/finance/overdue')" @click="go('/finance/overdue')">
          <span class="priority-icon urgent"><AlarmClock /></span><span class="priority-copy"><strong>优先跟进逾期</strong><small>{{ data.overdueBillCount }} 张账单已逾期<span v-if="!canGo('/finance/overdue')"> · 需开通逾期账单权限</span></small></span><span class="priority-money urgent-text">{{ money(data.overdueOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/overdue')" />
        </button>
        <button class="priority-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { onlyDue: 'true', onlyOutstanding: 'true', direction: '1' })">
          <span class="priority-icon due"><Money /></span><span class="priority-copy"><strong>核对到期未收</strong><small>{{ canGo('/finance/bill') ? '含逾期，确认到账后登记收款' : '查看明细需开通账单权限' }}</small></span><span class="priority-money">{{ money(data.dueOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/bill')" />
        </button>
        <button class="priority-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { feeType: '保证金', onlyOutstanding: 'true', direction: '1' })">
          <span class="priority-icon deposit"><Lock /></span><span class="priority-copy"><strong>核对保证金</strong><small>{{ canGo('/finance/bill') ? '尚未收齐的保证金' : '查看明细需开通账单权限' }}</small></span><span class="priority-money">{{ money(data.depositOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/bill')" />
        </button>
        <p class="priority-foot">未到应收日 <strong>{{ money(data.upcomingOutstanding) }} 元</strong><span v-if="Number(data.undatedOutstanding) > 0"> · 未设应收日 {{ money(data.undatedOutstanding) }} 元</span></p>
      </section>
    </div>

    <section class="surface workflow-panel" aria-labelledby="workflow-title">
      <div class="section-head"><div><h2 id="workflow-title">一笔费用，走完这四步</h2><p>新同事从这里开始。点击环节，了解该做什么。</p></div><span class="quiet-label workflow-hint">收错款可红冲，零元账单走核销</span></div>
      <div class="workflow-steps" aria-label="财务业务流程">
        <button v-for="(step, index) in steps" :key="step.key" class="workflow-step" :class="{ selected: selectedStep === index }" type="button" :aria-pressed="selectedStep === index" @click="selectedStep = index">
          <span class="step-number">{{ index + 1 }}</span><span class="step-copy"><strong>{{ step.title }}</strong><small>{{ step.subtitle }}</small></span><ArrowRight v-if="index < steps.length - 1" class="step-arrow" />
        </button>
      </div>
      <div class="step-detail" aria-live="polite">
        <div class="detail-icon"><component :is="activeStep.icon" /></div><div class="detail-copy"><h3>{{ activeStep.question }}</h3><p>{{ activeStep.description }}</p></div>
        <button class="primary-button" type="button" :disabled="!canGo(activeStep.path)" @click="go(activeStep.path)">{{ canGo(activeStep.path) ? activeStep.action : '需开通此功能权限' }}<ArrowRight /></button>
      </div>
    </section>

    <div v-if="data && !error" class="analysis-grid">
      <section class="surface trend-panel" aria-labelledby="trend-title">
        <div class="section-head"><div><h2 id="trend-title">近六个月收缴</h2><p>按账期比较应收与累计实收</p></div><span class="chart-unit">万元</span></div>
        <div class="chart-legend"><span><i class="due-dot"></i>应收</span><span><i class="paid-dot"></i>已收</span></div>
        <div v-if="trendMax > 0" class="bar-chart" role="group" aria-label="近六个月应收实收柱状图，点击月份查看">
          <div class="chart-grid" aria-hidden="true"><span>{{ compactAmount(trendMax) }}</span><span>{{ compactAmount(trendMax / 2) }}</span><span>0</span></div>
          <div class="chart-columns">
            <button v-for="point in data.trend" :key="point.month" class="chart-column" :class="{ active: point.month === month }" type="button" :aria-label="`${point.month}，应收${money(point.receivable)}元，已收${money(point.received)}元；查看该月`" :title="`${point.month} 应收 ¥${money(point.receivable)} / 已收 ¥${money(point.received)}`" @click="month = point.month">
              <span class="bar-pair"><span class="bar receivable-bar" :style="{ height: barHeight(point.receivable) }"></span><span class="bar received-bar" :style="{ height: barHeight(point.received) }"></span></span><span class="month-label">{{ Number(point.month.slice(5)) }}月</span>
            </button>
          </div>
        </div>
        <div v-else class="empty-chart"><TrendCharts /><p>这六个月还没有应收账单</p><small>登记并生成账单后，这里会显示收缴变化。</small></div>
        <details class="chart-details"><summary>查看精确金额</summary><table><caption class="sr-only">近六个月账期金额，单位元</caption><thead><tr><th>账期</th><th>应收 / 元</th><th>已收 / 元</th></tr></thead><tbody><tr v-for="point in data.trend" :key="point.month"><th>{{ point.month }}</th><td>{{ money(point.receivable) }}</td><td>{{ money(point.received) }}</td></tr></tbody></table></details>
      </section>

      <section class="surface fee-panel" aria-labelledby="fees-title">
        <div class="section-head"><div><h2 id="fees-title">应收来自哪里</h2><p>{{ periodLabel }}账期 · 含保证金与滞纳金</p></div></div>
        <div v-if="positiveFees.length" class="fee-list">
          <button v-for="(fee, index) in positiveFees" :key="fee.feeType" class="fee-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { feeType: fee.feeType, direction: '1' })">
            <span class="fee-label"><i :style="{ background: feeColors[index % feeColors.length] }"></i>{{ fee.feeType }}<small>{{ percent(fee.receivable, data.periodReceivable) }}%</small></span><strong>{{ money(fee.receivable) }}<small> 元</small></strong>
            <span class="fee-track"><span :style="{ width: `${percent(fee.receivable, data.periodReceivable)}%`, background: feeColors[index % feeColors.length] }"></span></span>
          </button>
        </div>
        <div v-else class="empty-chart"><Document /><p>本账期暂无费用</p><small>免租期的零元账单不会计入应收结构。</small></div>
        <p class="panel-note">保证金单独核对；此处为应收结构，不代表经营利润。{{ canGo('/finance/bill') ? '点击费用查看该类全部账单。' : '查看费用明细需开通账单权限。' }}</p>
      </section>

      <section class="surface debtors-panel" aria-labelledby="debtors-title">
        <div class="section-head"><div><h2 id="debtors-title">待收款租客</h2><p>全部账期待收最多的前 5 户，含未到期</p></div></div>
        <div v-if="data.topDebtors.length" class="debtor-list">
          <button v-for="(tenant, index) in data.topDebtors" :key="tenant.tenantRefId ?? tenant.tenantName" class="debtor-row" type="button" :disabled="!tenant.tenantRefId || !canGo('/finance/bill')" @click="go('/finance/bill', { tenantRefId: tenant.tenantRefId, onlyOutstanding: 'true', direction: '1' })">
            <span class="debtor-index">{{ index + 1 }}</span><span class="debtor-copy"><strong :title="tenant.tenantName">{{ tenant.tenantName }}</strong><small>{{ tenant.billCount }} 张未结清<span v-if="Number(tenant.overdueOutstanding) > 0" class="urgent-text"> · 含逾期</span><span v-if="!tenant.tenantRefId"> · 未关联租客档案</span></small></span><span class="debtor-amount">{{ money(tenant.outstanding) }}<small>元</small></span><ArrowRight v-if="tenant.tenantRefId && canGo('/finance/bill')" />
          </button>
        </div>
        <div v-else class="empty-chart"><CircleCheck /><p>当前账单已无待收款</p><small>新增应收后，待收款租客会出现在这里。</small></div>
        <p v-if="!canGo('/finance/bill') && data.topDebtors.length" class="panel-note">查看租客欠款明细需开通账单权限。</p>
        <button v-if="canGo('/finance/bill')" class="text-button" type="button" @click="go('/finance/bill')">查看全部账单<ArrowRight /></button>
      </section>
    </div>

    <section class="related-work" aria-label="财务工作入口">
      <div><h2>还有这些工作</h2><p>按业务进入各自台账</p></div>
      <div class="related-links"><button v-for="link in visibleRelated" :key="link.path" class="plain-button" type="button" @click="go(link.path)"><component :is="link.icon" />{{ link.label }}<ArrowRight /></button><span v-if="!visibleRelated.length" class="quiet-label">其他业务入口按账号权限开放</span></div>
    </section>
    <details class="definitions"><summary><InfoFilled />这些数字怎么算？</summary><div class="definition-grid"><p><strong>账期收缴</strong>按账单账期起始月统计。比如本月补收上月租金，会更新上月账期的已收。</p><p><strong>实际净收款</strong>按收款日期统计正向收款与负向红冲，包含保证金。它与账期已收可能不同。</p><p><strong>到期与逾期</strong>到期未收按应收日判断；逾期另外考虑登记表约定的延期起算日。两者均按今天计算。</p><p><strong>面板覆盖范围</strong>汇总财务账单中的应收费用；独立云仓服务费、佣金、提现与云仓结算在各自台账查看。</p></div></details>
  </div>
  </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlarmClock, ArrowRight, CircleCheck, Document, Files, FullScreen, InfoFilled, Loading, Lock, Money, OfficeBuilding, Refresh, Tickets, TrendCharts, Wallet, Warning } from '@element-plus/icons-vue'
import { reportApi } from '@/api/finance'
import { useAccessStore } from '@/stores/access'
import { canAccessNavigation } from '@/utils/navigationAccess'
import { money } from '@/utils/format'

const router = useRouter()
const access = useAccessStore()
const board = ref(null)
const expanded = ref(false)
const now = new Date()
const month = ref(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`)
const data = ref(null)
const loading = ref(false)
const error = ref(false)
const updatedAt = ref('')
const selectedStep = ref(0)
let requestVersion = 0
const periodLabel = computed(() => `${Number((data.value?.month || month.value).slice(5))} 月`)
const rate = computed(() => Math.min(100, Math.max(0, Math.round(Number(data.value?.collectionRate || 0) * 1000) / 10)))
const positiveFees = computed(() => (data.value?.feeStructure || []).filter(fee => Number(fee.receivable) > 0))
const trendMax = computed(() => Math.max(0, ...(data.value?.trend || []).flatMap(point => [Number(point.receivable), Number(point.received)])))
const feeColors = ['#4f46e5', '#38877d', '#bb8435', '#83709b', '#67758d']
const steps = [
  { key: 'register', title: '登记依据', subtitle: '确定谁该交、交多少', icon: Document, question: '这笔费用为什么要收？', description: '先核对租客、空间、合同期限与计费规则。租金和物业费以应收登记为依据；水电费由抄表分摊生成账单。', path: '/finance/receivable-register', action: '查看应收登记' },
  { key: 'bill', title: '生成账单', subtitle: '明确账期与应收日', icon: Tickets, question: '这笔费用什么时候收？', description: '系统按月生成租金、物业费，保证金单独出账。账期说明费用属于哪一月，应收日说明什么时候该交。', path: '/finance/bill', action: '查看所有账单' },
  { key: 'receive', title: '确认收款', subtitle: '到账后登记，欠款继续跟进', icon: Wallet, question: '钱到了，再登记到对应账单', description: '在收银台选择租客和账单，核对实际到账金额后登记。可分次收款；零元账单走核销，收错款通过红冲留痕。', path: '/finance/cashier', action: '进入收银台' },
  { key: 'record', title: '留存凭据', subtitle: '流水、收据与发票可追溯', icon: Files, question: '这笔收款，有哪些凭据？', description: '收款后自动生成流水和收据，可打印收据并留存回单。需要发票时，另行申请、审核与登记开票。', path: '/finance/receipt', action: '查看收据记录' }
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
function barHeight(value) { return `${trendMax.value > 0 ? Math.max(0, Number(value)) / trendMax.value * 100 : 0}%` }
function compactAmount(value) { return (Number(value) / 10000).toLocaleString('zh-CN', { maximumFractionDigits: 1 }) }
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
function onKey(event) { if (event.key === 'Escape') expanded.value = false }
watch(month, load)
onMounted(() => { load(); window.addEventListener('keydown', onKey) })
onDeactivated(() => { expanded.value = false })
onBeforeUnmount(() => { requestVersion++; window.removeEventListener('keydown', onKey) })
</script>

<style scoped>
.finance-overview { --finance-green: #277466; --finance-amber: #925d13; padding: 24px; color: var(--text-body, #374151); font-size: 13px; font-variant-numeric: tabular-nums; }
.finance-overview.is-expanded { position: fixed; inset: 0; z-index: 2100; overflow: auto; background: var(--bg-body, #f5f6f8); padding: 28px max(28px, calc((100vw - 1760px) / 2)); }
.finance-overview :is(button, input, summary):focus-visible { outline: 3px solid #4f46e5; outline-offset: 3px; }
.finance-overview svg { width: 18px; height: 18px; flex-shrink: 0; }
.finance-overview button { font: inherit; cursor: pointer; }
.finance-overview button:disabled { cursor: not-allowed; }
.finance-overview button:disabled:not(.priority-row):not(.debtor-row):not(.fee-row) { opacity: .6; }
.overview-header { display: flex; justify-content: space-between; gap: 20px; align-items: center; }
h1 { margin: 0; font-size: 28px; line-height: 1.3; color: var(--text-title, #111827); letter-spacing: -.025em; }
.overview-header p { margin: 6px 0 0; color: var(--text-secondary, #6b7280); }
.overview-controls { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.month-control { display: flex; align-items: center; gap: 10px; padding: 5px 12px; min-height: 38px; border: 1px solid var(--border-strong); border-radius: 8px; background: white; }
.month-control input { border: 0; background: transparent; color: var(--text-title); font: inherit; max-width: 140px; min-height: 26px; }
.plain-button, .primary-button { display: inline-flex; justify-content: center; align-items: center; gap: 7px; min-height: 38px; padding: 8px 12px; border-radius: 8px; border: 1px solid var(--border-strong); background: white; color: var(--text-body); white-space: nowrap; }
.plain-button:hover:not(:disabled) { border-color: var(--brand); color: var(--brand); background: #f6f5ff; }
.primary-button { background: var(--brand, #4f46e5); border-color: var(--brand, #4f46e5); color: white; }
.primary-button:hover:not(:disabled) { background: var(--brand-hover, #4338ca); }
.icon-button { padding: 9px; }
.scope-line { display: flex; flex-wrap: wrap; gap: 10px 16px; align-items: center; margin: 14px 0 18px; color: var(--text-secondary); font-size: 12px; }
.scope-line > span { display: inline-flex; align-items: center; gap: 5px; }
.scope-line svg { width: 14px; height: 14px; }
.scope-date { margin-left: auto; }
.surface { background: var(--bg-card, white); border: 1px solid var(--border, #ebedf0); border-radius: 12px; min-width: 0; }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
h2 { color: var(--text-title); font-size: 16px; line-height: 1.5; margin: 0; font-weight: 650; }
.section-head p { color: var(--text-secondary); font-size: 12px; margin: 4px 0 0; line-height: 1.5; }
.quiet-label, .chart-unit { color: var(--text-secondary); font-size: 11px; line-height: 24px; white-space: nowrap; }
.summary-grid { display: grid; grid-template-columns: minmax(0, 1.8fr) minmax(340px, 1fr); gap: 16px; margin-bottom: 16px; }
.refreshing { opacity: .6; transition: opacity .15s ease; }
.collection-panel { padding: 20px 22px 0; overflow: hidden; }
.period-tag { color: var(--brand); background: #f0effe; border-radius: 5px; font-size: 12px; padding: 4px 8px; white-space: nowrap; }
.collection-amounts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; margin: 24px 0 20px; }
.amount-label { display: flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-secondary); }
.amount-label i { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.amount { display: block; margin: 8px 0 6px; font-size: clamp(20px, 1.75vw, 31px); letter-spacing: -.035em; line-height: 1.25; color: var(--text-title); overflow-wrap: anywhere; }
.amount small { font-weight: 400; font-size: 11px; margin-left: 4px; letter-spacing: 0; }
.amount-help { color: var(--text-secondary); font-size: 11px; }
.received :is(.amount, .amount-label) { color: var(--finance-green); }
.outstanding :is(.amount, .amount-label) { color: var(--finance-amber); }
.collection-progress { display: flex; align-items: center; gap: 14px; margin-bottom: 22px; }
.collection-progress strong { font-size: 12px; color: var(--finance-green); white-space: nowrap; }
.collection-track { flex: 1; height: 8px; border-radius: 4px; background: #f1e7d7; overflow: hidden; }
.collection-track > span { display: block; height: 100%; background: var(--finance-green); border-radius: inherit; transform-origin: left; transition: transform .35s ease-out; }
.cash-line { margin: 0 -22px; padding: 12px 22px; border-top: 1px solid var(--border); background: #fafbfc; display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 11px; color: var(--text-secondary); }
.cash-line > span:first-child { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--text-body); }
.cash-line strong { color: var(--text-title); font-size: 14px; }
.priorities { padding: 18px 18px 0; }
.priorities .section-head { margin-bottom: 6px; }
.priority-row { display: flex; gap: 10px; align-items: center; width: 100%; text-align: left; color: var(--text-body); padding: 11px 0; border: none; border-bottom: 1px solid var(--border); background: transparent; }
.priority-row:hover:not(:disabled) { background: #faf9ff; }
.priority-icon { display: grid; place-items: center; width: 30px; height: 32px; flex-shrink: 0; }
.priority-icon.urgent { color: #ac4b3f; }.priority-icon.due { color: var(--finance-amber); }.priority-icon.deposit { color: var(--brand); }
.priority-copy { display: grid; gap: 3px; flex: 1; min-width: 0; }.priority-copy strong { font-size: 12px; font-weight: 600; }.priority-copy small { color: var(--text-secondary); font-size: 10px; }
.priority-money { font-size: 15px; font-weight: 600; text-align: right; overflow-wrap: anywhere; max-width: 45%; }.priority-money small { font-size: 10px; margin-left: 3px; font-weight: 400; }
.priority-row > svg { width: 13px; color: var(--text-muted); }
.urgent-text { color: #a94337; }
.priority-foot { margin: 11px 0; line-height: 1.5; font-size: 11px; color: var(--text-secondary); }.priority-foot strong { font-weight: 500; color: var(--text-body); margin-left: 4px; }
.workflow-panel { padding: 20px 22px 0; margin-bottom: 16px; overflow: hidden; }
.workflow-steps { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 18px 0; }
.workflow-step { position: relative; display: flex; align-items: center; gap: 10px; text-align: left; padding: 10px 8px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--text-body); }
.workflow-step:hover { background: #f8f7ff; }.workflow-step.selected { background: #f0effe; border-color: #d7d4fb; }
.step-number { width: 27px; height: 27px; border-radius: 50%; display: grid; place-items: center; border: 1px solid #d5d8e0; background: white; font-size: 12px; flex-shrink: 0; color: var(--text-secondary); }
.selected .step-number { background: var(--brand); color: white; border-color: var(--brand); }.selected .step-copy strong { color: var(--brand-active); }
.step-copy { display: grid; gap: 5px; }.step-copy strong { font-size: 14px; font-weight: 600; }.step-copy small { font-size: 11px; color: var(--text-secondary); line-height: 1.45; }
.step-arrow { position: absolute; right: -16px; width: 13px !important; color: #999daa; }
.step-detail { display: flex; align-items: center; gap: 14px; padding: 16px 22px; margin: 0 -22px; background: #f9f9fd; border-top: 1px solid var(--border); min-height: 94px; }
.detail-icon { color: var(--brand); }.detail-icon svg { width: 24px; height: 24px; }.detail-copy { flex: 1; min-width: 0; }
.detail-copy h3 { margin: 0 0 5px; font-size: 13px; color: var(--text-title); }.detail-copy p { margin: 0; font-size: 12px; line-height: 1.65; color: var(--text-secondary); max-width: 78ch; }
.step-detail .primary-button { font-size: 12px; }
.analysis-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(0, .85fr) minmax(0, 1.07fr); gap: 16px; }
.analysis-grid > .surface { padding: 18px 20px; }
.chart-legend { display: flex; gap: 16px; margin-top: 12px; color: var(--text-secondary); font-size: 11px; }.chart-legend span { display: flex; gap: 5px; align-items: center; }.chart-legend i { width: 7px; height: 7px; border-radius: 2px; }.due-dot { background: #aaa4ee; }.paid-dot { background: var(--finance-green); }
.bar-chart { position: relative; height: 160px; margin: 14px 0 6px; padding-left: 36px; }
.chart-grid { position: absolute; inset: 0 0 24px 0; display: flex; flex-direction: column; justify-content: space-between; color: var(--text-secondary); font-size: 10px; }.chart-grid span { position: relative; line-height: 1; }.chart-grid span::after { content: ''; position: absolute; height: 1px; left: 36px; right: 0; top: 5px; background: var(--border); }
.chart-columns { position: relative; display: flex; justify-content: space-around; gap: 8px; height: 100%; }
.chart-column { display: flex; flex: 1; min-width: 0; flex-direction: column; background: transparent; border: none; padding: 0 2px; gap: 9px; color: var(--text-secondary); }.chart-column:hover .bar { filter: brightness(.88); }.chart-column.active .month-label { color: var(--brand); font-weight: 700; }
.bar-pair { display: flex; gap: 4px; align-items: flex-end; height: 136px; width: 100%; justify-content: center; }.bar { width: 13px; max-width: 45%; display: block; border-radius: 3px 3px 0 0; min-height: 0; }.receivable-bar { background: #aaa4ee; }.received-bar { background: var(--finance-green); }.month-label { font-size: 11px; white-space: nowrap; }
.chart-details { color: var(--text-secondary); font-size: 11px; margin-top: 10px; }.chart-details summary { cursor: pointer; padding: 3px 0; }.chart-details table { width: 100%; border-collapse: collapse; margin-top: 8px; }.chart-details :is(th, td) { padding: 7px 3px; border-bottom: 1px solid var(--border); text-align: right; font-weight: 400; }.chart-details th:first-child { text-align: left; }
.fee-list { margin-top: 12px; }.fee-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: space-between; border: 0; background: transparent; text-align: left; width: 100%; padding: 10px 0; color: var(--text-body); }.fee-row:hover:not(:disabled) { color: var(--brand); }.fee-label { display: flex; gap: 6px; align-items: center; font-size: 12px; }.fee-label i { width: 6px; height: 6px; border-radius: 50%; }.fee-label small { color: var(--text-secondary); font-size: 10px; }.fee-row strong { font-size: 12px; font-weight: 600; }.fee-row strong small { font-size: 10px; font-weight: 400; }.fee-track { height: 4px; background: #f0f1f5; border-radius: 3px; width: 100%; overflow: hidden; }.fee-track > span { display: block; height: 100%; border-radius: inherit; }
.panel-note { color: var(--text-secondary); font-size: 10px; line-height: 1.6; margin: 16px 0 0; }
.debtor-list { margin-top: 9px; }.debtor-row { display: flex; width: 100%; align-items: center; text-align: left; gap: 9px; padding: 10px 0; border: 0; border-bottom: 1px solid var(--border); background: transparent; color: var(--text-body); }.debtor-row:hover:not(:disabled) { background: #faf9ff; }.debtor-index { color: var(--text-muted); font-size: 11px; width: 12px; }.debtor-copy { display: grid; flex: 1; min-width: 0; gap: 4px; }.debtor-copy strong { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 500; }.debtor-copy small { font-size: 10px; color: var(--text-secondary); }.debtor-amount { display: grid; gap: 3px; text-align: right; font-weight: 600; font-size: 12px; }.debtor-amount small { color: var(--text-secondary); font-weight: 400; font-size: 10px; }.debtor-row > svg { color: var(--text-muted); width: 12px; }
.text-button { border: 0; padding: 10px 0 0; color: var(--brand); background: transparent; display: flex; align-items: center; gap: 5px; font-size: 11px !important; }.text-button svg { width: 13px; }
.empty-chart { min-height: 175px; display: flex; align-items: center; flex-direction: column; justify-content: center; text-align: center; gap: 10px; color: var(--text-secondary); }.empty-chart svg { width: 26px; height: 26px; color: #7f839e; }.empty-chart p { margin: 0; font-size: 12px; }.empty-chart small { line-height: 1.6; font-size: 11px; max-width: 30ch; }
.related-work { display: flex; align-items: center; gap: 24px; padding: 22px 0 16px; }.related-work > div:first-child { flex-shrink: 0; }.related-work h2 { font-size: 14px; }.related-work p { margin: 3px 0 0; color: var(--text-secondary); font-size: 11px; }.related-links { display: flex; flex-wrap: wrap; gap: 8px; }.related-links button { font-size: 12px; }.related-links button > svg:last-child { width: 12px; color: var(--text-secondary); }
.definitions { border-top: 1px solid var(--border-strong); padding-top: 12px; color: var(--text-secondary); font-size: 12px; }.definitions > summary { cursor: pointer; list-style: none; display: flex; align-items: center; gap: 6px; width: fit-content; }.definitions summary svg { width: 14px; height: 14px; }.definition-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 22px; }.definition-grid p { line-height: 1.7; font-size: 11px; }.definition-grid strong { display: block; color: var(--text-body); }
.state-panel { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 15px; min-height: 220px; margin-bottom: 16px; padding: 24px; background: white; border: 1px solid var(--border); border-radius: 12px; color: var(--text-secondary); }.error-panel h2 { font-size: 16px; }.error-panel p { font-size: 12px; }.error-panel > svg { color: #a94337; width: 28px; height: 28px; }.spinning { animation: finance-spin 1s linear infinite; }@keyframes finance-spin { to { transform: rotate(360deg); } }
@media (min-width: 1700px) { .finance-overview { padding: 28px 32px; }.collection-amounts { margin-top: 28px; }.amount { font-size: 32px; }.workflow-steps { margin: 24px 0; }.bar-chart { height: 195px; }.bar-pair { height: 171px; }.analysis-grid > .surface { padding: 22px 24px; } }
@media (max-width: 1200px) { .finance-overview { padding: 20px; }.summary-grid { grid-template-columns: minmax(0, 1.5fr) minmax(315px, 1fr); }.amount { font-size: 22px; }.collection-panel { padding: 18px 16px 0; }.cash-line { margin: 0 -16px; padding: 10px 16px; }.collection-amounts { gap: 10px; }.analysis-grid { grid-template-columns: 1fr 1fr; }.debtors-panel { grid-column: 1 / -1; }.debtor-list { display: grid; grid-template-columns: 1fr 1fr; gap: 0 24px; }.workflow-hint { display: none; }.step-copy small { font-size: 10px; }.step-number { width: 23px; height: 23px; }.workflow-step { gap: 7px; } }
@media (max-width: 900px) { .summary-grid { grid-template-columns: 1fr; }.amount { font-size: 28px; }.scope-date { margin-left: 0; }.overview-header { align-items: flex-start; }.overview-controls { justify-content: flex-end; }.expand-button span { display: none; }.workflow-panel { padding: 18px 16px 0; }.step-detail { margin: 0 -16px; padding: 14px 16px; }.step-detail .primary-button { padding: 8px; }.workflow-steps { gap: 12px; }.step-copy strong { font-size: 12px; }.step-arrow { right: -13px; }.related-work { align-items: flex-start; gap: 18px; }.definition-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 600px) { .finance-overview, .finance-overview.is-expanded { padding: 16px 12px; }.overview-header { flex-direction: column; gap: 14px; }h1 { font-size: 25px; }.overview-controls { justify-content: flex-start; width: 100%; }.month-control { flex: 1; justify-content: space-between; }.scope-line { font-size: 10px; gap: 7px; margin: 12px 0 14px; }.scope-line > span:nth-child(2) { width: 100%; }.summary-grid { gap: 12px; }.section-head h2 { font-size: 15px; }.collection-amounts { grid-template-columns: 1fr 1fr; gap: 18px 12px; margin-top: 20px; }.collection-amounts > div:first-child { grid-column: 1 / -1; padding-bottom: 12px; border-bottom: 1px solid var(--border); }.collection-amounts > div:first-child .amount { font-size: 30px; }.amount { font-size: 23px; }.cash-line { gap: 8px; line-height: 1.6; }.priority-copy small { font-size: 11px; }.priority-money { font-size: 14px; }.priority-foot { font-size: 11px; }.workflow-panel { margin-bottom: 12px; }.workflow-steps { grid-template-columns: 1fr 1fr; gap: 8px; margin: 16px 0; }.workflow-step { padding: 10px 8px; }.step-copy strong { font-size: 13px; }.step-copy small { font-size: 11px; }.step-arrow { display: none; }.step-detail { flex-wrap: wrap; gap: 10px; }.detail-icon { display: none; }.detail-copy { flex-basis: 100%; }.step-detail .primary-button { width: 100%; }.analysis-grid { grid-template-columns: 1fr; gap: 12px; }.analysis-grid > .surface { padding: 18px; }.debtor-list { grid-template-columns: 1fr; }.debtor-copy strong, .debtor-amount { font-size: 13px; }.debtor-copy small { font-size: 11px; }.related-work { flex-direction: column; gap: 12px; }.definition-grid { grid-template-columns: 1fr; gap: 0; }.state-panel { min-height: 230px; text-align: center; }.error-panel .primary-button { width: 100%; } }
@media (prefers-reduced-motion: reduce) { .finance-overview *, .finance-overview *::before, .finance-overview *::after { animation: none !important; transition: none !important; } }
</style>
