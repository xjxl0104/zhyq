import { flushPromises, shallowMount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { ElMessage } from 'element-plus'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import FileUpload from '../FileUpload.vue'
import Archive from '@/views/contract/Archive.vue'
import { fileApi } from '@/api/file'
import { contractApi } from '@/api/contract'

vi.mock('@/api/file', () => ({
  uploadUrl: '/api/file/upload',
  fileApi: {
    createDownloadTicket: vi.fn().mockResolvedValue({ ticket: 'single-use-ticket' }),
    list: vi.fn().mockResolvedValue([])
  }
}))
vi.mock('@/api/contract', () => ({
  contractApi: {
    page: vi.fn().mockResolvedValue({ records: [], total: 0 }),
    expiryAlerts: vi.fn().mockResolvedValue([])
  }
}))

let clicked
const wrappers = []
beforeEach(() => {
  clicked = []
  fileApi.createDownloadTicket.mockResolvedValue({ ticket: 'single-use-ticket' })
  contractApi.page.mockResolvedValue({ records: [], total: 0 })
  contractApi.expiryAlerts.mockResolvedValue([])
  vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  vi.spyOn(ElMessage, 'error').mockImplementation(() => {})
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
    clicked.push({ href: this.getAttribute('href'), name: this.download })
  })
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
})

it('clicking a common attachment filename hands the real file URL to the browser', async () => {
  const wrapper = shallowMount(FileUpload, { global: { renderStubDefaultSlot: true } })
  wrappers.push(wrapper)
  await wrapper.findComponent({ name: 'ElUpload' }).props('onPreview')({ id: 35, name: '合同.pdf' })
  expect(clicked).toEqual([{ href: '/api/file/browser-download/35?ticket=single-use-ticket', name: '合同.pdf' }])
})

it('the archive download action hands off immediately and releases its loading state', async () => {
  const wrapper = shallowMount(Archive, {
    global: { plugins: [createPinia()], stubs: { Search: true, Download: true } }
  })
  wrappers.push(wrapper)
  await flushPromises()
  await wrapper.vm.downloadAttachment({ id: 35, originalName: '合同.pdf' })
  expect(clicked).toEqual([{ href: '/api/file/browser-download/35?ticket=single-use-ticket', name: '合同.pdf' }])
  expect(wrapper.vm.downloadingFileId).toBeNull()
})
