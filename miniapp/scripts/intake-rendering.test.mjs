import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import { parse, compileScript } from 'vue/compiler-sfc'
import * as Vue from 'vue'
import { renderToString } from 'vue/server-renderer'
import * as intake from '../src/utils/referral-intake.mjs'
import { componentReferences, verifyWechatComponents, vueFiles } from './wechat-components.mjs'

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url))
const read = name => readFileSync(join(sourceRoot, name), 'utf8')
const pageSource = read('pages/customer-intake/index.vue')
const formSource = read('components/ReferralForm.vue')
const script = source => source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const ref = value => ({ value })
const computed = get => ({ get value() { return get() } })
let renderSequence = 0
async function renderComponent(source, props) {
  // compileScript transforms the cached template AST; isolate it from template interaction tests.
  const { descriptor } = parse(`${source}\n<!-- render-${++renderSequence} -->`)
  const code = compileScript(descriptor, { id: 'intake-render-check', inlineTemplate: true,
    templateOptions: { compilerOptions: { isCustomElement: () => true } } }).content
    .replace(/^import \{([^}]+)\} from ['"]vue['"];?$/gm, (_line, names) => `const { ${names.replaceAll(' as ', ': ')} } = Vue`)
    .replace(/^import .*$/gm, '')
    .replace('export default', 'globalThis.Component =')
  const context = vm.createContext({ Vue, ...intake })
  vm.runInContext(code, context)
  return renderToString(Vue.createSSRApp(context.Component, props))
}
test('partner recommendation initially renders four basic fields; public intake renders full requirements', async () => {
  const form = intake.newReferralForm()
  const basic = await renderComponent(formSource, { form, progressive: true })
  assert.equal((basic.match(/<input\b/g) || []).length, 4)
  for (const label of ['公司名称', '品牌名称', '对接人', '联系手机', '补充详细需求']) assert.ok(basic.includes(label), label)
  for (const label of ['业务规模', '销售品类', '现有仓配现状']) assert.equal(basic.includes(label), false)
  const complete = await renderComponent(formSource, { form, progressive: false })
  for (const label of ['业务规模', '销售品类', '现有仓配现状', '6 件及以上']) assert.ok(complete.includes(label), label)
  assert.equal(complete.includes('<picker'), false)
})
test('customer requirement card renders submitted JSON fields and clearly labels missing details', async () => {
  const source = read('components/CustomerRequirements.vue')
  const shown = await renderComponent(source, { requirements: JSON.stringify({ productCategories: ['食品饮料'], skuCount: '10-20', itemsPerParcelRange: '4', warehouseMode: '外包云仓', invoiceRequired: false }), demand: '日常发货', remark: '周末发货' })
  for (const label of ['食品饮料', 'SKU 数量', '4 件', '外包云仓', '是否开发票', '否', '日常发货', '周末发货']) assert.ok(shown.includes(label), label)
  const empty = await renderComponent(source, { requirements: {} })
  assert.ok(empty.includes('暂未补充详细需求'))
})

test('all custom components are explicitly registered; missing public form import is rejected', () => {
  for (const file of vueFiles(sourceRoot)) componentReferences(readFileSync(file, 'utf8'), file)
  assert.deepEqual(componentReferences(pageSource).map(x => x.name), ['ReferralForm'])
  assert.throws(() => componentReferences(pageSource.replace(/^import ReferralForm.*$/m, '')), /ReferralForm 未显式导入/)
})

test('WeChat artifact gate rejects the exact missing usingComponents registration', () => {
  const root = mkdtempSync(join(tmpdir(), 'intake-components-'))
  const source = join(root, 'src'), build = join(root, 'dist')
  mkdirSync(join(source, 'pages/intake'), { recursive: true })
  mkdirSync(join(build, 'pages/intake'), { recursive: true })
  mkdirSync(join(build, 'components'), { recursive: true })
  writeFileSync(join(source, 'pages/intake/index.vue'), pageSource)
  writeFileSync(join(build, 'pages/intake/index.wxml'), '<referral-form><checkbox-group /></referral-form>')
  const manifest = join(build, 'pages/intake/index.json')
  writeFileSync(manifest, JSON.stringify({ usingComponents: {} }))
  assert.throws(() => verifyWechatComponents(source, build), /usingComponents 缺少 referral-form/)
  writeFileSync(manifest, JSON.stringify({ usingComponents: { 'referral-form': '../../components/ReferralForm' } }))
  const form = join(build, 'components/ReferralForm.wxml')
  writeFileSync(form, '<view><slot /></view>')
  assert.throws(() => verifyWechatComponents(source, build), /缺少表单内容/)
  writeFileSync(form, '<view><input placeholder="公司名称"/><input placeholder="品牌名称"/><input placeholder="对接人职位"/><input placeholder="联系手机"/>销售品类 销售平台 产品重量 指定快递承运商 现有仓配现状<radio-group bindchange="change"/><slot/><button bindtap="submit">提交需求</button></view>')
  writeFileSync(join(build, 'components/ReferralForm.js'), 'const fields=["SKU 数量","日均单量","平均包裹产品数量"]')
  assert.equal(verifyWechatComponents(source, build), 1)
})

test('anonymous QR scene loads form, submit records consent, and optional phone stays empty', async () => {
  const calls = [], pending = []; let onLoad
  const token = '0123456789abcdef0123456789abcdef'
  const page = vm.createContext({ ...intake, ref, reactive: value => value,
    onLoad: fn => { onLoad = fn }, onShareAppMessage() {},
    publicReferralApi: {
      info: async value => { calls.push(['info', value]); return { partnerName: '示例伙伴', warehouses: [] } },
      submit: async (value, body) => { calls.push(['submit', value, JSON.parse(JSON.stringify(body))]) }
    },
    uni: { getStorageSync() { throw new Error('匿名填写不应读取登录态') }, pageScrollTo() {}, showToast() {} }
  })
  vm.runInContext(script(pageSource), page)
  await onLoad({ scene: encodeURIComponent(token) })
  assert.equal(vm.runInContext('ready.value', page), true)
  assert.equal(vm.runInContext('info.value.partnerName', page), '示例伙伴')
  const form = vm.runInContext('form', page)
  form.name = '示例客户'
  const props = { form, warehouses: [], loading: false, disabled: false }
  const component = vm.createContext({ ...intake, ref, computed, defineProps: () => props,
    defineEmits: () => (_event, body) => { page.body = body; pending.push(vm.runInContext('submit(body)', page)) },
    uni: { showToast() {} }
  })
  vm.runInContext(script(formSource), component)
  vm.runInContext('submit()', component)
  await Promise.all(pending)
  assert.equal(calls.length, 2)
  assert.equal(calls[1][1], token)
  assert.equal(calls[1][2].name, '示例客户')
  assert.equal(calls[1][2].phone, null)
  assert.equal(calls[1][2].agreed, true)
  assert.equal(vm.runInContext('submitted.value', page), true)
})

test('warehouse mode is an inline choice that can be selected and cleared', () => {
  const form = intake.newReferralForm()
  const context = vm.createContext({ ...intake, ref, form, defineProps: () => ({ form }), defineEmits: () => () => {} })
  vm.runInContext(script(formSource), context)
  const groups = [], pickers = [], clearButtons = []
  const visit = node => {
    if (node.tag === 'radio-group') groups.push(node)
    if (node.tag === 'picker') pickers.push(node)
    if (node.tag === 'text' && node.props.some(p => p.type === 7 && p.name === 'on' && p.exp?.content === "clearRange('warehouseMode')")) clearButtons.push(node)
    for (const child of node.children || []) visit(child)
  }
  visit(parse(formSource).descriptor.template.ast)
  assert.equal(pickers.length, 0)
  const change = groups.find(g => g.props.some(p => p.name === 'on' && p.exp?.content.includes('warehouseMode'))).props.find(p => p.name === 'on')
  vm.runInContext('(' + change.exp.content + ')({detail:{value:"外包云仓"}})', context)
  assert.equal(form.requirements.warehouseMode, '外包云仓')
  assert.equal(clearButtons.length, 1)
  vm.runInContext("clearRange('warehouseMode')", context)
  assert.equal(form.requirements.warehouseMode, '')
})
test('optional business ranges start empty, show only real choices, and can be cleared', () => {
  const form = intake.newReferralForm(); form.name = '扫码客户'
  let emitted
  const context = vm.createContext({ ...intake, ref, form, defineProps: () => ({ form }),
    defineEmits: () => (_event, body) => { emitted = body }, uni: { showToast() {} } })
  vm.runInContext(script(formSource), context)
  const groups = [], pickers = [], clearButtons = []
  const visit = node => {
    if (node.tag === 'radio-group') groups.push(node)
    if (node.tag === 'picker') pickers.push(node)
    if (node.tag === 'text' && node.props.some(prop => prop.type === 6 && prop.name === 'class' && prop.value?.content === 'clear-choice')) clearButtons.push(node)
    for (const child of node.children || []) visit(child)
  }
  visit(parse(formSource).descriptor.template.ast)
  assert.equal(groups.length, 2)
  assert.equal(clearButtons.length, 2)
  assert.equal(pickers.length, 0)
  const change = groups[0].props.find(prop => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'change')
  const clear = clearButtons[0].props.find(prop => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'click')
  const clearVisible = clearButtons[0].props.find(prop => prop.type === 7 && prop.name === 'if')
  const fields = vm.runInContext('rangeFields', context)
  assert.deepEqual(Array.from(fields, field => field.key), ['skuCount', 'dailyOrders', 'itemsPerParcelRange'])
  for (const field of fields) {
    context.field = field
    assert.equal(form.requirements[field.key], '')
    assert.equal(Boolean(vm.runInContext(clearVisible.exp.content, context)), false)
    assert.ok(field.options.every(option => option.value && option.label !== '暂不填写'))
    vm.runInContext('submit()', context)
    assert.equal(emitted.requirements[field.key], '')
    for (const option of [field.options[0], field.options.at(-1)]) {
      context.next = option.value
      vm.runInContext(`(${change.exp.content})({detail:{value:next}})`, context)
      vm.runInContext('submit()', context)
      assert.equal(emitted.requirements[field.key], option.value)
    }
    assert.equal(Boolean(vm.runInContext(clearVisible.exp.content, context)), true)
    vm.runInContext(clear.exp.content, context)
    vm.runInContext('submit()', context)
    assert.equal(emitted.requirements[field.key], '')
    assert.equal(Boolean(vm.runInContext(clearVisible.exp.content, context)), false)
  }
})
