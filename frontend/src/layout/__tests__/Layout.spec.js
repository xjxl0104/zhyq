import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Layout from '../Layout.vue'

const projectStore = {
  init: vi.fn().mockResolvedValue(undefined),
  reset: vi.fn(),
}

vi.mock('@/stores/project', () => ({ useProjectStore: () => projectStore }))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard', fullPath: '/dashboard', meta: { title: '工作台' } }),
  useRouter: () => ({
    push: vi.fn(),
    resolve: (path) => ({ href: path }),
  }),
}))

const passthrough = { template: '<div><slot /></div>' }

function mountLayout() {
  return mount(Layout, {
    global: {
      stubs: {
        'el-container': passthrough,
        'el-aside': passthrough,
        'el-scrollbar': passthrough,
        'el-menu': passthrough,
        'el-header': passthrough,
        'el-main': passthrough,
        'el-icon': passthrough,
        'el-dropdown': passthrough,
        'el-dropdown-menu': passthrough,
        'el-dropdown-item': passthrough,
        'el-avatar': passthrough,
        'router-view': passthrough,
        transition: passthrough,
        'keep-alive': passthrough,
        MenuItem: true,
        ProjectSwitcher: {
          name: 'ProjectSwitcher',
          template: '<div class="project-switcher-stub" />',
        },
        FeedbackFab: true,
        OfficeBuilding: true,
        Monitor: true,
      },
    },
  })
}

describe('Layout navigation chrome', () => {
  beforeEach(() => {
    projectStore.init.mockClear()
  })

  it('keeps the project switcher in the right actions before the monitor button', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.nav-left .project-switcher-stub').exists()).toBe(false)
    const switcher = wrapper.find('.actions .project-switcher-stub')
    expect(switcher.exists()).toBe(true)
    expect(switcher.element.nextElementSibling.classList.contains('screen-btn')).toBe(true)
  })

  it('does not render tags, assistant, or theme controls', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.tags-view-stub').exists()).toBe(false)
    expect(wrapper.findAll('.theme-btn')).toHaveLength(0)
  })

  it('keeps the existing menu inside the gooey selection layer', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.gooey-nav').exists()).toBe(true)
    expect(wrapper.find('.gooey-nav .side-menu').exists()).toBe(true)
  })
})
