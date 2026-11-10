import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/team-add/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function harness({ profile = async () => ({ positionCode: 'P4', status: 1 }), create = async () => {} } = {}) {
  const calls = [], timers = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), onShow: () => {},
    meApi: { me: profile, addTeamMember: async data => { calls.push(data); await create(data) } },
    uni: { showToast: () => {}, navigateBack: () => {} }, setTimeout: callback => { timers.push(callback) }
  })
  vm.runInContext(source, context)
  vm.runInContext("name.value = '测试成员'", context)
  const run = code => vm.runInContext(code, context)
  return { calls, timers, run }
}

test('账号读取完成前无法提交，P4读取完成后保留默认P1选择', async () => {
  const gate = deferred()
  const { calls, run } = harness({ profile: () => gate.promise })
  const loading = run('loadProfile()')
  await run('submit()')
  assert.equal(calls.length, 0)
  assert.equal(run('formDisabled.value'), true)
  gate.resolve({ positionCode: 'P4', status: 1 })
  await loading
  assert.equal(run('positionIndex.value'), 0)
  await run('submit()')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].positionCode, 'P1')
})

test('账号读取失败时不降为普通身份提交，可以重新读取后继续', async () => {
  let attempts = 0
  const { calls, run } = harness({ profile: async () => {
    if (++attempts === 1) throw new Error('读取超时')
    return { positionCode: 'P4', status: 1 }
  } })
  await run('loadProfile()')
  await run('submit()')
  assert.equal(calls.length, 0)
  assert.equal(run('profileLoading.value'), false)
  assert.match(run('error.value'), /读取超时/)
  await run('loadProfile()')
  await run('submit()')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].positionCode, 'P1')
})

test('请求中和成功返回等待离页期间重复点击只创建一个无手机号成员', async () => {
  const gate = deferred()
  const { calls, timers, run } = harness({ create: () => gate.promise })
  await run('loadProfile()')
  const first = run('submit()')
  await run('submit()')
  assert.equal(calls.length, 1)
  gate.resolve()
  await first
  await run('submit()')
  await run('submit()')
  assert.equal(calls.length, 1)
  assert.equal(calls[0].phone, '')
  assert.equal(timers.length, 1)
  assert.equal(run('busy.value'), false)
  assert.equal(run('submitted.value'), true)
  assert.equal(run('formDisabled.value'), true)
})

test('新增失败保留输入与称号并允许重试，成功后才锁定', async () => {
  let attempts = 0
  const { calls, run } = harness({ create: async () => { if (++attempts === 1) throw new Error('保存失败') } })
  await run('loadProfile()')
  run('positionIndex.value = 2')
  await run('submit()')
  assert.equal(run('submitted.value'), false)
  assert.equal(run('formDisabled.value'), false)
  assert.equal(run('name.value'), '测试成员')
  assert.equal(run('positionIndex.value'), 2)
  assert.match(run('error.value'), /保存失败/)
  await run('submit()')
  assert.equal(calls.length, 2)
  assert.equal(calls[1].positionCode, 'P3')
  assert.equal(run('submitted.value'), true)
})

test('非正常账号读取后仍不能新增成员', async () => {
  for (const status of [2, 3, 4]) {
    const { calls, run } = harness({ profile: async () => ({ positionCode: 'P4', status }) })
    await run('loadProfile()')
    await run('submit()')
    assert.equal(calls.length, 0)
    assert.equal(run('profileReady.value'), false)
    assert.match(run('error.value'), /账号状态/)
  }
})

test('普通伙伴读取完成后不自行指定称号，仍由服务端按邀请身份定级', async () => {
  for (const positionCode of ['P1', 'P2', 'P3']) {
    const { calls, run } = harness({ profile: async () => ({ positionCode, status: 1 }) })
    await run('loadProfile()')
    await run('submit()')
    assert.equal(calls.length, 1)
    assert.equal(calls[0].positionCode, undefined)
  }
})
