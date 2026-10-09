import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FloorPlanViewer from '../FloorPlanViewer.vue'
import { fileApi } from '@/api/file'
vi.mock('@/api/file', () => ({ fileApi: { download: vi.fn() } }))
const wrappers = []
// jsdom has no PointerEvent and MouseEvent coordinates are read-only, so gestures use plain events.
const fire = (target, type, init) => target.dispatchEvent(Object.assign(new Event(type, { bubbles: true, cancelable: true }), init))
const create = props => {
  const wrapper = mount(FloorPlanViewer, { props, global: { stubs: { ElButton: { template: '<button><slot /></button>' }, ElEmpty: true, ElAlert: true }, directives: { loading: () => {} } } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.stubGlobal('URL', { createObjectURL: vi.fn(() => 'blob:plan'), revokeObjectURL: vi.fn() })
  fileApi.download.mockResolvedValue({ data: new Blob(['plan'], { type: 'image/png' }) })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.unstubAllGlobals() })
describe('floor-plan point selection', () => {
  it('uses rendered image proportions and keeps the point correct after zoom', async () => {
    const wrapper = create({ fileId: 5, editable: true, point: { x: 0.25, y: 0.75 } })
    await flushPromises()
    const canvas = wrapper.get('.plan-canvas')
    canvas.element.getBoundingClientRect = () => ({ left: 100, top: 50, width: 800, height: 400 })
    await canvas.trigger('click', { clientX: 300, clientY: 350 })
    expect(wrapper.emitted('update:point')[0][0]).toEqual({ x: 0.25, y: 0.75 })
    await wrapper.get('[aria-label="放大平面图"]').trigger('click')
    expect(wrapper.get('.plan-canvas').attributes('style')).toContain('scale(1.5)')
    expect(wrapper.get('.plan-marker').attributes('style')).toContain('left: 25%')
    expect(wrapper.get('.plan-marker').attributes('style')).toContain('top: 75%')
  })
  it('prevents old image responses from replacing a newly selected floor', async () => {
    let finishOld
    fileApi.download.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const wrapper = create({ fileId: 1 })
    await wrapper.setProps({ fileId: 2 })
    await flushPromises()
    const calls = URL.createObjectURL.mock.calls.length
    finishOld({ data: new Blob(['old'], { type: 'image/png' }) })
    await flushPromises()
    expect(URL.createObjectURL).toHaveBeenCalledTimes(calls)
  })
  it('does not edit saved work orders in the details viewer', async () => {
    const wrapper = create({ fileId: 5, editable: false, point: { x: 0, y: 1 } })
    await flushPromises()
    await wrapper.get('.plan-canvas').trigger('click', { clientX: 20, clientY: 20 })
    expect(wrapper.emitted('update:point')).toBeUndefined()
    expect(wrapper.get('.plan-marker').attributes('style')).toContain('left: 0%')
  })
  it('pans by dragging without marking a point, and keeps the drawing inside the viewport', async () => {
    const wrapper = create({ fileId: 5, editable: true, point: { x: 0.5, y: 0.5 } })
    await flushPromises()
    const view = wrapper.get('.plan-scroll'), canvas = wrapper.get('.plan-canvas')
    Object.defineProperties(view.element, { clientWidth: { value: 400 }, clientHeight: { value: 200 } })
    Object.defineProperties(canvas.element, { offsetWidth: { value: 400 }, offsetHeight: { value: 200 } })
    view.element.getBoundingClientRect = () => ({ left: 0, top: 0, width: 400, height: 200 })
    fire(view.element, 'wheel', { deltaY: -500, clientX: 0, clientY: 0 }); await wrapper.vm.$nextTick()
    const zoomed = Number(canvas.attributes('style').match(/scale\(([\d.]+)\)/)[1])
    expect(zoomed).toBeGreaterThan(1.5)
    expect(canvas.attributes('style')).toContain('translate(0px, 0px)')
    fire(view.element, 'pointerdown', { pointerId: 1, pointerType: 'mouse', button: 0, clientX: 200, clientY: 100 })
    fire(window, 'pointermove', { pointerId: 1, clientX: 150, clientY: 80 })
    fire(window, 'pointerup', { pointerId: 1, clientX: 150, clientY: 80 })
    await canvas.trigger('click', { clientX: 150, clientY: 80 })
    expect(wrapper.emitted('update:point')).toBeUndefined()
    await wrapper.vm.$nextTick()
    expect(canvas.attributes('style')).toContain('translate(-50px, -20px)')
    fire(view.element, 'pointerdown', { pointerId: 2, pointerType: 'mouse', button: 0, clientX: 0, clientY: 0 })
    fire(window, 'pointermove', { pointerId: 2, clientX: 900, clientY: 900 })
    fire(window, 'pointerup', { pointerId: 2, clientX: 900, clientY: 900 })
    await wrapper.vm.$nextTick()
    expect(canvas.attributes('style')).toContain('translate(0px, 0px)')
    await wrapper.get('[aria-label="复位平面图"]').trigger('click')
    expect(canvas.attributes('style')).toContain('scale(1)')
  })
})
