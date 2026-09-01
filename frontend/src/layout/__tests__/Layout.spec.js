import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Layout from '../Layout.vue'
import MenuItem from '../MenuItem.vue'

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
  afterEach(() => vi.unstubAllGlobals())

  it('renders the outlined stroke brand with the shortened title', () => {
    const wrapper = mountLayout()

    const brand = wrapper.get('.sidebar .brand-zone .stroke-brand')
    expect(brand.attributes('aria-label')).toBe('智慧云仓系统')
    expect(brand.findAll('[data-stroke-char]')).toHaveLength(6)
    expect(brand.find('[data-fill-text]').text()).toBe('智慧云仓系统')
    expect(wrapper.find('.depth-brand').exists()).toBe(false)
    wrapper.unmount()
  })

  it('stacks brand, switcher, menu and user top-to-bottom in the sidebar', () => {
    const wrapper = mountLayout()

    const zones = wrapper.get('.sidebar').element.children
    expect(zones[0].className).toContain('brand-zone')
    expect(zones[1].className).toContain('switcher-zone')
    expect(zones[2].className).toContain('menu-scroll')
    expect(zones[3].className).toContain('user-zone')
    expect(wrapper.find('.switcher-zone .project-switcher-stub').exists()).toBe(true)
    expect(wrapper.find('.user-zone .user').exists()).toBe(true)
    expect(wrapper.find('.screen-btn').exists()).toBe(false)
  })

  it('announces the current page title to assistive tech', () => {
    const wrapper = mountLayout()

    expect(wrapper.get('[aria-live="polite"]').text()).toBe('工作台')
    wrapper.unmount()
  })

  it('does not render tags, assistant, or theme controls', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.tags-view-stub').exists()).toBe(false)
    expect(wrapper.findAll('.theme-btn')).toHaveLength(0)
  })

  it('renders the menu directly without the gooey selection layer', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.gooey-nav').exists()).toBe(false)
    expect(wrapper.find('.side-menu').exists()).toBe(true)
  })

  it('keeps the menu directly on the connected chrome without a glass panel', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.sidebar .liquid-glass').exists()).toBe(false)
    expect(wrapper.find('.sidebar .side-menu').exists()).toBe(true)
  })

  it('drops the top bar entirely in favor of the single sidebar chrome', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.navbar').exists()).toBe(false)
    expect(wrapper.find('.logo').exists()).toBe(false)
  })

  it('hands each top-level menu group its index for numbering', () => {
    const wrapper = mountLayout()

    const items = wrapper.findAllComponents(MenuItem)
    expect(items).toHaveLength(10)
    expect(items[0].props('topIndex')).toBe(0)
    expect(items[9].props('topIndex')).toBe(9)
  })
})
