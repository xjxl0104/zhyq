import { describe, expect, it } from 'vitest'
import { buildPublicSeaDrilldownCards } from '../publicSeaDrilldown'
import { allowedNavigationPaths } from '@/utils/navigationAccess'

describe('publicSeaDrilldown 同源三层下钻装配', () => {
  it('将 summary 同源聚合映射为 3 张带 L2/L3 五联物证的分析卡，零二次偏差', () => {
    const cards = buildPublicSeaDrilldownCards({
      fetchedAt: '2026-11-25 00:55:00',
      activeTotal: 100,
      calcHotCount: 25,
      chanmamaCount: 40,
      tungeeJobCount: 20,
      expireReturnCount: 10,
      parkCrmCount: 5,
      washTotalRows: 200,
      washInsertedRows: 140,
      blockedPrivateLock: 30,
      blockedWhHmac: 18,
      blockedDupAgency: 12,
      adminClaimedTotal: 20,
      adminTouchedCount: 18,
      whClaimedTotal: 10,
      whTouchedCount: 9,
      nearExpireCount: 2,
      slaHours: 72,
      whDailyCap: 5,
      whActiveCap: 10
    })

    expect(cards).toHaveLength(3)
    expect(cards[0].primaryText).toBe('100 户')
    expect(cards[0].badgeText).toBe('🔥 热询盘 25.0%')
    expect(cards[0].rows[0].evidence.formula).toContain('25 ÷ 100')

    // 防撞净拦截率 = (30 + 18 + 12) / 200 = 30.0%
    expect(cards[1].primaryText).toBe('30.0%')
    expect(cards[1].rows[2].evidence.formula).toContain('30.0%')

    // 72h 首触达履约率 = (18 + 9) / (20 + 10) = 90.0%
    expect(cards[2].title).toBe('认领 72h 首触达履约率')
    expect(cards[2].primaryText).toBe('90.0%')
    expect(cards[2].badgeText).toContain('2 户临期弹回')
    expect(cards[2].footText).toContain('日限5/在途10户')
  })

  it('拥有 crm:marketing:customer:query 的内部招商角色自动获得 /crm/marketing/public-sea 导航可见性', () => {
    const paths = allowedNavigationPaths([
      { id: 101, status: 1, type: 3, perm: 'crm:marketing:customer:query', name: '客户查询' }
    ])
    expect(paths.has('/crm/marketing/public-sea')).toBe(true)
  })
})
