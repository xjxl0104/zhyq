import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerPricingDrawer from '../CustomerPricingDrawer.vue'
import CommissionBasis from '../CommissionBasis.vue'
import Position from '../Position.vue'
import Grade from '../Grade.vue'
import { mktCustomerApi, mktPositionApi, mktGradeApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({
  mktCustomerApi: { pricing: vi.fn() },
  mktPositionApi: { list: vi.fn(), update: vi.fn(), reviewNow: vi.fn() },
  mktGradeApi: { list: vi.fn(), update: vi.fn() }
}))

const customer = { id: 17, name: '品牌甲', referrerId: 30, referrerName: '推荐伙伴', assignedWarehouseName: '一号云仓' }
const configured = {
  customerId: 17, customerName: '品牌甲', pricingId: 8, configured: true, canEdit: false,
  ownerPromoterId: 40, ownerName: '定价伙伴', totalPerOrder: '0.030', ownerPerOrder: '0.006',
  allocatedPerOrder: '0.013', companyPerOrder: '0.011', remainingPerOrder: '0.011',
  beneficiaries: [
    { promoterId: 31, promoterName: '受益人甲', positionCode: 'P1', amountPerOrder: '0.004' },
    { promoterId: 32, promoterName: '受益人乙', positionCode: 'P3', amountPerOrder: '0.009' }
  ], updatedAt: '2026-09-29T15:30:00'
}
const wrappers = []
function drawer(props = {}) {
  const wrapper = mount(CustomerPricingDrawer, {
    props: { customer, modelValue: true, ...props },
    global: { plugins: [ElementPlus], stubs: { teleport: true, ElDrawer: { props: ['modelValue'], template: '<section v-if="modelValue"><slot /></section>' } } }
  })
  wrappers.push(wrapper)
  return wrapper
}
function configPage(component) {
  const wrapper = shallowMount(component, { global: { stubs: {
    ElButton: { template: '<button><slot /></button>' },
    ElTable: { template: '<div><slot /></div>' }
  } } })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mktCustomerApi.pricing.mockResolvedValue(configured)
  mktPositionApi.list.mockResolvedValue([{ id: 1, code: 'P1', name: '园区伙伴', sharePct: 50, shareMinPct: 30, lockCap: 5, promoteAmount: 1000, promoteOrders: 10, teamCounted: 1, demoteEnabled: 1 }])
  mktGradeApi.list.mockResolvedValue([{ id: 1, code: 'A', name: '重点客户', leaseCommissionMonths: 1, erpTotalRate: 50, contractBonus: 100, autoMinOrders: 1000 }])
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('管理端查看 P4 按单佣金', () => {
  it('打开后读取配置并显示真实归属、P4 本人和公司金额，保持千分位精度且不可编辑', async () => {
    const wrapper = drawer()
    await flushPromises()
    expect(mktCustomerApi.pricing).toHaveBeenCalledWith(17)
    expect(wrapper.text()).toContain('推荐伙伴')
    expect(wrapper.text()).toContain('一号云仓')
    expect(wrapper.text()).toContain('定价伙伴 · P4 #40')
    const amounts = Object.fromEntries(wrapper.findAll('.pricing-amounts > div').map(row => [row.get('dt').text(), row.get('dd').text()]))
    expect(amounts).toEqual({ 总佣金单价: '0.030', 'P4 本人金额': '0.006', 其他受益人合计: '0.013', 公司剩余额: '0.011' })
    expect(wrapper.text()).toContain('受益人甲')
    expect(wrapper.text()).toContain('0.004')
    expect(wrapper.findAll('input, select, textarea')).toHaveLength(0)
    expect(wrapper.findAll('button').some(button => /保存|提交|定价/.test(button.text()))).toBe(false)
  })

  it('未配置和零元配置分别展示，不把未配置金额当成零', async () => {
    mktCustomerApi.pricing.mockResolvedValueOnce({ ...configured, configured: false, totalPerOrder: null, ownerPerOrder: null, beneficiaries: [] })
    const wrapper = drawer()
    await flushPromises()
    expect(wrapper.text()).toContain('尚未设置按单金额')
    expect(wrapper.find('.pricing-amounts').exists()).toBe(false)
    await wrapper.setProps({ modelValue: false })
    mktCustomerApi.pricing.mockResolvedValueOnce({ ...configured, totalPerOrder: 0, ownerPerOrder: 0, allocatedPerOrder: 0, companyPerOrder: 0, beneficiaries: [] })
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(wrapper.get('.pricing-amounts').text()).toContain('0.000')
    expect(wrapper.text()).toContain('未分配给其他受益人')
  })

  it('没有 P4 归属时提示核实推荐关系，不暴露冒充定价入口', async () => {
    mktCustomerApi.pricing.mockResolvedValue({ customerId: 17, configured: false, ownerPromoterId: null, beneficiaries: [] })
    const wrapper = drawer()
    await flushPromises()
    expect(wrapper.text()).toContain('暂无可定价的 P4 归属')
    expect(wrapper.text()).toContain('请先核实客户的推荐伙伴及所属关系')
    expect(wrapper.findAll('input')).toHaveLength(0)
  })

  it('失败后可重试，关闭时不请求', async () => {
    const wrapper = drawer({ modelValue: false })
    expect(mktCustomerApi.pricing).not.toHaveBeenCalled()
    mktCustomerApi.pricing.mockRejectedValueOnce(new Error('服务暂不可用'))
    await wrapper.setProps({ modelValue: true })
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('佣金配置加载失败')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('.pricing-amounts').text()).toContain('0.030')
    expect(mktCustomerApi.pricing).toHaveBeenCalledTimes(2)
  })

  it('切换客户后旧响应不覆盖当前客户，关闭后不显示旧配置', async () => {
    let resolveFirst, resolveSecond
    mktCustomerApi.pricing.mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
      .mockImplementationOnce(() => new Promise(resolve => { resolveSecond = resolve }))
    const wrapper = drawer()
    await wrapper.setProps({ customer: { id: 18, name: '品牌乙' } })
    resolveSecond({ ...configured, customerId: 18, customerName: '品牌乙', totalPerOrder: '0.050' })
    await flushPromises()
    resolveFirst(configured)
    await flushPromises()
    expect(wrapper.text()).toContain('品牌乙')
    expect(wrapper.get('.pricing-amounts').text()).toContain('0.050')
    expect(wrapper.text()).not.toContain('品牌甲')
    await wrapper.setProps({ modelValue: false })
    expect(wrapper.text()).toBe('')
  })
})

