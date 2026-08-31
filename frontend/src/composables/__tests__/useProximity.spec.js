import { describe, expect, it } from 'vitest'
import { FALLOFF_CURVES, proximityTarget, smoothingStep, useProximity } from '../useProximity'

describe('FALLOFF_CURVES', () => {
  it('keeps linear as the identity curve', () => {
    expect(FALLOFF_CURVES.linear(0)).toBe(0)
    expect(FALLOFF_CURVES.linear(0.3)).toBeCloseTo(0.3, 10)
    expect(FALLOFF_CURVES.linear(1)).toBe(1)
  })

  it('maps smooth as smoothstep with a soft start', () => {
    expect(FALLOFF_CURVES.smooth(0)).toBe(0)
    expect(FALLOFF_CURVES.smooth(1)).toBe(1)
    expect(FALLOFF_CURVES.smooth(0.5)).toBeCloseTo(0.5, 10)
    // p²(3-2p) at 0.25 = 0.15625,慢于线性起步
    expect(FALLOFF_CURVES.smooth(0.25)).toBeCloseTo(0.15625, 10)
  })

  it('maps sharp as a cubic curve', () => {
    expect(FALLOFF_CURVES.sharp(0.5)).toBeCloseTo(0.125, 10)
    expect(FALLOFF_CURVES.sharp(1)).toBe(1)
  })
})

describe('proximityTarget', () => {
  it('returns full effect at zero distance', () => {
    expect(proximityTarget(0, 140)).toBe(1)
  })

  it('returns zero at or beyond the radius', () => {
    expect(proximityTarget(140, 140)).toBe(0)
    expect(proximityTarget(500, 140)).toBe(0)
  })

  it('interpolates with the given curve', () => {
    expect(proximityTarget(70, 140, FALLOFF_CURVES.linear)).toBeCloseTo(0.5, 10)
  })

  it('treats distance as an absolute value', () => {
    expect(proximityTarget(-70, 140, FALLOFF_CURVES.linear)).toBeCloseTo(0.5, 10)
  })

  it('defaults to the smooth curve', () => {
    expect(proximityTarget(70, 140)).toBeCloseTo(FALLOFF_CURVES.smooth(0.5), 10)
  })
})

describe('smoothingStep', () => {
  it('eases the current value toward the target with exponential smoothing', () => {
    // k = 1 - e^(-dt/tau) = 1 - e^(-0.16)
    const expected = 1 - Math.exp(-0.016 / 0.1)
    expect(smoothingStep(0, 1, 0.016, 0.1)).toBeCloseTo(expected, 10)
  })

  it('is frame-rate independent: two half steps equal one full step', () => {
    const twoSteps = smoothingStep(smoothingStep(0, 1, 0.016, 0.1), 1, 0.016, 0.1)
    const oneStep = smoothingStep(0, 1, 0.032, 0.1)
    expect(twoSteps).toBeCloseTo(oneStep, 6)
  })

  it('snaps to the target once within the settle threshold', () => {
    expect(smoothingStep(0.999, 1, 0.016, 0.1)).toBe(1)
    expect(smoothingStep(0.001, 0, 0.016, 0.1)).toBe(0)
  })

  it('jumps straight to the target when tau is zero or negative', () => {
    expect(smoothingStep(0, 1, 0.016, 0)).toBe(1)
    expect(smoothingStep(0.4, 0, 0.016, -5)).toBe(0)
  })
})

describe('useProximity DOM driver', () => {
  const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve))

  function makeHost(itemCount = 2) {
    const host = document.createElement('div')
    host.innerHTML = '<span class="item"></span>'.repeat(itemCount)
    document.body.appendChild(host)
    return host
  }

  it('writes --effect on items after pointer movement and decays after leave', async () => {
    const host = makeHost()
    // smoothing 1ms → 一帧内收敛,断言稳定值;jsdom 的 rect 全 0,指针 0 → 距离 0 → 满效果
    const prox = useProximity({ container: { value: host }, itemSelector: '.item', radius: 100, smoothing: 1 })
    prox.onPointerMove({ clientX: 0 })
    await nextFrame()
    await nextFrame()
    const item = host.querySelector('.item')
    expect(item.style.getPropertyValue('--effect')).toBe('1.0000')

    prox.onPointerLeave()
    await nextFrame()
    await nextFrame()
    expect(item.style.getPropertyValue('--effect')).toBe('0.0000')
    prox.dispose()
    host.remove()
  })

  it('measures along the y axis when configured', async () => {
    const host = makeHost(1)
    const prox = useProximity({ container: { value: host }, itemSelector: '.item', radius: 100, smoothing: 1, axis: 'y' })
    const item = host.querySelector('.item')

    // x 很远但 y 距离为 0(jsdom rect 全 0)→ y 轴模式下应满效果
    prox.onPointerMove({ clientX: 999, clientY: 0 })
    await nextFrame()
    await nextFrame()
    expect(item.style.getPropertyValue('--effect')).toBe('1.0000')

    // y 拉远 → 归零
    prox.onPointerMove({ clientX: 0, clientY: 500 })
    await nextFrame()
    await nextFrame()
    expect(item.style.getPropertyValue('--effect')).toBe('0.0000')
    prox.dispose()
    host.remove()
  })

  it('stays inert while the disabled option reports true', async () => {
    const host = makeHost(1)
    const prox = useProximity({ container: { value: host }, itemSelector: '.item', smoothing: 1, disabled: () => true })
    prox.onPointerMove({ clientX: 0 })
    await nextFrame()
    await nextFrame()
    expect(host.querySelector('.item').style.getPropertyValue('--effect')).toBe('')
    prox.dispose()
    host.remove()
  })

  it('refresh restarts the settled loop so a disabled flip decays stale values', async () => {
    const host = makeHost(1)
    let disabled = false
    const prox = useProximity({ container: { value: host }, itemSelector: '.item', radius: 100, smoothing: 1, disabled: () => disabled })
    const item = host.querySelector('.item')

    prox.onPointerMove({ clientX: 0 })
    await nextFrame()
    await nextFrame()
    expect(item.style.getPropertyValue('--effect')).toBe('1.0000')

    // 循环已停,禁用后残留 1.0;refresh 重新驱动一帧衰减归零
    disabled = true
    prox.refresh()
    await nextFrame()
    await nextFrame()
    expect(item.style.getPropertyValue('--effect')).toBe('0.0000')
    prox.dispose()
    host.remove()
  })

  it('cancels pending frames on dispose', async () => {
    const host = makeHost(1)
    const prox = useProximity({ container: { value: host }, itemSelector: '.item', smoothing: 1 })
    prox.onPointerMove({ clientX: 0 })
    prox.dispose()
    await nextFrame()
    await nextFrame()
    expect(host.querySelector('.item').style.getPropertyValue('--effect')).toBe('')
    host.remove()
  })
})
