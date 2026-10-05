import { afterEach, expect, it } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import PermissionSelector from '../PermissionSelector.vue'

const menus = [
  { id: 130, name: '系统管理', type: 1, status: 1 },
  { id: 1, name: '用户管理', path: '/system/user', type: 2, status: 1 },
  { id: 2, name: '用户-新增', perm: 'system:user:add', type: 3, status: 1 },
  { id: 3, name: '工单-查询', perm: 'property:workorder:query', type: 3, status: 1 },
  { id: 4, name: '工单-修改', perm: 'property:workorder:edit', type: 3, status: 1 },
  { id: 5, name: '我的建议', path: 'suggestion/mine', type: 1, status: 1 },
  { id: 6, name: '建议管理', path: 'suggestion/manage', perm: 'suggestion:manage', type: 1, status: 1 }
]
let wrapper
async function setup(ids = [], extra = {}) {
  wrapper = mount(PermissionSelector, { props: { menus, modelValue: ids, ...extra,
    'onUpdate:modelValue': value => wrapper.setProps({ modelValue: value }) }, global: { plugins: [ElementPlus] } })
  await flushPromises()
}
afterEach(() => wrapper?.unmount())
async function button(text) {
  const target = wrapper.findAll('button').find(n => n.text() === text)
  expect(target, text).toBeTruthy()
  await target.trigger('click'); await flushPromises()
}
async function category(index) {
  await wrapper.findAll('nav button')[index].trigger('click'); await flushPromises()
}
it('switches groups without changing a partially assigned role or expanding legacy directories', async () => {
  await setup([130, 1, 3]); await category(9)
  expect(wrapper.findComponent({ name: 'ElTree' }).vm.getCheckedKeys(true)).toEqual([1])
  await category(4); await category(9)
  expect(wrapper.props('modelValue')).toEqual([130, 1, 3])
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
})
it('group selection and clearing preserve permissions in other categories', async () => {
  await setup([3]); await category(9); await button('本组全选')
  expect(wrapper.props('modelValue').sort()).toEqual([1, 2, 3])
  await button('清空本组'); expect(wrapper.props('modelValue')).toEqual([3])
})
it('a page checkbox selects its access and actions; deselecting one action keeps access', async () => {
  await setup(); await category(9)
  await wrapper.find('.el-tree-node input[type=checkbox]').setValue(true); await flushPromises()
  expect(wrapper.props('modelValue').sort()).toEqual([1, 2])
  await wrapper.find('.el-tree-node__expand-icon').trigger('click'); await flushPromises()
  const action = wrapper.findAll('.el-tree-node__content').find(n => n.text().includes('用户-新增'))
  await action.find('input[type=checkbox]').setValue(false); await flushPromises()
  expect(wrapper.props('modelValue')).toEqual([1])
})
it('search selection affects only results and keeps hidden assignments', async () => {
  await setup([1, 4]); await wrapper.find('input[placeholder="搜索目录、页面或操作"]').setValue('工单-查询')
  await flushPromises(); await button('选中结果')
  expect(wrapper.props('modelValue').sort()).toEqual([1, 3, 4])
  await button('清空结果'); expect(wrapper.props('modelValue').sort()).toEqual([1, 4])
})
it('clear preserves default personal feedback without granting management or role-inherited IDs', async () => {
  await setup([1], { lockedIds: [5], inheritedIds: [3] }); await button('清空')
  expect(wrapper.props('modelValue')).toEqual([5])
  await category(4)
  await wrapper.findAll('.el-tree-node__expand-icon').find(n => !n.classes().includes('expanded')).trigger('click')
  await flushPromises(); expect(wrapper.text()).toContain('角色已授予')
  expect(wrapper.props('modelValue')).not.toContain(3)
  expect(wrapper.props('modelValue')).not.toContain(6)
})
it('all selection emits real IDs only and clearing empties editable grants', async () => {
  await setup(); await button('全部权限')
  expect(wrapper.props('modelValue').sort()).toEqual([1, 2, 3, 4, 5, 6])
  await button('清空'); expect(wrapper.props('modelValue')).toEqual([])
})
