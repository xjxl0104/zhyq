import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import FileUpload from '../FileUpload.vue'
import { fileApi } from '@/api/file'
vi.mock('@/api/file', () => ({ uploadUrl: '/api/file/upload', fileApi: { upload: vi.fn() } }))
vi.mock('@/utils/fileDownload', () => ({ startFileDownload: vi.fn() }))
it('uploads a captured photo as a pending work-order attachment and waits for it', async () => {
  let complete
  fileApi.upload.mockReturnValue(new Promise(resolve => { complete = resolve }))
  const wrapper = mount(FileUpload, { props: { camera: true, bizType: 'work_order', bizId: null, modelValue: [] }, global: { plugins: [ElementPlus], stubs: { PhotoCapture: true } } })
  const photo = new File(['picture'], '报修.jpg', { type: 'image/jpeg' })
  wrapper.findComponent({ name: 'PhotoCapture' }).vm.$emit('capture', photo)
  await flushPromises()
  const data = fileApi.upload.mock.calls[0][0]
  expect(data.get('file')).toBe(photo)
  expect(data.get('bizType')).toBe('work_order')
  expect(data.has('bizId')).toBe(false)
  expect(wrapper.emitted('busy').at(-1)).toEqual([true])
  complete({ id: 51, originalName: '报修.jpg', bizId: null })
  await flushPromises()
  expect(wrapper.emitted('update:modelValue').at(-1)[0][0].id).toBe(51)
  expect(wrapper.emitted('busy').at(-1)).toEqual([false])
  wrapper.unmount()
})
