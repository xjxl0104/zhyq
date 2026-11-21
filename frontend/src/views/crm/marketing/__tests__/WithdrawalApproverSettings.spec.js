import { shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import WithdrawalApproverSettings from '../WithdrawalApproverSettings.vue'
import { mktWithdrawalApi } from '@/api/marketing'
import { userApi } from '@/api/system'
const { permissions }=vi.hoisted(()=>({permissions:new Set()}))
vi.mock('@/utils/permission',()=>({hasPermission:p=>permissions.has(p)}))
vi.mock('@/api/marketing',()=>({mktWithdrawalApi:{approvalIdentity:vi.fn(),saveApprovalIdentity:vi.fn(),manualOptions:vi.fn(),manualContext:vi.fn()}}))
vi.mock('@/api/system',()=>({userApi:{list:vi.fn()}}))
const wrappers=[]
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
function mount(){const w=shallowMount(WithdrawalApproverSettings,{global:{stubs:{RouterLink:true,ElDialog:{template:'<div><slot /><slot name="footer" /></div>'}},directives:{loading:()=>{}}}});wrappers.push(w);return w}
beforeEach(()=>{
  vi.clearAllMocks();permissions.clear();permissions.add('ROLE_admin')
  userApi.list.mockResolvedValue([{id:5,username:'reviewer',nickname:'杨洪',phone:'13800138000'}])
  mktWithdrawalApi.approvalIdentity.mockResolvedValue({username:'reviewer',promoterId:'16',approverName:'杨洪'})
  mktWithdrawalApi.manualContext.mockResolvedValue({promoter:{id:16,name:'杨洪',phone:'138****8000'}})
  mktWithdrawalApi.manualOptions.mockResolvedValue([{id:20,name:'另一个伙伴',phone:'139****0000'}])
  mktWithdrawalApi.saveApprovalIdentity.mockResolvedValue({})
})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
it('非管理员不能读取或修改审批身份',async()=>{
  permissions.clear();const w=mount();await w.vm.open();await w.vm.save()
  expect(mktWithdrawalApi.approvalIdentity).not.toHaveBeenCalled();expect(mktWithdrawalApi.saveApprovalIdentity).not.toHaveBeenCalled()
})
it('同时展示后台员工与小程序伙伴，必须确认是同一人才保存',async()=>{
  const w=mount();await w.vm.open();expect(w.vm.selectedUser.username).toBe('reviewer');expect(w.vm.selectedPartner.id).toBe(16)
  expect(w.vm.maskPhone('13800138000')).toBe('138****8000')
  await w.vm.save();expect(mktWithdrawalApi.saveApprovalIdentity).not.toHaveBeenCalled()
  w.vm.form.confirmed=true;await w.vm.save()
  expect(mktWithdrawalApi.saveApprovalIdentity).toHaveBeenCalledWith({username:'reviewer',promoterId:16});expect(w.vm.form.visible).toBe(false)
})
it('伙伴搜索保留当前选中身份，保存失败重置核对确认',async()=>{
  const w=mount();await w.vm.open();await w.vm.searchPartners(' 另一个 ')
  expect(mktWithdrawalApi.manualOptions).toHaveBeenCalledWith('另一个');expect(w.vm.selectedPartner.id).toBe(16)
  mktWithdrawalApi.saveApprovalIdentity.mockRejectedValueOnce(new Error('请先指定审批流程'))
  w.vm.form.confirmed=true;await w.vm.save();expect(w.vm.form.error).toContain('审批流程');expect(w.vm.form.confirmed).toBe(false)
})
it('原绑定伙伴不存在时仍允许管理员选择有效身份修复配置',async()=>{
  mktWithdrawalApi.manualContext.mockRejectedValueOnce(new Error('伙伴不存在'))
  const w=mount();await w.vm.open();expect(w.vm.selectedUser.username).toBe('reviewer');expect(w.vm.form.error).toContain('原审批伙伴')
  await w.vm.searchPartners('另一个');w.vm.form.promoterId=20;w.vm.form.confirmed=true;await w.vm.save()
  expect(mktWithdrawalApi.saveApprovalIdentity).toHaveBeenCalledWith({username:'reviewer',promoterId:20})
})
it('提交时不能重复保存或关闭窗口，迟到的读取不能恢复关闭窗口资料',async()=>{
  const w=mount();await w.vm.open();w.vm.form.confirmed=true
  const pending=deferred();mktWithdrawalApi.saveApprovalIdentity.mockReturnValueOnce(pending.promise)
  const saving=w.vm.save();w.vm.close();await w.vm.save();expect(w.vm.form.visible).toBe(true);expect(mktWithdrawalApi.saveApprovalIdentity).toHaveBeenCalledTimes(1)
  pending.resolve({});await saving
  const late=deferred();mktWithdrawalApi.approvalIdentity.mockReturnValueOnce(late.promise)
  const loading=w.vm.open();w.vm.close();late.resolve({username:'reviewer',promoterId:'16'});await loading
  expect(w.vm.form.visible).toBe(false);expect(w.vm.form.users).toEqual([])
})
