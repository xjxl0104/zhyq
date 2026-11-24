import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { amountToMills, formatPerOrder, millsToAmount, validatePricing, platformFee, pricingRemainder, unitsToAmount, SALESPERSON_FEE_MILLS } from '../src/utils/pricing.mjs'

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

test('P4 本人可填零，也可以不选择其他受益人，且总单价需覆盖 10% 服务费和 0.020 业务员佣金', () => {
  assert.equal(validatePricing('0.300', '0', []), '')
  assert.equal(validatePricing('0.300', '0.250', []), '')
  assert.equal(validatePricing('0.100', '0.070', []), '')
  assert.match(validatePricing('0.020', '0', []), /0\.023/)
})

test('扣除10%服务费和0.020业务员(P0)佣金后的金额用整数精度核对，超出0.001拒绝', () => {
  const beneficiaries = [{ promoterId: 11, amountPerOrder: '0.100' }, { promoterId: 12, amountPerOrder: '0.100' }]
  // 0.300 - 0.030(10%) - 0.020(P0) = 0.250
  assert.equal(validatePricing('0.300', '0.050', beneficiaries), '')
  assert.match(validatePricing('0.300', '0.051', beneficiaries), /0\.250/)
  assert.match(validatePricing('0.100', '0.071', []), /0\.070/)
})

test('受益人必须具体选择且不能通过不同类型的同一ID重复分配', () => {
  assert.match(validatePricing('1', '0', [{ promoterId: '', amountPerOrder: '0.100' }]), /请选择/)
  assert.match(validatePricing('1', '0', [{ promoterId: 11, amountPerOrder: '0.1' }, { promoterId: '11', amountPerOrder: '0.2' }]), /不能重复/)
  assert.match(validatePricing('1', '0', [{ promoterId: 11, amountPerOrder: '' }]), /请填写/)
})

test('每个客户最多两位受益人，P1-P4金额没有递增或固定比例限制', () => {
  assert.match(validatePricing('1', '0', [1, 2, 3].map(promoterId => ({ promoterId, amountPerOrder: '0' }))), /最多/)
  // 1.000 - 0.100(10%) - 0.020(P0) = 0.880
  assert.equal(validatePricing('1', '0.001', [{ promoterId: 11, amountPerOrder: '0.874' }, { promoterId: 12, amountPerOrder: '0.005' }]), '')
})

const pageSource = readFileSync(new URL('../src/pages/allocation-detail/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')

function pageHarness({ positionCode = 'P4', saveFailure = '', viewOverride = {} } = {}) {
  const calls = []
  const customer = { id: 31, name: '测试品牌', configured: false }
  const view = { customerId: 31, customerName: customer.name, ownerPromoterId: 4, ownerName: 'P4', canEdit: true, configured: false,
    totalPerOrder: null, ownerPerOrder: null, beneficiaries: [], salespersonCommissionPerOrder: 0.02, ...viewOverride }
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, computed: getter => ({ get value() { return getter() } }),
    onLoad: () => {}, onUnload: () => {}, amountToMills, millsToAmount, formatPerOrder, validatePricing, platformFee, pricingRemainder, unitsToAmount, SALESPERSON_FEE_MILLS,
    meApi: { me: async () => ({ id: 4, name: 'P4', positionCode, status: 1 }) },
    pricingApi: {
      customers: async () => { calls.push('customers'); return [customer] },
      beneficiaries: async () => [{ id: 11, name: '受益人', positionCode: 'P1' }],
      customer: async () => { calls.push('customer'); return { ...view } },
      save: async (id, payload) => {
        calls.push({ id, payload: JSON.parse(JSON.stringify(payload)) })
        if (saveFailure) throw new Error(saveFailure)
        return { ...view, ...payload, configured: true }
      }
    },
    uni: { showToast: () => calls.push('saved'), showModal: ({ success }) => success({ confirm: false }) }
  })
  vm.runInContext(pageSource, context)
  vm.runInContext("customerId = '31'", context)
  return { context, calls }
}

