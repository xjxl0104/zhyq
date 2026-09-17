import { mount, flushPromises } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import Layout from '../Layout.vue'

const route = reactive({ path: '/dashboard', fullPath: '/dashboard', meta: { title: '首页' } })
vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push: vi.fn(), back: vi.fn(), resolve: path => ({ href: path }) })
}))
vi.mock('@/stores/project', () => ({ useProjectStore: () => ({ init: async () => {}, reset() {} }) }))
vi.mock('@/utils/request', () => ({ default: { post: vi.fn() } }))
let wrapper
let media
let listeners
function render() {
  wrapper = mount(Layout, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        GrainientBg: true, StrokeBrand: true, FeedbackFab: true,
        ProjectSwitcher: { template: '<button>选择项目</button>' },
        MenuItem: { emits: ['leaf'], template: '<button @click="$emit(\'leaf\', { path: \'/finance/bill\' })">账单入口</button>' },
        'router-view': true,
      }
    }
  })
  return wrapper
}
beforeEach(() => {
  listeners = new Set()
  media = { matches: true, addEventListener: (_, fn) => listeners.add(fn), removeEventListener: (_, fn) => listeners.delete(fn) }
  vi.stubGlobal('matchMedia', query => query.includes('max-width') ? media : { ...media, matches: true })
  localStorage.clear()
  route.fullPath = route.path = '/dashboard'
})
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
  vi.unstubAllGlobals()
})
async function resize(mobile) {
  media.matches = mobile
  for (const fn of listeners) fn({ matches: mobile })
  await nextTick()
}
describe('phone navigation', () => {
  it('opens a modal menu, closes on selection, and leaves desktop preference unchanged', async () => {
    localStorage.setItem('zhyq_sidebar_collapsed', '1')
    render()
    await wrapper.get('button[aria-label="打开菜单"]').trigger('click')
    await flushPromises()
    const drawer = document.querySelector('[role="dialog"]')
    expect(drawer?.textContent).toContain('账单入口')
    drawer.querySelector('.mobile-drawer-menu button').click()
    await nextTick()
    expect(wrapper.get('button[aria-label="打开菜单"]').attributes('aria-expanded')).toBe('false')
    expect(localStorage.getItem('zhyq_sidebar_collapsed')).toBe('1')
  })
  it('closes the menu when the route or viewport changes', async () => {
    render()
    await wrapper.get('button[aria-label="打开菜单"]').trigger('click')
    route.fullPath = route.path = '/finance/bill'
    await nextTick()
    expect(wrapper.get('button[aria-label="打开菜单"]').attributes('aria-expanded')).toBe('false')
    await wrapper.get('button[aria-label="打开菜单"]').trigger('click')
    await resize(false)
    expect(wrapper.find('button[aria-label="打开菜单"]').exists()).toBe(false)
    expect(wrapper.find('.sidebar').exists()).toBe(true)
    await resize(true)
    expect(wrapper.get('button[aria-label="打开菜单"]').attributes('aria-expanded')).toBe('false')
  })
})
