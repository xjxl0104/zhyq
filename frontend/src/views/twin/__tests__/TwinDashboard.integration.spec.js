import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia } from 'pinia'
import ElementPlus, { ElSelect } from 'element-plus'
import * as icons from '@element-plus/icons-vue'
import { describe, it, expect, vi } from 'vitest'
import Layout from '@/layout/Layout.vue'
import ProjectSwitcher from '@/layout/ProjectSwitcher.vue'
import TwinDashboard from '../TwinDashboard.vue'
import { useProjectStore } from '@/stores/project'

// Only external data and GPU-heavy presentation are replaced; Layout/project switching are real.
vi.mock('@/api/building', () => ({ projectApi: { list: async () => [{ id: 101, name: '云仓项目甲' }, { id: 102, name: '云仓项目乙' }] } }))
vi.mock('@/components/GrainientBg.vue', () => ({ default: { template: '<div />' } }))
vi.mock('@/views/suggestion/FeedbackFab.vue', () => ({ default: { template: '<div />' } }))
const { sceneUnmounted } = vi.hoisted(() => ({ sceneUnmounted: vi.fn() }))
vi.mock('../WarehouseScene.vue', async () => {
  const { onBeforeUnmount } = await import('vue')
  return { default: { name: 'WarehouseScene', setup() { onBeforeUnmount(sceneUnmounted) }, template: '<div />' } }
})

describe('three dimensional homepage in the existing application layout', () => {
  it('initializes the active project, switches context and releases the scene when leaving', async () => {
    localStorage.clear()
    sceneUnmounted.mockClear()
    const pinia = createPinia()
    const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: Layout, children: [
      { path: 'dashboard', component: TwinDashboard },
      { path: 'contract/list', component: { template: '<div>合同列表</div>' } },
    ] }] })
    await router.push('/dashboard')
    const wrapper = mount({ template: '<router-view />' }, { global: { plugins: [pinia, router, ElementPlus], components: icons } })
    await flushPromises()
    expect(wrapper.getComponent(ProjectSwitcher).text()).toContain('云仓项目甲')
    expect(wrapper.find('.twin-topbar').exists()).toBe(false)
    expect(wrapper.find('.twin-sidebar').exists()).toBe(false)
    expect(wrapper.find('.sidebar').exists()).toBe(true)
    const unmountedBefore = sceneUnmounted.mock.calls.length
    wrapper.getComponent(ProjectSwitcher).getComponent(ElSelect).vm.$emit('change', 102)
    await flushPromises()
    expect(useProjectStore(pinia).currentProjectId).toBe(102)
    expect(wrapper.getComponent(ProjectSwitcher).text()).toContain('云仓项目乙')
    expect(sceneUnmounted.mock.calls.length).toBe(unmountedBefore + 1)
    const titles = wrapper.findAll('.el-sub-menu__title')
    await titles.find(item => item.text().includes('招商租赁')).trigger('click')
    await titles.find(item => item.text() === '合同').trigger('click')
    await wrapper.findAll('.el-menu-item').find(item => item.text() === '合同列表').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/contract/list')
    expect(sceneUnmounted.mock.calls.length).toBe(unmountedBefore + 2)
    expect(wrapper.text()).toContain('合同列表')
    wrapper.unmount()
    localStorage.clear()
  })
})
