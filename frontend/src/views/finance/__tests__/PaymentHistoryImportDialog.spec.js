import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessage } from 'element-plus'
import PaymentHistoryImportDialog from '../components/PaymentHistoryImportDialog.vue'
import { paymentApi } from '@/api/finance'

vi.mock('@/api/finance', () => ({ paymentApi: { previewHistory: vi.fn(), confirmHistory: vi.fn() } }))

const stubs = {
  ElDialog: { template: '<div><slot /><slot name="footer" /></div>' },
  ElUpload: { template: '<div><slot /></div>' },
  ElButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
  ElTable: { template: '<div class="table"><slot /></div>', props: ['data'] },
  ElTableColumn: true, ElTag: true
}
function report(overrides = {}) {
  return { totalRows: 2, okCount: 1, skippedCount: 0, errorCount: 0, billCount: 2, totalAmount: 1500, rows: [
    { rowNo: 2, status: 'ok', message: '将冲抵 2 张账单', allocations: [] }, { rowNo: 3, status: 'error', message: '金额无法识别', allocations: [] }], ...overrides }
}
async function mountWithFile() {
  const wrapper = shallowMount(PaymentHistoryImportDialog, { props: { modelValue: true }, global: { stubs } })
  wrapper.findComponent(stubs.ElUpload).exists()
  wrapper.vm.$.setupState.onFile({ raw: new File(['x'], 'x.csv'), name: 'x.csv' })
  await wrapper.vm.$nextTick()
  return wrapper
}
const button = (wrapper, text) => wrapper.findAll('button').find(item => item.text().startsWith(text))

beforeEach(() => { vi.clearAllMocks(); vi.spyOn(ElMessage, 'success').mockImplementation(() => {}) })

describe('历史收款导入弹窗', () => {
  it('选文件前不能预览或确认', () => {
    const wrapper = shallowMount(PaymentHistoryImportDialog, { props: { modelValue: true }, global: { stubs } })
    expect(button(wrapper, '上传并预览').element.disabled).toBe(true)
    expect(button(wrapper, '确认导入').element.disabled).toBe(true)
  })

  it('预览有错误行时不能确认导入，并默认只看有问题的行', async () => {
    paymentApi.previewHistory.mockResolvedValue(report({ errorCount: 1 }))
    const wrapper = await mountWithFile()
    await button(wrapper, '上传并预览').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('有问题 1 行')
    expect(button(wrapper, '确认导入').element.disabled).toBe(true)
    expect(paymentApi.confirmHistory).not.toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.visibleRows.map(row => row.rowNo)).toEqual([3])
  })

  it('预览零错误后确认导入，成功后通知刷新并关闭', async () => {
    paymentApi.previewHistory.mockResolvedValue(report({ rows: [{ rowNo: 2, status: 'ok', message: '', allocations: [] }] }))
    paymentApi.confirmHistory.mockResolvedValue(report({ rows: [] }))
    const wrapper = await mountWithFile()
    await button(wrapper, '上传并预览').trigger('click')
    await flushPromises()
    const confirm = button(wrapper, '确认导入')
    expect(confirm.element.disabled).toBe(false)
    await confirm.trigger('click')
    await flushPromises()
    expect(paymentApi.confirmHistory).toHaveBeenCalledTimes(1)
    expect(wrapper.emitted('done')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue').at(-1)).toEqual([false])
  })

  it('重新选择文件会清掉旧的预览，必须重新预览才能确认', async () => {
    paymentApi.previewHistory.mockResolvedValue(report({ rows: [] }))
    const wrapper = await mountWithFile()
    await button(wrapper, '上传并预览').trigger('click')
    await flushPromises()
    wrapper.vm.$.setupState.onFile({ raw: new File(['y'], 'y.csv'), name: 'y.csv' })
    await wrapper.vm.$nextTick()
    expect(button(wrapper, '确认导入').element.disabled).toBe(true)
  })
})
