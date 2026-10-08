import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseScene from '../WarehouseScene.vue'

const state = vi.hoisted(() => ({
  render: vi.fn(), frames: new Map(), nextFrame: 0, renderer: null, modelRoot: null, controls: null, loadGate: null,
  animeRendering: vi.fn(), originalRendering: vi.fn(), applyStyle: vi.fn(), weather: vi.fn(),
  styleDispose: vi.fn(), weatherDispose: vi.fn(), modelDispose: vi.fn(),
  prepareExport: vi.fn(), exportSnapshot: vi.fn(async () => new ArrayBuffer(8)),
}))
vi.mock('three', async importOriginal => {
  const THREE = await importOriginal()
  return { ...THREE,
    WebGLRenderer: class {
      domElement = document.createElement('canvas')
      shadowMap = {}
      constructor() { state.renderer = this }
      setPixelRatio() {} setSize() {} dispose() {}
      render = state.render
    },
    PMREMGenerator: class { fromScene() { return { texture: null, dispose() {} } } dispose() {} },
  }
})
vi.mock('three/addons/controls/OrbitControls.js', async () => {
  const { Vector3 } = await import('three')
  return { OrbitControls: class { target = new Vector3(); constructor() { state.controls = this } addEventListener() {} update() { return false } dispose() {} } }
})
vi.mock('../warehouseAsset', async () => {
  const { Group } = await import('three')
  return { loadWarehouse: async () => { await state.loadGate; return { root: (state.modelRoot = new Group()), floors: [], update: () => false, setState() {}, dispose: state.modelDispose, localPointPosition: () => ({ project: () => ({ x: 0, y: 0, z: 0 }) }), pointPosition: () => ({ project: () => ({ x: 0, y: 0, z: 0 }) }) } } }
})
vi.mock('../sceneWeather', () => ({ createSceneWeather: options => {
  state.weather(options)
  return { setWeather() {}, update() {}, dispose: state.weatherDispose }
} }))
vi.mock('../sceneRendering', () => {
  const pipeline = () => ({ render: state.render, resize() {}, needsRender: () => false, dispose() {} })
  return {
    createSceneRendering: (...args) => { state.originalRendering(...args); return pipeline() },
    createAnimeSceneRendering: (...args) => { state.animeRendering(...args); return pipeline() },
  }
})
vi.mock('../animeSceneStyle.js', () => ({ createAnimeSceneStyle: scene => {
  state.applyStyle(scene)
  return { dispose: state.styleDispose, prepareExport: state.prepareExport }
} }))
vi.mock('three/addons/exporters/GLTFExporter.js', () => ({ GLTFExporter: class { parseAsync = state.exportSnapshot } }))

function framesUntil(time) {
  for (let now = 0; now <= time; now += 16) {
    const pending = [...state.frames.values()]
    state.frames.clear()
    pending.forEach(callback => callback(performance.now() + now))
  }
}
function mountScene(options) {
  vi.stubGlobal('requestAnimationFrame', callback => { const id = ++state.nextFrame; state.frames.set(id, callback); return id })
  vi.stubGlobal('cancelAnimationFrame', id => state.frames.delete(id))
  return mount(WarehouseScene, options)
}
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); vi.restoreAllMocks(); vi.clearAllMocks(); state.frames.clear(); state.loadGate = null })

