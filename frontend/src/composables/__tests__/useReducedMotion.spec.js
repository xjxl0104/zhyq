import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { useReducedMotion } from '../useReducedMotion'

const Host = defineComponent({
  setup() {
    return { reduced: useReducedMotion() }
  },
  template: '<div>{{ reduced }}</div>',
})

afterEach(() => vi.unstubAllGlobals())

describe('useReducedMotion', () => {
  it('reflects an active reduce preference on mount', async () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }))
    const wrapper = mount(Host)
    await flushPromises()
    expect(wrapper.text()).toBe('true')
    wrapper.unmount()
  })

  it('defaults to false and unsubscribes on unmount', () => {
    const removeEventListener = vi.fn()
    vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener }))
    const wrapper = mount(Host)
    expect(wrapper.text()).toBe('false')
    wrapper.unmount()
    expect(removeEventListener).toHaveBeenCalled()
  })
})
