import { flushPromises, mount } from '@vue/test-utils'
import { ref, reactive } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import FeeSummary from '../FeeSummary.vue'
import { feePeriodApi } from '@/api/energy'
import { leasePrintTitle } from '../leaseNoticePrint'
import { utilityPrintTitle } from '../noticePrint'

const state = vi.hoisted(() => ({ hook: null, opened: null }))
vi.mock('../feePeriod', async (original) => ({
  ...await original(),
  useFeePeriod: callback => { state.opened = callback; return state.hook }
}))
vi.mock('@/api/energy', () => ({ feePeriodApi: { notices: vi.fn() }, leaseFeeApi: { overview: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/oa', () => ({ sealApi: { list: vi.fn().mockResolvedValue([]), getPlacement: vi.fn().mockResolvedValue(null), savePlacement: vi.fn().mockResolvedValue(null) } }))
const payment = { name: 'FeeNoticePayment', props: ['modelValue', 'notice'], emits: ['saved', 'update:modelValue'], template: '<div />' }
const invoice = { name: 'NoticeInvoiceDialog', props: ['modelValue', 'notice', 'mode'], emits: ['saved', 'update:modelValue'], template: '<div />' }
beforeEach(() => {
  vi.clearAllMocks()
  state.hook = {
    month: ref('2026-11'), period: ref({ id: 3, status: 'CONFIRMED' }), loading: ref(false),
    state: reactive({ saving: false }), save: vi.fn(), reload: vi.fn(), back: vi.fn()
  }
  feePeriodApi.notices.mockResolvedValue([
    { id: 11, noticeNo: 'N11', tenantName: '未缴公司', unitName: '1层', subtotal: 130, paidAmount: 10, totalDue: 120 },
    { id: 12, noticeNo: 'N12', tenantName: '已缴公司', unitName: '2层', paymentStatus: 1, subtotal: 150, paidAmount: 50, totalDue: 100 }
  ])
})
it('已出账仍可登记缴费，保存后只更新状态并使该通知单整行变绿', async () => {
  const wrapper = mount(FeeSummary, { global: { stubs: {
    FeeNoticePayment: payment, NoticeInvoiceDialog: invoice, FeeMonthPicker: true, LeaseMonthPicker: true, LeaseFeeSummaryDetail: true, FeeBillFiles: true, FeeFilterBar: true, ElDialog: true
  } } })
  await state.opened()
  await flushPromises()
  const table = wrapper.find('.notice-table')
  const rows = () => table.findAll('.el-table__body tbody tr')
  expect(rows()).toHaveLength(2)
  expect(rows()[0].classes()).not.toContain('notice-payment-paid')
  expect(rows()[1].classes()).toContain('notice-payment-paid')
  expect(table.find('.el-table__footer-wrapper').classes()).not.toContain('notice-payment-paid')
  const pay = rows()[0].findAll('button').find(button => button.text() === '缴费')
  const adjust = rows()[0].findAll('button').find(button => button.text() === '调整')
  expect(adjust.element.disabled).toBe(true)
  expect(pay.element.disabled).toBe(false)
  await pay.trigger('click')
  const dialog = wrapper.findComponent({ name: 'FeeNoticePayment' })
  expect(dialog.props('notice')).toMatchObject({ id: 11, paidAmount: 10, totalDue: 120 })
  dialog.vm.$emit('saved', { id: 11, paymentStatus: 1 })
  await flushPromises()
  expect(rows()[0].classes()).toContain('notice-payment-paid')
  expect(rows()[0].text()).toContain('已缴费')
  expect(dialog.props('notice')).toMatchObject({ paidAmount: 10, totalDue: 120 })
  // Changing the status back preserves the original floor band and figures.
  dialog.vm.$emit('saved', { id: 11, paymentStatus: 0 })
  await flushPromises()
  expect(rows()[0].classes()).not.toContain('notice-payment-paid')
  expect(rows()[0].classes()).toContain('floor-band-0')
  expect(dialog.props('notice')).toMatchObject({ paidAmount: 10, totalDue: 120 })
  // 开票后整行从绿色升级为七彩流光
  const invBtn = rows()[1].findAll('button').find(button => button.text() === '发票')
  expect(invBtn).toBeTruthy()
  await invBtn.trigger('click')
  const invDialog = wrapper.findComponent({ name: 'NoticeInvoiceDialog' })
  expect(invDialog.props('notice')).toMatchObject({ id: 12 })
  invDialog.vm.$emit('saved', { id: 12, invoiceStatus: 1 })
  await flushPromises()
  expect(rows()[1].classes()).toContain('notice-invoice-issued')
  expect(rows()[1].text()).toContain('已开票')
  wrapper.unmount()
})

it('打印租金、物业管理费与水电垃圾费通知单时使用各自区分的默认文件名', () => {
  const p = { period: '2026-12' }
  expect(leasePrintTitle(p, [{ feeCategory: 'RENT', tenantName: '广东云山供应链科技有限公司' }]))
    .toBe('2026-12_广东云山供应链科技有限公司_租金缴费通知单')
  expect(leasePrintTitle(p, [{ feeCategory: 'PROPERTY', tenantName: '广东云山供应链科技有限公司' }]))
    .toBe('2026-12_广东云山供应链科技有限公司_物业管理费缴费通知单')
  expect(leasePrintTitle(p, [{ feeCategory: 'RENT' }, { feeCategory: 'RENT' }]))
    .toBe('2026-12_租金缴费通知单')
  expect(leasePrintTitle(p, [{ feeCategory: 'PROPERTY' }, { feeCategory: 'PROPERTY' }]))
    .toBe('2026-12_物业管理费缴费通知单')
  expect(utilityPrintTitle(p, [{ tenantName: '广东云山供应链科技有限公司' }]))
    .toBe('2026-12_广东云山供应链科技有限公司_水电垃圾费缴费通知单')
})
