import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import request from '../request'
import { useProjectStore } from '@/stores/project'

/**
 * 项目注入拦截器的请求体处理。
 *
 * 由来:拦截器原先只判 `typeof data === 'object'`,而 `typeof [] === 'object'` 为真,
 * 于是数组请求体被 `{ projectId, ...arr }` 摊成 `{ projectId, "0":…, "1":… }`,
 * 后端 `@RequestBody List<T>` 反序列化直接失败:
 *   Cannot deserialize value of type `ArrayList<WfNode>` from Object value
 * 表现为审批流程「保存节点」永远报 500 系统繁忙。
 */
describe('request 拦截器的 projectId 注入', () => {
  /** 拦截器注册顺序即 handlers 顺序,第 0 个就是 token + projectId 那个 */
  const runInterceptor = (config) => request.interceptors.request.handlers[0].fulfilled(config)

  beforeEach(() => {
    setActivePinia(createPinia())
    useProjectStore().currentProjectId = 7
    localStorage.setItem('zhyq_token', 'test-token')
  })

  it('数组请求体原样发出,不被摊平成对象', () => {
    const nodes = [
      { name: '部门负责人审批', approverType: 'user', approverValue: 'lijintang' },
      { name: '财务审批', approverType: 'user', approverValue: 'fangshisi' }
    ]
    const config = runInterceptor({ url: '/workflow/definition/3/nodes', method: 'put', data: nodes, headers: {} })

    expect(Array.isArray(config.data)).toBe(true)
    expect(config.data).toHaveLength(2)
    expect(config.data[0].approverValue).toBe('lijintang')
    expect(config.data).not.toHaveProperty('projectId')
  })

  it('普通对象请求体仍然注入 projectId', () => {
    const config = runInterceptor({ url: '/budget', method: 'post', data: { title: '年度预算' }, headers: {} })

    expect(config.data.projectId).toBe(7)
    expect(config.data.title).toBe('年度预算')
  })

  it('GET 请求注入到 params', () => {
    const config = runInterceptor({ url: '/budget/page', method: 'get', params: { pageNo: 1 }, headers: {} })

    expect(config.params.projectId).toBe(7)
    expect(config.params.pageNo).toBe(1)
  })

  it('FormData 请求体不被改写(文件上传)', () => {
    const fd = new FormData()
    fd.append('file', new Blob(['x']), 'a.txt')
    const config = runInterceptor({ url: '/file/upload', method: 'post', data: fd, headers: {} })

    expect(config.data).toBeInstanceOf(FormData)
  })

  it('鉴权与项目列表接口不注入 projectId', () => {
    const login = runInterceptor({ url: '/auth/login', method: 'post', data: { username: 'admin' }, headers: {} })
    expect(login.data).not.toHaveProperty('projectId')

    const projects = runInterceptor({ url: '/building/project/list', method: 'get', params: {}, headers: {} })
    expect(projects.params).not.toHaveProperty('projectId')
  })

  it('未选中项目时不注入', () => {
    useProjectStore().currentProjectId = null
    const config = runInterceptor({ url: '/budget', method: 'post', data: { title: 'x' }, headers: {} })

    expect(config.data).not.toHaveProperty('projectId')
  })

  it('携带 token', () => {
    const config = runInterceptor({ url: '/budget/page', method: 'get', params: {}, headers: {} })
    expect(config.headers.Authorization).toBe('Bearer test-token')
  })

  it('重新登录时不发送旧的大尺寸凭证，但权限查询仍携带凭证', async () => {
    const oversized = 'old-token'.repeat(1000)
    localStorage.setItem('zhyq_token', oversized)
    const requests = []
    const adapter = async config => {
      requests.push(config)
      return { data: { code: 0, data: {} }, status: 200, statusText: 'OK', headers: {}, config }
    }
    await request.post('/auth/login', { username: 'login-test', password: 'test-only' }, {
      adapter, headers: { Authorization: `Bearer ${oversized}` }
    })
    expect(requests[0].headers.get('Authorization')).toBeUndefined()
    expect(JSON.parse(requests[0].data)).toEqual({ username: 'login-test', password: 'test-only' })
    await request.get('/auth/my-access', { adapter })
    expect(requests[1].headers.get('Authorization')).toBe(`Bearer ${oversized}`)
  })
})
