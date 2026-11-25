import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import { buildConnectorHubDrilldownCards } from '../connectorHubDrilldown'

const mockOverview = vi.fn()
const mockSave = vi.fn()
const mockPing = vi.fn()

vi.mock('@/api/marketing', () => ({
  mktConnectorHubApi: {
    overview: (...args) => mockOverview(...args),
    save: (...args) => mockSave(...args),
    ping: (...args) => mockPing(...args)
  }
}))

import ConnectorHubPanel from '../ConnectorHubPanel.vue'

describe('ConnectorHubPanel.vue 7大域18通道外部数据连接器中心', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockOverview.mockResolvedValue({
      summary: {
        fetchedAt: '2026-11-25 17:15:00',
        totalConnectors: 18,
        activeConnectors: 5,
        freeConnectors: 8,
        paidConnectors: 10,
        fusedConnectors: 0,
        monthUsedTotal: 142,
        blockedOutGdTotal: 48,
        cacheHitTotal: 92,
        estimatedSavedYuan: 28.0,
        adInboxTotal: 26,
        shopVerifiedCount: 7,
        waybillCheckedCount: 15,
        waybillFakeCount: 0,
        invoiceVerifiedCount: 4
      },
      connectors: [
        {
          connectorCode: 'QCC_PRO',
          categoryCode: 'CORP',
          displayName: '企查查官方开放平台（旗舰工商+司法失信+名下网店）',
          feeTier: 'PAID_CALL',
          feeDesc: '商业按次 · 约¥0.15~0.30/次',
          description: '穿透统一信用代码、法人高管、失信限高与名下网店',
          enabled: false,
          gdLockOnly: true,
          monthlyQuotaLimit: 2000,
          monthUsedCount: 0,
          fields: ['appKey', 'secretKey'],
          credentials: { appKey: '', secretKey: '' },
          configured: false,
          applyUrl: 'https://openapi.qcc.com',
          applyGuide: '认证企查查开放平台拿 AppKey 与 SecretKey',
          healthStatus: 'STANDBY'
        },
        {
          connectorCode: 'AD_FEIYU',
          categoryCode: 'AD',
          displayName: '巨量引擎「飞鱼 CRM」广告线索 Webhook（抖音）',
          feeTier: 'FREE',
          feeDesc: '¥0免费实时推送',
          description: '抖音表单0秒直推公海',
          enabled: true,
          gdLockOnly: false,
          monthlyQuotaLimit: 999999,
          monthUsedCount: 26,
          fields: ['signatureSecret'],
          credentials: { signatureSecret: '' },
          configured: true,
          applyUrl: 'https://feiyu.oceanengine.com',
          applyGuide: '配置飞鱼Webhook回调',
          healthStatus: 'READY'
        }
      ]
    })
    mockSave.mockResolvedValue({ connectorCode: 'QCC_PRO', enabled: true })
    mockPing.mockResolvedValue({ ok: true, message: '自检正常' })
  })

  it('同源装配 3 张 L1-L3 下钻卡，渲染连接器卡片并支持填 Key 点亮与导出同事申领单', async () => {
    const cards = buildConnectorHubDrilldownCards({
      totalConnectors: 18,
      activeConnectors: 5,
      freeConnectors: 8,
      paidConnectors: 10,
      blockedOutGdTotal: 50,
      cacheHitTotal: 150,
      estimatedSavedYuan: 40
    })
    expect(cards).toHaveLength(3)
    expect(cards[0].primaryText).toBe('5 / 18 通道')
    expect(cards[1].primaryText).toBe('估算省 ¥40.00')

    const wrapper = mount(ConnectorHubPanel, {
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    expect(wrapper.findAll('.hub-kpi-card')).toHaveLength(3)
    expect(wrapper.findAll('.conn-card')).toHaveLength(2)
    expect(wrapper.text()).toContain('企查查官方开放平台')
    expect(wrapper.text()).toContain('○ 未开通(¥0)')

    // 点击「一键生成同事分批申领任务单」
    await wrapper.find('[data-testid="btn-batch-colleague-sheet"]').trigger('click')
    await flushPromises()
    expect(wrapper.html()).toContain('外部数据接口分批申领总清单')

    // 点击企查查卡片「填Key点亮」并保存
    await wrapper.find('[data-conn="QCC_PRO"] [data-testid="btn-cfg-conn"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="btn-save-conn"]').trigger('click')
    await flushPromises()
    expect(mockSave).toHaveBeenCalledWith('QCC_PRO', expect.objectContaining({
      monthlyQuotaLimit: 2000
    }))
  })
})
