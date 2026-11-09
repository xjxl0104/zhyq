import test from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import { readFileSync } from 'node:fs'
import { newReferralForm, referralPayload, parseReferralToken, referralShare, requirementRows, newSubmissionId } from '../src/utils/referral-intake.mjs'
const TOKEN = '0123456789abcdef0123456789abcdef'
const form = () => ({ ...newReferralForm(), name: '测试客户' })
test('new fields are optional; invoice is not silently false', () => {
  const r = referralPayload(form())
  assert.equal(r.requirements.invoiceRequired, null)
  assert.deepEqual(r.requirements.productWeights, [])
})
test('other options require text and stale hidden text is dropped', () => {
  const f = form(); f.requirements.salesPlatforms = ['其他']
  assert.throws(() => referralPayload(f), /其他销售平台/)
  f.requirements.salesPlatformOther = ' 自建商城 '
  assert.equal(referralPayload(f).requirements.salesPlatformOther, '自建商城')
  f.requirements.salesPlatforms = ['京东']; f.requirements.couriers = ['其他']
  assert.throws(() => referralPayload(f), /其他快递/)
  f.requirements.courierOther = ' 德邦 '
  const payload = referralPayload(f)
  assert.equal(payload.requirements.salesPlatformOther, '')
  assert.equal(payload.requirements.courierOther, '德邦')
})
test('weight percentages may be omitted; partial or incorrect totals are rejected', () => {
  const f = form()
  f.requirements.productWeights = [{ range: '0-0.3', percentage: '' }, { range: '>=3', percentage: null }]
  assert.deepEqual(referralPayload(f).requirements.productWeights.map(x => x.percentage), [null, null])
  f.requirements.productWeights[0].percentage = '60'
  assert.throws(() => referralPayload(f), /每个已选重量段/)
  f.requirements.productWeights[1].percentage = '30'
  assert.throws(() => referralPayload(f), /合计/)
  f.requirements.productWeights[1].percentage = '40'
  assert.deepEqual(referralPayload(f).requirements.productWeights.map(x => x.percentage), [60, 40])
  f.requirements.productWeights[1].percentage = '-1'
  assert.throws(() => referralPayload(f), /0–100/)
})
test('invalid ranges and duplicate weights are rejected', () => {
  const f = form(); f.requirements.skuCount = 'whatever'
  assert.throws(() => referralPayload(f), /需求范围/)
  f.requirements.skuCount = ''
  f.requirements.productWeights = [{ range: '>=3' }, { range: '>=3' }]
  assert.throws(() => referralPayload(f), /重量段/)
})
test('rental requests drop stale warehouse; false invoice is displayed', () => {
  const f = form(); f.serviceType = 4; f.warehouseId = 10; f.requirements.invoiceRequired = false
  const body = referralPayload(f)
  assert.equal(body.warehouseId, null)
  assert.ok(requirementRows(body.requirements).some(row => row.label === '是否开发票' && row.value === '否'))
})
test('direct links and WeChat scenes preserve partner token when shared', () => {
  for (const query of [{ token: TOKEN }, { scene: TOKEN }]) {
    assert.equal(parseReferralToken(query), TOKEN)
    assert.equal(referralShare(parseReferralToken(query)).path, `/pages/customer-intake/index?token=${TOKEN}`)
  }
  for (const query of [{ token: '1' }, { scene: '%invalid' }, { token: `${TOKEN}&promoterId=2` }]) assert.equal(parseReferralToken(query), '')
  assert.equal(referralShare('').path, '/pages/entry/index')
})
test('public request omits existing sessions and never redirects on invalid referral', async () => {
  const src = readFileSync(new URL('../src/utils/request.js', import.meta.url), 'utf8')
    .replaceAll('\r\n', '\n')
    .replace(/^import .*$/gm, '').replaceAll('import.meta.env', '{}').replace(/^export /gm, '')
    .replace(/\/\/ #ifdef (H5|MP-WEIXIN)\n([\s\S]*?)\/\/ #endif/g, (_, name, body) => name === 'MP-WEIXIN' ? body : '')
  const calls = [], redirects = [], removed = []
  const ctx = vm.createContext({ uni: { getStorageSync: () => 'stale-partner-token', request: x => calls.push(x), reLaunch: x => redirects.push(x), removeStorageSync: x => removed.push(x), showToast() {} } })
  vm.runInContext(src, ctx)
  const request = vm.runInContext(`get('/public/referral/${TOKEN}')`, ctx)
  assert.equal(calls[0].header.Authorization, undefined)
  calls[0].success({ statusCode: 401, data: { code: 401, message: '填写入口无效' } })
  await assert.rejects(request, /填写入口无效/)
  assert.deepEqual(redirects, []); assert.deepEqual(removed, [])
  const privateRequest = vm.runInContext("get('/referral-link')", ctx)
  assert.equal(calls[1].header.Authorization, 'Bearer stale-partner-token')
  calls[1].success({ statusCode: 200, data: { code: 0, data: {} } }); await privateRequest
})
test('submit requires consent and retry keeps identical request after a lost response', async () => {
  const source = readFileSync(new URL('../src/pages/customer-intake/index.vue', import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  const sent = []; let failures = 1
  const ctx = vm.createContext({ ref: value => ({ value }), reactive: value => value, newReferralForm, newSubmissionId, parseReferralToken, referralShare,
    onLoad() {}, onShareAppMessage() {}, publicReferralApi: { submit: async (token, body) => { sent.push(JSON.parse(JSON.stringify(body))); if (failures-- > 0) throw new Error('网络超时') } },
    uni: { showToast() {}, pageScrollTo() {} } })
  vm.runInContext(source, ctx)
  vm.runInContext(`ready.value=true; referralToken.value='${TOKEN}'`, ctx)
  await vm.runInContext("submit({name:'原始客户'})", ctx); assert.equal(sent.length, 0)
  vm.runInContext('agreed.value=true', ctx)
  await vm.runInContext("submit({name:'原始客户'})", ctx)
  await vm.runInContext("submit({name:'被修改的客户'})", ctx)
  assert.equal(sent.length, 2); assert.deepEqual(sent[0], sent[1]); assert.equal(sent[1].name, '原始客户')
  assert.match(sent[0].submissionId, /^[a-f0-9-]{36}$/)
  await vm.runInContext("submit({name:'重复客户'})", ctx); assert.equal(sent.length, 2)
})
