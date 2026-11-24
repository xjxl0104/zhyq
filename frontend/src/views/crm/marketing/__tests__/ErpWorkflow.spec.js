import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { reactive } from 'vue'
import Erp from '../Erp.vue'
import { mktYunshanApi } from '@/api/marketing'

const routeState = reactive({ query: { warehouseId: '9' } })

vi.mock('@/api/marketing', () => ({
  mktYunshanApi: { localWarehouses: vi.fn(), localWarehouse: vi.fn() }
}))
vi.mock('vue-router', () => ({ useRoute: () => routeState }))
vi.mock('../YunshanVolumePanel.vue', () => ({ default: { template: '<div class="yunshan-vol-stub" />' } }))
vi.mock('../CustomerOperationWarehousePanel.vue', () => ({ default: { template: '<div class="ops-panel-stub" />' } }))
vi.mock('../GeneralErpPanel.vue', () => ({ default: { template: '<div class="general-erp-stub" />' } }))

const pending = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const wrappers = []
function mount() { const w = shallowMount(Erp); wrappers.push(w); return w }
beforeEach(() => {
  vi.clearAllMocks()
  routeState.query = { warehouseId: '9' }
  mktYunshanApi.localWarehouses.mockResolvedValue({ records: [{ id: '9', name: '测试仓', code: 'WH9' }], total: 1 })
  mktYunshanApi.localWarehouse.mockImplementation(id => Promise.resolve({ id, name: `仓库${id}` }))
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('云仓数据来源绑定当前云仓与双轨切换', () => {
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

  it('新增客户关联后：云仓接口已配置才接着开通发货量同步，未配置则跳过', async () => {
    const w = mount(); await flushPromises()
    const link = { customerId: '1', operationWarehouseId: '2', customerName: '客户' }
    const openMap = vi.fn()
    w.vm.yunshanPanel = { ready: false, openMap }
    await w.vm.afterLinked(link)
    expect(openMap).not.toHaveBeenCalled()
    w.vm.yunshanPanel = { ready: true, openMap }
    await w.vm.afterLinked(link)
    expect(openMap).toHaveBeenCalledWith(link)
  })

  it('云仓 orderMode === erp 或路由带 onboarding=1 时自动切换到通用 ERP Webhook 轨道', async () => {
    mktYunshanApi.localWarehouse.mockResolvedValueOnce({ id: '9', name: '外部ERP仓', orderMode: 'erp', erpStatus: 1 })
    const w = mount(); await flushPromises()
    expect(w.vm.track).toBe('webhook')

    routeState.query = { warehouseId: '9', onboarding: '1' }
    mktYunshanApi.localWarehouse.mockResolvedValueOnce({ id: '9', name: '入驻中云仓', orderMode: 'pending', joinStatus: 3 })
    const w2 = mount(); await flushPromises()
    expect(w2.vm.track).toBe('webhook')
  })
})