describe('warehouse render demand', () => {
  it('keeps the overview clear with many work orders and only shows the selected interior point', async () => {
    const orders = Array.from({ length: 20 }, (_, index) => ({ id: 'workorder-' + index, orderId: index + 1, floor: 4, module: 'property', name: 'Order ' + index, localPosition: [index, .45, 0] }))
    const wrapper = mountScene({ props: { workOrders: orders } })
    await flushPromises()
    expect(wrapper.findAll('.scene-pin')).toHaveLength(0)
    await wrapper.setProps({ selectedPoint: orders[0], floor: 4 })
    expect(wrapper.findAll('.scene-pin')).toHaveLength(0)
    await wrapper.setProps({ mode: 'interior' })
    expect(wrapper.findAll('.scene-pin')).toHaveLength(1)
    expect(wrapper.get('.scene-pin').text()).toContain('Order 0')
    await wrapper.setProps({ selectedPoint: orders[1] })
    expect(wrapper.findAll('.scene-pin')).toHaveLength(1)
    expect(wrapper.get('.scene-pin').text()).toContain('Order 1')
    await wrapper.setProps({ selectedPoint: null })
    expect(wrapper.findAll('.scene-pin')).toHaveLength(0)
    await wrapper.setProps({ mode: 'exploded', selectedPoint: orders[1] })
    expect(wrapper.findAll('.scene-pin')).toHaveLength(0)
    wrapper.unmount()
  })

  it('cancels a queued order location when the user closes it before the model loads', async () => {
    let finishLoading
    state.loadGate = new Promise(resolve => { finishLoading = resolve })
    const selected = { id: 'workorder-1', orderId: 1, floor: 4, module: 'property', localPosition: [20, .45, -10] }
    const wrapper = mountScene({ props: { mode: 'interior', floor: 4, selectedPoint: selected } })
    wrapper.vm.focusPoint(selected)
    await wrapper.setProps({ selectedPoint: null, mode: 'exterior', floor: null })
    finishLoading(); await flushPromises(); framesUntil(2000)
    state.controls.target.toArray().forEach((value, index) => expect(value).toBeCloseTo([0, 24, 0][index]))
    expect(wrapper.findAll('.scene-pin')).toHaveLength(0)
    wrapper.unmount()
  })

  it('honours an order selected before loading and flies again to another point on the same floor', async () => {
    const wrapper = mountScene({ props: { mode: 'interior', floor: 4 } })
    wrapper.vm.focusPoint({ floor: 4, localPosition: [20, .45, -10] })
    await flushPromises()
    framesUntil(2000)
    const camera = state.animeRendering.mock.calls[0][2]
    state.controls.target.toArray().forEach((value, index) => expect(value).toBeCloseTo([12, .67, -6][index]))
    camera.position.toArray().forEach((value, index) => expect(value).toBeCloseTo([37, 36.67, 27][index]))
    wrapper.vm.focusPoint({ floor: 4, localPosition: [-40, .45, 18] })
    framesUntil(2000)
    expect(state.controls.target.x).toBeCloseTo(-24)
    expect(state.controls.target.z).toBeCloseTo(10.8)
    expect(camera.position.x).toBeCloseTo(1)
    expect(camera.position.z).toBeCloseTo(43.8)
    await wrapper.setProps({ viewpoint: 'plan' })
    wrapper.vm.focusPoint({ floor: 4, localPosition: [20, .45, -10] })
    framesUntil(2000)
    camera.position.toArray().forEach((value, index) => expect(value).toBeCloseTo([12, 45.67, -5.9][index]))
    wrapper.unmount()
  })

  it('uses anime rendering by default, idles when settled, and redraws weather and zoom changes', async () => {
    const wrapper = mountScene()
    await flushPromises()
    expect(wrapper.emitted('ready')).toHaveLength(1)
    expect(state.applyStyle).toHaveBeenCalledTimes(1)
    expect(state.animeRendering).toHaveBeenCalledTimes(1)
    expect(state.originalRendering).not.toHaveBeenCalled()
    expect(state.weather).toHaveBeenCalledWith(expect.objectContaining({ style: 'anime' }))
    framesUntil(2000)
    state.render.mockClear()
    framesUntil(500)
    expect(state.render.mock.calls.length).toBe(0)
    await wrapper.setProps({ weather: 'night' })
    framesUntil(32)
    expect(state.render).toHaveBeenCalledTimes(1)
    state.render.mockClear()
    await wrapper.setProps({ weather: 'sunny' })
    framesUntil(48)
    expect(state.render).toHaveBeenCalledTimes(1)
    // A zoom changes tree LOD without changing warehouse geometry. Its shadow
    // cache must update once, then a settled camera must become idle again.
    state.render.mockClear()
    state.renderer.shadowMap.needsUpdate = false
    wrapper.vm.zoom(3)
    framesUntil(32)
    expect(state.render).toHaveBeenCalledTimes(1)
    expect(state.renderer.shadowMap.needsUpdate).toBe(true)
    state.render.mockClear()
    state.renderer.shadowMap.needsUpdate = false
    framesUntil(500)
    expect(state.render).not.toHaveBeenCalled()
    expect(state.renderer.shadowMap.needsUpdate).toBe(false)
    wrapper.unmount()
    expect(state.frames.size).toBe(0)
    expect(state.styleDispose).toHaveBeenCalledTimes(1)
    expect(state.weatherDispose.mock.invocationCallOrder[0]).toBeLessThan(state.styleDispose.mock.invocationCallOrder[0])
    expect(state.styleDispose.mock.invocationCallOrder[0]).toBeLessThan(state.modelDispose.mock.invocationCallOrder[0])
  })

  it('keeps the previous renderer available only through the explicit development comparison profile', async () => {
    const wrapper = mountScene({ global: { provide: { 'warehouse-scene-demo': { style: 'original' } } } })
    await flushPromises()
    expect(wrapper.emitted('ready')).toHaveLength(1)
    expect(state.originalRendering).toHaveBeenCalledTimes(1)
    expect(state.animeRendering).not.toHaveBeenCalled()
    expect(state.applyStyle).not.toHaveBeenCalled()
    expect(state.weather).toHaveBeenCalledWith(expect.objectContaining({ style: 'realistic' }))
    wrapper.unmount()
    expect(state.styleDispose).not.toHaveBeenCalled()
    expect(state.frames.size).toBe(0)
  })

  it('prepares a cloned export with portable materials without changing the live anime model', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const createObjectURL = vi.fn(() => 'blob:warehouse-export')
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL: vi.fn() })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const wrapper = mountScene()
    await flushPromises()
    await wrapper.vm.exportModel()
    const snapshot = state.prepareExport.mock.calls[0][0]
    expect(snapshot).not.toBe(state.modelRoot)
    expect(snapshot.isGroup).toBe(true)
    expect(state.exportSnapshot).toHaveBeenCalledWith(snapshot, { binary: true, onlyVisible: true })
    expect(state.prepareExport.mock.invocationCallOrder[0]).toBeLessThan(state.exportSnapshot.mock.invocationCallOrder[0])
    expect(createObjectURL).toHaveBeenCalledTimes(1)
    expect(click).toHaveBeenCalledTimes(1)
    vi.runOnlyPendingTimers()
    wrapper.unmount()
  })
})
