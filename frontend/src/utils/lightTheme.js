/**
 * The application is light-only from ver6.7 onward. Remove stale state left by
 * earlier releases before Vue mounts so the first paint cannot inherit dark CSS.
 */
export function resetToLightTheme() {
  localStorage.removeItem('zhyq_dark')
  document.documentElement.classList.remove('dark')
}
