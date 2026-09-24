import { shallowMount, flushPromises } from '@vue/test-utils'
import { it, expect, vi, beforeEach, afterEach } from 'vitest'
import Customer from '../Customer.vue'
import { customerApi } from '@/api/crm'
const {permissions}=vi.hoisted(()=>({permissions:new Set()}))
vi.mock('@/utils/permission',()=>({hasPermission:p=>permissions.has(p)}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
vi.mock('@/api/crm',()=>({customerApi:{page:vi.fn(),add:vi.fn(),update:vi.fn()}}))
vi.mock('@/api/file',()=>({fileApi:{list:vi.fn().mockResolvedValue([])}}))
const wrappers=[]
function mount(){const w=shallowMount(Customer,{global:{stubs:{Search:true,Plus:true,FileUpload:true,ElDialog:{template:'<div><slot /></div>'},ElForm:{template:'<div><slot /></div>',methods:{validate:()=>Promise.resolve(true)}}}}});wrappers.push(w);return w}
beforeEach(()=>{permissions.clear();vi.clearAllMocks();customerApi.page.mockResolvedValue({records:[],total:0})})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
it('仅查询权限不展示新增且不能打开编辑，普通 CRM 编辑不借用营销权限',async()=>{
  permissions.add('crm:customer:query');permissions.add('crm:marketing:customer:edit')
  const w=mount();await flushPromises();expect(w.vm.canAdd).toBe(false);expect(w.vm.canEdit).toBe(false);expect(w.vm.canDelete).toBe(false)
  await w.vm.openDialog();expect(w.vm.dialog.visible).toBe(false);await w.vm.openDialog({id:1});expect(w.vm.dialog.visible).toBe(false)
})
it('营销客户只编辑档案，不能借通用状态恢复或发送营销归属字段',async()=>{
  permissions.add('crm:customer:edit');permissions.add('crm:marketing:customer:query')
  const w=mount();await flushPromises();await w.vm.openDialog({id:1,name:'营销客户',status:3,projectId:null,referrerId:90,assignedWarehouseId:20,grade:'A'})
  expect(w.vm.marketingCustomer).toBe(true);expect(w.vm.canMarketingQuery).toBe(true);expect(w.vm.form.status).toBe(3);expect(w.vm.form.referrerId).toBeUndefined()
  await w.vm.submit();expect(customerApi.update).toHaveBeenCalledWith(expect.objectContaining({id:1,status:3,projectId:null}));expect(customerApi.update.mock.calls[0][0]).not.toHaveProperty('assignedWarehouseId')
})
it('普通客户保留跟进状态编辑能力',async()=>{
  permissions.add('crm:customer:edit');const w=mount();await flushPromises();await w.vm.openDialog({id:2,name:'普通客户',status:3,projectId:7});expect(w.vm.marketingCustomer).toBe(false)
  w.vm.form.status=1;await w.vm.submit();expect(customerApi.update).toHaveBeenCalledWith(expect.objectContaining({id:2,status:1,projectId:7}))
})
