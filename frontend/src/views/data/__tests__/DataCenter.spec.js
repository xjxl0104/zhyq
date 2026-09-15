import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import * as icons from '@element-plus/icons-vue'
import { KeepAlive, h, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import DataCenter from '../DataCenter.vue'
import { dashboardApi } from '@/api/dashboard'
import { alarmApi } from '@/api/iot'
import { analysisApi } from '@/api/crm'

const setOption = vi.fn()
vi.mock('echarts', () => ({ init: vi.fn(() => ({ setOption, resize: vi.fn(), dispose: vi.fn() })) }))
vi.mock('@/api/iot', () => ({ alarmApi: { page: vi.fn() } }))
vi.mock('@/api/crm', () => ({ analysisApi: { funnel: vi.fn() } }))
vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    overview: vi.fn(), roomStatus: vi.fn(), revenueTrend: vi.fn(), workOrderCategory: vi.fn()
  }
}))

const global = { plugins: [ElementPlus], components: icons, stubs: { RouterLink: { template: '<a><slot /></a>' } } }
let wrapper

describe('统一数据看板', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    dashboardApi.overview.mockResolvedValue({
      finance: {}, contract: {}, device: { online: 18, total: 20 },
      room: { rentRate: 87.5, rented: 35, total: 40 }, other: { workOrderOpen: 9 },
      incomeSources: { rentPropertyBilled: 120000, rentPropertyReceived: 100000, vendingSales: 3500.5 }
    })
    dashboardApi.roomStatus.mockResolvedValue([])
    dashboardApi.revenueTrend.mockResolvedValue({ months: [], receivable: [], received: [] })
    dashboardApi.workOrderCategory.mockResolvedValue([])
    alarmApi.page.mockResolvedValue({ total: 7, records: [{ id: 1, level: 1, status: 1, content: '设备温度预警', location: '一号楼' }] })
    analysisApi.funnel.mockResolvedValue([{ name: '总线索', value: 70 }, { name: '跟进中', value: 22 }, { name: '意向', value: 13 }, { name: '已转化', value: 8 }])
  })
  afterEach(() => { wrapper?.unmount(); wrapper = null; vi.useRealTimers(); vi.restoreAllMocks() })

  it('将租费与售货机作为不同经营口径展示', async () => {
    wrapper = mount(DataCenter, { global })
    await flushPromises()

    expect(wrapper.text()).toContain('经营收入来源')
    expect(wrapper.text()).toContain('租金及物业应收')
    expect(wrapper.text()).toContain('租金及物业实收')
    expect(wrapper.text()).toContain('售货机销售')
    expect(wrapper.text()).toContain('120,000')
    expect(wrapper.text()).toContain('3,501')
  })

  it('在同一看板保留出租、工单、告警和实际招商阶段数据', async () => {
    wrapper = mount(DataCenter, { global })
    await flushPromises()
    expect(wrapper.get('[aria-label="园区运营指标"]').text()).toContain('87.5')
    expect(wrapper.get('[aria-label="园区运营指标"]').text()).toContain('35 / 40')
    expect(wrapper.get('[aria-label="园区运营指标"]').text()).toContain('9')
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('设备温度预警')
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('低')
    expect(wrapper.get('[aria-label="招商转化"]').text()).toContain('13')
    expect(alarmApi.page).toHaveBeenCalledWith({ pageNo: 1, pageSize: 4, status: 1 })
  })

  it('无待确认事件时将回退记录明确标为最近告警', async () => {
    alarmApi.page.mockResolvedValueOnce({ records: [], total: 0 }).mockResolvedValueOnce({ records: [{ id: 3, level: 2, status: 5, content: '已关闭事件' }], total: 1 })
    wrapper = mount(DataCenter, { global })
    await flushPromises()
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('最近告警')
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('已关闭事件')
    expect(wrapper.get('[aria-label="实时告警"]').text()).not.toContain('待确认')
  })

  it('告警接口失败时显示失败状态而不是暂无告警，并能随手动刷新恢复', async () => {
    alarmApi.page.mockRejectedValueOnce(new Error('offline'))
    wrapper = mount(DataCenter, { global })
    await flushPromises()
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('加载失败')
    expect(wrapper.get('[aria-label="实时告警"]').text()).not.toContain('暂无告警')
    await wrapper.get('[data-testid="refresh-dashboard"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="实时告警"]').text()).toContain('设备温度预警')
    expect(wrapper.get('[aria-label="实时告警"]').text()).not.toContain('加载失败')
  })

  it('进入、离开和返回缓存页面时只启动一组刷新任务', async () => {
    const visible = ref(true)
    wrapper = mount({ setup: () => () => h(KeepAlive, null, { default: () => visible.value ? h(DataCenter) : h('div') }) }, { global })
    await flushPromises()
    expect(dashboardApi.overview).toHaveBeenCalledTimes(1)
    expect(alarmApi.page).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(15000)
    expect(alarmApi.page).toHaveBeenCalledTimes(2)
    visible.value = false
    await flushPromises()
    await vi.advanceTimersByTimeAsync(120000)
    expect(alarmApi.page).toHaveBeenCalledTimes(2)
    expect(dashboardApi.overview).toHaveBeenCalledTimes(1)
    visible.value = true
    await flushPromises()
    expect(alarmApi.page).toHaveBeenCalledTimes(3)
    expect(dashboardApi.overview).toHaveBeenCalledTimes(2)
  })

  it('一键全屏只展示本看板，Esc 退出后按钮恢复', async () => {
    wrapper = mount(DataCenter, { global, attachTo: document.body })
    await flushPromises()
    let fullscreenElement = null
    Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => null })
    vi.spyOn(document, 'fullscreenElement', 'get').mockImplementation(() => fullscreenElement)
    const board = wrapper.element
    board.requestFullscreen = vi.fn(async () => {
      fullscreenElement = board
      document.dispatchEvent(new Event('fullscreenchange'))
    })
    const button = wrapper.get('[data-testid="fullscreen-dashboard"]')
    await button.trigger('click')
    await flushPromises()
    expect(board.requestFullscreen).toHaveBeenCalledTimes(1)
    expect(button.text()).toContain('退出全屏')
    fullscreenElement = null
    document.dispatchEvent(new Event('fullscreenchange'))
    await flushPromises()
    expect(button.text()).toContain('全屏展示')
  })
})
