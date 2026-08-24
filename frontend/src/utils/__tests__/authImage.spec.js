import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fileApi } from '@/api/file'
import { useAuthImage } from '../authImage'

vi.mock('@/api/file', () => ({
  fileApi: { download: vi.fn() }
}))

describe('useAuthImage — 鉴权图片预览', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:fake-url')
    globalThis.URL.revokeObjectURL = vi.fn()
  })

  it('把 fileId 换成 blob URL,而不是裸 /api/file/download 路径', async () => {
    fileApi.download.mockResolvedValue({ data: new Blob(['x']) })
    const { srcFor, resolve } = useAuthImage()

    await resolve(7)

    // 关键:原生 <img src> 不走 axios 拦截器,拿不到 Authorization 头 → 401。
    // 必须先鉴权取 Blob,再喂 objectURL 给 img。
    expect(fileApi.download).toHaveBeenCalledWith(7)
    expect(srcFor(7)).toBe('blob:fake-url')
    expect(srcFor(7)).not.toContain('/api/file/download')
  })

  it('同一 fileId 只请求一次(缓存,避免列表里重复拉同一张图)', async () => {
    fileApi.download.mockResolvedValue({ data: new Blob(['x']) })
    const { resolve } = useAuthImage()

    await resolve(9)
    await resolve(9)

    expect(fileApi.download).toHaveBeenCalledTimes(1)
  })

  it('下载失败时不抛出,srcFor 返回空串让 img 走占位', async () => {
    fileApi.download.mockRejectedValue(new Error('401'))
    const { srcFor, resolve } = useAuthImage()

    await expect(resolve(11)).resolves.toBeUndefined()
    expect(srcFor(11)).toBe('')
  })

  it('revoke 释放所有 blob URL,组件卸载时不泄漏', async () => {
    fileApi.download.mockResolvedValue({ data: new Blob(['x']) })
    const { resolve, revokeAll } = useAuthImage()

    await resolve(3)
    revokeAll()

    expect(globalThis.URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-url')
  })
})
