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
test('contact position, items per parcel and current logistics status', () => {
  const f = form()
  Object.assign(f.requirements, { contactPosition: ' 运营总监 ', itemsPerParcel: '1.5', warehouseMode: '外包云仓',
    currentCouriers: ['京东', '其他'], currentCourierOther: '', painPoints: ['大促爆仓', '其他'], painPointOther: '退货慢' })
  assert.throws(() => referralPayload(f), /其他快递/)
  f.requirements.currentCourierOther = ' 德邦 '
  const r = referralPayload(f).requirements
  assert.equal(r.contactPosition, '运营总监'); assert.equal(r.itemsPerParcel, 1.5); assert.equal(r.currentCourierOther, '德邦')
  const rows = Object.fromEntries(requirementRows(r).map(x => [x.label, x.value]))
  assert.equal(rows['对接人职位'], '运营总监'); assert.equal(rows['平均包裹产品数量'], '1.5 件')
  assert.equal(rows['目前合作快递'], '京东、其他：德邦'); assert.equal(rows['最头疼的问题'], '大促爆仓、其他：退货慢')
  for (const bad of ['0', '-1', 'abc', '1.234']) { f.requirements.itemsPerParcel = bad; assert.throws(() => referralPayload(f), /平均包裹/) }
  f.requirements.itemsPerParcel = ''; f.requirements.warehouseMode = '乱填'
  assert.throws(() => referralPayload(f), /仓配方式/)
})
test('couriers list puts 圆通/中通 first and includes 京东/邮政', async () => {
  const { COURIERS } = await import('../src/utils/referral-intake.mjs')
  assert.deepEqual(COURIERS.slice(0, 2), ['圆通', '中通']); assert.ok(COURIERS.includes('京东') && COURIERS.includes('邮政'))
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
test('submit records consent and retry keeps identical request after a lost response', async () => {
  const source = readFileSync(new URL('../src/pages/customer-intake/index.vue', import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  const sent = []; let failures = 1
  const ctx = vm.createContext({ ref: value => ({ value }), reactive: value => value, newReferralForm, newSubmissionId, parseReferralToken, referralShare,
    onLoad() {}, onShareAppMessage() {}, publicReferralApi: { submit: async (token, body) => { sent.push(JSON.parse(JSON.stringify(body))); if (failures-- > 0) throw new Error('网络超时') } },
    uni: { showToast() {}, pageScrollTo() {} } })
  vm.runInContext(source, ctx)
  vm.runInContext(`ready.value=true; referralToken.value='${TOKEN}'`, ctx)
  await vm.runInContext("submit({name:'原始客户'})", ctx)
  assert.equal(sent[0].agreed, true)
  await vm.runInContext("submit({name:'被修改的客户'})", ctx)
  assert.equal(sent.length, 2); assert.deepEqual(sent[0], sent[1]); assert.equal(sent[1].name, '原始客户')
  assert.match(sent[0].submissionId, /^[a-f0-9-]{36}$/)
  await vm.runInContext("submit({name:'重复客户'})", ctx); assert.equal(sent.length, 2)
})
test('无 is exclusive for couriers and the form no longer sends an intended warehouse', async () => {
  const { exclusiveNone, COURIERS } = await import('../src/utils/referral-intake.mjs')
  assert.equal(COURIERS.at(-1), '无')
  assert.deepEqual(exclusiveNone(['圆通', '中通'], ['圆通', '中通', '无']), ['无'])
  assert.deepEqual(exclusiveNone(['无'], ['无', '顺丰']), ['顺丰'])
  assert.deepEqual(exclusiveNone([], ['无']), ['无'])
  const f = form(); f.warehouseId = 12; f.requirements.couriers = ['无']; f.requirements.currentCouriers = ['无']
  const body = referralPayload(f)
  assert.equal(body.warehouseId, null); assert.deepEqual(body.requirements.couriers, ['无'])
  f.requirements.couriers = ['无', '圆通']
  assert.throws(() => referralPayload(f), /无/)
})
