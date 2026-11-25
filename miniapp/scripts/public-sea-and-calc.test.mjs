import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../src/', import.meta.url))

test('pages.json 注册 30秒云仓底价测算与云仓公海热单认领页，且伙伴端无公海入口', () => {
  const pagesJson = JSON.parse(readFileSync(resolve(root, 'pages.json'), 'utf8'))
  const paths = pagesJson.pages.map(p => p.path)
  assert.ok(paths.includes('pages/public-calc/index'), '缺少 30秒算价获客页路由')
  assert.ok(paths.includes('pages/warehouse-sea/index'), '缺少云仓公海热单认领页路由')

  const partnerHome = readFileSync(resolve(root, 'pages/home/index.vue'), 'utf8')
  assert.ok(!partnerHome.includes('warehouse-sea'), '外部推广伙伴首页严禁暴露公海认领入口')

  const whDashboard = readFileSync(resolve(root, 'pages/warehouse-dashboard/index.vue'), 'utf8')
  assert.ok(whDashboard.includes("page: 'warehouse-sea'"), '云仓工作台缺少公海热单认领入口')
  assert.ok(whDashboard.includes('Number(stats.joinStatus) === 5'), '仅已上线云仓主账号可见公海认领入口')
})
