import { expect, it } from 'vitest'
import { menuTree } from '../../layout/menu'
import { buildPermissionTree, filterPermission, normalizePermissionSelection, permissionIds } from '../permissionTree'
const grant = (id, perm, name = perm) => ({ id, perm, name, type: 3, status: 1, parentId: 0 })
const page = (id, path) => ({ id, path, name: path, type: 2, status: 1, parentId: 0 })
const flatten = nodes => nodes.flatMap(node => [node, ...flatten(node.children || [])])
it('uses the ten sidebar categories with roles before users', () => {
  const tree = buildPermissionTree([page(1, '/system/user'), page(2, '/system/role')])
  expect(tree.map(n => n.name)).toEqual(menuTree.map((n, i) => `${String(i + 1).padStart(2, '0')} ${n.title}`))
  expect(tree[9].children.map(n => n.name)).toEqual(['角色管理', '用户管理'])
  expect(JSON.stringify(menuTree)).not.toMatch(/system\/(dept|post)/)
})
it('keeps page access and actions together as independent real-ID leaves', () => {
  const user = buildPermissionTree([page(1, '/system/user'), grant(2, 'system:user:add', '用户-新增')])[9].children[0]
  expect(user.children.map(n => [n.id, n.name])).toEqual([[1, '页面访问'], [2, '用户-新增']])
  expect(typeof user.id).toBe('string')
})
it('does not turn selected legacy directories into action grants', () => {
  const menus = [{ id: 130, name: '系统管理', type: 1, status: 1 }, page(131, '/system/user'), grant(200, 'system:user:add')]
  expect(permissionIds(buildPermissionTree(menus))).toEqual([131, 200])
  expect(normalizePermissionSelection(menus, [130, 131])).toEqual([130, 131])
})
it('places suppliers, floor plans, assets, marketing and shared budgets in the right categories', () => {
  const tree = buildPermissionTree([grant(1, 'pur:supplierContract:add'), grant(2, 'pur:supplier:edit'),
    grant(3, 'building:floorPlan:edit'), grant(4, 'property:asset:edit'), grant(5, 'budget:edit'),
    grant(6, 'pur:plan:edit'), grant(7, 'crm:marketing:promoter:edit'), grant(8, 'workflow:definition:manage')])
  expect(permissionIds([tree[1]])).toEqual([3, 4])
  expect(permissionIds([tree[4]])).toEqual([1, 2])
  expect(permissionIds([tree[2]])).toEqual([7])
  expect(permissionIds([tree[6]]).sort()).toEqual([5, 6, 8])
  expect(flatten(tree).some(n => n.name === '年度 / 月度预算共用操作')).toBe(true)
})
it('handles slash-less routes and older type-1 permission records', () => {
  const tree = buildPermissionTree([{ ...page(1, 'bi/admin'), type: 1, perm: 'bi:admin:view' },
    grant(2, 'bi:admin:manage'), { ...page(3, 'suggestion/manage'), type: 1, perm: 'suggestion:manage' }])
  expect(permissionIds([tree[7]])).toEqual([1, 2])
  expect(permissionIds([tree[8]])).toEqual([3])
})
it('excludes retired and disabled grants but retains marketing positions', () => {
  const menus = [page(1, '/system/dept'), page(2, 'system/post'), grant(3, 'system:dept:query'),
    grant(4, 'system:post:add'), { ...grant(5, 'system:user:add'), status: 0 }, grant(6, 'crm:marketing:position:config')]
  expect(permissionIds(buildPermissionTree(menus))).toEqual([6])
  expect(normalizePermissionSelection(menus, [1, 2, 3, 4, 5, 6, 'nav:x', 999])).toEqual([6])
})
it('retains custom grants exactly once and searches the full ancestor context', () => {
  const tree = buildPermissionTree([grant(1, 'custom:resource:edit', '自定义功能-编辑'), grant(2, 'system:user:add', '用户-新增')])
  expect(permissionIds(tree)).toEqual([2, 1])
  expect(filterPermission('系统管理', flatten(tree).find(n => n.id === 2))).toBe(true)
  expect(filterPermission('自定义', tree[9])).toBe(true)
})
it('places legacy supplier URLs under the property supplier archive', () => {
  expect(permissionIds([buildPermissionTree([page(1, '/budget/supplier'), page(2, '/budget/supplier-contract')])[4]])).toEqual([1, 2])
})
