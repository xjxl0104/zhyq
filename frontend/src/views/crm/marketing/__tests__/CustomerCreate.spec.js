import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, describe, it, expect, vi } from 'vitest'
import CustomerCreateDialog from '../CustomerCreateDialog.vue'
import { emptyRequirements, validateRequirements, requirementsRows } from '../customerRequirements'
import { mktCustomerApi, mktWarehouseApi } from '@/api/marketing'
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ currentProjectId:1 }) }))
vi.mock('@/api/marketing', () => ({ mktCustomerApi:{ create:vi.fn(), referrers:vi.fn() }, mktWarehouseApi:{ online:vi.fn() } }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal(), ElMessage:{ success:vi.fn() } }))
beforeEach(() => {
  vi.clearAllMocks()
  mktCustomerApi.create.mockResolvedValue({ customerId:42 })
  mktCustomerApi.referrers.mockResolvedValue([])
  mktWarehouseApi.online.mockResolvedValue([])
})
describe('后台新增客户', () => {
  it('保存品类多选和业务规模区间，不提交已移除的产品种类数默认字段', async () => {
    const wrapper = shallowMount(CustomerCreateDialog, { props:{ modelValue:false } })
    await wrapper.setProps({ modelValue:true }); await flushPromises()
    Object.assign(wrapper.vm.form, { name:'分类客户', requirements:{ ...emptyRequirements(), productCategories:['食品饮料','其他'], productCategoryOther:'户外装备', skuCount:'20-50', dailyOrders:'200-1000', itemsPerParcelRange:'2-5' } })
    await wrapper.vm.submit()
    const payload = mktCustomerApi.create.mock.calls[0][0]
    expect(payload.requirements).toMatchObject({ productCategories:['食品饮料','其他'], productCategoryOther:'户外装备', skuCount:'20-50', dailyOrders:'200-1000', itemsPerParcelRange:'2-5' })
    expect(payload.requirements).not.toHaveProperty('productCount')
    wrapper.unmount()
  })
  it('空手机可以保存完整需求，无伙伴为园区直接客户', async () => {
    const wrapper = shallowMount(CustomerCreateDialog, { props:{ modelValue:false } })
    await wrapper.setProps({ modelValue:true }); await flushPromises()
    Object.assign(wrapper.vm.form, { name:' 客户甲 ', phone:' ', requirements:{ ...emptyRequirements(), salesPlatforms:['其他'], salesPlatformOther:'独立站', invoiceRequired:false } })
    await wrapper.vm.submit()
    expect(mktCustomerApi.create).toHaveBeenCalledWith(expect.objectContaining({ name:'客户甲', phone:null, referrerId:null, projectId:1, requirements:expect.objectContaining({ salesPlatformOther:'独立站', invoiceRequired:false }) }))
    expect(wrapper.emitted('created')).toHaveLength(1)
    wrapper.unmount()
  })
  it('无效手机、部分占比和其他未填写会拦截，提交失败保留表单可重试', async () => {
    const wrapper = shallowMount(CustomerCreateDialog, { props:{ modelValue:false } })
    await wrapper.setProps({ modelValue:true }); await flushPromises()
    Object.assign(wrapper.vm.form, { name:'客户甲', phone:'123' })
    await wrapper.vm.submit(); expect(mktCustomerApi.create).not.toHaveBeenCalled()
    wrapper.vm.form.phone = ''
    wrapper.vm.form.requirements.couriers = ['其他']
    await wrapper.vm.submit(); expect(mktCustomerApi.create).not.toHaveBeenCalled()
    wrapper.vm.form.requirements.courierOther = '本地配送'
    mktCustomerApi.create.mockRejectedValueOnce(new Error('客户已存在'))
    await wrapper.vm.submit()
    expect(wrapper.vm.error).toBe('客户已存在'); expect(wrapper.vm.form.name).toBe('客户甲')
    expect(wrapper.emitted('created')).toBeUndefined()
    wrapper.unmount()
  })
  it('重量占比可全部留空、填写后必须完整合计 100%，发票否不会显示未填写', () => {
    const value = emptyRequirements()
    value.productWeights = [{ range:'0-0.3', percentage:null }, { range:'>=3', percentage:null }]
    expect(validateRequirements(value)).toBe('')
    value.productWeights[0].percentage = 40
    expect(validateRequirements(value)).toContain('所有已选')
    value.productWeights[1].percentage = 50
    expect(validateRequirements(value)).toContain('100%')
    value.productWeights[1].percentage = 60
    expect(validateRequirements(value)).toBe('')
    expect(requirementsRows({ invoiceRequired:false })).toContainEqual(['是否开发票','否'])
  })
})
