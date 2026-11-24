import { warehouseDashboardEntries } from '../src/utils/warehouse-dashboard.mjs'
import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import * as Vue from 'vue'
import { renderToString } from 'vue/server-renderer'
import { parse, compileScript } from 'vue/compiler-sfc'
import { isWarehouseOwner, warehouseStageLabel, staffPayload, stagePayload, WAREHOUSE_STAGES } from '../src/utils/warehouse-workspace.mjs'
import { assignmentLabel } from '../src/utils/warehouse-ui.js'
import { requirementRows } from '../src/utils/referral-intake.mjs'

test('owner-only controls require the server owner identity; role is never inferred from a name', () => {
  assert.equal(isWarehouseOwner({ name: '负责人', warehouseRole: 'member', isOwner: true }), false)
  assert.equal(isWarehouseOwner({ warehouseRole: 'owner', isOwner: true }), true)
  assert.equal(isWarehouseOwner(null), false)
})
test('staff form sends only editable fields and requires a real mobile number', () => {
  assert.deepEqual(staffPayload({ name: ' 张三 ', phone: '13900139000', status: 0, role: 'owner', warehouseId: 99 }), { name: '张三', phone: '13900139000', status: 0 })
  assert.throws(() => staffPayload({ name: '', phone: '13900139000', status: 1 }), /真实姓名/)
  assert.throws(() => staffPayload({ name: '张三', phone: '123', status: 1 }), /11 位/)
  assert.throws(() => staffPayload({ name: '张三', phone: '13900139000', status: 2 }), /启用或停用/)
})
test('five real business stages are distinct and payload never invents a completed percentage', () => {
  assert.deepEqual([1, 2, 3, 4, 5].map(warehouseStageLabel), ['业务员承接', '价格谈判', '签约', '货品入仓', '正式发货'])
  assert.deepEqual(stagePayload(3, ' 等待签署件 '), { stage: 3, summary: '等待签署件' })
  assert.throws(() => stagePayload(6, ''), /实际服务进度/)
  assert.throws(() => stagePayload(2.5, ''), /实际服务进度/)
})

