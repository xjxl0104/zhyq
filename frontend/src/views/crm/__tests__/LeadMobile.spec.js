import { flushPromises, shallowMount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import Lead from '../Lead.vue'
import LeadFollowDrawer from '../LeadFollowDrawer.vue'
import { leadApi } from '@/api/crm'
import { fileApi } from '@/api/file'

vi.mock('@/api/crm', () => ({
  leadApi: {
    page: vi.fn(), stats: vi.fn(), get: vi.fn(), add: vi.fn(),
    update: vi.fn(), convert: vi.fn(), remove: vi.fn(), importExcel: vi.fn()
  },
  followApi: { list: vi.fn() }
}))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn(), attach: vi.fn() } }))

const row = {
  id: 17, leadNo: 'KH-0017', registerDate: '2026-09-01', contact: '陈总',
  phone: '13800138000', company: '云仓商贸', source: '电话咨询',
  customerType: '意向租仓客户(找仓)', coopMode: '仓库整租', demandArea: '5000',
  goodsType: '化妆品', orderVolume: '1万单', coopPeriod: '1年',
  budgetPrice: '7元/㎡·月', intentPark: '云仓产业园', region: '广州市海珠区',
  grade: 'A-高意向高价值', ownerName: '丁超', status: 2,
  lastFollowDate: '2026-09-16', followCount: 0, nextFollow: '2026-09-20 10:00:00',
  remark: '请准备分租方案'
}

const wrappers = []
let mobile = true
function mountPage() {
  const wrapper = shallowMount(Lead, {
    global: {
      stubs: {
        MobileRecordList: false,
        ElTag: { template: '<span><slot /></span>' },
        ElButton: { template: '<button><slot /></button>' },
        ElForm: { template: '<form><slot /></form>' },
        ElFormItem: { name: 'ElFormItem', props: ['label'], template: '<label><slot /></label>' },
        ElPopconfirm: { name: 'ElPopconfirm', emits: ['confirm'], template: '<div><slot name="reference" /></div>' },
        Search: true, Plus: true, Upload: true, UploadFilled: true
      }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('线索登记表移动适配', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mobile = true
    vi.stubGlobal('matchMedia', vi.fn(() => ({
      matches: mobile, addEventListener: vi.fn(), removeEventListener: vi.fn()
    })))
    leadApi.page.mockResolvedValue({ records: [row], total: 1 })
    leadApi.stats.mockResolvedValue({ total: 1, todayNew: 0, monthNew: 1, invalid: 0, convertRate: 0 })
    leadApi.get.mockResolvedValue(row)
    fileApi.list.mockResolvedValue([])
  })

  afterEach(() => {
    wrappers.splice(0).forEach(wrapper => wrapper.unmount())
    vi.unstubAllGlobals()
  })

  it('shows the current registration fields and keeps zero follow-ups visible', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const fields = Object.fromEntries(wrapper.findAll('.mobile-record-field').map(field => [
      field.get('dt').text(), field.get('dd').text()
    ]))
    expect(fields).toEqual({
      客户编号: row.leadNo, 登记日期: row.registerDate, 客户姓名: row.contact,
      联系电话: row.phone, '公司/店铺': row.company, 客户来源: row.source,
      客户类型: row.customerType, 意向合作方式: row.coopMode, '面积/库容(㎡)': row.demandArea,
      主营品类: row.goodsType, '日均/月单量': row.orderVolume, 意向合作周期: row.coopPeriod,
      心理价位: row.budgetPrice, '意向园区/仓库': row.intentPark, 所在地区: row.region,
      客户等级: row.grade, 负责人: row.ownerName, 当前状态: '跟进中',
      最近跟进: row.lastFollowDate, 跟进次数: '0', 下次跟进计划: row.nextFollow,
      跟进记录速记: row.remark
    })
    expect(wrapper.get('.mobile-record-details summary').attributes('aria-label'))
      .toBe('查看线索 KH-0017 全部信息')
  })

  it('opens the current follow-up drawer and reloads after a saved follow-up', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[aria-label="查看线索 KH-0017 跟进记录"]').trigger('click')

    const drawer = wrapper.getComponent(LeadFollowDrawer)
    expect(drawer.props('modelValue')).toBe(true)
    expect(drawer.props('lead')).toEqual(row)
    drawer.vm.$emit('saved')
    await flushPromises()
    expect(leadApi.page).toHaveBeenCalledTimes(2)
  })

  it('loads details and attachments through the latest edit flow', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[aria-label="编辑线索 KH-0017"]').trigger('click')
    await flushPromises()

    expect(leadApi.get).toHaveBeenCalledWith(17)
    expect(fileApi.list).toHaveBeenCalledWith('crm_lead', 17)
    expect(wrapper.findComponent({ name: 'ElDialog' }).props('modelValue')).toBe(true)
  })

  it('converts an active lead and refreshes the list and statistics', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[aria-label="将线索 KH-0017 转为客户"]').trigger('click')
    await flushPromises()

    expect(leadApi.convert).toHaveBeenCalledWith(17)
    expect(leadApi.page).toHaveBeenCalledTimes(2)
    expect(leadApi.stats).toHaveBeenCalledTimes(2)
  })

  it('uses the latest signed status and hides conversion for converted leads', async () => {
    leadApi.page.mockResolvedValue({ records: [{ ...row, status: 5 }], total: 1 })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.mobile-record-card__header').text()).toContain('已签约/已成交')
    expect(wrapper.find('[aria-label="将线索 KH-0017 转为客户"]').exists()).toBe(false)
  })

  it('deletes only after confirmation and refreshes current data', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.get('[aria-label="删除线索 KH-0017"]').trigger('click')
    expect(leadApi.remove).not.toHaveBeenCalled()

    wrapper.findComponent({ name: 'ElPopconfirm' }).vm.$emit('confirm')
    await flushPromises()
    expect(leadApi.remove).toHaveBeenCalledWith(17)
    expect(leadApi.page).toHaveBeenCalledTimes(2)
    expect(leadApi.stats).toHaveBeenCalledTimes(2)
  })

  it('keeps all filters, collapsing on phones and showing the original desktop table', async () => {
    const phone = mountPage()
    await flushPromises()
    expect(phone.get('details.search-bar').attributes('open')).toBeUndefined()
    expect(phone.findAllComponents({ name: 'ElFormItem' }).map(item => item.props('label')))
      .toEqual(['客户编号', '客户姓名', '联系电话', '公司/店铺', '客户类型', '客户等级', '当前状态', '负责人', undefined])

    mobile = false
    const desktop = mountPage()
    await flushPromises()
    expect(desktop.get('details.search-bar').attributes()).toHaveProperty('open')
    expect(desktop.find('.mobile-record-list').exists()).toBe(false)
    expect(desktop.findComponent({ name: 'ElTable' }).props('data')).toEqual([row])
  })
})
