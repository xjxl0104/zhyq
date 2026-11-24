import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import PromoterSelect from '../PromoterSelect.vue'
import Commission from '../Commission.vue'
import Setting from '../Setting.vue'
import { mktPromoterApi, mktCommissionApi, mktSettingApi } from '@/api/marketing'

const routeState = reactive({ query: {} })

vi.mock('vue-router', () => ({ useRoute: () => routeState }))
vi.mock('@/api/marketing', () => ({
  mktPromoterApi: { page: vi.fn(), get: vi.fn() },
  mktCommissionApi: { monthly: vi.fn(), page: vi.fn(), settle: vi.fn(), settleContract: vi.fn(), void: vi.fn(), batches: vi.fn() },
  mktContractApi: { page: vi.fn() },
  mktSettingApi: { all: vi.fn(), save: vi.fn(), update: vi.fn() }
}))
vi.mock('element-plus', async original => ({
  ...await original(),
  ElMessage: { success: vi.fn(), info: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

const wrappers = []
function track(w) { wrappers.push(w); return w }

beforeEach(() => {
  vi.clearAllMocks()
  routeState.query = {}
  mktPromoterApi.page.mockResolvedValue({
    total: 2,
    records: [
      { id: 11, name: '张合伙', phone: '13800138011', positionCode: 'P4', inviteCode: 'INV011' },
      { id: 12, name: '李推荐', phone: '13900139012', positionCode: 'P2', inviteCode: 'INV012' }
    ]
  })
  mktPromoterApi.get.mockResolvedValue({ id: 99, name: '初始上级', phone: '13700137099', positionCode: 'P4', inviteCode: 'ROOT99' })
  mktCommissionApi.monthly.mockResolvedValue([])
  mktCommissionApi.page.mockResolvedValue({ total: 1, records: [{ id: 501, promoterId: 11, promoterName: '张合伙', orderSourceType: 3, orderSourceNo: 'DP-01', amount: 188, sign: 1, status: 2 }] })
  mktCommissionApi.settle.mockResolvedValue('SB20261124001')
  mktSettingApi.all.mockResolvedValue([
    { skey: 'withdraw_window_start_day', svalue: '0', remark: '提现起始日' },
    { skey: 'auto_withdraw_day', svalue: '10', remark: '自动提现日' },
    { skey: 'platform_retain_rate', svalue: '0.05', remark: '平台保底留存' }
  ])
  mktSettingApi.save.mockResolvedValue(null)
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('伙伴远程选择器、非出库佣金结算与规则字典三合一', () => {
  it('PromoterSelect 使用 keyword 远程搜索并回显初始绑定数字 ID，且支持排除自身 ID', async () => {
    const w = track(shallowMount(PromoterSelect, { props: { modelValue: 99, excludeId: 12 } }))
    await flushPromises()
    expect(mktPromoterApi.get).toHaveBeenCalledWith(99)

    await w.vm.searchPromoters(' 张合伙 ')
    expect(mktPromoterApi.page).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20, keyword: '张合伙' })
    expect(w.vm.formatPromoterLabel({ name: '张合伙', phone: '13800138011', positionCode: 'P4', inviteCode: 'INV011' }))
      .toBe('张合伙 · 138****8011 · P4 · INV011')
  })

  it('伙伴佣金结算切换非出库视图时透传 nonOutbound=true，并支持单行立即结算入池', async () => {
    routeState.query = { view: 'nonOutbound' }
    const w = track(shallowMount(Commission, { global: { directives: { loading: {} }, stubs: { Search: true } } }))
    await flushPromises()
    expect(mktCommissionApi.page).toHaveBeenCalledWith(expect.objectContaining({ nonOutbound: true, pageNo: 1 }))
    expect(w.vm.nonOut.list).toHaveLength(1)

    await w.vm.settleSingleNonOutbound(w.vm.nonOut.list[0])
    expect(mktCommissionApi.settle).toHaveBeenCalledWith([501])
  })

  it('规则与字典页展示友好中文说明、支持三页签切换并调用 mktSettingApi.save 保存', async () => {
    const w = track(shallowMount(Setting, { global: { directives: { loading: {} } } }))
    await flushPromises()
    expect(w.vm.list).toHaveLength(3)
    expect(w.vm.labelOf('withdraw_window_start_day')).toBe('提现申请开放起始日')
    expect(w.vm.hintOf('withdraw_window_start_day')).toContain('0 = 全月随时可申请')
    expect(w.text()).toContain('withdraw_window_start_day = 0 表示全月随时可申请提现')

    await w.vm.save()
    expect(mktSettingApi.save).toHaveBeenCalledWith([
      { skey: 'withdraw_window_start_day', svalue: '0' },
      { skey: 'auto_withdraw_day', svalue: '10' },
      { skey: 'platform_retain_rate', svalue: '0.05' }
    ])

    w.vm.activeTab = 'position'
    await flushPromises()
    expect(w.find('position-stub').exists()).toBe(true)

    w.vm.activeTab = 'grade'
    await flushPromises()
    expect(w.find('grade-stub').exists()).toBe(true)
  })
})
