import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import MobileRecordList from '@/components/MobileRecordList.vue'

describe('MobileRecordList', () => {
  it('renders scoped card content with stable row keys', () => {
    const rows = [
      { code: 'WO-20260917-001', title: '空调漏水' },
      { code: 'WO-20260917-002', title: '门禁故障' }
    ]

    const wrapper = mount(MobileRecordList, {
      props: { items: rows, rowKey: 'code' },
      slots: {
        title: ({ row, index }) => `${index + 1}. ${row.code}`,
        default: ({ row }) => `<span class="record-title">${row.title}</span>`
      }
    })

    expect(wrapper.findAll('[data-record-key]')).toHaveLength(2)
    expect(wrapper.find('[data-record-key="WO-20260917-001"]').text()).toContain('空调漏水')
  })

  it('forwards the clicked row through the actions slot', async () => {
    const onOpen = vi.fn()
    const rows = [
      { id: 1, title: '空调漏水' },
      { id: 2, title: '门禁故障' }
    ]
    const wrapper = mount(MobileRecordList, {
      props: { items: rows },
      slots: {
        actions: ({ row }) => h('button', {
          class: 'open-record',
          onClick: () => onOpen(row)
        }, '打开')
      }
    })

    await wrapper.findAll('.open-record')[1].trigger('click')
    expect(onOpen).toHaveBeenCalledWith(rows[1])
  })

  it('labels each record and action group from its heading', () => {
    const wrapper = mount(MobileRecordList, {
      props: { items: [{ id: 17, code: 'WO-017' }] },
      slots: {
        title: ({ row }) => row.code,
        actions: () => h('button', { class: 'open-record' }, '详情')
      }
    })

    const article = wrapper.get('article')
    const heading = article.get('h2')
    const actions = article.get('[role="group"]')

    expect(heading.text()).toBe('WO-017')
    expect(article.attributes('aria-labelledby')).toBe(heading.attributes('id'))
    expect(actions.attributes('aria-labelledby')).toBe(heading.attributes('id'))
  })

  it('keeps heading references unique across list instances with the same row key', () => {
    const row = { id: 17, code: 'WO-017' }
    const Host = defineComponent({
      render() {
        const list = () => h(MobileRecordList, { items: [row] }, {
          title: ({ row: item }) => item.code,
          actions: () => h('button', '详情')
        })
        return h('div', [list(), list()])
      }
    })
    const wrapper = mount(Host)
    const articles = wrapper.findAll('article')
    const headingIds = articles.map(article => article.get('h2').attributes('id'))

    expect(articles).toHaveLength(2)
    expect(new Set(headingIds)).toHaveLength(2)
    articles.forEach((article, index) => {
      expect(article.attributes('aria-labelledby')).toBe(headingIds[index])
      expect(article.get('[role="group"]').attributes('aria-labelledby')).toBe(headingIds[index])
    })
  })

  it('applies the page row class hook to the matching card', () => {
    const wrapper = mount(MobileRecordList, {
      props: {
        items: [{ id: 17 }, { id: 18 }],
        rowClassName: ({ row }) => row.id === 17 ? 'source-highlight-row' : ''
      }
    })

    expect(wrapper.get('[data-record-key="17"]').classes()).toContain('source-highlight-row')
    expect(wrapper.get('[data-record-key="18"]').classes()).not.toContain('source-highlight-row')
  })

  it('shows loading and empty states without rendering record cards', async () => {
    const wrapper = mount(MobileRecordList, {
      props: { items: [], loading: true, emptyText: '暂无工单' },
      global: {
        stubs: {
          ElSkeleton: { template: '<div data-state="loading">加载中</div>' },
          ElEmpty: { props: ['description'], template: '<div data-state="empty">{{ description }}</div>' }
        }
      }
    })

    expect(wrapper.get('[data-state="loading"]').exists()).toBe(true)
    expect(wrapper.find('[data-state="empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-record-key]').exists()).toBe(false)

    await wrapper.setProps({ loading: false })

    expect(wrapper.get('[data-state="empty"]').text()).toBe('暂无工单')
    expect(wrapper.find('[data-state="loading"]').exists()).toBe(false)
  })
})
