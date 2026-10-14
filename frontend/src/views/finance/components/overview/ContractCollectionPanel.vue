<template>
  <section class="commerce-card contract-panel" aria-labelledby="contract-title">
    <header class="panel-head">
      <div>
        <h2 id="contract-title">合同收款全景</h2>
        <p v-if="data" class="panel-meta">{{ data.summary.contractCount }} 份合同<span v-if="data.summary.unlinkedGroups"> · {{ data.summary.unlinkedGroups }} 组账单未关联合同</span></p>
      </div>
      <label v-if="data" class="search-box"><Search /><input v-model.trim="keyword" type="search" placeholder="搜索租客或合同号" aria-label="搜索租客或合同号" /></label>
    </header>

    <div v-if="error" class="panel-state is-error" role="alert"><Warning /><span>合同收款数据没有加载成功</span><button type="button" class="ghost-button" @click="emit('retry')">重新加载</button></div>
    <div v-else-if="!data" class="panel-state" role="status"><Loading class="spinning" /><span>正在按合同汇总收款…</span></div>

    <div v-else class="panel-body">
      <aside class="state-side">
        <div class="state-chart"><OverviewChart :option="stateOption" label="合同收款状态分布，点击筛选" @select="onChartSelect" /></div>
        <div class="state-legend">
          <button v-for="meta in STATE_META" :key="meta.key" type="button" class="state-item" :class="{ active: activeState === meta.key }" :aria-pressed="activeState === meta.key" :disabled="!counts[meta.key]" @click="toggleState(meta.key)">
            <i :style="{ background: meta.color }"></i><span>{{ meta.label }}</span><b>{{ counts[meta.key] }}</b>
          </button>
        </div>
        <dl class="money-strip">
          <div><dt>已收</dt><dd class="paid">{{ wanLabel(data.summary.received) }}<small>万</small></dd></div>
          <div><dt>未收</dt><dd>{{ wanLabel(data.summary.outstanding) }}<small>万</small></dd></div>
          <div><dt>逾期</dt><dd class="risk">{{ wanLabel(data.summary.overdueOutstanding) }}<small>万</small></dd></div>
        </dl>
      </aside>

      <div class="contract-main">
        <div class="list-toolbar">
          <div class="state-tabs" role="group" aria-label="按收款状态筛选">
            <button type="button" :class="{ active: !activeState }" :aria-pressed="!activeState" @click="activeState = ''">全部 <b>{{ data.contracts.length }}</b></button>
            <button v-for="meta in STATE_META" :key="meta.key" type="button" :class="{ active: activeState === meta.key }" :aria-pressed="activeState === meta.key" :disabled="!counts[meta.key]" @click="toggleState(meta.key)">{{ meta.label }} <b>{{ counts[meta.key] }}</b></button>
          </div>
          <div class="bar-legend" aria-hidden="true"><span><i class="seg-paid"></i>已收</span><span><i class="seg-notDue"></i>未到期</span><span><i class="seg-due"></i>到期未收</span><span><i class="seg-overdue"></i>逾期</span></div>
        </div>

        <ul v-if="visibleRows.length" class="contract-list">
          <li v-for="row in visibleRows" :key="row.key" class="contract-item" :class="[`is-${row.state}`, { open: expandedKey === row.key }]">
            <button class="contract-row" type="button" :aria-expanded="expandedKey === row.key" @click="toggleRow(row.key)">
              <span class="contract-id">
                <strong :title="row.tenantName">{{ row.tenantName }}</strong>
                <small>{{ row.contractCode || '未关联合同' }}<template v-if="row.contractStatus"> · {{ contractStatusText(row.contractStatus) }}</template></small>
              </span>
              <span class="contract-progress">
                <span v-if="row.state !== 'unbilled'" class="segment-bar" role="img" :aria-label="`已收 ${money(row.received)} 元，未收 ${money(row.outstanding)} 元，其中逾期 ${money(row.overdueOutstanding)} 元`">
                  <span v-for="segment in contractSegments(row)" :key="segment.key" :class="`seg-${segment.key}`" :style="{ width: `${segment.percent}%` }"></span>
                </span>
                <span v-else class="segment-bar is-empty"></span>
                <small>{{ row.state === 'unbilled' ? '尚未生成账单' : `已收 ${money(row.received)} / 应收 ${money(row.receivable)}` }}</small>
              </span>
              <span class="contract-owe"><b :class="{ risk: Number(row.overdueOutstanding) > 0 }">{{ row.state === 'unbilled' ? '—' : money(row.outstanding) }}</b><small>未收 / 元</small></span>
              <span class="state-tag">{{ stateLabel(row.state) }}</span>
              <ArrowDown class="chevron" />
            </button>
            <div v-if="expandedKey === row.key" class="contract-detail">
              <table v-if="row.fees.length" class="fee-table">
                <caption class="sr-only">{{ row.tenantName }}各款项收款情况，单位元</caption>
                <thead><tr><th>款项</th><th>应收</th><th>已收</th><th>未收</th><th class="col-bar">收款进度</th></tr></thead>
                <tbody>
                  <tr v-for="fee in row.fees" :key="fee.feeType">
                    <th>{{ fee.feeType }}<small>{{ fee.billCount }} 张</small></th>
                    <td>{{ money(fee.receivable) }}</td>
                    <td class="paid">{{ money(fee.received) }}</td>
                    <td :class="{ risk: Number(fee.overdueOutstanding) > 0 }">{{ money(fee.outstanding) }}<small v-if="Number(fee.overdueOutstanding) > 0">逾期 {{ money(fee.overdueOutstanding) }}</small></td>
                    <td class="col-bar"><span class="fee-bar"><span class="seg-paid" :style="{ width: `${share(fee.received, fee.receivable)}%` }"></span><span class="seg-overdue" :style="{ width: `${share(fee.overdueOutstanding, fee.receivable)}%` }"></span></span></td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="detail-empty">还没有账单：合同审批通过，或在应收明细登记表生成账单后，这里会列出每笔款项。</p>
              <div class="detail-actions">
                <button v-if="row.contractId && canContract" type="button" class="ghost-button" @click="emit('open-contract', row)">合同详情</button>
                <button v-if="row.state !== 'unbilled'" type="button" class="solid-button" :disabled="!canBill" @click="emit('open-bills', row)">{{ canBill ? '查看这份合同的账单' : '需开通账单权限' }}<ArrowRight /></button>
              </div>
            </div>
          </li>
        </ul>
        <div v-else class="list-empty"><CircleCheck /><p>{{ data.contracts.length ? '没有符合条件的合同' : '还没有合同或账单' }}</p></div>
        <button v-if="filteredRows.length > visibleCount" type="button" class="more-button" @click="visibleCount += PAGE_SIZE">再显示 {{ Math.min(PAGE_SIZE, filteredRows.length - visibleCount) }} 份 · 共 {{ filteredRows.length }} 份</button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ArrowDown, ArrowRight, CircleCheck, Loading, Search, Warning } from '@element-plus/icons-vue'
