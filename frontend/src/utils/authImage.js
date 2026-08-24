import { reactive } from 'vue'

import { fileApi } from '@/api/file'

/**
 * 鉴权图片预览。
 *
 * 为什么需要这个:原生 <img src="/api/file/download/1"> 是浏览器自己发的请求,
 * 不经过 axios 拦截器,拿不到 Authorization 头。SecurityConfig 里 anyRequest().authenticated(),
 * 附件静态目录也已关闭匿名访问,所以这类 img 一律 401 → 图裂。
 * 表象很像「上传失败」,其实文件已经存进去了。
 *
 * 解法:先用 axios(带 token)把文件取成 Blob,再把 objectURL 喂给 img。
 */
export function useAuthImage() {
  const cache = reactive({})

  /** 拉取并缓存 fileId 对应的 blob URL;失败不抛出,留空串让 img 走占位。 */
  async function resolve(fileId) {
    if (fileId == null || cache[fileId] !== undefined) return
    cache[fileId] = ''                       // 占位,防同一 id 并发重复请求
    try {
      const res = await fileApi.download(fileId)
      cache[fileId] = URL.createObjectURL(res.data)
    } catch (e) {
      cache[fileId] = ''                     // 401/404 都当作无图
    }
  }

  /** 批量预取,列表/详情一次性把多张图换好。 */
  async function resolveAll(fileIds) {
    await Promise.all((fileIds || []).map(resolve))
  }

  function srcFor(fileId) {
    return cache[fileId] || ''
  }

  /** 组件卸载时调用,释放 objectURL 避免内存泄漏。 */
  function revokeAll() {
    Object.values(cache).forEach(url => {
      if (url) URL.revokeObjectURL(url)
    })
    Object.keys(cache).forEach(k => delete cache[k])
  }

  return { srcFor, resolve, resolveAll, revokeAll }
}
