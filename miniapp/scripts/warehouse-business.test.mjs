import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { createPager } from '../src/utils/pagination.mjs'

const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function harness(page, api = {}, extra = {}) {
  const calls = { toasts: [], routes: [], modals: [] }
  const source = readFileSync(new URL(`../src/pages/warehouse-${page}/index.vue`, import.meta.url), 'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value,
    computed: get => ({ get value() { return get() } }), createPager,
    onShow: fn => { calls.onShow = fn }, onLoad: fn => { calls.onLoad = fn },
    onPullDownRefresh: fn => { calls.onRefresh = fn }, onReachBottom: fn => { calls.onBottom = fn },
    warehouseApi: api, parseFiles: value => JSON.parse(value || '[]'),
    uni: {
      showToast: value => calls.toasts.push(value), navigateTo: value => calls.routes.push(value.url),
      stopPullDownRefresh() {}, showModal: value => calls.modals.push(value), setClipboardData() {},
    }, ...extra,
  })
  vm.runInContext(source, context)
  return { calls, run: source => vm.runInContext(source, context) }
}
const empty = async () => ({ records: [], total: 0 })

test('确认账单期间锁住类型并只提交最初核对的单据，阻止重复提交', async () => {
  const confirmed = []
  const { calls, run } = harness('settlement', { settlements: empty, confirmSettlement: async id => confirmed.push(id), confirmBill: () => assert.fail('不得确认另一类单据') })
  const first = run("confirm({id:7,amount:23,periodStart:'2026-10-01',periodEnd:'2026-10-31'})")
  assert.equal(run('busy.value'), 7)
  run("changeKind('bill')")
  await run('confirm({id:8})')
  assert.equal(run('kind.value'), 'settlement')
  assert.equal(calls.modals.length, 1)
  calls.modals[0].success({ confirm: true })
  await first
  assert.deepEqual(confirmed, [7])
  assert.equal(run('busy.value'), null)
})

test('争议失败释放操作锁并显示失败原因，可安全重试', async () => {
  const { calls, run } = harness('settlement', { disputeBill: async () => { throw new Error('账单已冻结') } })
  calls.onLoad({ kind: 'bill' })
  const pending = run('dispute({id:7})')
  calls.modals[0].success({ confirm: true, content: '有重复订单' })
  await pending
  assert.equal(run('error.value'), '账单已冻结')
  assert.equal(run('busy.value'), null)
  assert.equal(calls.toasts.length, 0)
})

test('切换账单类型后同编号的迟到明细不会覆盖新类型，失败可重试', async () => {
  const response = deferred()
  const { run } = harness('settlement', { bills: empty, settlementLines: () => response.promise, billLines: async () => { throw new Error('明细读取超时') } })
  const old = run('toggleDetails({id:1})')
  run("changeKind('bill')")
  response.resolve([{ id:1, amount:999 }])
  await old
  assert.equal(run('details.value[1]'), undefined)
  await run('toggleDetails({id:1})')
  assert.equal(run('error.value'), '明细读取超时')
  assert.equal(run('detailLoading.value[1]'), undefined)
})

test('加载更多期间连续触发只拉取同一页一次，失败不跳页', async () => {
  const response = deferred(), pages = []
  const { run } = harness('settlement', { settlements: ({ pageNo }) => { pages.push(pageNo); return pageNo === 1 ? Promise.resolve({records:[{id:1}],total:3}) : response.promise } })
  await run('load(true)')
  const pending = run('load(false)')
  await run('load(false)')
  response.reject(new Error('超时'))
  await pending
  assert.deepEqual(pages, [1, 2])
  assert.equal(run('page.value'), 2)
  assert.equal(run('rows.value.length'), 1)
})

test('服务费通知准确打开服务费标签，通知列表支持超过 50 条', async () => {
  const { calls, run } = harness('notice', {
    notices: async ({ pageNo, pageSize }) => ({ records: Array.from({length: Math.min(pageSize, 55 - (pageNo - 1) * pageSize)}, (_, i) => ({id:(pageNo - 1)*pageSize+i+1})), total:55 }),
    noticeUnread: async () => 55, noticeRead: async () => {},
  })
  await calls.onShow()
  await calls.onBottom(); await calls.onBottom()
  assert.equal(run('rows.value.length'), 55)
  await run("open({id:1,bizType:'bill'})")
  assert.equal(calls.routes[0], '/pages/warehouse-settlement/index?kind=bill')
  assert.equal(run('unread.value'), 54)
})

