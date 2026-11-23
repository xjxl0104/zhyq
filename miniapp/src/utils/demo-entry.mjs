// 免登录演示入口开关守卫：默认提供轻量辅助预览链接，可通过 VITE_ENABLE_DEMO_ENTRY=false 显式关闭。
export function isDemoEntryEnabled(rawValue) {
  if (rawValue === false) return false
  const text = String(rawValue ?? '').trim().toLowerCase()
  if (text === 'false' || text === '0' || text === 'off' || text === 'no') return false
  return true
}
