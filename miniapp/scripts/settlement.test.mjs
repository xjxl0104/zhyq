import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { flowSegments, barHeights, settleShares, withdrawalStatusText, scheduleSteps, money } from '../src/utils/settlement.mjs'

test('资金流转五段按顺序，占比按合计计算', () => {
  const segs = flowSegments({ frozen: 1, settleable: 1, withdrawable: 2, withdrawing: 0, withdrawn: 4 })
  assert.deepEqual(segs.map(s => s.label), ['冻结中', '待结算', '可提现', '提现审批中', '已提现'])
  assert.deepEqual(segs.map(s => s.percent), [12.5, 12.5, 25, 0, 50])
  assert.ok(flowSegments({}).every(s => s.percent === 0))
})
test('柱高按最大值缩放，非零至少 4，零为 0', () => {
  assert.deepEqual(barHeights([0, 1, 100], 200), [0, 4, 200])
  assert.deepEqual(barHeights([0, 0]), [0, 0])
})
test('结算进度与提现状态文案', () => {
  assert.deepEqual(settleShares({ settled: 3, settleable: 1, frozen: 0 }), { settled: 75, settleable: 25, frozen: 0 })
  assert.equal(withdrawalStatusText({ status: 1, nodeName: '数智云仓财务审批' }), '财务审批中（数智云仓财务审批）')
  assert.equal(withdrawalStatusText({ status: 2 }), '审批通过·待打款')
  assert.equal(money('1.005'), '1.00')
})
test('时间节点包含冻结、自动结算、自动提现与财务审批', () => {
  const steps = scheduleSteps({ freezeDays: 7, settleTime: '每天 02:00', autoWithdrawDay: 10 })
  assert.deepEqual(steps.map(s => s.title), ['客户发货', '冻结 7 天', '自动结算', '自动提现', '财务审批', '打款到账'])
  assert.match(steps[3].desc, /每月 10 日/)
  assert.match(scheduleSteps({ autoWithdrawDay: 0 })[3].desc, /随时手动/)
})
test('首页快捷入口改为结算提现并注册结算页面', () => {
  const home = readFileSync(new URL('../src/pages/home/index.vue', import.meta.url), 'utf8')
  const pages = readFileSync(new URL('../src/pages.json', import.meta.url), 'utf8')
  assert.match(home, /结算提现/)
  assert.doesNotMatch(home, />申请提现</)
  assert.match(home, /pages\/settlement\/index/)
  assert.match(pages, /pages\/settlement\/index/)
  assert.match(pages, /pages\/settlement\/contract/)
})

test('发货趋势：每日补零、每月 12 柱，峰值与平均', async () => {
  const { trendBarsOf, trendSummary } = await import('../src/utils/shipments.mjs')
  const trend = { daily: [{ date: '2026-11-01', volume: 0, hasData: false }, { date: '2026-11-02', volume: 5, hasData: true }, { date: '2026-11-03', volume: 10, hasData: true }],
    monthly: [{ month: '2026-10', volume: 30 }, { month: '2026-11', volume: 15 }] }
  const daily = trendBarsOf(trend, 'daily', 200)
  assert.deepEqual(daily.map(b => b.height), [0, 100, 200])
  assert.deepEqual(daily.map(b => b.label), ['01', '02', '03'])
  assert.equal(daily[0].hasData, false)
  const s = trendSummary(daily)
  assert.equal(s.total, 15); assert.equal(s.average, '5.0'); assert.equal(s.peak.full, '2026-11-03')
  const monthly = trendBarsOf(trend, 'monthly', 200)
  assert.deepEqual(monthly.map(b => b.label), ['26-10', '26-11'])
  assert.deepEqual(trendBarsOf(null), [])
})
test('发货量页展示每日/每月趋势并可按客户筛选', () => {
  const page = readFileSync(new URL('../src/pages/shipments/index.vue', import.meta.url), 'utf8')
  assert.match(page, /发货趋势/); assert.match(page, /shipmentApi\.trend/); assert.match(page, /focusContract/)
})
