import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SpatialWorkOrders from '../SpatialWorkOrders.vue'
import { useProjectStore } from '@/stores/project'
import { useAccessStore } from '@/stores/access'

const { page } = vi.hoisted(() => ({ page: vi.fn() }))
vi.mock('@/api/property', () => ({ workOrderApi: { page } }))
const order = { id: 123, projectId: 3, buildingId: 5, floorId: 16, floorPlanFileId: 70, planX: .5, planY: .5, title: '测试故障', status: 3 }
let pinia, project, access
beforeEach(() => {
  pinia = createPinia(); setActivePinia(pinia)
  project = useProjectStore(); project.currentProjectId = 3
  access = useAccessStore(); access.admin = true
  page.mockReset().mockImplementation(async query => ({ total: query.status === 3 ? 1 : 0, records: query.status === 3 ? [order] : [] }))
})
const render = props => mount(SpatialWorkOrders, { props, global: { plugins: [pinia] } })
describe('live spatial work orders', () => {
  it('queries active work in the mapped project, maps only current drawings, and locates on click', async () => {
    page.mockImplementation(async query => ({ total: 3, records: query.status === 3 ? [order, { ...order, id: 124, floorPlanFileId: 1 }, { ...order, id: 125, projectId: 9 }] : [] }))
    const wrapper = render({ floor: 4 }); await flushPromises()
    expect(page.mock.calls.map(([q]) => q.status)).toEqual([1, 2, 3, 4, 7])
    for (const [q] of page.mock.calls) expect(q).toMatchObject({ projectId: 3, buildingId: 5, pageSize: 100 })
    const toggle = wrapper.get('.order-dock-toggle')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(toggle.classes()).toContain('pending')
    expect(toggle.text()).toContain('待处理 2')
    expect(wrapper.find('.order-drawer').exists()).toBe(false)
    expect(wrapper.emitted('points').at(-1)[0]).toHaveLength(1)
    await toggle.trigger('click')
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.text()).toContain('1 条待处理工单缺少标注')
    await wrapper.get('li button').trigger('click')
    expect(wrapper.emitted('locate')[0][0]).toMatchObject({ orderId: 123, floor: 4 })
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('.order-drawer').exists()).toBe(false)
    wrapper.unmount()
  })
  it('shows work across floors even while the model has a selected floor', async () => {
    const lowerOrder = { ...order, id: 124, floorId: 13, floorPlanFileId: 67, title: '一楼待处理工单' }
    page.mockImplementation(async q => ({ total: q.status === 3 ? 2 : 0, records: q.status === 3 ? [order, lowerOrder] : [] }))
    const wrapper = render({ floor: 1 }); await flushPromises()
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.findAll('li')).toHaveLength(2)
    expect(wrapper.get('ul').text()).toContain('4F · 测试故障')
    expect(wrapper.get('ul').text()).toContain('1F · 一楼待处理工单')
    await wrapper.setProps({ floor: 7 })
    expect(wrapper.findAll('li')).toHaveLength(2)
    await wrapper.get('li button').trigger('click')
    expect(wrapper.emitted('locate')[0][0]).toMatchObject({ floor: 4 })
    wrapper.unmount()
  })
  it('counts work without a usable point and provides a calibration hint without a locate action', async () => {
    page.mockImplementation(async q => ({ total: q.status === 1 ? 1 : 0, records: q.status === 1 ? [{ ...order, status: 1, planX: null }] : [] }))
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('.order-dock-toggle').text()).toContain('待处理 1')
    expect(wrapper.get('.order-dock-toggle').classes()).toContain('pending')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.text()).toContain('请在工单中补充定位')
    expect(wrapper.find('li button').exists()).toBe(false)
    expect(wrapper.emitted('locate')).toBeUndefined()
    wrapper.unmount()
  })
  it('keeps an empty dock calm and explains the empty state after expansion', async () => {
    page.mockResolvedValue({ total: 0, records: [] })
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('.order-dock-toggle').classes()).not.toContain('pending')
    expect(wrapper.get('.order-dock-toggle').text()).toContain('暂无待处理')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.get('.order-drawer').text()).toContain('暂无待处理工单。')
    expect(wrapper.find('li').exists()).toBe(false)
    await wrapper.get('section').trigger('keydown', { key: 'Escape' })
    expect(wrapper.get('.order-dock-toggle').attributes('aria-expanded')).toBe('false')
    wrapper.unmount()
  })
  it('discards a late response after the project changes and clears emitted pins', async () => {
    let resolve
    page.mockImplementationOnce(() => new Promise(done => { resolve = done }))
    const wrapper = render(); project.currentProjectId = 8; await flushPromises()
    resolve({ total: 1, records: [order] }); await flushPromises()
    expect(wrapper.emitted('points').at(-1)).toEqual([[]])
    expect(wrapper.find('section').exists()).toBe(false)
    expect(page).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })
  it('does not fetch without existing work-order access', async () => {
    access.admin = false
    const wrapper = render(); await flushPromises()
    expect(page).not.toHaveBeenCalled(); wrapper.unmount()
  })
  it('can locate a completed order explicitly opened from its detail view', async () => {
    page.mockImplementation(async q => ({ total: q.id ? 1 : 0, records: q.id ? [{ ...order, status: 5 }] : [] }))
    const wrapper = render({ requestedOrderId: '123' }); await flushPromises()
    expect(page).toHaveBeenLastCalledWith(expect.objectContaining({ id: 123, projectId: 3, buildingId: 5 }))
    expect(wrapper.emitted('locate')[0][0]).toMatchObject({ orderId: 123, status: '已完成' })
    expect(wrapper.get('.order-dock-toggle').text()).toContain('暂无待处理')
    expect(wrapper.get('.order-dock-toggle').classes()).not.toContain('pending')
    expect(wrapper.get('.order-dock-toggle').attributes('aria-expanded')).toBe('false')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.find('li').exists()).toBe(false)
    wrapper.unmount()
  })
  it('reports failed loading and allows retry', async () => {
    page.mockRejectedValueOnce(new Error('offline'))
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('.order-dock-toggle').text()).toContain('加载失败')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.get('[role="alert"]').text()).toContain('加载失败')
    await wrapper.get('.retry-button').trigger('click'); await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.get('.order-dock-toggle').text()).toContain('待处理 1')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    wrapper.unmount()
  })
  it('does not inflate the pending count when a response repeats the same order', async () => {
    page.mockImplementation(async q => ({ total: q.status === 3 ? 2 : 0, records: q.status === 3 ? [order, order] : [] }))
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('.order-dock-toggle').text()).toContain('待处理 1')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.findAll('li')).toHaveLength(1)
    wrapper.unmount()
  })
  it('labels a capped result as a partial count instead of presenting it as the full total', async () => {
    page.mockImplementation(async q => ({ total: q.status === 3 ? 1200 : 0, records: q.status === 3 ? Array.from({ length: 10 }, (_, i) => ({ ...order, id: q.pageNo * 100 + i })) : [] }))
    const wrapper = render(); await flushPromises()
    expect(page.mock.calls.filter(([q]) => q.status === 3)).toHaveLength(10)
    expect(wrapper.get('.order-dock-toggle').text()).toContain('待处理 100+')
    await wrapper.get('.order-dock-toggle').trigger('click')
    expect(wrapper.text()).toContain('当前显示部分工单')
    wrapper.unmount()
  })
})
