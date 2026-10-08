import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
const source = readFileSync(new URL('../src/pages/admin-accounts/index.vue', import.meta.url), 'utf8').split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function harness({ admin = true, confirm = true, fail = '', page } = {}) {
  const calls = [], hooks = {}
  const record = { id: 7, name: '测试伙伴', inviteCode: 'ABCD2345', superAdmin: 0, loginDisabled: 0, businessStatus: 1 }
  const action = name => async (...args) => { calls.push([name, ...JSON.parse(JSON.stringify(args))]); if (fail) throw new Error(fail); return { id: 9, name: '新伙伴', inviteCode: 'NEWC2345' } }
  const ctx = vm.createContext({
    ref: value => ({ value }), reactive: value => value, computed: get => ({ get value() { return get() } }),
    onShow: fn => { hooks.show = fn }, onHide: fn => { hooks.hide = fn }, onUnload: fn => { hooks.unload = fn },
    meApi: { me: async () => ({ id: 2, superAdmin: admin }) },
    accountAdminApi: { page: page || (async params => { calls.push(['page', params.type, params.pageNo]); return { records: [record], total: 1, operatorId: 2 } }), create: action('create'), invite: action('invite'), status: action('status'), remove: action('remove') },
    uni: { pageScrollTo() {}, setClipboardData() {}, showModal: opts => opts.success({ confirm }) }, record,
  })
  vm.runInContext(source, ctx)
  return { ctx, calls, hooks, run: s => vm.runInContext(s, ctx) }
}
test('普通账号看不到管理数据，后台直达页面也先核验权限', async () => {
  const h = harness({ admin: false }); await h.hooks.show()
  assert.equal(h.run('authorized.value'), false); assert.equal(h.calls.length, 0)
})
test('改邀请码发送旧值做并发校验，成功后刷新', async () => {
  const h = harness(); await h.hooks.show()
  h.run("begin('invite', record); form.inviteCode='newc2345'; form.reason='调整'")
  await h.run('submit()')
  assert.deepEqual(h.calls[1], ['invite', 7, { inviteCode: 'NEWC2345', expectedInviteCode: 'ABCD2345', reason: '调整' }])
  assert.equal(h.run('mode.value'), ''); assert.equal(h.calls.at(-1)[0], 'page')
})
test('删除需要填写原因并确认，取消不发送删除请求', async () => {
  const h = harness({ confirm: false }); await h.hooks.show()
  h.run("begin('delete', record)"); await h.run('submit()'); assert.match(h.run('formError.value'), /原因/)
  h.run("form.reason='误建'"); await h.run('submit()')
  assert.equal(h.calls.some(c => c[0] === 'remove'), false); assert.equal(h.run('busy.value'), false)
})
test('有关联业务删除失败保留原因，允许改用停用登录', async () => {
  const h = harness({ fail: '有关联业务，不能删除' }); await h.hooks.show()
  h.run("begin('delete', record); form.reason='误建'"); await h.run('submit()')
  assert.match(h.run('formError.value'), /关联业务/); assert.equal(h.run('form.reason'), '误建')
  assert.equal(h.run('mode.value'), 'delete')
})
test('新增账号不存储密码，成功后清空；隐藏页面也清空', async () => {
  const h = harness(); await h.hooks.show()
  h.run("beginCreate(); form.name='新伙伴'; form.username='newuser'; form.password='a-secret'; form.reason='新增'")
  await h.run('submit()'); assert.equal(h.run('form.password'), '')
  assert.equal(h.calls[1][1].type, 'mp'); assert.equal(h.calls[1][1].password, 'a-secret')
  h.run("form.password='second-secret'"); h.hooks.hide(); assert.equal(h.run('form.password'), ''); assert.equal(h.run('rows.value.length'), 0)
})
test('切换身份列表时迟到的请求不会串入另一类账号', async () => {
  const pending = []
  const h = harness({ page: params => new Promise(resolve => pending.push({ params, resolve })) })
  const first = h.run('load(1)'); h.run("type.value='wh'"); const second = h.run('load(1)')
  pending[1].resolve({ records: [{ id: 12, name: '云仓' }], total: 1 }); await second
  pending[0].resolve({ records: [{ id: 7, name: '伙伴' }], total: 1 }); await first
  assert.equal(h.run('rows.value[0].name'), '云仓')
})
test('停用与恢复提交原状态，避免过期页面反向覆盖', async () => {
  const h = harness(); await h.hooks.show()
  h.run("type.value='wh'; begin('status',record); form.reason='暂停'"); await h.run('submit()')
  assert.deepEqual(h.calls[1], ['status', 'wh', 7, { disabled: true, expectedDisabled: false, reason: '暂停' }])
})
