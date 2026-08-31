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

async function mountNav({ activePath = '/dashboard', reducedMotion = false, withActive = true, collapsed = false } = {}) {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: reducedMotion,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
  })

  const wrapper = mount(GooeyNav, {
    props: { activePath, collapsed },
    slots: {
      default: withActive
        ? '<div class="el-menu"><button class="el-menu-item is-active">工作台</button><button class="el-menu-item other">其他</button><button class="el-menu-item is-disabled">禁用</button></div>'
        : '<div class="el-menu"><button class="el-menu-item">工作台</button></div>',
    },
  })

  wrapper.element.getBoundingClientRect = () => rootRect
  if (withActive) {
    wrapper.find('.el-menu-item').element.getBoundingClientRect = () => activeRect
    wrapper.find('.other').element.getBoundingClientRect = () => ({ ...activeRect, top: 160, bottom: 204 })
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

  it('moves on route changes and bounds transient particles to twelve', async () => {
    const wrapper = await mountNav()
    activeRect = {
      left: 22, top: 132, width: 180, height: 44, right: 202, bottom: 176,
    }

    await wrapper.setProps({ activePath: '/building/project' })
    await flushPromises()
    resizeObservers[0].callback()
    await settle()

    expect(wrapper.attributes('style')).toContain('--gooey-y: 112px')
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(12)
    expect(wrapper.find('.gooey-nav__particle').element.style.getPropertyValue('--particle-origin-x')).toBe('148.8px')

    vi.advanceTimersByTime(1100)
    await flushPromises()
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(0)
  })

  it('follows hovered items and returns to the active leaf on exit', async () => {
    const wrapper = await mountNav()
    await wrapper.find('.other').trigger('pointerover')
    await settle()
    expect(wrapper.attributes('style')).toContain('--gooey-y: 140px')
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(0)
    await wrapper.trigger('pointerleave')
    await settle()
    expect(wrapper.attributes('style')).toContain('--gooey-y: 48px')
    wrapper.unmount()
  })

  it('keeps even the smallest burst dot visible through the goo filter', async () => {
    const wrapper = await mountNav()
    await wrapper.find('.is-active').trigger('click')
    await settle()
    const sigma = Number(wrapper.find('feGaussianBlur').attributes('stdDeviation'))
    const matrix = wrapper.find('feColorMatrix').attributes('values').trim().split(/\s+/).map(Number)
    const diameter = Math.min(...wrapper.findAll('.gooey-nav__particle').map(particle =>
      parseFloat(particle.element.style.getPropertyValue('--particle-size'))))
    // Gaussian-blurred disk alpha at its center, at the animation's 95% opacity peak.
    const centerAlpha = (1 - Math.exp(-((diameter / 2) ** 2) / (2 * sigma ** 2))) * 0.95
    expect(centerAlpha * matrix[18] + matrix[19]).toBeGreaterThanOrEqual(1)
    wrapper.unmount()
  })

  it('follows keyboard focus without moving selection and ignores disabled items', async () => {
    const wrapper = await mountNav()
    await wrapper.find('.other').trigger('focusin')
    await settle()
    expect(wrapper.attributes('style')).toContain('--gooey-y: 140px')
    expect(wrapper.find('.is-active').text()).toBe('工作台')
    await wrapper.find('.other').trigger('focusout')
    await wrapper.find('.is-disabled').trigger('pointerover')
    await settle()
    expect(wrapper.attributes('style')).toContain('--gooey-y: 48px')
    wrapper.unmount()
  })

  it('centers particles in a collapsed menu and restarts feedback for repeated clicks', async () => {
    const wrapper = await mountNav({ collapsed: true })
    await wrapper.find('.is-active').trigger('click')
    await settle()
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(12)
    expect(wrapper.find('.gooey-nav__particle').element.style.getPropertyValue('--particle-origin-x')).toBe('102px')
    await wrapper.find('.is-active').trigger('click')
    await settle()
    expect(wrapper.findAll('.gooey-nav__particle')).toHaveLength(12)
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
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
