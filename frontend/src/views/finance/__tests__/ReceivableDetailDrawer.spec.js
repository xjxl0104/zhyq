import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ReceivableDetailDrawer from '../components/ReceivableDetailDrawer.vue'
import { receivableApi } from '@/api/receivable'

vi.mock('@/api/receivable', () => ({
  receivableApi: {
    get: vi.fn(),
    revealAccount: vi.fn()
  }
}))

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push })
}))

describe('应收明细详情抽屉', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    receivableApi.get.mockResolvedValue({
      register: { id: 7, tenantNameRaw: '示例租户甲' },
      rules: [],
      deposits: [],
      bills: [{ id: 42, code: 'RR7V1R202601', feeType: '租金', amount: 100000, dueDate: '2026-01-05' }]
    })
  })

  it('已生成账单的账单号可点,跳到账单页定位该单并收起抽屉', async () => {
    const wrapper = mount(ReceivableDetailDrawer, {
      props: { modelValue: true, registerId: 7, canViewAccount: false },
      global: { plugins: [ElementPlus], stubs: { teleport: true, transition: false } }
    })
    await flushPromises()

    const link = wrapper.findAll('a').find(a => a.text().includes('RR7V1R202601'))
    expect(link).toBeTruthy()
    await link.trigger('click')

    expect(push).toHaveBeenCalledWith({ path: '/finance/bill', query: { billId: 42 } })
    // 跳转同时收起抽屉,回来不会顶着一个盖脸的旧抽屉
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })
})
