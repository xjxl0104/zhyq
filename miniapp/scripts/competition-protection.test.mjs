import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/warehouse-protection/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness(overrides = {}) {
  const hooks = {}, saves = [], removals = []
  const context = vm.createContext({
    ref: value => ({ value }), onShow: fn => hooks.show = fn,
    onHide: fn => hooks.hide = fn, onUnload: fn => hooks.unload = fn,
    uni: { hideShareMenu() {}, showToast() {}, showModal: options => hooks.modal = options },
    warehouseApi: {
      protection: async () => [{ id: 1, name: '保密品牌' }],
      addProtection: async name => saves.push(name),
      removeProtection: async id => removals.push(id), ...overrides
    }
  })
  vm.runInContext(source, context)
  return { hooks, saves, removals, run: code => vm.runInContext(code, context) }
}
test('保护录入只发送名称，失败保留输入且显示具体错误', async () => {
  const h = harness({ addProtection: async () => { throw Error('保存失败') } })
  await h.run('load()')
  h.run("name.value = '  私密公司  '")
  await h.run('add()')
  assert.equal(h.run('name.value'), '  私密公司  ')
  assert.equal(h.run('error.value'), '保存失败')
  const success = harness()
  await success.run('load()')
  success.run("name.value = '  私密公司  '")
  await success.run('add()')
  assert.deepEqual(success.saves, ['私密公司'])
})
test('关闭页面清空名称并丢弃迟到响应', async () => {
  let resolve
  const h = harness({ protection: () => new Promise(done => resolve = done) })
  const request = h.run('load()')
  h.run("name.value = '草稿'")
  h.hooks.hide()
  resolve([{ id: 1, name: '迟到的商业机密' }])
  await request
  assert.equal(h.run('items.value.length'), 0)
  assert.equal(h.run('name.value'), '')
  assert.equal(h.run('ready.value'), false)
})
test('取消移除不发请求，确认后才移除', async () => {
  const h = harness()
  await h.run('load()')
  h.run('confirmRemove(items.value[0])')
  await h.hooks.modal.success({ confirm: false })
  assert.equal(h.removals.length, 0)
  await h.hooks.modal.success({ confirm: true })
  assert.deepEqual(h.removals, [1])
  assert.equal(h.run('items.value.length'), 0)
})
