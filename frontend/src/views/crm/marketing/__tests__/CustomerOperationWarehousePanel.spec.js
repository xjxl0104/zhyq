import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import CustomerOperationWarehousePanel from '../CustomerOperationWarehousePanel.vue'
import { mktWarehouseOperationsApi, mktYunshanApi } from '@/api/marketing'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: permission => permissions.has(permission) }))
vi.mock('@/api/marketing', () => ({
  mktWarehouseOperationsApi: Object.fromEntries(['sites', 'createSite', 'links', 'createLink', 'setLinkStatus', 'deleteLink'].map(key => [key, vi.fn()])),
  mktYunshanApi: Object.fromEntries(['customers', 'config', 'owners', 'warehouses', 'createMap', 'maps'].map(key => [key, vi.fn()]))
}))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn() } }))
const warehouseId = '9007199254740997'
const existingCustomer = { id: '9007199254740993', name: '广东泽浩棒棒猪科技股份有限公司' }
const newCustomer = { id: '9007199254740995', name: '超级棒棒猪' }
const site = { id: '9007199254740999', name: '广州新作业仓', address: '广州市白云区', status: 1 }
const wrappers = []
function mount() {
  const wrapper = shallowMount(CustomerOperationWarehousePanel, { props: { warehouseId, warehouseName: '微新云仓' }, global: {
    directives: { loading: {} }, stubs: {
      ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
      ElAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
      ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
      ElForm: { template: '<form><slot /></form>' },
      ElFormItem: { props: ['label'], template: '<div>{{ label }}<slot /></div>' },
      ElSelect: { template: '<div><slot /><slot name="footer" /></div>' }
    }
  } })
  wrappers.push(wrapper); return wrapper
}
function selectCustomer(wrapper, customer = newCustomer) {
  wrapper.vm.linkDialog.customerId = customer.id; wrapper.vm.selectCustomer(customer.id)
}
function selectSite(wrapper) {
  wrapper.vm.linkDialog.operationWarehouseId = site.id; wrapper.vm.selectSite(site.id)
}
beforeEach(() => {
  vi.resetAllMocks(); permissions.clear()
  permissions.add('crm:marketing:erp:query'); permissions.add('crm:marketing:erp:config')
  mktWarehouseOperationsApi.links.mockResolvedValue({ records: [], total: 0 })
  mktWarehouseOperationsApi.sites.mockResolvedValue({ records: [{ ...site }], total: 1 })
  mktWarehouseOperationsApi.createSite.mockResolvedValue(site.id)
  mktWarehouseOperationsApi.createLink.mockResolvedValue('9007199254741003')
  mktWarehouseOperationsApi.setLinkStatus.mockResolvedValue(null)
  mktYunshanApi.customers.mockResolvedValue({ records: [{ ...existingCustomer }, { ...newCustomer }], total: 2 })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('客户与手动作业仓库建档', () => {
  it('独立客户不需要云山接口授权即可关联本地仓库，保留完整64位编号', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink()
    selectCustomer(wrapper); selectSite(wrapper); await flushPromises()
    expect(wrapper.vm.canSaveLink).toBe(true)
    expect(wrapper.text()).toContain('每个客户独立建档')
    expect(wrapper.text()).not.toContain('同一客户在接口中的记录')
    await wrapper.vm.saveLink()
    expect(mktWarehouseOperationsApi.createLink).toHaveBeenCalledWith(warehouseId, { customerId: newCustomer.id, operationWarehouseId: site.id })
    for (const method of ['config', 'owners', 'warehouses', 'createMap']) expect(mktYunshanApi[method]).not.toHaveBeenCalled()
    // 保存关联后通知父页面接着开通发货量同步，带上客户和作业仓库
    expect(wrapper.emitted('linked')).toEqual([[{ customerId: newCustomer.id, operationWarehouseId: site.id, customerName: newCustomer.name }]])
  })

  it('超级棒棒猪与已有棒棒猪客户保持两个独立客户编号', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink()
    selectCustomer(wrapper, existingCustomer); selectSite(wrapper); await wrapper.vm.saveLink()
    await wrapper.vm.openLink(); selectCustomer(wrapper, newCustomer); selectSite(wrapper); await wrapper.vm.saveLink()
    expect(mktWarehouseOperationsApi.createLink.mock.calls.map(([, payload]) => payload.customerId)).toEqual([existingCustomer.id, newCustomer.id])
  })

  it('手动仓库持久化成功后选中服务器编号，客户关联仍需单独保存', async () => {
    mktWarehouseOperationsApi.sites.mockResolvedValue({ records: [], total: 0 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink(); selectCustomer(wrapper)
    expect(wrapper.vm.canSaveLink).toBe(false)
    wrapper.vm.openSite(); wrapper.vm.siteDialog.name = ' 广州新作业仓 '; wrapper.vm.siteDialog.address = ' 广州市白云区 '
    await wrapper.vm.saveSite(); await flushPromises()
    expect(mktWarehouseOperationsApi.createSite).toHaveBeenCalledWith(warehouseId, { name: site.name, address: site.address })
    expect(wrapper.vm.linkDialog.operationWarehouseId).toBe(site.id)
    expect(wrapper.vm.selectedSite).toMatchObject(site)
    expect(wrapper.vm.canSaveLink).toBe(true)
    expect(mktWarehouseOperationsApi.createLink).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain(site.address)
    await wrapper.vm.saveLink()
    expect(mktWarehouseOperationsApi.createLink).toHaveBeenCalledWith(warehouseId, { customerId: newCustomer.id, operationWarehouseId: site.id })
  })

  it('防止重复提交仓库和客户关联，失败信息保留并允许修正后重试', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink(); selectCustomer(wrapper); selectSite(wrapper)
    wrapper.vm.openSite(); wrapper.vm.siteDialog.name = '新作业仓'
    let rejectSite
    mktWarehouseOperationsApi.createSite.mockImplementationOnce(() => new Promise((_, reject) => { rejectSite = reject }))
    const firstSite = wrapper.vm.saveSite(); await wrapper.vm.saveSite()
    expect(mktWarehouseOperationsApi.createSite).toHaveBeenCalledTimes(1)
    rejectSite(new Error('仓库名称已存在')); await firstSite; await flushPromises()
    expect(wrapper.text()).toContain('仓库名称已存在')
    expect(wrapper.vm.siteDialog.visible).toBe(true)
    expect(wrapper.vm.siteDialog.name).toBe('新作业仓')
    wrapper.vm.siteDialog.visible = false; wrapper.vm.closeSite()
    let rejectLink
    mktWarehouseOperationsApi.createLink.mockImplementationOnce(() => new Promise((_, reject) => { rejectLink = reject }))
    const firstLink = wrapper.vm.saveLink(); await wrapper.vm.saveLink()
    expect(mktWarehouseOperationsApi.createLink).toHaveBeenCalledTimes(1)
    rejectLink(new Error('该客户已关联此仓库')); await firstLink; await flushPromises()
    expect(wrapper.text()).toContain('该客户已关联此仓库')
    expect(wrapper.vm.linkDialog.customerId).toBe(newCustomer.id)
    expect(wrapper.vm.loading.saveLink).toBe(false)
  })

  it('切换加盟云仓后关闭旧表单，旧仓库创建结果不会混入新云仓', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink(); selectCustomer(wrapper)
    wrapper.vm.openSite(); wrapper.vm.siteDialog.name = '旧云仓作业仓'
    let finishOld
    mktWarehouseOperationsApi.createSite.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const pending = wrapper.vm.saveSite()
    await wrapper.setProps({ warehouseId: '9007199254742001', warehouseName: '另一个云仓' }); await flushPromises()
    expect(wrapper.vm.linkDialog.visible).toBe(false); expect(wrapper.vm.siteDialog.visible).toBe(false)
    await wrapper.vm.openLink()
    finishOld('9007199254742003'); await pending
    expect(wrapper.vm.linkDialog.operationWarehouseId).toBe('')
    expect(wrapper.vm.siteOptions.some(row => row.id === '9007199254742003')).toBe(false)
    expect(mktWarehouseOperationsApi.createSite).toHaveBeenCalledWith(warehouseId, { name: '旧云仓作业仓', address: '' })
    expect(mktWarehouseOperationsApi.createLink).not.toHaveBeenCalled()
  })

  it('客户和启用仓库均可搜索翻页，迟到搜索不会覆盖新的目录', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink()
    let finishOld
    mktYunshanApi.customers.mockImplementationOnce(() => new Promise(resolve => { finishOld = resolve }))
    const pending = wrapper.vm.searchCustomers('棒棒猪')
    mktYunshanApi.customers.mockResolvedValueOnce({ records: [newCustomer], total: 21 })
    await wrapper.vm.searchCustomers('超级棒棒猪'); finishOld({ records: [existingCustomer], total: 1 }); await pending
    expect(wrapper.vm.customers).toEqual([newCustomer])
    wrapper.vm.customerPage = 2; await wrapper.vm.loadCustomers()
    expect(mktYunshanApi.customers).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '超级棒棒猪', pageNo: 2, pageSize: 20 }))
    await wrapper.vm.searchSites('广州'); wrapper.vm.sitePage = 2; await wrapper.vm.loadSites()
    expect(mktWarehouseOperationsApi.sites).toHaveBeenLastCalledWith(warehouseId, { keyword: '广州', pageNo: 2, pageSize: 20, status: 1 })
  })

  it('手动新增仓库成功后忽略此前未完成的仓库搜索', async () => {
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openLink(); selectCustomer(wrapper)
    let rejectOld
    mktWarehouseOperationsApi.sites.mockImplementationOnce(() => new Promise((_, reject) => { rejectOld = reject }))
    const pending = wrapper.vm.searchSites('不存在')
    wrapper.vm.openSite(); wrapper.vm.siteDialog.name = site.name; await wrapper.vm.saveSite()
    rejectOld(new Error('旧搜索失败')); await pending
    expect(wrapper.vm.selectedSite.id).toBe(site.id)
    expect(wrapper.vm.errors.sites).toBe('')
    expect(wrapper.vm.canSaveLink).toBe(true)
  })

  it('没有配置权限只能查看；停用仓库不可保存关联', async () => {
    permissions.delete('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises()
    expect(wrapper.text()).not.toContain('新增客户关联')
    await wrapper.vm.openLink(); wrapper.vm.openSite(); await wrapper.vm.toggleStatus({ id: '42', status: 1 })
    expect(mktYunshanApi.customers).not.toHaveBeenCalled()
    expect(mktWarehouseOperationsApi.setLinkStatus).not.toHaveBeenCalled()
    wrapper.unmount()
    permissions.add('crm:marketing:erp:config')
    mktWarehouseOperationsApi.sites.mockResolvedValue({ records: [{ ...site, status: 0 }], total: 1 })
    const next = mount(); await flushPromises(); await next.vm.openLink(); selectCustomer(next); selectSite(next)
    expect(next.vm.canSaveLink).toBe(false)
    await next.vm.saveLink(); expect(mktWarehouseOperationsApi.createLink).not.toHaveBeenCalled()
  })

  it('仓库必填名称并限制长度，不把失真的数值编号作为保存结果使用', async () => {
    const wrapper = mount(); await flushPromises(); wrapper.vm.openSite()
    wrapper.vm.siteDialog.name = '   '; await wrapper.vm.saveSite()
    wrapper.vm.siteDialog.name = '仓'.repeat(129); await wrapper.vm.saveSite()
    expect(mktWarehouseOperationsApi.createSite).not.toHaveBeenCalled()
    wrapper.vm.siteDialog.name = '新仓库'; mktWarehouseOperationsApi.createSite.mockResolvedValueOnce(Number.MAX_SAFE_INTEGER + 2)
    await wrapper.vm.saveSite(); await flushPromises()
    expect(wrapper.text()).toContain('未获得有效编号')
    expect(wrapper.vm.linkDialog.operationWarehouseId).toBe('')
  })
})

