import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import Warehouse from '../Warehouse.vue'
import Maintenance from '../WarehouseMaintenance.vue'
import { mktWarehouseApi } from '@/api/marketing'
const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: p => permissions.has(p) }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ projects: [] }) }))
vi.mock('@/api/marketing', () => ({ mktWarehouseApi: { page: vi.fn(), remove: vi.fn(), maintenance: vi.fn() }, mktYunshanApi: { syncStatus: vi.fn() } }))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn() } }))
const row = { id: 7, code: 'WH-7', name: '测试云仓', joinStatus: 2 }
const wrappers = []
function mount(component = Warehouse, props = {}) {
  const wrapper = shallowMount(component, { props, global: { stubs: {
    Search: true, Plus: true,
    ElTable: { template: '<div><slot /></div>' },
    ElTableColumn: { data: () => ({ row }), template: '<div><slot :row="row" /></div>' },
    ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
    ElForm: { template: '<div><slot /></div>' }, ElFormItem: { template: '<div><slot /></div>' }
  } } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  permissions.clear(); vi.clearAllMocks()
  mktWarehouseApi.page.mockResolvedValue({ records: [row], total: 1 })
  mktWarehouseApi.remove.mockResolvedValue(null)
  mktWarehouseApi.maintenance.mockResolvedValue({ records: [], total: 0 })
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('云仓立即删除及维护记录', () => {
  it('普通运营不能通过界面或事件发起删除', async () => {
    const w = mount(); await flushPromises()
    expect(w.findAll('button').some(b => b.text() === '删除')).toBe(false)
    w.vm.openDelete(row); expect(w.vm.removal.visible).toBe(false)
    Object.assign(w.vm.removal, { row, reason: '绕过' }); await w.vm.submitDelete()
    expect(mktWarehouseApi.remove).not.toHaveBeenCalled()
  })
  it('删除必须填写原因并告知立即生效及不可撤销', async () => {
    permissions.add('ROLE_admin'); const w = mount(); await flushPromises()
    expect(w.text()).toContain('加盟云仓维护记录')
    w.vm.openDelete(row); await flushPromises()
    expect(w.text()).toContain('立即删除')
    expect(w.find('el-alert-stub').attributes('title')).toContain('删除后不可撤销')
    await w.vm.submitDelete(); expect(mktWarehouseApi.remove).not.toHaveBeenCalled()
    expect(w.vm.removal.error).toContain('必填')
    w.vm.removal.reason = ' 误录 '; await w.vm.submitDelete()
    expect(mktWarehouseApi.remove).toHaveBeenCalledWith(7, { reason: '误录' })
    expect(w.vm.removal.visible).toBe(false)
    expect(ElMessage.success).toHaveBeenCalledWith('云仓已删除，立即生效')
    expect(mktWarehouseApi.page).toHaveBeenCalledTimes(2)
  })
  it('请求失败保留原因，重复点击不会重复提交', async () => {
    permissions.add('ROLE_admin'); const w = mount(); await flushPromises()
    let reject; mktWarehouseApi.remove.mockImplementation(() => new Promise((_, r) => { reject = r }))
    w.vm.openDelete(row); w.vm.removal.reason = '误录'
    const pending = w.vm.submitDelete(); await w.vm.submitDelete()
    expect(mktWarehouseApi.remove).toHaveBeenCalledTimes(1)
    reject(new Error('仍有在服客户')); await pending
    expect(w.vm.removal).toMatchObject({ visible: true, reason: '误录', busy: false, error: '仍有在服客户' })
  })
  it('历史待删除云仓也可以直接删除，不提供撤销入口', async () => {
    permissions.add('ROLE_admin'); const w = mount(); await flushPromises()
    w.vm.openDelete({ ...row, joinStatus: 8 }); w.vm.removal.reason = '立即删除'
    await w.vm.submitDelete()
    expect(mktWarehouseApi.remove).toHaveBeenCalledWith(7, { reason: '立即删除' })
    expect(w.text()).not.toContain('撤销删除')
  })
  it('每次打开维护记录重新查询，关闭后的迟到响应被忽略', async () => {
    const w = mount(Maintenance, { modelValue: false })
    let resolve; mktWarehouseApi.maintenance.mockImplementation(() => new Promise(r => { resolve = r }))
    await w.setProps({ modelValue: true }); await flushPromises()
    expect(mktWarehouseApi.maintenance).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20, keyword: '' })
    await w.setProps({ modelValue: false })
    resolve({ records: [{ id: 99 }], total: 1 }); await flushPromises()
    expect(w.vm.rows).toEqual([])
  })
})
