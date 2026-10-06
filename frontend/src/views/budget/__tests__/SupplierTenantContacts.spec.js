import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import Supplier from '../Supplier.vue'
import { supplierApi } from '@/api/supplier'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/utils/permission', () => ({ hasPermission: () => true }))
vi.mock('@/api/supplier', () => ({
  supplierApi: { stats: vi.fn(), page: vi.fn(), tenantContacts: vi.fn(), removeTenantContact: vi.fn() },
  supplierContractApi: { page: vi.fn() }
}))
vi.mock('@/api/building', () => ({ projectApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/system', () => ({ dictApi: { dataByType: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn().mockResolvedValue([]) } }))

beforeEach(() => {
  vi.clearAllMocks()
  supplierApi.stats.mockResolvedValue({})
  supplierApi.page.mockResolvedValue({ records: [], total: 0 })
})

it('removes only the saved contact from the drawer while keeping its tenant selectable', async () => {
  supplierApi.tenantContacts
    .mockResolvedValueOnce([{ id: 1, name: '租客一', contact: '张三' }, { id: 2, name: '租客二', contact: '' }])
    .mockResolvedValueOnce([{ id: 1, name: '租客一', contact: null }, { id: 2, name: '租客二', contact: '' }])
  supplierApi.removeTenantContact.mockResolvedValue()
  const wrapper = shallowMount(Supplier, { global: { stubs: {
    Search: true, Plus: true, Upload: true, UploadFilled: true
  } } })
  await flushPromises()
  await wrapper.vm.openTenantContacts()
  expect(wrapper.vm.savedTenantContacts.map(t => t.id)).toEqual([1])
  await wrapper.vm.removeTenantContact(1)
  expect(supplierApi.removeTenantContact).toHaveBeenCalledWith(1)
  expect(wrapper.vm.savedTenantContacts).toEqual([])
  expect(wrapper.vm.tenantContacts.map(t => t.id)).toEqual([1, 2])
  wrapper.unmount()
})
