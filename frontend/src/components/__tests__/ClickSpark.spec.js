import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ClickSpark from '../ClickSpark.vue'

describe('ClickSpark', () => {
  it('renders the overlay canvas and slot content', () => {
    const wrapper = mount(ClickSpark, {
      slots: { default: '<div class="probe">app</div>' },
      attachTo: document.body,
    })

    expect(wrapper.find('canvas.click-spark__canvas').exists()).toBe(true)
    expect(wrapper.get('.probe').text()).toBe('app')
    wrapper.unmount()
  })

  it('spawns sparkCount sparks per window click and cleans up on unmount', async () => {
    const wrapper = mount(ClickSpark, { attachTo: document.body })

    window.dispatchEvent(new MouseEvent('click', { clientX: 40, clientY: 60 }))
    expect(wrapper.vm.activeSparks()).toBe(8)

    window.dispatchEvent(new MouseEvent('click', { clientX: 10, clientY: 10 }))
    expect(wrapper.vm.activeSparks()).toBe(16)

    wrapper.unmount()
    window.dispatchEvent(new MouseEvent('click', { clientX: 5, clientY: 5 }))
    expect(wrapper.vm.activeSparks()).toBe(16)
  })
})
