import { amountToMills, millsToAmount } from './pricing.mjs'

function balanceToMills(value) {
  const text = String(value ?? '').trim()
  const negative = text.startsWith('-')
  const mills = amountToMills(negative ? text.slice(1) : text)
  return mills === null ? null : negative ? -mills : mills
}

export function formatLedgerBalance(value) {
  const mills = balanceToMills(value)
  return mills === null ? '—' : millsToAmount(mills)
}

export function cashToCents(value) {
  if (!/^\d+(?:\.\d{1,2})?$/.test(String(value ?? '').trim())) return null
  const mills = amountToMills(value)
  return mills === null ? null : mills / 10
}

function formatCents(cents) {
  return `${Math.floor(cents / 100)}.${String(cents % 100).padStart(2, '0')}`
}

// 提现仅精确到分；向下取整，不能把不足 1 分的余额四舍五入后申请。
export function availableCash(balance) {
  const mills = balanceToMills(balance)
  if (mills === null) return null
  const positiveMills = Math.max(0, mills)
  const cents = Math.floor(positiveMills / 10)
  const remainderMills = positiveMills - cents * 10
  return { cents, amount: formatCents(cents), remainderMills, remainder: millsToAmount(remainderMills) }
}

export function withdrawalBalanceView(data) {
  const ledger = availableCash(data?.balance)
  if (!ledger) return null
  const serverCash = data.cashableBalance == null ? null : availableCash(data.cashableBalance)
  if (!serverCash) return ledger
  const cents = Math.min(ledger.cents, serverCash.cents)
  const serverRemainder = balanceToMills(data.fractionalBalance)
  const remainderMills = serverRemainder !== null && serverRemainder >= 0 && serverRemainder < 10 ? serverRemainder : ledger.remainderMills
  return { cents, amount: formatCents(cents), remainderMills, remainder: millsToAmount(remainderMills) }
}

export function isValidWithdrawal(amount, balance, minimum = 0) {
  const cents = cashToCents(amount)
  const balanceMills = balanceToMills(balance)
  const minimumMills = amountToMills(minimum)
  return cents !== null && cents > 0 && balanceMills !== null && minimumMills !== null
    && cents * 10 >= minimumMills && cents * 10 <= balanceMills
}

export function withdrawalRuleRows(data) {
  const cash = withdrawalBalanceView(data)
  const policy = data?.withdrawalRules || {}
  const minimum = cashToCents(data?.minWithdraw)
  const amountRule = cash && minimum !== null
    ? `本次最多可申请 ${cash.amount} 元；${formatCents(Math.max(1, minimum))} 元起，支持部分或全部提现。`
    : '以加载成功后显示的可提现金额为准，金额精确到分。'
  return [
    { label: '可提现额度', value: amountRule },
    { label: '每日提现次数', value: Number(policy.dailyLimit || 0) > 0 ? `每日最多 ${policy.dailyLimit} 次。` : '不限制每日申请次数；已申请的金额在处理期间暂不可重复申请。' },
    { label: '提现时间', value: policy.applicationTime || '全天 24 小时可提交申请。' },
    { label: '到账时间', value: policy.arrivalTime || '人工审核后安排转账，实际到账以收款渠道处理为准。' },
    { label: '收款方式', value: policy.payoutMethod || '人工审核后转账至已审核的收款账户。' },
    { label: '费用与代扣', value: Number(data?.taxMode) === 1 && Number.isFinite(Number(data?.taxRate))
      ? `平台不收取提现手续费；按当前配置预估代扣 ${(Number(data.taxRate) * 100).toFixed(0)}%，最终代扣和到账金额以财务审核为准。`
      : '平台不收取提现手续费；当前申请不预扣税额，实际到账以结算结果为准。' },
    { label: '余额说明', value: '仅已结算且未申请提现的佣金可提，扣回款优先抵扣。冻结中、待结算的佣金不计入可提现额度；不足 1 分的余额保留累计。' }
  ]
}
