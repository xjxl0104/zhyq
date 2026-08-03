import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ReceivableRegister from '../ReceivableRegister.vue'
import { buildReceivableBinding, canConfirmReceivableImport, receivableColumns } from '../receivableModel'
import { receivableApi } from '@/api/receivable'

vi.mock('@/api/receivable', () => ({
  receivableApi: {
    page: vi.fn(),
    capabilities: vi.fn(),
    batches: vi.fn(),
    remove: vi.fn(),
    generate: vi.fn()
  }
}))

describe('应收明细登记表', () => {
  beforeEach(() => {
    receivableApi.capabilities.mockResolvedValue({ query: true, add: true, edit: true, importData: true, confirm: true, generate: true, exportData: true, deleteData: true })
    receivableApi.batches.mockResolvedValue([])
    receivableApi.page.mockResolvedValue({
      records: [{
        id: 1,
        seqNo: 1,
        tenantNameRaw: '示例租户甲',
        spaceNameRaw: '示例空间A',
        monthlyRent: 100000,
        monthlyProperty: 20000,
        monthlyTotal: 120000,
        status: 'CONFIRMED'
      }],
      total: 1
    })
  })

  it('使用同一份27列定义并渲染权威金额', async () => {
    const wrapper = mount(ReceivableRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(receivableColumns).toHaveLength(27)
    expect(receivableColumns.map(column => column.label)).toContain('月租金/元')
    expect(wrapper.text()).toContain('示例租户甲')
    expect(wrapper.text()).toContain('100,000.00')
    expect(wrapper.text()).toContain('20,000.00')
    expect(wrapper.text()).toContain('120,000.00')
  })

  it('有错误、待绑定或总计未核对时禁止确认', () => {
    expect(canConfirmReceivableImport({ invalidRows: 1, rows: [] })).toBe(false)
    expect(canConfirmReceivableImport({ invalidRows: 0, totalsReconciled: false, rows: [] })).toBe(false)
    expect(canConfirmReceivableImport({
      invalidRows: 0,
      totalsReconciled: true,
      rows: [{ status: 'NEEDS_BINDING' }]
    })).toBe(false)
    expect(canConfirmReceivableImport({
      invalidRows: 0,
      totalsReconciled: true,
      rows: [{ status: 'VALID' }]
    })).toBe(true)
  })

  it('导入绑定会同时提交租户、空间、房间和合同主数据', () => {
    expect(buildReceivableBinding({
      tenantRefId: '11', spaceId: '12', roomId: '13', contractId: '14'
    })).toEqual({ tenantRefId: 11, spaceId: 12, roomId: 13, contractId: 14 })
    expect(buildReceivableBinding({
      tenantRefId: '11', spaceId: '', roomId: '13', contractId: ''
    })).toEqual({ tenantRefId: 11, spaceId: null, roomId: 13, contractId: null })
  })

  it('查询权限账号不会看到新增、导入和导出操作', async () => {
    receivableApi.capabilities.mockResolvedValue({ query: true, add: false, edit: false, importData: false, confirm: false, generate: false, exportData: false, deleteData: false })
    const wrapper = mount(ReceivableRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).not.toContain('导入工作簿')
    expect(wrapper.text()).not.toContain('新增')
    expect(wrapper.text()).not.toContain('导出 Excel')
  })
})
