import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { PAGE_SIZE, chinaToday, rangePreset, validateRange, formatVolume } from '../src/utils/shipments.mjs'

test('business date uses China day across UTC midnight and leap months', () => {
  const now = new Date('2026-11-08T18:00:00Z')
  assert.equal(chinaToday(now), '2026-11-09')
  assert.deepEqual(rangePreset('month', now), { startDate: '2026-11-01', endDate: '2026-11-09' })
  assert.deepEqual(rangePreset('week', now), { startDate: '2026-11-03', endDate: '2026-11-09' })
  assert.deepEqual(rangePreset('yesterday', now), { startDate: '2026-11-08', endDate: '2026-11-08' })
  assert.deepEqual(rangePreset('lastMonth', new Date('2024-03-01T00:00:00Z')), { startDate: '2024-02-01', endDate: '2024-02-29' })
})
test('dates reject reversed invalid or oversized inclusive ranges', () => {
  assert.equal(validateRange('2024-01-01', '2024-12-31'), '')
  assert.match(validateRange('2024-01-01', '2025-01-01'), /366/)
  assert.match(validateRange('2026-02-29', '2026-03-01'), /正确/)
  assert.match(validateRange('2026-03-02', '2026-03-01'), /晚于/)
})
test('shipment volume preserves full decimal precision without number conversion', () => {
  assert.equal(formatVolume('9007199254740993.2500'), '9,007,199,254,740,993.25')
  assert.equal(formatVolume('0.00'), '0')
  assert.equal(formatVolume('1234'), '1,234')
  assert.equal(formatVolume('not-a-number'), '—')
})

const script = readFileSync(new URL('../src/pages/shipments/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness({ report = async () => response() } = {}) {
  const hooks = {}, calls = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }),
    onLoad: fn => hooks.load = fn, onShow: fn => hooks.show = fn, onHide: fn => hooks.hide = fn,
    onUnload: fn => hooks.unload = fn, onPullDownRefresh: fn => hooks.pull = fn,
    shipmentApi: { report: params => { calls.push(JSON.parse(JSON.stringify(params))); return report(params) } },
    PAGE_SIZE, formatVolume, rangePreset, validateRange, uni: { stopPullDownRefresh() {} }
  })
  vm.runInContext(script, context)
  const run = expr => vm.runInContext(expr, context)
  hooks.load({}); run('visible = true')
  return { hooks, run, calls, context }
}
function response(extra = {}) { return { scope: 'own', totalVolume: '12', canViewTeam: true, contracts: [], members: [], ...extra } }
const tick = () => new Promise(resolve => setImmediate(resolve))

test('defaults to own summary and preserves dates when opening member contract detail', async () => {
  const { run, calls } = harness({ report: async params => response(params) })
  await run('load()')
  assert.equal(calls[0].scope, 'own')
  run("switchScope('team')"); await tick()
  run('openMember({ memberId: 22 })'); await tick()
  assert.equal(calls.at(-1).memberId, 22)
  assert.equal(calls.at(-1).scope, 'team')
  assert.equal(calls.at(-1).startDate, calls[0].startDate)
  run('backToTeam()'); await tick()
  assert.equal(calls.at(-1).memberId, undefined)
})
test('regular partners cannot switch into team report from own view', async () => {
  const { run, calls } = harness({ report: async () => response({ canViewTeam: false }) })
  await run('load()'); run("switchScope('team')")
  assert.equal(calls.length, 1)
  assert.equal(run('scope.value'), 'own')
})
test('later query wins and hidden pages discard late responses', async () => {
  const resolves = []
  const { run, hooks } = harness({ report: () => new Promise(resolve => resolves.push(resolve)) })
  const first = run('load()'), second = run('load()')
  resolves[1](response({ totalVolume: '20' })); await second
  resolves[0](response({ totalVolume: '10' })); await first
  assert.equal(run('report.value.totalVolume'), '20')
  const third = run('load()'); hooks.hide(); resolves[2](response({ totalVolume: '30' })); await third
  assert.equal(run('report.value'), null)
  assert.equal(run('loading.value'), false)
})
test('invalid ranges and failed request clear stale shipment data', async () => {
  const { run, calls } = harness({ report: async () => { throw new Error('无权查看该成员') } })
  run("startDate.value='2026-12-01'; endDate.value='2026-11-01'")
  await run('load()'); assert.equal(calls.length, 0)
  assert.match(run('error.value'), /晚于/)
  run("startDate.value='2026-11-01'"); await run('load()')
  assert.equal(run('report.value'), null)
  assert.equal(run('error.value'), '无权查看该成员')
})
test('long contract lists reveal more rows without changing server totals', async () => {
  const contracts = Array.from({ length: 45 }, (_, index) => ({ contractId: index + 1, volume: '1' }))
  const { run } = harness({ report: async () => response({ totalVolume: '45', contracts }) })
  await run('load()')
  assert.equal(run('visibleRows.value.length'), 20)
  run('shown.value += PAGE_SIZE'); assert.equal(run('visibleRows.value.length'), 40)
  run('shown.value += PAGE_SIZE'); assert.equal(run('visibleRows.value.length'), 45)
  assert.equal(run('report.value.totalVolume'), '45')
})
