import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import ErpReconciliation from '../ErpReconciliation.vue'
import { erpReconciliationApi } from '@/api/erp-reconciliation'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/erp-reconciliation', () => ({ erpReconciliationApi: { preview: vi.fn(), submit: vi.fn(), confirm: vi.fn() } }))
vi.mock('@/utils/permission', () => ({ hasPermission: () => true }))
vi.mock('element-plus', async (importOriginal) => ({ ...(await importOriginal()), ElMessage: { success: vi.fn() }, ElMessageBox: { prompt: vi.fn() } }))
const wrappers = []
function mount() {
  const wrapper = shallowMount(ErpReconciliation, { props: { warehouseId: 7 }, global: { stubs: { ElForm: true, ElTable: true } } })
  wrappers.push(wrapper); return wrapper
}
function result(count = 2) { return { calculation: { systemOrderCount: count, blockingCount: 0, blockingReasons: [] }, history: [] } }
beforeEach(() => {
  vi.clearAllMocks()
  erpReconciliationApi.preview.mockResolvedValue(result())
  erpReconciliationApi.submit.mockResolvedValue({ id: 9 })
  erpReconciliationApi.confirm.mockResolvedValue({ id: 10 })
  ElMessageBox.prompt.mockResolvedValue({ value: '已核对外部导出凭据' })
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('ERP 对账凭据与版本确认', () => {
  it('计算系统单数不会自动填充外部单数', async () => {
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']; await w.vm.load()
    expect(w.vm.preview.calculation.systemOrderCount).toBe(2)
    expect(w.vm.form.externalOrderCount).toBeUndefined()
    expect(w.vm.canSave).toBeFalsy()
  })
  it('提交真实外部数量和凭据并保留周期', async () => {
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']
    w.vm.form = { externalOrderCount: 0, sourceName: 'ERP 出库导出', evidence: '202609.xlsx，营销货主筛选，无有效出库' }
    await w.vm.save()
    expect(erpReconciliationApi.submit).toHaveBeenCalledWith(7, {
      periodStart: '2026-09-01', periodEnd: '2026-09-30', externalOrderCount: 0,
      sourceName: 'ERP 出库导出', evidence: '202609.xlsx，营销货主筛选，无有效出库'
    })
  })
  it('切换云仓不会显示上一云仓迟到的对账结果', async () => {
    let complete
    erpReconciliationApi.preview.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']; const first = w.vm.load()
    await w.setProps({ warehouseId: 8 }); await flushPromises()
    complete(result(99)); await first
    expect(w.vm.preview.calculation.systemOrderCount).toBe(2)
  })
  it('确认弹窗保留原云仓和版本身份', async () => {
    let complete
    ElMessageBox.prompt.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']
    const pending = w.vm.confirm({ id: 91 })
    await w.setProps({ warehouseId: 8 })
    complete({ value: '已核对原云仓凭据' }); await pending
    expect(erpReconciliationApi.confirm).toHaveBeenCalledWith(7, 91, '已核对原云仓凭据')
  })
  it('连续点击确认只打开一次弹窗并提交一个确认版本', async () => {
    let complete
    ElMessageBox.prompt.mockImplementationOnce(() => new Promise(resolve => { complete = resolve }))
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']
    const first = w.vm.confirm({ id: 91 }); await w.vm.confirm({ id: 91 })
    expect(ElMessageBox.prompt).toHaveBeenCalledTimes(1)
    complete({ value: '已复核凭据' }); await first
    expect(erpReconciliationApi.confirm).toHaveBeenCalledTimes(1)
  })
  it('提交失败保留外部录入并提示原因', async () => {
    erpReconciliationApi.submit.mockRejectedValue(new Error('该云仓已冻结'))
    const w = mount(); w.vm.period = ['2026-09-01', '2026-09-30']
    w.vm.form = { externalOrderCount: 5, sourceName: 'ERP 报表', evidence: 'export.xlsx' }
    await w.vm.save()
    expect(w.vm.form.externalOrderCount).toBe(5)
    expect(w.vm.error).toBe('该云仓已冻结')
    expect(w.vm.saving).toBe(false)
  })
})
