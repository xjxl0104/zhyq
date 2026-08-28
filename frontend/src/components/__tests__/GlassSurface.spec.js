import { h } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import GlassSurface from '../GlassSurface.vue'

describe('GlassSurface', () => {
  it.each(['upload', 'card'])('renders the %s variant with inert decoration', (variant) => {
    const wrapper = mount(GlassSurface, {
      props: { variant },
      slots: { default: '<span class="real-content">业务内容</span>' },
    })

    expect(wrapper.classes()).toContain(`glass-surface--${variant}`)
    expect(wrapper.find('.real-content').text()).toBe('业务内容')
    expect(wrapper.findAll('[aria-hidden="true"]')).toHaveLength(2)
  })

  it('keeps slotted controls interactive above its visual layers', async () => {
    const onClick = vi.fn()
    const wrapper = mount(GlassSurface, {
      props: { variant: 'upload' },
      slots: {
        default: () => h('button', { class: 'real-control', onClick }, '选择文件'),
      },
    })

    await wrapper.find('.real-control').trigger('click')

    expect(onClick).toHaveBeenCalledOnce()
    expect(wrapper.find('.glass-surface__content').exists()).toBe(true)
  })
})
