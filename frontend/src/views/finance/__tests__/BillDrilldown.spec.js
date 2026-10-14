import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import Bill from '../Bill.vue'
import { billApi } from '@/api/finance'

const route = vi.hoisted(() => ({ path: '/finance/bill', query: {} }))
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ replace: vi.fn() })
}))
vi.mock('@/api/finance', () => ({
  billApi: { page: vi.fn(), stats: vi.fn(), capabilities: vi.fn() },
  paymentApi: {}, invoiceApi: {}
}))
vi.mock('@/api/tenant', () => ({ tenantApi: { list: vi.fn().mockResolvedValue([]) } }))

const wrappers = []
const requests = []
function mountPage() {
  const wrapper = shallowMount(Bill, {
    global: { stubs: {
      Search: true, Money: true,
      ElAlert: { template: '<div><slot name="title" /></div>' },
      ElForm: { template: '<form><slot /></form>' },
      ElFormItem: { template: '<div><slot /></div>' },
      ElButton: { template: '<button><slot /></button>' },
      ElTag: { props: { closable: Boolean }, emits: ['close'], template: '<span><slot /><button v-if="closable" aria-label="取消仅未结清应收" @click="$emit(\'close\')">×</button></span>' }
    } }
  })
  wrappers.push(wrapper)
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  route.query = {}
  requests.length = 0
  billApi.page.mockImplementation(params => {
    requests.push({ ...params })
    return Promise.resolve({ records: [], total: 0 })
  })
  billApi.stats.mockResolvedValue({})
  billApi.capabilities.mockResolvedValue({})
})
afterEach(() => {
  wrappers.splice(0).forEach(wrapper => wrapper.unmount())
  vi.restoreAllMocks()
})

describe('财务总览进入账单明细', () => {
  it('首次请求即带总览选定的租客、费用与到期条件', async () => {
    route.query = { tenantRefId: '23', feeType: '租金', onlyDue: 'true', onlyOutstanding: 'true', direction: '1' }
    mountPage()
    await flushPromises()
    expect(requests).toHaveLength(1)
    expect(requests[0]).toMatchObject({
      pageNo: 1, tenantRefId: 23, feeType: '租金', onlyDue: true, onlyOutstanding: true, direction: 1, billId: null
    })
  })

  it('合同收款全景按合同进入账单，关闭合同标签后恢复全部账单', async () => {
    route.query = { contractId: '7', contractCode: 'HT-007', direction: '1' }
    const wrapper = mountPage()
    await flushPromises()
    expect(requests[0]).toMatchObject({ pageNo: 1, contractId: 7, direction: 1, tenantRefId: null })
    expect(wrapper.text()).toContain('仅合同 HT-007')
    await wrapper.get('[aria-label="取消仅未结清应收"]').trigger('click')
    await flushPromises()
    expect(requests.at(-1)).toMatchObject({ contractId: null, direction: 1 })
    expect(wrapper.text()).not.toContain('仅合同')
  })

  it.each(['0', '-1', 'abc'])('忽略无效合同 ID %s', async contractId => {
    route.query = { contractId, contractCode: 'HT-X' }
    const wrapper = mountPage()
    await flushPromises()
    expect(requests[0].contractId).toBeNull()
    expect(wrapper.text()).not.toContain('仅合同')
  })

  it('保证金类别与 onlyDue=1 可以传到现有账单筛选接口', async () => {
    route.query = { feeType: '保证金', onlyDue: '1' }
    mountPage()
    await flushPromises()
    expect(requests[0]).toMatchObject({ tenantRefId: null, feeType: '保证金', onlyDue: true })
  })

  it.each(['false', '0', undefined])('onlyDue=%s 不会因字符串非空而误筛到期账单', async onlyDue => {
    route.query = { onlyDue }
    mountPage()
    await flushPromises()
    expect(requests[0].onlyDue).toBe(false)
  })

  it.each(['false', '0', undefined])('onlyOutstanding=%s 不误开启未结清应收筛选', async onlyOutstanding => {
    route.query = { onlyOutstanding }
    const wrapper = mountPage()
    await flushPromises()
    expect(requests[0].onlyOutstanding).toBe(false)
    expect(wrapper.find('[aria-label="取消仅未结清应收"]').exists()).toBe(false)
  })

  it('关闭未结清应收限制时保留租客、费用、方向和到期条件', async () => {
    route.query = { tenantRefId: '23', feeType: '保证金', onlyDue: 'true', onlyOutstanding: '1', direction: '1' }
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('仅未结清应收')
    expect(requests[0].onlyOutstanding).toBe(true)
    await wrapper.get('[aria-label="取消仅未结清应收"]').trigger('click')
    await flushPromises()
    expect(requests.at(-1)).toMatchObject({
      pageNo: 1, tenantRefId: 23, feeType: '保证金', onlyDue: true, onlyOutstanding: false, direction: 1
    })
    expect(wrapper.find('[aria-label="取消仅未结清应收"]').exists()).toBe(false)
  })

  it('清空筛选会一起移除待收范围和其他总览条件', async () => {
    route.query = { tenantRefId: '23', feeType: '保证金', onlyDue: 'true', onlyOutstanding: 'true', direction: '1' }
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '清空筛选').trigger('click')
    await flushPromises()
    expect(requests.at(-1)).toMatchObject({
      pageNo: 1, tenantRefId: null, feeType: null, onlyDue: false, onlyOutstanding: false, direction: null, billId: null
    })
    expect(wrapper.find('[aria-label="取消仅未结清应收"]').exists()).toBe(false)
  })

  it.each([['1', 1], ['2', 2], ['3', null], ['invalid', null], [['1', '2'], null]])(
    '仅接收合法收付方向 %s', async (direction, expected) => {
      route.query = { direction }
      mountPage()
      await flushPromises()
      expect(requests[0].direction).toBe(expected)
    }
  )

  it.each(['-2', '1.5', 'NaN', '9007199254740993', ['3', '4']])('忽略无效租客 ID %s', async tenantRefId => {
    route.query = { tenantRefId, feeType: ['租金', '物业费'] }
    mountPage()
    await flushPromises()
    expect(requests[0]).toMatchObject({ tenantRefId: null, feeType: null })
  })

  it('保留关联账单定位，且单张定位优先于总览筛选条件', async () => {
    route.query = { billId: '17', tenantRefId: '23', feeType: '物业费', onlyDue: 'true', onlyOutstanding: 'true' }
    const wrapper = mountPage()
    await flushPromises()
    expect(requests[0]).toMatchObject({ billId: 17, tenantRefId: null, feeType: null, onlyDue: false, onlyOutstanding: false })
    expect(wrapper.text()).toContain('仅显示关联账单 #17')
  })
})
