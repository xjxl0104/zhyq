import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockDetail = vi.fn()
const mockSubmitVerify = vi.fn()
const mockEstimate = vi.fn()
const mockSubmitLead = vi.fn()

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { token: 'sv_demo_token' }, query: { projectId: '1' } })
}))

vi.mock('@/api/publicMarketing', () => ({
  mktPublicOpenApi: {
    shopVerifyDetail: (...args) => mockDetail(...args),
    shopVerifySubmit: (...args) => mockSubmitVerify(...args),
    calcEstimate: (...args) => mockEstimate(...args),
    calcSubmitLead: (...args) => mockSubmitLead(...args)
  }
}))

import ShopVerifyOpen from '../ShopVerifyOpen.vue'
import PublicCalcOpen from '../PublicCalcOpen.vue'

describe('免登录公开获客与扫码验店页面回归', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDetail.mockResolvedValue({
      platform: 'DOUDIAN',
      shopName: '衣恋广派女装旗舰店',
      status: 'PENDING',
      expiresAt: '2026-11-29T18:00:00',
      expired: false,
      submittable: true
    })
    mockSubmitVerify.mockResolvedValue({
      verifyToken: 'sv_demo_token',
      status: 'SELF_REPORTED',
      verified30dOrders: 24000,
      verifiedDailyAvg: 800
    })
    mockEstimate.mockResolvedValue({
      lowPrice: '2.08',
      highPrice: '2.38',
      matchedWarehouseCount: 4,
      packersEquivalent: 4,
      estimatedYearlySaving: 82000
    })
    mockSubmitLead.mockResolvedValue({
      lowPrice: '2.08',
      highPrice: '2.38',
      matchedWarehouseCount: 4
    })
  })

  it('ShopVerifyOpen 诚实展示货主自报说明并完成单次提交', async () => {
    const w = mount(ShopVerifyOpen)
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledWith('sv_demo_token')
    expect(w.find('[data-testid="self-report-notice"]').text()).toContain('未经官方开放平台自动核验')
    expect(w.find('[data-testid="verify-form"]').exists()).toBe(true)

    await w.find('[data-testid="input-orders30d"]').setValue('24000')
    await w.find('[data-testid="input-weight"]').setValue('0.45')
    await w.find('[data-testid="input-return-rate"]').setValue('11.5')
    await w.find('[data-testid="verify-form"]').trigger('submit')
    await flushPromises()

    expect(mockSubmitVerify).toHaveBeenCalledWith('sv_demo_token', expect.objectContaining({
      orders30d: 24000,
      avgWeightKg: 0.45,
      returnRatePct: 11.5
    }))
    expect(w.find('[data-testid="verify-done-state"]').exists()).toBe(true)
    expect(w.text()).toContain('800')
  })

  it('PublicCalcOpen 实时测算一口价区间并校验手机号留资入池', async () => {
    const w = mount(PublicCalcOpen)
    await flushPromises()
    expect(mockEstimate).toHaveBeenCalled()
    expect(w.find('[data-testid="calc-quote-card"]').text()).toContain('2.08')

    await w.find('[data-testid="calc-shop-name"]').setValue('广州十三行衣恋女装')
    await w.find('[data-testid="calc-contact"]').setValue('林总')
    await w.find('[data-testid="calc-phone"]').setValue('13826098821')
    await w.find('[data-testid="calc-lead-form"]').trigger('submit')
    await flushPromises()

    expect(mockSubmitLead).toHaveBeenCalledWith(expect.objectContaining({
      shopName: '广州十三行衣恋女装',
      phone: '13826098821'
    }))
    expect(w.find('[data-testid="calc-lead-done"]').exists()).toBe(true)
  })
})
