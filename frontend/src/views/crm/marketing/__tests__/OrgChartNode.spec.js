import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import OrgChartNode from '../OrgChartNode.vue'

const tree = {
  id: 4, name: '丁超', positionCode: 'P4', status: 1, customers: 0, signedCustomers: 0,
  children: [
    { id: 3, name: '张三', positionCode: 'P3', status: 1, customers: 2, signedCustomers: 1,
      children: [{ id: 2, name: '乙', positionCode: 'P2', status: 1, customers: 3, signedCustomers: 1, children: [] }] },
    { id: 5, name: '李四', positionCode: 'P3', status: 2, customers: 1, signedCustomers: 0, children: [] }
  ]
}
const mountNode = (props = {}) => mount(OrgChartNode, { props: { node: tree, ...props }, global: { stubs: { ElTag: { template: '<span class="tag"><slot /></span>' } } } })

describe('组织架构图节点', () => {
  it('递归画出 P4 → P3 → P2 每个成员的卡片和客户数', () => {
    const wrapper = mountNode()
    const cards = wrapper.findAll('.oc-card')
    expect(cards).toHaveLength(4)
    expect(cards.map(card => card.find('.oc-name').text())).toEqual(['丁超', '张三', '乙', '李四'])
    expect(cards[2].text()).toContain('客户 3 · 已签约 1')
  })

  it('选中的成员高亮，非正常状态置灰，点击向上抛出选择事件', async () => {
    const wrapper = mountNode({ selectedId: 3 })
    const cards = wrapper.findAll('.oc-card')
    expect(cards[1].classes()).toContain('is-selected')
    expect(cards[3].classes()).toContain('is-off')
    await cards[2].trigger('click')
    expect(wrapper.emitted('select').at(-1)[0]).toMatchObject({ id: 2, name: '乙' })
  })

  it('收起后不显示下级并给出下级人数，点击按钮抛出展开/收起事件', async () => {
    const wrapper = mountNode({ collapsed: { 3: true } })
    expect(wrapper.findAll('.oc-card').map(card => card.find('.oc-name').text())).toEqual(['丁超', '张三', '李四'])
    const toggles = wrapper.findAll('.oc-toggle')
    expect(toggles.map(button => button.text())).toEqual(['−', '+1'])
    await toggles[1].trigger('click')
    expect(wrapper.emitted('toggle').at(-1)[0]).toBe(3)
    expect(wrapper.emitted('select')).toBeUndefined()
  })

  it('搜索时命中的成员高亮，并忽略收起状态以便看到命中的下级', () => {
    const wrapper = mountNode({ collapsed: { 3: true }, keyword: '乙' })
    const cards = wrapper.findAll('.oc-card')
    expect(cards).toHaveLength(4)
    expect(cards.filter(card => card.classes().includes('is-hit')).map(card => card.find('.oc-name').text())).toEqual(['乙'])
  })
})
