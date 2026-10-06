import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'

// Exercise the bundled uni-app runtime, not a source-text approximation of its
// compiler flags: imported composables alone can leave native sharing disabled.
const root = fileURLToPath(new URL('../dist/build/mp-weixin/', import.meta.url))
const pages = JSON.parse(readFileSync(resolve(root, 'app.json'), 'utf8')).pages
const registered = []
let app
const storage = new Map()
const wx = {
  getSystemInfoSync: () => ({ language: 'zh_CN', platform: 'devtools', SDKVersion: '3.0.0', windowWidth: 375, pixelRatio: 2 }),
  getLaunchOptionsSync: () => ({ path: pages[0], query: {} }),
  getAccountInfoSync: () => ({ miniProgram: { envVersion: 'release' } }),
  canIUse: () => true,
  getStorageSync: key => storage.get(key) || '',
  setStorageSync: (key, value) => storage.set(key, value),
  removeStorageSync: key => storage.delete(key),
  nextTick: callback => queueMicrotask(callback),
}
const context = vm.createContext({
  console, setTimeout, clearTimeout, setInterval, clearInterval, queueMicrotask,
  wx,
  App: options => { app = options; return options },
  Page: options => { registered.push(options); return options },
  Component: options => { registered.push(options); return options },
  Behavior: options => options,
  getApp: () => app,
  getCurrentPages: () => [],
})
context.global = context
const cache = new Map()
function load(filename) {
  filename = resolve(filename)
  if (cache.has(filename)) return cache.get(filename).exports
  const module = { exports: {} }
  cache.set(filename, module)
  const execute = vm.runInContext(`(function(require, module, exports) {\n${readFileSync(filename, 'utf8')}\n})`, context, { filename })
  execute(request => {
    assert.ok(request.startsWith('.'), `Unexpected external require: ${request}`)
    return load(resolve(dirname(filename), request))
  }, module, module.exports)
  return module.exports
}

load(resolve(root, 'app.js'))
assert.ok(app?.$vm, 'Bundled app did not mount')
app.onLaunch?.call(app, { path: pages[0], query: {} })
const defaultShare = {
  title: '数智云仓全民营销助手',
  path: '/pages/entry/index',
  imageUrl: '/static/share/app-card.png',
}
assert.ok(existsSync(resolve(root, defaultShare.imageUrl.slice(1))), 'Share image missing from build')
for (const page of pages) {
  const before = registered.length
  load(resolve(root, `${page}.js`))
  assert.equal(registered.length, before + 1, `${page}: native page not registered`)
  const options = registered.at(-1)
  assert.equal(typeof options.methods?.onShareAppMessage, 'function', `${page}: native share hook missing (menu would be disabled)`)
  const properties = Object.fromEntries(Object.entries(options.properties || {}).map(([key, value]) => [key, value.value]))
  const instance = {
    route: page, properties, data: { ...options.data },
    triggerEvent() {},
    setData(data, callback) { Object.assign(this.data, data); callback?.() },
    selectAllComponents: () => [],
    selectComponent: () => null,
    getOpenerEventChannel: () => ({}),
  }
  Object.assign(instance, options.methods)
  options.created?.call(instance)
  options.lifetimes.attached.call(instance)
  const result = instance.onShareAppMessage({ from: 'menu' })
  const plain = JSON.parse(JSON.stringify(result))
  if (page === 'pages/invite/index') {
    assert.equal(plain.title, '邀请你成为园区伙伴', 'Invite share was overridden by generic share')
    assert.equal(plain.path, '/pages/login/index', 'Invite fallback changed')
    assert.equal(plain.imageUrl, defaultShare.imageUrl, 'Invite must use the public share image')
    const { meApi } = load(resolve(root, 'api/index.js'))
    const poster = meApi.poster
    try {
      for (const path of ['pages/login/index?invite=TEST1234', '/pages/login/index?invite=TEST1234']) {
        meApi.poster = async () => ({ inviteCode: 'TEST1234', path })
        await instance.onLoad({})
        const invitation = instance.onShareAppMessage({ from: 'button' })
        assert.equal(invitation.path, '/pages/login/index?invite=TEST1234', 'Invite share must retain its invitation code')
        assert.equal(invitation.imageUrl, defaultShare.imageUrl)
      }
    } finally {
      meApi.poster = poster
    }
  } else {
    assert.deepEqual(plain, defaultShare, `${page}: generic share must use public entry and image`)
  }
  options.lifetimes.detached?.call(instance)
}
console.log(`微信分享构建已核验：${pages.length} 个页面均注册原生转发回调，默认入口和邀请页分享均正确。`)
