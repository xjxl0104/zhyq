import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const source = readFileSync(new URL('../src/pages/customer-detail/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness() {
  const hooks = {}, queries = [], timers = new Map(), cleared = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }),
    onLoad: fn => { hooks.load = fn }, onShow: fn => { hooks.show = fn }, onHide: fn => { hooks.hide = fn }, onUnload: fn => { hooks.unload = fn },
    setInterval: (fn, interval) => { assert.equal(interval, 15000); const id = timers.size + 1; timers.set(id, fn); return id },
    clearInterval: id => { cleared.push(id); timers.delete(id) },
    bizApi: { customer: id => new Promise((resolve, reject) => queries.push({ id, resolve, reject })) }, meApi: { me: async () => ({}) },
  })
  vm.runInContext(source, context)
  return { hooks, queries, timers, cleared, run: script => vm.runInContext(script, context) }
}
const flush = () => new Promise(resolve => setImmediate(resolve))
test('visible customer details refresh every 15 seconds without overlapping requests', async () => {
  const h = harness(); h.hooks.load({ id: 12 }); h.hooks.show()
  assert.equal(h.queries.length, 1)
  h.timers.values().next().value(); assert.equal(h.queries.length, 1)
  h.queries[0].resolve({ id: 12, progressPercentage: 30, progressHistory: [] }); await flush()
  assert.equal(h.run('c.value.progressPercentage'), 30)
  h.timers.values().next().value(); assert.equal(h.queries.length, 2)
  h.queries[1].resolve({ id: 12, progressPercentage: 60, progressHistory: [{ id: 2, percentage: 60, summary: '已承接' }] }); await flush()
  assert.equal(h.run('c.value.progressPercentage'), 60)
  assert.equal(h.run('c.value.progressHistory.length'), 1)
  h.hooks.hide(); assert.equal(h.timers.size, 0)
})
test('hidden page discards late results; returning to detail fetches fresh data', async () => {
  const h = harness(); h.hooks.load({ id: 12 }); h.hooks.show(); h.hooks.hide()
  h.hooks.show(); assert.equal(h.queries.length, 2)
  h.queries[1].resolve({ id: 12, progressPercentage: 100, lockStatus: 3 }); await flush()
  h.queries[0].resolve({ id: 12, progressPercentage: 30 }); await flush()
  assert.equal(h.run('c.value.progressPercentage'), 100)
  assert.equal(h.run('lockText.value'), '已成交 · 长期有效')
  h.hooks.unload(); assert.equal(h.timers.size, 0)
})
test('background refresh failures retain the last known progress instead of blanking detail', async () => {
  const h = harness(); h.hooks.load({ id: 12 }); h.hooks.show()
  h.queries[0].resolve({ id: 12, progressPercentage: 60 }); await flush()
  h.timers.values().next().value(); h.queries[1].reject(new Error('暂不可用')); await flush()
  assert.equal(h.run('c.value.progressPercentage'), 60)
  assert.equal(h.run('error.value'), '暂不可用')
  h.hooks.unload()
})
