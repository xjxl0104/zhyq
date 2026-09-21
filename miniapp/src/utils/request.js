// 统一请求:带 token、解 Result 包、401 跳登录。伙伴端 /api/mp/v1/**，云仓端 /api/wh/v1/**。
const BASE = import.meta.env.VITE_API_BASE || '/api/mp/v1'
const WH_BASE = import.meta.env.VITE_WH_API_BASE || '/api/wh/v1'

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

function requestWith(base, auth, loginPath, method, url, data = {}) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: base + url,
      method,
      data,
      header: { 'Content-Type': 'application/json', Authorization: auth.get() ? 'Bearer ' + auth.get() : '' },
      success: ({ statusCode, data: body }) => {
        if (statusCode === 401) {
          auth.clear()
          uni.reLaunch({ url: loginPath })
          return reject(new Error('请先登录'))
        }
        if (body && body.code === 0) return resolve(body.data)
        const msg = (body && body.message) || `请求失败 ${statusCode}`
        uni.showToast({ title: msg, icon: 'none', duration: 2500 })
        reject(new Error(msg))
      },
      fail: (e) => {
        uni.showToast({ title: '网络异常', icon: 'none' })
        reject(e)
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
