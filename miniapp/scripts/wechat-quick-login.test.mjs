import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')
const component = read('../src/components/WechatAuthForm.vue')
const scriptOf = source => source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const forPlatform = (source, platform) => source.replace(/\/\/ #(ifdef|ifndef) (H5|MP-WEIXIN)\n([\s\S]*?)\/\/ #endif/g,
  (_, condition, name, body) => (condition === 'ifdef') === (name === platform) ? body : '')
const wechatHelper = forPlatform(read('../src/utils/wechat.js')
  .split('export async function getWechatLogin')[1].split('export function readPhoneAuthorization')[0], 'MP-WEIXIN')
const helperSource = 'async function getWechatLogin' + wechatHelper
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
const clone = value => JSON.parse(JSON.stringify(value))

function harness({ warehouse = false, mock = false, agreed = true, inviteCode = '', uniLogin, quickLogin } = {}) {
  const calls = { login: [], accountInfo: 0, quick: [], emitted: [], oldLogin: 0 }
  const api = role => ({
    quickLogin: async data => {
      calls.quick.push({ role, data: clone(data) })
      return quickLogin ? quickLogin(data, calls.quick.length) : { token: `${role}-token`, registered: true }
    },
    wxLogin: () => { calls.oldLogin++; throw new Error('一键登录不得调用旧 wx-login') },
    bindPhone: () => { calls.oldLogin++; throw new Error('一键登录不得复用旧 bind-phone') },
  })
  const context = vm.createContext({
    watch: () => {}, ref: value => ({ value }), computed: get => ({ get value() { return get() } }),
    defineProps: () => ({ warehouse, inviteCode }),
    defineEmits: () => (...args) => calls.emitted.push(args),
    partnerMock: warehouse ? false : mock, warehouseMock: warehouse ? mock : false,
    authApi: api('mp'), warehouseAuthApi: api('wh'),
    uni: {
      login: options => { calls.login.push(clone(options)); return uniLogin ? uniLogin(calls.login.length) : Promise.resolve({ code: `wx-code-${calls.login.length}` }) },
      getAccountInfoSync: () => { calls.accountInfo++; return { miniProgram: { appId: 'wx-test-app' } } },
    },
  })
  vm.runInContext(helperSource + '\n' + scriptOf(component), context)
  vm.runInContext(`agreed.value=${agreed};testCode.value='  mock-identity  ';testPhone.value='13800138000';`, context)
  const run = value => vm.runInContext(value, context)
  return { calls, run }
}

