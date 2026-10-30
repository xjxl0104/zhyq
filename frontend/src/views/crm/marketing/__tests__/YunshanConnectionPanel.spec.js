import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import YunshanConnectionPanel from '../YunshanConnectionPanel.vue'
import { mktYunshanApi } from '@/api/marketing'

const permissions = new Set()
vi.mock('@/api/marketing', () => ({ mktYunshanApi: { config: vi.fn(), saveConnection: vi.fn() } }))
vi.mock('@/utils/permission', () => ({ hasPermission: code => permissions.has(code) }))
vi.mock('element-plus', async original => ({ ...await original(), ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }))

const wrappers = []
function mount() {
  const wrapper = shallowMount(YunshanConnectionPanel, { props: { warehouseId: '7', warehouseName: '云山华南一区' } })
  wrappers.push(wrapper); return wrapper
}
beforeEach(() => {
  vi.clearAllMocks(); permissions.clear(); permissions.add('crm:marketing:erp:query'); permissions.add('crm:marketing:erp:config')
  mktYunshanApi.config.mockResolvedValue({ configured: false, baseUrl: '', appId: '', source: 'NONE' })
})
afterEach(() => wrappers.splice(0).forEach(wrapper => wrapper.unmount()))

describe('云仓发货量接口', () => {
  it('按云仓读取接口配置，没有客户也能先配置', async () => {
    const wrapper = mount(); await flushPromises()
    expect(mktYunshanApi.config).toHaveBeenCalledWith('7')
    expect(wrapper.vm.statusText).toBe('未配置')
  })

  it('验证并保存后刷新状态并通知父页面，凭证不留在表单里', async () => {
    mktYunshanApi.saveConnection.mockResolvedValue({ configured: true, ownerCount: 3 })
    const wrapper = mount(); await flushPromises()
    wrapper.vm.openDialog()
    expect(wrapper.vm.canSave).toBeFalsy()
    Object.assign(wrapper.vm.form, { baseUrl: ' https://own.example/api ', appId: 'own-app', publicKey: 'public-key' })
    mktYunshanApi.config.mockResolvedValue({ configured: true, baseUrl: 'https://own.example/api', appId: 'ow***pp', source: 'WAREHOUSE' })
    await wrapper.vm.save(); await flushPromises()
    expect(mktYunshanApi.saveConnection).toHaveBeenCalledWith('7', { mode: 'CUSTOM', baseUrl: 'https://own.example/api', appId: 'own-app', publicKey: 'public-key' })
    expect(wrapper.emitted('changed')).toHaveLength(1)
    expect(wrapper.vm.statusText).toBe('已配置')
    wrapper.vm.reset()
    expect(wrapper.vm.form.appId).toBe(''); expect(wrapper.vm.form.publicKey).toBe('')
  })

  it('验证失败时留在弹窗并显示原因，不通知父页面', async () => {
    mktYunshanApi.saveConnection.mockRejectedValue(new Error('接口验证失败，请核对地址、App ID 和公钥'))
    const wrapper = mount(); await flushPromises()
    wrapper.vm.openDialog()
    Object.assign(wrapper.vm.form, { baseUrl: 'https://own.example/api', appId: 'own-app', publicKey: 'bad' })
    await wrapper.vm.save(); await flushPromises()
    expect(wrapper.vm.dialog.visible).toBe(true)
    expect(wrapper.vm.dialog.error).toContain('接口验证失败')
    expect(wrapper.emitted('changed')).toBeUndefined()
  })

  it('只有查询权限时不能保存', async () => {
    permissions.delete('crm:marketing:erp:config')
    const wrapper = mount(); await flushPromises()
    wrapper.vm.openDialog()
    Object.assign(wrapper.vm.form, { baseUrl: 'https://own.example/api', appId: 'own-app', publicKey: 'k' })
    await wrapper.vm.save()
    expect(mktYunshanApi.saveConnection).not.toHaveBeenCalled()
  })
})
