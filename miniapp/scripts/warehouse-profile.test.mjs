import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'

const source = readFileSync(new URL('../src/pages/warehouse-profile/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const emptyProfile = { warehouseName: '', name: '', phone: null, profileEditable: true, phoneEditable: true, profileComplete: false, joinStatus: 1 }
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function harness(api = {}) {
  const calls = { reads: 0, saves: [], toasts: [] }
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, computed: get => ({ get value() { return get() } }),
    onShow: fn => { calls.onShow = fn },
    warehouseApi: {
      me: async () => { calls.reads++; return { ...emptyProfile } },
      saveMe: async data => { calls.saves.push(data); return { ...emptyProfile, ...data } },
      submitApply: () => assert.fail('保存个人信息不应提交加盟审核'),
      saveApply: () => assert.fail('保存个人信息不应改写完整加盟申请'),
      ...api,
    },
    uni: { showToast: data => calls.toasts.push(data) },
  })
  vm.runInContext(source, context)
  return { calls, context, run: script => vm.runInContext(script, context) }
}

test('可只补充一个字段，并保留其他空项；保存不提交加盟申请', async () => {
  const { calls, run } = harness()
  await calls.onShow()
  run("form.name = '  张三  '")
  await run('save()')
  assert.deepEqual(JSON.parse(JSON.stringify(calls.saves)), [{ warehouseName: '', name: '张三', phone: '' }])
  assert.equal(run('dirty.value'), false)
  assert.equal(run('profile.joinStatus'), 1)
  assert.equal(calls.toasts.length, 1)
})

test('返回页面会更新权限，保留所有未保存输入', async () => {
  let status = { ...emptyProfile }
  const { calls, run } = harness({ me: async () => status })
  await calls.onShow()
  run("form.name='填写中的姓名';form.phone='13800138000'")
  status = { ...emptyProfile, name: '服务器姓名', profileEditable: false, phoneEditable: false, joinStatus: 3 }
  await calls.onShow()
  assert.equal(run('form.name'), '填写中的姓名')
  assert.equal(run('form.phone'), '13800138000')
  assert.equal(run('profile.profileEditable'), false)
  await run('save()')
  assert.equal(calls.saves.length, 0)
  assert.equal(run('dirty.value'), true)
})

test('刷新请求期间输入的草稿也不能被迟到响应覆盖', async () => {
  const request = deferred()
  let reads = 0
  const { calls, run } = harness({ me: () => ++reads === 1 ? Promise.resolve(emptyProfile) : request.promise })
  await calls.onShow()
  const refresh = calls.onShow()
  run("form.warehouseName='输入中的云仓'")
  request.resolve({ ...emptyProfile, warehouseName: '服务器名称' })
  await refresh
  assert.equal(run('form.warehouseName'), '输入中的云仓')
  assert.equal(run('dirty.value'), true)
})

test('微信绑定电话只读，保存联系人不会提交或覆盖电话', async () => {
  const { calls, run } = harness({ me: async () => ({ ...emptyProfile, phone: '13800138000', phoneEditable: false }) })
  await calls.onShow()
  run("form.name='李四'")
  await run('save()')
  assert.equal(calls.saves.length, 1)
  assert.equal(Object.hasOwn(calls.saves[0], 'phone'), false)
})

test('手机号可留空；填写了不完整手机号时给出纠正提示', async () => {
  const { calls, run } = harness()
  await calls.onShow()
  run("form.phone='123'")
  await run('save()')
  assert.equal(calls.saves.length, 0)
  assert.match(run('error.value'), /11 位手机号/)
  run("form.phone='';form.name='张三'")
  await run('save()')
  assert.equal(calls.saves.length, 1)
})

test('首次读取失败可重试，不提前开放保存', async () => {
  let attempts = 0
  const { calls, run } = harness({ me: async () => { if (++attempts === 1) throw new Error('网络超时'); return emptyProfile } })
  await calls.onShow()
  assert.equal(run('ready.value'), false)
  assert.equal(run('loading.value'), false)
  assert.equal(run('loadError.value'), true)
  assert.equal(run('error.value'), '网络超时')
  await run('save()')
  assert.equal(calls.saves.length, 0)
  await run('load()')
  assert.equal(run('ready.value'), true)
  assert.equal(run('loadError.value'), false)
})

test('保存失败保留输入并可重试，重复点击和 onShow 不会重复提交或覆盖', async () => {
  const request = deferred()
  let saves = 0
  const { calls, run } = harness({ saveMe: data => ++saves === 1 ? request.promise : Promise.resolve({ ...emptyProfile, ...data }) })
  await calls.onShow()
  run("form.warehouseName='云仓 A'")
  const pending = run('save()')
  await run('save()')
  await calls.onShow()
  assert.equal(saves, 1)
  assert.equal(calls.reads, 1)
  request.reject(new Error('保存超时'))
  await pending
  assert.equal(run('form.warehouseName'), '云仓 A')
  assert.equal(run('busy.value'), false)
  assert.equal(run('dirty.value'), true)
  assert.equal(calls.toasts.length, 0)
  await run('save()')
  assert.equal(saves, 2)
  assert.equal(run('dirty.value'), false)
})

test('工作台即使资料读取失败，仍保留业务数据和个人信息入口', async () => {
  const dashboard = readFileSync(new URL('../src/pages/warehouse-dashboard/index.vue', import.meta.url), 'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  let route
  const context = vm.createContext({ ref: value => ({ value }), reactive: value => value, onShow: () => {},
    warehouseApi: { dashboard: async () => ({ customerCount: 2 }), me: async () => { throw new Error('资料读取超时') } },
    warehouseToken: {}, uni: { navigateTo: options => { route = options.url } },
  })
  vm.runInContext(dashboard, context)
  await vm.runInContext('load()', context)
  assert.equal(vm.runInContext('stats.customerCount', context), 2)
  assert.equal(vm.runInContext('loading.value', context), false)
  assert.equal(vm.runInContext('error.value', context), '资料读取超时')
  assert.equal(vm.runInContext("entries[0].page", context), 'warehouse-profile')
  vm.runInContext("go('warehouse-profile')", context)
  assert.equal(route, '/pages/warehouse-profile/index')
})
