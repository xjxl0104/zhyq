// Isolated visual QA harness. Not imported by the production application.
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn.mjs'
import * as icons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '../src/styles/index.scss'
import Layout from '../src/layout/Layout.vue'
import Overview from '../src/views/finance/Overview.vue'
import request from '../src/utils/request'
import { useAccessStore } from '../src/stores/access'

if (!import.meta.env.DEV || location.port !== '5331') throw new Error('This isolated preview runs only on local development port 5331.')

const variant = new URLSearchParams(location.search).get('state')
function overview(month) {
  const empty = variant === 'empty'
  const large = variant === 'large' ? 80 : 1
  const n = value => empty ? 0 : Math.round(value * large * 100) / 100
  const fees = [ ['租金', 768000, 586400], ['物业费', 126400, 102800], ['保证金', 80000, 55000], ['水电费', 43860, 29260] ]
  const [year, m] = month.split('-').map(Number)
  const trend = Array.from({ length: 6 }, (_, index) => {
    const date = new Date(year, m - 6 + index, 1)
    return { month: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`, receivable: n([842800, 914200, 896600, 956800, 1012600, 1018260][index]), received: n([808600, 874300, 816500, 845200, 911600, 773460][index]), outstanding: n([34200, 39900, 80100, 111600, 101000, 244800][index]) }
  })
  return { month, asOfDate: '2026-10-14', scopeLabel: '预览示例 · 全部项目', periodReceivable: n(1018260), periodReceived: n(773460), periodOutstanding: n(244800), collectionRate: empty ? 0 : 773460 / 1018260, cashReceived: n(862730), totalOutstanding: n(608960), dueOutstanding: n(408960), upcomingOutstanding: n(200000), undatedOutstanding: 0, overdueOutstanding: n(286540), overdueBillCount: empty ? 0 : 18, depositOutstanding: n(65000),
    feeStructure: empty ? [] : fees.map(([feeType, receivable, received]) => ({ feeType, receivable: n(receivable), received: n(received), outstanding: n(receivable - received) })), trend,
    topDebtors: empty ? [] : ['示例 · 云帆物流', '示例 · 晨星智能设备', '示例 · 青禾食品', '示例 · 新程供应链', '示例 · 盛远科技'].map((tenantName, index) => ({ tenantRefId: index + 1, tenantName, outstanding: n([176800, 132400, 98860, 74000, 56900][index]), overdueOutstanding: n([94000, 88000, 64540, 0, 40000][index]), billCount: [6, 5, 4, 3, 2][index] })) }
}
function contractCollection() {
  const empty = variant === 'empty'
  const large = variant === 'large' ? 80 : 1
  const n = value => Math.round(value * large * 100) / 100
  const fee = (feeType, receivable, received, overdue = 0, billCount = 1) => ({ feeType, receivable: n(receivable), received: n(received), outstanding: n(receivable - received), overdueOutstanding: n(overdue), billCount })
  const row = (id, code, tenantName, status, state, fees, split = {}) => {
    const sum = key => fees.reduce((total, item) => total + Number(item[key]), 0)
    const outstanding = sum('outstanding')
    const overdue = split.overdue ?? sum('overdueOutstanding')
    const due = split.due ?? 0
    return { key: id ? `contract:${id}` : `register:${code}`, contractId: id, contractCode: id ? code : null, tenantRefId: id || 99, tenantName, contractStatus: status,
      startDate: '2026-01-01', endDate: '2027-12-31', state, receivable: sum('receivable'), received: sum('received'), outstanding,
      overdueOutstanding: overdue, dueOutstanding: due, notDueOutstanding: Math.max(0, outstanding - overdue - due), billCount: fees.reduce((t, f) => t + f.billCount, 0), fees }
  }
  const contracts = empty ? [] : [
    row(1, 'HT-2026-001', '示例 · 云帆物流', 5, 'overdue', [fee('租金', 360000, 240000, 94000, 6), fee('物业费', 72000, 48000, 0, 6), fee('租金保证金', 60000, 60000)], { due: 26000 }),
    row(2, 'HT-2026-002', '示例 · 晨星智能设备', 5, 'overdue', [fee('租金', 240000, 150000, 60000, 5), fee('物业费', 58000, 30000, 28000, 5)]),
    row(3, 'HT-2026-003', '示例 · 青禾食品', 5, 'overdue', [fee('租金', 180000, 120000, 40000, 4), fee('能源费', 24540, 0, 24540, 2)]),
    row(4, 'HT-2026-004', '示例 · 新程供应链', 5, 'partial', [fee('租金', 200000, 140000, 0, 4), fee('物业费', 40000, 26000, 0, 4)], { due: 30000 }),
    row(5, 'HT-2026-005', '示例 · 盛远科技', 5, 'partial', [fee('租金', 150000, 110000, 0, 3), fee('物业费', 30000, 20000, 0, 3)]),
    row(6, 'HT-2026-006', '示例 · 北辰文创', 4, 'unpaid', [fee('租金', 90000, 0, 0, 1), fee('租金保证金', 30000, 0, 0, 1)], { due: 30000 }),
    row(7, 'HT-2025-018', '示例 · 安途汽配', 8, 'settled', [fee('租金', 288000, 288000, 0, 12), fee('物业费', 57600, 57600, 0, 12)]),
    row(8, 'HT-2025-022', '示例 · 繁星餐饮', 9, 'settled', [fee('租金', 96000, 96000, 0, 6)]),
    row(9, 'HT-2026-007', '示例 · 绿洲生物', 5, 'settled', [fee('租金', 120000, 120000, 0, 4), fee('物业费', 24000, 24000, 0, 4)]),
    row(null, 'R-11', '示例 · 未签约临时租户', null, 'partial', [fee('能源费', 12000, 4000, 0, 2)]),
    row(10, 'HT-2023-031', '示例 · 历史合同（待补录）', 1, 'unbilled', []),
    row(11, 'HT-2024-007', '示例 · 历史合同（审批中）', 2, 'unbilled', [])
  ]
  const count = state => contracts.filter(item => item.state === state).length
  const total = key => contracts.reduce((sum, item) => sum + Number(item[key]), 0)
  return { asOfDate: '2026-10-14', contracts, aging: [
    { key: 'notDue', label: '未到期', amount: n(empty ? 0 : 200000), billCount: 9 }, { key: 'undated', label: '未设应收日', amount: 0, billCount: 0 },
    { key: 'grace', label: '宽限期内', amount: n(empty ? 0 : 56000), billCount: 3 }, { key: 'd30', label: '逾期 1–30 天', amount: n(empty ? 0 : 128000), billCount: 7 },
    { key: 'd90', label: '逾期 31–90 天', amount: n(empty ? 0 : 98540), billCount: 6 }, { key: 'd90plus', label: '逾期 90 天以上', amount: n(empty ? 0 : 60000), billCount: 5 }],
    summary: { contractCount: contracts.filter(item => item.contractId).length, unlinkedGroups: contracts.filter(item => !item.contractId).length,
      settled: count('settled'), partial: count('partial'), unpaid: count('unpaid'), overdue: count('overdue'), unbilled: count('unbilled'),
      receivable: total('receivable'), received: total('received'), outstanding: total('outstanding'), overdueOutstanding: total('overdueOutstanding') } }
}
// Every Axios request is intercepted locally; no authentication or business API is called.
request.defaults.adapter = async config => {
  if (config.url === '/finance/report/overview' && variant === 'error') throw new Error('示例：接口加载失败')
  if (config.url === '/finance/report/contract-collection' && variant === 'error') throw new Error('示例：接口加载失败')
  const data = config.url === '/finance/report/overview' ? overview(config.params.month)
    : config.url === '/finance/report/contract-collection' ? contractCollection()
    : config.url === '/building/project/list' ? [{ id: 1001, name: '园区示例项目' }] : null
  return { data: { code: 0, data }, status: 200, statusText: 'OK', headers: {}, config }
}
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Layout, children: [
  { path: 'finance/overview', meta: { title: '财务总览' }, component: Overview },
  { path: ':pathMatch(.*)*', component: { setup: () => () => h('div', { style: 'padding:48px' }, [
    h('h2', '业务入口跳转成功'), h('p', router.currentRoute.value.fullPath),
    h('p', '这里是示例预览。正式系统会打开已有业务页面，并带上对应筛选条件。'),
    h('button', { onClick: () => router.push('/finance/overview') }, '返回财务总览'),
  ]) } },
] }] })
const app = createApp({ render: () => h(RouterView) })
for (const [key, component] of Object.entries(icons)) app.component(key, component)
const pinia = createPinia()
app.use(pinia).use(router).use(ElementPlus, { locale: zhCn })
const access = useAccessStore(pinia)
access.admin = new URLSearchParams(location.search).get('access') !== 'report'
access.menus = [{ id: 1, parentId: 0, name: '财务报表', type: 2, status: 1, visible: 1, path: '/finance/report' }]
await router.push('/finance/overview')
const style = document.createElement('style')
style.textContent = '#preview-notice{height:32px;display:flex;align-items:center;justify-content:center;padding:0 8px;font:12px sans-serif;color:#744611;background:#fff4da;text-align:center}#app{height:calc(100% - 32px)}.app-wrapper{height:100%!important}.finance-overview.is-expanded::before{content:"示例数据预览 · 非真实财务数据";display:block;color:#925d13;font-size:12px;margin-bottom:12px}@media(max-width:600px){#preview-notice{height:40px;font-size:11px}#app{height:calc(100% - 40px)}}'
document.head.append(style)
app.mount('#app')
