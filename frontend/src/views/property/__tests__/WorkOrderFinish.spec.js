import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import WorkOrder from '../WorkOrder.vue'
import { workOrderApi } from '@/api/property'
import { fileApi } from '@/api/file'
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/property/workorder', query: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/api/property', () => ({ workOrderApi: { page: vi.fn().mockResolvedValue({ records: [], total: 0 }), stats: vi.fn().mockResolvedValue({}), finish: vi.fn(), verify: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/supplier', () => ({ supplierApi: { options: vi.fn().mockResolvedValue([]), tenantContacts: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn().mockResolvedValue([]) } }))
const row = { id: 42, code: 'WO42', status: 3 }
async function page() {
  const wrapper = shallowMount(WorkOrder, { global: { stubs: { Search: true, Plus: true, FileUpload: true } } })
  await flushPromises()
  wrapper.vm.doFinish(row)
  return wrapper
}
beforeEach(() => vi.clearAllMocks())
it('requires processing photos; existing report attachments do not count', async () => {
  const w = await page()
  w.vm.attachFiles = [{ id: 1, bizType: 'work_order' }]
  w.vm.finishForm.content = '已修好'
  await w.vm.submitFinish()
  expect(workOrderApi.finish).not.toHaveBeenCalled()
  expect(w.vm.finishDialog.visible).toBe(true)
  w.unmount()
})
it('blocks completion until camera/upload finishes then submits photo IDs together', async () => {
  const w = await page()
  w.vm.finishForm.files = [{ id: 7 }]
  w.vm.finishForm.content = '已修好'
  w.vm.finishBusy = true
  await w.vm.submitFinish()
  expect(workOrderApi.finish).not.toHaveBeenCalled()
  w.vm.finishBusy = false
  workOrderApi.finish.mockResolvedValue()
  await w.vm.submitFinish()
  expect(workOrderApi.finish).toHaveBeenCalledWith(42, { content: '已修好', photoIds: [7] })
  expect(w.vm.finishDialog.visible).toBe(false)
  w.unmount()
})
it('preserves photos and text on failure and keeps drafts separate across orders', async () => {
  const w = await page()
  w.vm.finishForm.files = [{ id: 7 }]
  w.vm.finishForm.content = '处理结果'
  workOrderApi.finish.mockRejectedValueOnce(new Error('offline'))
  await w.vm.submitFinish()
  expect(w.vm.finishDialog.visible).toBe(true)
  expect(w.vm.finishSaving).toBe(false)
  w.vm.doFinish({ id: 99 })
  expect(w.vm.finishForm.files).toEqual([])
  w.vm.doFinish(row)
  expect(w.vm.finishForm.files).toEqual([{ id: 7 }])
  expect(w.vm.finishForm.content).toBe('处理结果')
  w.unmount()
})
it('does not submit a second request during completion', async () => {
  const w = await page()
  w.vm.finishForm.files = [{ id: 7 }]
  let resolve
  workOrderApi.finish.mockImplementation(() => new Promise(r => { resolve = r }))
  const first = w.vm.submitFinish()
  await w.vm.submitFinish()
  expect(workOrderApi.finish).toHaveBeenCalledOnce()
  resolve()
  await first
  w.unmount()
})
it('loads completion photos separately for the processing timeline', async () => {
  const w = await page()
  workOrderApi.get.mockResolvedValue({ order: row, logs: [{ id: 8, action: '处理' }] })
  fileApi.list.mockImplementation(type => Promise.resolve(type === 'work_order_finish' ? [{ id: 7, originalName: '处理照片.jpg' }] : [{ id: 1 }]))
  await w.vm.openDetail(row)
  expect(w.vm.detailCompletionFiles).toEqual([{ id: 7, originalName: '处理照片.jpg' }])
  expect(w.vm.detailFiles).toEqual([{ id: 1 }])
  w.unmount()
})
it('waits for verification uploads and submits their photo IDs with the rating', async () => {
  const w = await page()
  w.vm.openVerify({ id: 42, code: 'WO42' })
  w.vm.verifyForm.files = [{ id: 11 }]
  w.vm.verifyBusy = true
  await w.vm.submitVerify()
  expect(workOrderApi.verify).not.toHaveBeenCalled()
  w.vm.verifyBusy = false
  workOrderApi.verify.mockResolvedValue()
  await w.vm.submitVerify()
  expect(workOrderApi.verify).toHaveBeenCalledWith(42, { score: 5, photoIds: [11] })
  w.unmount()
})
