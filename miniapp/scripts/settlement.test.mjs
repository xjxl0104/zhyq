import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { flowSegments, barHeights, settleShares, withdrawalStatusText, withdrawalScheduleText, scheduleSteps, money } from '../src/utils/settlement.mjs'
import { withdrawalRuleRows } from '../src/utils/withdrawal.mjs'

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
  assert.equal(withdrawalStatusText({ status: 1, nodeName: '提现审批' }), '审批中（提现审批）')
  assert.equal(withdrawalStatusText({ status: 1, approval: { assigneeName: '杨洪' } }), '待杨洪审批')
  assert.equal(withdrawalStatusText({ status: 2 }), '审批通过·待打款')
  assert.equal(money('1.005'), '1.00')
})
test('时间节点包含冻结、自动结算、自动提现与指定审批', () => {
  const steps = scheduleSteps({ freezeDays: 7, settleTime: '每天 02:00', autoWithdrawDay: 10 })
  assert.deepEqual(steps.map(s => s.title), ['客户发货', '冻结 7 天', '自动结算', '自动提现', '提现审批', '打款到账'])
  assert.match(steps[3].desc, /每月 10 日/)
  for (const raw of [0, '0']) assert.equal(scheduleSteps({ freezeDays: raw })[1].title, '冻结 0 天')
  for (const raw of [undefined, null, ' ', false, -1, 1.5]) assert.equal(scheduleSteps({ freezeDays: raw })[1].title, '冻结 7 天')
  assert.match(scheduleSteps({ autoWithdrawDay: 0 })[3].desc, /未开启自动提现/)
})
test('结算空态及节点统一遵守后端25至30号提现窗口，不承诺随时申请', () => {
  const schedule = { autoWithdrawDay: 25, rules: { applicationTime: '每月 25–30 日开放提现申请', autoWithdraw: '每月 25 日 10:00 自动提交提现申请' } }
  const text = withdrawalScheduleText(schedule)
  assert.match(text, /每月 25–30 日开放提现申请/)
  assert.match(text, /每月 25 日 10:00 自动提交提现申请/)
  assert.doesNotMatch(text, /随时|全天/)
  assert.equal(scheduleSteps(schedule)[3].desc, text)
  assert.match(withdrawalScheduleText({ withdrawalRules: { applicationTime: '每月 20–25 日开放提现申请' } }), /每月 20–25 日/)
  assert.equal(withdrawalRuleRows({ withdrawalRules: schedule.rules }).find(row => row.label === '提现时间').value, schedule.rules.applicationTime)
  const page = readFileSync(new URL('../src/pages/settlement/index.vue', import.meta.url), 'utf8')
  assert.match(page, /暂无提现记录。\{\{ withdrawalSchedule \}\}/)
  assert.doesNotMatch(page, /随时手动申请/)
})
test('提现窗口缺失或异常时保守提示；只有后端明确无限制才显示随时', () => {
  for (const applicationTime of [undefined, null, '', ' ', false, 0]) {
    const rules = { applicationTime }
    const text = withdrawalScheduleText({ rules })
    assert.match(text, /以提现页显示的开放时间为准/)
    assert.doesNotMatch(text, /随时|全天|每月 0/)
    assert.equal(withdrawalRuleRows({ withdrawalRules: rules }).find(row => row.label === '提现时间').value, '以提现页显示的开放时间为准')
  }
  assert.match(withdrawalScheduleText({ rules: { applicationTime: '随时可提交申请' } }), /随时可提交申请/)
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
