// @vitest-environment node
import { afterEach, it, expect, vi } from 'vitest'
import { createSceneDemoMetrics } from '../sceneDemoMetrics.js'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

function makeRenderer(gl = { getExtension: () => null }) {
  return {
    info: { autoReset: true, reset: vi.fn(), render: { calls: 5, triangles: 300 }, memory: { geometries: 2, textures: 1 }, programs: [] },
    getContext: () => gl,
  }
}

it('reports actual render frames and explicit unsupported GPU timing, then zero during idle', () => {
  const report = vi.fn()
  const renderer = makeRenderer()
  const metrics = createSceneDemoMetrics(renderer, report)
  metrics.tick(1000, { style: 'anime' })
  const clock = vi.spyOn(performance, 'now').mockReturnValueOnce(10).mockReturnValueOnce(12)
  metrics.beforeRender(); metrics.afterRender(); metrics.tick(2000, { style: 'anime' })
  expect(report).toHaveBeenLastCalledWith(expect.objectContaining({ fps: 1, frames: 1, submissionMs: 2, gpuMs: null, calls: 5, triangles: 300 }))
  metrics.tick(3000, { style: 'anime' })
  expect(report).toHaveBeenLastCalledWith(expect.objectContaining({ fps: 0, frames: 0, submissionMs: 0, gpuMs: null }))
  metrics.dispose(); metrics.dispose()
  expect(renderer.info.autoReset).toBe(true)
  clock.mockRestore()
})

it('invalidates a hidden session and starts a fresh timing window after visibility returns', () => {
  const document = new EventTarget(), report = vi.fn(), renderer = makeRenderer()
  vi.stubGlobal('document', document)
  const metrics = createSceneDemoMetrics(renderer, report)
  vi.spyOn(performance, 'now').mockReturnValue(1)
  metrics.tick(1000, { style: 'anime' })
  metrics.beforeRender(); metrics.afterRender()
  document.dispatchEvent(new Event('visibilitychange'))
  expect(report).toHaveBeenLastCalledWith(null)
  document.dispatchEvent(new Event('visibilitychange'))
  metrics.tick(20000, { style: 'anime' })
  expect(report).toHaveBeenCalledTimes(2)
  metrics.beforeRender(); metrics.afterRender()
  metrics.tick(21000, { style: 'anime' })
  expect(report).toHaveBeenLastCalledWith(expect.objectContaining({ fps: 1, frames: 1, gpuMs: null }))
  metrics.dispose()
  expect(report).toHaveBeenLastCalledWith(null)
  const callsAfterDispose = report.mock.calls.length
  document.dispatchEvent(new Event('visibilitychange'))
  metrics.tick(22000, { style: 'anime' }); metrics.dispose()
  expect(report).toHaveBeenCalledTimes(callsAfterDispose)
  expect(renderer.info.autoReset).toBe(true)
})

it('discards all outstanding GPU queries during disjoint, including unavailable results', () => {
  const timer = { GPU_DISJOINT_EXT: 1, TIME_ELAPSED_EXT: 2 }
  let disjoint = false, available = false, nextQuery = 0
  const gl = {
    QUERY_RESULT_AVAILABLE: 3, QUERY_RESULT: 4,
    getExtension: () => timer,
    getParameter: () => disjoint,
    createQuery: () => ({ id: ++nextQuery }), beginQuery: vi.fn(), endQuery: vi.fn(), deleteQuery: vi.fn(),
    getQueryParameter: vi.fn((query, parameter) => parameter === 3 ? available : 8e6),
  }
  const report = vi.fn(), metrics = createSceneDemoMetrics(makeRenderer(gl), report)
  vi.spyOn(performance, 'now').mockReturnValue(1)
  metrics.tick(1000, {})
  metrics.beforeRender(); metrics.afterRender()
  metrics.beforeRender(); metrics.afterRender()
  disjoint = true
  metrics.tick(1100, {})
  expect(gl.deleteQuery.mock.calls.map(([query]) => query.id)).toEqual([1, 2])
  disjoint = false; available = true
  metrics.tick(2000, {})
  expect(report).toHaveBeenLastCalledWith(expect.objectContaining({ gpuMs: null }))
  expect(gl.getQueryParameter).not.toHaveBeenCalled()
  metrics.beforeRender(); metrics.afterRender()
  metrics.tick(3000, {})
  expect(report).toHaveBeenLastCalledWith(expect.objectContaining({ gpuMs: 8 }))
  metrics.dispose()
  expect(gl.deleteQuery).toHaveBeenCalledTimes(3)
})
