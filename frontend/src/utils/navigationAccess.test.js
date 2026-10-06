import { describe, expect, it } from 'vitest'
import { allowedNavigationPaths, canAccessNavigation, firstNavigationPath, visibleNavigation } from './navigationAccess'

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
})
