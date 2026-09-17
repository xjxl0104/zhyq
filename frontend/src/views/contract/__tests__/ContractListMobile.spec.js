import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ContractList from '../ContractList.vue'
import { contractApi } from '@/api/contract'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/contract', () => ({ contractApi: { page: vi.fn() } }))
vi.mock('@/api/tenant', () => ({ tenantApi: { list: vi.fn().mockResolvedValue([{ id: 3, name: '测试租客' }]) } }))
vi.mock('@/api/building', () => ({ projectApi: { list: vi.fn().mockResolvedValue([{ id: 4, name: '测试园区' }]) } }))
vi.mock('@/api/file', () => ({ fileApi: {} }))

let mobile = true
const rows = [
  { id: 17, code: 'CONTRACT-017', tenantRefId: 3, projectId: 4, startDate: '2026-09-01', endDate: '2027-08-31', rentPrice: 20, rentArea: 100.25, deposit: 12345.6, status: 1 },
  { id: 18, code: 'CONTRACT-018', tenantRefId: 3, projectId: 4, rentPrice: 30, rentArea: 200.5, deposit: 1000.5, status: 5 }
]
const wrappers = []
function mountPage() {
  const wrapper = shallowMount(ContractList, {
    global: {
      stubs: {
        MobileRecordList: false, FileUpload: true,
        ElPopconfirm: { template: '<div><slot name="reference" /></div>' },
        Search: true, Plus: true
      }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mobile = true
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn()
  })))
  contractApi.page.mockResolvedValue({ records: rows.map(row => ({ ...row })), total: 200 })
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.unstubAllGlobals()
})

describe('合同手机版保留主线台账能力', () => {
  it('保留本页合同数、面积和保证金合计及金额格式', async () => {
    const wrapper = mountPage()
    await flushPromises()
    const summary = wrapper.get('.mobile-page-summary').text()
    expect(summary).toContain('本页合计 · 2 份')
    expect(summary).toContain('面积 300.75 ㎡')
    expect(summary).toContain('保证金 13,346.10 元')
    const first = wrapper.get('[data-record-key="17"]')
    const fields = Object.fromEntries(first.findAll('.mobile-record-field').map(field => [
      field.get('dt').text(), field.get('dd').text()
    ]))
    expect(fields).toMatchObject({
      序号: '1', 合同编号: 'CONTRACT-017', 租客: '测试租客', 园区: '测试园区',
      起止日期: '2026-09-01 ~ 2027-08-31', 租赁单价: '20 元/㎡', 面积: '100.25 ㎡', 保证金: '12,345.60 元', 状态: '草稿'
    })
  })

  it('各状态只显示主线允许的审批及退租动作，并保留详情、编辑和删除', async () => {
    contractApi.page.mockResolvedValue({
      records: Array.from({ length: 10 }, (_, index) => ({ ...rows[0], id: index + 1, code: `C-${index + 1}`, status: index + 1 })),
      total: 10
    })
    const wrapper = mountPage()
    await flushPromises()
    for (let status = 1; status <= 10; status++) {
      const card = wrapper.get(`[data-record-key="${status}"]`)
      expect(card.find(`[aria-label="提交合同 C-${status} 审批"]`).exists()).toBe(status === 1)
      expect(card.find(`[aria-label="审批通过合同 C-${status}"]`).exists()).toBe(status === 2)
      expect(card.find(`[aria-label="办理合同 C-${status} 退租"]`).exists()).toBe(status === 5)
      expect(card.find(`[aria-label="查看合同 C-${status} 详情"]`).exists()).toBe(true)
      expect(card.find(`[aria-label="编辑合同 C-${status}"]`).exists()).toBe(true)
      expect(card.find(`[aria-label="删除合同 C-${status}"]`).exists()).toBe(true)
    }
  })

  it('桌面仍使用带本页合计的原表格', async () => {
    mobile = false
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.mobile-record-list').exists()).toBe(false)
    const table = wrapper.findComponent({ name: 'ElTable' })
    expect(table.props('showSummary')).toBe(true)
    expect(table.props('summaryMethod')({ columns: Array(10).fill({}) }))
      .toEqual(['本页合计 · 2 份', '', '', '', '', '', '300.75 ㎡', '13,346.10 元', '', ''])
  })
})
