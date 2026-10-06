import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replaceAll('import.meta.env', '{}').replace(/^export /gm, '')
  .replace(/\/\/ #ifdef (H5|MP-WEIXIN)\n([\s\S]*?)\/\/ #endif/g, (_, name, body) => name === 'MP-WEIXIN' ? body : '')

function harness() {
  const storage = new Map([['mp_token', 'mp-old'], ['wh_token', 'wh-old']])
  const calls = { requests: [], routes: [], removed: [], toasts: [], opened: [] }
  const capture = options => calls.requests.push(options)
  const context = vm.createContext({ uni: {
    getStorageSync: key => storage.get(key) || '', setStorageSync: (key, value) => storage.set(key, value),
    removeStorageSync: key => { calls.removed.push(key); storage.delete(key) },
    reLaunch: options => calls.routes.push(options.url), showToast: options => calls.toasts.push(options),
    request: capture, uploadFile: capture, downloadFile: capture,
    getImageInfo: options => options.success({}), previewImage: options => calls.opened.push(options),
    openDocument: options => calls.opened.push(options),
  } })
  vm.runInContext(source, context)
  return { calls, storage, run: script => vm.runInContext(script, context) }
}

for (const role of ['mp', 'wh']) {
  const get = role === 'mp' ? 'get' : 'whGet'
  const tokenKey = `${role}_token`
  const login = role === 'mp' ? '/pages/login/index' : '/pages/warehouse-login/index'
  for (const response of [
    { statusCode: 401, data: { code: 401 } },
    { statusCode: 200, data: { code: 401 } },
    { statusCode: 200, data: { code: 0, data: { name: '上一个账号' } } },
  ]) {
    test(`${role} 新会话不受旧请求 ${response.data.code} 回包影响，也不接收旧账号数据`, async () => {
      const { calls, storage, run } = harness()
      const pending = run(`${get}('/me')`)
      assert.equal(calls.requests[0].header.Authorization, `Bearer ${role}-old`)
      storage.set(tokenKey, `${role}-new`)
      calls.requests[0].success(response)
      await assert.rejects(pending, /登录状态已变化/)
      assert.equal(storage.get(tokenKey), `${role}-new`)
      assert.deepEqual(calls.removed, [])
      assert.deepEqual(calls.routes, [])
      assert.deepEqual(calls.toasts, [])
    })
  }

  test(`${role} 并发失效请求只跳登录一次，并保留另一身份`, async () => {
    const { calls, storage, run } = harness()
    const first = run(`${get}('/me')`), second = run(`${get}('/home')`)
    calls.requests[0].success({ statusCode: 401, data: { code: 401 } })
    calls.requests[1].success({ statusCode: 200, data: { code: 401 } })
    await assert.rejects(first, /请先登录/)
    await assert.rejects(second, /登录状态已变化/)
    assert.deepEqual(calls.routes, [login])
    assert.deepEqual(calls.removed, [tokenKey])
    assert.ok(storage.get(role === 'mp' ? 'wh_token' : 'mp_token'))
  })

  test(`${role} 另一身份会话变化不干扰正常数据请求`, async () => {
    const { calls, storage, run } = harness()
    const pending = run(`${get}('/me')`)
    storage.delete(role === 'mp' ? 'wh_token' : 'mp_token')
    calls.requests[0].success({ statusCode: 200, data: { code: 0, data: 'current-data' } })
    assert.equal(await pending, 'current-data')
  })
}

for (const [invoke, role, response] of [
  ["uploadWarehouseFile('/tmp/agreement.pdf')", 'wh', { statusCode: 401, data: '{"code":401}' }],
  ["uploadWarehouseFile('/tmp/agreement.pdf')", 'wh', { statusCode: 200, data: '{"code":401}' }],
  ["openWarehouseFile(7, 'agreement.pdf')", 'wh', { statusCode: 401 }],
  ['downloadInvitationCode()', 'mp', { statusCode: 401 }],
]) {
  test(`${invoke} 过期会话回到对应登录页`, async () => {
    const { calls, storage, run } = harness()
    const pending = run(invoke)
    calls.requests[0].success(response)
    await assert.rejects(pending, /请先登录/)
    assert.equal(storage.has(`${role}_token`), false)
    assert.deepEqual(calls.routes, [role === 'mp' ? '/pages/login/index' : '/pages/warehouse-login/index'])
  })
}

for (const [invoke, role, response] of [
  ["uploadWarehouseFile('/tmp/agreement.pdf')", 'wh', { statusCode: 200, data: '{"code":0,"data":{"id":7}}' }],
  ["openWarehouseFile(7, 'agreement.pdf')", 'wh', { statusCode: 200, tempFilePath: '/tmp/private.pdf' }],
  ['downloadInvitationCode()', 'mp', { statusCode: 200, tempFilePath: '/tmp/private.png' }],
]) {
  test(`${invoke} 切换账号后丢弃之前的文件回包`, async () => {
    const { calls, storage, run } = harness()
    const pending = run(invoke)
    storage.set(`${role}_token`, 'new-session')
    calls.requests[0].success(response)
    await assert.rejects(pending, /登录状态已变化/)
    assert.deepEqual(calls.opened, [])
    assert.deepEqual(calls.routes, [])
    assert.equal(storage.get(`${role}_token`), 'new-session')
  })
}
