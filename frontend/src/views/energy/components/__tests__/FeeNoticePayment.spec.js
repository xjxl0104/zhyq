import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import FeeNoticePayment from '../FeeNoticePayment.vue'
import { feePeriodApi } from '@/api/energy'
import { fileApi } from '@/api/file'
import { startFileDownload } from '@/utils/fileDownload'
import { ElMessageBox } from 'element-plus'

vi.mock('@/api/energy', () => ({ feePeriodApi: { updateNoticePayment: vi.fn() } }))
vi.mock('@/api/file', () => ({ fileApi: { list: vi.fn(), upload: vi.fn(), remove: vi.fn() } }))
vi.mock('@/utils/fileDownload', () => ({ startFileDownload: vi.fn() }))
vi.mock('element-plus', async (original) => ({
  ...await original(),
  ElMessage: { success: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))
const notice = { id: 12, noticeNo: 'DIPARK-12', tenantName: '测试租户', unitName: '3层A区', paidAmount: 100, totalDue: 230 }
const dialog = { props: ['modelValue'], template: '<section v-if="modelValue"><slot /><footer><slot name="footer" /></footer></section>' }
const upload = { name: 'ElUpload', props: { httpRequest: Function, beforeUpload: Function, multiple: Boolean, accept: String, disabled: Boolean }, template: '<div><slot /></div>' }
const block = { template: '<div><slot /></div>' }
function form(overrides = {}) {
  return mount(FeeNoticePayment, {
    props: { modelValue: true, notice: { ...notice }, ...overrides },
    global: { stubs: {
      ElDialog: dialog, ElUpload: upload, ElDescriptions: block, ElDescriptionsItem: block,
      ElForm: block, ElFormItem: block,
      ElAlert: { props: ['title'], template: '<div role="alert">{{ title }}</div>' }
    } }
  })
}
function button(wrapper, label) { return wrapper.findAll('button').find(item => item.text() === label) }
function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => { resolve = ok; reject = fail })
  return { promise, resolve, reject }
}
beforeEach(() => {
  vi.clearAllMocks()
  fileApi.list.mockResolvedValue([])
  feePeriodApi.updateNoticePayment.mockResolvedValue({ ...notice, paymentStatus: 1 })
  ElMessageBox.confirm.mockResolvedValue('confirm')
})

