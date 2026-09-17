import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage, ElMessageBox } from 'element-plus'

import Bill from '../Bill.vue'
import { billApi, paymentApi } from '@/api/finance'

vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/finance/bill', query: {} }),
  useRouter: () => ({ replace: vi.fn() })
}))
vi.mock('@/api/finance', () => ({
  billApi: { page: vi.fn(), stats: vi.fn(), capabilities: vi.fn() },
  paymentApi: { writeOff: vi.fn() },
  invoiceApi: {}
}))
vi.mock('@/api/tenant', () => ({ tenantApi: { list: vi.fn().mockResolvedValue([]) } }))

const row = {
  id: 17, code: 'BILL-017', tenantName: '测试租客', tenantRefId: 3,
  agreementNo: 'AG-017', feeType: '租金', source: '应收登记表',
  amount: 0, paidAmount: 0, lateFee: 0, status: 3,
  periodStart: '2026-09-01', periodEnd: '2026-09-30', dueDate: '2026-09-10', overdueDays: 7
}
const wrappers = []
function mountPage() {
  const wrapper = shallowMount(Bill, {
    global: {
      stubs: {
        MobileRecordList: false,
        ElPopconfirm: { template: '<div><slot name="reference" /></div>' },
        Search: true, Money: true
      }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn()
  })))
  billApi.page.mockResolvedValue({ records: [{ ...row }], total: 1 })
  billApi.stats.mockResolvedValue({})
  billApi.capabilities.mockResolvedValue({ writeOff: true, paymentVoid: true })
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('账单手机版与主线业务一致', () => {
  it('零欠款且有权限时显示核销，并复用核销确认与接口', async () => {
    vi.spyOn(ElMessageBox, 'prompt').mockResolvedValue({ value: '免租期' })
    vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
    paymentApi.writeOff.mockResolvedValue({})
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('[aria-label="登记账单 BILL-017 收款"]').exists()).toBe(false)
    await wrapper.get('[aria-label="核销账单 BILL-017"]').trigger('click')
    await flushPromises()

    expect(ElMessageBox.prompt).toHaveBeenCalled()
    expect(paymentApi.writeOff).toHaveBeenCalledWith({ billId: 17, remark: '免租期' })
  })

  it.each([
    ['没有核销权限', { writeOff: false }, {}],
    ['已结清', { writeOff: true }, { status: 5 }],
    ['本金待收', { writeOff: true }, { amount: 100, paidAmount: 50 }],
    ['仅剩滞纳金', { writeOff: true }, { amount: 100, paidAmount: 100, lateFee: 5 }]
  ])('%s时保留主线收款入口，不显示核销', async (_label, capabilities, overrides) => {
    billApi.capabilities.mockResolvedValue(capabilities)
    billApi.page.mockResolvedValue({ records: [{ ...row, ...overrides }], total: 1 })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('[aria-label="核销账单 BILL-017"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="登记账单 BILL-017 收款"]').exists()).toBe(true)
  })

  it('卡片保留账单关联字段、金额、序号及完整操作', async () => {
    billApi.page.mockResolvedValue({ records: [{ ...row, amount: 12345.6, paidAmount: 1000 }], total: 1 })
    const wrapper = mountPage()
    await flushPromises()
    const fields = Object.fromEntries(wrapper.findAll('.mobile-record-field').map(field => [
      field.get('dt').text(), field.get('dd').text()
    ]))
    expect(fields).toMatchObject({
      序号: '1', 账单号: 'BILL-017', 对方租客: '测试租客', 协议编号: 'AG-017',
      费用类型: '租金', 来源: '应收登记表', 应收: '¥12,345.60', 实收: '¥1,000.00',
      账期: '2026-09-01 ~ 2026-09-30', 应收日: '2026-09-10', 逾期天数: '7', 状态: '待收付'
    })
    expect(wrapper.find('[aria-label="查看账单 BILL-017 详情"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="为账单 BILL-017 开票"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="删除账单 BILL-017"]').exists()).toBe(true)
  })
})
