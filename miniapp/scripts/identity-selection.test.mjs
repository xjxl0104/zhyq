import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const script = page => readFileSync(new URL(`../src/pages/${page}/index.vue`, import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function entry({ mp = true, wh = true } = {}) {
  const routes = []
  let load
  const context = vm.createContext({
    onLoad: callback => { load = callback },
    token: { get: () => mp ? 'partner-session' : '' }, warehouseToken: { get: () => wh ? 'warehouse-session' : '' },
    uni: { switchTab: options => routes.push(['tab', options.url]), reLaunch: options => routes.push(['launch', options.url]) },
  })
  vm.runInContext(script('entry'), context)
  return { routes, load, run: value => vm.runInContext(value, context) }
}

test('显式切换身份时即使两端都有会话也停留在选择页，启动时仍自动进入工作台', () => {
  const select = entry()
  select.load({ select: '1' })
  assert.deepEqual(select.routes, [])
  const launch = entry()
  launch.load({})
  assert.deepEqual(launch.routes, [['tab', '/pages/home/index']])
})

for (const loggedIn of [false, true]) {
  test(`选择身份后进入${loggedIn ? '已有会话的工作台' : '相应登录页'}`, () => {
    const { routes, run } = entry({ mp: loggedIn, wh: loggedIn })
    run('goPartner(); goWarehouse()')
    assert.deepEqual(routes, loggedIn
      ? [['tab', '/pages/home/index'], ['launch', '/pages/warehouse-dashboard/index']]
      : [['launch', '/pages/login/index'], ['launch', '/pages/warehouse-login/index']])
  })
}

for (const page of ['login', 'warehouse-login']) {
  test(`${page} 登录期间禁用身份、登录方式、体验页切换；空闲时显示身份选择页`, () => {
    const routes = []
    const context = vm.createContext({
      ref: value => ({ value }), onLoad: () => {}, mock: false, token: {}, warehouseToken: {},
      uni: { reLaunch: options => routes.push(options.url), navigateTo: options => routes.push(options.url) },
    })
    vm.runInContext(script(page), context)
    vm.runInContext("method.value='password';busy.value=true;choose('wechat');chooseIdentity();goDemo()", context)
    assert.equal(vm.runInContext('method.value', context), 'password')
    assert.deepEqual(routes, [])
    vm.runInContext('busy.value=false;chooseIdentity()', context)
    assert.deepEqual(routes, ['/pages/entry/index?select=1'])
    const template = readFileSync(new URL(`../src/pages/${page}/index.vue`, import.meta.url), 'utf8').split('<script setup>')[0]
    assert.match(template, /<PasswordAuthForm[^>]+@busy="busy = \$event"/)
  })
}
