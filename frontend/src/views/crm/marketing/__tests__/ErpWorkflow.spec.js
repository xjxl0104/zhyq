import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Erp from '../Erp.vue'
import { mktErpApi, mktWarehouseApi, mktYunshanApi } from '@/api/marketing'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/marketing', () => ({
  mktErpApi: Object.fromEntries(['get','mappings','logs','exceptions','issue','resetSecret','update','saveMapping','deleteMapping','retry'].map(k => [k,vi.fn()])),
  mktWarehouseApi: {page:vi.fn(),get:vi.fn(),confirmErp:vi.fn()},
  mktYunshanApi: {localWarehouses:vi.fn(),localWarehouse:vi.fn(),customers:vi.fn()}
}))
vi.mock('@/utils/permission', () => ({hasPermission:() => true}))
vi.mock('vue-router', () => ({useRoute:() => ({query:{warehouseId:'9'}}), useRouter:() => ({push:vi.fn()})}))
vi.mock('element-plus', async (importOriginal) => ({...(await importOriginal()),ElMessage:{success:vi.fn(),error:vi.fn()},ElMessageBox:{confirm:vi.fn(),prompt:vi.fn()}}))
vi.mock('../ErpReconciliation.vue', () => ({default:{template:'<div />'}}))
vi.mock('../YunshanVolumePanel.vue', () => ({default:{template:'<div />'}}))
vi.mock('../CustomerOperationWarehousePanel.vue', () => ({default:{template:'<div />'}}))

const pending = () => {let resolve; const promise = new Promise(r => {resolve=r});return {promise,resolve}}
const wrappers=[]
function mount(){const w=shallowMount(Erp,{global:{stubs:{ElForm:true,ElDialog:true}}});wrappers.push(w);return w}
beforeEach(() => {
  vi.clearAllMocks()
  mktYunshanApi.localWarehouses.mockResolvedValue({records:[{id:'9',name:'测试仓',code:'WH9'}],total:1})
  mktYunshanApi.localWarehouse.mockImplementation(id=>Promise.resolve({id,erpStatus:2,joinStatus:5,orderMode:'erp'}))
  mktYunshanApi.customers.mockResolvedValue({records:[{id:'42',name:'超级棒棒猪'},{id:'43',name:'超级棒棒猪'}],total:2})
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
    await w.vm.openMapping();Object.assign(w.vm.mapDialog,{customerId:'42',customerCode:' OWNER-A '});w.vm.selectCustomer('42')
    await w.vm.saveMapping()
    expect(mktErpApi.saveMapping).toHaveBeenCalledWith('9',{customerId:'42',customerCode:'OWNER-A'})
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
  it('同名客户按选择的完整编号保存，翻页后仍保留所选客户',async()=>{
    const w=mount();await flushPromises();await w.vm.openMapping()
    Object.assign(w.vm.mapDialog,{customerId:'43',customerCode:'OWNER-B'});w.vm.selectCustomer('43')
    mktYunshanApi.customers.mockResolvedValue({records:[{id:'9007199254740993',name:'另一客户'}],total:25})
    w.vm.customerPage=2;await w.vm.loadCustomers()
    expect(mktYunshanApi.customers).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'',pageNo:2,pageSize:20}))
    expect(w.vm.customerOptions.map(c=>c.id)).toEqual(['43','9007199254740993'])
    await w.vm.saveMapping()
    expect(mktErpApi.saveMapping).toHaveBeenCalledWith('9',{customerId:'43',customerCode:'OWNER-B'})
  })
  it('只有输入名称或手动填入编号，未选择候选客户时不能保存',async()=>{
    const w=mount();await flushPromises();await w.vm.openMapping()
    Object.assign(w.vm.mapDialog,{customerId:'42',customerCode:'OWNER-A'})
    await w.vm.saveMapping();expect(mktErpApi.saveMapping).not.toHaveBeenCalled()
  })
  it('较旧的搜索响应不能覆盖新关键词的结果，关闭表单后不回填',async()=>{
    const w=mount();await flushPromises();await w.vm.openMapping()
    const old=pending(),latest=pending()
    mktYunshanApi.customers.mockImplementation(({keyword})=>keyword==='旧'?old.promise:latest.promise)
    const first=w.vm.searchCustomers('旧'),second=w.vm.searchCustomers('新')
    latest.resolve({records:[{id:'51',name:'新客户'}],total:1});await second
    old.resolve({records:[{id:'41',name:'旧客户'}],total:1});await first
    expect(w.vm.customers.map(c=>c.name)).toEqual(['新客户'])
    const closing=pending();mktYunshanApi.customers.mockReturnValue(closing.promise)
    const third=w.vm.searchCustomers('关闭');w.vm.mapDialog.visible=false;w.vm.closeMapping()
    closing.resolve({records:[{id:'99',name:'不能回填'}],total:1});await third
    expect(w.vm.customers).toEqual([])
  })
  it('客户搜索失败显示错误并允许重试，保存不丢失长编号精度',async()=>{
    const w=mount();await flushPromises()
    mktYunshanApi.customers.mockRejectedValueOnce(new Error('连接失败'))
    await w.vm.openMapping();expect(w.vm.customerError).toBe('连接失败')
    mktYunshanApi.customers.mockResolvedValue({records:[{id:'9007199254740993',name:'客户甲'}],total:1})
    await w.vm.loadCustomers();expect(w.vm.customerError).toBe('')
    Object.assign(w.vm.mapDialog,{customerId:'9007199254740993',customerCode:'LONG'});w.vm.selectCustomer('9007199254740993')
    await w.vm.saveMapping()
    expect(mktErpApi.saveMapping).toHaveBeenCalledWith('9',{customerId:'9007199254740993',customerCode:'LONG'})
  })
})