const source = name => readFileSync(new URL(`../src/pages/${name}/index.vue`, import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const flush = () => new Promise(resolve => setImmediate(resolve))
function detailHarness() {
  const calls = [], hooks = {}, pending = []
  const context = vm.createContext({
    ref: value => ({ value }), onLoad: fn => { hooks.load = fn }, onShow: fn => { hooks.show = fn }, stagePayload,
    uni: { showToast() {}, navigateTo() {} },
    warehouseApi: {
      customerDetail: async id => { calls.push(['detail', id]); return { id, warehouseAssignmentStatus: 2, warehouseSalesStage: 3, warehouseSalespersonId: 5, isOwner: false } },
      customerProgress: (id, payload) => new Promise((resolve, reject) => pending.push({ id, payload, resolve, reject }))
    }
  })
  vm.runInContext(source('warehouse-customer-detail'), context)
  return { calls, hooks, pending, run: code => vm.runInContext(code, context) }
}
test('saving actual progress sends the selected stage and reloads authoritative detail once', async () => {
  const h = detailHarness(); h.hooks.load({ id: '9' }); await flush()
  h.run("stage.value = 4; summary.value = '已入仓'; saveProgress(); saveProgress()")
  assert.equal(h.pending.length, 1)
  assert.deepEqual(JSON.parse(JSON.stringify(h.pending[0].payload)), { stage: 4, summary: '已入仓' })
  h.pending[0].resolve(); await flush()
  assert.equal(h.calls.filter(c => c[0] === 'detail').length, 2)
  assert.equal(h.run('summary.value'), '')
})
test('failed progress update keeps text for retry and does not show fake success', async () => {
  const h = detailHarness(); h.hooks.load({ id: '9' }); await flush()
  h.run("stage.value = 5; summary.value = '实际发货说明'; saveProgress()")
  h.pending[0].reject(new Error('此客户已重新分配')); await flush()
  assert.equal(h.run('summary.value'), '实际发货说明')
  assert.equal(h.run('actionError.value'), '此客户已重新分配')
  assert.equal(h.calls.length, 1)
})
test('invalid customer links do not query a guessed fallback customer', () => {
  const h = detailHarness(); h.hooks.load({ id: '-9' }); assert.equal(h.calls.length, 0); assert.equal(h.run('error.value'), '客户参数无效')
})
test('same dashboard exposes only scoped features to salespeople and the tariff only to online owners', () => {
  const staff = warehouseDashboardEntries({ warehouseRole:'member',isOwner:false },{ joinStatus:5 })
  assert.deepEqual(staff.map(item => item.page), ['warehouse-profile','warehouse-customers','warehouse-orders'])
  const owner = { warehouseRole:'owner',isOwner:true }
  assert.ok(warehouseDashboardEntries(owner,{}).some(item => item.page === 'warehouse-staff'))
  assert.ok(warehouseDashboardEntries(owner,{ joinStatus:5 }).some(item => item.page === 'warehouse-rate-card'))
  assert.ok(!warehouseDashboardEntries(owner,{ joinStatus:6 }).some(item => item.page === 'warehouse-rate-card'))
})

test('customer detail renders one complete authoritative progress history beside the five-stage editor', async () => {
  const fixture = {
    id: 9, name: '样例客户', isOwner: false, warehouseAssignmentStatus: 2,
    warehouseSalespersonId: 5, warehouseSalesStage: 2, salespersonName: '业务员甲',
    requirements: {}, progressPercentage: 40, publicProgress: '报价已发送，等待客户确认',
    progressUpdatedAt: '2026-11-23T14:30:00', progressHistory: [
      { id: 3, percentage: 40, summary: '价格谈判：已发送报价方案', createdAt: '2026-11-23T14:30:00' },
      { id: 2, percentage: 20, summary: '业务员承接：已完成首次沟通', createdAt: '2026-11-22T10:00:00' },
      { id: 1, percentage: 10, summary: '后台分配：客户已派给本仓', createdAt: '2026-11-21T09:00:00' }
    ],
    // The legacy stage-only list must not be rendered alongside the complete common history.
    salesProgressHistory: [{ id: 30, stage: 2, summary: '不应重复显示的业务员列表', createTime: '2026-11-23T14:30:00' }]
  }
  function compile(relativePath, extra = {}) {
    const original = readFileSync(new URL(`../src/${relativePath}`, import.meta.url), 'utf8')
    const { descriptor } = parse(original.replace(/<script>[\s\S]*?<\/script>/g, ''))
    const code = compileScript(descriptor, { id: relativePath, inlineTemplate: true,
      templateOptions: { compilerOptions: { isCustomElement: tag => !['CustomerProgress', 'CustomerRequirements'].includes(tag) } } }).content
      .replace(/^import \{([^}]+)\} from ['"]vue['"];?$/gm, (_line, names) => `const { ${names.replaceAll(' as ', ': ')} } = Vue`)
      .replace(/^import .*$/gm, '').replace('export default', 'globalThis.Component =')
    const context = vm.createContext({ Vue, ...extra })
    vm.runInContext(code, context)
    return context.Component
  }
  const CustomerProgress = compile('components/CustomerProgress.vue')
  const CustomerRequirements = compile('components/CustomerRequirements.vue', { requirementRows })
  const Page = compile('pages/warehouse-customer-detail/index.vue', {
    CustomerProgress, CustomerRequirements, WAREHOUSE_STAGES, warehouseStageLabel, stagePayload, assignmentLabel,
    warehouseApi: { customerDetail: async () => fixture },
    onLoad: fn => Vue.onServerPrefetch(async () => { fn({ id: '9' }); await flush() }), onShow() {}
  })
  const html = await renderToString(Vue.createSSRApp(Page))
  assert.equal((html.match(/class="progress-heading"/g) || []).length, 1)
  assert.equal((html.match(/class="history-item"/g) || []).length, 3)
  assert.equal((html.match(/class="stage-option"/g) || []).length, 5)
  assert.ok(html.includes('更新服务进度'))
  for (const row of fixture.progressHistory) assert.equal(html.split(row.summary).length - 1, 1)
  assert.equal(html.includes('历次服务进度'), false)
  assert.equal(html.includes('不应重复显示的业务员列表'), false)
  assert.equal(html.includes('暂无进度记录'), false)
})
