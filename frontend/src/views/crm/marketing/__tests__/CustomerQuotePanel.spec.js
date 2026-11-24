import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CustomerQuotePanel from '../CustomerQuotePanel.vue'
import { quoteApi } from '@/api/warehouseQuotes'
vi.mock('@/api/warehouseQuotes',()=>({quoteApi:{intake:vi.fn(),comparison:vi.fn(),advice:vi.fn(),retry:vi.fn(),confirmProject:vi.fn()}}))

const props={customerId:12,projectId:3,canEdit:true,canAssign:true,assignmentStatus:0}
const open=async(overrides={})=>{
  const wrapper=mount(CustomerQuotePanel,{props:{...props,...overrides}})
  await flushPromises()
  return wrapper
}
const button=(wrapper,label)=>wrapper.findAll('button').find(b=>b.text().includes(label))
beforeEach(()=>{
  vi.resetAllMocks()
  quoteApi.intake.mockResolvedValue({stage:'intent',submittedAt:'2026-11-23 10:00',requirements:{couriers:['圆通']}})
  quoteApi.comparison.mockResolvedValue({warehouses:[{warehouseId:21,warehouseName:'演示仓',version:1,offers:[]}]})
  quoteApi.advice.mockResolvedValue({status:'ready',message:'评估完成',suggestions:['建议优先分配：演示仓']})
})
afterEach(()=>vi.useRealTimers())
describe('客户需求到人工分配',()=>{
  it('展示已提交需求、当前报告和待分配操作，不自动分派',async()=>{
    const w=await open()
    expect(w.text()).toContain('待后台分配')
    expect(w.text()).toContain('建议优先分配：演示仓')
    expect(w.emitted('assign')).toBeUndefined()
    await button(w,'选择此仓分配').trigger('click')
    expect(w.emitted('assign')).toEqual([[21]])
    await w.setProps({assignmentStatus:2})
    expect(button(w,'选择此仓分配')).toBeUndefined()
    await w.setProps({assignmentStatus:3})
    expect(button(w,'选择此仓分配')).toBeTruthy()
    w.unmount()
  })
  it('失败可手动重试；浏览权限不显示分配或重试按钮',async()=>{
    quoteApi.advice.mockResolvedValueOnce({status:'failed',message:'评估暂不可用',suggestions:[]})
    const w=await open()
    await button(w,'重新评估').trigger('click')
    await flushPromises()
    expect(quoteApi.retry).toHaveBeenCalledWith(12,3)
    expect(w.text()).toContain('建议优先分配')
    await w.setProps({canEdit:false,canAssign:false})
    expect(button(w,'选择此仓分配')).toBeUndefined()
    w.unmount()
  })
  it('未归属项目需显式确认方可关联，不加载其他园区报价',async()=>{
    quoteApi.intake.mockResolvedValue({stage:'project_required'})
    const w=await open()
    expect(quoteApi.comparison).not.toHaveBeenCalled()
    expect(quoteApi.confirmProject).not.toHaveBeenCalled()
    await button(w,'确认归属').trigger('click')
    await flushPromises()
    expect(quoteApi.confirmProject).toHaveBeenCalledWith(12,3)
    expect(w.emitted('project-confirmed')).toBeTruthy()
    w.unmount()
  })
  it('同仓两个承运方案只算一家，缺价仓不进入跨仓比较',async()=>{
    const priced={status:'estimate',perParcelMin:2,perParcelMax:3}
    quoteApi.comparison.mockResolvedValue({warehouses:[
      {warehouseId:10,warehouseName:'甲仓',version:1,offers:[{carrier:'圆通',...priced},{carrier:'顺丰',...priced}]},
      {warehouseId:12,warehouseName:'乙仓',version:0,offers:[]}]})
    const w=await open()
    expect(w.text()).toContain('本次有可比报价 1 家')
    expect(w.text()).toContain('暂无跨仓比价')
    expect(w.text()).not.toContain('票价核对参考')
    w.unmount()
  })
})
