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
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.text()).toContain('另有 1 条')
    await wrapper.get('li button').trigger('click')
    expect(wrapper.emitted('locate')[0][0]).toMatchObject({ orderId: 123, floor: 4 })
    await wrapper.setProps({ floor: 1 }); expect(wrapper.findAll('li')).toHaveLength(0)
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
    wrapper.unmount()
  })
  it('reports failed loading and allows retry', async () => {
    page.mockRejectedValueOnce(new Error('offline'))
    const wrapper = render(); await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('加载失败')
    await wrapper.get('header button').trigger('click'); await flushPromises()
    expect(wrapper.findAll('li')).toHaveLength(1)
    wrapper.unmount()
  })
})
