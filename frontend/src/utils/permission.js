// Visibility only. Every action is independently authorized by the backend.
export function hasPermission(permission) {
  try {
    const token = localStorage.getItem('zhyq_token') || ''
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(payload))
    return Array.isArray(claims.auth) && claims.auth.includes(permission)
  } catch (_) { return false }
}
