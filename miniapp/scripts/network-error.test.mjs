import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { createNetworkError } from '../src/utils/network-error.mjs'

for (const [raw, code] of [
  ['request:fail url not in domain list', 'NETWORK_DOMAIN'],
  ['uploadFile:fail 域名不在合法域名列表中', 'NETWORK_DOMAIN'],
  ['downloadFile:fail net::ERR_CERT_AUTHORITY_INVALID', 'NETWORK_TLS'],
  ['request:fail SSL handshake failed', 'NETWORK_TLS'],
  ['request:fail timeout', 'NETWORK_TIMEOUT'],
  ['request:fail net::ERR_INTERNET_DISCONNECTED', 'NETWORK_OFFLINE'],
  ['request:fail net::ERR_CONNECTION_REFUSED', 'NETWORK_CONNECTION'],
  ['Failed to fetch', 'NETWORK_CONNECTION'],
  ['request:fail unknown failure 913', 'NETWORK_UNKNOWN']
]) {
  test(`preserves and classifies ${code}: ${raw}`, () => {
    const cause = { errMsg: raw, errno: 913 }
    const error = createNetworkError(cause)
    assert.equal(error.code, code)
    assert.equal(error.errMsg, raw)
    assert.equal(error.cause, cause)
    assert.equal(error.name, 'NetworkError')
    assert.notEqual(error.message, raw)
  })
}

// Load the actual request adapter with only build-time constants / platform blocks replaced.
const source = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
async function adapter(platform) {
  const transformed = source
    .replace("'./network-error.mjs'", JSON.stringify(new URL('../src/utils/network-error.mjs', import.meta.url).href))
    .replaceAll('import.meta.env', '{}')
    .replace(/\/\/ #ifdef (H5|MP-WEIXIN)\n([\s\S]*?)\/\/ #endif/g, (_, name, body) => name === platform ? body : '')
  return import(`data:text/javascript;base64,${Buffer.from(transformed).toString('base64')}`)
}
const mp = await adapter('MP-WEIXIN')
const h5 = await adapter('H5')
const runtime = (overrides = {}) => ({ getStorageSync: () => 'test-token', showToast: () => {}, ...overrides })

test('request exposes diagnostic cause and shows only the friendly message', async () => {
  const cause = { errMsg: 'request:fail url not in domain list' }
  let toast
  globalThis.uni = runtime({ request: options => options.fail(cause), showToast: options => { toast = options } })
  await assert.rejects(mp.post('/auth/password-register', {}), error => {
    assert.equal(error.cause, cause)
    assert.equal(error.code, 'NETWORK_DOMAIN')
    assert.equal(toast.title, error.message)
    assert.ok(!toast.title.includes('test-token'))
    return true
  })
})

test('server business rejection remains a business error, including public login 401', async () => {
  for (const statusCode of [400, 401, 500]) {
    globalThis.uni = runtime({ request: options => options.success({ statusCode, data: { code: statusCode, message: '账号或密码错误' } }) })
    await assert.rejects(mp.post('/auth/password-login', {}), error => {
      assert.equal(error.message, '账号或密码错误')
      assert.equal(error.code, undefined)
      assert.equal(error.cause, undefined)
      return true
    })
  }
})

test('upload and both WeChat download entry points retain the transport failure', async () => {
  const cause = { errMsg: 'downloadFile:fail SSL handshake failed' }
  globalThis.uni = runtime({ uploadFile: o => o.fail(cause), downloadFile: o => o.fail(cause) })
  for (const invoke of [() => mp.uploadWarehouseFile('/tmp/file.pdf'), () => mp.openWarehouseFile(1), () => mp.downloadInvitationCode()]) {
    await assert.rejects(invoke(), error => error.code === 'NETWORK_TLS' && error.cause === cause)
  }
})

test('upload and download HTTP failures are not labelled network failures', async () => {
  globalThis.uni = runtime({
    uploadFile: o => o.success({ statusCode: 403, data: JSON.stringify({ code: 403, message: '无权访问' }) }),
    downloadFile: o => o.success({ statusCode: 403 })
  })
  for (const invoke of [() => mp.uploadWarehouseFile('/tmp/file.pdf'), () => mp.openWarehouseFile(1), () => mp.downloadInvitationCode()]) {
    await assert.rejects(invoke(), error => error.name === 'Error' && error.code === undefined)
  }
})

test('H5 fetch rejections use the same diagnostics; HTTP rejection keeps the business message', async () => {
  globalThis.uni = runtime()
  const cause = new TypeError('Failed to fetch')
  const originalFetch = globalThis.fetch
  try {
    globalThis.fetch = async () => { throw cause }
    for (const invoke of [() => h5.openWarehouseFile(1), () => h5.downloadInvitationCode()]) {
      await assert.rejects(invoke(), error => error.code === 'NETWORK_CONNECTION' && error.cause === cause)
    }
    globalThis.fetch = async () => ({ ok: false, json: async () => ({ message: '尚未发布' }) })
    await assert.rejects(h5.downloadInvitationCode(), error => error.message === '尚未发布' && error.code === undefined)
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('release checker rejects disabled or missing source / output domain verification', () => {
  const directory = mkdtempSync(join(tmpdir(), 'mp-domain-check-'))
  const checker = fileURLToPath(new URL('./check-wechat-build.mjs', import.meta.url))
  mkdirSync(join(directory, 'src'), { recursive: true })
  mkdirSync(join(directory, 'dist/build/mp-weixin'), { recursive: true })
  try {
    for (const [sourceCheck, outputCheck, expected] of [[true, true, 0], [false, true, 1], [true, false, 1], [true, undefined, 1]]) {
      writeFileSync(join(directory, 'src/manifest.json'), JSON.stringify({ 'mp-weixin': { appid: 'test-app', setting: { urlCheck: sourceCheck } } }))
      writeFileSync(join(directory, 'dist/build/mp-weixin/project.config.json'), JSON.stringify({ appid: 'test-app', setting: { urlCheck: outputCheck } }))
      const result = spawnSync(process.execPath, [checker], { cwd: directory, encoding: 'utf8' })
      assert.equal(result.status, expected, result.stderr)
    }
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
})
