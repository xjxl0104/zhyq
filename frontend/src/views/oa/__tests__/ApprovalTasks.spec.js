import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, it, expect, vi } from 'vitest'
import { reactive, nextTick } from 'vue'
import Approval from '../Approval.vue'
import { approvalApi } from '@/api/oa'
import { wfTaskApi } from '@/api/workflow'
import { mktWithdrawalApi } from '@/api/marketing'
const { permissions, route }=vi.hoisted(()=>({permissions:new Set(),route:{query:{}}}))
vi.mock('vue-router',()=>({useRoute:()=>route}))
vi.mock('@/utils/permission',()=>({hasPermission:p=>permissions.has(p)}))
vi.mock('@/api/oa',()=>({approvalApi:{page:vi.fn(),stats:vi.fn(),approve:vi.fn(),reject:vi.fn(),remove:vi.fn()}}))
vi.mock('@/api/workflow',()=>({wfTaskApi:{myPending:vi.fn(),approve:vi.fn(),reject:vi.fn()}}))
vi.mock('@/api/marketing',()=>({mktWithdrawalApi:{get:vi.fn()}}))
const wrappers=[]
const todo={taskId:901,instanceId:301,bizType:'contract',bizId:17,seq:1,assignee:'reviewer',createTime:'2026-09-24 10:00:00'}
function mount(){const w=shallowMount(Approval,{global:{stubs:{Search:true,ElDialog:{template:'<div><slot /></div>'},MobileRecordList:false},directives:{loading:()=>{}}}});wrappers.push(w);return w}
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve}}
beforeEach(()=>{vi.clearAllMocks();permissions.clear();route.query=reactive({});wfTaskApi.myPending.mockResolvedValue([{...todo}]);wfTaskApi.approve.mockResolvedValue({});wfTaskApi.reject.mockResolvedValue({});approvalApi.page.mockResolvedValue({records:[],total:0});approvalApi.stats.mockResolvedValue({pending:0,approved:0,rejected:0,total:0});approvalApi.approve.mockResolvedValue({});approvalApi.reject.mockResolvedValue({})})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
it('仅获派用户无单头查询权限，仍可读取并办理我的任务',async()=>{
  const w=mount();await flushPromises();expect(w.vm.activeTab).toBe('tasks');expect(wfTaskApi.myPending).toHaveBeenCalledWith();expect(approvalApi.page).not.toHaveBeenCalled();expect(approvalApi.stats).not.toHaveBeenCalled()
  w.vm.openAudit(w.vm.list[0],'approve');w.vm.audit.opinion=' 已核对 ';await w.vm.submitAudit()
  expect(wfTaskApi.approve).toHaveBeenCalledWith(901,'已核对');expect(approvalApi.approve).not.toHaveBeenCalled();expect(wfTaskApi.myPending).toHaveBeenCalledTimes(2)
})
it('合同入口 bizType/bizId 精确过滤获派待办，其他业务的相同ID不能混入',async()=>{
  route.query=reactive({bizType:'contract',bizId:'17'});wfTaskApi.myPending.mockResolvedValue([todo,{...todo,taskId:902,bizId:18},{...todo,taskId:903,bizType:'budget'}]);const w=mount();await flushPromises();expect(w.vm.list.map(t=>t.taskId)).toEqual([901]);expect(w.vm.total).toBe(1)
})
it('单头与统计接口 403 不阻断任务审批；成功后分别刷新三种数据',async()=>{
  permissions.add('contract:query');approvalApi.page.mockRejectedValue(new Error('单头无权查看'));approvalApi.stats.mockRejectedValue(new Error('统计无权查看'));const w=mount();await flushPromises();expect(w.vm.list[0].taskId).toBe(901);expect(w.vm.taskError).toBe('')
  w.vm.openAudit(w.vm.list[0],'approve');await w.vm.submitAudit();expect(wfTaskApi.approve).toHaveBeenCalledWith(901,'');expect(approvalApi.page).toHaveBeenCalledTimes(2);expect(approvalApi.stats).toHaveBeenCalledTimes(2);expect(w.vm.saving).toBe(false)
})
it('驳回要求原因，按 taskId 驳回且保存中不重复处理',async()=>{
  const pending=deferred();wfTaskApi.reject.mockReturnValue(pending.promise);const w=mount();await flushPromises();w.vm.openAudit(w.vm.list[0],'reject');await w.vm.submitAudit();expect(wfTaskApi.reject).not.toHaveBeenCalled()
  w.vm.audit.opinion=' 资料缺失 ';const saving=w.vm.submitAudit();await w.vm.submitAudit();expect(wfTaskApi.reject).toHaveBeenCalledTimes(1);expect(wfTaskApi.reject).toHaveBeenCalledWith(901,'资料缺失');pending.resolve({});await saving
})
it('有审批链或无直接审批授权的单头不能触发原 OA 写接口',async()=>{
  permissions.add('ROLE_admin');const w=mount();await flushPromises();for(const permission of [undefined,false]){w.vm.openAudit({id:18,bizType:'contract',bizId:17,status:2,canDirectApprove:permission},'approve','direct');expect(w.vm.audit.visible).toBe(false)}
  await w.vm.submitAudit();expect(approvalApi.approve).not.toHaveBeenCalled();expect(wfTaskApi.approve).not.toHaveBeenCalled()
})
it('仅后端明确允许的无链历史单头保留直接审批并刷新待办/单头',async()=>{
  permissions.add('contract:query');const w=mount();await flushPromises();w.vm.openAudit({id:18,bizType:'contract',bizId:17,status:2,canDirectApprove:true},'approve','direct');w.vm.audit.opinion='历史单据核验';await w.vm.submitAudit();expect(approvalApi.approve).toHaveBeenCalledWith(18,{opinion:'历史单据核验'});expect(wfTaskApi.approve).not.toHaveBeenCalled();expect(approvalApi.page).toHaveBeenCalledTimes(2)
})
it('全部单据不再误带审批中状态，按业务ID服务端查询',async()=>{
  permissions.add('contract:query');route.query=reactive({bizType:'contract',bizId:'17'});const w=mount();await flushPromises();w.vm.activeTab='all';await w.vm.onTabChange('all');expect(approvalApi.page).toHaveBeenLastCalledWith(expect.objectContaining({bizType:'contract',bizId:17,status:null}));await w.vm.reset();expect(approvalApi.page).toHaveBeenLastCalledWith(expect.objectContaining({bizType:null,bizId:null,status:null}))
})
it('我的待办完整分页且请求乱序不会回滚任务列表',async()=>{
  const old=deferred();wfTaskApi.myPending.mockReturnValueOnce(old.promise).mockResolvedValueOnce(Array.from({length:25},(_,i)=>({...todo,taskId:1000+i,bizId:i+1})));const w=mount();await w.vm.loadTasks();old.resolve([todo]);await flushPromises();expect(w.vm.total).toBe(25);w.vm.query.pageNo=3;await nextTick();expect(w.vm.list.map(x=>x.taskId)).toEqual([1020,1021,1022,1023,1024])
})
it('提现待办必须读取伙伴和收款快照后才能审批，资料失败时禁止提交',async()=>{
  wfTaskApi.myPending.mockResolvedValue([{...todo,bizType:'withdrawal',bizId:8}])
  const response=deferred();mktWithdrawalApi.get.mockReturnValueOnce(response.promise)
  const w=mount();await flushPromises();const opening=w.vm.openAudit(w.vm.list[0],'approve')
  await w.vm.submitAudit();expect(wfTaskApi.approve).not.toHaveBeenCalled()
  response.resolve({id:8,promoterName:'谢益长',promoterPhone:'137****3664',accountName:'谢益长',accountTail:'5678',amount:55.95,netAmount:55.95,canApprove:true})
  await opening;expect(w.vm.audit.detail.accountTail).toBe('5678');await w.vm.submitAudit()
  expect(wfTaskApi.approve).toHaveBeenCalledWith(901,'')
  wfTaskApi.approve.mockClear();mktWithdrawalApi.get.mockRejectedValueOnce(new Error('资料不可用'))
  await w.vm.openAudit(w.vm.list[0],'approve');await w.vm.submitAudit()
  expect(w.vm.audit.detailError).toBe('资料不可用');expect(wfTaskApi.approve).not.toHaveBeenCalled()
})
it('提现资料返回错误单据或无审批权限，不允许使用旧待办审批',async()=>{
  wfTaskApi.myPending.mockResolvedValue([{...todo,bizType:'withdrawal',bizId:8}])
  const w=mount();await flushPromises();mktWithdrawalApi.get.mockResolvedValueOnce({id:9,canApprove:true})
  await w.vm.openAudit(w.vm.list[0],'approve');expect(w.vm.audit.detailError).toContain('不一致');await w.vm.submitAudit()
  mktWithdrawalApi.get.mockResolvedValueOnce({id:8,canApprove:false});await w.vm.openAudit(w.vm.list[0],'approve');await w.vm.submitAudit()
  expect(wfTaskApi.approve).not.toHaveBeenCalled()
})
it('从A提现单切换到B时迟到的A资料不能覆盖B',async()=>{
  wfTaskApi.myPending.mockResolvedValue([{...todo,bizType:'withdrawal',bizId:8},{...todo,taskId:902,bizType:'withdrawal',bizId:9}])
  const a=deferred(),b=deferred();mktWithdrawalApi.get.mockImplementation(id=>id===8?a.promise:b.promise)
  const w=mount();await flushPromises();const first=w.vm.openAudit(w.vm.list[0],'approve'),second=w.vm.openAudit(w.vm.list[1],'approve')
  b.resolve({id:9,canApprove:true});await second;a.resolve({id:8,canApprove:true});await first
  expect(w.vm.audit.detail.id).toBe(9);await w.vm.submitAudit();expect(wfTaskApi.approve).toHaveBeenCalledWith(902,'')
})
