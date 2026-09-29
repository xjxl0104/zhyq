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
