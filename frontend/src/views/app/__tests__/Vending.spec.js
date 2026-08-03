import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Vending from '../Vending.vue'
import {
  canConfirmVendingImport,
  openExternalVending,
  vendingTypes
} from '../vendingModel'
import { vendingApi } from '@/api/vending'

vi.mock('@/api/vending', () => ({
  vendingApi: {
    config: vi.fn(),
    configurationStatus: vi.fn(),
    openAudit: vi.fn(),
    stats: vi.fn(),
    page: vi.fn(),
    template: vi.fn(),
    preview: vi.fn(),
    exclude: vi.fn(),
    confirm: vi.fn(),
    rollback: vi.fn()
  }
}))

describe('自动售货机整合页', () => {
  beforeEach(() => {
    vendingApi.config.mockResolvedValue({
      externalUrl: 'https://fanmaiji.top/index?isFrom=login',
      apiAvailable: false,
      nativeFormatSupported: false
    })
    vendingApi.stats.mockResolvedValue({ machineCount: 2, onlineCount: 1, openFaultCount: 0, todaySales: 38.5 })
    vendingApi.page.mockResolvedValue({ records: [], total: 0 })
    vendingApi.openAudit.mockResolvedValue()
  })

  it('清楚标注无API边界并展示五类本地数据', async () => {
    const wrapper = mount(Vending, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('厂商暂未提供开放 API')
    expect(wrapper.text()).toContain('仅支持智慧园区标准模板')
    expect(vendingTypes).toHaveLength(5)
    for (const type of vendingTypes) expect(wrapper.text()).toContain(type.label)
  })

  it('厂商入口只以noopener和noreferrer打开HTTPS地址', () => {
    const opener = vi.fn()
    openExternalVending('https://fanmaiji.top/index?isFrom=login', opener)
    expect(opener).toHaveBeenCalledWith(
      'https://fanmaiji.top/index?isFrom=login', '_blank', 'noopener,noreferrer')
    expect(() => openExternalVending('http://fanmaiji.top', opener)).toThrow('HTTPS')
  })

  it('仍有错误或没有有效行时禁止确认', () => {
    expect(canConfirmVendingImport({ validRows: 1, invalidRows: 1 })).toBe(false)
    expect(canConfirmVendingImport({ validRows: 0, invalidRows: 0 })).toBe(false)
    expect(canConfirmVendingImport({ validRows: 2, invalidRows: 0 })).toBe(true)
  })
})
