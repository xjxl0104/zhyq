import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Withdrawal from '../Withdrawal.vue'
import { mktWithdrawalApi } from '@/api/marketing'
import { ElMessageBox } from 'element-plus'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: p => permissions.has(p) }))
vi.mock('@/utils/fileDownload', () => ({ startFileDownload: vi.fn() }))
vi.mock('@/api/marketing', () => ({ mktWithdrawalApi: {
  page: vi.fn(), manualOptions: vi.fn(), manualContext: vi.fn(), manual: vi.fn(),
  get: vi.fn(), approve: vi.fn(), reject: vi.fn(), payAccount: vi.fn(), pay: vi.fn()
} }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal(), ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }, ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() } }))
const wrappers = []
const pending = () => { let resolve, reject; const promise = new Promise((a,b) => { resolve=a; reject=b }); return { promise, resolve, reject } }
const context = (id, extra = {}) => ({ promoter: { id, name: `伙伴${id}`, phone: '13800138000', positionCode: 'P2', inviteCode: '123456', status: 1 }, account: { name: `伙伴${id}`, accountType: 2, accountTail: '5678', bankName: '测试银行', reviewStatus: 1 }, availableBalance: 55.955, pendingAmount: 0, pendingCount: 0, frozenAmount: 2, approval: { assigneeName: '杨洪', nodeName: '提现审批' }, canApply: true, ...extra })
function mount() {
  const w = shallowMount(Withdrawal, { global: { stubs: { Search: true, Plus: true, RouterLink: { template: '<span><slot /></span>' }, ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } }, directives: { loading: () => {} } } })
  wrappers.push(w); return w
}
beforeEach(() => {
  vi.clearAllMocks(); permissions.clear(); permissions.add('crm:marketing:withdrawal:audit')
  mktWithdrawalApi.page.mockResolvedValue({ records: [], total: 0 })
  mktWithdrawalApi.manualOptions.mockResolvedValue([{ id: 15, name: '谢益长', phone: '137****3664', positionCode: 'P2' }])
  mktWithdrawalApi.manualContext.mockImplementation(id => Promise.resolve(context(id)))
  mktWithdrawalApi.manual.mockResolvedValue({ id: 10 })
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('代申请提现核对与防错', () => {
  it('搜索姓名后选中伙伴，未核对资料或超余额均不能提交', async () => {
    const w=mount(); await flushPromises(); await w.vm.openManual(); await w.vm.searchManualPartners(' 谢益长 ')
    expect(mktWithdrawalApi.manualOptions).toHaveBeenLastCalledWith('谢益长')
    await w.vm.loadManualContext(15); w.vm.manual.amount=55.95
    await w.vm.submitManual(); expect(mktWithdrawalApi.manual).not.toHaveBeenCalled()
    w.vm.manual.confirmed=true; w.vm.manual.amount=55.96
    await w.vm.submitManual(); expect(mktWithdrawalApi.manual).not.toHaveBeenCalled()
    w.vm.manual.amount=55.95; await w.vm.submitManual()
    expect(mktWithdrawalApi.manual).toHaveBeenCalledWith({ promoterId: 15, amount: 55.95 })
    expect(w.vm.manual.visible).toBe(false)
  })
  it('从伙伴A切到B，A迟到资料不能覆盖B，也不能沿用A的金额和勾选', async () => {
    const a=pending(),b=pending(); mktWithdrawalApi.manualContext.mockImplementation(id=>id===1?a.promise:b.promise)
    const w=mount(); await flushPromises(); await w.vm.openManual()
    const pa=w.vm.loadManualContext(1); w.vm.manual.amount=10; w.vm.manual.confirmed=true
    const pb=w.vm.loadManualContext(2)
    expect(w.vm.manual.amount).toBeNull(); expect(w.vm.manual.confirmed).toBe(false)
    b.resolve(context(2)); await pb; a.resolve(context(1)); await pa
    expect(w.vm.manual.context.promoter.id).toBe(2)
  })
  it('读取失败、无收款账户、后端返回了错误伙伴时都阻止申请', async () => {
    const w=mount(); await flushPromises(); await w.vm.openManual()
    mktWithdrawalApi.manualContext.mockRejectedValueOnce(new Error('读取失败'))
    await w.vm.loadManualContext(15); w.vm.manual.amount=10; w.vm.manual.confirmed=true
    await w.vm.submitManual(); expect(mktWithdrawalApi.manual).not.toHaveBeenCalled()
    mktWithdrawalApi.manualContext.mockResolvedValueOnce(context(15,{ account:null,canApply:false,blockedReason:'请先完善收款资料' }))
    await w.vm.loadManualContext(15); w.vm.manual.amount=10; w.vm.manual.confirmed=true
    await w.vm.submitManual(); expect(mktWithdrawalApi.manual).not.toHaveBeenCalled()
    mktWithdrawalApi.manualContext.mockResolvedValueOnce(context(16))
    await w.vm.loadManualContext(15)
    expect(w.vm.manual.context).toBeNull(); expect(w.vm.manual.error).toContain('不一致')
  })
  it('关闭后响应不能回填，提交过程中不能切换伙伴或重复提交', async () => {
    const late=pending(); mktWithdrawalApi.manualContext.mockReturnValueOnce(late.promise)
    const w=mount(); await flushPromises(); await w.vm.openManual(); const load=w.vm.loadManualContext(1)
    w.vm.closeManual(); late.resolve(context(1)); await load; expect(w.vm.manual.context).toBeNull()
    await w.vm.openManual(); await w.vm.loadManualContext(15)
    Object.assign(w.vm.manual,{ amount:10,confirmed:true })
    const saving=pending(); mktWithdrawalApi.manual.mockReturnValueOnce(saving.promise)
    const request=w.vm.submitManual(); await w.vm.loadManualContext(16); w.vm.closeManual(); await w.vm.submitManual()
    expect(w.vm.manual.promoterId).toBe(15); expect(w.vm.manual.visible).toBe(true); expect(mktWithdrawalApi.manual).toHaveBeenCalledTimes(1)
    saving.resolve({}); await request
  })
  it('失败后必须重新读取资料再核对，不能连续重试旧余额', async () => {
    const w=mount(); await flushPromises(); await w.vm.openManual(); await w.vm.loadManualContext(15)
    Object.assign(w.vm.manual,{ amount:10,confirmed:true }); mktWithdrawalApi.manual.mockRejectedValueOnce(new Error('余额已变化'))
    await w.vm.submitManual(); expect(w.vm.manual.error).toBe('余额已变化'); expect(w.vm.manual.confirmed).toBe(false)
    w.vm.manual.confirmed=true; await w.vm.submitManual(); expect(mktWithdrawalApi.manual).toHaveBeenCalledTimes(1)
  })
})

it('审批必须服务器明确允许，并核对最新伙伴金额收款信息', async () => {
  const w=mount(); await flushPromises(); await w.vm.approve({ id:8,status:1 })
  expect(mktWithdrawalApi.get).not.toHaveBeenCalled()
  mktWithdrawalApi.get.mockResolvedValueOnce({ id:8,canApprove:false })
  await w.vm.approve({ id:8,canApprove:true }); expect(mktWithdrawalApi.approve).not.toHaveBeenCalled()
  mktWithdrawalApi.get.mockResolvedValueOnce({ id:9,canApprove:true })
  await w.vm.approve({ id:8,canApprove:true }); expect(mktWithdrawalApi.approve).not.toHaveBeenCalled()
  mktWithdrawalApi.get.mockResolvedValueOnce({ id:8,promoterId:15,promoterName:'谢益长',promoterPhone:'137****3664',accountName:'谢益长',accountTail:'5678',bankName:'测试银行',amount:55.95,taxAmount:0,netAmount:55.95,canApprove:true })
  ElMessageBox.confirm.mockResolvedValueOnce('confirm'); mktWithdrawalApi.approve.mockResolvedValueOnce({})
  await w.vm.approve({ id:8,canApprove:true })
  expect(ElMessageBox.confirm).toHaveBeenCalledWith(expect.stringContaining('尾号 5678'),expect.any(String),expect.any(Object))
  expect(mktWithdrawalApi.approve).toHaveBeenCalledWith(8)
})
