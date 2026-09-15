import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import WarehouseScene from '../WarehouseScene.vue'

const state = vi.hoisted(() => ({ render: vi.fn(), frames: new Map(), nextFrame: 0 }))
vi.mock('three', async importOriginal => {
  const THREE = await importOriginal()
  return { ...THREE,
    WebGLRenderer: class {
      domElement = document.createElement('canvas')
      shadowMap = {}
      setPixelRatio() {} setSize() {} dispose() {}
      render = state.render
    },
    PMREMGenerator: class { fromScene() { return { texture: null, dispose() {} } } dispose() {} },
  }
})
vi.mock('three/addons/controls/OrbitControls.js', async () => {
  const { Vector3 } = await import('three')
  return { OrbitControls: class { target = new Vector3(); addEventListener() {} update() { return false } dispose() {} } }
})
vi.mock('../warehouseAsset', async () => {
  const { Group } = await import('three')
  return { loadWarehouse: async () => ({ root: new Group(), floors: [], update: () => false, setState() {}, dispose() {}, pointPosition: () => ({ project: () => ({ x: 0, y: 0, z: 0 }) }) }) }
})
vi.mock('../sceneWeather', () => ({ createSceneWeather: () => ({ setWeather() {}, update() {}, dispose() {} }) }))
vi.mock('../sceneRendering', () => ({ createSceneRendering: () => ({ render: state.render, resize() {}, needsRender: () => false, dispose() {} }) }))

function framesUntil(time) {
  for (let now = 0; now <= time; now += 16) {
    const pending = [...state.frames.values()]
    state.frames.clear()
    pending.forEach(callback => callback(performance.now() + now))
  }
}
afterEach(() => { vi.unstubAllGlobals(); state.frames.clear(); state.render.mockClear() })

describe('warehouse render demand', () => {
  it('stops drawing a settled sunny scene, redraws weather changes without continuous frames', async () => {
    vi.stubGlobal('requestAnimationFrame', callback => { const id = ++state.nextFrame; state.frames.set(id, callback); return id })
    vi.stubGlobal('cancelAnimationFrame', id => state.frames.delete(id))
    const wrapper = mount(WarehouseScene)
    await flushPromises()
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
    wrapper.unmount()
    expect(state.frames.size).toBe(0)
  })
})
