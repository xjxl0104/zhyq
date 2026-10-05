import { expect, it } from 'vitest'
import { buildPermissionTree, filterPermission } from '../permissionTree'

it('按模块和功能归组权限，同时保留数据库 ID 并排除停用权限', () => {
  const tree = buildPermissionTree([
    { id: 1, parentId: 0, type: 3, status: 1, name: '用户-查询', perm: 'system:user:query' },
    { id: 2, parentId: 0, type: 3, status: 1, name: '用户-新增', perm: 'system:user:add' },
    { id: 3, parentId: 0, type: 3, status: 0, name: '用户-删除', perm: 'system:user:delete' },
    { id: 4, parentId: 0, type: 3, status: 1, name: '供应商合同-查询', perm: 'pur:supplierContract:query' }
  ])
  expect(tree.map(n => n.name)).toEqual(['系统管理', '物业服务'])
  expect(tree[0].children[0].children.map(n => n.id)).toEqual([1, 2])
  expect(tree[0].children[0].children.map(n => n.name)).toEqual(['查询', '新增'])
  expect(filterPermission('供应商', tree[1].children[0].children[0])).toBe(true)
  expect(filterPermission('用户', tree[1])).toBe(false)
})
