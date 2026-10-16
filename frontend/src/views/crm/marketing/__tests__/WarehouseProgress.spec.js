import { shallowMount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Warehouse from '../Warehouse.vue'
import { mktWarehouseApi } from '@/api/marketing'

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/utils/permission', () => ({ hasPermission: () => true }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ projects: [] }) }))
vi.mock('@/api/marketing', () => ({ mktWarehouseApi: { page: vi.fn(), get: vi.fn(), steps: vi.fn() } }))
const row = { id: 7, name: '数智-云仓', joinStatus: 5 }
const history = [1, 2, 3, 4, 5].map(step => ({ step, status: 2, doneTime: '2026-10-12 14:39:07' }))
const wrappers = []
function mount() {
  const w = shallowMount(Warehouse, { global: { directives: { loading: {} }, stubs: {
    Search: true, Plus: true,
    ElDrawer: { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /></section>' },
    ElSteps: { template: '<div><slot /></div>' },
    ElStep: { props: ['title', 'status'], template: '<article :data-status="status"><h3>{{ title }}</h3><slot name="description" /></article>' },
    ElTag: { template: '<span><slot /></span>' },
    ElAlert: { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
    ElButton: { template: '<button><slot /></button>' }
  } } })
  wrappers.push(w)
  return w
}
beforeEach(() => {
  vi.resetAllMocks()
  mktWarehouseApi.page.mockResolvedValue({ records: [{ ...row }], total: 1 })
  mktWarehouseApi.get.mockResolvedValue({ ...row })
  mktWarehouseApi.steps.mockResolvedValue(history)
})
afterEach(() => wrappers.splice(0).forEach(w => w.unmount()))

describe('加盟进度与当前状态一致', () => {
  it('退出后展示最新退出状态，已完成上线保留为历史，并同步列表', async () => {
    mktWarehouseApi.get.mockResolvedValue({ ...row, joinStatus: 7 })
    const w = mount(); await flushPromises(); await w.vm.openSteps(row); await flushPromises()
    expect(w.text()).toContain('当前加盟状态：退出')
    expect(w.text()).toContain('上线（历史记录）')
    expect(w.text()).toContain('完成于 2026-10-12 14:39:07')
    expect(w.findAll('article').at(-1).text()).toContain('当前状态：退出')
    expect(w.findAll('article').at(-1).attributes('data-status')).toBe('error')
    expect(w.vm.list[0].joinStatus).toBe(7)
    expect(w.text()).not.toContain('审核后可选择')
    expect(mktWarehouseApi.get).toHaveBeenCalledWith(7)
  })
  it.each([[6, '暂停'], [8, '冻结待删除']])('状态 %s 显示当前状态而不是仍上线', async (joinStatus, label) => {
    mktWarehouseApi.get.mockResolvedValue({ ...row, joinStatus, deleteDueAt: '2026-10-23T12:00:00', deleteReason: '误录' })
    const w = mount(); await w.vm.openSteps(row); await flushPromises()
    expect(w.text()).toContain(`当前加盟状态：${label}`)
    expect(w.findAll('article').at(-1).text()).toContain(`当前状态：${label}`)
    if (joinStatus === 8) {
      expect(w.text()).toContain('历史待删除记录，正在处理')
      expect(w.text()).not.toContain('2026-10-23')
      expect(w.text()).toContain('删除原因：误录')
    }
  })
  it('加盟尚未完成就冻结时，不再把待办节点显示为进行中', async () => {
    mktWarehouseApi.get.mockResolvedValue({ ...row, joinStatus: 8 })
    mktWarehouseApi.steps.mockResolvedValue([{ step: 2, status: 1 }])
    const w = mount(); await w.vm.openSteps(row); await flushPromises()
    expect(w.find('article').attributes('data-status')).toBe('wait')
    expect(w.text()).not.toContain('进行中')
  })
  it('恢复上线后刷新，移除旧的暂停提示', async () => {
    mktWarehouseApi.get.mockResolvedValueOnce({ ...row, joinStatus: 6 }).mockResolvedValueOnce({ ...row })
    const w = mount(); await w.vm.openSteps(row); await w.vm.openSteps(row); await flushPromises()
    expect(w.text()).toContain('当前加盟状态：已上线')
    expect(w.findAll('article')).toHaveLength(5)
    expect(w.text()).not.toContain('上线（历史记录）')
    expect(w.text()).not.toContain('云仓已暂停')
  })
  it('读取失败不展示旧进度，可重试恢复', async () => {
    mktWarehouseApi.get.mockRejectedValueOnce(new Error('暂时无法读取')).mockResolvedValueOnce({ ...row, joinStatus: 7 })
    const w = mount(); await w.vm.openSteps(row); await flushPromises()
    expect(w.text()).toContain('暂时无法读取')
    expect(w.findAll('article')).toHaveLength(0)
    await w.findAll('button').find(b => b.text() === '重试').trigger('click'); await flushPromises()
    expect(w.text()).toContain('当前加盟状态：退出')
  })
  it('切换云仓或关闭抽屉后，忽略迟到响应', async () => {
    let resolveFirst
    mktWarehouseApi.get.mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
    const w = mount(); const first = w.vm.openSteps(row)
    mktWarehouseApi.get.mockResolvedValue({ id: 8, name: '另一云仓', joinStatus: 7 })
    await w.vm.openSteps({ id: 8 }); resolveFirst(row); await first; await flushPromises()
    expect(w.vm.steps.row.id).toBe(8)
    let resolveClosed
    mktWarehouseApi.get.mockImplementationOnce(() => new Promise(resolve => { resolveClosed = resolve }))
    const pending = w.vm.openSteps(row); w.vm.closeSteps(); resolveClosed({ ...row, joinStatus: 6 }); await pending
    expect(w.vm.steps.list).toEqual([])
    expect(w.vm.steps.row.joinStatus).toBe(5)
  })
})