describe('缴费通知单资料与状态', () => {
  it('旧通知单默认未缴，只保存缴费状态且不修改原金额', async () => {
    const wrapper = form()
    await flushPromises()
    expect(fileApi.list).toHaveBeenCalledWith('fee_notice_payment', 12)
    expect(wrapper.findAll('input[type="radio"]').map(input => input.element.checked)).toEqual([true, false])
    await wrapper.findAll('input[type="radio"]')[1].setValue(true)
    await button(wrapper, '保存状态').trigger('click')
    await flushPromises()
    expect(feePeriodApi.updateNoticePayment).toHaveBeenCalledWith(12, { paymentStatus: 1 })
    expect(wrapper.emitted('saved')).toEqual([[{ id: 12, paymentStatus: 1 }]])
    expect(wrapper.props('notice')).toMatchObject({ paidAmount: 100, totalDue: 230 })
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    wrapper.unmount()
  })
  it('支持并发上传文档和压缩包，全部完成前不能保存或关闭', async () => {
    const wrapper = form()
    await flushPromises()
    const uploader = wrapper.findComponent({ name: 'ElUpload' })
    expect(uploader.props('multiple')).toBe(true)
    expect(uploader.props('accept')).toBeUndefined()
    const first = deferred(), second = deferred()
    fileApi.upload.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const office = new File(['office'], '回单.xlsx')
    const zip = new File(['zip'], '缴费资料.zip')
    expect(uploader.props('beforeUpload')(office)).toBe(true)
    expect(uploader.props('beforeUpload')(zip)).toBe(true)
    const a = uploader.props('httpRequest')({ file: office })
    const b = uploader.props('httpRequest')({ file: zip })
    await flushPromises()
    expect(button(wrapper, '保存状态').element.disabled).toBe(true)
    expect(button(wrapper, '关闭').element.disabled).toBe(true)
    for (const [data, options] of fileApi.upload.mock.calls) {
      expect(data.get('bizType')).toBe('fee_notice_payment')
      expect(data.get('bizId')).toBe('12')
      expect(options).toEqual({ timeout: 10 * 60 * 1000 })
    }
    second.resolve({ id: 22, originalName: zip.name })
    await b; await flushPromises()
    expect(button(wrapper, '保存状态').element.disabled).toBe(true)
    first.resolve({ id: 21, originalName: office.name })
    await a; await flushPromises()
    expect(wrapper.findAll('.file-name').map(item => item.text())).toEqual(['缴费资料.zip', '回单.xlsx'])
    expect(button(wrapper, '保存状态').element.disabled).toBe(false)
    wrapper.unmount()
  })
  it('删除失败保留资料，下载继续走鉴权下载接口', async () => {
    fileApi.list.mockResolvedValue([{ id: 7, originalName: '付款证明.pdf' }])
    fileApi.remove.mockRejectedValue(new Error('无权删除该附件'))
    const wrapper = form()
    await flushPromises()
    await button(wrapper, '删除').trigger('click')
    await flushPromises()
    expect(fileApi.remove).toHaveBeenCalledWith(7)
    expect(wrapper.find('.file-name').text()).toBe('付款证明.pdf')
    expect(wrapper.find('[role="alert"]').text()).toContain('无权删除')
    await button(wrapper, '下载').trigger('click')
    expect(startFileDownload).toHaveBeenCalledWith(7, '付款证明.pdf')
    wrapper.unmount()
  })
  it('加载失败明确显示重试，不能当作无资料覆盖状态', async () => {
    fileApi.list.mockRejectedValueOnce(new Error('资料加载超时')).mockResolvedValueOnce([{ id: 8, originalName: '原缴费回单.png' }])
    const wrapper = form()
    await flushPromises()
    expect(wrapper.text()).toContain('资料加载超时')
    expect(wrapper.text()).not.toContain('暂无缴费资料')
    expect(button(wrapper, '保存状态').element.disabled).toBe(true)
    await button(wrapper, '重新加载资料').trigger('click')
    await flushPromises()
    expect(wrapper.find('.file-name').text()).toBe('原缴费回单.png')
    expect(button(wrapper, '保存状态').element.disabled).toBe(false)
    wrapper.unmount()
  })
  it('切换通知单后忽略上一个单据迟到的资料响应', async () => {
    const old = deferred()
    fileApi.list.mockReturnValueOnce(old.promise).mockResolvedValueOnce([{ id: 2, originalName: '新租户.pdf' }])
    const wrapper = form()
    await wrapper.setProps({ notice: { ...notice, id: 99, tenantName: '新租户', paymentStatus: 1 } })
    await flushPromises()
    old.resolve([{ id: 1, originalName: '旧租户.pdf' }])
    await flushPromises()
    expect(wrapper.findAll('.file-name').map(item => item.text())).toEqual(['新租户.pdf'])
    expect(wrapper.findAll('input[type="radio"]').map(input => input.element.checked)).toEqual([false, true])
    wrapper.unmount()
  })
  it('保存失败不关闭、不发送成功事件，并可重试', async () => {
    feePeriodApi.updateNoticePayment.mockRejectedValueOnce(new Error('保存失败'))
    const wrapper = form()
    await flushPromises()
    await button(wrapper, '保存状态').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('保存失败')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(button(wrapper, '保存状态').element.disabled).toBe(false)
    wrapper.unmount()
  })
  it('旧上传在切换单据后返回时不会污染新单据的资料或忙态', async () => {
    const pending = deferred()
    fileApi.upload.mockReturnValue(pending.promise)
    const wrapper = form()
    await flushPromises()
    const request = wrapper.findComponent({ name: 'ElUpload' }).props('httpRequest')({ file: new File(['a'], '旧回单.pdf') })
    await wrapper.setProps({ notice: { ...notice, id: 99 } })
    await flushPromises()
    pending.resolve({ id: 44, originalName: '旧回单.pdf' })
    await request; await flushPromises()
    expect(wrapper.findAll('.file-name')).toHaveLength(0)
    expect(button(wrapper, '保存状态').element.disabled).toBe(false)
    wrapper.unmount()
  })
})
