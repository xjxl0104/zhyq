import { describe, expect, it } from 'vitest'
import { allowedNavigationPaths, canAccessNavigation, firstNavigationPath, navigationGroup, visibleNavigation } from './navigationAccess'

describe('navigation access', () => {
  it('shows only property repair for a repair-only role and preserves section 05', () => {
    const grants = [
      { id: 61, parentId: 60, name: '物业报修', type: 2, path: '/property/workorder', status: 1 },
      { id: 601, parentId: 61, name: '工单查询', type: 3, perm: 'property:workorder:query', status: 1 }
    ]
    const paths = allowedNavigationPaths(grants)
    const navigation = visibleNavigation(paths)
    expect([...paths].sort()).toEqual(['/property/workorder', '/suggestion/mine'])
    expect(navigation.map(item => [item.title, item.topIndex])).toEqual([
      ['物业服务', 4], ['建议与反馈', 8]
    ])
    expect(canAccessNavigation('/system/user', paths)).toBe(false)
    expect(canAccessNavigation('/finance/bill', paths)).toBe(false)
    expect(firstNavigationPath(paths)).toBe('/property/workorder')
  })

  it('maps shared action grants and never restores a disabled menu', () => {
    const paths = allowedNavigationPaths([
      { id: 1, name: '预算查询', type: 3, perm: 'budget:query', status: 1 },
      { id: 2, name: '系统用户', type: 2, path: '/system/user', status: 0 }
    ])
    expect(paths.has('/budget/annual')).toBe(true)
    expect(paths.has('/budget/monthly')).toBe(true)
    expect(paths.has('/system/user')).toBe(false)
  })

  it('merges marketing pages into grouped sidebar entries with in-page tabs', () => {
    const marketing = visibleNavigation(new Set(), true).flatMap(top => top.children || []).find(item => item.title === '全民营销')
    expect(marketing.children.map(item => item.title)).toEqual(['看板', '伙伴', '客户', '云仓', '合同', '结算', '设置'])
    const settle = navigationGroup('/crm/marketing/withdrawal', [marketing])
    expect(settle.path).toBe('/crm/marketing/order')
    expect(settle.tabs.map(tab => tab.title)).toEqual(['计佣订单', '佣金结算', '服务费账单', '云仓结算', '提现审核'])
    expect(navigationGroup('/crm/lead', [marketing])).toBeNull()
  })

  it('points a merged entry at the first page the role may open', () => {
    const paths = new Set(['/crm/marketing/withdrawal', '/crm/marketing/bill'])
    const settle = navigationGroup('/crm/marketing/withdrawal', visibleNavigation(paths))
    expect(settle.path).toBe('/crm/marketing/bill')
    expect(settle.tabs.map(tab => tab.path)).toEqual(['/crm/marketing/bill', '/crm/marketing/withdrawal'])
  })
})
