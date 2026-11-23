import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import GeneralErpPanel from '../GeneralErpPanel.vue'
import { mktErpApi, mktWarehouseApi } from '@/api/marketing'
import { erpReconciliationApi } from '@/api/erp-reconciliation'
import { ElMessageBox } from 'element-plus'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))

vi.mock('@/utils/permission', () => ({ hasPermission: code => permissions.has(code) }))
vi.mock('@/api/marketing', () => ({
  mktErpApi: {
    get: vi.fn(),
    issue: vi.fn(),
    resetSecret: vi.fn(),
    update: vi.fn(),
    mappings: vi.fn(),
    saveMapping: vi.fn(),
    deleteMapping: vi.fn(),
    exceptions: vi.fn(),
    retry: vi.fn(),
    logs: vi.fn()
  },
  mktCustomerApi: { page: vi.fn().mockResolvedValue({ records: [{ id: 12, name: '星辰服饰' }] }) },
  mktYunshanApi: { customers: vi.fn().mockResolvedValue({ records: [] }) },
  mktWarehouseApi: { confirmErp: vi.fn() }
}))
vi.mock('@/api/erp-reconciliation', () => ({
  erpReconciliationApi: {
    preview: vi.fn(),
    submit: vi.fn(),
    confirm: vi.fn(),
    refresh: vi.fn()
  }
}))
vi.mock('element-plus', async original => ({
  ...await original(),
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

const wrappers = []
function mount(props = {}) {
  const w = shallowMount(GeneralErpPanel, {
    props: { warehouseId: '9', warehouseName: '杭州通用仓', warehouse: { id: 9, joinStatus: 3, erpStatus: 2 }, onboarding: true, ...props },
    global: { directives: { loading: {} } }
  })
  wrappers.push(w)
  return w
}

beforeEach(() => {
  vi.clearAllMocks()
  permissions.clear()
  permissions.add('crm:marketing:erp:query')
  permissions.add('crm:marketing:erp:config')
  permissions.add('crm:marketing:onboarding:audit')

  mktErpApi.get.mockResolvedValue(null)
  mktErpApi.mappings.mockResolvedValue([])
  mktErpApi.exceptions.mockResolvedValue({ total: 0, records: [] })
  mktErpApi.logs.mockResolvedValue({ total: 0, records: [] })
  erpReconciliationApi.preview.mockResolvedValue({
    metric: 'VALID_OUTBOUND_ORDER_COUNT',
    scope: '系统登记客户有效出库单数',
    calculation: { systemOrderCount: 120, cancelledOrderCount: 3, unmappedCount: 0, retryCount: 0, blockingCount: 0, blockingReasons: [] },
    history: []
  })
})

afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('通用 ERP Webhook 接入面板', () => {
  it('签发沙箱凭证一次性弹窗展示 appKey / appSecret，关闭后清空明文密钥，并支持切至正式环境 env=2', async () => {
    mktErpApi.issue.mockResolvedValue({ id: 1, warehouseId: 9, appId: 'APP_HZ_01', secret: 'SEC_ONCE_999', env: 1, status: 1 })
    mktErpApi.get.mockResolvedValueOnce(null).mockResolvedValue({ id: 1, warehouseId: 9, appId: 'APP_HZ_01', env: 2, status: 1 })
    mktErpApi.update.mockResolvedValue(null)
    ElMessageBox.confirm.mockResolvedValue('confirm')

    const w = mount(); await flushPromises()
    await w.vm.issueCredential()
    expect(mktErpApi.issue).toHaveBeenCalledWith('9')
    expect(w.vm.secretDialog).toMatchObject({ visible: true, appKey: 'APP_HZ_01', appSecret: 'SEC_ONCE_999' })
    expect(w.vm.credential.secret).toBeNull()

    await w.vm.switchEnv(2)
    expect(mktErpApi.update).toHaveBeenCalledWith('9', { env: 2 })
    expect(w.emitted('updated')).toBeTruthy()

    mktWarehouseApi.confirmErp.mockResolvedValue(null)
    await w.vm.confirmOnboardingErp()
    expect(mktWarehouseApi.confirmErp).toHaveBeenCalledWith('9')
  })

  it('保存货主编码映射、重放异常队列与提交周期强对账快照均使用真实参数契约', async () => {
    mktErpApi.get.mockResolvedValue({ id: 1, warehouseId: 9, appId: 'APP_HZ_01', env: 2, status: 1 })
    mktErpApi.saveMapping.mockResolvedValue({ queued: 2 })
    mktErpApi.retry.mockResolvedValue(null)
    erpReconciliationApi.submit.mockResolvedValue({ id: 55 })

    const w = mount(); await flushPromises()

    w.vm.mappingDialog.customerId = 12
    w.vm.mappingDialog.customerCode = '  OWNER_012  '
    await w.vm.submitMapping()
    expect(mktErpApi.saveMapping).toHaveBeenCalledWith('9', { customerId: 12, customerCode: 'OWNER_012' })

    w.vm.retryDialog.row = { id: 301, orderNo: 'ORD-301', status: 'DEAD' }
    w.vm.retryDialog.reason = '  已补齐映射人工重放  '
    await w.vm.submitRetry()
    expect(mktErpApi.retry).toHaveBeenCalledWith('9', 301, { reason: '已补齐映射人工重放' })

    w.vm.reconcile.period = ['2026-11-01', '2026-11-23']
    w.vm.submitRecDialog.externalOrderCount = 120
    w.vm.submitRecDialog.sourceName = '旺店通生产导出'
    w.vm.submitRecDialog.evidence = '2026-11导出单号清单.xlsx'
    await w.vm.submitReconciliation()
    expect(erpReconciliationApi.submit).toHaveBeenCalledWith('9', {
      periodStart: '2026-11-01',
      periodEnd: '2026-11-23',
      externalOrderCount: 120,
      sourceName: '旺店通生产导出',
      evidence: '2026-11导出单号清单.xlsx'
    })
  })
})
