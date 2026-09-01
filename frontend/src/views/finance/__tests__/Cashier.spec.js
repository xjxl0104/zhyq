import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Cashier from '../Cashier.vue'
import { billOwe, buildPaymentPlan, hasOutstanding, tenantOptionBadge, tenantOptionLabel } from '../cashierModel'
import { billApi, paymentApi } from '@/api/finance'

vi.mock('@/api/finance', () => ({
  billApi: {
    payableTenants: vi.fn(),
    page: vi.fn()
  },
  paymentApi: {
    pay: vi.fn(),
    list: vi.fn()
  }
}))

describe('收银台租客选项文案', () => {
  it('有欠款的租客显示笔数与金额徽标', () => {
    expect(tenantOptionBadge({ tenantRefId: 1, tenantName: '甲公司', billCount: 3, owe: 12345.5 }))
      .toBe('3 笔 · ¥12,345.50')
    expect(hasOutstanding({ owe: 12345.5 })).toBe(true)
  })

  it('没欠款的租客照样可选,徽标显示「无欠款」', () => {
    expect(tenantOptionBadge({ tenantRefId: 2, tenantName: '乙公司', billCount: 0, owe: 0 })).toBe('无欠款')
    expect(tenantOptionBadge({ tenantRefId: 2, tenantName: '乙公司', billCount: 0, owe: null })).toBe('无欠款')
    expect(hasOutstanding({ owe: 0 })).toBe(false)
  })

  it('租客 id 永远跟着名字走:名字缺失时兜底成「租客 #id」而不是裸数字', () => {
    expect(tenantOptionLabel({ tenantRefId: 9, tenantName: '丙公司' })).toBe('丙公司')
    expect(tenantOptionLabel({ tenantRefId: 9, tenantName: '' })).toBe('租客 #9')
    expect(tenantOptionLabel({})).toBe('-')
  })
})

describe('欠款口径与结算计划', () => {
  it('欠款 = 本金 + 滞纳金 - 实收:本金付清只剩滞纳金时,这笔钱收得进来', () => {
    expect(billOwe({ amount: 100, paidAmount: 100, lateFee: 5 })).toBe(5)
    expect(billOwe({ amount: 100, paidAmount: 40, lateFee: 0 })).toBe(60)
    expect(billOwe({ amount: 100, paidAmount: 120, lateFee: null })).toBe(0)
  })

  it('金额不足整单时只收部分,顺序拆单', () => {
    const rows = [
      { id: 1, amount: 100, paidAmount: 0, lateFee: 0 },
      { id: 2, amount: 50, paidAmount: 0, lateFee: 0 }
    ]
    expect(buildPaymentPlan(rows, 120)).toEqual([
      { billId: 1, amount: 100 },
      { billId: 2, amount: 20 }
    ])
  })

  it('浮点残差回归:一批两位小数账单全额结清,不会多出 0 元支付条目', () => {
    // 这组金额用浮点直接递减会剩 8.79e-14 的残差,残差再遇到下一张账单
    // 就会发出 amount=0 的请求,被后端"收款金额必须大于0"拒绝
    const amounts = [1234.56, 2345.67, 876.54, 333.33, 999.99, 111.11, 222.22, 1.01, 2.02, 3.03]
    const rows = amounts.map((a, i) => ({ id: i + 1, amount: a, paidAmount: 0, lateFee: 0 }))
    rows.push({ id: 99, amount: 500, paidAmount: 0, lateFee: 0 }) // 预算之外的第 11 张
    const total = amounts.reduce((s, a) => s + Math.round(a * 100), 0) / 100

    const plan = buildPaymentPlan(rows, total)

    expect(plan).toHaveLength(10)
    expect(plan.every(p => p.amount > 0)).toBe(true)
    expect(plan.reduce((s, p) => s + Math.round(p.amount * 100), 0) / 100).toBe(total)
  })

  it('欠款为 0 的行直接跳过,滞纳金计入拆单金额', () => {
    const rows = [
      { id: 1, amount: 100, paidAmount: 100, lateFee: 0 },  // 已结清
      { id: 2, amount: 100, paidAmount: 100, lateFee: 5 }   // 只剩滞纳金
    ]
    expect(buildPaymentPlan(rows, 50)).toEqual([{ billId: 2, amount: 5 }])
  })
})

