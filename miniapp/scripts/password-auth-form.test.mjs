import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const component = readFileSync(new URL('../src/components/PasswordAuthForm.vue', import.meta.url), 'utf8')
const source = component.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function harness({ warehouse = false, registering = false, username = '123', password = '123', agreed = true, inviteCode = '' } = {}) {
  const calls = []
  const api = role => ({
    passwordLogin: async data => { calls.push({ role, action: 'login', data }); return { token: 'ok' } },
    passwordRegister: async data => { calls.push({ role, action: 'register', data }); return { token: 'ok' } },
  })
  const context = vm.createContext({
    defineProps: () => ({ warehouse, inviteCode: '' }), defineEmits: () => (...args) => calls.push({ emitted: args }),
    ref: value => ({ value }), reactive: value => value, computed: get => ({ get value() { return get() } }), watch: () => {},
    authApi: api('mp'), warehouseAuthApi: api('wh'), values: { username, password, agreed, inviteCode },
  })
  vm.runInContext(source + `\nregistering.value=${registering};Object.assign(form,values);`, context)
  return { calls, context }
}

for (const warehouse of [false, true]) {
  for (const registering of [false, true]) {
    test(`${warehouse ? '云仓' : '伙伴'}${registering ? '注册' : '登录'}完整提交123及中文符号长密码`, async () => {
      for (const [username, password] of [['123', '123'], ['园区 伙伴@1', '字'], ['😀'.repeat(512), '密🔑'.repeat(300) + '尾']]) {
        const { calls, context } = harness({ warehouse, registering, username, password })
        await vm.runInContext('submit()', context)
        assert.equal(calls[0]?.role, warehouse ? 'wh' : 'mp')
        assert.equal(calls[0]?.action, registering ? 'register' : 'login')
        assert.equal(calls[0]?.data.username, username)
        assert.equal(calls[0]?.data.password, password)
        assert.equal(calls[1]?.emitted[0], 'authenticated')
      }
    })
  }
}

test('登录注册保留账号密码必填，注册必须同意协议', async () => {
  for (const [values, message] of [
    [{ username: '' }, '请输入账号'],
    [{ password: '' }, '请输入密码'],
    [{ registering: true, agreed: false }, '请先阅读并同意协议'],
  ]) {
    const { calls, context } = harness(values)
    await vm.runInContext('submit()', context)
    assert.deepEqual(calls, [])
    assert.equal(vm.runInContext('error.value', context), message)
  }
})

test('所有账号和密码输入框显式取消uni-app默认长度上限，避免静默截断', () => {
  const security = readFileSync(new URL('../src/pages/account-security/index.vue', import.meta.url), 'utf8')
  for (const source of [component, security]) {
    const inputs = source.match(/<input[^>]+v-model="(?:form\.(?:username|password|currentPassword)|confirmation)"[^>]*>/g)
    assert.ok(inputs.length >= 2)
    for (const input of inputs) assert.match(input, /:maxlength="-1"/)
  }
})

for (const warehouse of [false, true]) {
  test(`${warehouse ? '云仓' : '伙伴'}无需业务资料即可注册`, async () => {
    const { calls, context } = harness({ warehouse, registering: true })
    await vm.runInContext('submit()', context)
    assert.deepEqual(JSON.parse(JSON.stringify(calls[0]?.data)), { username: '123', password: '123', agreed: true })
    assert.equal(calls[1]?.emitted[2], true)
  })
}

test('邀请链接仍带入伙伴注册，云仓注册不携带伙伴邀请', async () => {
  for (const warehouse of [false, true]) {
    const { calls, context } = harness({ warehouse, registering: true, inviteCode: 'ABCD1234' })
    await vm.runInContext('submit()', context)
    assert.equal(calls[0]?.data.inviteCode, warehouse ? undefined : 'ABCD1234')
  }
})

test('云仓注册完成直接进入工作台，不强制完善加盟资料', () => {
  const login = readFileSync(new URL('../src/pages/warehouse-login/index.vue', import.meta.url), 'utf8')
  const script = login.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  let route, saved
  const context = vm.createContext({ mock: false, ref: value => ({ value }), warehouseToken: { set: value => { saved = value } }, uni: { reLaunch: options => { route = options.url } } })
  vm.runInContext(script + "\ndone('new-token', true)", context)
  assert.equal(saved, 'new-token')
  assert.equal(route, '/pages/warehouse-dashboard/index')
})
