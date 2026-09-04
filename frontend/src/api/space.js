import request from '@/utils/request'

// 统一空间树(项目/楼宇/楼层/房间)。应收登记、账单出账认的是 spaceId/roomId,
// 不是登记表里的楼层文本,所以手工建单必须能从这里选到真实节点。
export const spaceApi = {
  tree: () => request.get('/space/tree'),
  children: (parentId) => request.get('/space/children', { params: { parentId } })
}

/**
 * 把空间树摊平成下拉选项,层级用缩进表达。
 * 后端 /space/tree 已按 level、sort 排好序,这里只补父子缩进,不重排。
 */
export function flattenSpaceOptions(nodes) {
  const list = nodes || []
  const byParent = new Map()
  for (const node of list) {
    const key = node.parentId ?? 0
    if (!byParent.has(key)) byParent.set(key, [])
    byParent.get(key).push(node)
  }
  const options = []
  const walk = (parentKey, depth) => {
    for (const node of byParent.get(parentKey) || []) {
      options.push({ ...node, depth, label: `${'　'.repeat(depth)}${node.name}` })
      walk(node.id, depth + 1)
    }
  }
  walk(0, 0)
  // 树里有断链节点(父节点被停用)时兜底,别让它们从下拉里凭空消失
  if (options.length < list.length) {
    const picked = new Set(options.map(o => o.id))
    for (const node of list) {
      if (!picked.has(node.id)) options.push({ ...node, depth: 0, label: node.name })
    }
  }
  return options
}
