import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import { withdrawalStage, withdrawalAmount, withdrawalTime, withdrawalAccount, approvalHistoryStatus, canDecideWithdrawal, approvalOpinionError } from '../src/utils/withdrawal-approval.mjs'
import { createPager } from '../src/utils/pagination.mjs'

test('提现进度使用实际审批人，无法识别的状态不伪造审批成功', () => {
  assert.equal(withdrawalStage({ status: 1, approval: { assigneeName: '杨洪' } }), '待杨洪审批')
  assert.equal(withdrawalStage({ status: 2 }), '审批通过·待打款')
  assert.equal(withdrawalStage({ status: 3 }), '已打款')
  assert.equal(withdrawalStage({ status: 99 }), '状态待确认')
  assert.equal(approvalHistoryStatus(3), '已驳回')
  assert.equal(approvalHistoryStatus(null), '状态待确认')
})

test('收款资料使用本次申请快照，缺失金额不能显示为0元', () => {
  assert.equal(withdrawalAmount(null), '—')
  assert.equal(withdrawalAmount(''), '—')
  assert.equal(withdrawalAmount('NaN'), '—')
  assert.equal(withdrawalAmount('123.40'), '123.40')
  assert.equal(withdrawalAccount({ accountType: 2, accountTail: '0088' }), '银行卡 · 尾号 0088')
  assert.equal(withdrawalAccount({ accountType: 2 }), '银行卡 · 尾号未记录')
})

test('通过和驳回均需服务端明确授权、待审批状态与真实意见', () => {
  assert.equal(canDecideWithdrawal({ status: 1, canApprove: true }, true), true)
  assert.equal(canDecideWithdrawal({ status: 1, canReject: true }, false), true)
  for (const record of [{ status: 1, positionCode: 'P4' }, { status: 1, promoterName: '杨洪' }, { status: 1, canApprove: 'true' }, { status: 2, canApprove: true }]) {
    assert.equal(canDecideWithdrawal(record, true), false)
  }
  assert.notEqual(approvalOpinionError('   '), '')
  assert.notEqual(approvalOpinionError('字'.repeat(501)), '')
  assert.equal(approvalOpinionError('已核对收款信息'), '')
})

