import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import MenuItem from '../MenuItem.vue'

const stubs = {
  'el-sub-menu': { template: '<div class="sub-stub"><slot name="title" /><slot /></div>' },
  'el-menu-item': { template: '<div class="leaf-stub"><slot /><slot name="title" /></div>' },
  'el-icon': true,
}

describe('MenuItem', () => {
  it('prefixes a top-level group with a zero-padded index, children stay unnumbered', () => {
    const wrapper = mount(MenuItem, {
      props: {
        item: { title: '财务', icon: 'Money', children: [{ title: '所有账单', path: '/finance/bill' }] },
        topIndex: 3,
      },
      global: { stubs },
    })

    expect(wrapper.get('.menu-index').text()).toBe('04')
    expect(wrapper.findAll('.menu-index')).toHaveLength(1)
  })

  it('omits the index by default and wraps labels for proximity styling', () => {
    const wrapper = mount(MenuItem, {
      props: { item: { title: '所有账单', path: '/finance/bill' } },
      global: { stubs },
    })

    expect(wrapper.find('.menu-index').exists()).toBe(false)
    expect(wrapper.get('.menu-label').text()).toBe('所有账单')
  })
})
