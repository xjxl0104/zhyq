import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DataCenter from '../DataCenter.vue'
import { dashboardApi } from '@/api/dashboard'

const setOption = vi.fn()
vi.mock('echarts', () => ({ init: vi.fn(() => ({ setOption })) }))
vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    overview: vi.fn(), roomStatus: vi.fn(), revenueTrend: vi.fn(), workOrderCategory: vi.fn()
  }
}))

describe('数据中心经营收入来源', () => {
  beforeEach(() => {
    dashboardApi.overview.mockResolvedValue({
      finance: {}, contract: {}, device: {},
      incomeSources: { rentPropertyBilled: 120000, rentPropertyReceived: 100000, vendingSales: 3500.5 }
    })
    dashboardApi.roomStatus.mockResolvedValue([])
    dashboardApi.revenueTrend.mockResolvedValue({ months: [], receivable: [], received: [] })
    dashboardApi.workOrderCategory.mockResolvedValue([])
  })

  it('将租费与售货机作为不同经营口径展示', async () => {
    const wrapper = mount(DataCenter, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('经营收入来源')
    expect(wrapper.text()).toContain('租金及物业应收')
    expect(wrapper.text()).toContain('租金及物业实收')
    expect(wrapper.text()).toContain('售货机销售')
    expect(wrapper.text()).toContain('120,000')
    expect(wrapper.text()).toContain('3,501')
  })
})
