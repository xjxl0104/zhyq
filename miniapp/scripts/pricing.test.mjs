import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { amountToMills, formatPerOrder, millsToAmount, validatePricing } from '../src/utils/pricing.mjs'

test('0.1 分的单价可以完整保存并展示，不舍入为零', () => {
  assert.equal(amountToMills('0.001'), 1)
  assert.equal(formatPerOrder('0.001'), '0.001')
  assert.equal(formatPerOrder(0), '0.000')
  assert.equal(millsToAmount(3), '0.003')
})

test('不从空输入或异常输入产生默认金额', () => {
  for (const value of ['', null, undefined, '0.0001', '-0.001', 'abc', '1e3', 'Infinity']) {
    assert.equal(amountToMills(value), null)
  }
  assert.notEqual(validatePricing('', '', []), '')
  assert.notEqual(validatePricing('0.300', '', []), '')
})

test('P4 本人可填零，也可以不选择其他受益人', () => {
  assert.equal(validatePricing('0.300', '0', []), '')
  assert.equal(validatePricing('0.300', '0.300', []), '')
  assert.equal(validatePricing('0', '0', []), '')
})

test('按整数精度核对总额，金额相等允许保存而超出0.001拒绝', () => {
  const beneficiaries = [{ promoterId: 11, amountPerOrder: '0.100' }, { promoterId: 12, amountPerOrder: '0.100' }]
  assert.equal(validatePricing('0.300', '0.100', beneficiaries), '')
  assert.match(validatePricing('0.299', '0.100', beneficiaries), /超过总佣金/)
  assert.match(validatePricing('0.001', '0.002', []), /超过总佣金/)
})

test('受益人必须具体选择且不能通过不同类型的同一ID重复分配', () => {
  assert.match(validatePricing('1', '0', [{ promoterId: '', amountPerOrder: '0.100' }]), /请选择/)
  assert.match(validatePricing('1', '0', [{ promoterId: 11, amountPerOrder: '0.1' }, { promoterId: '11', amountPerOrder: '0.2' }]), /不能重复/)
  assert.match(validatePricing('1', '0', [{ promoterId: 11, amountPerOrder: '' }]), /请填写/)
})

test('每个客户最多两位受益人，P1-P4金额没有递增或固定比例限制', () => {
  assert.match(validatePricing('1', '0', [1, 2, 3].map(promoterId => ({ promoterId, amountPerOrder: '0' }))), /最多/)
  assert.equal(validatePricing('1', '0.001', [{ promoterId: 11, amountPerOrder: '0.900' }, { promoterId: 12, amountPerOrder: '0.005' }]), '')
})

const pageSource = readFileSync(new URL('../src/pages/allocation/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function pageHarness({ positionCode = 'P4', saveFailure = '' } = {}) {
  const calls = []
  const customer = { id: 31, name: '测试品牌', configured: false }
  const view = { customerId: 31, customerName: customer.name, ownerPromoterId: 4, ownerName: 'P4', canEdit: true, configured: false,
    totalPerOrder: null, ownerPerOrder: null, beneficiaries: [] }
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, computed: getter => ({ get value() { return getter() } }),
    onLoad: () => {}, amountToMills, millsToAmount, formatPerOrder, validatePricing,
    meApi: { me: async () => ({ id: 4, name: 'P4', positionCode, status: 1 }) },
    pricingApi: {
      customers: async () => { calls.push('customers'); return [customer] },
      beneficiaries: async () => [{ id: 11, name: '受益人', positionCode: 'P1' }],
      customer: async () => ({ ...view }),
      save: async (id, payload) => {
        calls.push({ id, payload: JSON.parse(JSON.stringify(payload)) })
        if (saveFailure) throw new Error(saveFailure)
        return { ...view, ...payload, configured: true }
      }
    },
    uni: { showToast: () => calls.push('saved'), showModal: ({ success }) => success({ confirm: false }) }
  })
  vm.runInContext(pageSource, context)
  return { context, calls }
}

test('P1用户进入定价页面不会请求客户金额，也不能通过保存函数提交', async () => {
  const { context, calls } = pageHarness({ positionCode: 'P1' })
  await vm.runInContext('load()', context)
  await vm.runInContext('save()', context)
  assert.equal(vm.runInContext('canPrice.value', context), false)
  assert.deepEqual(calls, [])
})

test('P4定价以独立本人金额和具体受益人单价提交API，并读取服务端回显', async () => {
  const { context, calls } = pageHarness()
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('form.totalPerOrder', context), '')
  assert.equal(vm.runInContext('form.ownerPerOrder', context), '')
  vm.runInContext("form.totalPerOrder='0.300'; form.ownerPerOrder='0.001'; distribute.value=true; form.beneficiaries=[{promoterId:11,amountPerOrder:'0.005'}]", context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls[1], { id: 31, payload: { totalPerOrder: '0.300', ownerPerOrder: '0.001', beneficiaries: [{ promoterId: 11, amountPerOrder: '0.005' }] } })
  assert.equal(vm.runInContext('companyText.value', context), '0.294')
  assert.equal(vm.runInContext('detail.value.configured', context), true)
})

test('关闭分配时不提交保留在草稿中的受益人金额', async () => {
  const { context, calls } = pageHarness()
  await vm.runInContext('load()', context)
  vm.runInContext("form.totalPerOrder='0.3'; form.ownerPerOrder='0'; form.beneficiaries=[{promoterId:11,amountPerOrder:'0.005'}]; distribute.value=false", context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls[1].payload, { totalPerOrder: '0.300', ownerPerOrder: '0.000', beneficiaries: [] })
})

test('保存失败保留全部输入并恢复操作，不显示保存成功', async () => {
  const { context, calls } = pageHarness({ saveFailure: '网络暂不可用' })
  await vm.runInContext('load()', context)
  vm.runInContext("form.totalPerOrder='0.3'; form.ownerPerOrder='0.001'", context)
  await vm.runInContext('save()', context)
  assert.equal(vm.runInContext('form.totalPerOrder', context), '0.3')
  assert.equal(vm.runInContext('form.ownerPerOrder', context), '0.001')
  assert.equal(vm.runInContext('formError.value', context), '网络暂不可用')
  assert.equal(vm.runInContext('editable.value', context), true)
  assert.equal(calls.includes('saved'), false)
})
