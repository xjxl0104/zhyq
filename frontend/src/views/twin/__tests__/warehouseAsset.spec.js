// @vitest-environment node
import { readFile } from 'node:fs/promises'
import { describe, it, expect } from 'vitest'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { Box3, Vector3 } from 'three'
import { MODEL, floorBase, modelHeight } from '../twinData'
import { bindWarehouse } from '../warehouseController'

describe('delivered Blender asset', () => {
  it('preserves coordinate scale and all floor systems through the Blender round trip', async () => {
    const bytes = await readFile(new URL('../../../../public/models/dipark-warehouse.glb', import.meta.url))
    const gltf = await new GLTFLoader().parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '')
    const model = bindWarehouse(gltf.scene)
    const bounds = new Box3()
    model.building.children.filter(object => !object.userData.runtimeOnly).forEach(object => bounds.expandByObject(object))
    const size = bounds.getSize(new Vector3())
    expect(size.x).toBeGreaterThan(96)
    expect(size.x).toBeLessThan(102)
    expect(size.y).toBeGreaterThan(modelHeight())
    expect(size.y).toBeLessThan(modelHeight() + 9)
    expect(model.building.children.filter(object => object.userData.twinRole === 'floor')).toHaveLength(MODEL.floors)
    model.setState({ mode: 'interior', floor: 5 })
    model.update(2)
    expect(model.floors.filter(f => f.group.visible).map(f => f.group.userData.floor)).toEqual([5])
    expect(model.floors[4].fire.children.length).toBeGreaterThan(0)
    expect(model.floors[4].interior.children.length).toBeGreaterThan(0)
    model.setState({ mode: 'exploded', floor: 5 })
    model.update(2)
    expect(model.floors[4].group.position.y).toBeCloseTo(floorBase(5) + 16, 2)
    expect(model.pointPosition({ position: [1, 24, 3], floor: 5 }).y).toBeCloseTo(40, 2)
    model.setState({ mode: 'exterior', floor: null })
    model.update(2)
    expect(model.floors.every(f => f.shell.visible && !f.fire.visible)).toBe(true)
    model.dispose()
  })
})
