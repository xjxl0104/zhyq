import { flushPromises, mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { describe, expect, it, vi } from 'vitest'
import WorkOrderLocation from '../WorkOrderLocation.vue'
import { floorApi, buildingApi } from '@/api/building'
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ currentProjectId: 3 }) }))
vi.mock('@/api/building', () => ({
  buildingApi: { list: vi.fn().mockResolvedValue([{ id: 2, name: '主楼' }, { id: 8, name: '副楼' }]) },
  floorApi: { list: vi.fn().mockResolvedValue([{ id: 4, name: '三层' }]), plan: vi.fn().mockResolvedValue({ id: 15 }) }
}))
const mountLocation = modelValue => mount(WorkOrderLocation, { props: { modelValue }, global: { plugins: [ElementPlus], stubs: { FloorPlanViewer: true } } })

describe('work-order location association', () => {
  it('loads the saved plan version without replacing it with the latest floor plan', async () => {
    floorApi.plan.mockClear()
    const wrapper = mountLocation({ buildingId: 2, floorId: 4, zone: 'A', planFileId: 5, x: 0.2, y: 0.8 })
    await flushPromises()
    expect(buildingApi.list).toHaveBeenCalledWith(3)
    expect(floorApi.plan).not.toHaveBeenCalled()
    expect(wrapper.findComponent({ name: 'FloorPlanViewer' }).props('fileId')).toBe(5)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })
  it('clears the old floor and point when switching buildings', async () => {
    const wrapper = mountLocation({ buildingId: 2, floorId: 4, zone: 'A', planFileId: 5, x: 0.2, y: 0.8 })
    await flushPromises()
    wrapper.findAllComponents({ name: 'ElSelect' })[0].vm.$emit('change', 8)
    await flushPromises()
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual({ buildingId: 8, floorId: null, zone: null, planFileId: null, x: null, y: null })
    expect(floorApi.list).toHaveBeenLastCalledWith(8)
    wrapper.unmount()
  })
  it('clears the zone when selecting a different floor', async () => {
    const wrapper = mountLocation({ buildingId: 2, floorId: 4, zone: 'B', planFileId: 5, x: 0.2, y: 0.8 })
    await flushPromises()
    wrapper.findAllComponents({ name: 'ElSelect' })[1].vm.$emit('change', 6)
    expect(wrapper.emitted('update:modelValue').at(-1)[0]).toEqual({
      buildingId: 2, floorId: 6, zone: null, planFileId: null, x: null, y: null
    })
    wrapper.unmount()
  })
})
