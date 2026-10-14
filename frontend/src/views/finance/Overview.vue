<template>
  <div class="finance-overview-host">
  <Teleport to="body" :disabled="!expanded">
  <div ref="board" class="commerce-page finance-overview" :class="{ 'is-expanded': expanded }" :aria-busy="loading">
    <header class="commerce-page__header overview-header">
      <div>
        <div class="commerce-page__eyebrow">Finance overview</div>
        <h1 class="commerce-page__title">财务总览</h1>
        <p class="commerce-page__subtitle">看懂每一笔应收，找到下一步工作。</p>
      </div>
      <div class="commerce-page__actions overview-controls">
        <label class="month-control"><Calendar /><span>账期</span><input v-model="month" type="month" min="2000-01" max="2100-12" aria-label="选择账期月份" /></label>
        <button class="plain-button icon-button" type="button" :disabled="loading" aria-label="刷新财务数据" @click="load"><Refresh :class="{ spinning: loading }" /></button>
        <button class="plain-button expand-button" type="button" :aria-label="expanded ? '退出大屏' : '展开大屏'" :aria-pressed="expanded" @click="expanded = !expanded"><FullScreen /><span>{{ expanded ? '退出大屏' : '展开大屏' }}</span></button>
      </div>
    </header>

    <div class="scope-line">
      <span class="scope-chip"><OfficeBuilding />{{ data?.scopeLabel || '全园区 · 全部项目' }}</span>
      <span class="scope-chip"><InfoFilled />财务账单口径 · 不随顶部项目切换</span>
      <span v-if="data" class="scope-date">更新于 {{ updatedAt }} · 累计待办截至 {{ data.asOfDate }}</span>
    </div>

    <div v-if="error" class="state-panel error-panel" role="alert">
      <span class="state-icon"><Warning /></span><div><h2>财务数据暂时没有加载成功</h2><p>请检查网络后重试。业务流程说明仍可查看。</p></div>
      <button type="button" class="primary-button" @click="load">重新加载</button>
    </div>
    <div v-else-if="!data" class="state-panel loading-panel" role="status"><Loading class="spinning" />正在汇总账单与收款数据…</div>

    <div v-if="data && !error" class="summary-grid" :class="{ refreshing: loading }">
      <section class="commerce-card surface collection-panel" aria-labelledby="collection-title">
        <div class="section-head"><div><h2 id="collection-title">{{ periodLabel }}账期，收到了哪一步</h2><p>看这个账期应收费用的累计收缴进度</p></div><span class="period-tag">{{ data.month }}</span></div>
        <div class="collection-body">
          <div class="collection-ring" :class="{ 'is-empty': !hasReceivable }" role="progressbar" aria-label="所选账期收缴率" :aria-valuenow="rate" aria-valuemin="0" aria-valuemax="100">
            <svg class="ring-chart" viewBox="0 0 120 120" aria-hidden="true">
              <circle class="ring-track" cx="60" cy="60" :r="ringRadius" />
              <circle v-if="ringFilled > 0" class="ring-value" cx="60" cy="60" :r="ringRadius" :stroke-dasharray="`${ringFilled} ${ringCircumference}`" />
            </svg>
            <span class="ring-copy">
              <template v-if="hasReceivable"><strong>{{ rate }}<small>%</small></strong><span>已收回</span></template>
              <template v-else><strong class="ring-empty">—</strong><span>本账期暂无应收</span></template>
            </span>
          </div>
          <div class="collection-amounts">
            <div class="amount-total"><span class="amount-label">应收总额</span><strong class="amount">{{ money(data.periodReceivable) }}<small>元</small></strong><span class="amount-help">本金 + 滞纳金</span></div>
            <div class="amount-tile received"><span class="amount-label"><i></i>已经收回</span><strong class="amount">{{ money(data.periodReceived) }}<small>元</small></strong><span class="amount-help">这些账单累计收到的钱</span></div>
            <div class="amount-tile outstanding"><span class="amount-label"><i></i>还待收回</span><strong class="amount">{{ money(data.periodOutstanding) }}<small>元</small></strong><span class="amount-help">这些账单尚未结清的金额</span></div>
          </div>
        </div>
        <div class="cash-line"><span><span class="cash-icon"><Wallet /></span>{{ periodLabel }}实际净收款 <strong>{{ money(data.cashReceived) }} 元</strong></span><span>按到账日统计，含跨期补收与红冲</span></div>
      </section>

      <section class="commerce-card surface priorities" aria-labelledby="priorities-title">
        <div class="section-head"><div><h2 id="priorities-title">接下来处理什么</h2><p>按紧急程度排序，点击进入对应账单</p></div><span class="quiet-label">全部账期累计</span></div>
        <div class="priority-list">
          <button class="priority-row is-urgent" type="button" :disabled="!canGo('/finance/overdue')" @click="go('/finance/overdue')">
            <span class="priority-icon urgent"><AlarmClock /></span><span class="priority-copy"><strong>优先跟进逾期</strong><small>{{ data.overdueBillCount }} 张账单已逾期<span v-if="!canGo('/finance/overdue')"> · 需开通逾期账单权限</span></small></span><span class="priority-money urgent-text">{{ money(data.overdueOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/overdue')" />
          </button>
          <button class="priority-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { onlyDue: 'true', onlyOutstanding: 'true', direction: '1' })">
            <span class="priority-icon due"><Money /></span><span class="priority-copy"><strong>核对到期未收</strong><small>{{ canGo('/finance/bill') ? '含逾期，确认到账后登记收款' : '查看明细需开通账单权限' }}</small></span><span class="priority-money">{{ money(data.dueOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/bill')" />
          </button>
          <button class="priority-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { feeType: '保证金', onlyOutstanding: 'true', direction: '1' })">
            <span class="priority-icon deposit"><Lock /></span><span class="priority-copy"><strong>核对保证金</strong><small>{{ canGo('/finance/bill') ? '尚未收齐的保证金' : '查看明细需开通账单权限' }}</small></span><span class="priority-money">{{ money(data.depositOutstanding) }}<small>元</small></span><ArrowRight v-if="canGo('/finance/bill')" />
          </button>
        </div>
        <p class="priority-foot"><Clock />未到应收日 <strong>{{ money(data.upcomingOutstanding) }} 元</strong><span v-if="Number(data.undatedOutstanding) > 0"> · 未设应收日 {{ money(data.undatedOutstanding) }} 元</span></p>
      </section>
    </div>

    <section class="commerce-card surface workflow-panel" aria-labelledby="workflow-title">
      <div class="section-head"><div><h2 id="workflow-title">一笔费用，走完这四步</h2><p>新同事从这里开始。点击环节，了解该做什么。</p></div><span class="quiet-label workflow-hint">收错款可红冲，零元账单走核销</span></div>
      <div class="workflow-steps" aria-label="财务业务流程">
        <button v-for="(step, index) in steps" :key="step.key" class="workflow-step" :class="{ selected: selectedStep === index, done: index < selectedStep }" type="button" :aria-pressed="selectedStep === index" @click="selectedStep = index">
          <span class="step-number">{{ index + 1 }}</span><span class="step-copy"><strong>{{ step.title }}</strong><small>{{ step.subtitle }}</small></span>
        </button>
      </div>
      <div class="step-detail" aria-live="polite">
        <div class="detail-icon"><component :is="activeStep.icon" /></div><div class="detail-copy"><h3>{{ activeStep.question }}</h3><p>{{ activeStep.description }}</p></div>
        <button class="primary-button" type="button" :disabled="!canGo(activeStep.path)" @click="go(activeStep.path)">{{ canGo(activeStep.path) ? activeStep.action : '需开通此功能权限' }}<ArrowRight /></button>
      </div>
    </section>

    <div v-if="data && !error" class="analysis-grid">
      <section class="commerce-card surface trend-panel" aria-labelledby="trend-title">
        <div class="section-head"><div><h2 id="trend-title">近六个月收缴</h2><p>按账期比较应收与累计实收，点击柱子切换账期</p></div><span class="chart-unit">单位：万元</span></div>
        <div class="chart-legend"><span><i class="due-dot"></i>应收</span><span><i class="paid-dot"></i>已收</span></div>
        <div v-if="trendMax > 0" class="bar-chart" role="group" aria-label="近六个月应收实收柱状图，点击月份查看">
          <div class="chart-grid" aria-hidden="true"><span>{{ compactAmount(trendMax) }}</span><span>{{ compactAmount(trendMax / 2) }}</span><span>0</span></div>
          <div class="chart-columns">
            <button v-for="point in data.trend" :key="point.month" class="chart-column" :class="{ active: point.month === month }" type="button" :aria-label="`${point.month}，应收${money(point.receivable)}元，已收${money(point.received)}元；查看该月`" @click="month = point.month">
              <span class="bar-pair"><span class="bar receivable-bar" :style="{ height: barHeight(point.receivable) }"></span><span class="bar received-bar" :style="{ height: barHeight(point.received) }"></span></span><span class="month-label">{{ Number(point.month.slice(5)) }}月</span>
              <span class="column-tip" aria-hidden="true"><b>{{ point.month }}</b><span><i class="due-dot"></i>应收 {{ compactAmount(point.receivable) }} 万</span><span><i class="paid-dot"></i>已收 {{ compactAmount(point.received) }} 万</span></span>
            </button>
          </div>
        </div>
        <div v-else class="empty-chart"><TrendCharts /><p>这六个月还没有应收账单</p><small>登记并生成账单后，这里会显示收缴变化。</small></div>
        <details class="chart-details"><summary>查看精确金额</summary><table><caption class="sr-only">近六个月账期金额，单位元</caption><thead><tr><th>账期</th><th>应收 / 元</th><th>已收 / 元</th></tr></thead><tbody><tr v-for="point in data.trend" :key="point.month"><th>{{ point.month }}</th><td>{{ money(point.receivable) }}</td><td>{{ money(point.received) }}</td></tr></tbody></table></details>
      </section>

      <section class="commerce-card surface fee-panel" aria-labelledby="fees-title">
        <div class="section-head"><div><h2 id="fees-title">应收来自哪里</h2><p>{{ periodLabel }}账期 · 含保证金与滞纳金</p></div></div>
        <div v-if="positiveFees.length" class="fee-stack" aria-hidden="true"><span v-for="(fee, index) in positiveFees" :key="fee.feeType" :style="{ flexGrow: Math.max(Number(fee.receivable), 0), background: feeColors[index % feeColors.length] }"></span></div>
        <div v-if="positiveFees.length" class="fee-list">
          <button v-for="(fee, index) in positiveFees" :key="fee.feeType" class="fee-row" type="button" :disabled="!canGo('/finance/bill')" @click="go('/finance/bill', { feeType: fee.feeType, direction: '1' })">
            <span class="fee-label"><i :style="{ background: feeColors[index % feeColors.length] }"></i>{{ fee.feeType }}<small>{{ percent(fee.receivable, data.periodReceivable) }}%</small></span><strong>{{ money(fee.receivable) }}<small> 元</small></strong>
            <span class="fee-track"><span :style="{ width: `${percent(fee.receivable, data.periodReceivable)}%`, background: feeColors[index % feeColors.length] }"></span></span>
          </button>
        </div>
        <div v-else class="empty-chart"><Document /><p>本账期暂无费用</p><small>免租期的零元账单不会计入应收结构。</small></div>
        <p class="panel-note">保证金单独核对；此处为应收结构，不代表经营利润。{{ canGo('/finance/bill') ? '点击费用查看该类全部账单。' : '查看费用明细需开通账单权限。' }}</p>
      </section>

      <section class="commerce-card surface debtors-panel" aria-labelledby="debtors-title">
        <div class="section-head"><div><h2 id="debtors-title">待收款租客</h2><p>全部账期待收最多的前 5 户，含未到期</p></div></div>
        <div v-if="data.topDebtors.length" class="debtor-list">
          <button v-for="(tenant, index) in data.topDebtors" :key="tenant.tenantRefId ?? tenant.tenantName" class="debtor-row" type="button" :disabled="!tenant.tenantRefId || !canGo('/finance/bill')" @click="go('/finance/bill', { tenantRefId: tenant.tenantRefId, onlyOutstanding: 'true', direction: '1' })">
            <span class="debtor-index" :class="{ top: index < 3 }">{{ index + 1 }}</span><span class="debtor-copy"><strong :title="tenant.tenantName">{{ tenant.tenantName }}</strong><small>{{ tenant.billCount }} 张未结清<span v-if="Number(tenant.overdueOutstanding) > 0" class="overdue-badge">含逾期</span><span v-if="!tenant.tenantRefId"> · 未关联租客档案</span></small></span><span class="debtor-amount">{{ money(tenant.outstanding) }}<small>元</small></span><ArrowRight v-if="tenant.tenantRefId && canGo('/finance/bill')" />
          </button>
        </div>
        <div v-else class="empty-chart"><CircleCheck /><p>当前账单已无待收款</p><small>新增应收后，待收款租客会出现在这里。</small></div>
        <p v-if="!canGo('/finance/bill') && data.topDebtors.length" class="panel-note">查看租客欠款明细需开通账单权限。</p>
        <button v-if="canGo('/finance/bill')" class="text-button" type="button" @click="go('/finance/bill')">查看全部账单<ArrowRight /></button>
      </section>
    </div>

    <section class="commerce-card related-work" aria-label="财务工作入口">
      <div class="related-copy"><h2>还有这些工作</h2><p>按业务进入各自台账</p></div>
      <div class="related-links"><button v-for="link in visibleRelated" :key="link.path" type="button" @click="go(link.path)"><span class="related-icon"><component :is="link.icon" /></span>{{ link.label }}<ArrowRight class="related-arrow" /></button><span v-if="!visibleRelated.length" class="quiet-label">其他业务入口按账号权限开放</span></div>
    </section>
    <details class="definitions"><summary><InfoFilled />这些数字怎么算？</summary><div class="definition-grid"><p><strong>账期收缴</strong>按账单账期起始月统计。比如本月补收上月租金，会更新上月账期的已收。</p><p><strong>实际净收款</strong>按收款日期统计正向收款与负向红冲，包含保证金。它与账期已收可能不同。</p><p><strong>到期与逾期</strong>到期未收按应收日判断；逾期另外考虑登记表约定的延期起算日。两者均按今天计算。</p><p><strong>面板覆盖范围</strong>汇总财务账单中的应收费用；独立云仓服务费、佣金、提现与云仓结算在各自台账查看。</p></div></details>
  </div>
  </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { AlarmClock, ArrowRight, Calendar, CircleCheck, Clock, Document, Files, FullScreen, InfoFilled, Loading, Lock, Money, OfficeBuilding, Refresh, Tickets, TrendCharts, Wallet, Warning } from '@element-plus/icons-vue'
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
const hasReceivable = computed(() => Number(data.value?.periodReceivable) > 0)
const ringRadius = 52
const ringCircumference = 2 * Math.PI * ringRadius
const ringFilled = computed(() => (hasReceivable.value ? rate.value / 100 : 0) * ringCircumference)
const positiveFees = computed(() => (data.value?.feeStructure || []).filter(fee => Number(fee.receivable) > 0))
const trendMax = computed(() => Math.max(0, ...(data.value?.trend || []).flatMap(point => [Number(point.receivable), Number(point.received)])))
// Fixed categorical order, validated for colour-vision deficiency on a white surface; amber and red stay reserved for outstanding/overdue.
const feeColors = ['#2f4bf0', '#1a9aa0', '#7a5af0', '#e0659a', '#5f8f3e']
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
/* 颜色按含义固定：蓝 = 已收，琥珀 = 待收，红 = 逾期，紫 = 保证金；正文色均 ≥ 4.5:1 */
.finance-overview {
  --fo-paid: #2f4bf0;
  --fo-paid-soft: #eef0ff;
  --fo-due: #dfe3fd;
  --fo-wait: #a35c00;
  --fo-wait-mark: #eaa23c;
  --fo-wait-soft: #fff7eb;
  --fo-wait-track: #f9e4c4;
  --fo-risk: #c2413a;
  --fo-risk-soft: #fff3f2;
  --fo-deposit: #5b4bc4;
  --fo-deposit-soft: #f3f0ff;
  --fo-ink-2: #68708a;
  color: var(--text-body);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}
.finance-overview.is-expanded { position: fixed; inset: 0; z-index: 2100; overflow: auto; background: #f3f5fa; padding: 28px max(28px, calc((100vw - 1760px) / 2)); border-radius: 0; }
.finance-overview :is(button, input, summary):focus-visible { outline: 3px solid var(--fo-paid); outline-offset: 3px; }
.finance-overview svg { width: 18px; height: 18px; flex-shrink: 0; }
.finance-overview button { font: inherit; cursor: pointer; }
.finance-overview button:disabled { cursor: not-allowed; }
.finance-overview button:disabled:not(.priority-row):not(.debtor-row):not(.fee-row) { opacity: .55; }
h2 { color: var(--text-title); font-size: 16px; line-height: 1.45; margin: 0; font-weight: 680; letter-spacing: -.015em; }

/* —— 顶部 —— */
.overview-header { margin-bottom: 14px; }
.month-control { display: inline-flex; align-items: center; gap: 8px; min-height: 40px; padding: 0 6px 0 13px; color: var(--fo-ink-2); font-size: 12px; font-weight: 560; background: #fff; border: 1px solid var(--border); border-radius: 11px; box-shadow: 0 1px 2px rgba(11, 13, 23, .025); }
.month-control svg { width: 15px; height: 15px; color: var(--brand); }
.month-control input { border: 0; background: transparent; color: var(--text-title); font: inherit; font-size: 13px; font-weight: 650; max-width: 150px; min-height: 30px; }
.plain-button, .primary-button { display: inline-flex; justify-content: center; align-items: center; gap: 7px; min-height: 40px; padding: 8px 14px; border-radius: 11px; border: 1px solid var(--border); background: #fff; color: var(--text-body); font-size: 13px; font-weight: 560; white-space: nowrap; transition: background .16s, border-color .16s, color .16s, box-shadow .16s; }
.plain-button { box-shadow: 0 1px 2px rgba(11, 13, 23, .025); }
.plain-button:hover:not(:disabled) { border-color: #c9cffb; color: var(--brand); background: var(--fo-paid-soft); }
.plain-button svg { width: 16px; height: 16px; }
.icon-button { width: 40px; padding: 0; }
.primary-button { background: var(--brand); border-color: var(--brand); color: #fff; box-shadow: 0 7px 16px rgba(10, 36, 233, .16); }
.primary-button:hover:not(:disabled) { background: var(--brand-hover); border-color: var(--brand-hover); }
.primary-button svg { width: 14px; height: 14px; }
.scope-line { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; margin: 0 0 18px; color: var(--fo-ink-2); font-size: 12px; }
.scope-chip { display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px; background: rgba(255, 255, 255, .72); border: 1px solid var(--border); border-radius: 999px; }
.scope-line svg { width: 13px; height: 13px; color: var(--brand); }
.scope-date { margin-left: auto; }

/* —— 卡片通用 —— */
.surface { min-width: 0; }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.section-head p { color: var(--fo-ink-2); font-size: 12px; margin: 4px 0 0; line-height: 1.5; }
.quiet-label, .chart-unit { flex-shrink: 0; padding: 3px 9px; color: var(--fo-ink-2); background: #f4f5f9; border-radius: 999px; font-size: 12px; line-height: 18px; white-space: nowrap; }
.summary-grid { display: grid; grid-template-columns: minmax(0, 1.75fr) minmax(360px, 1fr); gap: 16px; margin-bottom: 16px; }
.refreshing { opacity: .6; transition: opacity .15s ease; }

/* —— 账期收缴 —— */
.collection-panel { display: flex; flex-direction: column; padding: 20px 22px 0; overflow: hidden; }
.period-tag { flex-shrink: 0; color: var(--brand); background: var(--fo-paid-soft); border-radius: 999px; font-size: 12px; font-weight: 650; padding: 4px 10px; white-space: nowrap; }
.collection-body { flex: 1; display: flex; align-items: center; gap: 28px; padding: 22px 0 22px; }
.collection-ring { position: relative; width: 152px; height: 152px; flex-shrink: 0; }
.finance-overview .ring-chart { width: 100%; height: 100%; transform: rotate(-90deg); }
.ring-chart circle { fill: none; stroke-width: 11; }
.ring-track { stroke: var(--fo-wait-track); }
.is-empty .ring-track { stroke: #eceef4; }
.ring-value { stroke: var(--fo-paid); stroke-linecap: round; transition: stroke-dasharray .6s cubic-bezier(.2, .7, .2, 1); }
.ring-copy { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px; text-align: center; }
.ring-copy strong { color: var(--text-title); font-size: 34px; font-weight: 760; letter-spacing: -.045em; line-height: 1; }
.ring-copy strong small { margin-left: 1px; font-size: 16px; font-weight: 650; letter-spacing: 0; }
.ring-copy > span { color: var(--fo-ink-2); font-size: 12px; max-width: 96px; line-height: 1.4; }
.ring-copy .ring-empty { color: var(--fo-ink-2); font-size: 28px; }
.collection-amounts { flex: 1; min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.amount-total { grid-column: 1 / -1; }
.amount-label { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--fo-ink-2); font-weight: 560; }
.amount-label i { display: inline-block; width: 8px; height: 8px; border-radius: 3px; background: currentColor; }
.amount { display: block; margin: 6px 0 4px; color: var(--text-title); font-size: 24px; font-weight: 720; letter-spacing: -.035em; line-height: 1.2; white-space: nowrap; }
.amount small { font-weight: 500; font-size: 12px; margin-left: 4px; letter-spacing: 0; color: var(--fo-ink-2); }
.amount-total .amount { font-size: clamp(28px, 2.5vw, 38px); letter-spacing: -.045em; }
.amount-help { color: var(--fo-ink-2); font-size: 12px; }
.amount-tile { min-width: 0; padding: 12px 14px; border-radius: 13px; border: 1px solid transparent; }
.amount-tile.received { background: var(--fo-paid-soft); border-color: #e0e4fd; }
.amount-tile.outstanding { background: var(--fo-wait-soft); border-color: #f8e8cf; }
.received .amount-label, .received .amount { color: var(--fo-paid); }
.received .amount-label i { background: var(--fo-paid); }
.outstanding .amount-label, .outstanding .amount { color: var(--fo-wait); }
.outstanding .amount-label i { background: var(--fo-wait-mark); }
.cash-line { margin: 0 -22px; padding: 12px 22px; border-top: 1px solid var(--border); background: #f8f9fc; display: flex; justify-content: space-between; align-items: center; gap: 8px 16px; flex-wrap: wrap; font-size: 12px; color: var(--fo-ink-2); }
.cash-line > span:first-child { display: inline-flex; align-items: center; gap: 8px; flex-wrap: wrap; color: var(--text-body); font-size: 13px; }
.cash-icon { display: inline-grid; place-items: center; width: 28px; height: 28px; color: var(--brand); background: #fff; border: 1px solid var(--border); border-radius: 9px; }
.cash-icon svg { width: 15px; height: 15px; }
.cash-line strong { color: var(--text-title); font-size: 16px; font-weight: 720; letter-spacing: -.02em; }

/* —— 待办 —— */
.priorities { display: flex; flex-direction: column; padding: 20px 20px 0; }
.priority-list { display: grid; gap: 8px; margin-top: 16px; }
.priority-row { position: relative; display: flex; gap: 12px; align-items: center; width: 100%; min-height: 64px; text-align: left; color: var(--text-body); padding: 11px 14px 11px 12px; border: 1px solid #eef0f4; border-radius: 13px; background: #f8f9fc; transition: background .16s, border-color .16s, transform .16s; }
.priority-row.is-urgent { background: var(--fo-risk-soft); border-color: #fbe0dd; }
.priority-row:hover:not(:disabled) { background: #fff; border-color: #c9cffb; transform: translateY(-1px); box-shadow: 0 8px 18px rgba(41, 49, 93, .07); }
.priority-row.is-urgent:hover:not(:disabled) { border-color: #f1b5af; }
.priority-icon { display: grid; place-items: center; width: 38px; height: 38px; flex-shrink: 0; border-radius: 11px; background: #fff; }
.priority-icon svg { width: 18px; height: 18px; }
.priority-icon.urgent { color: var(--fo-risk); box-shadow: 0 0 0 1px #fbe0dd; }
.priority-icon.due { color: var(--fo-wait); background: var(--fo-wait-soft); }
.priority-icon.deposit { color: var(--fo-deposit); background: var(--fo-deposit-soft); }
.priority-copy { display: grid; gap: 3px; flex: 1; min-width: 0; }
.priority-copy strong { color: var(--text-title); font-size: 14px; font-weight: 650; }
.priority-copy small { color: var(--fo-ink-2); font-size: 12px; line-height: 1.45; }
.priority-money { color: var(--text-title); font-size: 18px; font-weight: 720; letter-spacing: -.025em; text-align: right; white-space: nowrap; }
.priority-money small { font-size: 12px; margin-left: 3px; font-weight: 500; letter-spacing: 0; color: var(--fo-ink-2); }
.priority-row > svg { width: 14px; height: 14px; color: var(--fo-ink-2); }
.urgent-text, .priority-money.urgent-text { color: var(--fo-risk); }
.priority-foot { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; margin: auto 0 0; padding: 14px 0; line-height: 1.5; font-size: 12px; color: var(--fo-ink-2); }
.priority-foot svg { width: 14px; height: 14px; margin-right: 2px; }
.priority-foot strong { font-weight: 650; color: var(--text-body); margin-left: 2px; }

/* —— 四步流程 —— */
.workflow-panel { padding: 20px 22px 0; margin-bottom: 16px; overflow: hidden; }
.workflow-steps { position: relative; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 18px 0; }
.workflow-step { position: relative; display: flex; align-items: center; gap: 12px; min-width: 0; text-align: left; padding: 12px 14px; border: 1px solid #eef0f4; border-radius: 13px; background: #f8f9fc; color: var(--text-body); transition: background .16s, border-color .16s; }
.workflow-step:not(:last-child)::after { content: ''; position: absolute; top: 50%; right: -13px; width: 14px; height: 2px; background: #d9ddf0; border-radius: 2px; }
.workflow-step:hover { background: #fff; border-color: #c9cffb; }
.workflow-step.selected { background: var(--fo-paid-soft); border-color: #bfc7fb; box-shadow: inset 0 0 0 1px #bfc7fb; }
.workflow-step.done .step-number { color: var(--brand); border-color: #bfc7fb; background: #fff; }
.step-number { width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; border: 1px solid #d5d9e4; background: #fff; font-size: 13px; font-weight: 700; flex-shrink: 0; color: var(--fo-ink-2); }
.selected .step-number { background: var(--brand); color: #fff; border-color: var(--brand); box-shadow: 0 0 0 4px rgba(10, 36, 233, .12); }
.selected .step-copy strong { color: var(--brand-active); }
.step-copy { display: grid; gap: 3px; min-width: 0; }
.step-copy strong { color: var(--text-title); font-size: 14px; font-weight: 650; }
.step-copy small { font-size: 12px; color: var(--fo-ink-2); line-height: 1.45; }
.step-detail { display: flex; align-items: center; gap: 16px; padding: 16px 22px; margin: 0 -22px; background: linear-gradient(90deg, #f5f7ff, #fafbff); border-top: 1px solid #e6e9fb; min-height: 92px; }
.detail-icon { display: grid; place-items: center; width: 44px; height: 44px; flex-shrink: 0; color: var(--brand); background: #fff; border: 1px solid #dfe3fd; border-radius: 13px; }
.detail-icon svg { width: 22px; height: 22px; }
.detail-copy { flex: 1; min-width: 0; }
.detail-copy h3 { margin: 0 0 4px; font-size: 14px; color: var(--text-title); font-weight: 680; }
.detail-copy p { margin: 0; font-size: 13px; line-height: 1.65; color: var(--fo-ink-2); max-width: 80ch; }

/* —— 分析区 —— */
.analysis-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr) minmax(0, 1.05fr); gap: 16px; }
.analysis-grid > .surface { display: flex; flex-direction: column; padding: 20px 20px 18px; }
.chart-legend { display: flex; gap: 16px; margin-top: 14px; color: var(--fo-ink-2); font-size: 12px; }
.chart-legend span, .column-tip span { display: flex; gap: 6px; align-items: center; }
.chart-legend i, .column-tip i { width: 9px; height: 9px; border-radius: 3px; }
.due-dot { background: #c3cbfa; }
.paid-dot { background: var(--fo-paid); }
.bar-chart { position: relative; flex: 1; min-height: 184px; margin: 14px 0 4px; padding-left: 50px; }
.chart-grid { position: absolute; inset: 0 0 26px 0; display: flex; flex-direction: column; justify-content: space-between; color: var(--fo-ink-2); font-size: 11px; }
.chart-grid span { position: relative; line-height: 1; height: 0; }
.chart-grid span::after { content: ''; position: absolute; height: 0; left: 50px; right: 0; top: 0; border-top: 1px dashed #e3e6ee; }
.chart-grid span:last-child::after { border-top-style: solid; border-color: #d9dde6; }
.chart-columns { position: relative; display: flex; justify-content: space-around; gap: 6px; height: 100%; }
.chart-column { position: relative; display: flex; flex: 1; min-width: 0; flex-direction: column; align-items: center; background: transparent; border: none; border-radius: 10px; padding: 0 2px; gap: 8px; color: var(--fo-ink-2); }
.chart-column::before { content: ''; position: absolute; inset: -6px 0 22px; border-radius: 10px; background: transparent; transition: background .16s; }
.chart-column:hover::before, .chart-column:focus-visible::before { background: #f3f5fc; }
.chart-column.active::before { background: var(--fo-paid-soft); }
.bar-pair { position: relative; display: flex; flex: 1; min-height: 0; gap: 3px; align-items: flex-end; width: 100%; justify-content: center; }
.bar { width: 14px; max-width: 42%; display: block; border-radius: 4px 4px 0 0; min-height: 0; transition: height .45s cubic-bezier(.2, .7, .2, 1); }
.receivable-bar { background: #c3cbfa; }
.received-bar { background: var(--fo-paid); }
.month-label { position: relative; font-size: 12px; line-height: 18px; white-space: nowrap; }
.chart-column.active .month-label { color: var(--brand); font-weight: 720; }
.column-tip { position: absolute; z-index: 2; bottom: calc(100% - 18px); left: 50%; display: grid; gap: 4px; padding: 9px 11px; color: #fff; background: #0b0d17; border-radius: 10px; font-size: 12px; line-height: 1.3; white-space: nowrap; text-align: left; box-shadow: 0 12px 28px rgba(11, 13, 23, .22); opacity: 0; pointer-events: none; transform: translate(-50%, 4px); transition: opacity .14s, transform .14s; }
.column-tip b { font-weight: 650; color: #c3cbfa; }
.column-tip .due-dot { background: #c3cbfa; }
.column-tip .paid-dot { background: #7d8ff7; }
.chart-column:hover .column-tip, .chart-column:focus-visible .column-tip { opacity: 1; transform: translate(-50%, 0); }
.chart-details { color: var(--fo-ink-2); font-size: 12px; margin-top: auto; padding-top: 10px; }
.chart-details summary { cursor: pointer; padding: 3px 0; width: fit-content; }
.chart-details summary:hover { color: var(--brand); }
.chart-details table { width: 100%; border-collapse: collapse; margin-top: 8px; }
.chart-details :is(th, td) { padding: 7px 4px; border-bottom: 1px solid var(--border); text-align: right; font-weight: 400; }
.chart-details th:first-child { text-align: left; }
.chart-details thead th { color: var(--fo-ink-2); background: #f8f9fc; }
.fee-stack { display: flex; gap: 2px; height: 10px; margin: 18px 0 6px; border-radius: 999px; overflow: hidden; }
.fee-stack > span { min-width: 3px; }
.fee-list { display: grid; margin-top: 6px; }
.fee-row { display: flex; flex-wrap: wrap; gap: 8px; justify-content: space-between; align-items: center; border: 0; border-radius: 10px; background: transparent; text-align: left; padding: 10px 8px; margin: 0 -8px; width: calc(100% + 16px); color: var(--text-body); transition: background .16s; }
.fee-row:hover:not(:disabled) { background: #f6f7fc; }
.fee-row:hover:not(:disabled) .fee-label { color: var(--brand); }
.fee-label { display: flex; gap: 8px; align-items: center; color: var(--text-title); font-size: 13px; font-weight: 600; }
.fee-label i { width: 9px; height: 9px; border-radius: 3px; }
.fee-label small { padding: 1px 7px; color: var(--fo-ink-2); background: #f2f3f7; border-radius: 999px; font-size: 12px; font-weight: 500; }
.fee-row strong { color: var(--text-title); font-size: 14px; font-weight: 700; letter-spacing: -.01em; }
.fee-row strong small { color: var(--fo-ink-2); font-size: 12px; font-weight: 500; }
.fee-track { height: 6px; background: #f0f2f7; border-radius: 999px; width: 100%; overflow: hidden; }
.fee-track > span { display: block; height: 100%; border-radius: inherit; transition: width .45s cubic-bezier(.2, .7, .2, 1); }
.panel-note { color: var(--fo-ink-2); font-size: 12px; line-height: 1.65; margin: auto 0 0; padding-top: 14px; }
.debtor-list { display: grid; gap: 6px; margin-top: 14px; }
.debtor-row { display: flex; width: 100%; align-items: center; text-align: left; gap: 11px; padding: 8px 12px 8px 10px; border: 1px solid #eef0f4; border-radius: 12px; background: #f8f9fc; color: var(--text-body); transition: background .16s, border-color .16s; }
.debtor-row:hover:not(:disabled) { background: #fff; border-color: #c9cffb; }
.debtor-index { display: grid; place-items: center; width: 26px; height: 26px; flex-shrink: 0; color: var(--fo-ink-2); background: #fff; border: 1px solid var(--border); border-radius: 8px; font-size: 12px; font-weight: 700; }
.debtor-index.top { color: var(--brand); background: var(--fo-paid-soft); border-color: #dfe3fd; }
.debtor-copy { display: grid; flex: 1; min-width: 0; gap: 2px; }
.debtor-copy strong { color: var(--text-title); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 620; }
.debtor-copy small { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; font-size: 12px; color: var(--fo-ink-2); }
.overdue-badge { padding: 0 7px; color: var(--fo-risk); background: var(--fo-risk-soft); border: 1px solid #fbe0dd; border-radius: 999px; font-size: 11px; line-height: 18px; font-weight: 600; }
.debtor-amount { color: var(--text-title); text-align: right; font-weight: 720; font-size: 14px; white-space: nowrap; letter-spacing: -.01em; }
.debtor-amount small { margin-left: 3px; color: var(--fo-ink-2); font-weight: 500; font-size: 12px; }
.debtor-row > svg { color: var(--fo-ink-2); width: 13px; height: 13px; }
.text-button { align-self: flex-start; border: 0; margin-top: auto; padding: 14px 0 0; color: var(--brand); background: transparent; display: flex; align-items: center; gap: 5px; font-size: 13px !important; font-weight: 600; }
.text-button:hover { color: var(--brand-hover); }
.text-button svg { width: 13px; height: 13px; }
.empty-chart { flex: 1; min-height: 180px; display: flex; align-items: center; flex-direction: column; justify-content: center; text-align: center; gap: 10px; color: var(--fo-ink-2); }
.empty-chart svg { width: 40px; height: 40px; padding: 10px; color: var(--brand); background: var(--fo-paid-soft); border-radius: 12px; }
.empty-chart p { margin: 0; color: var(--text-title); font-size: 13px; font-weight: 600; }
.empty-chart small { line-height: 1.6; font-size: 12px; max-width: 30ch; }

/* —— 其他入口与口径 —— */
.related-work { display: flex; align-items: center; gap: 24px; margin-top: 16px; padding: 16px 20px; }
.related-copy { flex-shrink: 0; }
.related-work h2 { font-size: 15px; }
.related-work p { margin: 3px 0 0; color: var(--fo-ink-2); font-size: 12px; }
.related-links { flex: 1; min-width: 0; display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 10px; }
.related-links button { display: flex; align-items: center; gap: 8px; min-width: 0; overflow: hidden; padding: 9px 10px; text-align: left; white-space: nowrap; color: var(--text-body); background: #f8f9fb; border: 1px solid transparent; border-radius: 11px; font-size: 13px; font-weight: 600; transition: background .16s, border-color .16s, color .16s; }
.related-links button:hover { color: var(--brand); background: var(--fo-paid-soft); border-color: #d7dbfb; }
.related-icon { display: grid; place-items: center; width: 30px; height: 30px; flex-shrink: 0; color: var(--brand); background: #fff; border-radius: 9px; }
.related-icon svg { width: 16px; height: 16px; }
.finance-overview .related-arrow { width: 12px; height: 12px; margin-left: auto; color: var(--fo-ink-2); }
.related-links .quiet-label { width: fit-content; }
.definitions { margin-top: 16px; padding: 12px 18px; color: var(--fo-ink-2); font-size: 13px; background: rgba(255, 255, 255, .6); border: 1px solid var(--border); border-radius: var(--radius); }
.definitions > summary { cursor: pointer; list-style: none; display: flex; align-items: center; gap: 6px; width: fit-content; font-weight: 600; }
.definitions > summary::-webkit-details-marker { display: none; }
.definitions summary:hover { color: var(--brand); }
.definitions summary svg { width: 15px; height: 15px; color: var(--brand); }
.definition-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 22px; }
.definition-grid p { line-height: 1.7; font-size: 12px; margin: 14px 0 4px; }
.definition-grid strong { display: block; margin-bottom: 3px; color: var(--text-title); font-size: 13px; }

/* —— 状态 —— */
.state-panel { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 16px; min-height: 220px; margin-bottom: 16px; padding: 24px; background: #fff; border: 1px solid var(--border); border-radius: var(--radius); box-shadow: var(--shadow-card); color: var(--fo-ink-2); font-size: 13px; }
.error-panel h2 { font-size: 16px; }
.error-panel p { margin: 4px 0 0; font-size: 13px; }
.state-icon { display: grid; place-items: center; width: 48px; height: 48px; color: var(--fo-risk); background: var(--fo-risk-soft); border-radius: 14px; }
.state-icon svg { width: 24px; height: 24px; }
.loading-panel svg { color: var(--brand); }
.spinning { animation: finance-spin 1s linear infinite; }
@keyframes finance-spin { to { transform: rotate(360deg); } }

/* —— 响应式 —— */
@media (min-width: 1700px) {
  .collection-body { gap: 36px; }
  .collection-ring { width: 172px; height: 172px; }
  .ring-copy strong { font-size: 40px; }
  .amount { font-size: 28px; }
  .bar-chart { min-height: 214px; }
  .bar { width: 16px; }
  .analysis-grid > .surface { padding: 22px 24px 20px; }
}
@media (max-width: 1280px) {
  .summary-grid { grid-template-columns: 1fr; }
  .priority-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .priority-row { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; grid-template-areas: "icon copy arrow" "money money money"; align-items: start; gap: 10px 12px; padding: 14px; }
  .priority-icon { grid-area: icon; }
  .priority-copy { grid-area: copy; }
  .priority-money { grid-area: money; text-align: left; font-size: 20px; }
  .priority-row > svg { grid-area: arrow; margin-top: 4px; }
  .analysis-grid { grid-template-columns: 1fr 1fr; }
  .debtors-panel { grid-column: 1 / -1; }
  .debtor-list { grid-template-columns: 1fr 1fr; gap: 8px 12px; }
  .workflow-hint { display: none; }
  .related-links { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 1020px) {
  .priority-list { grid-template-columns: 1fr; }
  .priority-row { display: flex; align-items: center; padding: 11px 14px 11px 12px; }
  .priority-row > svg { margin-top: 0; }
  .priority-money { font-size: 17px; text-align: right; }
  .analysis-grid { grid-template-columns: 1fr; }
  .debtor-list { grid-template-columns: 1fr; }
  .workflow-steps { grid-template-columns: 1fr 1fr; }
  .workflow-step:nth-child(2)::after { display: none; }
  .definition-grid { grid-template-columns: 1fr 1fr; }
  .related-work { flex-direction: column; align-items: stretch; gap: 12px; }
  .related-copy { width: auto; }
}
@media (max-width: 900px) {
  .collection-body { flex-direction: column; align-items: stretch; gap: 18px; padding: 18px 0; }
  .collection-ring { align-self: center; }
  .amount-total { text-align: center; }
  .amount-total .amount-label { justify-content: center; }
  .scope-date { margin-left: 0; }
  .expand-button span { display: none; }
  .expand-button { width: 40px; padding: 0; }
  .workflow-panel { padding: 18px 16px 0; }
  .step-detail { margin: 0 -16px; padding: 14px 16px; }
  .collection-panel { padding: 18px 16px 0; }
  .cash-line { margin: 0 -16px; padding: 12px 16px; }
}
@media (max-width: 680px) {
  .finance-overview, .finance-overview.is-expanded { padding: 16px 12px; }
  .overview-controls { width: 100%; flex-wrap: nowrap; }
  .month-control { flex: 1; min-width: 0; justify-content: space-between; }
  .month-control input { max-width: none; flex: 1; min-width: 0; text-align: right; }
  .scope-line { gap: 6px; margin-bottom: 14px; }
  .scope-chip { font-size: 12px; }
  .summary-grid { gap: 12px; margin-bottom: 12px; }
  .section-head h2 { font-size: 15px; }
  .amount-total .amount { font-size: 30px; }
  .collection-amounts { grid-template-columns: 1fr; gap: 8px; }
  .amount-tile { display: grid; grid-template-columns: minmax(0, 1fr) auto; grid-template-areas: "label amount" "help amount"; align-items: center; gap: 2px 12px; padding: 11px 12px; }
  .amount-tile .amount-label { grid-area: label; }
  .amount-tile .amount { grid-area: amount; margin: 0; font-size: 20px; }
  .amount-tile .amount-help { grid-area: help; }
  .amount-help { font-size: 12px; }
  .priorities { padding: 18px 14px 0; }
  .priority-row { padding: 11px 10px; gap: 10px; }
  .priority-icon { width: 34px; height: 34px; }
  .priority-money { font-size: 15px; text-align: right; }
  .workflow-panel { margin-bottom: 12px; }
  .workflow-steps { gap: 8px; margin: 14px 0; }
  .workflow-step { padding: 10px; gap: 9px; align-items: flex-start; }
  .workflow-step::after { display: none; }
  .step-number { width: 28px; height: 28px; font-size: 12px; }
  .step-copy strong { font-size: 13px; }
  .step-detail { flex-wrap: wrap; gap: 10px; }
  .detail-icon { display: none; }
  .detail-copy { flex-basis: 100%; }
  .step-detail .primary-button { width: 100%; }
  .analysis-grid { gap: 12px; }
  .analysis-grid > .surface { padding: 18px 14px 16px; }
  .related-links { grid-template-columns: 1fr 1fr; gap: 8px; }
  .related-links button { padding: 9px; }
  .definition-grid { grid-template-columns: 1fr; gap: 0; }
  .state-panel { min-height: 230px; text-align: center; }
  .error-panel .primary-button { width: 100%; }
}
@media (max-width: 380px) {
  .workflow-steps { grid-template-columns: 1fr; }
}
@media (prefers-reduced-motion: reduce) { .finance-overview *, .finance-overview *::before, .finance-overview *::after { animation: none !important; transition: none !important; } }
</style>
