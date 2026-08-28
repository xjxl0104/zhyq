import { beforeEach, describe, expect, it } from 'vitest'
import { resetToLightTheme } from '../lightTheme'

describe('resetToLightTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.className = ''
  })

  it('clears the retired dark preference and root class', () => {
    localStorage.setItem('zhyq_dark', '1')
    document.documentElement.classList.add('dark')

    resetToLightTheme()

    expect(localStorage.getItem('zhyq_dark')).toBeNull()
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
