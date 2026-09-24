import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Customer from '../Customer.vue'
import { mktCustomerApi } from '@/api/marketing'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: permission => permissions.has(permission) }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ projects: [] }) }))
vi.mock('@/api/marketing', () => ({ mktCustomerApi: { page: vi.fn(), remove: vi.fn() }, mktWarehouseApi: {} }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal(), ElMessage: { success: vi.fn(), error: vi.fn() } }))

const row = { id: 17, name: '测试推荐客户', status: 1 }
const wrappers = []
function mount() {
  const wrapper = shallowMount(Customer, { global: { stubs: {
    Search: true,
    ElTable: { template: '<div><slot /></div>' },
    ElTableColumn: { data: () => ({ row }), template: '<div><slot :row="row" /></div>' },
    ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
    ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' }
  } } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  permissions.clear()
  vi.clearAllMocks()
  mktCustomerApi.page.mockResolvedValue({ records: [row], total: 1 })
  mktCustomerApi.remove.mockResolvedValue(null)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('推荐客户删除', () => {
  it('普通编辑权限不显示删除入口，也不能通过处理函数发送请求', async () => {
    permissions.add('crm:marketing:customer:edit')
    const wrapper = mount()
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text() === '删除')).toBe(false)
    wrapper.vm.openDelete(row)
    expect(wrapper.vm.removal.visible).toBe(false)
    Object.assign(wrapper.vm.removal, { visible: true, row })
    await wrapper.vm.submitDelete()
    expect(mktCustomerApi.remove).not.toHaveBeenCalled()
  })

  it('管理员先确认具体客户，取消不会删除', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    await wrapper.findAll('button').find(button => button.text() === '删除').trigger('click')
    expect(wrapper.text()).toContain('确认删除客户「测试推荐客户」')
    expect(mktCustomerApi.remove).not.toHaveBeenCalled()
    wrapper.vm.removal.visible = false
    await wrapper.vm.submitDelete()
    expect(mktCustomerApi.remove).not.toHaveBeenCalled()
  })

  it('关联业务拒绝删除时保留客户和确认窗口，展示原因', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    wrapper.vm.openDelete(row)
    mktCustomerApi.remove.mockRejectedValue(new Error('客户已关联合同，不能删除'))
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.removal).toMatchObject({ visible: true, busy: false, error: '客户已关联合同，不能删除' })
    expect(wrapper.vm.list).toEqual([row])
  })

  it('阻止重复提交及切换目标，成功后刷新并回到上一页', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    wrapper.vm.query.pageNo = 2
    wrapper.vm.openDelete(row)
    let resolve
    mktCustomerApi.remove.mockImplementation(() => new Promise(done => { resolve = done }))
    const pending = wrapper.vm.submitDelete()
    wrapper.vm.openDelete({ id: 18, name: '另一位客户' })
    await wrapper.vm.submitDelete()
    expect(mktCustomerApi.remove).toHaveBeenCalledTimes(1)
    expect(mktCustomerApi.remove).toHaveBeenCalledWith(17)
    expect(wrapper.vm.removal.row.id).toBe(17)
    mktCustomerApi.page.mockResolvedValue({ records: [], total: 10 })
    resolve(null)
    await pending
    expect(wrapper.vm.removal).toMatchObject({ visible: false, busy: false })
    expect(wrapper.vm.query.pageNo).toBe(1)
    expect(wrapper.vm.list).toEqual([])
  })
})
