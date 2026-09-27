import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/profile/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function harness(profile, failure) {
  const calls = [], events = {}
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, onShow: callback => { events.show = callback },
    meApi: { me: async () => { calls.push('load'); return profile }, update: async body => { calls.push(JSON.parse(JSON.stringify(body))); if (failure) throw new Error(failure) } },
    uni: { showToast: () => calls.push('saved') },
  })
  vm.runInContext(source, context)
  return { context, calls, events }
}

test('后补伙伴资料读取完整联系电话，保存不提交头像或身份字段', async () => {
  const { context, calls } = harness({ name: '伙伴', phone: '138****8000', contactPhone: '13800138000', phoneEditable: true })
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('form.phone', context), '13800138000')
  await vm.runInContext('save()', context)
  assert.deepEqual(calls, ['load', { name: '伙伴', phone: '13800138000' }, 'load', 'saved'])
})

test('微信绑定手机号保留脱敏显示，只保存姓名，不把脱敏手机号误判为错误', async () => {
  const { context, calls } = harness({ name: '伙伴', phone: '138****8000', phoneEditable: false })
  await vm.runInContext('load()', context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls, ['load', { name: '伙伴' }, 'load', 'saved'])
})

test('返回页面不会覆盖未保存资料，保存失败仍保留输入', async () => {
  const { context, calls, events } = harness({ name: '伙伴', phoneEditable: true }, '网络暂不可用')
  await vm.runInContext('load()', context)
  vm.runInContext("form.name='新姓名'; form.phone='13800138000'", context)
  await events.show()
  assert.equal(calls.filter(call => call === 'load').length, 1)
  await vm.runInContext('save()', context)
  assert.equal(vm.runInContext('form.name', context), '新姓名')
  assert.equal(vm.runInContext('form.phone', context), '13800138000')
  assert.equal(vm.runInContext('error.value', context), '网络暂不可用')
  assert.equal(calls.includes('saved'), false)
})

test('后补联系电话允许留空，非空时验证格式', async () => {
  const { context, calls } = harness({ name: '伙伴', phoneEditable: true })
  await vm.runInContext('load()', context)
  vm.runInContext("form.phone='123'", context)
  await vm.runInContext('save()', context)
  assert.equal(calls.length, 1)
  vm.runInContext("form.phone=''", context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls[1], { name: '伙伴' })
})

test('清空已有资料后回显服务端实际保留值，避免误报已清空', async () => {
  const { context } = harness({ name: '原姓名', phone: '138****8000', contactPhone: '13800138000', phoneEditable: true })
  await vm.runInContext('load()', context)
  vm.runInContext("form.name=''; form.phone=''", context)
  await vm.runInContext('save()', context)
  assert.equal(vm.runInContext('form.name', context), '原姓名')
  assert.equal(vm.runInContext('form.phone', context), '13800138000')
})
