import { describe, expect, it } from 'vitest'
import { buildPublicSeaDrilldownCards } from '../publicSeaDrilldown'
import { allowedNavigationPaths } from '@/utils/navigationAccess'

describe('publicSeaDrilldown 四卡同源三层下钻装配', () => {
  it('将 summary 同源聚合映射为 4 张带 L2/L3 五联物证的分析卡，零二次偏差', () => {
    const cards = buildPublicSeaDrilldownCards({
      fetchedAt: '2026-11-25 15:55:00',
      activeTotal: 100,
      calcHotCount: 25,
      feiyuAdCount: 8,
      amapPoiCount: 15,
      chanmamaCount: 40,
      tungeeJobCount: 5,
      expireReturnCount: 10,
      parkCrmCount: 5,
      zoneCounts: { GZ: 48, DG: 26, FS: 14, SZ: 8, YD: 4 },
      within5kmCount: 62,
      clusterBuildingCount: 7,
      totalLinkedShops: 218,
      dishonestRiskCount: 2,
      visitProtectedCount: 14,
      washTotalRows: 200,
      washInsertedRows: 110,
      blockedPrivateLock: 30,
      blockedWhHmac: 18,
      blockedDupAgency: 12,
      skippedOutProvince: 30,
      qccCacheHits: 45,
      qccApiCalls: 35,
      adminClaimedTotal: 20,
      adminTouchedCount: 18,
      whClaimedTotal: 10,
      whTouchedCount: 9,
      nearExpireCount: 2,
      slaHours: 72,
      whDailyCap: 5,
      whActiveCap: 10,
      checkinMaxMeters: 500,
      visitProtectDays: 15
    })

    expect(cards).toHaveLength(4)
    // 卡1：活水池 6源归集
    expect(cards[0].key).toBe('pool')
    expect(cards[0].primaryText).toBe('100 户')
    expect(cards[0].badgeText).toBe('🔥 热单 25.0%')
    expect(cards[0].rows[0].evidence.formula).toContain('25 ÷ 100')

    // 卡2：高德云仓 5km 黄金地推圈
    expect(cards[1].key).toBe('map')
    expect(cards[1].primaryText).toBe('62 户')
    expect(cards[1].badgeText).toContain('7 栋聚集大厦')

    // 卡3：工商网店穿透 & 省钱防撞闸
    expect(cards[2].key).toBe('qcc')
    expect(cards[2].primaryText).toBe('218 店')
    expect(cards[2].badgeText).toContain('排雷 2 户失信')
    expect(cards[2].rows[0].value).toContain('75 次') // 30 外省跳过 + 45 缓存命中
    expect(cards[2].rows[1].evidence.formula).toContain('30.0%')

    // 卡4：72h 电销 & 500m 打卡保活率 = (18 + 9) / (20 + 10) = 90.0%
    expect(cards[3].key).toBe('sla')
    expect(cards[3].primaryText).toBe('90.0%')
    expect(cards[3].badgeText).toContain('14户延保15天')
    expect(cards[3].footText).toContain('日限5/在途10户')
  })

  it('拥有 crm:marketing:customer:query 的内部招商角色自动获得 /crm/marketing/public-sea 导航可见性', () => {
    const paths = allowedNavigationPaths([
      { id: 101, status: 1, type: 3, perm: 'crm:marketing:customer:query', name: '客户查询' }
    ])
    expect(paths.has('/crm/marketing/public-sea')).toBe(true)
  })
})
