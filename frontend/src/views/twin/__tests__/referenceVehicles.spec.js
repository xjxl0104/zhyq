import * as THREE from 'three'
import { describe, expect, it } from 'vitest'
import { createWarehouse } from '../warehouseModel'

describe('reference-photo vehicle replacement', () => {
  it('keeps the photo fleet at human scale and clear of the building and entrance', () => {
    const model = createWarehouse()
    const vehicles = []
    model.root.traverse(object => { if (object.userData.vehicleKind) vehicles.push(object) })
    expect(vehicles.filter(object => object.userData.vehicleKind === 'truck')).toHaveLength(3)
    expect(vehicles.filter(object => object.userData.vehicleKind === 'car')).toHaveLength(4)
    const buildingFootprint = new THREE.Box3(new THREE.Vector3(-48, .3, -27), new THREE.Vector3(48, 60, 27))
    const entrance = new THREE.Box3(new THREE.Vector3(4, -.2, 46), new THREE.Vector3(41, 10, 65))
    for (const vehicle of vehicles) {
      const bounds = new THREE.Box3().setFromObject(vehicle)
      expect(bounds.intersectsBox(buildingFootprint)).toBe(false)
      expect(bounds.intersectsBox(entrance)).toBe(false)
      expect(bounds.max.x).toBeLessThan(61)
      const size = bounds.getSize(new THREE.Vector3())
      const length = Math.max(size.x, size.z)
      expect(length).toBeGreaterThan(vehicle.userData.vehicleKind === 'truck' ? 6.8 : 4.1)
      expect(length).toBeLessThan(vehicle.userData.vehicleKind === 'truck' ? 8 : 4.9)
    }
    let vehicleDraws = 0
    for (const vehicle of vehicles) vehicle.traverse(object => { if (object.isMesh) vehicleDraws++ })
    expect(vehicleDraws).toBeLessThanOrEqual(14)
    model.dispose()
  })
})
