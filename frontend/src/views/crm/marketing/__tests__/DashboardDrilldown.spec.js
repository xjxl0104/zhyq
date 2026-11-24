import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Dashboard from '../Dashboard.vue'
import { buildDashboardDrilldownCards } from '../dashboardDrilldown'
import { mktDashboardApi } from '@/api/marketing'

const pushMock = vi.fn()

vi.mock('vue-router', () => ({ useRouter: () => ({ push: pushMock }) }))
vi.mock('echarts', () => ({
  init: () => ({ setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn() })
}))
vi.mock('@/api/marketing', () => ({
  mktDashboardApi: {
    summary: vi.fn(),
    funnel: vi.fn(),
    trend: vi.fn()
  }
}))

const __dirname = dirname(fileURLToPath(import.meta.url))
const wrappers = []
function mount() {
  const w = shallowMount(Dashboard, {
    global: {
      stubs: {
        ArrowRight: true,
        ElDrawer: { props: ['modelValue', 'title'], template: '<div v-if="modelValue" class="drawer-stub"><slot /></div>' },
        ElTag: { template: '<span><slot /></span>' },
        ElButton: { template: '<button @click="$emit(\'click\')"><slot /></button>' }
      }
    }
  })
  wrappers.push(w)
  return w
}

beforeEach(() => {
  vi.clearAllMocks()
  mktDashboardApi.funnel.mockResolvedValue([])
  mktDashboardApi.trend.mockResolvedValue([])
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('营销看板 4 张经营分析结论卡与 L1→L2→L3 三层下钻', () => {
  it('后端 drilldown 为空时优雅兜底为 0，不抛错且不留空壳', () => {
    const cards = buildDashboardDrilldownCards({}, '近 30 天')
    expect(cards.map(c => c.key)).toEqual(['margin', 'reconcile', 'nonOutbound', 'lockConversion'])
    expect(cards[0].primaryText).toBe('¥0.00')
    expect(cards[1].primaryText).toBe('0.0%')
    expect(cards[2].primaryText).toBe('¥0.00')
    expect(cards[3].primaryText).toBe('0.0%')
    for (const c of cards) {
      expect(c.rows.length).toBeGreaterThan(0)
      for (const r of c.rows) {
        expect(r.evidence.raw).toBeTruthy()
        expect(r.evidence.source).toBeTruthy()
        expect(r.evidence.to).toBeTruthy()
      }
    }
  })

  it('同源搬运后端 summary.drilldown 字段，点开下钻抽屉展开 L3 物证并可跳转对应台账', async () => {
    mktDashboardApi.summary.mockResolvedValue({
      serviceFee: 10000,
      commissionPaid: 1200,
      outboundOrders: 500,
      referrals: 20,
      activePromoters: 8,
      pendingWithdrawals: 1,
      drilldown: {
        serviceFeeTotal: 10000,
        warehouseSettlementCost: 6000,
        validCommissionTotal: 1500,
        netRetainedMargin: 2500,
        marginRatePct: 25,
        yunshanActiveMaps: 4,
        yunshanTotalTickets: 500,
        erpLiveWarehouses: 2,
        erpDiffSnapshots: 1,
        erpUnmappedPending: 0,
        nonOutboundSettleableAmount: 3800.5,
        nonOutboundSettleableCount: 3,
        activeLocksCount: 6,
        dealLocksCount: 4,
        releasedLocksCount: 10
      }
    })

    const w = mount(); await flushPromises()
    expect(w.findAll('.drilldown-trigger')).toHaveLength(4)
    expect(w.vm.analysisCards[0].primaryText).toBe('¥2,500.00')
    expect(w.vm.analysisCards[2].primaryText).toBe('¥3,800.50')
    expect(w.vm.analysisCards[3].primaryText).toBe('20.0%')

    // 打开第 3 张「非出库待结算佣金池」下钻抽屉
    w.vm.openDrilldown('nonOutbound')
    await flushPromises()
    expect(w.vm.drilldown.visible).toBe(true)
    expect(w.vm.activeSpec.title).toBe('非出库待结算佣金池')
    // 默认首条判据已展开 L3 物证
    const firstRowKey = w.vm.activeSpec.rows[0].key
    expect(w.vm.expandedRowKeys).toContain(firstRowKey)
    expect(w.text()).toContain('① 原始接口返回数值')
    expect(w.text()).toContain('nonOutboundSettleableAmount')

    // 点击站内台账直达按钮关闭抽屉并路由跳转至非出库佣金视图
    w.vm.goLedger(w.vm.activeSpec.rows[0].evidence.to)
    expect(w.vm.drilldown.visible).toBe(false)
    expect(pushMock).toHaveBeenCalledWith({ path: '/crm/marketing/commission', query: { view: 'nonOutbound' } })
  })

  it('反向约束守卫：纯流水表、表单与设置字典页严禁挂载「下钻 ›」入口', () => {
    const pureRecordFiles = [
      '../Erp.vue',
      '../GeneralErpPanel.vue',
      '../ReferralOrder.vue',
      '../Commission.vue',
      '../Promoter.vue',
      '../Customer.vue',
      '../Withdrawal.vue',
      '../Setting.vue',
      '../Position.vue',
      '../Grade.vue',
      '../AuditLog.vue'
    ]
    for (const rel of pureRecordFiles) {
      const content = readFileSync(resolve(__dirname, rel), 'utf8')
      expect(content).not.toContain('drilldown-trigger')
      expect(content).not.toContain('下钻 ›')
    }
  })
})
