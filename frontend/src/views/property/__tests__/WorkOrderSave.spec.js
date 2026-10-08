import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import WorkOrder from '../WorkOrder.vue'
import { workOrderApi } from '@/api/property'
import { fileApi } from '@/api/file'
import { supplierApi } from '@/api/supplier'
vi.mock('vue-router', () => ({ useRoute: () => ({ path: '/property/workorder', query: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))
vi.mock('@/api/property', () => ({ workOrderApi: { page: vi.fn().mockResolvedValue({ records: [], total: 0 }), stats: vi.fn().mockResolvedValue({}), add: vi.fn(), update: vi.fn() } }))
vi.mock('@/api/supplier', () => ({ supplierApi: { options: vi.fn().mockResolvedValue([]), tenantContacts: vi.fn().mockResolvedValue([]), tenantDirectory: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/system', () => ({ userApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/file', () => ({ fileApi: { attach: vi.fn(), list: vi.fn() } }))
beforeEach(() => vi.clearAllMocks())
it('retries a failed photo association on the saved order without duplicating it', async () => {
  workOrderApi.add.mockResolvedValue(42)
  workOrderApi.update.mockResolvedValue()
  fileApi.attach.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(1)
  fileApi.list.mockResolvedValue([{ id: 51, bizId: 42, originalName: '照片.jpg' }])
  const wrapper = shallowMount(WorkOrder, { global: { stubs: { Search: true, Plus: true, WorkOrderLocation: true, FloorPlanViewer: true, FileUpload: true } } })
  await flushPromises()
  wrapper.vm.openDialog()
  wrapper.vm.formRef = { validate: vi.fn().mockResolvedValue(true) }
  wrapper.vm.form.title = '漏水'
  wrapper.vm.attachFiles = [{ id: 51, bizId: null }]
  await wrapper.vm.submit()
  expect(workOrderApi.add).toHaveBeenCalledWith(expect.objectContaining({ slaResolveMin: 7 * 24 * 60 }))
  expect(wrapper.vm.form.id).toBe(42)
  expect(wrapper.vm.dialog.visible).toBe(true)
  await wrapper.vm.submit()
  expect(workOrderApi.add).toHaveBeenCalledOnce()
  expect(workOrderApi.update).toHaveBeenCalledOnce()
  expect(fileApi.attach).toHaveBeenLastCalledWith('work_order', 42, [51])
  expect(wrapper.vm.dialog.visible).toBe(false)
  wrapper.unmount()
})

it('selects a freely entered contact for a new work order', async () => {
  supplierApi.tenantContacts.mockResolvedValueOnce([{ id: 7, name: '自填租客', contact: '张三', phone: '13800000000' }])
  const wrapper = shallowMount(WorkOrder, { global: { stubs: { Search: true, Plus: true, WorkOrderLocation: true, FloorPlanViewer: true, FileUpload: true } } })
  await flushPromises()
  wrapper.vm.openDialog()
  wrapper.vm.form.tenantContactRefId = 7
  wrapper.vm.onTenantChange(7)
  expect(wrapper.vm.form.tenantName).toBe('自填租客')
  expect(wrapper.vm.form.tenantContact).toBe('张三')
  expect(wrapper.vm.form.tenantRefId).toBeNull()
  wrapper.unmount()
})
