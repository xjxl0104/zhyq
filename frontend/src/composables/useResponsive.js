import { onScopeDispose, readonly, ref } from 'vue'

// Keep this breakpoint aligned with styles/responsive.scss.
export const MOBILE_QUERY = '(max-width: 767px), (max-width: 1023px) and (max-height: 500px)'

export function useResponsive() {
  const media = typeof window !== 'undefined' ? window.matchMedia(MOBILE_QUERY) : null
  const isMobile = ref(media?.matches ?? false)
  const update = (event) => { isMobile.value = event.matches }
  media?.addEventListener('change', update)
  onScopeDispose(() => media?.removeEventListener('change', update))
  return { isMobile: readonly(isMobile) }
}
