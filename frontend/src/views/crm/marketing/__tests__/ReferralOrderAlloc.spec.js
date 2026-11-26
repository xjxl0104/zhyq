import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ReferralOrder from '../ReferralOrder.vue'
import { mktOrderApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({
  mktOrderApi: Object.fromEntries(['contractSummary', 'contractPricing', 'contractCommissions', 'saveContractPricing', 'page', 'splits', 'void'].map(key => [key, vi.fn()])),
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

describe('合同佣金分配：按每单具体金额 + 10% 平台服务费 + 0.020 元/单业务员(P0)佣金', () => {
  it('未配置时扣除 10% 服务费和 0.020 业务员佣金，按每单具体金额直接保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false,
      totalPerOrder: 0.15, totalPerOrderLocked: true, lockedTotalPerOrder: 0.15,
      salespersonCommissionPerOrder: 0.02, salespersonName: '王业务', chain
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    const members = wrapper.vm.alloc.form.members
    // 0.150 - 0.015(10%) - 0.020(P0) = 0.115 默认给 P4 本人
    expect(wrapper.vm.alloc.form.totalPerOrder).toBe(0.15)
    expect(wrapper.vm.allocMaxMember).toBe('0.115')
    expect(members.find(m => m.promoterId === 4)).toMatchObject({ isOwner: true, amountPerOrder: 0.115 })
    members.find(m => m.promoterId === 2).amountPerOrder = 0.04
    members.find(m => m.promoterId === 3).amountPerOrder = 0.025
    members.find(m => m.promoterId === 4).amountPerOrder = 0.05
    expect(wrapper.vm.allocSum).toBe('0.115')
    expect(wrapper.vm.allocOver).toBe(false)
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).toHaveBeenCalledWith({
      contractId: 5, totalPerOrder: 0.15, ownerPerOrder: 0.05,
      beneficiaries: [{ promoterId: 2, amountPerOrder: 0.04 }, { promoterId: 3, amountPerOrder: 0.025 }]
    })
  })

  it('成员分配金额合计超过扣除服务费及业务员佣金后的上限时不能保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false,
      totalPerOrder: 0.1, totalPerOrderLocked: true, lockedTotalPerOrder: 0.1,
      salespersonCommissionPerOrder: 0.02, chain
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    // 0.100 - 0.010 - 0.020 = 0.070
    expect(wrapper.vm.allocMaxMember).toBe('0.070')
    wrapper.vm.alloc.form.members.find(m => m.promoterId === 2).amountPerOrder = 0.001
    expect(wrapper.vm.allocSum).toBe('0.071')
    expect(wrapper.vm.allocOver).toBe(true)
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).not.toHaveBeenCalled()
  })

  it('已配置的每单金额直接回显；被调整层级的原上级显示为原链路成员并保留金额', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: true,
      totalPerOrder: 0.3, salespersonCommissionPerOrder: 0.02,
      chain: [
        { ...chain[0], amountPerOrder: 0.09 }, { ...chain[2], isOwner: true, amountPerOrder: 0.115 },
        { ...chain[1], former: true, amountPerOrder: 0.045 }
      ]
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    const members = wrapper.vm.alloc.form.members
    expect(members.map(m => m.amountPerOrder)).toEqual([0.09, 0.115, 0.045])
    expect(members[2].former).toBe(true)
    expect(wrapper.vm.allocSum).toBe('0.250')
    expect(wrapper.vm.allocMaxMember).toBe('0.250')
    expect(wrapper.vm.allocOver).toBe(false)
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing.mock.calls[0][0].beneficiaries).toEqual([
      { promoterId: 2, amountPerOrder: 0.09 }, { promoterId: 3, amountPerOrder: 0.045 }
    ])
  })

  it('超管可以把团队里的其他成员加进分成表，按金额配置并可移除', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false,
      totalPerOrder: 0.2, salespersonCommissionPerOrder: 0.02, chain,
      teamMembers: [{ id: 2, name: '乙', positionCode: 'P2' }, { id: 9, name: '新人', positionCode: 'P1', phoneTail: '1234' }]
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    expect(wrapper.vm.addableMembers.map(m => m.id)).toEqual([9])
    wrapper.vm.alloc.addId = 9
    wrapper.vm.addMember()
    const added = wrapper.vm.alloc.form.members.find(m => m.promoterId === 9)
    expect(added).toMatchObject({ added: true, amountPerOrder: 0, name: '新人' })
    expect(wrapper.vm.addableMembers).toEqual([])
    wrapper.vm.alloc.form.members.find(m => m.promoterId === 4).amountPerOrder = 0.12
    added.amountPerOrder = 0.04
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing.mock.calls[0][0]).toEqual({
      contractId: 5, totalPerOrder: 0.2, ownerPerOrder: 0.12, beneficiaries: [{ promoterId: 9, amountPerOrder: 0.04 }]
    })
    wrapper.vm.removeMember(added)
    expect(wrapper.vm.alloc.form.members.some(m => m.promoterId === 9)).toBe(false)
  })

  it('非超级管理员只读账号不能保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, canEdit: false, configured: false,
      readOnlyReason: '后台仅超级管理员（丁超、杨洪）可修改佣金分配，当前账号仅可查看', chain
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing).not.toHaveBeenCalled()
  })

  it('按合同打开逐笔计佣订单明细抽屉，展开分佣拆分并对未提现异常订单执行作废冲正', async () => {
    mktOrderApi.page.mockResolvedValue({
      total: 2,
      records: [
        { id: 101, sourceId: 5, sourceNo: 'YS-202611-01', sourceType: 2, commissionOrderCount: 18, serviceFee: 36, poolAmount: 5.4, status: 2 },
        { id: 102, sourceId: 5, sourceNo: 'ERP-002', sourceType: 2, commissionOrderCount: 1, serviceFee: 2, poolAmount: 0.3, status: 2 }
      ]
    })
    mktOrderApi.splits.mockImplementation(async id => id === 101
      ? [{ id: 1, promoterId: 4, positionCode: 'P4', sign: 1, amount: 5.13, status: 2, withdrawalId: null }]
      : [{ id: 2, promoterId: 4, positionCode: 'P4', sign: 1, amount: 0.285, status: 4, withdrawalId: 88 }]
    )
    mktOrderApi.void.mockResolvedValue(null)

    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openOrderDrawer({ contractId: 5, contractNo: 'CS-5', customerName: '测试客户', signMode: 1 })
    expect(mktOrderApi.page).toHaveBeenCalledWith(expect.objectContaining({ sourceId: 5, contractNo: 'CS-5' }))
    expect(wrapper.vm.orderDrawer.list).toHaveLength(2)

    await wrapper.vm.toggleSplits(wrapper.vm.orderDrawer.list[0])
    expect(mktOrderApi.splits).toHaveBeenCalledWith(101)
    expect(wrapper.vm.splitsCache[101]).toHaveLength(1)

    await wrapper.vm.openVoidDialog(wrapper.vm.orderDrawer.list[1])
    expect(wrapper.vm.voidDialog.visible).toBe(false)

    await wrapper.vm.openVoidDialog(wrapper.vm.orderDrawer.list[0])
    expect(wrapper.vm.voidDialog.visible).toBe(true)
    await wrapper.vm.submitVoidOrder()
    expect(mktOrderApi.void).not.toHaveBeenCalled()
    wrapper.vm.voidDialog.reason = '  重复汇总单冲正  '
    await wrapper.vm.submitVoidOrder()
    expect(mktOrderApi.void).toHaveBeenCalledWith(101, { reason: '重复汇总单冲正' })
  })
})
