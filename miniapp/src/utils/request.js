// 统一请求:带 token、解 Result 包、401 跳登录。伙伴端 /api/mp/v1/**，云仓端 /api/wh/v1/**。
import { createNetworkError } from './network-error.mjs'
const injectedBase = typeof __ZHYQ_MP_API_BASE__ === 'string' ? __ZHYQ_MP_API_BASE__ : ''
const injectedWhBase = typeof __ZHYQ_WH_API_BASE__ === 'string' ? __ZHYQ_WH_API_BASE__ : ''
const BASE = injectedBase || import.meta.env.VITE_API_BASE || '/api/mp/v1'
const WH_BASE = injectedWhBase || import.meta.env.VITE_WH_API_BASE || '/api/wh/v1'

export const token = {
  get: () => uni.getStorageSync('mp_token') || '',
  set: (t) => uni.setStorageSync('mp_token', t),
  clear: () => uni.removeStorageSync('mp_token')
}

export const warehouseToken = {
  get: () => uni.getStorageSync('wh_token') || '',
  set: (t) => uni.setStorageSync('wh_token', t),
  clear: () => uni.removeStorageSync('wh_token')
}

function sessionError(auth, sentToken, loginPath, statusCode, bodyCode) {
  // A late response from the previous login must not clear or expose the next account's session.
  if (auth.get() !== sentToken) return new Error('登录状态已变化，请重新打开页面')
  if (statusCode === 401 || bodyCode === 401) {
    auth.clear()
    uni.reLaunch({ url: loginPath })
    return new Error('请先登录')
  }
  return null
}

function requestWith(base, auth, loginPath, method, url, data = {}) {
  const sentToken = auth.get()
  const publicAuth = /^\/auth\/(quick-login|wx-login|bind-phone|password-login|password-register)$/.test(url)
  return new Promise((resolve, reject) => {
    uni.request({
      url: base + url,
      method,
      data,
      header: { 'Content-Type': 'application/json', Authorization: sentToken ? 'Bearer ' + sentToken : '' },
      success: ({ statusCode, data: body }) => {
        const authError = !publicAuth && sessionError(auth, sentToken, loginPath, statusCode, body?.code)
        if (authError) return reject(authError)
        if (body && body.code === 0) return resolve(body.data)
        const msg = (body && body.message) || `请求失败 ${statusCode}`
        uni.showToast({ title: msg, icon: 'none', duration: 2500 })
        reject(new Error(msg))
      },
      fail: (e) => {
        const error = createNetworkError(e)
        uni.showToast({ title: error.message, icon: 'none', duration: 2500 })
        reject(error)
      }
    })
  })
}
export function request(method, url, data = {}) {
  return requestWith(BASE, token, '/pages/login/index', method, url, data)
}
export function warehouseRequest(method, url, data = {}) {
  return requestWith(WH_BASE, warehouseToken, '/pages/warehouse-login/index', method, url, data)
}
export const get = (url, params) => request('GET', url, params)
export const post = (url, data) => request('POST', url, data)
export const put = (url, data) => request('PUT', url, data)
export const whGet = (url, params) => warehouseRequest('GET', url, params)
export const whPost = (url, data) => warehouseRequest('POST', url, data)
export const whPut = (url, data) => warehouseRequest('PUT', url, data)

export function uploadWarehouseFile(filePath, name) {
  const sentToken = warehouseToken.get()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: WH_BASE + '/files/upload', filePath, name: 'file',
      header: { Authorization: 'Bearer ' + sentToken },
      success: ({ statusCode, data }) => {
        let body
        try { body = typeof data === 'string' ? JSON.parse(data) : data } catch (_) { /* handled below */ }
        const authError = sessionError(warehouseToken, sentToken, '/pages/warehouse-login/index', statusCode, body?.code)
        if (authError) return reject(authError)
        if (statusCode === 200 && body?.code === 0) return resolve(body.data)
        const message = body?.message || '文件上传失败，请重试'
        uni.showToast({ title: message, icon: 'none' }); reject(new Error(message))
      },
      fail: e => reject(createNetworkError(e))
    })
  })
}

export async function openWarehouseFile(id, name = '附件') {
  const sentToken = warehouseToken.get()
  // #ifdef H5
  const response = await fetch(WH_BASE + '/files/' + id, { headers: { Authorization: 'Bearer ' + sentToken } }).catch(e => { throw createNetworkError(e) })
  const authError = sessionError(warehouseToken, sentToken, '/pages/warehouse-login/index', response.status)
  if (authError) throw authError
  if (!response.ok || response.headers.get('content-type')?.includes('application/json')) throw new Error('附件读取失败或无权访问')
  const url = URL.createObjectURL(await response.blob())
  const link = document.createElement('a'); link.href = url; link.download = name; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return
  // #endif
  // #ifdef MP-WEIXIN
  const result = await new Promise((resolve, reject) => uni.downloadFile({
    url: WH_BASE + '/files/' + id,
    header: { Authorization: 'Bearer ' + sentToken },
    success: r => {
      const authError = sessionError(warehouseToken, sentToken, '/pages/warehouse-login/index', r.statusCode)
      if (authError) return reject(authError)
      r.statusCode === 200 ? resolve(r) : reject(new Error('附件读取失败或无权访问'))
    },
    fail: e => reject(createNetworkError(e))
  }))
  const ext = name.split('.').pop().toLowerCase()
  if (['jpg', 'jpeg', 'png'].includes(ext)) {
    await uni.previewImage({ urls: [result.tempFilePath] })
  } else {
    await uni.openDocument({ filePath: result.tempFilePath, fileType: ext, showMenu: true })
  }
  // #endif
}

export async function downloadInvitationCode(env = 'release') {
  const sentToken = token.get()
  // #ifdef H5
  const res = await fetch(BASE + '/poster/code?env=' + env, { headers: { Authorization: 'Bearer ' + sentToken } }).catch(e => { throw createNetworkError(e) })
  const authError = sessionError(token, sentToken, '/pages/login/index', res.status)
  if (authError) throw authError
  if (!res.ok || !res.headers.get('content-type')?.startsWith('image/')) {
    let body; try { body = await res.json() } catch (_) { /* use fallback */ }
    const bodyAuthError = sessionError(token, sentToken, '/pages/login/index', res.status, body?.code)
    if (bodyAuthError) throw bodyAuthError
    throw new Error(body?.message || '小程序码生成失败，可先复制邀请码')
  }
  return URL.createObjectURL(await res.blob())
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => uni.downloadFile({
    url: BASE + '/poster/code?env=' + env, header: { Authorization: 'Bearer ' + sentToken },
    success: r => {
      const authError = sessionError(token, sentToken, '/pages/login/index', r.statusCode)
      if (authError) return reject(authError)
      if (r.statusCode !== 200) return reject(new Error('小程序码生成失败，可先分享卡片或复制邀请码'))
      uni.getImageInfo({src:r.tempFilePath,success:()=>resolve(r.tempFilePath),fail:()=>reject(new Error('小程序码尚不可用，请检查小程序发布状态；可先复制邀请码'))})
    }, fail: e => reject(createNetworkError(e))
  }))
  // #endif
}
