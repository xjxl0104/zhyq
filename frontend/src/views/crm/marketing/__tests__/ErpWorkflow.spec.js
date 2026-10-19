import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Erp from '../Erp.vue'
import { mktYunshanApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({
  mktYunshanApi: { localWarehouses: vi.fn(), localWarehouse: vi.fn() }
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: { warehouseId: '9' } }) }))
vi.mock('../YunshanVolumePanel.vue', () => ({ default: { template: '<div />' } }))
vi.mock('../CustomerOperationWarehousePanel.vue', () => ({ default: { template: '<div />' } }))

const pending = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const wrappers = []
function mount() { const w = shallowMount(Erp); wrappers.push(w); return w }
beforeEach(() => {
  vi.clearAllMocks()
  mktYunshanApi.localWarehouses.mockResolvedValue({ records: [{ id: '9', name: '测试仓', code: 'WH9' }], total: 1 })
  mktYunshanApi.localWarehouse.mockImplementation(id => Promise.resolve({ id, name: `仓库${id}` }))
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('云仓数据来源绑定当前云仓', () => {
  it('切换云仓后旧响应不能覆盖新仓资料', async () => {
    const w = mount(); await flushPromises()
    const old = pending()
    mktYunshanApi.localWarehouse.mockImplementation(id => id === '9' ? old.promise : Promise.resolve({ id, name: '新仓' }))
    const first = w.vm.loadWarehouse()
    w.vm.warehouseId = '10'
    await w.vm.loadWarehouse()
    old.resolve({ id: '9', name: '旧仓' }); await first
    expect(w.vm.warehouse.id).toBe('10')
    expect(w.vm.warehouseName).toBe('新仓')
  })
})
