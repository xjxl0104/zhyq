import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import IcpFooter from '../IcpFooter.vue'

describe('IcpFooter', () => {
  it('links the ICP filing number to the MIIT filing system in a new tab', () => {
    const wrapper = mount(IcpFooter)

    const link = wrapper.get('a')
    expect(link.text()).toBe('粤ICP备2026136948号-1')
    expect(link.attributes('href')).toBe('https://beian.miit.gov.cn/')
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toContain('noopener')
  })

  it('shows the operating company beside the number by default', () => {
    const wrapper = mount(IcpFooter)

    expect(wrapper.text()).toContain('广州数智云仓产业园运营有限公司')
  })

  it('keeps only the filing link in compact mode', () => {
    const wrapper = mount(IcpFooter, { props: { compact: true } })

    expect(wrapper.classes()).toContain('is-compact')
    expect(wrapper.text()).toBe('粤ICP备2026136948号-1')
    expect(wrapper.get('a').attributes('href')).toBe('https://beian.miit.gov.cn/')
  })

  it('splits the number into three lines only when stacked, without changing its text', () => {
    const wrapper = mount(IcpFooter, { props: { compact: true, stacked: true } })

    expect(wrapper.classes()).toContain('is-stacked')
    expect(wrapper.findAll('a .part').map((p) => p.text())).toEqual(['粤ICP备', '2026136948', '号-1'])
    expect(wrapper.get('a').text()).toBe('粤ICP备2026136948号-1')
  })
})
