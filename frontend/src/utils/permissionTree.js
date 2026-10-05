import { menuTree as navigation } from '../layout/menu'

const pathOf = path => path ? `/${path.replace(/^\/+|\/+$/g, '')}` : ''
const ALIASES = {
  '/budget/supplier': '/property/responsible-unit',
  '/budget/supplier-contract': '/property/responsible-unit'
}
export function isRetiredPermission(menu) {
  return ['/system/dept', '/system/post'].includes(pathOf(menu.path))
    || /^system:(dept|post)(:|$)/.test(menu.perm || '')
}

// These API permissions are shared by several pages or have an older resource name.
// Keep each database grant in exactly one place; never invent new grant IDs.
const RESOURCE_PATHS = {
  contract: '/contract/list', tenant: '/tenant/list', receivable: '/finance/receivable-register',
  'finance:receivable': '/finance/receivable-register', 'finance:payment': '/finance/cashier',
  'building:floorPlan': '/building/building', 'energy:reading': '/energy/meter',
  'property:unit': '/property/responsible-unit', 'pur:supplier': '/property/responsible-unit',
  'pur:supplierContract': '/property/responsible-unit', 'bi:admin': '/bi/admin',
  'bi:product': '/bi/product', suggestion: '/suggestion/manage',
  'crm:marketing:account': '/crm/marketing/promoter',
  'workflow:definition': '/budget/flow', vending: '/app/center'
}
const SHARED = {
  budget: ['预算管理', '年度 / 月度预算共用操作'],
  'pur:plan': ['预算管理', '年度 / 月度采购计划共用操作'],
  'pur:request': ['预算管理', '采购申请操作'],
  'property:check': ['物业', '保洁 / 绿化 / 品质共用操作'],
  'property:feedback': ['物业', '投诉 / 意见反馈共用操作']
}
const MODULE_INDEX = {
  dashboard: 0, data: 0, screen: 0, overview: 0, building: 1, am: 1, space: 1,
  crm: 2, contract: 2, tenant: 2, finance: 3, receivable: 3,
  property: 4, rsv: 4, service: 4, hui: 4, energy: 5, iot: 5, acc: 5,
  oa: 6, budget: 6, pur: 6, vending: 6, app: 6, workflow: 6, bi: 7, suggestion: 8, system: 9
}

export function permissionIds(nodes) {
  return nodes.flatMap(node => node.children?.length ? permissionIds(node.children)
    : typeof node.id === 'number' ? [node.id] : [])
}

export function buildPermissionTree(menus) {
  const paths = new Map(), folders = new Map()
  const makeNode = (item, ancestors = [], index) => {
    const name = index === undefined ? item.title : `${String(index + 1).padStart(2, '0')} ${item.title}`
    const node = { id: `nav:${[...ancestors, item.title].join('/')}`, name, children: [],
      searchText: [...ancestors, name].join(' ') }
    folders.set(item.title, node)
    if (item.path) paths.set(item.path, node)
    node.children = (item.children || []).map(child => makeNode(child, [...ancestors, name]))
    return node
  }
  const tree = navigation.map((item, index) => makeNode(item, [], index))
  const active = menus.filter(menu => menu.status === 1 && !isRetiredPermission(menu))
  const byId = new Map(active.map(menu => [menu.id, menu]))
  const group = (parent, name) => {
    let node = parent.children.find(child => child.name === name && typeof child.id === 'string')
    if (!node) {
      node = { id: `${parent.id}/${name}`, name, searchText: `${parent.searchText} ${name}`, children: [] }
      parent.children.push(node)
    }
    return node
  }
  const add = (parent, menu, name, kind) => parent.children.push({
    ...menu, name, kind, searchText: `${parent.searchText} ${menu.name} ${menu.perm || ''}`
  })

  for (const menu of active) {
    const path = ALIASES[pathOf(menu.path)] || pathOf(menu.path)
    if (path && paths.has(path) && menu.type !== 3) {
      add(paths.get(path), menu, '页面访问', 'page')
      continue
    }
    // Old directory records are structural only. Keeping them out of the tree prevents
    // reopening a selected directory from implicitly selecting every action under it.
    if (!menu.perm && !path) continue
    const parts = (menu.perm || '').split(':')
    const resource = parts.slice(0, -1).join(':')
    let parent
    if (SHARED[resource]) {
      const [folder, label] = SHARED[resource]
      parent = group(folders.get(folder), label)
    } else {
      const mappedPath = RESOURCE_PATHS[resource]
      const inferredPath = `/${parts.slice(0, -1).join('/')}`
      parent = paths.get(mappedPath) || paths.get(inferredPath) || paths.get(RESOURCE_PATHS[parts[0]])
    }
    if (!parent) {
      const visited = new Set([menu.id])
      let ancestor = byId.get(menu.parentId)
      while (ancestor && !visited.has(ancestor.id)) {
        visited.add(ancestor.id)
        const target = paths.get(ALIASES[pathOf(ancestor.path)] || pathOf(ancestor.path))
        if (target) { parent = target; break }
        ancestor = byId.get(ancestor.parentId)
      }
    }
    if (!parent) {
      const module = MODULE_INDEX[parts[0]] ?? MODULE_INDEX[path.split('/')[1]] ?? 9
      parent = group(tree[module], '其他功能')
      parent = group(parent, (menu.name || '自定义权限').split('-')[0])
    }
    // Preserve the scope in labels, e.g. supplier contracts and floor-plan uploads.
    add(parent, menu, menu.name || menu.perm || path, menu.perm ? 'action' : 'page')
  }
  const prune = nodes => nodes.filter(node => typeof node.id === 'number' || permissionIds([node]).length)
    .map(node => node.children ? { ...node, children: prune(node.children).sort((a, b) => Number(b.kind === 'page') - Number(a.kind === 'page')) } : node)
  return tree.map(node => ({ ...node, children: prune(node.children).sort((a, b) => Number(b.kind === 'page') - Number(a.kind === 'page')) }))
}

export function filterPermission(value, node) {
  const term = value.trim().toLowerCase()
  return !term || `${node.name || ''} ${node.searchText || ''}`.toLowerCase().includes(term)
    || !!node.children?.some(child => filterPermission(value, child))
}

// Preserve structural/custom records that have no visible checkbox, without ever
// submitting virtual navigation IDs or reinstating retired department/post grants.
export function normalizePermissionSelection(menus, ids) {
  const active = new Set(menus.filter(menu => menu.status === 1 && !isRetiredPermission(menu)).map(menu => menu.id))
  return [...new Set(ids)].filter(id => active.has(id))
}
