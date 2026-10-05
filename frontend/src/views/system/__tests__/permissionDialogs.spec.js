import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { shallowMount, flushPromises } from '@vue/test-utils'
import Role from '../Role.vue'
import User from '../User.vue'
const api = vi.hoisted(() => ({ menuApi: { list: vi.fn() }, roleApi: { page: vi.fn(), list: vi.fn(), menuIds: vi.fn(), saveMenuIds: vi.fn() }, userApi: { page: vi.fn(), get: vi.fn() } }))
vi.mock('@/api/system', () => api)
const menus = [{ id: 1, status: 1, name: '用户', type: 2, path: '/system/user' }, { id: 2, status: 1, name: '角色', type: 2, path: '/system/role' }]
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
let wrapper
beforeEach(() => {
  vi.resetAllMocks()
  api.roleApi.page.mockResolvedValue({ records: [], total: 0 }); api.roleApi.list.mockResolvedValue([])
  api.userApi.page.mockResolvedValue({ records: [], total: 0 }); api.menuApi.list.mockResolvedValue(menus)
})
afterEach(() => wrapper?.unmount())
it('saves exactly selected role grants', async () => {
  api.roleApi.menuIds.mockResolvedValue([1]); wrapper = shallowMount(Role); await flushPromises()
  await wrapper.vm.openPermission({ id: 10, name: '测试角色' }); await wrapper.vm.savePermissions()
  expect(api.roleApi.saveMenuIds).toHaveBeenCalledWith(10, [1])
})
it('cannot save after permission loading fails', async () => {
  api.roleApi.menuIds.mockRejectedValue(new Error('network')); wrapper = shallowMount(Role); await flushPromises()
  await expect(wrapper.vm.openPermission({ id: 10 })).rejects.toThrow('network')
  await wrapper.vm.savePermissions(); expect(api.roleApi.saveMenuIds).not.toHaveBeenCalled()
})
it('ignores stale grants when switching roles while loading', async () => {
  const old = deferred(); api.roleApi.menuIds.mockImplementation(id => id === 10 ? old.promise : Promise.resolve([2]))
  wrapper = shallowMount(Role); await flushPromises()
  const first = wrapper.vm.openPermission({ id: 10 }); await wrapper.vm.openPermission({ id: 20 })
  old.resolve([1]); await first; await wrapper.vm.savePermissions()
  expect(api.roleApi.saveMenuIds).toHaveBeenCalledWith(20, [2])
})
it('retains user grants when details arrive before menu definitions', async () => {
  const pending = deferred(); api.menuApi.list.mockReturnValue(pending.promise)
  api.userApi.get.mockResolvedValue({ user: { id: 10 }, roleIds: [], menuIds: [1, 2] })
  wrapper = shallowMount(User); await wrapper.vm.openDialog({ id: 10 })
  expect(wrapper.vm.form.menuIds).toEqual([1, 2])
  pending.resolve(menus); await flushPromises(); expect(wrapper.vm.form.menuIds).toEqual([1, 2])
})
