import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GooeyNav from '../GooeyNav.vue'

const rootRect = {
  left: 10, top: 20, width: 224, height: 500, right: 234, bottom: 520,
}

let activeRect
let resizeObservers

class TestResizeObserver {
  constructor(callback) {
    this.callback = callback
    this.observe = vi.fn()
    this.unobserve = vi.fn()
    this.disconnect = vi.fn()
    resizeObservers.push(this)
  }
}

async function settle() {
  await flushPromises()
  await vi.advanceTimersByTimeAsync(4)
  await flushPromises()
}

async function mountNav({ activePath = '/dashboard', reducedMotion = false, withActive = true } = {}) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: reducedMotion,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })

  const wrapper = mount(GooeyNav, {
    props: { activePath },
    slots: {
      default: withActive
        ? '<div class="el-menu"><button class="el-menu-item is-active">工作台</button></div>'
        : '<div class="el-menu"><button class="el-menu-item">工作台</button></div>',
    },
  })

  wrapper.element.getBoundingClientRect = () => rootRect
  if (withActive) {
    wrapper.find('.el-menu-item').element.getBoundingClientRect = () => activeRect
  }
  window.dispatchEvent(new Event('resize'))
  await settle()
  return wrapper
}

describe('GooeyNav', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    activeRect = {
      left: 22, top: 68, width: 180, height: 44, right: 202, bottom: 112,
    }
    resizeObservers = []
    vi.stubGlobal('ResizeObserver', TestResizeObserver)
    vi.stubGlobal('requestAnimationFrame', (callback) => setTimeout(callback, 0))
    vi.stubGlobal('cancelAnimationFrame', (id) => clearTimeout(id))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('positions a static indicator under the initial active leaf', async () => {
    const wrapper = await mountNav()

    expect(wrapper.classes()).toContain('gooey-nav--ready')
    expect(wrapper.attributes('style')).toContain('--gooey-x: 12px')
    expect(wrapper.attributes('style')).toContain('--gooey-y: 48px')
    expect(wrapper.attributes('style')).toContain('--gooey-width: 180px')
    expect(wrapper.attributes('style')).toContain('--gooey-height: 44px')
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(0)
  })

  it('moves on route changes and bounds transient particles to eight', async () => {
    const wrapper = await mountNav()
    activeRect = {
      left: 22, top: 132, width: 180, height: 44, right: 202, bottom: 176,
    }

    await wrapper.setProps({ activePath: '/building/project' })
    await settle()

    expect(wrapper.attributes('style')).toContain('--gooey-y: 112px')
    expect(wrapper.findAll('.gooey-nav__particle').length).toBeGreaterThan(0)
    expect(wrapper.findAll('.gooey-nav__particle').length).toBeLessThanOrEqual(8)

    vi.advanceTimersByTime(900)
    await flushPromises()
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(0)
  })

  it('keeps selection feedback static when reduced motion is requested', async () => {
    const wrapper = await mountNav({ reducedMotion: true })

    await wrapper.setProps({ activePath: '/building/project' })
    await settle()

    expect(wrapper.classes()).toContain('gooey-nav--reduced')
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(0)
  })

  it('hides only the effect when no active leaf can be measured', async () => {
    const wrapper = await mountNav({ withActive: false })

    expect(wrapper.classes()).not.toContain('gooey-nav--ready')
    expect(wrapper.text()).toContain('工作台')
  })

  it('disconnects observers and removes media listeners on unmount', async () => {
    const wrapper = await mountNav()
    const media = window.matchMedia.mock.results[0].value

    wrapper.unmount()

    expect(resizeObservers[0].disconnect).toHaveBeenCalledOnce()
    expect(media.removeEventListener).toHaveBeenCalledWith('change', expect.any(Function))
    expect(vi.getTimerCount()).toBe(0)
  })
})
