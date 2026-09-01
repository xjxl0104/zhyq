import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import LiquidGlass from '../LiquidGlass.vue'

describe('LiquidGlass', () => {
  it('renders slot content inside the glass panel', () => {
    const wrapper = mount(LiquidGlass, {
      slots: { default: '<nav class="probe">menu</nav>' },
    })

    expect(wrapper.get('.liquid-glass__content .probe').text()).toBe('menu')
  })

  it('generates a displacement map for the svg filter after mount', async () => {
    const wrapper = mount(LiquidGlass, { attachTo: document.body })
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))

    const href = wrapper.get('feImage').attributes('href')
    expect(href).toMatch(/^data:image\/svg\+xml,/)
    expect(decodeURIComponent(href)).toContain('mix-blend-mode: difference')
    wrapper.unmount()
  })

  it('resolves to either the svg or the fallback material class', () => {
    const wrapper = mount(LiquidGlass)

    const classes = wrapper.classes().join(' ')
    expect(/liquid-glass--(svg|fallback)/.test(classes)).toBe(true)
  })
})
