import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, expect, it, vi } from 'vitest'
import WorkOrderPhotoGallery from '../WorkOrderPhotoGallery.vue'
import { fileApi } from '@/api/file'

vi.mock('@/api/file', () => ({ fileApi: { download: vi.fn() } }))
vi.mock('@/utils/fileDownload', () => ({ startFileDownload: vi.fn() }))

beforeEach(() => {
  vi.clearAllMocks()
  URL.createObjectURL = vi.fn(() => 'blob:authorized-photo')
  URL.revokeObjectURL = vi.fn()
})

it('renders authenticated thumbnails with zoom and leaves non-images as downloads', async () => {
  fileApi.download.mockResolvedValue({ data: new Blob(['image'], { type: 'image/jpeg' }) })
  const wrapper = mount(WorkOrderPhotoGallery, { props: { files: [
    { id: 7, originalName: '报修.jpg', contentType: 'image/jpeg' },
    { id: 8, originalName: '说明.pdf', contentType: 'application/pdf' }
  ] }, global: { plugins: [ElementPlus] } })
  await flushPromises()
  expect(fileApi.download).toHaveBeenCalledOnce()
  expect(fileApi.download).toHaveBeenCalledWith(7)
  const image = wrapper.findComponent({ name: 'ElImage' })
  expect(image.props('src')).toBe('blob:authorized-photo')
  expect(image.props('previewSrcList')).toEqual(['blob:authorized-photo'])
  expect(wrapper.text()).toContain('说明.pdf（下载）')
  wrapper.unmount()
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:authorized-photo')
})
