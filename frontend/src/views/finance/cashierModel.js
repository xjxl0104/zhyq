// 收银台下拉选项的展示逻辑(纯函数,便于测试)。
// 口径:后端 /finance/bill/payable-tenants 返回全部租客(欠款的在前),
// 这里只负责把一条选项渲染成人能读的文案 —— 租客 id 永远跟着名字走,不裸奔。

export function tenantOwe(t) {
  return Number(t?.owe || 0)
}

export function hasOutstanding(t) {
  return tenantOwe(t) > 0
}

function formatMoney(v) {
  return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

// 选项右侧徽标:有欠款 → 「N 笔 · ¥金额」;没欠款 → 「无欠款」(照样可选,选进去看账)
export function tenantOptionBadge(t) {
  if (!hasOutstanding(t)) return '无欠款'
  return `${t.billCount} 笔 · ¥${formatMoney(t.owe)}`
}

// 选项主文案:后端已保证 tenantName 兜底(档案名/登记明细名/「租客 #id」),
// 这里再兜一层防御,任何情况下都不显示空白
export function tenantOptionLabel(t) {
  if (t?.tenantName) return t.tenantName
  return t?.tenantRefId != null ? `租客 #${t.tenantRefId}` : '-'
}

// ---------- 金额:一律整数分运算 ----------
// 浮点直接加减会留下 8.79e-14 这类残差:残差进了预算,尾单就会发出 0 元收款请求,
// 被后端"收款金额必须大于0"拒绝,整批收款中断。

// 前提:入参本身不超过两位小数(后端 DECIMAL(14,2) 序列化值 / :precision="2" 的输入框)。
// 三位小数的 1.005 这类值在二进制浮点下会被 Math.round 舍错半分,别把这函数用在
// 未约束精度的计算结果上
export function toCents(v) {
  return Math.round(Number(v || 0) * 100)
}

// 单张账单欠款(分):本金 + 滞纳金 - 实收,与后端 BillMetrics.outstandingOf 同口径
export function billOweCents(row) {
  return Math.max(0, toCents(row?.amount) + toCents(row?.lateFee) - toCents(row?.paidAmount))
}

export function billOwe(row) {
  return billOweCents(row) / 100
}

/**
 * 结算计划:把本次收款金额按选中顺序拆到每张账单上。
 * 整数分递减,永不产生 0 元条目;金额不足整单时只收部分。
 * @returns {Array<{billId: number, amount: number}>} amount 单位元,精确两位小数
 */
export function buildPaymentPlan(rows, payAmountYuan) {
  let budget = toCents(payAmountYuan)
  const plan = []
  for (const row of rows || []) {
    if (budget <= 0) break
    const owe = billOweCents(row)
    if (owe <= 0) continue
    const cents = Math.min(budget, owe)
    plan.push({ billId: row.id, amount: cents / 100 })
    budget -= cents
  }
  return plan
}