describe('收银台页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    billApi.payableTenants.mockResolvedValue([
      { tenantRefId: 3, tenantName: '欠款大户', billCount: 2, owe: 300 },
      { tenantRefId: 5, tenantName: '无欠款租客', billCount: 0, owe: 0 }
    ])
  })

  it('批量收款中途失败:已收的不回滚,界面必刷新,重试复用同一 payNo', async () => {
    billApi.page.mockResolvedValue({ records: [], total: 0 })
    paymentApi.pay
      .mockResolvedValueOnce({ id: 1 })                 // 第 1 张成功
      .mockRejectedValueOnce(new Error('网络中断'))      // 第 2 张失败
      .mockResolvedValue({ id: 2 })                     // 重试成功
    const wrapper = mount(Cashier, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    wrapper.vm.tenantRefId = 3
    wrapper.vm.selected = [
      { id: 11, amount: 100, paidAmount: 0, lateFee: 0 },
      { id: 12, amount: 50, paidAmount: 0, lateFee: 0 }
    ]
    wrapper.vm.payAmount = 150
    await wrapper.vm.confirmPay()
    await flushPromises()

    // 失败后依然刷新了账单与租客(把服务端真实入账状态拉回来)
    expect(paymentApi.pay).toHaveBeenCalledTimes(2)
    expect(billApi.page).toHaveBeenCalled()
    expect(billApi.payableTenants.mock.calls.length).toBeGreaterThanOrEqual(2)
    const failedPayNo = paymentApi.pay.mock.calls[1][0].payNo

    // 重试:失败那张账单必须复用同一个幂等键,后端撞同 payNo 不会重复入账
    wrapper.vm.selected = [{ id: 12, amount: 50, paidAmount: 0, lateFee: 0 }]
    wrapper.vm.payAmount = 50
    await wrapper.vm.confirmPay()
    await flushPromises()

    const retryPayNo = paymentApi.pay.mock.calls[2][0].payNo
    expect(retryPayNo).toBe(failedPayNo)
  })

  it('重试金额变了必须换新幂等键:否则首笔已入账时,同键会被后端当重放吞掉还误报成功', async () => {
    billApi.page.mockResolvedValue({ records: [], total: 0 })
    paymentApi.pay
      .mockRejectedValueOnce(new Error('超时'))   // 首次 50 元失败(可能已入账)
      .mockResolvedValue({ id: 9 })               // 之后成功
    const wrapper = mount(Cashier, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    wrapper.vm.tenantRefId = 3
    wrapper.vm.selected = [{ id: 21, amount: 50, paidAmount: 0, lateFee: 0 }]
    wrapper.vm.payAmount = 50
    await wrapper.vm.confirmPay()
    await flushPromises()
    const firstPayNo = paymentApi.pay.mock.calls[0][0].payNo

    // 刷新后欠款变小(假设首笔其实入账了),按新金额重试 → 新 payNo,走后端全量校验
    wrapper.vm.selected = [{ id: 21, amount: 50, paidAmount: 30, lateFee: 0 }]
    wrapper.vm.payAmount = 20
    await wrapper.vm.confirmPay()
    await flushPromises()

    const secondCall = paymentApi.pay.mock.calls[1][0]
    expect(secondCall.amount).toBe(20)
    expect(secondCall.payNo).not.toBe(firstPayNo)
  })

  it('totalOwe 用整数分求和:浮点欠款相加不产生残差', async () => {
    const wrapper = mount(Cashier, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    // 0.1 + 0.2 直接相加是 0.30000000000000004
    wrapper.vm.selected = [
      { id: 1, amount: 0.1, paidAmount: 0, lateFee: 0 },
      { id: 2, amount: 0.2, paidAmount: 0, lateFee: 0 }
    ]
    await flushPromises()
    expect(wrapper.vm.totalOwe).toBe(0.3)
  })

  it('全部租客都渲染成下拉选项:无欠款的也在,且不做前端过滤', async () => {
    const wrapper = mount(Cashier, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(billApi.payableTenants).toHaveBeenCalledTimes(1)
    // 断言到渲染层:两条租客 el-option 都在(而不是只看内存数组),
    // 谁要是往 loadTenants 里加回 owe>0 的过滤,这里会先红。
    // 页面上还有"支付方式"下拉的 6 个选项,按租客 id(数字 value)圈出租客选项
    const tenantOptions = wrapper.findAllComponents({ name: 'ElOption' })
      .filter(o => typeof o.props('value') === 'number')
    expect(tenantOptions).toHaveLength(2)
    expect(tenantOptions.map(o => o.props('label'))).toEqual(['欠款大户', '无欠款租客'])
    expect(tenantOptions[0].text()).toContain('2 笔 · ¥300.00')
    expect(tenantOptions[1].text()).toContain('无欠款')
  })
})
