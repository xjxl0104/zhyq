import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import vm from 'node:vm'
import { parse } from 'vue/compiler-sfc'
import * as intake from '../src/utils/referral-intake.mjs'
import { componentReferences, verifyWechatComponents, vueFiles } from './wechat-components.mjs'

const sourceRoot = fileURLToPath(new URL('../src/', import.meta.url))
const read = name => readFileSync(join(sourceRoot, name), 'utf8')
const pageSource = read('pages/customer-intake/index.vue')
const formSource = read('components/ReferralForm.vue')
const script = source => source.split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const ref = value => ({ value })
const computed = get => ({ get value() { return get() } })

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
  writeFileSync(form, '<view><input placeholder="公司 / 品牌名称"/><input placeholder="对接人职位"/><input placeholder="联系手机"/>销售平台 产品重量 指定快递承运商 平均包裹产品数量 现有仓配现状<slot/><button bindtap="submit">提交需求</button></view>')
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
  const component = vm.createContext({ ...intake, computed, defineProps: () => props,
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

test('all picker types reopen at selected value and can return to first option', () => {
  const form = intake.newReferralForm()
  form.warehouseId = 12
  form.requirements.storageArea = intake.AREA_RANGES[1]
  form.requirements.warehouseMode = '外包云仓'
  // Vue templates unwrap computed refs before evaluating picker bindings.
  const context = vm.createContext({ ...intake, form, computed: get => get(),
    defineProps: () => ({ form, warehouses: [{ id: 12, name: '示例仓库' }] }), defineEmits: () => () => {} })
  vm.runInContext(script(formSource), context)
  vm.runInContext('globalThis.field = rangeFields[0]', context)
  const pickers = []
  const visit = node => { if (node.tag === 'picker') pickers.push(node); for (const child of node.children || []) visit(child) }
  visit(parse(formSource).descriptor.template.ast)
  assert.equal(pickers.length, 2)
  for (const [index, picker] of pickers.entries()) {
    const value = picker.props.find(prop => prop.type === 7 && prop.name === 'bind' && prop.arg?.content === 'value')
    assert.ok(value, `picker ${index} 必须绑定当前选择`)
    const change = picker.props.find(prop => prop.type === 7 && prop.name === 'on' && prop.arg?.content === 'change')
    assert.equal(vm.runInContext(value.exp.content, context), [2, 2][index])
    vm.runInContext(`(${change.exp.content})({detail:{value:0}})`, context)
    assert.equal(vm.runInContext(value.exp.content, context), 0)
  }
})
