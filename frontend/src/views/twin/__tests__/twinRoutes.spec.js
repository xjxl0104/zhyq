import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/layout/Layout.vue', () => ({ default: { template: '<router-view />' } }))
vi.mock('@/views/twin/TwinHome.vue', () => ({ default: { template: '<div>twin</div>' } }))
vi.mock('@/views/Login.vue', () => ({ default: { template: '<div>login</div>' } }))
describe('twin preview routing boundaries', () => {
  beforeEach(() => { localStorage.clear(); vi.resetModules(); vi.unstubAllEnvs() })
  it('allows development preview while keeping real business routes behind login', async () => {
    const { default: router } = await import('@/router/index.js')
    await router.push('/twin-preview')
    expect(router.currentRoute.value.path).toBe('/twin-preview')
    await router.push('/iot/fire')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(localStorage.getItem('zhyq_token')).toBeNull()
  })
  it('does not register preview routes in a production environment', async () => {
    vi.stubEnv('DEV', false)
    const { default: router } = await import('@/router/index.js')
    expect(router.getRoutes().some(route => route.path.startsWith('/twin-preview'))).toBe(false)
    expect(router.resolve('/dashboard').name).toBe('Dashboard')
    expect(router.resolve('/dashboard').matched.map(record => record.path)).toEqual(['/', '/dashboard'])
    expect(router.resolve('/overview').name).toBe('Overview')
    vi.unstubAllEnvs()
  })
})
