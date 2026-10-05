import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { authApi } from '@/api/system'
import { allowedNavigationPaths, visibleNavigation } from '@/utils/navigationAccess'

export const useAccessStore = defineStore('access', () => {
  const admin = ref(false)
  const menus = ref([])
  const loadedToken = ref('')
  const paths = computed(() => admin.value ? new Set() : allowedNavigationPaths(menus.value))
  const navigation = computed(() => visibleNavigation(paths.value, admin.value))

  async function load() {
    const token = localStorage.getItem('zhyq_token') || ''
    if (!token) { reset(); return }
    if (loadedToken.value === token) return
    const access = await authApi.myAccess()
    admin.value = !!access.admin
    menus.value = access.menus || []
    loadedToken.value = token
  }

  function reset() {
    admin.value = false
    menus.value = []
    loadedToken.value = ''
  }
  return { admin, menus, paths, navigation, load, reset }
})
