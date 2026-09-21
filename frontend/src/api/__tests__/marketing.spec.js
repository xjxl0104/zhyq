import { createServer } from 'node:http'
import { createPinia, setActivePinia } from 'pinia'
import { afterAll, beforeAll, expect, it } from 'vitest'
import request from '@/utils/request'
import { useProjectStore } from '@/stores/project'
import { mktCommissionApi, mktOrderApi } from '../marketing'

let server
let received
const originalBaseURL = request.defaults.baseURL
const originalAdapter = request.defaults.adapter

beforeAll(async () => {
  setActivePinia(createPinia())
  useProjectStore().currentProjectId = 7
  localStorage.setItem('zhyq_token', 'mkt-test-token')
  server = createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type')
    if (req.method === 'OPTIONS') {
      res.writeHead(204).end()
      return
    }
    let body = ''
    req.on('data', chunk => { body += chunk })
    req.on('end', () => {
      received = { method: req.method, url: req.url, authorization: req.headers.authorization, body }
      if (req.url.endsWith('/import-template')) {
        res.writeHead(200, { 'Content-Type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
        res.end('PK-fake-xlsx')
        return
      }
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(JSON.stringify({ code: 0, data: 'SB20260921001' }))
    })
  })
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
  request.defaults.baseURL = `http://127.0.0.1:${server.address().port}/api`
  request.defaults.adapter = 'xhr'
})

afterAll(async () => {
  request.defaults.baseURL = originalBaseURL
  request.defaults.adapter = originalAdapter
  localStorage.removeItem('zhyq_token')
  server.closeAllConnections()
  await new Promise(resolve => server.close(resolve))
})

// 后端 settle 用 Map<String, List<Long>> 接;拦截器给对象体注入的 projectId 是数字,
// Jackson 反序列化成 List 会 400。这里钉住:请求体里只能有 ids。
it('settle sends only ids and does not get projectId injected into the body', async () => {
  await expect(mktCommissionApi.settle([11, 12])).resolves.toBe('SB20260921001')
  expect(received.method).toBe('POST')
  expect(received.url).toBe('/api/crm/marketing/commission/settle')
  expect(JSON.parse(received.body)).toEqual({ ids: [11, 12] })
})

// 模板接口有 @PreAuthorize,裸 <a href> 不带 token 会 401;必须走 axios 带 Authorization 拿 blob。
it('template download goes through axios with the login token and returns a blob', async () => {
  const res = await mktOrderApi.template()
  expect(received.authorization).toBe('Bearer mkt-test-token')
  expect(received.url).toBe('/api/crm/marketing/order/import-template?projectId=7')
  expect(res.data).toBeInstanceOf(Blob)
})
