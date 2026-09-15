import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import TwinOperationsPanel from '../TwinOperationsPanel.vue'
import { POINTS } from '../twinData'

describe('compact operating panel navigation', () => {
  it('emits a floor selection and reflects the current selection accessibly', async () => {
    const wrapper = mount(TwinOperationsPanel, { props: { side: 'left', floor: 3 } })
    const floorButtons = wrapper.findAll('.ops-floor-row')
    expect(floorButtons).toHaveLength(7)
    expect(floorButtons.find(button => button.text().startsWith('3F')).attributes('aria-pressed')).toBe('true')
    await floorButtons.find(button => button.text().startsWith('5F')).trigger('click')
    expect(wrapper.emitted('select-floor')).toEqual([[5]])
    wrapper.unmount()
  })

  it('distinguishes the demo occupancy mean from the sourced park facts', () => {
    const wrapper = mount(TwinOperationsPanel, { props: { side: 'left' } })
    const occupancy = wrapper.get('.ops-occupancy-metric').text()
    expect(occupancy).toContain('82.4%')
    expect(occupancy).toContain('7 层平均出租率 · 演示')
    expect(wrapper.get('.ops-reference').attributes('aria-label')).toBe('来源：招商资料，园区建筑面积8.2万平方米，19台货梯')
    wrapper.unmount()
  })

  it('opens the existing six modules through the corresponding business buttons', async () => {
    const left = mount(TwinOperationsPanel, { props: { side: 'left' } })
    const right = mount(TwinOperationsPanel, { props: { side: 'right' } })
    for (const button of left.findAll('.ops-link')) await button.trigger('click')
    for (const button of right.findAll('.ops-device-row')) await button.trigger('click')
    await right.get('.ops-energy .ops-link').trigger('click')
    expect(left.emitted('open-module')).toEqual([['park'], ['contract'], ['property']])
    expect(right.emitted('open-module')).toEqual([['camera'], ['fire'], ['energy']])
    left.unmount()
    right.unmount()
  })

  it('locates the original demo point so its detailed module association is preserved', async () => {
    const wrapper = mount(TwinOperationsPanel, { props: { side: 'right', floor: 3 } })
    const firePoint = POINTS.find(point => point.module === 'fire')
    const pendingButtons = wrapper.findAll('.ops-pending-row')
    expect(pendingButtons[0].text()).toContain('HYD-032')
    await pendingButtons[0].trigger('click')
    expect(wrapper.emitted('select-point')[0][0]).toBe(firePoint)
    wrapper.unmount()
  })
})
