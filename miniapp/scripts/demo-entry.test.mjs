import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { isDemoEntryEnabled } from '../src/utils/demo-entry.mjs'

const root = new URL('../', import.meta.url)
const read = file => readFileSync(new URL(file, root), 'utf8')
const demo = read('src/pages/demo/index.vue')
const pages = read('src/pages.json')

test('临时体验入口注册独立页面，收敛为受 VITE_ENABLE_DEMO_ENTRY 控制的次级辅助链接', () => {
  assert.match(pages, /pages\/demo\/index/)
  assert.equal(isDemoEntryEnabled(undefined), true)
  assert.equal(isDemoEntryEnabled('true'), true)
  assert.equal(isDemoEntryEnabled('1'), true)
  assert.equal(isDemoEntryEnabled('false'), false)
  assert.equal(isDemoEntryEnabled('0'), false)
  assert.equal(isDemoEntryEnabled(false), false)
  for (const page of ['src/pages/entry/index.vue', 'src/pages/login/index.vue', 'src/pages/warehouse-login/index.vue']) {
    const source = read(page)
    assert.match(source, /pages\/demo\/index/)
    assert.match(source, /demoEntryEnabled/)
    assert.match(source, /v-if="showDemo"/)
  }
})

test('体验页只使用本地演示数据，不创建 token 或调用业务接口', () => {
  assert.doesNotMatch(demo, /@\/api|uni\.request|uni\.uploadFile|setStorageSync|mp_token|wh_token|fetch\(/)
  assert.match(demo, /不会访问或修改真实账号、客户、订单与收益/)
  assert.match(demo, /当前为体验模式，不会提交数据/)
  assert.match(demo, /演示示例数据 · 登录后使用真实业务功能/)
})

test('体验页彻底清除虚构未实现功能文案，全部对齐真实落地的双轨业务表述', () => {
  assert.doesNotMatch(demo, /聚水潭|旺店通|电子协议已签署|现场核验|上门核验|库存、订单双向同步/)
  assert.match(demo, /云山发货量同步 \/ 通用 ERP Webhook 接入/)
  assert.match(demo, /线下签署件上传核验/)
  assert.match(demo, /入驻第 3 步：订单同步联调/)
  assert.match(demo, /园区签按单计佣 \/ 云仓直签按实收平台费计佣/)
})

test('体验页的提现、结算、加盟等流程都能完整走通，不再提示“登录后可用”（微信审核要求可完整体验）', () => {
  assert.doesNotMatch(demo, /登录后可用/)
  for (const fn of ['submitWithdraw', 'advanceCustomer', 'createErpKey', 'confirmSettlement', 'advanceOnboarding']) assert.ok(demo.includes(`function ${fn}(`), fn)
  assert.match(demo, /模拟申请提现/)
})

test('云仓登录与演示入口复用完整运营工作台，演示入口保留云仓身份', () => {
  const merchant = read('src/pages/warehouse-dashboard/index.vue')
  const shared = read('src/components/WarehouseDashboard.vue')
  assert.match(merchant, /<WarehouseDashboard /)
  assert.match(demo, /<WarehouseDashboard /)
  assert.match(read('src/pages/warehouse-login/index.vue'), /demo\/index\?role=warehouse/)
  assert.match(demo, /options\?\.role === 'warehouse'/)
  assert.doesNotMatch(shared, /@\/api|uni\.request|navigateTo|setStorageSync/)
  assert.doesNotMatch(demo, /uni\.navigateTo/)
})
