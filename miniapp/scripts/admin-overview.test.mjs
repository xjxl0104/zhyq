import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import * as helpers from '../src/utils/admin-overview.mjs'

const read = path => readFileSync(new URL('../src/' + path, import.meta.url), 'utf8')
const script = source => source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const ref = value => ({ value })
const computed = getter => ({ get value() { return getter() } })
const flush = () => new Promise(resolve => setImmediate(resolve))
function overviewHarness(api) {
  const watches = [], props = { view: 'partners', refreshKey: 0 }, urls = []
  let unmount
  const context = vm.createContext({ ...helpers, money: helpers.overviewMoney, count: helpers.overviewCount,
    ref, computed, defineProps: () => props, watch: (source, callback, options) => { watches.push(callback); if (options?.immediate) callback() },
    onUnmounted: fn => { unmount = fn }, adminOverviewApi: { overview: api }, navigateTo: url => urls.push(url) })
  vm.runInContext(script(read('components/AdminOverview.vue')), context)
  return { context, props, watches, urls, unmount: () => unmount(), run: code => vm.runInContext(code, context) }
}
const result = (name, id = 1, kind = 'partners') => ({ records: [{ id, name }], total: 1, summary: { shipmentVolume: '12' }, kind, selectionName: name })

test('business dates use Shanghai midnight and preserve cumulative range', () => {
  const now = new Date('2026-10-31T16:05:00Z')
  assert.deepEqual(helpers.overviewRange('month', now), { startDate: '2026-11-01', endDate: '2026-11-01' })
  assert.deepEqual(helpers.overviewRange('all', now), { startDate: '2000-01-01', endDate: '2026-11-01' })
  assert.deepEqual(helpers.overviewRange('recent', now), { startDate: '2026-10-03', endDate: '2026-11-01' })
  assert.equal(helpers.overviewCount('9007199254740993'), '9007199254740993')
  assert.throws(() => helpers.overviewQuery({ view: 'admin' }), /请选择/)
})
test('old partner response cannot replace a newly selected warehouse view', async () => {
  const pending = [], calls = []
  const h = overviewHarness(query => { calls.push(query); return new Promise(resolve => pending.push(resolve)) })
  h.props.view = 'warehouses'; h.watches[0]()
  pending[1](result('新云仓', 22, 'warehouses')); await flush()
  pending[0](result('旧伙伴', 11)); await flush()
  assert.equal(h.run('records.value[0].name'), '新云仓')
  assert.equal(h.run('loading.value'), false)
  assert.equal(calls[1].view, 'warehouses')
})
test('selecting a team drills into customers and client links retain the date range', async () => {
  const calls = []
  const h = overviewHarness(async query => { calls.push(query); return query.selectionId ? result('客户A', 101, 'customers') : result('P4团队', 5) })
  await flush(); h.context.selected = { id: 5 }; h.run('select(selected)'); await flush()
  assert.equal(calls[1].selectionId, 5)
  h.context.customer = { id: 101 }; h.run('select(customer)')
  assert.match(h.urls[0], /^\/pages\/admin-customer\/index\?id=101&startDate=2000-01-01&endDate=/)
  h.run('back()'); await flush()
  assert.equal(Object.hasOwn(calls.at(-1), 'selectionId'), false)
})
test('warehouse charts expose operations only and customer links preserve warehouse scope', async () => {
  const h = overviewHarness(async query => {
    const metrics = {shipmentVolume:'24',signedCustomerCount:2,storedCustomerCount:1,acceptedCustomerCount:4,pendingCustomerCount:0,shippingCustomerCount:1}
    return {records:[{id:query.selectionId ? 101 : 20,name:'示例',summary:metrics}], total:1,summary:metrics,kind:query.selectionId ? 'customers' : 'warehouses'}
  })
  h.props.view = 'warehouses'; h.watches[0](); await flush()
  assert.deepEqual(Array.from(h.run('distribution.value.map(item => item.width)')), ['0%','100%','50%','25%','25%'])
  assert.equal(h.run('ranking.value[0].display'), '24 票')
  h.run('select(records.value[0])'); await flush()
  h.run('select(records.value[0])')
  assert.match(h.urls[0], /warehouseId=20$/)
})
test('failed or unmounted request clears private previous data and cannot restore it late', async () => {
  let resolve
  const h = overviewHarness(() => new Promise(r => { resolve = r }))
  h.unmount(); resolve(result('敏感数据')); await flush()
  assert.equal(h.run('records.value.length'), 0)
  const denied = overviewHarness(async () => { throw new Error('无权限') }); await flush()
  assert.equal(denied.run('error.value'), '无权限')
  assert.equal(denied.run('summary.value'), null)
})
test('typing an unapplied search cannot mix the next page into the existing search results', async () => {
  const calls = []
  const h = overviewHarness(async query => { calls.push(query); return { ...result('伙伴'), total: 40 } })
  await flush(); h.run("keyword.value = '待搜索名称'")
  await h.run('load(true)')
  assert.equal(calls[1].keyword, '')
  assert.equal(calls[1].page, 2)
  h.run('search()'); await flush()
  assert.equal(calls[2].keyword, '待搜索名称')
  assert.equal(calls[2].page, 1)
})
test('personal view is default and non-admin cannot open the view switch', async () => {
  let menu = null
  const me = { status: 1, positionCode: 'P1', superAdmin: false }
  const context = vm.createContext({ ...helpers, ref, computed, onShow() {}, onHide() {}, onUnmounted() {},
    token: { get: () => 'same-token' }, shipmentNavigation: () => ({}),
    meApi: { home: async () => ({}), me: async () => me, workbench: async () => ({}) },
    uni: { showActionSheet: options => { menu = options }, reLaunch() {} }, navigateTo() {}, switchTab() {}, setInterval() {}, clearInterval() {} })
  vm.runInContext(script(read('pages/home/index.vue')), context)
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('activeView.value', context), 'personal')
  vm.runInContext('chooseView()', context); assert.equal(menu, null)
  me.superAdmin = true; await vm.runInContext('load()', context)
  vm.runInContext('chooseView()', context)
  assert.deepEqual(Array.from(menu.itemList), ['个人总览', '伙伴总览', '云仓总览'])
  menu.success({ tapIndex: 2 }); assert.equal(vm.runInContext('activeView.value', context), 'warehouses')
  me.superAdmin = false; await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('activeView.value', context), 'personal')
})
test('overview and new detail templates compile with explicitly registered child components', () => {
  for (const path of ['components/AdminOverview.vue', 'pages/admin-customer/index.vue', 'pages/home/index.vue']) {
    const { descriptor } = parse(read(path))
    const compiled = compileScript(descriptor, { id: path })
    const output = compileTemplate({ source: descriptor.template.content, filename: path, id: path,
      compilerOptions: { bindingMetadata: compiled.bindings } })
    assert.deepEqual(output.errors, [], path)
  }
})
