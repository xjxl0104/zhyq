import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import ServiceContract from '../ServiceContract.vue'
import { mktContractApi } from '@/api/marketing'
const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: p => permissions.has(p) }))
vi.mock('@/api/marketing', () => ({ mktContractApi: { page: vi.fn(), remove: vi.fn() }, mktWarehouseApi: {}, mktCustomerApi: {}, mktTemplateApi: {} }))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn() } }))
const row = { id: 7, contractNo: 'SC7', customerName: '测试客户', status: 8 }
const wrappers = []
function mount() {
  const w = shallowMount(ServiceContract, { global: { stubs: {
    Search: true, Plus: true, WarehouseAttachments: true,
    ElTable: { template: '<div><slot /></div>' },
    ElTableColumn: { data: () => ({ row }), template: '<div><slot :row="row" /></div>' },
    ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
    ElForm: { template: '<div><slot /></div>' }, ElFormItem: { template: '<div><slot /></div>' }
  } } }); wrappers.push(w); return w
}
beforeEach(() => {
  permissions.clear(); vi.clearAllMocks(); row.status = 8
  mktContractApi.page.mockResolvedValue({ records: [row], total: 1 })
  mktContractApi.remove.mockResolvedValue(null)
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))
describe('终止合同删除', () => {
  it('普通运营看不到删除按钮也不能通过事件删除', async () => {
    const w = mount(); await flushPromises()
    expect(w.findAll('button').some(b => b.text() === '删除')).toBe(false)
    w.vm.openDelete(row); expect(w.vm.removal.visible).toBe(false)
    Object.assign(w.vm.removal, { row, reason: '绕过' }); await w.vm.submitDelete()
    expect(mktContractApi.remove).not.toHaveBeenCalled()
  })
  it('仅终止状态显示删除，输入原因后删除指定合同并刷新', async () => {
    permissions.add('ROLE_admin'); const w = mount(); await flushPromises()
    expect(w.findAll('button').some(b => b.text() === '删除')).toBe(true)
    w.vm.openDelete({ ...row, status: 5 }); expect(w.vm.removal.visible).toBe(false)
    w.vm.openDelete(row); await w.vm.submitDelete(); expect(mktContractApi.remove).not.toHaveBeenCalled()
    w.vm.removal.reason = ' 误录 '; await w.vm.submitDelete()
    expect(mktContractApi.remove).toHaveBeenCalledWith(7, { reason: '误录' })
    expect(w.vm.removal.visible).toBe(false); expect(mktContractApi.page).toHaveBeenCalledTimes(2)
  })
  it('失败保留原因，提交中不能重复删除或切换目标', async () => {
    permissions.add('ROLE_admin'); const w = mount(); await flushPromises()
    let reject; mktContractApi.remove.mockImplementation(() => new Promise((_, r) => { reject = r }))
    w.vm.openDelete(row); w.vm.removal.reason = '误录'
    const pending = w.vm.submitDelete(); w.vm.openDelete({ ...row, id: 9 }); await w.vm.submitDelete()
    expect(mktContractApi.remove).toHaveBeenCalledTimes(1); expect(w.vm.removal.row.id).toBe(7)
    reject(new Error('已有账单')); await pending
    expect(w.vm.removal).toMatchObject({ visible: true, reason: '误录', busy: false, error: '已有账单' })
  })
})
