import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Cashier from '../Cashier.vue'
import { hasOutstanding, tenantOptionBadge, tenantOptionLabel } from '../cashierModel'
import { billApi } from '@/api/finance'

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

describe('收银台页面', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    billApi.payableTenants.mockResolvedValue([
      { tenantRefId: 3, tenantName: '欠款大户', billCount: 2, owe: 300 },
      { tenantRefId: 5, tenantName: '无欠款租客', billCount: 0, owe: 0 }
    ])
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
