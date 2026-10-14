import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ContractCollectionPanel from '../ContractCollectionPanel.vue'
import OverviewChart from '../OverviewChart.vue'

function row(key, tenantName, contractCode, state, extra = {}) {
  return {
    key, contractId: contractCode ? Number(key.split(':')[1]) : null, contractCode, tenantRefId: 1, tenantName, contractStatus: 5, state,
    receivable: 100, received: 40, outstanding: 60, overdueOutstanding: state === 'overdue' ? 60 : 0, dueOutstanding: 0,
    notDueOutstanding: state === 'overdue' ? 0 : 60, billCount: 1, fees: [], ...extra
  }
}
function data(contracts) {
  const count = state => contracts.filter(item => item.state === state).length
  return {
    asOfDate: '2026-10-14', aging: [], contracts,
    summary: { contractCount: contracts.length, unlinkedGroups: 0, settled: count('settled'), partial: count('partial'), unpaid: count('unpaid'), overdue: count('overdue'), unbilled: count('unbilled'), receivable: 0, received: 0, outstanding: 0, overdueOutstanding: 0 }
  }
}
function mountPanel(props) {
  return shallowMount(ContractCollectionPanel, { props: { canBill: true, canContract: true, ...props } })
}
const names = wrapper => wrapper.findAll('.contract-id strong').map(item => item.text())

describe('合同收款全景面板', () => {
  it('加载中与失败状态不渲染列表，失败可重试', async () => {
    expect(mountPanel({ data: null }).get('[role="status"]').text()).toContain('正在按合同汇总')
    const failed = mountPanel({ data: null, error: true })
    await failed.get('[role="alert"] button').trigger('click')
    expect(failed.emitted('retry')).toHaveLength(1)
  })

  it('按状态页签、环形图点击与关键词筛选合同', async () => {
    const wrapper = mountPanel({ data: data([
      row('contract:1', '启明科技', 'HT-001', 'overdue'), row('contract:2', '青禾食品', 'HT-002', 'partial'),
      row('contract:3', '北辰文创', 'HT-003', 'unbilled', { billCount: 0, receivable: 0, received: 0, outstanding: 0 })
    ]) })
    expect(names(wrapper)).toEqual(['启明科技', '青禾食品', '北辰文创'])
    await wrapper.findAll('.state-tabs button').find(button => button.text().startsWith('逾期')).trigger('click')
    expect(names(wrapper)).toEqual(['启明科技'])
    wrapper.getComponent(OverviewChart).vm.$emit('select', { data: { key: 'overdue' } })
    await wrapper.vm.$nextTick()
    expect(names(wrapper)).toHaveLength(3)
    await wrapper.get('input[type="search"]').setValue('ht-002')
    expect(names(wrapper)).toEqual(['青禾食品'])
    await wrapper.get('input[type="search"]').setValue('不存在')
    expect(wrapper.text()).toContain('没有符合条件的合同')
  })

  it('未出账合同显示提示且没有账单入口', async () => {
    const wrapper = mountPanel({ data: data([row('contract:3', '北辰文创', 'HT-003', 'unbilled', { billCount: 0, receivable: 0, received: 0, outstanding: 0 })]) })
    expect(wrapper.text()).toContain('尚未生成账单')
    await wrapper.get('.contract-row').trigger('click')
    expect(wrapper.get('.contract-detail').text()).toContain('还没有账单')
    expect(wrapper.findAll('.solid-button')).toHaveLength(0)
  })

  it('展开合同显示款项明细，并发出账单与合同详情跳转', async () => {
    const contract = row('contract:1', '启明科技', 'HT-001', 'overdue', {
      fees: [{ feeType: '租金', receivable: 80, received: 20, outstanding: 60, overdueOutstanding: 60, billCount: 2 }, { feeType: '保证金', receivable: 20, received: 20, outstanding: 0, overdueOutstanding: 0, billCount: 1 }]
    })
    const wrapper = mountPanel({ data: data([contract]) })
    expect(wrapper.find('.contract-detail').exists()).toBe(false)
    await wrapper.get('.contract-row').trigger('click')
    expect(wrapper.get('.contract-row').attributes('aria-expanded')).toBe('true')
    const rows = wrapper.findAll('.fee-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('租金')
    expect(rows[0].text()).toContain('逾期 60.00')
    await wrapper.get('.solid-button').trigger('click')
    await wrapper.get('.ghost-button').trigger('click')
    expect(wrapper.emitted('open-bills')[0][0].key).toBe('contract:1')
    expect(wrapper.emitted('open-contract')[0][0].contractId).toBe(1)
  })

  it('没有账单权限时按钮禁用，没有合同权限时不显示合同详情', async () => {
    const wrapper = mountPanel({ canBill: false, canContract: false, data: data([row('contract:1', '启明科技', 'HT-001', 'partial')]) })
    await wrapper.get('.contract-row').trigger('click')
    expect(wrapper.get('.solid-button').element.disabled).toBe(true)
    expect(wrapper.get('.solid-button').text()).toContain('需开通账单权限')
    expect(wrapper.find('.ghost-button').exists()).toBe(false)
  })

  it('超过 8 份时分批显示，筛选后重置数量', async () => {
    const contracts = Array.from({ length: 11 }, (_, index) => row(`contract:${index + 1}`, `租客${index + 1}`, `HT-${index + 1}`, 'partial'))
    const wrapper = mountPanel({ data: data(contracts) })
    expect(names(wrapper)).toHaveLength(8)
    await wrapper.get('.more-button').trigger('click')
    expect(names(wrapper)).toHaveLength(11)
    expect(wrapper.find('.more-button').exists()).toBe(false)
    await wrapper.get('input[type="search"]').setValue('租客')
    expect(names(wrapper)).toHaveLength(8)
  })
})
