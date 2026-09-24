import { createServer } from 'node:http'
import { createPinia, setActivePinia } from 'pinia'
import { afterAll, beforeAll, expect, it } from 'vitest'
import request from '@/utils/request'
import { useProjectStore } from '@/stores/project'
import { mktCommissionApi, mktOrderApi, mktContractApi, mktCustomerApi } from '../marketing'

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


it('marketing restore and amendment send the actual business request bodies', async () => {
  await mktCustomerApi.restore(42, { reason: '重新沟通' })
  expect(received.url).toBe('/api/crm/marketing/customer/42/restore')
  expect(JSON.parse(received.body)).toMatchObject({ reason: '重新沟通' })
  const terms = { priceTable: '{"perOrder":3}', endDate: '2027-12-31', payCycle: 3, files: '[{"id":18}]', remark: '新约定', effectiveDate: '2026-10-01' }
  await mktContractApi.amendDone(77, terms)
  expect(received.url).toBe('/api/crm/marketing/contract/77/amend-done')
  expect(JSON.parse(received.body)).toMatchObject(terms)
  await mktContractApi.amendCancel(77, { reason: '暂缓变更' })
  expect(received.url).toBe('/api/crm/marketing/contract/77/amend-cancel')
  expect(JSON.parse(received.body)).toMatchObject({ reason: '暂缓变更' })
})