import { money } from '@/utils/format'
import OverviewChart from './OverviewChart.vue'
import { STATE_META, contractSegments, stateDonutOption, wanLabel } from './overviewCharts'

const props = defineProps({
  data: { type: Object, default: null },
  error: { type: Boolean, default: false },
  canBill: { type: Boolean, default: false },
  canContract: { type: Boolean, default: false }
})
const emit = defineEmits(['retry', 'open-bills', 'open-contract'])

const PAGE_SIZE = 8
const CONTRACT_STATUS = { 1: '草稿', 2: '待审核', 3: '待签署', 4: '待执行', 5: '执行中', 6: '变更中', 7: '退租中', 8: '已到期', 9: '已终止', 10: '已归档' }
const activeState = ref('')
const keyword = ref('')
const expandedKey = ref('')
const visibleCount = ref(PAGE_SIZE)

const counts = computed(() => Object.fromEntries(STATE_META.map(meta => [meta.key, Number(props.data?.summary?.[meta.key] || 0)])))
const stateOption = computed(() => stateDonutOption(props.data?.summary, activeState.value))
const filteredRows = computed(() => {
  const term = keyword.value.toLowerCase()
  return (props.data?.contracts || []).filter(row => (!activeState.value || row.state === activeState.value)
    && (!term || `${row.tenantName || ''} ${row.contractCode || ''}`.toLowerCase().includes(term)))
})
const visibleRows = computed(() => filteredRows.value.slice(0, visibleCount.value))

