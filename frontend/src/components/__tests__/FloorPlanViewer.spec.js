import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import FloorPlanViewer from '../FloorPlanViewer.vue'
import { fileApi } from '@/api/file'
vi.mock('@/api/file', () => ({ fileApi: { download: vi.fn() } }))
const wrappers = []
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
    expect(wrapper.get('.plan-canvas').attributes('style')).toContain('150%')
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
})