test('重复点同一通知不会重复扣未读数；读取失败不会伪标记已读', async () => {
  const response = deferred()
  const { run } = harness('notice', { noticeRead: () => response.promise })
  run('unread.value=2;var notice={id:1,bizType:"bill"}')
  const first = run('open(notice)')
  await run('open(notice)')
  response.reject(new Error('标记已读失败'))
  await first
  assert.equal(run('notice.readAt'), undefined)
  assert.equal(run('unread.value'), 2)
  assert.equal(run('error.value'), '标记已读失败')
})

test('ERP 一次性密钥可显示，并在复制返回页面后的元数据刷新中保留', async () => {
  let configured = false, issued = 0
  const credentials = { configured:true, appId:'wh-test', status:1, env:1, endpoint:'/test', secret:null }
  const { calls, run } = harness('erp', {
    erp: async () => configured ? credentials : {configured:false},
    issueSandbox: async () => { configured=true; issued++; return {...credentials,secret:'test-secret'} },
  })
  await calls.onShow()
  assert.equal(run('statusText.value'), '尚未配置接入凭证')
  await run('issue()')
  assert.equal(run('secret.value'), 'test-secret')
  assert.equal(run('statusText.value'), '接入凭证已启用')
  await calls.onShow()
  assert.equal(run('secret.value'), 'test-secret')
  await run('issue()')
  assert.equal(issued, 1)
})

test('ERP 服务连接成功不会伪称 ERP 已联通，错误保留重试机会', async () => {
  const { run } = harness('erp', { ping: async () => ({ok:true,erpStatus:0,lastSyncAt:null}), issueSandbox: async () => { throw new Error('签发失败') }, erp: async () => ({configured:false}) })
  await run('load()'); await run('ping()')
  assert.equal(run('connectionMessage.value'), '园区服务可访问；ERP 状态：未接入')
  await run('issue()')
  assert.equal(run('busy.value'), '')
  assert.equal(run('error.value'), '签发失败')
  assert.equal(run('erp.configured'), false)
})

const contractApi = (extra = {}) => ({ contracts: empty, agreement: async () => ({joinStatus:4}), customers: empty, ...extra })
test('协议预览返回和迟到的刷新响应不会清除未提交的附件', async () => {
  const agreement = deferred()
  const { calls, run } = harness('contracts', contractApi({agreement: () => agreement.promise}))
  const refresh = calls.onShow()
  run("agreementFiles.value=[{id:8,name:'新协议.pdf'}]")
  agreement.resolve({joinStatus:4,file:{id:1,name:'旧协议.pdf'}})
  await refresh
  assert.equal(run('agreementFiles.value[0].id'), 8)
  run('contractFileBusy.value=true')
  assert.equal(run('fileBusy.value'), true)
  await calls.onShow()
  run('newDraft()')
  assert.equal(run('editing.value'), false)
})

test('合同选择包含第 101 个及以后的已承接客户，排除流失客户', async () => {
  const pages = []
  const { run } = harness('contracts', contractApi({customers: async ({pageNo}) => {
    pages.push(pageNo)
    return {total:102,records:pageNo===1?Array.from({length:100},(_,i)=>({id:i+1,status:1,warehouseAssignmentStatus:1})):[{id:101,status:1,warehouseAssignmentStatus:2},{id:102,status:3,warehouseAssignmentStatus:2}]}
  }}))
  await run('load()')
  assert.deepEqual(pages,[1,2])
  assert.equal(run('acceptedCustomers.value.length'),1)
  assert.equal(run('acceptedCustomers.value[0].id'),101)
})

test('编辑合法的四位小数合同费率保留其他收费项', async () => {
  const saved = []
  const { run } = harness('contracts', contractApi({updateContract: async (id, data) => saved.push(data)}))
  run(`edit({id:7,customerId:9,customerName:'测试客户',serviceType:2,feeModel:2,startDate:'2026-10-01',endDate:'2026-10-31',deposit:0,payCycle:3,priceTable:'{"perOrder":0.1234,"perItem":0.2}',files:'[]'})`)
  await run('saveDraft()')
  assert.equal(saved.length,1)
  assert.deepEqual(JSON.parse(saved[0].priceTable),{perOrder:0.1234,perItem:0.2})
})


test('加载下一页不会丢弃当前单据的待返回明细或留下禁用按钮', async () => {
  const response = deferred()
  const { run } = harness('settlement', {
    settlements: async ({pageNo}) => ({records:[{id:pageNo}],total:2}),
    settlementLines: () => response.promise,
  })
  await run('load(true)')
  const detail = run('toggleDetails({id:1})')
  await run('load(false)')
  response.resolve([{id:5,amount:100}])
  await detail
  assert.equal(run('details.value[1][0].amount'),100)
  assert.equal(run('detailLoading.value[1]'),undefined)
})
