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

  it('renders the depth-stacked brand with the shortened title', () => {
    const wrapper = mountLayout()

    const brand = wrapper.get('.navbar .depth-brand')
    expect(brand.get('.depth-brand__face').text()).toBe('智慧云仓系统')
    expect(brand.findAll('.depth-brand__layer').length).toBeGreaterThan(1)
    expect(wrapper.find('.gradient-brand').exists()).toBe(false)
    wrapper.unmount()
  })

  it('keeps the switcher, monitor button and user in ordered action slots', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.nav-left .project-switcher-stub').exists()).toBe(false)
    const slots = wrapper.findAll('.actions .action-slot')
    expect(slots).toHaveLength(3)
    expect(slots[0].find('.project-switcher-stub').exists()).toBe(true)
    expect(slots[1].find('.screen-btn').exists()).toBe(true)
    expect(slots[2].find('.user').exists()).toBe(true)
  })

  it('announces the current page title to assistive tech', () => {
    const wrapper = mountLayout()

    expect(wrapper.get('[aria-live="polite"]').text()).toBe('工作台')
    wrapper.unmount()
  })

  it('shows the module index and marker line beside the page title', () => {
    const wrapper = mountLayout()

    const block = wrapper.get('.crumb-block')
    expect(block.find('.crumb-marker').exists()).toBe(true)
    expect(block.get('.crumb-index').text()).toBe('01')
    expect(block.get('.crumb').text()).toBe('工作台')
  })

  it('opens the monitoring screen without click particle chrome', async () => {
    const open = vi.fn()
    vi.stubGlobal('open', open)
    const wrapper = mountLayout()

    await wrapper.get('.screen-btn').trigger('click')

    expect(open).toHaveBeenCalledWith('/screen', '_blank')
    expect(wrapper.find('.navbar-burst').exists()).toBe(false)
    expect(wrapper.find('.navbar-burst__filter').exists()).toBe(false)
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

  it('mounts the brand inline in the unified top bar without a framed logo box', () => {
    const wrapper = mountLayout()

    expect(wrapper.find('.navbar .depth-brand').exists()).toBe(true)
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
