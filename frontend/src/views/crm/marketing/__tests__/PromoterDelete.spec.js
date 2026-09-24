import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import Promoter from '../Promoter.vue'
import { mktPromoterApi, mktPositionApi } from '@/api/marketing'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: permission => permissions.has(permission) }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ projects: [] }) }))
vi.mock('@/api/marketing', () => ({
  mktPromoterApi: { page: vi.fn(), remove: vi.fn() },
  mktPositionApi: { list: vi.fn() }
}))
vi.mock('element-plus', async importOriginal => ({
  ...await importOriginal(), ElMessage: { success: vi.fn(), error: vi.fn() }
}))

const row = { id: 17, name: '测试伙伴', phone: '13800000017', status: 1 }
const wrappers = []
function mount() {
  const wrapper = shallowMount(Promoter, {
    global: {
      stubs: {
        Search: true, Plus: true,
        ElTable: { template: '<div><slot /></div>' },
        ElTableColumn: { data: () => ({ row }), template: '<div><slot :row="row" /></div>' },
        ElButton: { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
        ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
        ElForm: { template: '<form><slot /></form>' },
        ElFormItem: { template: '<div><slot /></div>' }
      }
    }
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  permissions.clear()
  vi.clearAllMocks()
  mktPromoterApi.page.mockResolvedValue({ records: [row], total: 1 })
  mktPromoterApi.remove.mockResolvedValue(null)
  mktPositionApi.list.mockResolvedValue([])
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('伙伴删除仅管理员可操作', () => {
  it('运营即使有编辑和同名删除权限也没有入口，直接调用事件也不会请求删除', async () => {
    permissions.add('crm:marketing:promoter:edit')
    permissions.add('crm:marketing:promoter:delete')
    const wrapper = mount()
    await flushPromises()
    expect(wrapper.findAll('button').some(button => button.text() === '删除')).toBe(false)
    wrapper.vm.openDelete(row)
    expect(wrapper.vm.removal.visible).toBe(false)
    Object.assign(wrapper.vm.removal, { visible: true, row, reason: '尝试绕过' })
    await wrapper.vm.submitDelete()
    expect(mktPromoterApi.remove).not.toHaveBeenCalled()
  })

  it('管理员可打开具名确认弹窗，取消不发送删除请求', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    const button = wrapper.findAll('button').find(button => button.text() === '删除')
    await button.trigger('click')
    expect(wrapper.text()).toContain('确认删除伙伴「测试伙伴」')
    expect(wrapper.text()).toContain('138****0017')
    expect(mktPromoterApi.remove).not.toHaveBeenCalled()
    wrapper.vm.removal.reason = '重复录入'
    wrapper.vm.removal.visible = false
    await wrapper.vm.submitDelete()
    expect(mktPromoterApi.remove).not.toHaveBeenCalled()
  })

  it('拒绝空原因和过长原因', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    wrapper.vm.openDelete(row)
    wrapper.vm.removal.reason = '   '
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.removal.error).toBe('请填写删除原因')
    wrapper.vm.removal.reason = '字'.repeat(501)
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.removal.error).toContain('500')
    expect(mktPromoterApi.remove).not.toHaveBeenCalled()
  })

  it('有关联业务被拒绝时保留确认对象和原因，展示具体错误', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    wrapper.vm.openDelete(row)
    wrapper.vm.removal.reason = '重复录入'
    mktPromoterApi.remove.mockRejectedValue(new Error('该伙伴关联客户，不能删除'))
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.removal).toMatchObject({ visible: true, reason: '重复录入', busy: false, error: '该伙伴关联客户，不能删除' })
    expect(wrapper.vm.list).toEqual([row])
  })

  it('请求中禁止重复提交和切换删除对象，只提交一次指定伙伴', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    let resolve
    mktPromoterApi.remove.mockImplementation(() => new Promise(done => { resolve = done }))
    wrapper.vm.openDelete(row)
    wrapper.vm.removal.reason = '  测试清理  '
    const pending = wrapper.vm.submitDelete()
    wrapper.vm.openDelete({ id: 18, name: '另一个伙伴' })
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.removal.row.id).toBe(17)
    expect(mktPromoterApi.remove).toHaveBeenCalledTimes(1)
    expect(mktPromoterApi.remove).toHaveBeenCalledWith(17, { reason: '测试清理' })
    resolve(null)
    await pending
    expect(wrapper.vm.removal.visible).toBe(false)
    expect(wrapper.vm.removal.busy).toBe(false)
  })

  it('删除最后一页的唯一记录后返回上一页并刷新', async () => {
    permissions.add('ROLE_admin')
    const wrapper = mount()
    await flushPromises()
    wrapper.vm.query.pageNo = 2
    wrapper.vm.openDelete(row)
    wrapper.vm.removal.reason = '重复录入'
    mktPromoterApi.page.mockResolvedValue({ records: [], total: 10 })
    await wrapper.vm.submitDelete()
    expect(wrapper.vm.query.pageNo).toBe(1)
    expect(mktPromoterApi.page).toHaveBeenLastCalledWith(expect.objectContaining({ pageNo: 1 }))
    expect(wrapper.vm.list).toEqual([])
  })
})
