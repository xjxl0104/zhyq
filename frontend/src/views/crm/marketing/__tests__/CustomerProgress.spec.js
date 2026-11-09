import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Customer from '../Customer.vue'
import { mktCustomerApi } from '@/api/marketing'
vi.mock('@/utils/permission', () => ({ hasPermission: () => true }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ projects:[], currentProjectId:1 }) }))
vi.mock('@/api/marketing', () => ({ mktCustomerApi:{ page:vi.fn(), get:vi.fn(), progress:vi.fn() }, mktWarehouseApi:{}, mktCompetitionProtectionApi:{} }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal(), ElMessage:{ success:vi.fn(), error:vi.fn() } }))
const row = { id:17, name:'客户甲', status:1, progressPercentage:30, publicProgress:'已分派云仓', progressHistory:[{ id:1, percentage:30, summary:'已分派云仓', createdAt:'2026-11-09T10:00:00' }] }
let wrapper
beforeEach(() => {
  vi.clearAllMocks()
  mktCustomerApi.page.mockResolvedValue({ records:[row], total:1 })
  mktCustomerApi.get.mockResolvedValue(row)
  mktCustomerApi.progress.mockResolvedValue(null)
  wrapper = shallowMount(Customer, { global:{ stubs:{ Search:true } } })
})
afterEach(() => wrapper.unmount())
describe('客户跟进进度与历史', () => {
  it('每次打开读取最新比例和历史，发布后重新读取并保留记录窗口', async () => {
    await flushPromises(); await wrapper.vm.openProgress(row)
    expect(wrapper.vm.progress).toMatchObject({ percentage:30, summary:'已分派云仓', history:row.progressHistory, ready:true })
    wrapper.vm.progress.percentage = 75; wrapper.vm.progress.summary = ' 已发送报价 '
    const latest = { ...row, progressPercentage:75, publicProgress:'已发送报价', progressHistory:[{ id:2, percentage:75, summary:'已发送报价' }, ...row.progressHistory] }
    mktCustomerApi.get.mockResolvedValue(latest)
    await wrapper.vm.saveProgress()
    expect(mktCustomerApi.progress).toHaveBeenCalledWith(17, { percentage:75, summary:'已发送报价' })
    expect(wrapper.vm.progress).toMatchObject({ visible:true, percentage:75, history:latest.progressHistory })
    expect(mktCustomerApi.get).toHaveBeenCalledTimes(2)
  })
  it('失败保留说明与比例，未加载完成不能发送', async () => {
    await flushPromises(); await wrapper.vm.saveProgress()
    expect(mktCustomerApi.progress).not.toHaveBeenCalled()
    await wrapper.vm.openProgress(row)
    wrapper.vm.progress.summary = '已联系客户'; wrapper.vm.progress.percentage = 45
    mktCustomerApi.progress.mockRejectedValue(new Error('状态已变化'))
    await wrapper.vm.saveProgress()
    expect(wrapper.vm.progress).toMatchObject({ visible:true, percentage:45, summary:'已联系客户', error:'状态已变化', busy:false })
  })
  it('正式签约客户显示 100%，并标记不可手工降低', async () => {
    mktCustomerApi.get.mockResolvedValue({ ...row, status:2, progressPercentage:100 })
    await wrapper.vm.openProgress(row)
    expect(wrapper.vm.progress).toMatchObject({ percentage:100, signed:true })
  })
})