for (const warehouse of [false, true]) {
  const role = warehouse ? '云仓' : '伙伴'
  test(`${role}微信一键登录无需手机号，只调用本身份 quick-login`, async () => {
    const { calls, run } = harness({ warehouse })
    await run('login()')
    assert.deepEqual(calls.login, [{ provider: 'weixin' }])
    assert.deepEqual(calls.quick, [{ role: warehouse ? 'wh' : 'mp', data: { jsCode: 'wx-code-1', appId: 'wx-test-app', agreed: true } }])
    assert.equal(calls.oldLogin, 0)
    assert.deepEqual(calls.emitted, [['busy', true], ['authenticated', warehouse ? 'wh-token' : 'mp-token'], ['busy', false]])
    assert.equal(run('busy.value'), false)
  })

  test(`${role}手机号授权后重新取微信 code，并使用同一 quick-login 接口`, async () => {
    const { calls, run } = harness({ warehouse })
    await run('login()')
    await run("onPhone({detail:{errMsg:'getPhoneNumber:ok',code:'phone-authorization-1'}})")
    assert.equal(calls.login.length, 2)
    assert.equal(calls.oldLogin, 0)
    assert.deepEqual(calls.quick[1], { role: warehouse ? 'wh' : 'mp', data: {
      jsCode: 'wx-code-2', appId: 'wx-test-app', agreed: true, phoneCode: 'phone-authorization-1',
    } })
    assert.equal(Object.hasOwn(calls.quick[1].data, 'phone'), false)
  })

  test(`${role}拒绝、取消或缺失手机号授权时不登录，并允许改用微信登录`, async () => {
    for (const detail of [
      { errMsg: 'getPhoneNumber:fail user deny' },
      { errMsg: 'getPhoneNumber:fail user cancel', code: 'must-not-use' },
      { errMsg: 'getPhoneNumber:ok' },
      {},
    ]) {
      const { calls, run } = harness({ warehouse })
      await run(`onPhone(${JSON.stringify({ detail })})`)
      assert.equal(calls.login.length, 0)
      assert.equal(calls.quick.length, 0)
      assert.equal(calls.emitted.length, 0)
      assert.match(run('error.value'), /微信一键登录/)
      await run('login()')
      assert.equal(calls.quick.length, 1)
      assert.equal(Object.hasOwn(calls.quick[0].data, 'phoneCode'), false)
      assert.equal(run('error.value'), '')
    }
  })

  test(`${role}未同意协议时，两种登录均不调用微信或后端`, async () => {
    for (const action of ['login()', "onPhone({detail:{errMsg:'getPhoneNumber:ok',code:'phone-code'}})", 'loginWithTestPhone()']) {
      const { calls, run } = harness({ warehouse, mock: action === 'loginWithTestPhone()', agreed: false })
      await run(action)
      assert.equal(calls.login.length, 0)
      assert.equal(calls.accountInfo, 0)
      assert.equal(calls.quick.length, 0)
      assert.equal(calls.emitted.length, 0)
      assert.match(run('error.value'), /同意协议/)
    }
  })

  test(`${role}登录忙时重复点击或手机号回调不能发起第二次请求`, async () => {
    const pending = deferred()
    const { calls, run } = harness({ warehouse, uniLogin: () => pending.promise })
    const first = run('login()')
    assert.equal(run('busy.value'), true)
    await run('login()')
    await run("onPhone({detail:{errMsg:'getPhoneNumber:ok',code:'another-phone-code'}})")
    assert.equal(calls.login.length, 1)
    assert.equal(calls.quick.length, 0)
    assert.deepEqual(calls.emitted, [['busy', true]])
    pending.resolve({ code: 'only-code' })
    await first
    assert.equal(calls.quick.length, 1)
    assert.equal(calls.quick[0].data.jsCode, 'only-code')
    assert.equal(run('busy.value'), false)
  })

  test(`${role}接口失败不发登录成功事件；再次授权会取新 code 后重试`, async () => {
    const { calls, run } = harness({ warehouse, quickLogin: async (_, count) => {
      if (count === 1) throw new Error('登录服务暂不可用')
      return { token: 'retry-token' }
    } })
    await run("onPhone({detail:{errMsg:'getPhoneNumber:ok',code:'phone-first'}})")
    assert.equal(calls.emitted.some(event => event[0] === 'authenticated'), false)
    assert.equal(run('busy.value'), false)
    assert.equal(run('error.value'), '登录服务暂不可用')
    await run("onPhone({detail:{errMsg:'getPhoneNumber:ok',code:'phone-retry'}})")
    assert.deepEqual(calls.quick.map(call => call.data.jsCode), ['wx-code-1', 'wx-code-2'])
    assert.deepEqual(calls.quick.map(call => call.data.phoneCode), ['phone-first', 'phone-retry'])
    assert.deepEqual(calls.emitted.filter(event => event[0] === 'authenticated'), [['authenticated', 'retry-token']])
    assert.equal(run('error.value'), '')
  })

  test(`${role}微信取 code 失败或无 code 不调接口，重试重新登录`, async () => {
    for (const failure of [() => Promise.reject(new Error('微信连接失败')), () => Promise.resolve({})]) {
      const { calls, run } = harness({ warehouse, uniLogin: count => count === 1 ? failure() : Promise.resolve({ code: 'fresh-code' }) })
      await run('login()')
      assert.equal(calls.quick.length, 0)
      assert.equal(calls.emitted.some(event => event[0] === 'authenticated'), false)
      assert.equal(run('busy.value'), false)
      assert.ok(run('error.value'))
      await run('login()')
      assert.equal(calls.login.length, 2)
      assert.equal(calls.quick[0].data.jsCode, 'fresh-code')
    }
  })

  test(`${role}响应缺少 token 时提示重试，不能发登录成功事件`, async () => {
    for (const response of [null, {}, { registered: true }, { token: '' }]) {
      const { calls, run } = harness({ warehouse, quickLogin: async () => response })
      await run('login()')
      assert.equal(calls.emitted.some(event => event[0] === 'authenticated'), false)
      assert.equal(run('busy.value'), false)
      assert.match(run('error.value'), /登录未完成.*重新/)
    }
  })

  test(`${role}开发 mock 手机号只走测试路径，真实手机号授权不传明文号码`, async () => {
    const { calls, run } = harness({ warehouse, mock: true })
    await run('loginWithTestPhone()')
    assert.equal(calls.login.length, 0)
    assert.equal(calls.accountInfo, 0)
    assert.deepEqual(calls.quick[0], { role: warehouse ? 'wh' : 'mp', data: {
      jsCode: 'mock-identity', appId: '', agreed: true, phone: '13800138000',
    } })
    const production = harness({ warehouse })
    await production.run('loginWithTestPhone()')
    assert.equal(production.calls.quick.length, 0, '真实环境不得使用 mock 明文手机号入口')
    assert.equal(production.calls.login.length, 0)
    assert.equal(production.calls.emitted.some(event => event[0] === 'authenticated'), false)
  })
}