const detailSource = readFileSync(new URL('../src/pages/withdrawal-detail/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
const record = { id: 10, promoterId: 8, promoterName: '测试伙伴', status: 1, amount: '100.00', netAmount: '80.00', accountName: '测试收款人', accountType: 2, accountTail: '5678', canApprove: true, canReject: true }
function harness({ approval = true, detail = record, auditError = '', confirm = true, deferredConfirm = false } = {}) {
  const calls = [], hooks = {}
  let modal
  const context = vm.createContext({
    ref: value => ({ value }), computed: getter => ({ get value() { return getter() } }),
    onLoad: fn => fn({ id: '10', approval: approval ? '1' : undefined }), onShow: () => {}, onHide: fn => { hooks.hide = fn }, onUnload: () => {}, onPullDownRefresh: () => {},
    withdrawalStage, withdrawalAmount, withdrawalTime, withdrawalAccount, approvalHistoryStatus, canDecideWithdrawal, approvalOpinionError,
    bizApi: { withdrawal: async id => { calls.push(['own', id]); return { ...detail } } },
    withdrawalApprovalApi: { detail: async id => { calls.push(['detail', id]); return { ...detail } }, audit: async (id, body) => { calls.push(['audit', id, body.approved, body.remark]); if (auditError) throw new Error(auditError) } },
    uni: { showModal: options => { calls.push(['confirm', options.content]); modal = options; if (!deferredConfirm) options.success({ confirm }) }, showToast: () => calls.push(['toast']) }
  })
  vm.runInContext(detailSource, context)
  return { context, calls, hooks, confirm: value => modal.success({ confirm: value }), run: expr => vm.runInContext(expr, context) }
}

test('申请人详情永远走本人接口，不能通过改页面操作调用审批', async () => {
  const h = harness({ approval: false })
  await h.run('load()')
  h.run("opinion.value = '核对无误'")
  await h.run('decide(true)')
  assert.deepEqual(h.calls, [['own', '10']])
})

test('审批确认展示收款人与金额，重复点击只能产生一次请求', async () => {
  const h = harness({ deferredConfirm: true })
  await h.run('load()')
  h.run("opinion.value = '  已核对资料  '")
  const first = h.run('decide(true)')
  await h.run('decide(true)')
  assert.equal(h.calls.filter(x => x[0] === 'confirm').length, 1)
  assert.match(h.calls.find(x => x[0] === 'confirm')[1], /测试伙伴.*编号 8[\s\S]*100.00[\s\S]*测试收款人[\s\S]*5678/)
  h.confirm(true)
  await first
  assert.deepEqual(h.calls.filter(x => x[0] === 'audit'), [['audit', '10', true, '已核对资料']])
  assert.equal(h.calls.filter(x => x[0] === 'detail').length, 2)
})

test('空意见、取消确认、切换页面都不会提交审批', async () => {
  const empty = harness()
  await empty.run('load()'); await empty.run('decide(true)')
  assert.equal(empty.calls.some(x => x[0] === 'confirm'), false)
  const cancelled = harness({ confirm: false })
  await cancelled.run('load()'); cancelled.run("opinion.value = '资料不符'"); await cancelled.run('decide(false)')
  assert.equal(cancelled.calls.some(x => x[0] === 'audit'), false)
  const hidden = harness({ deferredConfirm: true })
  await hidden.run('load()'); hidden.run("opinion.value = '核对无误'"); const pending = hidden.run('decide(true)')
  hidden.hooks.hide(); hidden.confirm(true); await pending
  assert.equal(hidden.calls.some(x => x[0] === 'audit'), false)
  assert.equal(hidden.run('detail.value'), null)
  assert.equal(hidden.run('opinion.value'), '')
})

test('服务端拒绝审批时不显示成功，保留意见并允许重新读取', async () => {
  const h = harness({ auditError: '该申请已由其他端处理，请刷新' })
  await h.run('load()'); h.run("opinion.value = '资料不符'"); await h.run('decide(false)')
  assert.equal(h.run('actionError.value'), '该申请已由其他端处理，请刷新')
  assert.equal(h.run('opinion.value'), '资料不符')
  assert.equal(h.run('submitting.value'), false)
  assert.equal(h.calls.some(x => x[0] === 'toast'), false)
})

const listSource = readFileSync(new URL('../src/pages/withdrawal-approvals/index.vue', import.meta.url), 'utf8')
  .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
function listHarness({ capability = async () => ({ canApproveWithdrawals: true, approverName: '测试审批人' }), page = async () => ({ records: [record], total: 1 }) } = {}) {
  const calls = [], hooks = {}
  const context = vm.createContext({
    ref: value => ({ value }), reactive: value => value, createPager,
    onShow: fn => { hooks.show = fn }, onHide: fn => { hooks.hide = fn }, onUnload: () => {}, onPullDownRefresh: () => {}, onReachBottom: () => {},
    withdrawalStage, withdrawalAmount, withdrawalTime, withdrawalAccount,
    withdrawalApprovalApi: { capability, page: async filters => { calls.push(filters); return page() } }
  })
  vm.runInContext(listSource, context)
  return { context, calls, hooks, run: expr => vm.runInContext(expr, context) }
}

test('未授权伙伴连列表请求都不发；页面再次显示会重新鉴权并刷新审批结果', async () => {
  const forbidden = listHarness({ capability: async () => ({ canApproveWithdrawals: false }) })
  await forbidden.hooks.show()
  assert.equal(forbidden.calls.length, 0)
  assert.match(forbidden.run('error.value'), /没有提现审批权限/)
  let response = { records: [record], total: 1 }
  const h = listHarness({ page: async () => response })
  await h.hooks.show()
  assert.equal(h.run('state.records.length'), 1)
  h.hooks.hide()
  assert.equal(h.run('state.records.length'), 0)
  assert.equal(h.run('access.value.canApproveWithdrawals'), undefined)
  response = { records: [], total: 0 }
  await h.hooks.show()
  assert.equal(h.calls.length, 2)
  assert.equal(h.run('state.records.length'), 0)
})

test('身份切换后迟到的审批能力和审批列表响应都不能恢复旧数据', async () => {
  let resolveCapability
  const cap = listHarness({ capability: () => new Promise(resolve => { resolveCapability = resolve }) })
  const capLoad = cap.hooks.show(); cap.hooks.hide(); resolveCapability({ canApproveWithdrawals: true }); await capLoad
  assert.equal(cap.calls.length, 0)
  assert.equal(cap.run('access.value.canApproveWithdrawals'), undefined)
  let resolvePage, pageStarted
  const started = new Promise(resolve => { pageStarted = resolve })
  const rows = listHarness({ page: () => new Promise(resolve => { resolvePage = resolve; pageStarted() }) })
  const rowsLoad = rows.hooks.show(); await started; rows.hooks.hide()
  resolvePage({ records: [record], total: 1 }); await rowsLoad
  assert.equal(rows.run('state.records.length'), 0)
  assert.equal(rows.run('access.value.canApproveWithdrawals'), undefined)
})

test('详情权限失败不会留下上一笔数据，也不会发出审批请求', async () => {
  const h = harness()
  await h.run('load()')
  assert.notEqual(h.run('detail.value'), null)
  h.context.withdrawalApprovalApi.detail = async () => { throw new Error('无权查看该提现申请') }
  await h.run('load()')
  assert.equal(h.run('detail.value'), null)
  h.run("opinion.value = '核对无误'"); await h.run('decide(true)')
  assert.match(h.run('error.value'), /无权查看/)
  assert.equal(h.calls.some(x => x[0] === 'audit'), false)
})
