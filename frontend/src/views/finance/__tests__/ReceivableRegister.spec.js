import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ReceivableRegister from '../ReceivableRegister.vue'
import { canConfirmReceivableImport, receivableColumns } from '../receivableModel'
import { receivableApi } from '@/api/receivable'

vi.mock('@/api/receivable', () => ({
  receivableApi: {
    page: vi.fn(),
    remove: vi.fn(),
    generate: vi.fn()
  }
}))

describe('应收明细登记表', () => {
  beforeEach(() => {
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
})