describe('发货量同步状态列与行选择', () => {
  const link = { id: '1', customerId: existingCustomer.id, customerName: existingCustomer.name, operationWarehouseId: site.id, operationWarehouseName: '新华仓库', status: 1 }
  const map = { id: '11', customerId: existingCustomer.id, operationWarehouseId: site.id, status: 1, lastSyncAt: '2026-10-22 09:30:01', lastSyncOk: 1 }
  async function mountWith(links, maps) {
    mktWarehouseOperationsApi.links.mockResolvedValue({ records: links, total: links.length })
    if (maps instanceof Error) mktYunshanApi.maps.mockRejectedValue(maps)
    else mktYunshanApi.maps.mockResolvedValue(maps)
    const wrapper = mount(); await flushPromises(); return wrapper
  }

  it('客户和作业仓库都匹配时显示已配置及最近拉取结果', async () => {
    const wrapper = await mountWith([link], [map])
    expect(wrapper.vm.syncOf(link)).toMatchObject({ text: '已配置', configured: true })
    expect(wrapper.vm.syncOf(link).detail).toBe('最近拉取 2026-10-22 09:30 成功')
  })

  it('作业仓库上线前的历史配置没有作业仓库，客户只有一条关联时按客户对应', async () => {
    const wrapper = await mountWith([link], [{ ...map, operationWarehouseId: null }])
    expect(wrapper.vm.syncOf(link)).toMatchObject({ text: '已配置', configured: true })
    expect(wrapper.vm.syncOf(link).detail).toContain('历史配置')
  })

  it('同一客户有多条关联时，历史配置不能随意算到某一条上', async () => {
    const other = { ...link, id: '2', operationWarehouseId: '9007199254741001', operationWarehouseName: '另一仓库' }
    const wrapper = await mountWith([link, other], [{ ...map, operationWarehouseId: null }])
    expect(wrapper.vm.syncOf(link).configured).toBe(false)
    expect(wrapper.vm.syncOf(other).configured).toBe(false)
  })

  it('没有配置、已停用、拉取失败以及读取失败分别展示', async () => {
    expect((await mountWith([link], [])).vm.syncOf(link)).toMatchObject({ text: '未配置', configured: false })
    expect((await mountWith([link], [{ ...map, status: 0 }])).vm.syncOf(link)).toMatchObject({ text: '已停用', configured: true })
    expect((await mountWith([link], [{ ...map, lastSyncOk: 0 }])).vm.syncOf(link)).toMatchObject({ text: '已配置 · 拉取失败' })
    expect((await mountWith([link], new Error('network'))).vm.syncOf(link)).toMatchObject({ text: '未配置' })
  })

  it('点击某一行选中并通知上层，再次点击取消筛选', async () => {
    const wrapper = await mountWith([link], [map])
    wrapper.vm.pickLink(link)
    expect(wrapper.emitted('select')[0][0]).toEqual({ customerId: existingCustomer.id, operationWarehouseId: site.id, customerName: existingCustomer.name })
    wrapper.vm.pickLink(link)
    expect(wrapper.emitted('select')[1][0]).toBeNull()
  })

  it('没有配置的行可以带着客户信息请求配置同步', async () => {
    mktWarehouseOperationsApi.links.mockResolvedValue({ records: [link], total: 1 })
    mktYunshanApi.maps.mockResolvedValue([])
    const wrapper = shallowMount(CustomerOperationWarehousePanel, { props: { warehouseId, warehouseName: '微新云仓' }, global: {
      directives: { loading: {} }, stubs: {
        ElTable: { template: '<div><slot /></div>' },
        ElTableColumn: { data: () => ({ row: link }), template: '<div><slot :row="row" /></div>' },
        ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' }
      }
    } })
    wrappers.push(wrapper); await flushPromises()
    const configure = wrapper.findAll('button').find(button => button.text() === '配置同步')
    expect(configure).toBeTruthy()
    await configure.trigger('click')
    expect(wrapper.emitted('configure')[0][0]).toMatchObject({ customerId: existingCustomer.id, operationWarehouseId: site.id })
  })
})
