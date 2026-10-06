import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { createPager } from '../src/utils/pagination.mjs'
import { incomeFilters, incomePage, incomeAmount, incomeTime } from '../src/utils/income.mjs'

const source = readFileSync(new URL('../src/pages/income/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const plain = value => JSON.parse(JSON.stringify(value))
function page(fetchPage) {
  const events = {}
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value,
    computed: callback => ({ get value() { return callback() } }),
    onShow: callback => { events.show = callback },
    onReachBottom: callback => { events.bottom = callback },
    onUnmounted: callback => { events.unmount = callback },
    bizApi: { commissions: fetchPage }, createPager,
    incomeFilters, incomePage, incomeAmount, incomeTime,
  })
  vm.runInContext(source, context)
  return { events, state: vm.runInContext('state', context), run: expression => vm.runInContext(expression, context) }
}

test('收益首次进入和切回全部不发送 undefined 状态，真实空结果结束加载', async () => {
  const calls = []
  const income = page(async params => { calls.push(params); return { records: [], total: 0 } })
  await income.events.show()
  assert.equal(Object.hasOwn(calls[0], 'status'), false)
  assert.deepEqual(calls[0], { pageNo: 1, pageSize: 20 })
  assert.equal(income.state.loading, false)
  assert.equal(income.state.loaded, true)
  assert.equal(income.state.error, '')
  assert.deepEqual(plain(income.run('list.value')), [])
  await income.run("changeStatus('2')")
  assert.equal(calls[1].status, 2)
  assert.equal(income.run('selectedLabel.value'), '可结算')
  await income.run("changeStatus('')")
  assert.equal(Object.hasOwn(calls[2], 'status'), false)
  await income.events.bottom()
  assert.equal(calls.length, 3, '空列表不反复触底请求')
})

test('收益请求失败保留具体提示，重试成功后恢复为空结果', async () => {
  let fail = true
  const income = page(async () => {
    if (fail) throw new Error('网络连接超时，请重试')
    return { records: [], total: 0 }
  })
  await income.events.show()
  assert.equal(income.state.loading, false)
  assert.equal(income.state.loaded, false)
  assert.equal(income.state.error, '网络连接超时，请重试')
  fail = false
  await income.run('pager.retry()')
  assert.equal(income.state.loaded, true)
  assert.equal(income.state.error, '')
})

test('分页失败保留已显示收益，重试继续同一页和同一筛选', async () => {
  const calls = []
  let fail = true
  const income = page(async params => {
    calls.push(params)
    if (params.pageNo === 2 && fail) throw new Error('服务暂时不可用')
    return { records: [{ id: params.pageNo, amount: '12.345' }], total: 2 }
  })
  await income.run("changeStatus('1')")
  await income.events.bottom()
  assert.equal(income.state.loading, false)
  assert.equal(income.state.records.length, 1)
  assert.equal(income.state.records[0].id, 1)
  fail = false
  await income.run('pager.retry()')
  assert.deepEqual(calls.map(call => [call.pageNo, call.status]), [[1, 1], [2, 1], [2, 1]])
  assert.equal(income.state.records.length, 2)
  assert.equal(income.state.error, '')
})

test('成功响应结构损坏显示可重试错误，不留下加载中或伪装暂无佣金', async () => {
  for (const result of [null, {}, { records: {}, total: 0 }, { records: [null], total: 1 }, { records: [{ amount: 10 }], total: 1 }, { records: [], total: 'bad' }]) {
    const income = page(async () => result)
    await income.events.show()
    assert.equal(income.state.loading, false)
    assert.equal(income.state.loaded, false)
    assert.match(income.state.error, /收益数据暂时无法读取/)
  }
})

test('收益金额与时间的缺失值不崩溃、不显示 NaN 或错误的零元', () => {
  assert.equal(incomeAmount('12.345'), '12.345')
  assert.equal(incomeAmount('-2.1'), '-2.100')
  assert.equal(incomeAmount(0), '0.000')
  for (const value of [null, undefined, '', 'bad', Infinity]) assert.equal(incomeAmount(value), '—')
  assert.equal(incomeTime('2026-10-06T10:22:30'), '2026-10-06 10:22')
  assert.equal(incomeTime('2026-10-06T10:22:30', true), '2026-10-06')
  for (const value of [null, {}, 1728000000, []]) assert.equal(incomeTime(value), '时间待确认')
})
