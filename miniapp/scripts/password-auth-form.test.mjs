import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const component = readFileSync(new URL('../src/components/PasswordAuthForm.vue', import.meta.url), 'utf8')
const source = component.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function harness({ warehouse = false, registering = false, username = '123', password = '123', confirmation = password } = {}) {
  const calls = []
  const api = role => ({
    passwordLogin: async data => { calls.push({ role, action: 'login', data }); return { token: 'ok' } },
    passwordRegister: async data => { calls.push({ role, action: 'register', data }); return { token: 'ok' } },
  })
  const context = vm.createContext({
    defineProps: () => ({ warehouse, inviteCode: '' }), defineEmits: () => (...args) => calls.push({ emitted: args }),
    ref: value => ({ value }), reactive: value => value, computed: get => ({ get value() { return get() } }), watch: () => {},
    authApi: api('mp'), warehouseAuthApi: api('wh'), values: { username, password, phone: '13800138000', name: '测试', warehouseName: '云仓', agreed: true }, confirmed: confirmation,
  })
  vm.runInContext(source + `\nregistering.value=${registering};Object.assign(form,values);confirmation.value=confirmed;`, context)
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

test('登录注册表单保留必填和确认密码检查', async () => {
  for (const [values, message] of [
    [{ username: '' }, '请输入账号'],
    [{ password: '' }, '请输入密码'],
    [{ registering: true, confirmation: '124' }, '两次输入的密码不一致'],
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
    assert.ok(inputs.length >= 3)
    for (const input of inputs) assert.match(input, /:maxlength="-1"/)
  }
})
