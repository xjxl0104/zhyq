import { describe, expect, it } from 'vitest'
import { hexToRgb, lerpPalette } from '../palette'

describe('palette utils', () => {
  it('converts hex to normalized rgb and tolerates bad input', () => {
    expect(hexToRgb('#ff0000')).toEqual([1, 0, 0])
    expect(hexToRgb('00ff00')).toEqual([0, 1, 0])
    expect(hexToRgb('oops')).toEqual([1, 1, 1])
  })

  it('lerps palettes channel-wise', () => {
    const mid = lerpPalette(['#000000'], ['#ffffff'], 0.5)
    expect(mid[0].map(v => Math.round(v * 100) / 100)).toEqual([0.5, 0.5, 0.5])
    expect(lerpPalette(['#102030'], ['#405060'], 0)[0]).toEqual(hexToRgb('#102030'))
  })
})
