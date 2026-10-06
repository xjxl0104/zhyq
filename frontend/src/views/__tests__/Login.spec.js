import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Login from '../Login.vue'
import { useAccessStore } from '@/stores/access'

const calls = vi.hoisted(() => ({ post: vi.fn(), myAccess: vi.fn(), replace: vi.fn() }))
vi.mock('@/utils/request', () => ({ default: { post: calls.post } }))
vi.mock('@/api/system', () => ({ authApi: { myAccess: calls.myAccess } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ replace: calls.replace }) }))

let wrapper
beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
  setActivePinia(createPinia())
  calls.post.mockResolvedValue({ token: 'compact-identity-token', username: 'login-test', nickname: '测试人员' })
  vi.spyOn(ElMessage, 'success').mockImplementation(() => {})
  wrapper = mount(Login, { global: { plugins: [ElementPlus], stubs: { OfficeBuilding: true } } })
})
afterEach(() => { wrapper?.unmount(); vi.restoreAllMocks() })

async function submit() {
  await wrapper.find('input[placeholder="账号"]').setValue('login-test')
  await wrapper.find('input[placeholder="密码"]').setValue('test-only')
  await wrapper.find('button.login-btn').trigger('click')
  await flushPromises()
}

describe('登录完成必须包括权限加载', () => {
  it('超级管理员加载权限成功后进入首页', async () => {
    calls.myAccess.mockResolvedValue({ admin: true, menus: [] })
    await submit()
    expect(calls.replace).toHaveBeenCalledWith('/dashboard')
    expect(useAccessStore().admin).toBe(true)
    expect(ElMessage.success).toHaveBeenCalledOnce()
  })

  it('物业角色登录后进入获授权的报修页', async () => {
    calls.myAccess.mockResolvedValue({ admin: false, menus: [
      { id: 61, name: '物业报修', type: 2, path: '/property/workorder', status: 1 }
    ] })
    await submit()
    expect(calls.replace).toHaveBeenCalledWith('/property/workorder')
  })

  it('权限加载失败时清除半完成的登录，不提示成功或跳转', async () => {
    calls.myAccess.mockRejectedValue(new Error('permission-load-failed'))
    await submit()
    expect(localStorage.getItem('zhyq_token')).toBeNull()
    expect(localStorage.getItem('zhyq_user')).toBeNull()
    expect(useAccessStore().admin).toBe(false)
    expect(calls.replace).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
  })
})
