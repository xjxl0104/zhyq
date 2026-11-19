// 结算提现页的纯计算：金额格式、资金流转占比、柱状图高度、提现状态文案。
const n = v => { const x = Number(v); return Number.isFinite(x) ? x : 0 }
export const money = v => n(v).toFixed(2)

/** 资金流转的五段（顺序即流转顺序），percent 为占五段合计的比例，合计为 0 时全为 0。 */
export function flowSegments(totals = {}) {
  const segs = [
    { key: 'frozen', label: '冻结中', tone: 'frozen', amount: n(totals.frozen) },
    { key: 'settleable', label: '待结算', tone: 'settleable', amount: n(totals.settleable) },
    { key: 'withdrawable', label: '可提现', tone: 'withdrawable', amount: n(totals.withdrawable) },
    { key: 'withdrawing', label: '提现审批中', tone: 'withdrawing', amount: n(totals.withdrawing) },
    { key: 'withdrawn', label: '已提现', tone: 'withdrawn', amount: n(totals.withdrawn) },
  ]
  const sum = segs.reduce((s, x) => s + Math.max(0, x.amount), 0)
  return segs.map(x => ({ ...x, percent: sum > 0 ? Math.round(Math.max(0, x.amount) / sum * 1000) / 10 : 0 }))
}

/** 按最大值把数值换成柱高（rpx），非零值至少 4，零值为 0。 */
export function barHeights(values, maxHeight = 220) {
  const max = Math.max(0, ...values.map(n))
  return values.map(v => (max <= 0 || n(v) <= 0) ? 0 : Math.max(4, Math.round(n(v) / max * maxHeight)))
}

/** 合同/月份的结算进度：已结算、待结算、冻结三段百分比。 */
export function settleShares(row = {}) {
  const total = n(row.settled) + n(row.settleable) + n(row.frozen)
  if (total <= 0) return { settled: 0, settleable: 0, frozen: 0 }
  const pct = v => Math.round(n(v) / total * 1000) / 10
  return { settled: pct(row.settled), settleable: pct(row.settleable), frozen: pct(row.frozen) }
}

export const WITHDRAW_STATUS = { 1: '财务审批中', 2: '审批通过·待打款', 3: '已打款', 4: '已驳回' }
export function withdrawalStatusText(w = {}) {
  const base = WITHDRAW_STATUS[w.status] || '处理中'
  return Number(w.status) === 1 && w.nodeName ? `${base}（${w.nodeName}）` : base
}

/** 时间节点说明（与后台自动任务一致）。 */
export function scheduleSteps(schedule = {}) {
  const freeze = n(schedule.freezeDays) || 7
  const day = n(schedule.autoWithdrawDay)
  return [
    { title: '客户发货', desc: '按发货日生成佣金' },
    { title: `冻结 ${freeze} 天`, desc: '发货满冻结期自动解冻' },
    { title: '自动结算', desc: `每天 ${schedule.settleTime?.replace('每天 ', '') || '02:00'} 结算已结束月份的已解冻佣金，计入可提现余额` },
    { title: '自动提现', desc: day > 0 ? `每月 ${day} 日 10:00 自动把全部可提现余额提交申请；也可随时手动申请` : '随时手动申请提现' },
    { title: '财务审批', desc: '数智云仓财务审批提现申请' },
    { title: '打款到账', desc: schedule.rules?.arrivalTime || '审批通过后由财务打款' },
  ]
}