test('邀请码只传入伙伴 quick-login，云仓不接收伙伴邀请', async () => {
  for (const warehouse of [false, true]) {
    const { calls, run } = harness({ warehouse, inviteCode: 'ABCD2345' })
    await run('login()')
    assert.equal(calls.quick[0].data.inviteCode, warehouse ? undefined : 'ABCD2345')
  }
})

test('mock 手机号校验失败不取 code 或调用接口', async () => {
  const { calls, run } = harness({ mock: true })
  run("testPhone.value='123'")
  await run('loginWithTestPhone()')
  assert.equal(calls.login.length, 0)
  assert.equal(calls.quick.length, 0)
  assert.match(run('error.value'), /测试手机号/)
})

for (const page of ['login', 'warehouse-login']) {
  for (const platform of ['H5', 'MP-WEIXIN']) {
    test(`${page} 在${platform} 默认使用${platform === 'H5' ? '账号密码' : '微信一键'}登录`, () => {
      const context = vm.createContext({ ref: value => ({ value }), mock: false, onLoad: () => {}, token: {}, warehouseToken: {} })
      vm.runInContext(forPlatform(scriptOf(read(`../src/pages/${page}/index.vue`)), platform), context)
      assert.equal(vm.runInContext('canUseWechat.value', context), platform === 'MP-WEIXIN')
      assert.equal(vm.runInContext('method.value', context), platform === 'H5' ? 'password' : 'wechat')
    })
  }
}

const requestSource = read('../src/utils/request.js').split('export function uploadWarehouseFile')[0]
  .replace(/^import .*$/gm, '').replaceAll('import.meta.env', '{}').replace(/^export /gm, '')
for (const warehouse of [false, true]) {
  test(`${warehouse ? '云仓' : '伙伴'} quick-login 401 是公开登录错误，不清除既有 token 或跳转`, async () => {
    for (const response of [
      { statusCode: 401, data: { code: 401, message: '微信凭证已过期，请重试' } },
      { statusCode: 200, data: { code: 401, message: '微信凭证已过期，请重试' } },
    ]) {
      const calls = { removed: [], routes: [], requests: [], toasts: [] }
      const context = vm.createContext({ uni: {
        getStorageSync: key => `${key}-existing`, removeStorageSync: key => calls.removed.push(key),
        reLaunch: options => calls.routes.push(options.url), showToast: options => calls.toasts.push(options),
        request: options => { calls.requests.push(options.url); options.success(response) },
      } })
      vm.runInContext(requestSource, context)
      await assert.rejects(vm.runInContext(`${warehouse ? 'whPost' : 'post'}('/auth/quick-login', {})`, context),
        error => error.message === '微信凭证已过期，请重试')
      assert.deepEqual(calls.removed, [])
      assert.deepEqual(calls.routes, [])
      assert.deepEqual(calls.requests, [`/api/${warehouse ? 'wh' : 'mp'}/v1/auth/quick-login`])
      assert.equal(calls.toasts[0].title, '微信凭证已过期，请重试')
    }
  })
}

test('新微信伙伴缺少邀请码时提示填写，重试带邀请码且重新获取微信身份', async () => {
  const { calls, run } = harness({ quickLogin: async (data) => {
    if (!data.inviteCode) throw new Error('请填写上级邀请码')
    return { token: 'new-partner-token' }
  } })
  await run('login()')
  assert.equal(calls.emitted.some(event => event[0] === 'authenticated'), false)
  assert.equal(run('needsInvite.value'), true)
  await run('login()')
  assert.equal(calls.quick.length, 1)
  await run("invite.value = ' abcd2345 '; login()")
  assert.equal(calls.quick.length, 2)
  assert.equal(calls.quick[1].data.inviteCode, 'ABCD2345')
  assert.equal(calls.login.length, 2)
  assert.ok(calls.emitted.some(event => event[0] === 'authenticated'))
})
