import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import * as intake from '../src/utils/referral-intake.mjs'

const TOKEN = '0123456789abcdef0123456789abcdef'
const source = page => readFileSync(new URL(`../src/pages/${page}/index.vue`, import.meta.url), 'utf8')
  .match(/<script setup>([\s\S]*?)<\/script\s*>/)[1].replace(/^import .*$/gm, '')

function pageContext(page, api) {
  let onLoad
  const ctx = vm.createContext({
    ...intake, ref: value => ({ value }), reactive: value => value,
    onLoad: fn => { onLoad = fn }, onUnload() {}, onShareAppMessage() {},
    ...(page === 'referral' ? { bizApi: api } : { publicReferralApi: api }),
    uni: { showModal() {}, showToast() {}, pageScrollTo() {}, switchTab() {} }
  })
  vm.runInContext(source(page), ctx)
  return { ctx, load: query => onLoad(query), run: code => vm.runInContext(code, ctx) }
}

test('direct referral cannot submit twice while saving or after confirmed success', async () => {
  let finish, calls = 0
  const page = pageContext('referral', { referral: () => {
    calls++
    return new Promise(resolve => { finish = resolve })
  } })
  const pending = page.run("submit({name:'示例客户'})")
  await page.run("submit({name:'示例客户'})")
  assert.equal(calls, 1)
  finish({ lockUntil: '2027-02-22' })
  await pending
  assert.equal(page.run('submitted.value'), true)
  assert.equal(page.run('loading.value'), false)
  await page.run("submit({name:'示例客户'})")
  assert.equal(calls, 1)
})

test('failed direct referral remains editable and can be resubmitted', async () => {
  let calls = 0
  const page = pageContext('referral', { referral: async () => {
    if (++calls === 1) throw new Error('请修正客户信息')
    return {}
  } })
  await page.run("submit({name:'示例客户'})")
  assert.equal(page.run('submitted.value'), false)
  assert.equal(page.run('loading.value'), false)
  await page.run("submit({name:'已修正客户'})")
  assert.equal(calls, 2)
  assert.equal(page.run('submitted.value'), true)
})

test('public intake recovers from a definite validation rejection without losing entered data', async () => {
  const sent = []
  const page = pageContext('customer-intake', {
    info: async () => ({ partnerName: '示例伙伴' }),
    submit: async (_, body) => {
      sent.push(JSON.parse(JSON.stringify(body)))
      if (sent.length === 1) throw Object.assign(new Error('不能自我推荐'), { code: 400 })
    }
  })
  await page.load({ token: TOKEN })
  page.run("form.name='示例客户'; form.phone='13800138000'")
  await page.run('submit(referralPayload(form))')
  assert.equal(page.run('pendingBody.value'), null)
  assert.equal(page.run('form.name'), '示例客户')
  assert.equal(page.run('form.phone'), '13800138000')
  assert.equal(page.run('ready.value'), true)
  assert.equal(page.run('submitting.value'), false)
  page.run("form.phone=''")
  await page.run('submit(referralPayload(form))')
  assert.equal(sent.length, 2)
  assert.equal(sent[1].phone, null)
  assert.equal(sent[1].submissionId, sent[0].submissionId)
  assert.equal(page.run('submitted.value'), true)
})

test('public intake preserves uncertain submissions across timeout and server failures', async () => {
  for (const code of [undefined, 408, 500, 503]) {
    const page = pageContext('customer-intake', {
      info: async () => ({ partnerName: '示例伙伴' }),
      submit: async () => { throw Object.assign(new Error('暂未收到结果'), { code }) }
    })
    await page.load({ token: TOKEN })
    await page.run("submit({name:'原始客户'})")
    assert.equal(page.run('pendingBody.value.name'), '原始客户')
    assert.equal(page.run('submitting.value'), false)
    assert.equal(page.run('submitted.value'), false)
  }
})

test('public entry load can be retried after failure and invalid links never submit', async () => {
  let reads = 0, writes = 0
  const page = pageContext('customer-intake', {
    info: async () => {
      if (++reads === 1) throw new Error('网络超时')
      return { partnerName: '示例伙伴' }
    },
    submit: async () => { writes++ }
  })
  await page.load({ scene: '%invalid' })
  await page.run("submit({name:'示例客户'})")
  assert.equal(reads, 0)
  assert.equal(writes, 0)
  assert.equal(page.run('ready.value'), false)
  await page.load({ token: TOKEN })
  assert.equal(page.run('ready.value'), false)
  assert.equal(page.run('initialLoading.value'), false)
  await page.run('load()')
  assert.equal(reads, 2)
  assert.equal(page.run('ready.value'), true)
  assert.equal(page.run('error.value'), '')
})
