import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/team/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const addSource = readFileSync(new URL('../src/pages/team-add/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function harness({ positionCode = 'P4', confirm = true, failure = '' } = {}) {
  const calls = [], confirmations = []
  const members = [{ id: 11, name: '成员甲', positionCode: 'P1' }]
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), onShow: () => {},
    meApi: { me: async () => ({ id: 4, positionCode, status: 1 }), team: async () => { calls.push('direct-team'); return members } },
    pricingApi: {
      team: async () => { calls.push('managed-team'); return members },
      positions: async () => ['P1', 'P2', 'P3', 'P4'].map(code => ({ code, name: code === 'P4' ? '园区负责人' : '团队称号' })),
      setPosition: async (id, code) => { calls.push({ id, code }); if (failure) throw new Error(failure); members[0].positionCode = code }
    },
    uni: { showModal: options => { confirmations.push(options.content); options.success({ confirm }) }, showToast: () => calls.push('saved') }
  })
  vm.runInContext(source, context)
  return { context, calls, confirmations, members }
}

test('P4读取整个邀请体系并使用服务端称号选项，非P4仍读取直属成员', async () => {
  const owner = harness()
  await vm.runInContext('load()', owner.context)
  assert.deepEqual(owner.calls, ['managed-team'])
  assert.equal(vm.runInContext('positions.value.length', owner.context), 3)
  assert.equal(vm.runInContext("positions.value.some(row => row.code === 'P4')", owner.context), false)
  const member = harness({ positionCode: 'P1' })
  await vm.runInContext('load()', member.context)
  await vm.runInContext('changePosition(list.value[0], {detail:{value:2}})', member.context)
  assert.deepEqual(member.calls, ['direct-team'])
})
test('新增成员只能选择P1至P3，异常选项不提交', async () => {
  const calls = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), onShow: () => {},
    meApi: { addTeamMember: async value => calls.push(value) },
    uni: { showToast: () => {} }, setTimeout: () => {}
  })
  vm.runInContext(addSource, context)
  vm.runInContext("me.value = {positionCode:'P4',status:1}; name.value='成员乙'; positionIndex.value=3", context)
  await vm.runInContext('submit()', context)
  assert.equal(calls.length, 0)
  assert.match(vm.runInContext('error.value', context), /P1–P3/)
  vm.runInContext('positionIndex.value=2', context)
  await vm.runInContext('submit()', context)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].positionCode, 'P3')
})

test('P4管理者仅能授予P1到P3，确认后提交目标成员和称号并回显', async () => {
  const { context, calls, confirmations } = harness()
  await vm.runInContext('load()', context)
  await vm.runInContext('changePosition(list.value[0], {detail:{value:2}})', context)
  assert.match(confirmations[0], /P3/)
  assert.match(confirmations[0], /不会自动套用/)
  assert.deepEqual(calls.find(call => typeof call === 'object'), { id: 11, code: 'P3' })
  assert.equal(vm.runInContext('list.value[0].positionCode', context), 'P3')
})

test('取消确认不改变成员称号，不调用写接口', async () => {
  const { context, calls } = harness({ confirm: false })
  await vm.runInContext('load()', context)
  await vm.runInContext('changePosition(list.value[0], {detail:{value:2}})', context)
  assert.deepEqual(calls, ['managed-team'])
  assert.equal(vm.runInContext('list.value[0].positionCode', context), 'P1')
})

test('P4不能通过页面操作更改自己的称号', async () => {
  const { context, calls, confirmations } = harness()
  await vm.runInContext('load()', context)
  await vm.runInContext("changePosition({id:'4',positionCode:'P4'}, {detail:{value:0}})", context)
  assert.deepEqual(calls, ['managed-team'])
  assert.deepEqual(confirmations, [])
})

test('服务端拒绝变更时保留原称号，解除禁用并显示可重试错误', async () => {
  const { context, calls } = harness({ failure: '该成员已离开邀请体系' })
  await vm.runInContext('load()', context)
  await vm.runInContext('changePosition(list.value[0], {detail:{value:2}})', context)
  assert.equal(vm.runInContext('list.value[0].positionCode', context), 'P1')
  assert.equal(vm.runInContext('savingMemberId.value', context), '')
  assert.equal(vm.runInContext('error.value', context), '该成员已离开邀请体系')
  assert.equal(calls.includes('saved'), false)
})

test('已有P4只能后台调整，伪造选项也不能从手机授予P4', async () => {
  const { context, calls, confirmations } = harness()
  await vm.runInContext('load()', context)
  await vm.runInContext("changePosition({id:12,name:'已有P4',positionCode:'P4'}, {detail:{value:0}})", context)
  await vm.runInContext("positions.value.push({code:'P4',name:'恶意选项'}); changePosition(list.value[0], {detail:{value:3}})", context)
  assert.deepEqual(calls, ['managed-team'])
  assert.deepEqual(confirmations, [])
})
