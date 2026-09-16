// Opt-in local laboratory telemetry. No globals, production timers or GPU stalls.
export function createSceneDemoMetrics(renderer, report) {
  const previousAutoReset = renderer.info.autoReset
  renderer.info.autoReset = false
  const gl = renderer.getContext()
  const timer = gl.getExtension('EXT_disjoint_timer_query_webgl2')
  const pending = [], gpuTimes = []
  let activeQuery = null, started = 0, frameStart = 0, drawCount = 0, cpuTotal = 0
  let lastCalls = 0, lastTriangles = 0, disposed = false
  const visibilityTarget = typeof document !== 'undefined' ? document : null
  function clearGpuQueries() {
    if (activeQuery) {
      gl.endQuery(timer.TIME_ELAPSED_EXT); gl.deleteQuery(activeQuery); activeQuery = null
    }
    pending.forEach(query => gl.deleteQuery(query)); pending.length = 0
    gpuTimes.length = 0
  }
  function resetSession() {
    if (disposed) return
    started = 0; frameStart = 0; drawCount = 0; cpuTotal = 0
    lastCalls = 0; lastTriangles = 0
    clearGpuQueries()
    // Invalidates the toolbar's current sample as well as this timing window.
    report(null)
  }
  visibilityTarget?.addEventListener('visibilitychange', resetSession)
  function pollGpu() {
    if (!timer) return
    // Even unavailable queries become invalid during a disjoint interval.
    if (gl.getParameter(timer.GPU_DISJOINT_EXT)) { clearGpuQueries(); return }
    for (let i = pending.length - 1; i >= 0; i--) {
      const query = pending[i]
      if (!gl.getQueryParameter(query, gl.QUERY_RESULT_AVAILABLE)) continue
      gpuTimes.push(gl.getQueryParameter(query, gl.QUERY_RESULT) / 1e6)
      if (gpuTimes.length > 90) gpuTimes.shift()
      gl.deleteQuery(query); pending.splice(i, 1)
    }
  }
  return {
    beforeRender() {
      renderer.info.reset()
      frameStart = performance.now()
      if (timer && pending.length < 8) {
        activeQuery = gl.createQuery()
        if (activeQuery) gl.beginQuery(timer.TIME_ELAPSED_EXT, activeQuery)
      }
    },
    afterRender() {
      if (activeQuery) {
        gl.endQuery(timer.TIME_ELAPSED_EXT)
        pending.push(activeQuery); activeQuery = null
      }
      cpuTotal += performance.now() - frameStart
      drawCount++
      lastCalls = renderer.info.render.calls
      lastTriangles = renderer.info.render.triangles
    },
    tick(time, state) {
      if (disposed) return
      pollGpu()
      if (!started) started = time
      const elapsed = time - started
      if (elapsed < 1000) return
      const sortedGpu = [...gpuTimes].sort((a, b) => a - b)
      report({
        ...state, fps: drawCount * 1000 / elapsed, frames: drawCount,
        submissionMs: drawCount ? cpuTotal / drawCount : 0,
        gpuMs: sortedGpu.length ? sortedGpu[Math.floor(sortedGpu.length / 2)] : null,
        calls: lastCalls, triangles: lastTriangles,
        geometries: renderer.info.memory.geometries, textures: renderer.info.memory.textures,
        programs: renderer.info.programs?.length || 0,
      })
      started = time; drawCount = 0; cpuTotal = 0
    },
    dispose() {
      if (disposed) return
      disposed = true
      visibilityTarget?.removeEventListener('visibilitychange', resetSession)
      clearGpuQueries()
      renderer.info.autoReset = previousAutoReset
      // Navigation and project switches unmount the scene but keep the lab bar.
      report(null)
    },
  }
}