test('P1用户进入定价页面不会请求客户金额，也不能通过保存函数提交', async () => {
  const { context, calls } = pageHarness({ positionCode: 'P1' })
  await vm.runInContext('load()', context)
  await vm.runInContext('save()', context)
  assert.equal(vm.runInContext('canPrice.value', context), false)
  assert.deepEqual(calls, [])
})

test('P4定价以独立本人金额和具体受益人单价提交API，并读取服务端回显与业务员固定佣金', async () => {
  const { context, calls } = pageHarness()
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('form.totalPerOrder', context), '')
  assert.equal(vm.runInContext('form.ownerPerOrder', context), '')
  vm.runInContext("form.totalPerOrder='0.300'; form.ownerPerOrder='0.001'; distribute.value=true; form.beneficiaries=[{promoterId:11,amountPerOrder:'0.005'}]", context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls[1], { id: '31', payload: { totalPerOrder: '0.300', ownerPerOrder: '0.001', beneficiaries: [{ promoterId: 11, amountPerOrder: '0.005' }] } })
  assert.equal(vm.runInContext('feeText.value', context), '0.0300')
  assert.equal(vm.runInContext('salespersonFeeText.value', context), '0.020')
  // 0.300 - 0.0300(10%) - 0.0200(P0) - 0.0010 - 0.0050 = 0.2440
  assert.equal(vm.runInContext('remainderText.value', context), '0.2440')
  assert.equal(vm.runInContext('detail.value.configured', context), true)
})

test('云仓确认返点锁定后自动带入总计佣单价并展示锁定状态', async () => {
  const { context } = pageHarness({ viewOverride: { totalPerOrderLocked: true, lockedTotalPerOrder: 0.15, totalPerOrder: 0.15, salespersonName: '张三' } })
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('form.totalPerOrder', context), '0.150')
  assert.equal(vm.runInContext('detail.value.totalPerOrderLocked', context), true)
  assert.equal(vm.runInContext('maxForOwner.value', context), '0.115')
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

test('10%服务费保留第四位，扣除0.020业务员佣金后未分配尾差独立显示', () => {
  assert.equal(platformFee('0.025'), '0.0025')
  // 0.025 - 0.0025 - 0.0200 - 0.0020 = 0.0005
  assert.equal(unitsToAmount(pricingRemainder('0.025', '0.002', [])), '0.0005')
  assert.match(validatePricing('0.025', '0.003', []), /0\.002/)
})

test('超出可分配预算不会发出保存请求', async () => {
  const { context, calls } = pageHarness()
  await vm.runInContext('load()', context)
  vm.runInContext("form.totalPerOrder='0.100'; form.ownerPerOrder='0.071'", context)
  await vm.runInContext('save()', context)
  assert.deepEqual(calls, ['customer'])
  assert.match(vm.runInContext('formError.value', context), /0\.070/)
})

const listSource = readFileSync(new URL('../src/pages/allocation/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function listHarness(positionCode) {
  const calls = []
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }), onLoad: () => {}, onShow: () => {},
    meApi: { me: async () => ({ id: 1, positionCode, status: 1 }) },
    pricingApi: { allocations: async () => { calls.push('allocations'); return [{ key: 'contract:9', contractId: 9, customerId: 31, customerName: '客户', amountPerOrder: '0.018', canEdit: positionCode === 'P4' }] } },
    formatPerOrder, navigateTo: path => calls.push(path)
  })
  vm.runInContext(listSource, context)
  return { context, calls }
}
test('非P4只读取本人分佣列表，不能打开编辑页面', async () => {
  const { context, calls } = listHarness('P1')
  await vm.runInContext('load()', context)
  vm.runInContext('openItem({ contractId: 9, canEdit: true })', context)
  assert.deepEqual(calls, ['allocations'])
  assert.equal(vm.runInContext('visibleItems.value[0].amountPerOrder', context), '0.018')
})
test('P4先看合同列表，明确点击后才进入该合同分佣', async () => {
  const { context, calls } = listHarness('P4')
  await vm.runInContext('load()', context)
  assert.deepEqual(calls, ['allocations'])
  vm.runInContext('openItem(visibleItems.value[0])', context)
  assert.deepEqual(calls, ['allocations', '/pages/allocation-detail/index?contractId=9'])
})
