import { useAccessStore } from '@/stores/access'

// Visibility only. Every action is independently authorized by the backend.
export function hasPermission(permission) {
  if (typeof permission !== 'string' || !permission) return false
  try {
    const access = useAccessStore()
    return access.admin || access.menus.some(menu => menu.perm === permission)
  } catch (_) { return false }
}
