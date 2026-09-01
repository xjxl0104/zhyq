import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AuroraBg from '../AuroraBg.vue'

describe('AuroraBg', () => {
  it('renders the container and degrades gracefully without WebGL', () => {
    // jsdom 无 WebGL 上下文:组件必须静默退化,不抛错、不挂载 canvas
    const wrapper = mount(AuroraBg, { attachTo: document.body })

    expect(wrapper.find('.aurora-bg').exists()).toBe(true)
    expect(() => wrapper.unmount()).not.toThrow()
  })
})
