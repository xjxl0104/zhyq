import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'

const mockSummary = vi.fn()
const mockPage = vi.fn()
const mockClaim = vi.fn()
const mockFirstTouch = vi.fn()
const mockWashRows = vi.fn()
const mockFeed = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/utils/permission', () => ({
  hasPermission: () => true
}))

vi.mock('@/api/marketing', () => ({
  mktPublicSeaApi: {
    summary: (...args) => mockSummary(...args),
    page: (...args) => mockPage(...args),
    claim: (...args) => mockClaim(...args),
    firstTouch: (...args) => mockFirstTouch(...args),
    washRows: (...args) => mockWashRows(...args),
    feedWarehouses: (...args) => mockFeed(...args),
    release: vi.fn()
  },
  mktWarehouseApi: {
    page: vi.fn().mockResolvedValue({ records: [{ id: 10, name: '星逸一号云仓' }] })
  }
}))

vi.mock('../CustomerQuotePanel.vue', () => ({
  default: { template: '<div class="stub-quote-panel">多仓比价面板</div>' }
}))

import PublicSea from '../PublicSea.vue'

describe('PublicSea.vue 内部专属公海池交互与下钻', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockSummary.mockResolvedValue({
      fetchedAt: '2026-11-25 01:05:00',
      activeTotal: 42,
      calcHotCount: 12,
      chanmamaCount: 18,
      tungeeJobCount: 6,
      expireReturnCount: 4,
      parkCrmCount: 2,
      washTotalRows: 100,
      washInsertedRows: 70,
      blockedPrivateLock: 15,
      blockedWhHmac: 9,
      blockedDupAgency: 6,
      adminClaimedTotal: 10,
      adminTouchedCount: 9,
      whClaimedTotal: 5,
      whTouchedCount: 4,
      nearExpireCount: 1,
      slaHours: 72,
      whDailyCap: 5,
      whActiveCap: 10
    })
    mockPage.mockResolvedValue({
      total: 1,
      records: [
        {
          customerId: 901,
          name: '杭州衣恋女装有限公司',
          shopName: '衣恋抖店',
          contact: '林总',
          phone: '138****8821',
          phoneUnlocked: false,
          seaSource: 'CALC_HOT',
          category: '女装服饰',
          estDailyOrders: 1200,
          avgWeightKg: 0.45,
          urgencyLevel: 'S',
          urgencyReason: '30秒算价器主动留资热询盘',
          blockNote: '期望48h内500件爆款分仓试发',
          claimStatus: 'UNCLAIMED'
        }
      ]
    })
    mockClaim.mockResolvedValue({ customerId: 901, phone: '13857198821', hoursLeft: 72 })
  })

  it('渲染顶部 3 张带下钻入口的分析卡与桌面/移动双视图列表，并支持认领解锁', async () => {
    const wrapper = mount(PublicSea, {
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const cards = wrapper.findAll('.analysis-card')
    expect(cards).toHaveLength(3)
    expect(cards[0].text()).toContain('42 户')
    expect(cards[1].text()).toContain('30.0%')
    expect(cards[2].text()).toContain('72h 首触达履约率')

    // 同时挂载桌面表格容器与移动端自适应卡片容器
    expect(wrapper.find('.desktop-table-wrap').exists()).toBe(true)
    expect(wrapper.findAll('.mobile-sea-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('杭州衣恋女装有限公司')

    // 点击下钻按钮打开 L1/L2/L3 下钻态
    await wrapper.find('.drilldown-trigger').trigger('click')
    await flushPromises()
    expect(document.body.innerHTML).toContain('L2 判定逻辑')
    expect(document.body.innerHTML).toContain('30秒云仓算价器主动留资')

    // 点击认领按钮触发后端原子认领接口
    const claimBtn = wrapper.findAll('button').find(b => b.text().includes('认领电销'))
    expect(claimBtn).toBeTruthy()
    await claimBtn.trigger('click')
    await flushPromises()
    expect(mockClaim).toHaveBeenCalledWith(901)
  })
})
