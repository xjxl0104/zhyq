import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Withdrawal from '../Withdrawal.vue'
import MoneyDocuments from '../MoneyDocuments.vue'
import ServiceContract from '../ServiceContract.vue'
import { mktWithdrawalApi, mktBillApi, mktSettlementApi, mktWarehouseApi, mktContractApi, mktCustomerApi } from '@/api/marketing'

vi.mock('@/api/marketing', () => ({
  mktWithdrawalApi: { page:vi.fn(),payAccount:vi.fn(),pay:vi.fn() },
  mktBillApi: {page:vi.fn()}, mktSettlementApi:{page:vi.fn(),directPayment:vi.fn()},
  mktWarehouseApi:{page:vi.fn(),online:vi.fn()}, mktContractApi:{page:vi.fn(),get:vi.fn(),update:vi.fn(),amendDone:vi.fn(),amendCancel:vi.fn()},
  mktCustomerApi:{page:vi.fn()},mktTemplateApi:{list:vi.fn().mockResolvedValue([])}
}))
vi.mock('@/utils/permission',()=>({hasPermission:()=>true}))
vi.mock('@/utils/fileDownload',()=>({startFileDownload:vi.fn()}))
const pending=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
const wrappers=[]
function mount(component,props={}){const w=shallowMount(component,{props,global:{stubs:{Search:true,Plus:true,ElDialog:{template:'<div><slot /></div>'},FileUpload:true,WarehouseAttachments:true,ElForm:{template:'<div><slot /></div>',methods:{validate:()=>Promise.resolve(true)}}}}});wrappers.push(w);return w}
beforeEach(()=>{
  vi.clearAllMocks()
  for(const api of [mktWithdrawalApi,mktBillApi,mktSettlementApi,mktWarehouseApi,mktContractApi,mktCustomerApi])api.page.mockResolvedValue({records:[],total:0})
  mktWarehouseApi.online.mockResolvedValue([])
  mktWithdrawalApi.pay.mockResolvedValue({});mktSettlementApi.directPayment.mockResolvedValue({});mktContractApi.amendDone.mockResolvedValue({})
})
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount())})

describe('提现账户加载与当前付款单绑定',()=>{
  it('先打开 A 后打开 B，迟到的 A 账户不能覆盖 B，提交仅使用 B',async()=>{
    const a=pending(),b=pending();mktWithdrawalApi.payAccount.mockImplementation(id=>id===1?a.promise:b.promise)
    const w=mount(Withdrawal);await flushPromises()
    const pa=w.vm.pay({id:1,netAmount:10}),pb=w.vm.pay({id:2,netAmount:20})
    b.resolve({name:'账户 B',accountNo:'B'});await pb;a.resolve({name:'账户 A',accountNo:'A'});await pa
    expect(w.vm.payment.account.accountNo).toBe('B');expect(w.vm.payment.row.id).toBe(2)
    w.vm.payment.payNo=' bank-B ';w.vm.payment.files=[{id:22}];await w.vm.savePayment()
    expect(mktWithdrawalApi.pay).toHaveBeenCalledWith(2,{payNo:'bank-B',payProof:'file:22'})
  })
  it('旧请求失败不会清除当前加载状态，关闭弹框后响应不能恢复账户',async()=>{
    const a=pending(),b=pending();mktWithdrawalApi.payAccount.mockImplementation(id=>id===1?a.promise:b.promise)
    const w=mount(Withdrawal);await flushPromises()
    const pa=w.vm.pay({id:1}),pb=w.vm.pay({id:2});a.reject(new Error('旧请求失败'));await pa
    expect(w.vm.payment.loading).toBe(true);expect(w.vm.payment.error).toBe('')
    w.vm.closePayment();b.resolve({name:'B'});await pb
    expect(w.vm.payment.visible).toBe(false);expect(w.vm.payment.account).toBeNull()
    await w.vm.savePayment();expect(mktWithdrawalApi.pay).not.toHaveBeenCalled()
  })
  it('付款请求进行中不能切换到其他单据或重复提交',async()=>{
    const response=pending();mktWithdrawalApi.payAccount.mockResolvedValue({name:'B'});mktWithdrawalApi.pay.mockReturnValue(response.promise)
    const w=mount(Withdrawal);await flushPromises();await w.vm.pay({id:2});Object.assign(w.vm.payment,{payNo:'B',files:[{id:22}]})
    const pay=w.vm.savePayment();await w.vm.pay({id:3});await w.vm.savePayment()
    expect(w.vm.payment.row.id).toBe(2);expect(mktWithdrawalApi.pay).toHaveBeenCalledTimes(1)
    response.resolve({});await pay
  })
})

