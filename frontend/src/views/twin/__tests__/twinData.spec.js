import { describe, expect, it } from 'vitest'
import { FLOORS, MODEL, POINTS, floorBase, floorHeight, moduleDestination, visiblePoints } from '../twinData'

describe('warehouse model business contracts', () => {
  it('uses the seven storeys and unequal heights supplied in the park brochure', () => {
    expect(MODEL.floors).toBe(7)
    expect(FLOORS.map(floor => floorHeight(floor.id))).toEqual([12, 9.5, 6.6, 6.6, 6.6, 6.6, 6.6])
    expect(floorBase(3)).toBeCloseTo(21.5)
    expect(floorBase(7) + floorHeight(7)).toBeCloseTo(54.5)
  })
  it('keeps local previews separate from protected existing modules', () => {
    expect(moduleDestination('contract')).toBe('/contract/list')
    expect(moduleDestination('camera')).toBe('/iot/camera')
    expect(moduleDestination('fire')).toBe('/iot/fire')
    expect(moduleDestination('fire', true)).toBe('/twin-preview/module/fire')
    expect(moduleDestination('unknown', true)).toBeNull()
  })
  it('uses contiguous floor elevations and keeps every point on a modeled floor', () => {
    FLOORS.slice(0, -1).forEach(floor => expect(floorBase(floor.id) + floorHeight(floor.id)).toBeCloseTo(floorBase(floor.id + 1)))
    POINTS.forEach(point => expect(point.floor).toBeGreaterThanOrEqual(1))
    POINTS.forEach(point => expect(point.floor).toBeLessThanOrEqual(MODEL.floors))
  })
  it('filters exploded floor points by both system and floor', () => {
    expect(visiblePoints('fire', 3, 'exploded').map(point => point.id)).toEqual(['fire-01'])
    expect(visiblePoints('fire', 1, 'exploded')).toEqual([])
    expect(visiblePoints('camera', null, 'exterior').map(point => point.module)).toEqual(['camera'])
  })
})
