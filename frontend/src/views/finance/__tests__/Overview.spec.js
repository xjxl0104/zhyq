import { flushPromises, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Overview from '../Overview.vue'
import ContractCollectionPanel from '../components/overview/ContractCollectionPanel.vue'
import OverviewChart from '../components/overview/OverviewChart.vue'
import request from '@/utils/request'

const { access, push } = vi.hoisted(() => ({ access: { admin: false, paths: new Set() }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/stores/access', () => ({ useAccessStore: () => access }))
// 保持 reportApi 真实：组件必须调用总览与合同全景两个接口，并显式退出项目过滤。
vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))

function overview(overrides = {}) {
  return {
    month: '2026-10', asOfDate: '2026-10-14', scopeLabel: '全园区 · 全部项目',
    periodReceivable: 123456.78, periodReceived: 75000, periodOutstanding: 48456.78,
    collectionRate: 0.6075, cashReceived: -2000, totalOutstanding: 90000,
    dueOutstanding: 60000, upcomingOutstanding: 28000, undatedOutstanding: 2000,
    overdueOutstanding: 32000, overdueBillCount: 4, depositOutstanding: 5000,
    feeStructure: [
      { feeType: '租金', receivable: 100000, received: 70000, outstanding: 30000 },
      { feeType: '物业费', receivable: 23456.78, received: 5000, outstanding: 18456.78 },
      { feeType: '免租期', receivable: 0, received: 0, outstanding: 0 }
    ],
    trend: [
      { month: '2026-09', receivable: 200000, received: 150000, outstanding: 50000 },
      { month: '2026-10', receivable: 123456.78, received: 75000, outstanding: 48456.78 }
    ],
    topDebtors: [],
    ...overrides
  }
}
function collection() {
  return {
    asOfDate: '2026-10-14',
    summary: { contractCount: 1, unlinkedGroups: 1, settled: 0, partial: 1, unpaid: 0, overdue: 1, unbilled: 0, receivable: 300, received: 100, outstanding: 200, overdueOutstanding: 50 },
    contracts: [
      { key: 'contract:7', contractId: 7, contractCode: 'HT-007', tenantRefId: 3, tenantName: '启明科技', state: 'overdue', receivable: 200, received: 50, outstanding: 150, overdueOutstanding: 50, dueOutstanding: 0, notDueOutstanding: 100, billCount: 2, fees: [] },
      { key: 'register:9', contractId: null, contractCode: null, tenantRefId: 4, tenantName: '临时租户', state: 'partial', receivable: 100, received: 50, outstanding: 50, overdueOutstanding: 0, dueOutstanding: 0, notDueOutstanding: 50, billCount: 1, fees: [] }
    ],
    aging: [{ key: 'notDue', label: '未到期', amount: 150, billCount: 2 }, { key: 'd30', label: '逾期 1–30 天', amount: 50, billCount: 1 }]
  }
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function respond({ overviewResult = () => Promise.resolve(overview()), collectionResult = () => Promise.resolve(collection()) } = {}) {
  request.get.mockImplementation(url => (url === '/finance/report/overview' ? overviewResult() : collectionResult()))
}
const wrappers = []
function mountPage() {
  const wrapper = shallowMount(Overview, { global: { stubs: { Teleport: true } } })
  wrappers.push(wrapper)
  return wrapper
}
const overviewCalls = () => request.get.mock.calls.filter(([url]) => url === '/finance/report/overview')

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-14T02:00:00Z'))
  access.admin = false
  access.paths = new Set(['/finance/overview', '/finance/report'])
  respond()
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.useRealTimers()
})

describe('财务总览数据与交互', () => {
  it('并行调用总览与合同全景接口，均退出顶部项目过滤', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/finance/report/overview', { params: { month: '2026-10', projectId: null } })
    expect(request.get).toHaveBeenCalledWith('/finance/report/contract-collection', { params: { projectId: null } })
    expect(wrapper.get('.scope-line').text()).toContain('全园区 · 全部项目')
    expect(wrapper.findAll('.kpi-value').map(item => item.text())).toEqual([
      '123,456.78元', '75,000.00元', '90,000.00元', '32,000.00元'
    ])
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('60.8')
    expect(wrapper.text()).toContain('4 张账单')
    expect(wrapper.findAll('.fee-row')).toHaveLength(2)
    expect(wrapper.findAll('.chart-details tbody tr')).toHaveLength(2)
    expect(wrapper.getComponent(ContractCollectionPanel).props('data').contracts).toHaveLength(2)
  })

  it('待收分段条按逾期、到期未逾期、未到期拆分', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const widths = wrapper.findAll('.split-track > span').map(item => [item.classes()[0], item.attributes('style')])
    // 逾期 32000；到期未逾期 60000-32000=28000；未到期 28000+2000=30000
    expect(widths).toEqual([
      ['seg-overdue', 'width: 35.6%;'], ['seg-due', 'width: 31.1%;'], ['seg-notDue', 'width: 33.3%;']
    ])
  })

  it('首次响应到达前显示加载状态，不渲染虚假的零金额', async () => {
    const pending = deferred()
    respond({ overviewResult: () => pending.promise })
    const wrapper = mountPage()
    await nextTick()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('[role="status"]').text()).toContain('正在汇总')
    expect(wrapper.find('.kpi-grid').exists()).toBe(false)
    expect(wrapper.find('.chart-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0.00')
    expect(wrapper.findAll('.workflow-step')).toHaveLength(4)
    pending.resolve(overview())
    await flushPromises()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('false')
    expect(wrapper.find('.kpi-grid').exists()).toBe(true)
  })

  it('总览失败时保留流程并可重试；合同全景失败单独提示', async () => {
    let overviewFails = true
    respond({
      overviewResult: () => (overviewFails ? Promise.reject(new Error('down')) : Promise.resolve(overview())),
      collectionResult: () => Promise.reject(new Error('down'))
    })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('财务数据暂时没有加载成功')
    expect(wrapper.findAll('.workflow-step')).toHaveLength(4)
    expect(wrapper.getComponent(ContractCollectionPanel).props('error')).toBe(true)
    overviewFails = false
    await wrapper.get('.error-panel button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.error-panel').exists()).toBe(false)
    expect(wrapper.get('.kpi-grid').text()).toContain('123,456.78')
  })

  it.each(['success', 'failure'])('快速切换月份后，忽略较早请求的迟到%s响应', async outcome => {
    const older = deferred()
    const latest = deferred()
    const queue = [() => Promise.resolve(overview()), () => older.promise, () => latest.promise]
    respond({ overviewResult: () => queue.shift()() })
    const wrapper = mountPage()
    await flushPromises()
    const picker = wrapper.get('input[aria-label="选择账期月份"]')
    await picker.setValue('2026-09')
    await picker.setValue('2026-08')
    latest.resolve(overview({ month: '2026-08', periodReceivable: 88888 }))
    await flushPromises()
    if (outcome === 'success') older.resolve(overview({ month: '2026-09', periodReceivable: 99999 }))
    else older.reject(new Error('stale'))
    await flushPromises()
    expect(wrapper.get('.kpi-grid').text()).toContain('88,888.00')
    expect(wrapper.get('.kpi-grid').text()).not.toContain('99,999.00')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('false')
  })

  it('点击趋势图柱子切换账期，只重新加载总览', async () => {
    const wrapper = mountPage()
    await flushPromises()
    wrapper.findAllComponents(OverviewChart)[0].vm.$emit('select', { dataIndex: 0 })
    await flushPromises()
    expect(wrapper.get('input[type="month"]').element.value).toBe('2026-09')
    expect(overviewCalls().at(-1)).toEqual(['/finance/report/overview', { params: { month: '2026-09', projectId: null } }])
    expect(request.get.mock.calls.filter(([url]) => url === '/finance/report/contract-collection')).toHaveLength(1)
  })

  it('合同行跳到账单：有合同按合同筛选，未关联合同按租客筛选', async () => {
    access.paths = new Set(['/finance/bill', '/contract/list'])
    const wrapper = mountPage()
    await flushPromises()
    const panel = wrapper.getComponent(ContractCollectionPanel)
    expect(panel.props('canBill')).toBe(true)
    expect(panel.props('canContract')).toBe(true)
    panel.vm.$emit('open-bills', collection().contracts[0])
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { contractId: '7', contractCode: 'HT-007', direction: '1' } })
    panel.vm.$emit('open-bills', collection().contracts[1])
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { tenantRefId: '4', direction: '1' } })
    panel.vm.$emit('open-contract', collection().contracts[0])
    expect(push).toHaveBeenLastCalledWith({ path: '/contract/detail/7' })
  })

  it('没有业务页面权限时指标卡与入口不可跳转', async () => {
    const wrapper = mountPage()
    await flushPromises()
    for (const tile of wrapper.findAll('button.kpi-tile')) expect(tile.element.disabled).toBe(true)
    for (const row of wrapper.findAll('.fee-row')) expect(row.element.disabled).toBe(true)
    wrapper.getComponent(ContractCollectionPanel).vm.$emit('open-bills', collection().contracts[0])
    await wrapper.findAll('.workflow-step')[2].trigger('click')
    expect(wrapper.get('.step-detail button').text()).toContain('需开通此功能权限')
    expect(wrapper.getComponent(ContractCollectionPanel).props('canBill')).toBe(false)
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.findAll('.related-links button').map(button => button.text())).toEqual(['财务报表'])
  })

  it('四步流程切换说明并跳转到对应入口', async () => {
    access.paths = new Set(['/finance/receivable-register', '/finance/bill', '/finance/cashier', '/finance/receipt'])
    const wrapper = mountPage()
    await flushPromises()
    const paths = ['/finance/receivable-register', '/finance/bill', '/finance/cashier', '/finance/receipt']
    for (const [index, path] of paths.entries()) {
      await wrapper.findAll('.workflow-step')[index].trigger('click')
      expect(wrapper.findAll('.workflow-step').filter(step => step.attributes('aria-pressed') === 'true')).toHaveLength(1)
      await wrapper.get('.step-detail button').trigger('click')
      expect(push).toHaveBeenLastCalledWith({ path, query: {} })
    }
  })

  it('已授权时指标卡跳转到待收与逾期账单', async () => {
    access.paths = new Set(['/finance/bill', '/finance/overdue'])
    const wrapper = mountPage()
    await flushPromises()
    const [outstanding, overdue] = wrapper.findAll('button.kpi-tile')
    await outstanding.trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { onlyOutstanding: 'true', direction: '1' } })
    await overdue.trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/overdue', query: {} })
    await wrapper.findAll('.fee-row')[0].trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { feeType: '租金', direction: '1' } })
  })

  it('展开大屏后可以按 Escape 退出', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('.expand-button').trigger('click')
    expect(wrapper.get('.finance-overview').classes()).toContain('is-expanded')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.get('.finance-overview').classes()).not.toContain('is-expanded')
    expect(wrapper.get('.expand-button').attributes('aria-pressed')).toBe('false')
  })
})
