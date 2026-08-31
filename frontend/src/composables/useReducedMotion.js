import { onBeforeUnmount, onMounted, ref } from 'vue'

// prefers-reduced-motion 的统一响应式入口,Layout 与 GooeyNav 共用。
// 仅限组件 setup 内同步调用(依赖生命周期钩子做订阅清理)。
export function useReducedMotion() {
  const reduced = ref(false)
  let query
  const onChange = e => { reduced.value = e.matches }

  onMounted(() => {
    query = window.matchMedia('(prefers-reduced-motion: reduce)')
    reduced.value = query.matches
    query.addEventListener?.('change', onChange)
  })
  onBeforeUnmount(() => {
    query?.removeEventListener?.('change', onChange)
    query = undefined
  })

  return reduced
}
