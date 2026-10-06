import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { availableCash, cashToCents, formatLedgerBalance, isValidWithdrawal, withdrawalBalanceView } from '../src/utils/withdrawal.mjs'

test('余额显示千分位，“全部”只能按分向下截取，余款保留', () => {
  assert.equal(formatLedgerBalance('100.009'), '100.009')
  assert.deepEqual(availableCash('100.009'), { cents: 10000, amount: '100.00', remainderMills: 9, remainder: '0.009' })
  assert.deepEqual(availableCash('0.009'), { cents: 0, amount: '0.00', remainderMills: 9, remainder: '0.009' })
  assert.deepEqual(availableCash('100.010'), { cents: 10001, amount: '100.01', remainderMills: 0, remainder: '0.000' })
})

test('按分取整不受浮点误差影响，0.29和1.15不多丢一分', () => {
  assert.equal(availableCash(0.29).amount, '0.29')
  assert.equal(availableCash(1.15).amount, '1.15')
  assert.equal(availableCash('9999999.999').amount, '9999999.99')
})

test('优先使用后端可提金额字段，同时保证填入金额不超过账本余额', () => {
  assert.deepEqual(withdrawalBalanceView({ balance: '100.009', cashableBalance: '100.00', fractionalBalance: '0.009' }), availableCash('100.009'))
  assert.equal(withdrawalBalanceView({ balance: '100.009', cashableBalance: '99.00', fractionalBalance: '0.009' }).amount, '99.00')
  assert.equal(withdrawalBalanceView({ balance: '100.009', cashableBalance: '100.01' }).amount, '100.00')
  assert.equal(withdrawalBalanceView({ balance: '100.009' }).amount, '100.00')
})

test('扣回后的负余额照实展示，可申请金额为零，异常余额不伪造数字', () => {
  assert.equal(formatLedgerBalance('-0.003'), '-0.003')
  assert.equal(availableCash('-0.003').amount, '0.00')
  assert.equal(isValidWithdrawal('0.01', '-0.003', 0), false)
  assert.equal(formatLedgerBalance(undefined), '—')
  assert.equal(availableCash('未知'), null)
})

test('提现支持部分金额，仍校验最低金额、分精度和实际余额', () => {
  assert.equal(isValidWithdrawal('100.00', '100.009', '100'), true)
  assert.equal(isValidWithdrawal('100.00', '200.009', '100'), true)
  assert.equal(isValidWithdrawal('99.99', '100.009', '100'), false)
  assert.equal(isValidWithdrawal('100.01', '100.009', '100'), false)
  assert.equal(isValidWithdrawal('100.009', '100.009', '100'), false)
  assert.equal(isValidWithdrawal('0', '100.009', '0'), false)
  for (const value of ['', '1e2', '-0.01', 'abc', '0.001']) assert.equal(cashToCents(value), null)
})

const source = readFileSync(new URL('../src/pages/withdraw/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness({ balance = '100.009', failure = '', idVerified = 1 } = {}) {
  const calls = []
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, computed: getter => ({ get value() { return getter() } }),
    onShow: () => {}, onReachBottom: () => {}, onUnmounted: () => {},
    withdrawalBalanceView, formatLedgerBalance, isValidWithdrawal,
    createPager: (api, state) => { state.records = []; return { load: async () => {}, invalidate: () => {} } },
    bizApi: { withdrawals: async () => [], balance: async () => ({ balance, cashableBalance: availableCash(balance).amount, fractionalBalance: availableCash(balance).remainder, minWithdraw: '100', idVerified, taxMode: 1, taxRate: 0.2 }),
      withdraw: async amount => { calls.push(amount); if (failure) throw new Error(failure) } },
    uni: { showToast: () => calls.push('submitted') }
  })
  vm.runInContext(source, context)
  return { context, calls }
}

test('全部按钮填入两位金额并可提交，不能把三位余额直接发送', async () => {
  const { context, calls } = harness()
  await vm.runInContext('load()', context)
  vm.runInContext('fillAll()', context)
  assert.equal(vm.runInContext('amount.value', context), '100.00')
  assert.equal(vm.runInContext('validAmount.value', context), true)
  await vm.runInContext('submit()', context)
  assert.deepEqual(calls, ['100.00', 'submitted'])
})

test('不足一分或收款资料未审核不能提交；服务端拒绝时保留输入', async () => {
  for (const options of [{ balance: '0.009' }, { idVerified: 0 }]) {
    const { context, calls } = harness(options)
    await vm.runInContext('load()', context)
    vm.runInContext('fillAll()', context)
    await vm.runInContext('submit()', context)
    assert.deepEqual(calls, [])
  }
  const { context, calls } = harness({ failure: '余额已变化，请重试' })
  await vm.runInContext('load()', context)
  vm.runInContext('fillAll()', context)
  await vm.runInContext('submit()', context)
  assert.equal(vm.runInContext('amount.value', context), '100.00')
  assert.equal(vm.runInContext('error.value', context), '余额已变化，请重试')
  assert.equal(vm.runInContext('loading.value', context), false)
  assert.deepEqual(calls, ['100.00'])
})

test('余额刷新失败后不能使用过期余额再次申请，重试成功后恢复', async () => {
  const { context, calls } = harness()
  await vm.runInContext('load()', context)
  vm.runInContext('fillAll()', context)
  const readBalance = context.bizApi.balance
  context.bizApi.balance = async () => { throw new Error('余额刷新失败') }
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('b.value', context), null)
  assert.equal(vm.runInContext('validAmount.value', context), false)
  await vm.runInContext('submit()', context)
  assert.deepEqual(calls, [])
  assert.equal(vm.runInContext('amount.value', context), '100.00')
  context.bizApi.balance = readBalance
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('validAmount.value', context), true)
})

test('手工输入提现金额同样遵守服务端可提上限', async () => {
  const { context, calls } = harness({ balance: '200.009' })
  const readBalance = context.bizApi.balance
  context.bizApi.balance = async () => ({ ...await readBalance(), cashableBalance: '100.00' })
  await vm.runInContext('load()', context)
  vm.runInContext("amount.value = '150.00'", context)
  assert.equal(vm.runInContext('validAmount.value', context), false)
  await vm.runInContext('submit()', context)
  assert.deepEqual(calls, [])
  vm.runInContext('fillAll()', context)
  assert.equal(vm.runInContext('amount.value', context), '100.00')
  assert.equal(vm.runInContext('validAmount.value', context), true)
})
