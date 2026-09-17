import { createServer } from 'node:http'
import { createPinia, setActivePinia } from 'pinia'
import { afterAll, beforeAll, expect, it } from 'vitest'
import request from '@/utils/request'
import { useProjectStore } from '@/stores/project'
import { fileApi } from '../file'

let server
let received
const originalBaseURL = request.defaults.baseURL
const originalAdapter = request.defaults.adapter
const pdf = '%PDF-1.7\nslow contract scan\n%%EOF'

beforeAll(async () => {
  setActivePinia(createPinia())
  useProjectStore().currentProjectId = 3
  localStorage.setItem('zhyq_token', 'download-test-token')
  server = createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*')
    res.setHeader('Access-Control-Allow-Headers', 'Authorization')
    if (req.method === 'OPTIONS') {
      res.writeHead(204).end()
      return
    }
    received = { url: req.url, authorization: req.headers.authorization }
    res.writeHead(200, { 'Content-Type': 'application/pdf' })
    res.write(pdf.slice(0, 9))
    // A real response that takes longer than the ordinary API's 15-second budget.
    const timer = setTimeout(() => res.end(pdf.slice(9)), 16000)
    res.on('close', () => clearTimeout(timer))
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

it('downloads the complete authenticated attachment when transfer exceeds 15 seconds', async () => {
  const response = await fileApi.download(35)
  const content = await new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = reject
    reader.readAsText(response.data)
  })
  expect(content).toBe(pdf)
  expect(received).toEqual({
    url: '/api/file/download/35?projectId=3',
    authorization: 'Bearer download-test-token'
  })
}, 22000)
