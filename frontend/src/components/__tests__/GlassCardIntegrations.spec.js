import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import Dashboard from '@/views/dashboard/Index.vue'
import AppCenter from '@/views/app/AppCenter.vue'

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    workbench: vi.fn().mockResolvedValue({}),
    overview: vi.fn().mockResolvedValue({
      room: { rented: 8, total: 10, rentRate: 80 },
      contract: { executing: 6 },
      other: { tenantTotal: 5 },
    }),
    revenueTrend: vi.fn().mockResolvedValue({ months: [], receivable: [], received: [] }),
  },
}))

vi.mock('@/api/todo', () => ({ todoApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/utils/request', () => ({ default: { get: vi.fn().mockResolvedValue({ records: [] }) } }))
vi.mock('@/composables/useChart', () => ({
  useChart: () => ({ refresh: vi.fn(), getChart: vi.fn() }),
}))

vi.mock('@/api/app', () => ({
  appApi: {
    list: vi.fn().mockResolvedValue([{ id: 2, name: '资产管理', category: '物业', description: '管理园区资产' }]),
    categories: vi.fn().mockResolvedValue(['物业']),
    favoriteList: vi.fn().mockResolvedValue([{ id: 1, name: '工作台', category: '常用' }]),
    addFavorite: vi.fn(),
    removeFavorite: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const container = {
  template: '<div><slot name="header" /><slot /></div>',
}

const stubs = {
  'el-row': container,
  'el-col': container,
  'el-card': container,
  'el-icon': container,
  'el-button': container,
  'el-empty': true,
  'el-timeline': container,
  'el-timeline-item': container,
  'el-input': container,
  'el-tabs': container,
  'el-tab-pane': true,
  'el-tag': container,
  ArrowRight: true,
  StarFilled: true,
  Star: true,
  Close: true,
  Grid: true,
  Search: true,
}

describe('glass display card integrations', () => {
  it('uses glass only for dashboard metrics and overview cards', async () => {
    const wrapper = mount(Dashboard, {
      global: { stubs, mocks: { $router: { push: vi.fn() } } },
    })
    await flushPromises()

    expect(wrapper.findAll('.metric.glass-surface--card')).toHaveLength(7)
    expect(wrapper.findAll('.ov-card.glass-surface--card')).toHaveLength(4)
    expect(wrapper.find('.alarm-card.glass-surface').exists()).toBe(false)
    expect(wrapper.find('.quick.glass-surface').exists()).toBe(false)
  })

  it('uses glass for the two App Center display sections, not every app item', async () => {
    const wrapper = mount(AppCenter, { global: { stubs } })
    await flushPromises()

    expect(wrapper.findAll('.fav-card.glass-surface--card')).toHaveLength(1)
    expect(wrapper.findAll('.app-card.glass-surface--card')).toHaveLength(1)
    expect(wrapper.find('.fav-item.glass-surface').exists()).toBe(false)
    expect(wrapper.find('.app-item.glass-surface').exists()).toBe(false)
  })
})
