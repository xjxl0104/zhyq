import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import Layout from '../Layout.vue'

// 手机宽度:Layout 没有侧栏,备案号要出现在内容区下方的底栏里
vi.mock('@/composables/useResponsive', () => ({ useResponsive: () => ({ isMobile: ref(true) }) }))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ init: vi.fn().mockResolvedValue(undefined), reset: vi.fn() }) }))
vi.mock('@/stores/access', () => ({ useAccessStore: () => ({ navigation: [], reset: vi.fn() }) }))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/dashboard', fullPath: '/dashboard', meta: { title: '工作台' } }),
  useRouter: () => ({ push: vi.fn(), resolve: (path) => ({ href: path }) }),
}))

const passthrough = { template: '<div><slot /></div>' }

describe('Layout ICP footer on mobile', () => {
  it('renders the filing link in a bar below the content, not in a sidebar', () => {
    const wrapper = mount(Layout, {
      global: {
        stubs: {
          'el-container': passthrough, 'el-aside': passthrough, 'el-main': passthrough, 'el-scrollbar': passthrough,
          'el-menu': passthrough, 'el-icon': passthrough, 'el-drawer': passthrough, 'el-button': passthrough,
          'el-dropdown': passthrough, 'el-dropdown-menu': passthrough, 'el-dropdown-item': passthrough, 'el-avatar': passthrough,
          'router-view': passthrough, transition: passthrough, 'keep-alive': passthrough,
          MenuItem: true, ProjectSwitcher: true, FeedbackFab: true, SectionTabs: true, GrainientBg: true,
        },
      },
    })

    const bar = wrapper.get('.app-wrapper.is-mobile > .mobile-icp')
    expect(bar.get('a').attributes('href')).toBe('https://beian.miit.gov.cn/')
    expect(bar.text()).toBe('粤ICP备2026136948号-1')
    expect(wrapper.find('.sidebar').exists()).toBe(false)
    wrapper.unmount()
  })
})
