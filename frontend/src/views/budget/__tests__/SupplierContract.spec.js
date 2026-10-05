import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import SupplierContract from '../SupplierContract.vue'
import { supplierApi, supplierContractApi } from '@/api/supplier'
import { dictApi } from '@/api/system'

const { route } = vi.hoisted(() => ({ route: { query: {} } }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/supplier', () => ({
  supplierApi: { list: vi.fn() },
  supplierContractApi: { page: vi.fn(), stats: vi.fn(), get: vi.fn() }
}))
vi.mock('@/api/system', () => ({ dictApi: { dataByType: vi.fn() } }))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn().mockResolvedValue([]) } }))

let wrapper
beforeEach(() => {
  vi.clearAllMocks()
  route.query = { supplierId: '3', create: '1' }
  supplierApi.list.mockResolvedValue([{ id: 3, name: '消防公司' }])
  dictApi.dataByType.mockResolvedValue([])
  supplierContractApi.stats.mockResolvedValue({ total: 2 })
  supplierContractApi.page.mockResolvedValue({ total: 2, records: [
    { id: 8, supplierId: 3, name: '主合同', parentContractId: null },
    { id: 9, supplierId: 3, name: '补充协议', parentContractId: 8 }
  ] })
})
afterEach(() => wrapper?.unmount())
function mount() {
  wrapper = shallowMount(SupplierContract, { global: { stubs: {
    FileUpload: true, Search: true, Plus: true, Refresh: true
  } } })
  return wrapper
}

it('供应商入口打开草稿，重置查询和继续新增都保留供应商', async () => {
  const w = mount()
  await flushPromises()
  expect(w.vm.dialog.visible).toBe(true)
  expect(w.vm.form.supplierId).toBe(3)
  expect(supplierContractApi.stats).toHaveBeenCalledWith({ supplierId: 3 })
  w.vm.reset()
  await w.vm.openDialog()
  expect(w.vm.query.supplierId).toBe(3)
  expect(w.vm.form.supplierId).toBe(3)
})

it('从主合同新增补充协议时带入主合同，候选项排除其他补充协议', async () => {
  route.query.parentContractId = '8'
  const w = mount()
  await flushPromises()
  expect(w.vm.isAddendum).toBe(true)
  expect(w.vm.form.parentContractId).toBe(8)
  expect(w.vm.parentContracts.map(c => c.id)).toEqual([8])
})