describe('财务可选合同涵盖完整列表与到期尾款',()=>{
  it('拉取第 2 页后保留老合同，已到期直签合同可登记原合同期尾款',async()=>{
    const old={id:101,contractNo:'OLD',signMode:2,status:7,warehouseId:9,startDate:'2025-01-01',endDate:'2025-12-31'}
    mktContractApi.page.mockImplementation(({pageNo})=>Promise.resolve({total:101,records:pageNo===1?Array.from({length:100},(_,i)=>({id:i+1,signMode:1,status:5})):[old]}))
    const w=mount(MoneyDocuments,{kind:'bill'});await flushPromises();await w.vm.openDirect()
    expect(mktContractApi.page).toHaveBeenCalledWith({pageNo:2,pageSize:100});expect(w.vm.directContracts).toEqual([old])
    Object.assign(w.vm.direct,{contractId:101,amount:'25.50',paymentNo:'old-fee',files:[{id:50}],period:['2025-12-01','2025-12-31']})
    await w.vm.recordDirect()
    expect(mktSettlementApi.directPayment).toHaveBeenCalledWith({contractId:101,warehouseId:9,amount:'25.50',paymentNo:'old-fee',payProof:'file:50',periodStart:'2025-12-01',periodEnd:'2025-12-31'})
  })
  it('拒绝合同期外尾款、倒置账期与已终止合同',async()=>{
    mktContractApi.page.mockResolvedValue({total:2,records:[{id:1,signMode:2,status:7,startDate:'2025-01-01',endDate:'2025-12-31'},{id:2,signMode:2,status:8}]})
    const w=mount(MoneyDocuments,{kind:'bill'});await flushPromises();await w.vm.openDirect()
    expect(w.vm.directContracts.map(c=>c.id)).toEqual([1]);Object.assign(w.vm.direct,{contractId:1,amount:'5',paymentNo:'p',files:[{id:1}],period:['2025-12-01','2026-01-01']})
    await w.vm.recordDirect();w.vm.direct.period=['2025-12-31','2025-12-01'];await w.vm.recordDirect()
    expect(mktSettlementApi.directPayment).not.toHaveBeenCalled()
  })
})

describe('合同变更提交真实条款版本',()=>{
  const c={id:77,contractNo:'SC77',customerId:3,warehouseId:9,status:6,serviceType:2,feeModel:2,signMode:1,startDate:'2026-01-01',endDate:'2099-12-31',payCycle:3,deposit:100,priceTable:'{"perOrder":2}',files:'[{"id":1}]',remark:'原备注'}
  it('不复用原签署件，明确新条款与生效日，提交白名单且保留主体',async()=>{
    mktContractApi.get.mockResolvedValue({...c});const w=mount(ServiceContract);await flushPromises();await w.vm.openAmend(c);await flushPromises()
    expect(w.vm.attachments).toEqual([]);expect(w.vm.dialog.amending).toBe(true)
    w.vm.prices.perOrder=3;w.vm.effectiveDate='2099-01-01';w.vm.attachments=[{id:90,name:'签署变更.pdf'}];w.vm.form.remark='新备注'
    await w.vm.submit()
    expect(mktContractApi.amendDone).toHaveBeenCalledWith(77,{priceTable:'{"perOrder":3}',endDate:'2099-12-31',payCycle:3,files:'[{"id":90,"name":"签署变更.pdf"}]',remark:'新备注',effectiveDate:'2099-01-01'})
    expect(mktContractApi.update).not.toHaveBeenCalled()
  })
  it('缺已签附件或者回溯生效日期不能完成变更',async()=>{
    mktContractApi.get.mockResolvedValue({...c});const w=mount(ServiceContract);await flushPromises();await w.vm.openAmend(c);await flushPromises()
    await w.vm.submit();expect(mktContractApi.amendDone).not.toHaveBeenCalled()
    w.vm.attachments=[{id:90}];w.vm.effectiveDate='2020-01-01';await w.vm.submit();expect(mktContractApi.amendDone).not.toHaveBeenCalled()
  })
})
