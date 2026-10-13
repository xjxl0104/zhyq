import { flushPromises, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Overview from '../Overview.vue'
import request from '@/utils/request'

const { access, push } = vi.hoisted(() => ({ access: { admin: false, paths: new Set() }, push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))
vi.mock('@/stores/access', () => ({ useAccessStore: () => access }))
// Keep reportApi real: the component must use the overview endpoint and opt out of project filtering.
vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))

function overview(overrides = {}) {
  // Mirrors FinanceOverviewService.Overview, including BigDecimal values and nested record names.
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
    topDebtors: [
      { tenantRefId: 23, tenantName: '启明科技', outstanding: 40000, overdueOutstanding: 20000, billCount: 3 },
      { tenantRefId: null, tenantName: '未关联租客 · BILL-99', outstanding: 10000, overdueOutstanding: 0, billCount: 1 }
    ],
    ...overrides
  }
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const wrappers = []
function mountPage() {
  const wrapper = shallowMount(Overview, { global: { stubs: { Teleport: true } } })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(new Date('2026-10-14T02:00:00Z'))
  access.admin = false
  access.paths = new Set(['/finance/overview', '/finance/report'])
  request.get.mockResolvedValue(overview())
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.useRealTimers()
})

describe('财务总览数据与交互', () => {
  it('使用真实 overview API 契约，并分别展示账期收缴、净收款与累计待办', async () => {
    const wrapper = mountPage()
    await flushPromises()
    expect(request.get).toHaveBeenCalledWith('/finance/report/overview', {
      params: { month: '2026-10', projectId: null }
    })
    expect(wrapper.get('.scope-line').text()).toContain('全园区 · 全部项目')
    expect(wrapper.get('.scope-line').text()).toContain('2026-10-14')
    expect(wrapper.findAll('.collection-amounts .amount').map(item => item.text())).toEqual([
      '123,456.78元', '75,000.00元', '48,456.78元'
    ])
    expect(wrapper.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('60.8')
    expect(wrapper.get('.cash-line').text()).toContain('-2,000.00 元')
    expect(wrapper.get('.priorities').text()).toContain('4 张账单已逾期')
    expect(wrapper.get('.priorities').text()).toContain('32,000.00')
    expect(wrapper.get('.priority-foot').text()).toContain('未设应收日 2,000.00 元')
    expect(wrapper.findAll('.fee-row')).toHaveLength(2)
    expect(wrapper.findAll('.chart-details tbody tr')).toHaveLength(2)
    expect(wrapper.get('.debtor-list').text()).toContain('启明科技')
  })

  it('首次响应到达前显示加载状态，不渲染虚假的零金额或空账提示', async () => {
    const pending = deferred()
    request.get.mockReturnValue(pending.promise)
    const wrapper = mountPage()
    await nextTick()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('true')
    expect(wrapper.get('[role="status"]').text()).toContain('正在汇总')
    expect(wrapper.find('.summary-grid').exists()).toBe(false)
    expect(wrapper.find('.analysis-grid').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('0.00')
    expect(wrapper.text()).not.toContain('当前账单已无待收款')
    expect(wrapper.findAll('.workflow-step')).toHaveLength(4)
    pending.resolve(overview())
    await flushPromises()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('false')
    expect(wrapper.find('[role="status"]').exists()).toBe(false)
    expect(wrapper.find('.summary-grid').exists()).toBe(true)
  })

  it('请求失败时保留业务流程，允许重试并恢复真实数据', async () => {
    request.get.mockRejectedValueOnce(new Error('network unavailable')).mockResolvedValueOnce(overview())
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('财务数据暂时没有加载成功')
    expect(wrapper.find('.summary-grid').exists()).toBe(false)
    expect(wrapper.findAll('.workflow-step')).toHaveLength(4)
    await wrapper.findAll('.workflow-step')[2].trigger('click')
    expect(wrapper.get('.step-detail').text()).toContain('钱到了，再登记到对应账单')
    await wrapper.get('.error-panel button').trigger('click')
    await flushPromises()
    expect(request.get).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.collection-amounts').text()).toContain('123,456.78')
  })

  it.each(['success', 'failure'])('快速切换月份后，忽略较早请求的迟到%s响应', async outcome => {
    const older = deferred()
    const latest = deferred()
    request.get.mockResolvedValueOnce(overview()).mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = mountPage()
    await flushPromises()
    const picker = wrapper.get('input[aria-label="选择账期月份"]')
    await picker.setValue('2026-09')
    await picker.setValue('2026-08')
    expect(request.get).toHaveBeenLastCalledWith('/finance/report/overview', {
      params: { month: '2026-08', projectId: null }
    })
    latest.resolve(overview({ month: '2026-08', periodReceivable: 88888 }))
    await flushPromises()
    if (outcome === 'success') older.resolve(overview({ month: '2026-09', periodReceivable: 99999 }))
    else older.reject(new Error('stale request failed'))
    await flushPromises()
    expect(wrapper.get('.period-tag').text()).toBe('2026-08')
    expect(wrapper.get('.collection-amounts').text()).toContain('88,888.00')
    expect(wrapper.get('.collection-amounts').text()).not.toContain('99,999.00')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('false')
  })

  it('旧请求先完成时仍保持最新月份的加载状态', async () => {
    const older = deferred()
    const latest = deferred()
    request.get.mockReturnValueOnce(older.promise).mockReturnValueOnce(latest.promise)
    const wrapper = mountPage()
    await wrapper.get('input[type="month"]').setValue('2026-09')
    older.resolve(overview())
    await flushPromises()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('true')
    expect(wrapper.find('.summary-grid').exists()).toBe(false)
    latest.resolve(overview({ month: '2026-09' }))
    await flushPromises()
    expect(wrapper.get('.finance-overview').attributes('aria-busy')).toBe('false')
    expect(wrapper.get('.period-tag').text()).toBe('2026-09')
  })

  it('四步切换展示对应工作说明，并跳转到已授权的业务入口', async () => {
    access.paths = new Set(['/finance/receivable-register', '/finance/bill', '/finance/cashier', '/finance/receipt'])
    const wrapper = mountPage()
    await flushPromises()
    const expected = [
      ['这笔费用为什么要收？', '/finance/receivable-register'],
      ['这笔费用什么时候收？', '/finance/bill'],
      ['钱到了，再登记到对应账单', '/finance/cashier'],
      ['这笔收款，有哪些凭据？', '/finance/receipt']
    ]
    for (const [index, [question, path]] of expected.entries()) {
      await wrapper.findAll('.workflow-step')[index].trigger('click')
      expect(wrapper.get('.detail-copy h3').text()).toBe(question)
      const steps = wrapper.findAll('.workflow-step')
      expect(steps.filter(step => step.attributes('aria-pressed') === 'true')).toHaveLength(1)
      expect(steps[index].attributes('aria-pressed')).toBe('true')
      await wrapper.get('.step-detail button').trigger('click')
      expect(push).toHaveBeenLastCalledWith({ path, query: {} })
    }
  })

  it('没有业务页面权限时仍可读流程，但操作和数据明细入口不可跳转', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('.workflow-step')[2].trigger('click')
    const action = wrapper.get('.step-detail button')
    expect(action.text()).toContain('需开通此功能权限')
    expect(action.element.disabled).toBe(true)
    await action.trigger('click')
    for (const button of wrapper.findAll('.priority-row, .fee-row, .debtor-row')) {
      expect(button.element.disabled).toBe(true)
      await button.trigger('click')
    }
    expect(wrapper.findAll('.priority-row')[0].text()).toContain('需开通逾期账单权限')
    expect(wrapper.findAll('.priority-row')[1].text()).toContain('查看明细需开通账单权限')
    expect(wrapper.findAll('.priority-row')[2].text()).toContain('查看明细需开通账单权限')
    expect(wrapper.get('.fee-panel .panel-note').text()).toContain('查看费用明细需开通账单权限')
    expect(wrapper.get('.debtors-panel .panel-note').text()).toContain('查看租客欠款明细需开通账单权限')
    for (const button of wrapper.findAll('.priority-row, .debtor-row')) {
      expect(button.findComponent(ArrowRight).exists()).toBe(false)
    }
    expect(push).not.toHaveBeenCalled()
    expect(wrapper.findAll('.related-links button').map(button => button.text())).toEqual(['财务报表'])
    await wrapper.get('.related-links button').trigger('click')
    expect(push).toHaveBeenCalledWith({ path: '/finance/report', query: {} })
  })

  it('已授权的明细入口保留到期、费用、租客与应收方向条件', async () => {
    access.paths = new Set(['/finance/bill', '/finance/overdue'])
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('.priority-row')[1].trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { onlyDue: 'true', onlyOutstanding: 'true', direction: '1' } })
    await wrapper.findAll('.priority-row')[2].trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { feeType: '保证金', onlyOutstanding: 'true', direction: '1' } })
    await wrapper.findAll('.fee-row')[0].trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { feeType: '租金', direction: '1' } })
    await wrapper.findAll('.debtor-row')[0].trigger('click')
    expect(push).toHaveBeenLastCalledWith({ path: '/finance/bill', query: { tenantRefId: 23, onlyOutstanding: 'true', direction: '1' } })
    expect(wrapper.findAll('.priority-row')[0].findComponent(ArrowRight).exists()).toBe(true)
    expect(wrapper.findAll('.debtor-row')[0].findComponent(ArrowRight).exists()).toBe(true)
    expect(wrapper.findAll('.debtor-row')[1].element.disabled).toBe(true)
    expect(wrapper.findAll('.debtor-row')[1].findComponent(ArrowRight).exists()).toBe(false)
    expect(wrapper.findAll('.debtor-row')[1].text()).toContain('未关联租客档案')
  })

  it('点击历史柱状图切换到对应账期并重新加载', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('.chart-column')[0].trigger('click')
    await flushPromises()
    expect(wrapper.get('input[type="month"]').element.value).toBe('2026-09')
    expect(request.get).toHaveBeenLastCalledWith('/finance/report/overview', {
      params: { month: '2026-09', projectId: null }
    })
  })

  it('展开大屏后可以按 Escape 退出并同步按钮状态', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('.expand-button').trigger('click')
    expect(wrapper.get('.finance-overview').classes()).toContain('is-expanded')
    expect(wrapper.get('.expand-button').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.expand-button').text()).toContain('退出大屏')
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await nextTick()
    expect(wrapper.get('.finance-overview').classes()).not.toContain('is-expanded')
    expect(wrapper.get('.expand-button').attributes('aria-pressed')).toBe('false')
    expect(wrapper.get('.expand-button').text()).toContain('展开大屏')
  })
})
