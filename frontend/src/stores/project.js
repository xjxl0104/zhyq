import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { projectApi } from '@/api/building'

export const PROJECT_STORAGE_KEY = 'zhyq_project_id'

export const useProjectStore = defineStore('project', () => {
  const currentProjectId = ref(null)
  const projects = ref([])
  const loaded = ref(false)

  const currentProject = computed(
    () => projects.value.find((p) => p.id === currentProjectId.value) || null
  )

  async function init() {
    const list = (await projectApi.list()) || []
    projects.value = Array.isArray(list) ? list : []
    loaded.value = true
    // 确定当前项目:优先 localStorage,校验仍在列表内,否则取第一个
    const savedRaw = localStorage.getItem(PROJECT_STORAGE_KEY)
    const saved = savedRaw != null ? Number(savedRaw) : null
    const exists = saved != null && projects.value.some((p) => p.id === saved)
    if (exists) {
      currentProjectId.value = saved
    } else {
      currentProjectId.value = projects.value.length ? projects.value[0].id : null
    }
    persist()
  }

  function switchTo(id) {
    if (id === currentProjectId.value) return false
    currentProjectId.value = id
    persist()
    return true
  }

  function reset() {
    currentProjectId.value = null
    projects.value = []
    loaded.value = false
    localStorage.removeItem(PROJECT_STORAGE_KEY)
  }

  function persist() {
    if (currentProjectId.value == null) localStorage.removeItem(PROJECT_STORAGE_KEY)
    else localStorage.setItem(PROJECT_STORAGE_KEY, String(currentProjectId.value))
  }

  return { currentProjectId, projects, loaded, currentProject, init, switchTo, reset }
})
