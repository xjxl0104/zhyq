import { describe, expect, it } from 'vitest'

import { money } from '../format'

describe('money — 会计专用金额格式', () => {
  it('大额加千分位、固定两位小数', () => {
    expect(money(3533300)).toBe('3,533,300.00')
    expect(money(1706300)).toBe('1,706,300.00')
    expect(money(9400)).toBe('9,400.00')
  })

  it('小数四舍五入到两位', () => {
    expect(money(4723.5)).toBe('4,723.50')
    expect(money(0.067)).toBe('0.07')
    expect(money('131600')).toBe('131,600.00')
  })

  it('空值与非数值按 0.00 显示,不出 NaN', () => {
    expect(money(null)).toBe('0.00')
    expect(money(undefined)).toBe('0.00')
    expect(money('')).toBe('0.00')
    expect(money('abc')).toBe('0.00')
  })

  it('零与负数(红冲)', () => {
    expect(money(0)).toBe('0.00')
    expect(money(-9400)).toBe('-9,400.00')
  })
})