watch([activeState, keyword], () => { visibleCount.value = PAGE_SIZE })

function toggleState(key) { activeState.value = activeState.value === key ? '' : key }
function toggleRow(key) { expandedKey.value = expandedKey.value === key ? '' : key }
function onChartSelect(params) { if (params?.data?.key) toggleState(params.data.key) }
function stateLabel(state) { return STATE_META.find(meta => meta.key === state)?.label || state }
function contractStatusText(status) { return CONTRACT_STATUS[status] || `状态 ${status}` }
function share(part, total) { return Number(total) > 0 ? Math.min(100, Math.max(0, Number(part) / Number(total) * 100)) : 0 }
</script>

<style scoped>
.contract-panel { --c-paid: #2f4bf0; --c-notDue: #dfe3ec; --c-due: #eaa23c; --c-overdue: #e0473d; --c-ink2: #68708a; margin-bottom: 16px; padding: 18px 20px 20px; }
.contract-panel svg { width: 16px; height: 16px; flex-shrink: 0; }
.contract-panel button { font: inherit; cursor: pointer; }
.contract-panel button:disabled { cursor: not-allowed; opacity: .45; }
.contract-panel :is(button, input):focus-visible { outline: 3px solid var(--c-paid); outline-offset: 2px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
h2 { margin: 0; color: var(--text-title); font-size: 16px; font-weight: 700; letter-spacing: -.015em; }
.panel-meta { margin: 3px 0 0; color: var(--c-ink2); font-size: 12px; }
.search-box { display: flex; align-items: center; gap: 8px; width: min(280px, 100%); min-height: 36px; padding: 0 12px; color: var(--c-ink2); background: #f6f7fb; border: 1px solid transparent; border-radius: 10px; transition: border-color .16s, background .16s; }
.search-box:focus-within { background: #fff; border-color: #c3cbfa; }
.search-box input { flex: 1; min-width: 0; border: 0; outline: 0; background: transparent; color: var(--text-title); font: inherit; font-size: 13px; }
.panel-state { display: flex; align-items: center; justify-content: center; gap: 10px; min-height: 260px; color: var(--c-ink2); font-size: 13px; }
.panel-state.is-error svg { color: var(--c-overdue); }
.panel-body { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 24px; margin-top: 16px; }
.state-side { align-self: start; display: flex; flex-direction: column; gap: 12px; padding-right: 24px; border-right: 1px solid var(--border); }
.state-chart { height: 180px; }
.state-legend { display: grid; gap: 4px; }
.state-item { display: grid; grid-template-columns: 10px 1fr auto; align-items: center; gap: 8px; padding: 7px 10px; color: var(--text-body); text-align: left; background: transparent; border: 1px solid transparent; border-radius: 9px; font-size: 13px; transition: background .16s, border-color .16s; }
.state-item i { width: 10px; height: 10px; border-radius: 3px; }
.state-item b { color: var(--text-title); font-weight: 700; }
.state-item:hover:not(:disabled), .state-item.active { background: #f4f6fd; border-color: #dfe3fd; }
.money-strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 0; padding-top: 12px; border-top: 1px dashed var(--border); }
.money-strip div { min-width: 0; }
.money-strip dt { color: var(--c-ink2); font-size: 12px; }
.money-strip dd { margin: 2px 0 0; color: var(--text-title); font-size: 16px; font-weight: 720; letter-spacing: -.02em; white-space: nowrap; }
.money-strip dd small { margin-left: 2px; color: var(--c-ink2); font-size: 11px; font-weight: 500; }
.money-strip .paid { color: var(--c-paid); }
.money-strip .risk, .risk { color: #c2413a !important; }
.contract-main { min-width: 0; display: flex; flex-direction: column; }
.list-toolbar { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px 16px; margin-bottom: 10px; }
.state-tabs { display: flex; flex-wrap: wrap; gap: 4px; padding: 3px; background: #f4f5f9; border-radius: 11px; }
.state-tabs button { padding: 5px 11px; color: var(--c-ink2); background: transparent; border: 0; border-radius: 8px; font-size: 13px; font-weight: 600; white-space: nowrap; }
.state-tabs button b { margin-left: 2px; font-weight: 700; }
.state-tabs button.active { color: var(--text-title); background: #fff; box-shadow: 0 1px 3px rgba(11, 13, 23, .1); }
.bar-legend { display: flex; flex-wrap: wrap; gap: 12px; color: var(--c-ink2); font-size: 12px; }
.bar-legend span { display: inline-flex; align-items: center; gap: 5px; }
.bar-legend i { width: 10px; height: 10px; border-radius: 3px; }
.seg-paid { background: var(--c-paid); }
.seg-notDue { background: var(--c-notDue); }
.seg-due { background: var(--c-due); }
.seg-overdue { background: var(--c-overdue); }
.contract-list { display: grid; gap: 6px; margin: 0; padding: 0; list-style: none; }
.contract-item { border: 1px solid #eef0f4; border-radius: 12px; background: #fff; transition: border-color .16s, box-shadow .16s; }
.contract-item.open { border-color: #c9cffb; box-shadow: 0 8px 20px rgba(41, 49, 93, .07); }
.contract-row { display: grid; grid-template-columns: minmax(150px, 1.1fr) minmax(200px, 1.6fr) 120px 72px 16px; align-items: center; gap: 16px; width: 100%; padding: 11px 14px; color: var(--text-body); text-align: left; background: transparent; border: 0; border-radius: 12px; }
.contract-row:hover { background: #fafbff; }
.contract-id { display: grid; gap: 2px; min-width: 0; }
.contract-id strong { overflow: hidden; color: var(--text-title); font-size: 14px; font-weight: 650; white-space: nowrap; text-overflow: ellipsis; }
.contract-id small, .contract-progress small, .contract-owe small { overflow: hidden; color: var(--c-ink2); font-size: 12px; white-space: nowrap; text-overflow: ellipsis; }
.contract-progress { display: grid; gap: 5px; min-width: 0; }
.segment-bar { display: flex; gap: 2px; height: 10px; overflow: hidden; background: #f1f2f6; border-radius: 999px; }
.segment-bar > span { min-width: 3px; height: 100%; }
.segment-bar.is-empty { background: repeating-linear-gradient(135deg, #f1f2f6 0 6px, #e6e8ef 6px 8px); }
.contract-owe { display: grid; gap: 1px; text-align: right; }
.contract-owe b { color: var(--text-title); font-size: 15px; font-weight: 720; letter-spacing: -.02em; white-space: nowrap; }
.state-tag { justify-self: start; padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 650; white-space: nowrap; }
.is-overdue .state-tag { color: #c2413a; background: #fff1f0; }
.is-unpaid .state-tag { color: #a35c00; background: #fff6e8; }
.is-partial .state-tag { color: #2f4bf0; background: #eef0ff; }
.is-settled .state-tag { color: #1c7f55; background: #e9f7f0; }
.is-unbilled .state-tag { color: #5d6480; background: #f0f1f6; }
.chevron { color: var(--c-ink2); transition: transform .2s; }
.open .chevron { transform: rotate(180deg); }
.contract-detail { padding: 2px 14px 14px; }
.fee-table { width: 100%; border-collapse: collapse; font-size: 13px; font-variant-numeric: tabular-nums; }
.fee-table th, .fee-table td { padding: 8px 10px; text-align: right; border-bottom: 1px solid #f0f1f5; white-space: nowrap; }
.fee-table thead th { color: var(--c-ink2); background: #f8f9fc; font-size: 12px; font-weight: 600; }
.fee-table thead th:first-child { border-radius: 8px 0 0 8px; }
.fee-table thead th:last-child { border-radius: 0 8px 8px 0; }
.fee-table tbody th { color: var(--text-title); font-weight: 650; text-align: left; }
.fee-table :is(th, td) small { display: block; color: var(--c-ink2); font-size: 11px; font-weight: 500; }
.fee-table th:first-child { text-align: left; }
.fee-table .paid { color: var(--c-paid); }
.col-bar { width: 26%; }
.fee-bar { display: flex; height: 8px; overflow: hidden; background: #f1f2f6; border-radius: 999px; }
.detail-empty { margin: 4px 0 0; padding: 12px 14px; color: var(--c-ink2); background: #f8f9fc; border-radius: 10px; font-size: 13px; line-height: 1.6; }
.detail-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.ghost-button, .solid-button, .more-button { display: inline-flex; align-items: center; gap: 6px; min-height: 34px; padding: 6px 13px; border-radius: 10px; font-size: 13px; font-weight: 600; white-space: nowrap; }
.ghost-button { color: var(--text-body); background: #fff; border: 1px solid var(--border); }
.ghost-button:hover { color: var(--brand); border-color: #c9cffb; }
.solid-button { color: #fff; background: var(--brand); border: 1px solid var(--brand); }
.solid-button:hover:not(:disabled) { background: var(--brand-hover); }
.solid-button svg { width: 13px; height: 13px; }
.more-button { align-self: center; margin-top: 10px; color: var(--brand); background: #f4f6fd; border: 0; }
.list-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; min-height: 200px; color: var(--c-ink2); font-size: 13px; }
.list-empty svg { width: 36px; height: 36px; padding: 9px; color: var(--brand); background: #eef0ff; border-radius: 11px; }
.list-empty p { margin: 0; }
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
.spinning { animation: contract-spin 1s linear infinite; }
@keyframes contract-spin { to { transform: rotate(360deg); } }
@media (max-width: 1280px) {
  .panel-body { grid-template-columns: 1fr; }
  .state-side { display: grid; grid-template-columns: 170px minmax(0, 1fr) minmax(0, 1fr); align-items: center; gap: 12px 24px; padding: 0 0 16px; border-right: 0; border-bottom: 1px solid var(--border); }
  .money-strip { grid-template-columns: 1fr; padding: 0 0 0 24px; border-top: 0; border-left: 1px dashed var(--border); }
  .state-chart { height: 170px; }
}
@media (max-width: 1020px) {
  .state-side { grid-template-columns: 170px minmax(0, 1fr); }
  .money-strip { grid-column: 1 / -1; grid-template-columns: repeat(3, 1fr); padding: 12px 0 0; border-left: 0; border-top: 1px dashed var(--border); }
  .contract-row { grid-template-columns: minmax(130px, 1fr) minmax(150px, 1.3fr) 104px 66px 16px; gap: 12px; }
}
@media (max-width: 680px) {
  .contract-panel { padding: 16px 12px; }
  .panel-head { flex-direction: column; align-items: stretch; gap: 10px; }
  .search-box { width: 100%; }
  .state-side { grid-template-columns: 1fr; }
  .state-chart { grid-row: auto; height: 170px; }
  .bar-legend { display: none; }
  .state-tabs { flex-wrap: nowrap; overflow-x: auto; max-width: 100%; }
  .contract-row { grid-template-columns: minmax(0, 1fr) auto 16px; grid-template-areas: "id owe chevron" "bar bar bar" "tag tag tag"; gap: 8px 10px; padding: 12px; }
  .contract-id { grid-area: id; }
  .contract-owe { grid-area: owe; }
  .chevron { grid-area: chevron; }
  .contract-progress { grid-area: bar; }
  .state-tag { grid-area: tag; }
  .contract-detail { padding: 0 10px 12px; overflow-x: auto; }
  .col-bar { display: none; }
  .fee-table th, .fee-table td { padding: 8px 6px; }
}
@media (prefers-reduced-motion: reduce) { .contract-panel * { animation: none !important; transition: none !important; } }
</style>
