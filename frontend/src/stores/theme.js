import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

const STORAGE_KEY = 'zhyq_dark'

/**
 * 主题 store：一键暗色。
 * isDark 持久化到 localStorage，切换时 toggle <html> 的 .dark 类，
 * 同一套 CSS 变量（styles/index.scss 的 :root 与 html.dark）驱动全站亮/暗。
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem(STORAGE_KEY) === '1')

  function apply() {
    document.documentElement.classList.toggle('dark', isDark.value)
  }

  function toggle() {
    isDark.value = !isDark.value
  }

  watch(
    isDark,
    (v) => {
      localStorage.setItem(STORAGE_KEY, v ? '1' : '0')
      apply()
    },
    { immediate: true }
  )

  return { isDark, toggle, apply }
})
