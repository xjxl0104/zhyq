import { beforeEach, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { fileApi } from '../file'
vi.mock('@/utils/request', () => ({ default: { post: vi.fn().mockResolvedValue({ id: 1 }) } }))
beforeEach(() => vi.clearAllMocks())
it('未指定上传超时的原有业务保持默认请求配置', async () => {
  const form = new FormData()
  await fileApi.upload(form)
  expect(request.post).toHaveBeenCalledWith('/file/upload', form, { headers: { 'Content-Type': 'multipart/form-data' } })
})
it('缴费资料可使用大文件上传超时并保留原 multipart 请求头', async () => {
  const form = new FormData()
  await fileApi.upload(form, { timeout: 10 * 60 * 1000 })
  expect(request.post).toHaveBeenCalledWith('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }, timeout: 600000
  })
})
