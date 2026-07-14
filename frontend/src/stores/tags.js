import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'zhyq_tags'

/**
 * 标签页 store：多标签导航 + KeepAlive 缓存集合。
 * 每个 tab 以 fullPath 为唯一键（解决 Feedback/Check/DeviceCategory 等
 * 一组件多路由的缓存串号问题）。首页固定不可关闭。
 */
export const useTagsStore = defineStore('tags', () => {
  const HOME = { fullPath: '/dashboard', path: '/dashboard', title: '首页', affix: true }

  function load() {
    try {
      const raw = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
      const list = Array.isArray(raw) ? raw.filter((t) => t && t.fullPath) : []
      if (!list.some((t) => t.fullPath === HOME.fullPath)) list.unshift({ ...HOME })
      return list
    } catch {
      return [{ ...HOME }]
    }
  }

  const tags = ref(load())

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(tags.value))
  }

  /** 进入某路由时登记标签（已存在则忽略） */
  function addTag(route) {
    if (!route || !route.fullPath) return
    if (route.meta?.public) return
    if (tags.value.some((t) => t.fullPath === route.fullPath)) return
    tags.value.push({
      fullPath: route.fullPath,
      path: route.path,
      title: route.meta?.title || route.name || route.path,
      affix: false,
    })
    persist()
  }

  /** 关闭单个标签，返回关闭后应跳转的标签（若关的是当前页） */
  function closeTag(fullPath) {
    const idx = tags.value.findIndex((t) => t.fullPath === fullPath)
    if (idx === -1) return null
    if (tags.value[idx].affix) return null
    tags.value.splice(idx, 1)
    persist()
    return tags.value[idx] || tags.value[idx - 1] || tags.value[tags.value.length - 1] || null
  }

  function closeOthers(fullPath) {
    tags.value = tags.value.filter((t) => t.affix || t.fullPath === fullPath)
    persist()
  }

  function closeAll() {
    tags.value = tags.value.filter((t) => t.affix)
    persist()
    return tags.value[tags.value.length - 1] || null
  }

  return { tags, addTag, closeTag, closeOthers, closeAll }
})
