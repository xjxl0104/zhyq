import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReferralOrder from '../ReferralOrder.vue'
import { mktOrderApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({ mktOrderApi: { page: vi.fn(), splits: vi.fn() } }))
const wrappers = []
let row
function mountPage() {
  const wrapper = shallowMount(ReferralOrder, { global: { stubs: {
    Search: true, Upload: true,
    ElTable: { template: '<div><slot /></div>' },
    ElTableColumn: { data: () => ({ row }), template: '<div><slot :row="row" /></div>' },
    ElButton: { template: '<button><slot /></button>' },
    ElDrawer: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /></section>' }
  } } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  row = { id: 7, sourceNo: 'OUT7', sourceType: 2, status: 2, pricingId: 9, commissionUnitPrice: '0.030', companyPerOrder: '0.011', commissionOrderCount: 1, poolAmount: '0.019' }
  mktOrderApi.page.mockResolvedValue({ records: [row], total: 1 })
  mktOrderApi.splits.mockResolvedValue([{ amount: '0.006' }, { amount: '0.004' }, { amount: '0.009' }])
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('按单订单佣金拆分', () => {
  it('打开拆分后分别展示总额、个人佣金池、公司余额及流水合计', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '拆分').trigger('click')
    await flushPromises()
    expect(mktOrderApi.splits).toHaveBeenCalledWith(7)
    expect(wrapper.get('.split-summary').text()).toContain('总佣金 0.030 元；伙伴佣金池 0.019 元；公司剩余额 0.011 元')
    expect(wrapper.get('.split-summary').text()).toContain('当前流水合计 0.019 元')
  })

  it('全部归公司且没有个人流水时仍可查看金额拆分', async () => {
    Object.assign(row, { companyPerOrder: '0.030', poolAmount: 0 })
    mktOrderApi.splits.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '拆分').trigger('click')
    await flushPromises()
    expect(wrapper.get('.split-summary').text()).toContain('总佣金 0.030 元；伙伴佣金池 0.000 元；公司剩余额 0.030 元')
    expect(wrapper.get('.split-summary').text()).toContain('当前流水合计 0.000 元')
  })
})
