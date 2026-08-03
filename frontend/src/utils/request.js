import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useProjectStore } from '@/stores/project'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 这些前缀不注入 projectId:登录鉴权、项目列表接口自身(否则会被自己过滤)
const NO_PROJECT_PREFIXES = ['/auth', '/building/project']

// 请求带 token + 按当前项目自动注入 projectId
request.interceptors.request.use((config) => {
  const token = localStorage.getItem('zhyq_token')
  if (token) config.headers.Authorization = `Bearer ${token}`

  const url = config.url || ''
  const excluded = NO_PROJECT_PREFIXES.some((p) => url.startsWith(p))
  if (!excluded) {
    const pid = useProjectStore().currentProjectId
    if (pid != null) {
      const method = (config.method || 'get').toLowerCase()
      if (method === 'get') {
        config.params = { projectId: pid, ...config.params }
      } else if (
        config.data &&
        typeof config.data === 'object' &&
        !(config.data instanceof FormData)
      ) {
        config.data = { projectId: pid, ...config.data }
      }
    }
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const data = res.data
    // 二进制流直接返回
    if (res.config.responseType === 'blob') return res
    if (data.code === 0) return data.data
    if (data.code === 401) {
      localStorage.removeItem('zhyq_token')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
      return Promise.reject(new Error('未登录'))
    }
    ElMessage.error(data.message || '请求失败')
    return Promise.reject(new Error(data.message))
  },
  (err) => {
    // HTTP 401(拦截器直接拒的情况)
    if (err.response && err.response.status === 401) {
      localStorage.removeItem('zhyq_token')
      if (!location.pathname.startsWith('/login')) location.href = '/login'
      return Promise.reject(err)
    }
    ElMessage.error(err.message || '网络异常')
    return Promise.reject(err)
  }
)

export default request
