import { mount } from '@vue/test-utils'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { useResponsive } from '../useResponsive'

afterEach(() => vi.unstubAllGlobals())

describe('responsive viewport state', () => {
  it('uses the current viewport immediately, follows changes and releases the listener', () => {
    const listeners = new Set()
    const media = {
      matches: true,
      addEventListener: (event, callback) => listeners.add(callback),
      removeEventListener: (event, callback) => listeners.delete(callback),
    }
    vi.stubGlobal('matchMedia', vi.fn(() => media))
    let state
    const wrapper = mount({ setup() { state = useResponsive(); return () => null } })
    expect(state.isMobile.value).toBe(true)
    for (const callback of listeners) callback({ matches: false })
    expect(state.isMobile.value).toBe(false)
    wrapper.unmount()
    expect(listeners.size).toBe(0)
  })
})
