import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'

const mockSummary = vi.fn()
const mockPage = vi.fn()
const mockClaim = vi.fn()
const mockVisitCheckin = vi.fn()
const mockCheckins = vi.fn()
const mockShopLookup = vi.fn()
const mockAmapRadar = vi.fn()
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
    visitCheckin: (...args) => mockVisitCheckin(...args),
    checkins: (...args) => mockCheckins(...args),
    shopLookup: (...args) => mockShopLookup(...args),
    amapRadar: (...args) => mockAmapRadar(...args),
    firstTouch: (...args) => mockFirstTouch(...args),
    washRows: (...args) => mockWashRows(...args),
    washBatch: vi.fn().mockResolvedValue({ batchNo: 'WASH-1', status: 'RUNNING', totalRows: 60, processedRows: 20 }),
    feedWarehouses: (...args) => mockFeed(...args),
    batchFeedWarehouses: vi.fn(),
    release: vi.fn()
  },
  mktConnectorHubApi: {
    pullLeads: vi.fn().mockResolvedValue({ status: 'STANDBY', message: '待开通' }),
    createShopVerifyInvite: vi.fn().mockResolvedValue({ invitePath: '/open/shop-verify/sv_test', modeHint: '测试' }),
    listShopVerifications: vi.fn().mockResolvedValue([]),
    privacyCall: vi.fn().mockResolvedValue({ status: 'STANDBY', message: '待开通' })
  },
  mktWarehouseApi: {
    page: vi.fn().mockResolvedValue({ records: [{ id: 10, name: '广州番禺一号云仓' }] })
  }
}))

vi.mock('@/api/file', () => ({
  fileApi: { upload: vi.fn().mockResolvedValue({ id: 77 }) }
}))

vi.mock('@/components/PhotoCapture.vue', async () => {
  const { h } = await import('vue')
  return {
    default: {
      emits: ['capture'],
      setup(_, { emit }) {
        return () => h('button', {
          class: 'stub-photo-capture',
          type: 'button',
          onClick: () => emit('capture', new File(['x'], 'door.jpg', { type: 'image/jpeg' }))
        }, '拍照')
      }
    }
  }
})

vi.mock('../CustomerQuotePanel.vue', () => ({
  default: { template: '<div class="stub-quote-panel">多仓比价面板</div>' }
}))

import PublicSea from '../PublicSea.vue'

