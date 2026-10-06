import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const shareSource = readFileSync(new URL('../src/utils/share.js', import.meta.url), 'utf8')
const { createAppShare } = await import(`data:text/javascript;base64,${Buffer.from(shareSource).toString('base64')}`)

const script = page => readFileSync(new URL(`../src/pages/${page}/index.vue`, import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const pending = () => {
  let resolve, reject
  const promise = new Promise((a, b) => { resolve = a; reject = b })
  return { promise, resolve, reject }
}
function harness(page, api) {
  const events = {}, calls = []
  const context = vm.createContext({
    ref: value => ({ value }), onShow: callback => { events.show = callback },
    onLoad: callback => { events.load = callback }, onUnmounted: callback => { events.unmount = callback },
    onShareAppMessage: callback => { events.share = callback }, createAppShare, getCurrentInstance: () => ({ proxy: {} }),
    token: { get: () => 'current-token' }, meApi: api,
    uni: { reLaunch: options => calls.push(options.url), setClipboardData: options => calls.push(options.data) }
  })
  vm.runInContext(script(page), context)
  return { context, events, calls }
}

test('首页收益加载失败不显示虚假的零余额，重试后回显真实数据', async () => {
  let fail = true
  const { context, events } = harness('home', {
    home: async () => { if (fail) throw new Error('网络超时'); return { total: '123.456', recent: [] } },
    me: async () => ({ positionCode: 'P4', status: 1 })
  })
  await events.show()
  assert.equal(vm.runInContext('home.value', context), null)
  assert.equal(vm.runInContext('fmt(home.value?.withdrawable)', context), '—')
  assert.equal(vm.runInContext('error.value', context), '网络超时')
  assert.equal(vm.runInContext('loading.value', context), false)
  fail = false
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('home.value.total', context), '123.456')
  assert.equal(vm.runInContext('error.value', context), '')
})

test('首页较早的刷新不得覆盖较新的余额，退出页面后忽略未完成请求', async () => {
  const first = pending(), second = pending(), third = pending()
  const requests = [first, second, third]
  const { context, events } = harness('home', {
    home: () => requests.shift().promise, me: async () => ({ positionCode: 'P1' })
  })
  const older = events.show(), newer = events.show()
  second.resolve({ total: '2.000' }); await newer
  first.resolve({ total: '1.000' }); await older
  assert.equal(vm.runInContext('home.value.total', context), '2.000')
  const last = events.show()
  events.unmount()
  third.reject(new Error('已离开页面')); await last
  assert.equal(vm.runInContext('error.value', context), '')
})

test('称号请求失败有可重试状态，刷新期间不保留旧账号称号', async () => {
  let fail = true
  const { context, events } = harness('position', {
    position: async () => { if (fail) throw new Error('称号读取超时'); return { code: 'P2', orders12m: 3 } }
  })
  await events.show()
  assert.equal(vm.runInContext('error.value', context), '称号读取超时')
  assert.equal(vm.runInContext('loading.value', context), false)
  fail = false
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('p.value.code', context), 'P2')
  fail = true
  await events.show()
  assert.equal(vm.runInContext('p.value', context), null)
})

test('邀请码加载失败可重新读取，未加载前分享不会生成 undefined 路径', async () => {
  let fail = true
  const { context, events, calls } = harness('invite', {
    poster: async () => {
      if (fail) throw new Error('邀请码读取超时')
      return { inviteCode: 'ABC123', path: 'pages/login/index?invite=ABC123' }
    }
  })
  assert.equal(events.share().path, '/pages/login/index')
  await events.load()
  assert.equal(vm.runInContext('infoError.value', context), '邀请码读取超时')
  assert.equal(vm.runInContext('infoLoading.value', context), false)
  await vm.runInContext('makePoster()', context)
  vm.runInContext('copy()', context)
  assert.deepEqual(calls, [])
  fail = false
  await vm.runInContext('loadInfo()', context)
  assert.equal(events.share().path, '/pages/login/index?invite=ABC123')
  assert.equal(events.share().title, '邀请你成为园区伙伴')
  assert.equal(events.share().imageUrl, '/static/share/app-card.png')
  assert.equal(vm.runInContext('infoError.value', context), '')
  vm.runInContext('copy()', context)
  assert.deepEqual(calls, ['ABC123'])
})
