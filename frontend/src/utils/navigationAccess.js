import { menuTree } from '../layout/menu'
import { buildPermissionTree, permissionIds } from './permissionTree'

const SHARED_PATHS = {
  budget: ['/budget/annual', '/budget/monthly'],
  'pur:plan': ['/budget/plan-year', '/budget/plan-month'],
  'pur:request': ['/budget/plan-year', '/budget/plan-month'],
  'property:check': ['/property/check-clean', '/property/check-green', '/property/check-quality'],
  'property:feedback': ['/property/complaint', '/property/feedback'],
  contract: ['/contract/list'],
  tenant: ['/tenant/list'],
  receivable: ['/finance/receivable-register'],
  'finance:payment': ['/finance/cashier'],
  'building:floorPlan': ['/building/building'],
  'energy:reading': ['/energy/meter'],
  'property:unit': ['/property/responsible-unit'],
  'pur:supplier': ['/property/responsible-unit'],
  'pur:supplierContract': ['/property/responsible-unit'],
  'workflow:definition': ['/budget/flow'],
  vending: ['/app/center'],
  suggestion: ['/suggestion/manage']
}

export function allowedNavigationPaths(menus) {
  const selected = new Set(menus.map(menu => menu.id))
  const paths = new Set(['/suggestion/mine'])
  const visit = node => {
    if (node.path && permissionIds([node]).some(id => selected.has(id))) paths.add(node.path)
    node.children?.forEach(visit)
  }
  buildPermissionTree(menus).forEach(visit)
  for (const menu of menus) {
    if (!selected.has(menu.id) || !menu.perm) continue
    for (const [prefix, routes] of Object.entries(SHARED_PATHS)) {
      if (menu.perm === prefix || menu.perm.startsWith(`${prefix}:`)) routes.forEach(route => paths.add(route))
    }
  }
  return paths
}

export function visibleNavigation(paths, admin = false) {
  const filter = item => {
    if (item.path) return admin || paths.has(item.path) ? { ...item } : null
    const children = item.children?.map(filter).filter(Boolean) || []
    return children.length ? { ...item, children } : null
  }
  return menuTree.map((item, topIndex) => ({ ...item, topIndex })).map(filter).filter(Boolean)
}

const DETAIL_PARENTS = [
  [/^\/building\/room\/detail\//, '/building/room'],
  [/^\/tenant\/detail\//, '/tenant/list'],
  [/^\/contract\/detail\//, '/contract/list'],
  [/^\/budget\/supplier-contract(?:\/|$)/, '/property/responsible-unit'],
  [/^\/app\/vending(?:\/|$)/, '/app/center'],
  [/^\/screen$/, '/data/center']
]

export function canAccessNavigation(path, paths, admin = false) {
  if (admin || paths.has(path)) return true
  return DETAIL_PARENTS.some(([pattern, parent]) => pattern.test(path) && paths.has(parent))
}

export function firstNavigationPath(paths, admin = false) {
  const first = visibleNavigation(paths, admin)
  const leaf = node => node.path || node.children?.map(leaf).find(Boolean)
  return first.map(leaf).find(Boolean) || '/suggestion/mine'
}
