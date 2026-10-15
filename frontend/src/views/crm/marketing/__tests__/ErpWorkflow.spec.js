import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Erp from '../Erp.vue'
import { mktErpApi, mktWarehouseApi, mktYunshanApi } from '@/api/marketing'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/marketing', () => ({
  mktErpApi: Object.fromEntries(['get','mappings','logs','exceptions','issue','resetSecret','update','saveMapping','deleteMapping','retry'].map(k => [k,vi.fn()])),
  mktWarehouseApi: {page:vi.fn(),get:vi.fn(),confirmErp:vi.fn()},
  mktYunshanApi: {localWarehouses:vi.fn(),localWarehouse:vi.fn()}
}))
vi.mock('@/utils/permission', () => ({hasPermission:() => true}))
vi.mock('vue-router', () => ({useRoute:() => ({query:{warehouseId:'9'}}), useRouter:() => ({push:vi.fn()})}))
vi.mock('element-plus', async (importOriginal) => ({...(await importOriginal()),ElMessage:{success:vi.fn(),error:vi.fn()},ElMessageBox:{confirm:vi.fn(),prompt:vi.fn()}}))
vi.mock('../ErpReconciliation.vue', () => ({default:{template:'<div />'}}))
vi.mock('../YunshanVolumePanel.vue', () => ({default:{template:'<div />'}}))

const pending = () => {let resolve; const promise = new Promise(r => {resolve=r});return {promise,resolve}}
const wrappers=[]
function mount(){const w=shallowMount(Erp,{global:{stubs:{ElForm:true,ElDialog:true}}});wrappers.push(w);return w}
beforeEach(() => {
  vi.clearAllMocks()
  mktYunshanApi.localWarehouses.mockResolvedValue({records:[{id:'9',name:'测试仓',code:'WH9'}],total:1})
  mktYunshanApi.localWarehouse.mockImplementation(id=>Promise.resolve({id,erpStatus:2,joinStatus:5,orderMode:'erp'}))
  mktErpApi.get.mockImplementation(id=>Promise.resolve({warehouseId:id,appId:`APP${id}`,env:2,status:1}))
  mktErpApi.mappings.mockResolvedValue([])
  mktErpApi.logs.mockResolvedValue({records:[],total:0})
  mktErpApi.exceptions.mockResolvedValue({records:[],total:0})
  mktErpApi.saveMapping.mockResolvedValue({queued:2})
  mktErpApi.retry.mockResolvedValue({})
  ElMessageBox.prompt.mockResolvedValue({value:'映射已核对'})
})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))

describe('ERP 维护操作绑定当前云仓与原事件',()=>{
  it('保存货主映射后刷新补处理队列，不要求ERP创建新事件',async()=>{
    const w=mount();await flushPromises()
    w.vm.openMapping();Object.assign(w.vm.mapDialog,{customerId:42,customerCode:' OWNER-A '})
    await w.vm.saveMapping()
    expect(mktErpApi.saveMapping).toHaveBeenCalledWith('9',{customerId:42,customerCode:'OWNER-A'})
    expect(mktErpApi.exceptions).toHaveBeenCalledTimes(2)
    expect(w.vm.mapDialog.visible).toBe(false)
  })
  it('切换云仓后旧凭证响应不能覆盖新仓或重新显示密钥',async()=>{
    const w=mount();await flushPromises()
    const old=pending();mktErpApi.get.mockImplementation(id=>id==='9'?old.promise:Promise.resolve({warehouseId:id,appId:'NEW',env:1,status:1}))
    const first=w.vm.load();w.vm.warehouseId='10';const second=w.vm.changeWarehouse()
    await second;old.resolve({warehouseId:9,appId:'OLD',secret:'old-secret'});await first
    expect(w.vm.cred.appId).toBe('NEW');expect(w.vm.secret).toBe('')
  })
  it('映射弹框不能意外提交给另一个仓',async()=>{
    const w=mount();await flushPromises();w.vm.openMapping()
    Object.assign(w.vm.mapDialog,{customerId:42,customerCode:'A'});w.vm.warehouseId='10'
    await w.vm.saveMapping();expect(mktErpApi.saveMapping).not.toHaveBeenCalled()
  })
  it('重试提交原事件ID和原因，等待期间不重复提交',async()=>{
    const w=mount();await flushPromises();const reply=pending();mktErpApi.retry.mockReturnValue(reply.promise)
    const first=w.vm.retryEvent({id:77});await flushPromises();await w.vm.retryEvent({id:77})
    expect(mktErpApi.retry).toHaveBeenCalledTimes(1)
    expect(mktErpApi.retry).toHaveBeenCalledWith('9',77,{reason:'映射已核对'})
    reply.resolve({});await first;expect(w.vm.busy).toBe(false)
  })
})
