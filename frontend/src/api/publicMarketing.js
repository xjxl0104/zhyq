import axios from 'axios'

// 公开获客与货主扫码验店为免登录 H5/Web 入口，独立 axios 实例避免注入后台 token / projectId 或触发 401 踢登录页
const publicHttp = axios.create({ baseURL: '/api', timeout: 15000 })

function unwrap(res) {
  const data = res?.data
  if (data && data.code === 0) return data.data
  throw new Error((data && data.message) || '请求失败')
}

export const mktPublicOpenApi = {
  calcEstimate: (data) => publicHttp.post('/open/public-calc/estimate', data).then(unwrap),
  calcSubmitLead: (data) => publicHttp.post('/open/public-calc/lead', data).then(unwrap),
  shopVerifyDetail: (token) => publicHttp.get(`/open/shop-verify/${encodeURIComponent(token)}`).then(unwrap),
  shopVerifySubmit: (token, data) => publicHttp.post(`/open/shop-verify/${encodeURIComponent(token)}/submit`, data).then(unwrap)
}
