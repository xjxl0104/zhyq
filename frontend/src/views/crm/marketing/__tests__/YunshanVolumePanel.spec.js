import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import YunshanVolumePanel from '../YunshanVolumePanel.vue'
import { mktYunshanApi } from '@/api/marketing'
const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: permission => permissions.has(permission) }))
vi.mock('@/api/marketing', () => ({ mktYunshanApi: Object.fromEntries(['config', 'maps', 'customers', 'owners', 'warehouses', 'createMap', 'setStatus', 'volume', 'logs', 'sync', 'reconcile'].map(key => [key, vi.fn()])) }))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn(), warning: vi.fn() } }))
const map = { id: '9007199254740993', customerId: '9007199254740995', customerName: '棒棒猪', parentOwnerNo: '0009007199254740993', parentOwnerName: '棒棒猪', parentWarehouseNo: '000123', parentWarehouseName: '授权仓库', status: 1, identityMismatch: false }
const wrappers = []
function mount() {
  const wrapper = shallowMount(YunshanVolumePanel, { props: { warehouseId: '9007199254740997', warehouseName: '云山' }, global: {
    directives: { loading: {} }, stubs: {
      ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
      ElAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' },
      ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
      ElForm: { template: '<form><slot /></form>' }, ElFormItem: { template: '<div><slot /></div>' },
      ElSelect: { template: '<div><slot /><slot name="footer" /></div>' }
    }
  } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  permissions.clear(); permissions.add('crm:marketing:erp:query'); vi.resetAllMocks()
  mktYunshanApi.config.mockResolvedValue({ configured: true, baseUrl: 'https://example.test/api', appId: 'te***01' })
  mktYunshanApi.maps.mockResolvedValue([{ ...map }])
  mktYunshanApi.volume.mockResolvedValue({ records: [], total: 0, totalConsignNum: '0', daysWithData: 0, daysMissing: 7, dataState: 'NO_STAT_DATA' })
  mktYunshanApi.logs.mockResolvedValue({ records: [], total: 0 })
  mktYunshanApi.customers.mockResolvedValue({ records: [{ id: map.customerId, name: '棒棒猪' }], total: 21 })
  mktYunshanApi.owners.mockResolvedValue({ records: [{ no: map.parentOwnerNo, name: map.customerName }], pages: 1, total: 1 })
  mktYunshanApi.warehouses.mockResolvedValue({ records: [{ no: map.parentWarehouseNo, name: '授权仓库' }], pages: 1, total: 1 })
  mktYunshanApi.createMap.mockResolvedValue(null)
  mktYunshanApi.setStatus.mockResolvedValue(null)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))
describe('发货量同步与云山云仓接口授权边界', () => {
  it('同名仅作为候选，唯一仓库可带出但必须明确确认身份才允许保存', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.maps.mockResolvedValue([])
    mktYunshanApi.owners.mockResolvedValue({ records: [{ no: map.parentOwnerNo, name: '棒棒猪' }], total: 1, pages: 1 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    wrapper.vm.mapDialog.customerId = map.customerId; await wrapper.vm.matchCustomer(); await flushPromises()
    expect(wrapper.vm.mapDialog).toMatchObject({ parentOwnerNo: map.parentOwnerNo, parentWarehouseNo: map.parentWarehouseNo })
    expect(wrapper.vm.matchingOwners.map(item => item.no)).toEqual([map.parentOwnerNo])
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    expect(wrapper.text()).toContain('同名不代表同一主体')
    expect(wrapper.text()).toContain('授权仓库（已自动选择）')
    expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
    await wrapper.vm.saveMap()
    expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
    wrapper.vm.mapDialog.identityConfirmed = true
    await wrapper.vm.saveMap()
    expect(mktYunshanApi.createMap).toHaveBeenCalledWith('9007199254740997', {
      customerId: map.customerId, parentOwnerNo: map.parentOwnerNo, parentWarehouseNo: map.parentWarehouseNo, identityConfirmed: true
    })
  })
  it('超级棒棒猪是独立客户，不能选择广东泽浩棒棒猪代替或强行确认后保存', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.customers.mockResolvedValue({ records: [{ id: '42', name: '超级棒棒猪' }], total: 1 })
    mktYunshanApi.owners.mockResolvedValue({ records: [{ no: map.parentOwnerNo, name: '广东泽浩棒棒猪科技股份有限公司' }], pages: 1, total: 1 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    expect(wrapper.vm.customers[0].name).toBe('超级棒棒猪')
    wrapper.vm.mapDialog.customerId = '42'; await wrapper.vm.matchCustomer(); await flushPromises()
    expect(wrapper.vm.matchingOwners).toEqual([])
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe('')
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    expect(wrapper.text()).toContain('不能选择其他客户代替')
    expect(wrapper.text()).not.toContain('同一客户在接口中的记录')
    expect(mktYunshanApi.warehouses).not.toHaveBeenCalled()
    await wrapper.vm.saveMap(); expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
    wrapper.vm.mapDialog.parentOwnerNo = map.parentOwnerNo
    await wrapper.vm.changeOwner()
    wrapper.vm.mapDialog.identityConfirmed = true
    expect(wrapper.vm.mapDialog.parentWarehouseNo).toBe(map.parentWarehouseNo)
    expect(wrapper.vm.selectedOwner).toBeUndefined()
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    await wrapper.vm.saveMap(); expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
  })
  it('历史错绑不能作为客户身份依据，也不能优先沿用不匹配的旧编号', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.maps.mockResolvedValue([{ ...map, customerName: '超级棒棒猪', parentOwnerName: '广东泽浩棒棒猪科技股份有限公司', identityMismatch: true }])
    mktYunshanApi.customers.mockResolvedValue({ records: [{ id: map.customerId, name: '超级棒棒猪' }], total: 1 })
    mktYunshanApi.owners.mockResolvedValue({ records: [{ no: map.parentOwnerNo, name: '广东泽浩棒棒猪科技股份有限公司' }], pages: 1, total: 1 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    wrapper.vm.mapDialog.customerId = map.customerId; await wrapper.vm.matchCustomer()
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe('')
    expect(wrapper.vm.mapDialog.parentWarehouseNo).toBe('')
    expect(wrapper.vm.matchingOwners).toEqual([])
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    expect(mktYunshanApi.warehouses).not.toHaveBeenCalled()
    mktYunshanApi.owners.mockResolvedValue({ records: [
      { no: map.parentOwnerNo, name: '广东泽浩棒棒猪科技股份有限公司' }, { no: 'NEW', name: '超级棒棒猪' }
    ], pages: 1, total: 2 })
    await wrapper.vm.reloadDirectory()
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe('NEW')
    expect(wrapper.vm.ownerMatchKind).toBe('name')
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.canSaveMap).toBeFalsy()
  })
  it('同名接口客户有歧义时不自动选第一条，允许核对后关联', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.maps.mockResolvedValue([])
    mktYunshanApi.owners.mockResolvedValue({ records: [{ no: 'A', name: '棒棒猪' }, { no: 'B', name: '棒棒猪' }], total: 2, pages: 1 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    wrapper.vm.mapDialog.customerId = map.customerId; await wrapper.vm.matchCustomer(); await flushPromises()
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe('')
    expect(wrapper.text()).toContain('找到多个同名候选')
    wrapper.vm.mapDialog.parentOwnerNo = 'B'; await wrapper.vm.selectInterfaceCustomer()
    expect(wrapper.vm.ownerMatchKind).toBe('manual')
    expect(mktYunshanApi.warehouses).toHaveBeenCalledWith({ parentOwnerNo: 'B', current: 1 })
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.canSaveMap).toBeFalsy()
  })
  it('客户、接口货主或目录变化后清除旧确认，不能沿用上一组合的身份确认', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.customers.mockResolvedValue({ records: [
      { id: map.customerId, name: map.customerName }, { id: '42', name: '独立客户乙' }
    ], total: 2 })
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap()
    wrapper.vm.mapDialog.customerId = map.customerId; await wrapper.vm.matchCustomer()
    wrapper.vm.mapDialog.identityConfirmed = true
    expect(wrapper.vm.canSaveMap).toBeTruthy()

    wrapper.vm.owners.push({ no: 'SECOND', name: map.customerName })
    wrapper.vm.mapDialog.parentOwnerNo = 'SECOND'; await wrapper.vm.selectInterfaceCustomer()
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.canSaveMap).toBeFalsy()

    wrapper.vm.mapDialog.identityConfirmed = true
    await wrapper.vm.reloadDirectory()
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.mapDialog.customerId).toBe(map.customerId)
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe(map.parentOwnerNo)
    expect(wrapper.vm.canSaveMap).toBeFalsy()

    wrapper.vm.owners.push({ no: 'CLIENT-B', name: '独立客户乙' })
    wrapper.vm.mapDialog.identityConfirmed = true
    wrapper.vm.mapDialog.customerId = '42'; await wrapper.vm.matchCustomer()
    expect(wrapper.vm.mapDialog.parentOwnerNo).toBe('CLIENT-B')
    expect(wrapper.vm.mapDialog.identityConfirmed).toBe(false)
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
  })
  it('历史错绑禁止补拉、远端对账与启用，但仍可查看本地快照和停用', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.maps.mockResolvedValue([{ ...map, customerName: '超级棒棒猪', parentOwnerName: '广东泽浩棒棒猪科技股份有限公司', identityMismatch: true }])
    const wrapper = mount(); await flushPromises()
    const mismatched = wrapper.vm.maps[0]
    expect(mktYunshanApi.volume).toHaveBeenCalledWith(expect.objectContaining({ mapId: map.id }))
    await wrapper.vm.sync(); await wrapper.vm.reconcile()
    await wrapper.vm.toggleStatus({ ...mismatched, status: 0 })
    expect(mktYunshanApi.sync).not.toHaveBeenCalled()
    expect(mktYunshanApi.reconcile).not.toHaveBeenCalled()
    expect(mktYunshanApi.setStatus).not.toHaveBeenCalled()

    await wrapper.vm.toggleStatus(mismatched)
    expect(mktYunshanApi.setStatus).toHaveBeenCalledTimes(1)
    expect(mktYunshanApi.setStatus).toHaveBeenCalledWith(map.id, 0)
    expect(mismatched.status).toBe(0)
    mktYunshanApi.volume.mockClear()
    await wrapper.vm.queryVolume()
    expect(mktYunshanApi.volume).toHaveBeenCalledWith(expect.objectContaining({ mapId: map.id }))
  })
  it('明确区分加盟云仓与接口目录，显示单一授权选项的来源', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    expect(wrapper.text()).toContain('云山接口发货量')
    expect(wrapper.text()).toContain('云山接口的共享连接')
    expect(wrapper.text()).toContain('不代表当前加盟云仓已接入')
    expect(wrapper.text()).toContain('加盟云仓：云山')
    expect(wrapper.text()).toContain('当前接口开放 1 个客户的查询权限')
    expect(wrapper.text()).toContain('一个客户可以在多个作业仓库发货')
    expect(wrapper.text()).not.toContain('云山货主')
    wrapper.vm.mapDialog.parentOwnerNo = map.parentOwnerNo; await wrapper.vm.changeOwner(); await flushPromises()
    expect(wrapper.text()).toContain('该客户可用 1 个作业仓库')
    expect(mktYunshanApi.owners).toHaveBeenCalledWith({ current: 1 })
  })
  it('仓库目录也读取全部分页，授权刷新保留客户并清空旧组合', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap()
    wrapper.vm.mapDialog.customerId = map.customerId; wrapper.vm.mapDialog.parentOwnerNo = map.parentOwnerNo
    mktYunshanApi.warehouses.mockImplementation(({ current }) => Promise.resolve({
      records: [{ no: current === 1 ? 'WH-A' : 'WH-B', name: `仓库${current}` }], pages: 2, total: 2
    }))
    await wrapper.vm.changeOwner(); await flushPromises()
    expect(mktYunshanApi.warehouses.mock.calls.map(([params]) => params.current)).toEqual([1, 2])
    expect(wrapper.vm.entityWarehouses.map(item => item.no)).toEqual(['WH-A', 'WH-B'])
    expect(wrapper.text()).toContain('该客户可用 2 个作业仓库')
    wrapper.vm.mapDialog.parentWarehouseNo = 'WH-B'
    mktYunshanApi.owners.mockResolvedValue({ records: [{ no: 'NEW-OWNER', name: '新授权货主' }], total: 1, pages: 1 })
    await wrapper.vm.reloadDirectory()
    expect(wrapper.vm.mapDialog).toMatchObject({ customerId: map.customerId, parentOwnerNo: '', parentWarehouseNo: '' })
    expect(wrapper.vm.owners.map(item => item.no)).toEqual(['NEW-OWNER'])
    expect(wrapper.vm.canSaveMap).toBeFalsy()
    expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
  })
  it('区分接口读取失败和空授权目录，失败后不能保留旧选项', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap()
    mktYunshanApi.owners.mockRejectedValueOnce(new Error('目录读取失败'))
    await wrapper.vm.reloadDirectory(); await flushPromises()
    expect(wrapper.text()).toContain('目录读取失败')
    expect(wrapper.text()).not.toContain('当前接口开放')
    expect(wrapper.vm.owners).toEqual([])
    mktYunshanApi.owners.mockResolvedValue({ records: [], pages: 0, total: 0 })
    await wrapper.vm.reloadDirectory(); await flushPromises()
    expect(wrapper.text()).toContain('当前接口开放 0 个客户的查询权限')
    expect(wrapper.text()).not.toContain('目录读取失败')
    expect(wrapper.vm.canSaveMap).toBeFalsy()
  })
  it('查询权限可读历史；没有配置权限不能打开目录、停用或补拉', async () => {
    const wrapper = mount(); await flushPromises()
    expect(wrapper.text()).not.toContain('配置接口同步'); expect(wrapper.text()).not.toContain('补拉此区间'); expect(wrapper.text()).toContain('只读对账')
    await wrapper.vm.openMap(); await wrapper.vm.sync(); await wrapper.vm.toggleStatus(map)
    expect(mktYunshanApi.owners).not.toHaveBeenCalled(); expect(mktYunshanApi.sync).not.toHaveBeenCalled(); expect(mktYunshanApi.setStatus).not.toHaveBeenCalled()
  })
  it('无统计不显示为零；64位票数与ID保留全部数字', async () => {
    const wrapper = mount(); await flushPromises()
    expect(wrapper.find('.volume-summary').text()).toContain('暂无统计')
    expect(mktYunshanApi.volume).toHaveBeenCalledWith(expect.objectContaining({ mapId: map.id }))
    mktYunshanApi.volume.mockResolvedValue({ records: [], total: 1, totalConsignNum: '9223372036854775807', daysWithData: 1, daysMissing: 6, dataState: 'HAS_STAT_DATA' })
    await wrapper.vm.queryVolume(); await flushPromises()
    expect(wrapper.find('.volume-summary').text()).toContain('9,223,372,036,854,775,807 票')
    expect(wrapper.vm.formatCount(null, '无统计')).toBe('无统计'); expect(wrapper.vm.formatCount('0')).toBe('0')
  })
  it('货主目录遍历全部页，按货主过滤仓库，提交编码不丢前导零', async () => {
    permissions.add('crm:marketing:erp:config')
    mktYunshanApi.owners.mockImplementation(({ current }) => Promise.resolve({ records: current === 1 ? [{ no: 'OTHER', name: '其他货主' }] : [{ no: map.parentOwnerNo, name: map.customerName }], pages: 2, total: 2 }))
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    expect(mktYunshanApi.owners.mock.calls.map(([params]) => params.current)).toEqual([1, 2])
    wrapper.vm.mapDialog.customerId = map.customerId; await wrapper.vm.matchCustomer()
    expect(mktYunshanApi.warehouses).toHaveBeenCalledWith({ parentOwnerNo: map.parentOwnerNo, current: 1 })
    wrapper.vm.mapDialog.identityConfirmed = true
    wrapper.vm.mapDialog.parentWarehouseNo = 'UNAUTHORIZED'; await wrapper.vm.saveMap(); expect(mktYunshanApi.createMap).not.toHaveBeenCalled()
    wrapper.vm.mapDialog.parentWarehouseNo = map.parentWarehouseNo; await wrapper.vm.saveMap()
    expect(mktYunshanApi.createMap).toHaveBeenCalledWith('9007199254740997', { customerId: map.customerId, parentOwnerNo: map.parentOwnerNo, parentWarehouseNo: map.parentWarehouseNo, identityConfirmed: true })
  })
  it('切换货主清空旧仓库，忽略旧货主的迟到目录', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap()
    let resolveOld
    mktYunshanApi.warehouses.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve })).mockResolvedValueOnce({ records: [{ no: 'NEW', name: '新货主的仓库' }], pages: 1 })
    wrapper.vm.mapDialog.parentOwnerNo = 'OLD'; wrapper.vm.mapDialog.parentWarehouseNo = 'STALE'
    const oldRequest = wrapper.vm.changeOwner(); expect(wrapper.vm.mapDialog.parentWarehouseNo).toBe('')
    wrapper.vm.mapDialog.parentOwnerNo = 'NEW'; await wrapper.vm.changeOwner()
    resolveOld({ records: [{ no: 'STALE', name: '旧仓库' }], pages: 1 }); await oldRequest
    expect(wrapper.vm.entityWarehouses.map(item => item.no)).toEqual(['NEW'])
  })
  it('客户搜索和翻页可访问首20条以外的客户', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises(); await wrapper.vm.openMap(); await flushPromises()
    await wrapper.vm.searchCustomers('棒棒猪'); wrapper.vm.customerPage = 2; await wrapper.vm.loadCustomers()
    expect(mktYunshanApi.customers).toHaveBeenLastCalledWith({ keyword: '棒棒猪', pageNo: 2, pageSize: 20 })
  })
  it('切换云仓后旧请求不能覆盖新客户快照', async () => {
    const wrapper = mount(); await flushPromises()
    let resolveOld
    mktYunshanApi.volume.mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve }))
    const oldRequest = wrapper.vm.queryVolume()
    mktYunshanApi.maps.mockResolvedValue([{ ...map, id: 'NEW-MAP' }])
    mktYunshanApi.volume.mockResolvedValue({ records: [], total: 1, totalConsignNum: '4', daysWithData: 1, daysMissing: 6, dataState: 'HAS_STAT_DATA' })
    await wrapper.setProps({ warehouseId: 'NEW-WAREHOUSE' }); await flushPromises()
    resolveOld({ records: [], total: 1, totalConsignNum: '999' }); await oldRequest
    expect(wrapper.vm.volume.totalConsignNum).toBe('4'); expect(wrapper.vm.mapId).toBe('NEW-MAP')
  })
  it('拒绝超过92天的补拉；只读对账不调用同步接口', async () => {
    permissions.add('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises()
    wrapper.vm.dateRange = ['2020-01-01', '2020-04-02']; await wrapper.vm.sync(); expect(mktYunshanApi.sync).not.toHaveBeenCalled()
    wrapper.vm.dateRange = ['2020-01-01', '2020-01-02']
    mktYunshanApi.reconcile.mockResolvedValue({ localTotal: '4', summaryTotal: '0', dailyTotal: '0', difference: '4', matched: false, dataState: 'NO_STAT_DATA', differences: [{ statDate: '2020-01-01', localConsignNum: '4', remoteConsignNum: null, reason: 'REMOTE_MISSING' }] })
    await wrapper.vm.reconcile(); await flushPromises()
    expect(wrapper.text()).toContain('接口暂无统计，无法确认一致'); expect(mktYunshanApi.sync).not.toHaveBeenCalled()
  })
})
