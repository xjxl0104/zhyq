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

  it('P4 可以把自己团队里的其他成员加进分成表，可移除，保存时一并提交', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({
      contractId: 5, contractNo: 'CS-1', partnerId: 2, ownerPromoterId: 4, canEdit: true, configured: false, chain,
      teamMembers: [{ id: 2, name: '乙', positionCode: 'P2' }, { id: 9, name: '新人', positionCode: 'P1', phoneTail: '1234' }]
    })
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.openAlloc({ contractId: 5 }); await flushPromises()
    // 已在链路表里的成员(乙)不会再出现在可添加列表
    expect(wrapper.vm.addableMembers.map(m => m.id)).toEqual([9])
    wrapper.vm.alloc.addId = 9
    wrapper.vm.addMember()
    const added = wrapper.vm.alloc.form.members.find(m => m.promoterId === 9)
    expect(added).toMatchObject({ added: true, pct: 0, name: '新人' })
    expect(wrapper.vm.addableMembers).toEqual([])
    wrapper.vm.alloc.form.totalPerOrder = 0.2
    wrapper.vm.alloc.form.members.find(m => m.promoterId === 4).pct = 60
    added.pct = 20
    await wrapper.vm.saveAlloc()
    expect(mktOrderApi.saveContractPricing.mock.calls[0][0]).toEqual({
      contractId: 5, totalPerOrder: 0.2, ownerPerOrder: 0.12, beneficiaries: [{ promoterId: 9, amountPerOrder: 0.04 }]
    })
    wrapper.vm.removeMember(added)
    expect(wrapper.vm.alloc.form.members.some(m => m.promoterId === 9)).toBe(false)
  })

  it('只读账号不能保存', async () => {
    mktOrderApi.contractPricing.mockResolvedValue({ contractId: 5, contractNo: 'CS-1', partnerId: 2, canEdit: false, configured: false, readOnlyReason: '当前后台账号未关联伙伴档案', chain })
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

    // 已提现关联订单不允许进入作废确认弹窗
    await wrapper.vm.openVoidDialog(wrapper.vm.orderDrawer.list[1])
    expect(wrapper.vm.voidDialog.visible).toBe(false)

    // 未提现订单必填原因后作废冲正
    await wrapper.vm.openVoidDialog(wrapper.vm.orderDrawer.list[0])
    expect(wrapper.vm.voidDialog.visible).toBe(true)
    await wrapper.vm.submitVoidOrder()
    expect(mktOrderApi.void).not.toHaveBeenCalled()
    wrapper.vm.voidDialog.reason = '  重复汇总单冲正  '
    await wrapper.vm.submitVoidOrder()
    expect(mktOrderApi.void).toHaveBeenCalledWith(101, { reason: '重复汇总单冲正' })
  })
})
