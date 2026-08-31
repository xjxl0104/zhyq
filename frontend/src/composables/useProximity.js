import { getCurrentInstance, onBeforeUnmount } from 'vue'

// LineSidebar(React Bits)的接近动效数学:falloff 曲线 + 帧率无关指数平滑。
// 纯函数单独导出供测试;useProximity 负责 DOM 驱动(--effect CSS 变量)。
export const FALLOFF_CURVES = {
  linear: p => p,
  smooth: p => p * p * (3 - 2 * p),
  sharp: p => p * p * p,
}

const SETTLE_EPSILON = 0.0015

export function proximityTarget(distance, radius, curve = FALLOFF_CURVES.smooth) {
  if (radius <= 0) return 0
  return curve(Math.max(0, 1 - Math.abs(distance) / radius))
}

export function smoothingStep(current, target, dtSeconds, tauSeconds) {
  if (tauSeconds <= 0) return target
  const k = 1 - Math.exp(-dtSeconds / tauSeconds)
  const next = current + (target - current) * k
  return Math.abs(target - next) < SETTLE_EPSILON ? target : next
}

/**
 * proximity 驱动:指针在容器内移动时,按与各命中元素中心沿 axis 轴的距离
 * 把 0..1 的接近度写入元素的 --effect 变量,CSS 侧消费。
 * pointermove 只记录指针位置;元素查询与布局测量集中在每帧的 rAF 回调里,
 * 避免高频事件下逐事件强制回流。
 * 在组件 setup 内同步调用时自动随卸载清理,否则需手动调用返回的 dispose()。
 * @param {object} options
 * @param {import('vue').Ref} options.container 容器 ref(元素或组件)
 * @param {string} options.itemSelector 参与动效的子元素选择器
 * @param {'x'|'y'} [options.axis] 距离测量轴:'x' 横向(顶栏)/'y' 纵向(侧栏)
 * @param {() => boolean} [options.disabled] 返回 true 时动效整体停用(如 reduced motion)
 */
export function useProximity({ container, itemSelector, radius = 140, smoothing = 100, falloff = 'smooth', axis = 'x', disabled = () => false }) {
  const curve = FALLOFF_CURVES[falloff] ?? FALLOFF_CURVES.linear
  const current = new WeakMap()
  let pointerPos = null
  let rafId = null
  let last = 0

  function resolveItems() {
    const rootEl = container.value?.$el ?? container.value
    return rootEl?.querySelectorAll ? rootEl.querySelectorAll(itemSelector) : []
  }

  function runFrame(now) {
    // dt 钳制在 [0, 0.05s]:rAF 时间戳可能早于调度时刻(负值会让指数平滑发散),
    // 上限则保证切后台回来不产生跳变
    const dt = Math.min(Math.max((now - last) / 1000, 0), 0.05)
    last = now
    const tau = Math.max(smoothing, 1) / 1000
    const off = pointerPos == null || disabled()
    let moving = false
    for (const el of resolveItems()) {
      const rect = el.getBoundingClientRect()
      const center = axis === 'y' ? rect.top + rect.height / 2 : rect.left + rect.width / 2
      const target = off ? 0 : proximityTarget(pointerPos - center, radius, curve)
      const value = smoothingStep(current.get(el) || 0, target, dt, tau)
      current.set(el, value)
      el.style.setProperty('--effect', value.toFixed(4))
      if (value !== target) moving = true
    }
    rafId = moving ? requestAnimationFrame(runFrame) : null
  }

  function ensureLoop() {
    if (rafId != null) return
    last = performance.now()
    rafId = requestAnimationFrame(runFrame)
  }

  function onPointerMove(event) {
    if (disabled()) return
    pointerPos = axis === 'y' ? event.clientY : event.clientX
    ensureLoop()
  }

  function onPointerLeave() {
    pointerPos = null
    ensureLoop()
  }

  function dispose() {
    if (rafId != null) cancelAnimationFrame(rafId)
    rafId = null
    pointerPos = null
  }

  if (getCurrentInstance()) onBeforeUnmount(dispose)
  return { onPointerMove, onPointerLeave, dispose }
}
