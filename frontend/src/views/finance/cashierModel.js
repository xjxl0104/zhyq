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
