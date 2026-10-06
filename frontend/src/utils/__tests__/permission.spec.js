import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { computed } from 'vue'
import { authApi } from '@/api/system'
import { useAccessStore } from '@/stores/access'
import { hasPermission } from '../permission'

vi.mock('@/api/system', () => ({ authApi: { myAccess: vi.fn() } }))

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  vi.clearAllMocks()
})

describe('effective action permissions', () => {
  it('uses current server grants with an identity-only token', async () => {
    localStorage.setItem('zhyq_token', 'identity-only-token')
    authApi.myAccess.mockResolvedValue({ admin: false, menus: [
      { id: 1, path: '/property/workorder' },
      { id: 2, perm: 'property:workorder:query' }
    ] })

    await useAccessStore().load()

    expect(hasPermission('property:workorder:query')).toBe(true)
    expect(hasPermission('property:workorder:edit')).toBe(false)
    expect(hasPermission('crm:customer:query')).toBe(false)
    expect(hasPermission('ROLE_admin')).toBe(false)
  })

  it('allows administrator actions even when the server omits individual grants', async () => {
    localStorage.setItem('zhyq_token', 'identity-only-token')
    authApi.myAccess.mockResolvedValue({ admin: true, menus: [] })

    await useAccessStore().load()

    expect(hasPermission('ROLE_admin')).toBe(true)
    expect(hasPermission('pur:supplierContract:add')).toBe(true)
  })

  it('does not restore stale permissions from a legacy token', () => {
    localStorage.setItem('zhyq_token', `header.${btoa(JSON.stringify({ auth: ['ROLE_admin', 'crm:customer:edit'] }))}.signature`)
    expect(hasPermission('ROLE_admin')).toBe(false)
    expect(hasPermission('crm:customer:edit')).toBe(false)
  })

  it('revokes access when the effective session is reset', () => {
    const access = useAccessStore()
    access.admin = true
    expect(hasPermission('crm:customer:edit')).toBe(true)

    access.reset()

    expect(hasPermission('crm:customer:edit')).toBe(false)
    expect(hasPermission('ROLE_admin')).toBe(false)
  })

  it('updates reactive button permissions when server grants change', () => {
    const access = useAccessStore()
    const canEdit = computed(() => hasPermission('property:workorder:edit'))
    expect(canEdit.value).toBe(false)

    access.menus = [{ id: 1, perm: 'property:workorder:edit' }]
    expect(canEdit.value).toBe(true)

    access.menus = []
    expect(canEdit.value).toBe(false)
  })

  it('fails closed before store initialization or for an invalid permission', () => {
    expect(hasPermission(undefined)).toBe(false)
    expect(hasPermission('')).toBe(false)
    setActivePinia(undefined)
    expect(hasPermission('ROLE_admin')).toBe(false)
  })
})
