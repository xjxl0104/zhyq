import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const root = new URL('../', import.meta.url)
const read = file => readFileSync(new URL(file, root), 'utf8')
const demo = read('src/pages/demo/index.vue')
const pages = read('src/pages.json')

test('临时体验入口注册独立页面并可从身份选择与登录页进入', () => {
  assert.match(pages, /pages\/demo\/index/)
  for (const page of ['src/pages/entry/index.vue', 'src/pages/login/index.vue', 'src/pages/warehouse-login/index.vue']) {
    assert.match(read(page), /pages\/demo\/index/)
  }
})

test('体验页只使用本地演示数据，不创建 token 或调用业务接口', () => {
  assert.doesNotMatch(demo, /@\/api|uni\.request|uni\.uploadFile|setStorageSync|mp_token|wh_token|fetch\(/)
  assert.match(demo, /不会访问或修改真实账号、客户、订单与收益/)
  assert.match(demo, /当前为体验模式，不会提交数据/)
})