describe('PublicSea.vue 广东公海双视图、四卡三层下钻与500m打卡交互', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockSummary.mockResolvedValue({
      fetchedAt: '2026-11-25 15:58:00',
      activeTotal: 68,
      calcHotCount: 16,
      feiyuAdCount: 6,
      amapPoiCount: 12,
      chanmamaCount: 28,
      tungeeJobCount: 4,
      expireReturnCount: 5,
      parkCrmCount: 3,
      zoneCounts: { GZ: 32, DG: 18, FS: 10, SZ: 5, YD: 3 },
      within5kmCount: 39,
      clusterBuildingCount: 5,
      totalLinkedShops: 126,
      dishonestRiskCount: 1,
      visitProtectedCount: 9,
      washTotalRows: 120,
      washInsertedRows: 68,
      blockedPrivateLock: 15,
      blockedWhHmac: 9,
      blockedDupAgency: 6,
      skippedOutProvince: 22,
      qccCacheHits: 31,
      qccApiCalls: 15,
      adminClaimedTotal: 10,
      adminTouchedCount: 9,
      whClaimedTotal: 5,
      whTouchedCount: 4,
      nearExpireCount: 1,
      slaHours: 72,
      whDailyCap: 5,
      whActiveCap: 10,
      checkinMaxMeters: 500,
      visitProtectDays: 15,
      connectors: {
        regionLockProvince: '广东省',
        amapWebConfigured: false,
        amapJsConfigured: false,
        amapJsKey: '',
        qccConfigured: false
      }
    })
    mockPage.mockResolvedValue({
      total: 1,
      records: [
        {
          customerId: 901,
          name: '广州衣恋风尚服饰电子商务有限公司',
          shopName: '衣恋广派女装旗舰店',
          shops: [
            { platform: '抖音', shopName: '衣恋广派女装旗舰店', rankNo: 18 },
            { platform: '1688', shopName: '广州番禺衣恋制衣厂' }
          ],
          zoneCode: 'GZ',
          city: '广州市',
          district: '番禺区',
          address: '广州市番禺区南村万博直播电商大厦802室',
          buildingName: '南村万博直播电商大厦',
          buildingPeerCount: 4,
          lng: 113.34921,
          lat: 23.02912,
          nearestWhName: '广州番禺一号云仓',
          distNearestWhKm: 3.1,
          qccAligned: 1,
          legalPerson: '林晓峰',
          regCapital: '200万',
          insuredCount: 14,
          dishonestCount: 0,
          contact: '林总',
          phone: '138****8821',
          phoneUnlocked: false,
          seaSource: 'CALC_HOT',
          category: '女装服饰',
          estDailyOrders: 1400,
          avgWeightKg: 0.42,
          urgencyLevel: 'S',
          urgencyReason: '30秒算价器主动留资热询盘',
          blockNote: '十三行爆单慢发被扣分，期望本周试发500件',
          claimStatus: 'UNCLAIMED'
        }
      ]
    })
    mockClaim.mockResolvedValue({ customerId: 901, phone: '13826098821', hoursLeft: 72 })
    mockVisitCheckin.mockResolvedValue({ customerId: 901, distanceMeters: 42, visitProtectDays: 15 })
    mockCheckins.mockResolvedValue([])
  })

  it('渲染诚实连接器状态条、广东分区快切、4张L1-L3下钻卡，并支持双视图切换与认领/500m打卡', async () => {
    const wrapper = mount(PublicSea, {
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    // 顶部 4 张分析卡
    const cards = wrapper.findAll('.analysis-card')
    expect(cards).toHaveLength(4)
    expect(cards[0].text()).toContain('68 户')
    expect(cards[1].text()).toContain('39 户')
    expect(cards[2].text()).toContain('126 店')
    expect(cards[3].text()).toContain('86.7%')

    // 诚实未配 Key 提示可见
    expect(wrapper.find('.connector-honest-bar').exists()).toBe(true)

    // 桌面表格与手机自适应卡片同时存在
    expect(wrapper.find('.desktop-table-wrap').exists()).toBe(true)
    expect(wrapper.findAll('.mobile-sea-card')).toHaveLength(1)
    expect(wrapper.text()).toContain('广州衣恋风尚服饰电子商务有限公司')
    expect(wrapper.text()).toContain('南村万博直播电商大厦(同楼4户)')

    // 切换广东东莞战区触发带 zoneCode=DG 的分页查询
    await wrapper.find('[data-zone="DG"]').trigger('click')
    await flushPromises()
    expect(mockPage).toHaveBeenLastCalledWith(expect.objectContaining({ zoneCode: 'DG' }))

    // 切换至广东高德地推地图视图
    await wrapper.find('[data-testid="mode-map"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="gd-map-workspace"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('云仓 5km 黄金揽收圈')

    // 切回列表视图并测试下钻与认领
    await wrapper.find('[data-testid="mode-list"]').trigger('click')
    await flushPromises()
    await wrapper.find('.drilldown-trigger').trigger('click')
    await flushPromises()
    expect(document.body.innerHTML).toContain('L2 判定逻辑')
    expect(document.body.innerHTML).toContain('巨量飞鱼免费 Webhook')

    const claimBtn = wrapper.findAll('button').find(b => b.text().includes('认领电销'))
    expect(claimBtn).toBeTruthy()
    await claimBtn.trigger('click')
    await flushPromises()
    expect(mockClaim).toHaveBeenCalledWith(901)

    // 打开现场打卡弹窗：未定位、未拍照时不能提交；开发环境模拟到门头 + 拍照 + 纪要后提交
    await wrapper.find('[data-testid="btn-visit-checkin"]').trigger('click')
    await flushPromises()
    const submitBtn = () => wrapper.find('[data-testid="btn-submit-checkin"]')
    expect(submitBtn().attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('/uploads/checkin/door-live.jpg')

    await wrapper.find('[data-testid="btn-mock-near-gps"]').trigger('click')
    await wrapper.find('.stub-photo-capture').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="checkin-photo-ok"]').exists()).toBe(true)
    await wrapper.find('textarea[placeholder*="现场面见林老板"]').setValue('现场面见老板核实单量')
    await flushPromises()
    expect(submitBtn().attributes('disabled')).toBeUndefined()
    await submitBtn().trigger('click')
    await flushPromises()
    expect(mockVisitCheckin).toHaveBeenCalledWith(901, expect.objectContaining({ photoUrl: 'file:77' }))
  })

  it('大名单正式导入转后台时显示进度条，而不是直接提示完成', async () => {
    mockWashRows.mockResolvedValue({ async: true, status: 'RUNNING', batchNo: 'WASH-1', totalRows: 60, processedRows: 0, message: '已转为后台处理' })
    const wrapper = mount(PublicSea, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const openBtn = wrapper.findAll('button').find(b => b.text().includes('类目TOP500店铺榜对齐入池'))
    await openBtn.trigger('click')
    await flushPromises()
    await wrapper.find('textarea[placeholder*="广州白云悦颜生物科技有限公司"]').setValue('广州甲公司,13700000001,个人洗护,100,4.5,广州市白云区')
    const submit = wrapper.findAll('button').find(b => b.text().includes('确认洗数入池'))
    await submit.trigger('click')
    await flushPromises()
    expect(mockWashRows).toHaveBeenCalledWith(expect.objectContaining({ dryRun: false }))
    expect(wrapper.find('[data-testid="wash-progress"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('后台导入中')
    wrapper.unmount()
  })
})
