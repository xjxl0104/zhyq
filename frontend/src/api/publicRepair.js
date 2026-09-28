import axios from 'axios'

// 手机自助报修是免登录入口，不能走 request.js —— 那里会注入 token/projectId，
// 401 还会把用户踢到登录页。这里单独起一个不带拦截器的实例。
const http = axios.create({ baseURL: '/api', timeout: 15000 })

function unwrap(res) {
  const data = res.data
  if (data && data.code === 0) return data.data
  throw new Error((data && data.message) || '请求失败')
}

export const repairApi = {
  submit: (data) => http.post('/public/repair', data).then(unwrap),
  uploadPhoto(file) {
    const fd = new FormData()
    fd.append('file', file)
    // 手机原图较大，单独放宽超时
    return http.post('/public/repair/photo', fd, { timeout: 60000 }).then(unwrap)
  },
  my: (phone) => http.get('/public/repair/my', { params: { phone } }).then(unwrap)
}
