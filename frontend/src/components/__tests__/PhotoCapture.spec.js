import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PhotoCapture from '../PhotoCapture.vue'
const dialog = { props: ['modelValue'], emits: ['opened', 'close'], template: '<section><slot /><slot name="footer" /></section>' }
const mountCamera = () => mount(PhotoCapture, { global: { stubs: { ElDialog: dialog, ElButton: { template: '<button><slot /></button>' }, ElAlert: true } } })
afterEach(() => vi.restoreAllMocks())
describe('camera lifecycle', () => {
  it('offers mobile rear-camera capture and emits the selected photo', async () => {
    const wrapper = mountCamera()
    const input = wrapper.get('input[type=file]')
    expect(input.attributes('capture')).toBe('environment')
    const file = new File(['photo'], '现场.jpg', { type: 'image/jpeg' })
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    expect(wrapper.emitted('capture')[0][0]).toBe(file)
    wrapper.unmount()
  })
  it('stops the camera when the dialog closes', async () => {
    const stop = vi.fn()
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] }) } })
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue()
    const wrapper = mountCamera()
    await wrapper.findAll('button')[0].trigger('click')
    wrapper.findComponent(dialog).vm.$emit('opened')
    await flushPromises()
    wrapper.findComponent(dialog).vm.$emit('close')
    await flushPromises()
    expect(stop).toHaveBeenCalledOnce()
    wrapper.unmount()
  })
})
