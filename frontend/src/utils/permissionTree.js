const MODULE_NAMES = {
  building: '空间与资产', crm: '招商租赁', tenant: '招商租赁', contract: '招商租赁',
  finance: '财务', receivable: '财务', property: '物业服务', rsv: '物业服务',
  hui: '物业服务', energy: '能源与物联', iot: '能源与物联',
  oa: '园区运营', pur: '预算管理', budget: '预算管理',
  system: '系统管理', vending: '应用中心', acc: '便捷通行'
}

// Group presentation only: original database IDs are kept on every grant.
export function buildPermissionTree(menus) {
  const active = menus.filter(menu => menu.status === 1)
  const pages = active.filter(menu => menu.type !== 3)
  const pageTree = parentId => pages.filter(menu => menu.parentId === parentId).map(menu => ({
    ...menu, children: pageTree(menu.id)
  }))
  const modules = new Map()
  for (const menu of active.filter(menu => menu.type === 3)) {
    const moduleCode = (menu.perm || '').split(':')[0]
    const moduleName = menu.perm?.startsWith('pur:supplier')
      ? '物业服务' : MODULE_NAMES[moduleCode] || '其他权限'
    const [groupName, ...action] = (menu.name || '其他操作').split('-')
    if (!modules.has(moduleName)) modules.set(moduleName, new Map())
    const groups = modules.get(moduleName)
    if (!groups.has(groupName)) groups.set(groupName, [])
    groups.get(groupName).push({ ...menu, name: action.join('-') || menu.name,
      searchText: `${moduleName} ${menu.name} ${menu.perm || ''}` })
  }
  const actions = [...modules].map(([name, groups]) => ({
    id: `module:${name}`, name, children: [...groups].map(([groupName, children]) => ({
      id: `group:${name}:${groupName}`, name: groupName, children
    }))
  }))
  return [...pageTree(0), ...actions]
}

export function filterPermission(value, node) {
  const term = value.trim().toLowerCase()
  if (!term) return true
  return `${node.name || ''} ${node.searchText || ''}`.toLowerCase().includes(term)
    || !!node.children?.some(child => filterPermission(value, child))
}
