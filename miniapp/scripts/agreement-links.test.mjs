import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')

test('登录与注册表单中的协议和隐私协议可点击打开', () => {
  for (const file of ['../src/components/WechatAuthForm.vue', '../src/components/PasswordAuthForm.vue']) {
    const source = read(file)
    assert.match(source, /@tap\.stop="openAgreement\(warehouse \? 'warehouse' : 'partner'\)"/, file)
    assert.match(source, /@tap\.stop="openPrivacy"[^>]*>《隐私协议》/, file)
    assert.match(source, /import \{ openAgreement, openPrivacy \} from '@\/utils\/agreement'/, file)
  }
})

test('协议页已注册并包含三类协议内容', () => {
  const pages = JSON.parse(read('../src/pages.json')).pages.map(page => page.path)
  assert.ok(pages.includes('pages/agreement/index'))
  const page = read('../src/pages/agreement/index.vue')
  for (const title of ['园区伙伴协议', '云仓入驻协议', '隐私协议']) assert.ok(page.includes(`title: '${title}'`), title)
  const helper = read('../src/utils/agreement.js')
  assert.match(helper, /url: `\/pages\/agreement\/index\?type=\$\{type\}`/)
  assert.match(helper, /openPrivacyContract\(\{ fail: \(\) => openAgreement\('privacy'\) \}\)/)
})
