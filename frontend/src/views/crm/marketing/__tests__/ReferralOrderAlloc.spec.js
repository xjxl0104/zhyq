import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReferralOrder from '../ReferralOrder.vue'
import { mktOrderApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({
  mktOrderApi: Object.fromEntries(['contractSummary', 'contractPricing', 'contractCommissions', 'saveContractPricing'].map(key => [key, vi.fn()])),
  mktPromoterApi: { page: vi.fn().mockResolvedValue({ records: [] }) },
  mktCustomerApi: { page: vi.fn().mockResolvedValue({ records: [] }) },
  mktWarehouseApi: { online: vi.fn().mockResolvedValue([]) }
}))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

const chain = [
  { promoterId: 2, name: '乙', positionCode: 'P2', status: 1, isOwner: false, amountPerOrder: null },
  { promoterId: 3, name: '原P3', positionCode: 'P3', status: 1, isOwner: false, amountPerOrder: null },
  { promoterId: 4, name: '丁超', positionCode: 'P4', status: 1, isOwner: false, amountPerOrder: null }
]
const wrappers = []
function mount() {
  const wrapper = shallowMount(ReferralOrder, { global: { directives: { loading: {} }, stubs: { Search: true } } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  vi.clearAllMocks()
  mktOrderApi.contractSummary.mockResolvedValue({ records: [], total: 0 })
  mktOrderApi.contractCommissions.mockResolvedValue([])
  mktOrderApi.saveContractPricing.mockImplementation(async data => ({ ...data, canEdit: true, chain }))
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('合同佣金分配：平台固定 5% + 按比例分成', () => {
  it('未配置时默认把 95% 给 P4 本人，保存按比例折算金额并向下取到 0.001 元', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({ contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false, totalPerOrder: null, chain })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    const members = wrapper.vm.alloc.form.members
    expect(members.find(m => m.promoterId === 4)).toMatchObject({ isOwner: true, pct: 95 })
    wrapper.vm.alloc.form.totalPerOrder = 0.3
    members.find(m => m.promoterId === 2).pct = 30
    members.find(m => m.promoterId === 3).pct = 20
    members.find(m => m.promoterId === 4).pct = 45
    expect(wrapper.vm.allocOver).toBe(false)
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).toHaveBeenCalledWith({
      contractId: 5, totalPerOrder: 0.3, ownerPerOrder: 0.135,
      beneficiaries: [{ promoterId: 2, amountPerOrder: 0.09 }, { promoterId: 3, amountPerOrder: 0.06 }]
    })
  })

  it('成员分成合计超过 95% 不能保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({ contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false, chain })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    wrapper.vm.alloc.form.totalPerOrder = 0.3
    wrapper.vm.alloc.form.members.find(m => m.promoterId === 2).pct = 10
    expect(wrapper.vm.allocPct).toBe('105.00')
    expect(wrapper.vm.allocOver).toBe(true)
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).not.toHaveBeenCalled()
  })

  it('已配置的金额还原为比例；被调整层级的原上级显示为原链路成员并保留分成', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: true, totalPerOrder: 0.3,
      chain: [
        { ...chain[0], amountPerOrder: 0.09 }, { ...chain[2], isOwner: true, amountPerOrder: 0.15 },
        { ...chain[1], former: true, amountPerOrder: 0.045 }
      ]
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    const members = wrapper.vm.alloc.form.members
    expect(members.map(m => m.pct)).toEqual([30, 50, 15])
    expect(members[2].former).toBe(true)
    expect(wrapper.vm.allocPct).toBe('95.00')
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing.mock.calls[0][0].beneficiaries).toEqual([
      { promoterId: 2, amountPerOrder: 0.09 }, { promoterId: 3, amountPerOrder: 0.045 }
    ])
  })

  it('只读账号不能保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({ contractId: 5, contractNo: 'CS-1', partnerId: 2, canEdit: false, configured: false, readOnlyReason: '当前后台账号未关联伙伴档案', chain })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).not.toHaveBeenCalled()
  })
})
