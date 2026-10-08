// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { Raycaster, Vector3 } from 'three'
import { PLAN_FLOORS, planPointToLocal } from '../floorPlanData'
import { spatialWorkOrder } from '../workOrderSpatial'
import { createWarehouse } from '../warehouseModel'

const mark = (floor, u, v) => ({ projectId: 3, buildingId: 5, floorId: floor.floorId, floorPlanFileId: floor.fileId,
  planX: floor.anchors[0] + u * (floor.anchors[2] - floor.anchors[0]), planY: floor.anchors[1] + v * (floor.anchors[3] - floor.anchors[1]) })
describe('drawing-to-world positioning', () => {
  it('uses each drawing main grid instead of its image bounds, and rotates the partial basement', () => {
    for (const floor of PLAN_FLOORS.filter(f => f.floor > 0)) {
      const result = planPointToLocal(mark(floor, .25, .75))
      expect(result.floor).toBe(floor.floor)
      expect(result.position[0]).toBeCloseTo(-40)
      expect(result.position[2]).toBeCloseTo(18)
    }
    expect(planPointToLocal(mark(PLAN_FLOORS[7], 0, 0)).position).toEqual([42.5, .45, -12])
    expect(planPointToLocal(mark(PLAN_FLOORS[7], 1, 1)).position).toEqual([80, .45, -36])
  })
  it('rejects absent, outdated, off-plan or cross-building coordinates', () => {
    const valid = mark(PLAN_FLOORS[0], .5, .5)
    for (const invalid of [{ projectId: 2 }, { buildingId: 4 }, { floorId: 20 }, { floorPlanFileId: 65 }, { planX: null }, { planY: '' }, { planX: NaN }, { planY: Infinity }, { planX: .98 }]) {
      expect(planPointToLocal({ ...valid, ...invalid })).toBeNull()
    }
  })
  it('keeps floor-relative order coordinates through floor animation and isolates B1', () => {
    const model = createWarehouse()
    try {
      const point = spatialWorkOrder({ ...mark(PLAN_FLOORS[3], .25, .75), id: 123, title: '测试工单', status: 3 })
      for (const [mode, elevation, verticalScale, depthScale] of [['exterior', 28.1, 1, .75], ['exploded', 40.1, 1, .75], ['interior', .4, .6, .6]]) {
        model.setState({ mode, floor: 4 }); model.update(5)
        const actual = model.pointPosition(point)
        expect(actual.x).toBeCloseTo(-24)
        expect(actual.z).toBeCloseTo(18 * depthScale)
        expect(actual.y).toBeCloseTo(elevation + .45 * verticalScale)
      }
      model.setState({ mode: 'interior', floor: -1 }); model.update(5)
      expect(model.floors.filter(f => f.group.visible).map(f => f.group.userData.floor)).toEqual([-1])
      model.setState({ mode: 'exterior', floor: null }); model.update(5)
      expect(model.floors[7].group.visible).toBe(false)
    } finally { model.dispose() }
  })
  it('keeps the terrace visible inside and does not seal a shared lift/lobby doorway', () => {
    const model = createWarehouse()
    try {
      model.setState({ mode: 'interior', floor: 7 }); model.update(5)
      const roof = model.building.children.find(o => o.userData.twinRole === 'roof')
      expect(roof.visible).toBe(false)
      expect(model.floors[6].structure.children.find(o => o.userData.planRole === 'overhead').visible).toBe(false)
      model.setState({ mode: 'interior', floor: 3 }); model.update(5); model.root.updateMatrixWorld(true)
      // South passenger lift opens into its adjacent lobby, which must have the same gap.
      const ray = new Raycaster(new Vector3((51.25 - 80) * .6, 1, (67 - 36) * .6), new Vector3(0, 0, 1), 0, 1)
      const walls = model.floors[2].interior.children.filter(o => o.material?.name === 'plan-wall')
      expect(ray.intersectObjects(walls, true)).toHaveLength(0)
    } finally { model.dispose() }
  })
})
