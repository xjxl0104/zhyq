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
// Every Axios request is intercepted locally; no authentication or business API is called.
request.defaults.adapter = async config => {
  if (config.url === '/finance/report/overview' && variant === 'error') throw new Error('示例：接口加载失败')
  const data = config.url === '/finance/report/overview' ? overview(config.params.month)
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
