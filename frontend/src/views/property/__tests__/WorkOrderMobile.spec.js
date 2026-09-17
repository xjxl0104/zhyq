import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WorkOrder from '../WorkOrder.vue'
import { workOrderApi } from '@/api/property'

const route = { path: '/property/workorder', query: { highlightId: '17' } }
let mobile = true

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))

vi.mock('@/api/property', () => ({
  workOrderApi: {
    page: vi.fn(),
    stats: vi.fn(),
    get: vi.fn(),
    add: vi.fn(),
    update: vi.fn(),
    dispatch: vi.fn(),
    accept: vi.fn(),
    arrive: vi.fn(),
    finish: vi.fn(),
    verify: vi.fn(),
    close: vi.fn()
  },
  responsibleUnitApi: { options: vi.fn().mockResolvedValue([]) }
}))

vi.mock('@/api/system', () => ({ userApi: { list: vi.fn().mockResolvedValue([]) } }))
vi.mock('@/api/file', () => ({ fileApi: { attach: vi.fn() } }))

const row = {
  id: 17,
  code: 'WO-017',
  title: '空调漏水',
  location: 'A座 3层',
  category: '空调',
  urgency: 3,
  status: 1,
  assignee: '',
  createTime: '2026-09-17 10:00:00'
}

function mountPage() {
  return shallowMount(WorkOrder, {
    global: {
      stubs: { MobileRecordList: false, FileUpload: true, Search: true, Plus: true }
    }
  })
}

describe('工单移动列表', () => {
  afterEach(() => vi.unstubAllGlobals())

  beforeEach(() => {
    mobile = true
    route.query = { highlightId: '17' }
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: mobile,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn()
    })))
    workOrderApi.page.mockResolvedValue({ records: [row], total: 1 })
    workOrderApi.stats.mockResolvedValue({ pendingDispatch: 1, processing: 0, done: 0, total: 1 })
  })

  it('keeps the source-located work order highlighted on its mobile card', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('[data-record-key="17"]').classes()).toContain('source-highlight-row')
  })

  it('names repeated details and actions with the work-order code', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.mobile-record-details summary').attributes('aria-label'))
      .toBe('查看工单 WO-017 全部信息')
    expect(wrapper.get('.mobile-record-card__actions [type="info"]').attributes('aria-label'))
      .toBe('查看工单 WO-017 详情')
  })

  it('collapses filters on phones and leaves them open on desktop', async () => {
    const mobileWrapper = mountPage()
    await flushPromises()
    expect(mobileWrapper.get('details.search-bar').attributes('open')).toBeUndefined()
    mobileWrapper.unmount()

    mobile = false
    route.query = {}
    const desktopWrapper = mountPage()
    await flushPromises()
    expect(desktopWrapper.get('details.search-bar').attributes()).toHaveProperty('open')
  })
})
