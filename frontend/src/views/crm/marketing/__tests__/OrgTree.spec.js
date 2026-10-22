import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OrgTree from '../OrgTree.vue'
import { mktOrgApi } from '@/api/marketing'

const { permissions } = vi.hoisted(() => ({ permissions: new Set() }))
vi.mock('@/utils/permission', () => ({ hasPermission: permission => permissions.has(permission) }))
vi.mock('@/api/marketing', () => ({
  mktOrgApi: Object.fromEntries(['tree', 'customers', 'positionPreview', 'position'].map(key => [key, vi.fn()]))
}))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

const flat = [
  { id: 2, name: '乙', positionCode: 'P2', status: 1, parentId: 3, customers: 3, signedCustomers: 1 },
  { id: 4, name: '丁超', positionCode: 'P4', status: 1, parentId: null, customers: 0, signedCustomers: 0 },
  { id: 3, name: '张三', positionCode: 'P3', status: 1, parentId: 4, customers: 2, signedCustomers: 0 },
  { id: 5, name: '李四', positionCode: 'P3', status: 1, parentId: 4, customers: 1, signedCustomers: 1 }
]
const wrappers = []
function mount() {
  const wrapper = shallowMount(OrgTree, { global: { directives: { loading: {} }, stubs: { ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' } } } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  vi.clearAllMocks(); permissions.clear()
  permissions.add('crm:marketing:promoter:query'); permissions.add('crm:marketing:promoter:edit')
  mktOrgApi.tree.mockResolvedValue(flat)
  mktOrgApi.customers.mockResolvedValue({ self: [{ id: 1, name: '自己的客户', signed: true, contractNo: 'CS-1', signMode: 1 }], next: [{ id: 2, name: '下级的客户', referrerName: '乙', signed: false }] })
  mktOrgApi.positionPreview.mockResolvedValue({ name: '乙', parentChanges: true, currentParentName: '张三', newParentName: '丁超', contractsWithoutAllocation: 2 })
  mktOrgApi.position.mockResolvedValue(null)
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('营销组织架构', () => {
  it('按上下级组成 P4 → P3 → P2 的树，同级按岗位和编号排序', async () => {
    const wrapper = mount(); await flushPromises()
    const roots = wrapper.vm.roots
    expect(roots).toHaveLength(1)
    expect(roots[0]).toMatchObject({ id: 4, positionCode: 'P4' })
    expect(roots[0].children.map(node => node.id)).toEqual([3, 5])
    expect(roots[0].children[0].children.map(node => node.id)).toEqual([2])
  })

  it('点击成员加载他自己推荐的客户和下一层级推荐的客户，可只看已签约', async () => {
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.select(wrapper.vm.roots[0].children[0]); await flushPromises()
    expect(mktOrgApi.customers).toHaveBeenCalledWith(3)
    expect(wrapper.vm.selfRows).toHaveLength(1)
    expect(wrapper.vm.nextRows).toHaveLength(1)
    wrapper.vm.onlySigned = true
    expect(wrapper.vm.selfRows).toHaveLength(1)
    expect(wrapper.vm.nextRows).toHaveLength(0)
  })

  it('P2 可调整为 P3：先预览新上级和未保存分配的合同数，确认后提交并刷新', async () => {
    const wrapper = mount(); await flushPromises()
    const yi = wrapper.vm.roots[0].children[0].children[0]
    await wrapper.vm.select(yi); await flushPromises()
    expect(wrapper.vm.canAdjust).toBe(true)
    expect(wrapper.vm.adjustOptions).toEqual(['P3'])
    await wrapper.vm.openAdjust(); await flushPromises()
    expect(mktOrgApi.positionPreview).toHaveBeenCalledWith(2, { code: 'P3' })
    expect(wrapper.text()).toContain('丁超')
    expect(wrapper.findAll('el-alert-stub').map(alert => alert.attributes('title')).join('|')).toContain('2 份还没有保存过佣金分配')
    await wrapper.vm.saveAdjust(); await flushPromises()
    expect(mktOrgApi.position).toHaveBeenCalledWith(2, { code: 'P3' })
    expect(mktOrgApi.tree).toHaveBeenCalledTimes(2)
  })

  it('P3、P4 不能在这里调整；没有编辑权限也看不到调整入口', async () => {
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.select(wrapper.vm.roots[0]); await flushPromises()
    expect(wrapper.vm.canAdjust).toBe(false)
    await wrapper.vm.select(wrapper.vm.roots[0].children[0]); await flushPromises()
    expect(wrapper.vm.canAdjust).toBe(false)
    const readOnly = (permissions.delete('crm:marketing:promoter:edit'), mount()); await flushPromises()
    await readOnly.vm.select(readOnly.vm.roots[0].children[0].children[0]); await flushPromises()
    expect(readOnly.vm.canAdjust).toBe(false)
  })

  it('预览失败（如后台账号未关联 P4）时显示原因且不能确认调整', async () => {
    mktOrgApi.positionPreview.mockRejectedValue(new Error('当前后台账号未关联伙伴档案'))
    const wrapper = mount(); await flushPromises()
    await wrapper.vm.select(wrapper.vm.roots[0].children[0].children[0]); await flushPromises()
    await wrapper.vm.openAdjust(); await flushPromises()
    expect(wrapper.vm.adjust.error).toContain('未关联伙伴档案')
    await wrapper.vm.saveAdjust()
    expect(mktOrgApi.position).not.toHaveBeenCalled()
  })
})

describe('营销组织架构 · 树状图', () => {
  it('默认是树状图，可切换为列表；展开/收起与缩放有边界', async () => {
    const wrapper = mount(); await flushPromises()
    expect(wrapper.vm.mode).toBe('chart')
    wrapper.vm.toggleCollapse(3)
    expect(wrapper.vm.collapsed[3]).toBe(true)
    wrapper.vm.toggleCollapse(3)
    expect(wrapper.vm.collapsed[3]).toBe(false)
    wrapper.vm.setAll(true)   // 收起到上两层：P3 有下级所以被收起，P4 不收起
    expect(wrapper.vm.collapsed).toEqual({ 3: true })
    wrapper.vm.setAll(false)
    expect(wrapper.vm.collapsed).toEqual({})
    for (let i = 0; i < 10; i++) wrapper.vm.zoomBy(0.1)
    expect(wrapper.vm.zoom).toBe(1.4)
    for (let i = 0; i < 20; i++) wrapper.vm.zoomBy(-0.1)
    expect(wrapper.vm.zoom).toBe(0.6)
    wrapper.vm.mode = 'list'
    expect(wrapper.vm.mode).toBe('list')
  })
})
