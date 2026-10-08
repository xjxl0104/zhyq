import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import Supplier from '../Supplier.vue'
import { supplierApi } from '@/api/supplier'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/utils/permission', () => ({ hasPermission: () => true }))
vi.mock('@/api/supplier', () => ({
  supplierApi: { stats: vi.fn(), page: vi.fn(), tenantContacts: vi.fn(), addTenantContact: vi.fn(),
    updateTenantContact: vi.fn(), removeTenantContact: vi.fn() },
  supplierContractApi: { page: vi.fn() }
}))
vi.mock('@/api/building', () => ({ projectApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/system', () => ({ dictApi: { dataByType: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn().mockResolvedValue([]) } }))

beforeEach(() => {
  vi.clearAllMocks()
  supplierApi.stats.mockResolvedValue({})
  supplierApi.page.mockResolvedValue({ records: [], total: 0 })
  supplierApi.tenantContacts.mockResolvedValue([])
})

function mount() {
  return shallowMount(Supplier, { global: { stubs: {
    Search: true, Plus: true, Upload: true, UploadFilled: true
  } } })
}

it('creates a freely typed tenant contact without selecting a preset tenant', async () => {
  supplierApi.addTenantContact.mockResolvedValue(7)
  const wrapper = mount()
  await flushPromises()
  wrapper.vm.openTenantContactDialog()
  wrapper.vm.tenantContactForm.name = '自填租客'
  wrapper.vm.tenantContactForm.contact = '张三'
  await wrapper.vm.saveTenantContact()
  expect(supplierApi.addTenantContact).toHaveBeenCalledWith(expect.objectContaining({ name: '自填租客', contact: '张三' }))
  expect(wrapper.vm.tenantContactDialog).toBe(false)
  wrapper.unmount()
})

it('edits and removes a saved contact record', async () => {
  supplierApi.tenantContacts.mockResolvedValue([{ id: 1, name: '租客一', contact: '张三' }])
  supplierApi.updateTenantContact.mockResolvedValue()
  supplierApi.removeTenantContact.mockResolvedValue()
  const wrapper = mount()
  await flushPromises()
  await wrapper.vm.openTenantContacts()
  wrapper.vm.openTenantContactDialog(wrapper.vm.tenantContacts[0])
  wrapper.vm.tenantContactForm.name = '改名租客'
  await wrapper.vm.saveTenantContact()
  expect(supplierApi.updateTenantContact).toHaveBeenCalledWith(expect.objectContaining({ id: 1, name: '改名租客' }))
  await wrapper.vm.removeTenantContact(1)
  expect(supplierApi.removeTenantContact).toHaveBeenCalledWith(1)
  wrapper.unmount()
})