describe('称号和评级不提交固定分成', () => {
  it('保存称号与锁定参数时不提交旧份额', async () => {
    const wrapper = configPage(Position)
    await flushPromises()
    wrapper.vm.list[0].name = ' 新称号 '
    await wrapper.findAll('button').find(button => button.text() === '保存设置').trigger('click')
    await flushPromises()
    expect(mktPositionApi.update).toHaveBeenCalledWith([{ id: 1, name: '新称号', lockCap: 5 }])
  })

  it('不提供已停用的晋降控件，明确由 P4 手动管理角色', async () => {
    const wrapper = configPage(Position)
    await flushPromises()
    expect(wrapper.findAll('button').map(button => button.text())).toEqual(['保存设置'])
    expect(wrapper.findAllComponents({ name: 'ElTableColumn' }).map(column => column.props('label')))
      .toEqual(['角色', '称号', '按单定价权限', '锁定客户上限（含预锁）'])
    expect(wrapper.text()).toContain('自动晋升与降级已停用')
    expect(wrapper.text()).toContain('P4 可在小程序中手动设置邀请体系内成员')
    expect(mktPositionApi.reviewNow).not.toHaveBeenCalled()
  })

  it('锁定客户上限拒绝小数、负数和空值，不发送无效设置', async () => {
    const wrapper = configPage(Position)
    await flushPromises()
    for (const value of [0.5, -1, null]) {
      wrapper.vm.list[0].lockCap = value
      await wrapper.findAll('button').find(button => button.text() === '保存设置').trigger('click')
      await flushPromises()
    }
    expect(mktPositionApi.update).not.toHaveBeenCalled()
    wrapper.vm.list[0].lockCap = 0
    await wrapper.findAll('button').find(button => button.text() === '保存设置').trigger('click')
    await flushPromises()
    expect(mktPositionApi.update).toHaveBeenCalledWith([{ id: 1, name: '园区伙伴', lockCap: 0 }])
  })

  it('保存评级保留租赁及签约奖业务，但不提交 ERP 固定比例', async () => {
    const wrapper = configPage(Grade)
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '保存').trigger('click')
    await flushPromises()
    expect(mktGradeApi.update).toHaveBeenCalledWith([expect.objectContaining({ leaseCommissionMonths: 1, contractBonus: 100, autoMinOrders: 1000 })])
    expect(mktGradeApi.update.mock.calls[0][0][0]).not.toHaveProperty('erpTotalRate')
  })
})

describe('佣金历史展示', () => {
  it('新流水显示每单金额与单量，不舍掉千分之一元或显示旧比例', () => {
    const wrapper = mount(CommissionBasis, { props: { row: { pricingId: 9, amountPerOrder: '0.001', orderCount: 7, sharePct: 0, diffPct: 0 } } })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain('0.001 元 / 单 × 7 单')
    expect(wrapper.text()).not.toContain('%')
  })

  it('旧流水仍按原记录显示岗位份额与级差', () => {
    const wrapper = mount(CommissionBasis, { props: { row: { sharePct: 70, diffPct: 20 } } })
    wrappers.push(wrapper)
    expect(wrapper.text()).toContain('历史比例：70% / 20%')
    expect(wrapper.text()).not.toContain('P4 自定义')
  })
})
