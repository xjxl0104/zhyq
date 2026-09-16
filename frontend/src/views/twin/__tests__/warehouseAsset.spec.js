// @vitest-environment node
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { spawnSync } from 'node:child_process'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, it, expect } from 'vitest'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { Box3, Vector3 } from 'three'
import { MODEL, POINTS, floorBase, modelHeight } from '../twinData'
import { bindWarehouse } from '../warehouseController'

const assetUrl = new URL('../../../../public/models/dipark-warehouse.glb', import.meta.url)
const parseAsset = bytes => new GLTFLoader().parseAsync(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength), '')
const findEntrance = root => {
  let entrance
  root.traverse(object => { if (object.userData.sitePart === 'entrance') entrance = object })
  return entrance
}

describe('delivered warehouse asset', () => {
  it('preserves coordinate scale and all floor systems after GLB loading', async () => {
    const gltf = await parseAsset(await readFile(assetUrl))
    const model = bindWarehouse(gltf.scene)
    const bounds = new Box3()
    model.building.children.filter(object => !object.userData.runtimeOnly).forEach(object => bounds.expandByObject(object))
    const size = bounds.getSize(new Vector3())
    expect(size.x).toBeGreaterThan(96)
    expect(size.x).toBeLessThan(102)
    expect(size.y).toBeGreaterThan(modelHeight())
    expect(size.y).toBeLessThan(modelHeight() + 9)
    expect(model.building.children.filter(object => object.userData.twinRole === 'floor')).toHaveLength(MODEL.floors)
    for (const floor of model.floors) {
      for (const role of ['shell', 'structure', 'interior', 'fire']) expect(floor[role].children.length).toBeGreaterThan(0)
    }
    model.setState({ mode: 'interior', floor: 5 })
    model.update(2)
    expect(model.floors.filter(f => f.group.visible).map(f => f.group.userData.floor)).toEqual([5])
    expect(model.floors[4].fire.children.length).toBeGreaterThan(0)
    expect(model.floors[4].interior.children.length).toBeGreaterThan(0)
    model.setState({ mode: 'exploded', floor: 5 })
    model.update(2)
    expect(model.floors[4].group.position.y).toBeCloseTo(floorBase(5) + 16, 2)
    expect(model.pointPosition({ position: [1, 24, 3], floor: 5 }).y).toBeCloseTo(40, 2)
    for (const point of POINTS) {
      expect(model.pointPosition(point).y).toBeCloseTo(point.position[1] + (point.floor - 1) * 4, 2)
    }
    model.setState({ mode: 'exterior', floor: null })
    model.update(2)
    expect(model.floors.every(f => f.shell.visible && !f.fire.visible)).toBe(true)
    for (const point of POINTS) {
      expect(model.pointPosition(point).distanceTo(new Vector3(...point.position))).toBeLessThan(.001)
    }
    model.dispose()
  })

  it('delivers the reference trucks and cars with their body colours in the loaded asset', async () => {
    const gltf = await parseAsset(await readFile(assetUrl))
    const vehicles = gltf.scene.getObjectByName('warehouse-photo-vehicles')
    expect(vehicles?.userData.sitePart).toBe('vehicles')
    expect(vehicles.children.filter(vehicle => vehicle.userData.vehicleKind === 'truck')).toHaveLength(3)
    expect(vehicles.children.filter(vehicle => vehicle.userData.vehicleKind === 'car')).toHaveLength(4)
    for (const vehicle of vehicles.children) {
      expect(vehicle.userData.vehicleId).toBeTruthy()
      expect(vehicle.userData.referenceBodyColour).toMatch(/^#[a-f\d]{6}$/i)
      const meshes = []
      vehicle.traverse(object => { if (object.isMesh) meshes.push(object) })
      expect(meshes).toHaveLength(1)
      expect(meshes[0].geometry.getAttribute('color')?.count).toBe(meshes[0].geometry.getAttribute('position').count)
      expect(meshes[0].material.vertexColors).toBe(true)
    }
  })

  it('rebuilds an actual GLB without losing hidden floor systems or the existing entrance', async () => {
    const directory = await mkdtemp(join(tmpdir(), 'warehouse-rebuild-'))
    try {
      const source = join(directory, 'source.glb')
      const output = join(directory, 'rebuilt.glb')
      // Exercise inherited transforms too: simply reparenting the imported
      // entrance would put the old lettering and gate in the wrong place.
      const originalBytes = await readFile(assetUrl)
      const jsonLength = originalBytes.readUInt32LE(12)
      const json = JSON.parse(originalBytes.subarray(20, 20 + jsonLength).toString())
      const site = json.nodes.find(node => node.extras?.twinRole === 'site')
      delete site.matrix
      site.translation = [7, 2, -4]
      site.rotation = [0, Math.sin(.15), 0, Math.cos(.15)]
      site.scale = [1.2, 1.2, 1.2]
      const jsonBytes = Buffer.from(JSON.stringify(json))
      const paddedJson = Buffer.alloc(Math.ceil(jsonBytes.length / 4) * 4, 0x20)
      jsonBytes.copy(paddedJson)
      const remainingChunks = originalBytes.subarray(20 + jsonLength)
      const header = Buffer.from(originalBytes.subarray(0, 20))
      header.writeUInt32LE(20 + paddedJson.length + remainingChunks.length, 8)
      header.writeUInt32LE(paddedJson.length, 12)
      await writeFile(source, Buffer.concat([header, paddedJson, remainingChunks]))
      const original = await parseAsset(await readFile(source))
      original.scene.updateMatrixWorld(true)
      const sourceEntrance = findEntrance(original.scene)
      const sourceBounds = new Box3().setFromObject(sourceEntrance)

      const script = fileURLToPath(new URL('../../../../scripts/rebuild-warehouse-model.mjs', import.meta.url))
      for (const args of [[script, output, source], [script, output]]) {
        const result = spawnSync(process.execPath, args, { encoding: 'utf8' })
        expect(result.status, result.stderr || result.stdout).toBe(0)
        const rebuilt = await parseAsset(await readFile(output))
        rebuilt.scene.updateMatrixWorld(true)
        const entrance = findEntrance(rebuilt.scene)
        expect(entrance?.userData).toEqual(sourceEntrance.userData)
        expect(entrance.children.map(child => child.name).sort()).toEqual(sourceEntrance.children.map(child => child.name).sort())
        expect(entrance.getObjectByName('Entrance_Chinese_park_name')?.isMesh).toBe(true)
        expect(entrance.getObjectByName('Entrance_DIPARK_DI')?.isMesh).toBe(true)
        const bounds = new Box3().setFromObject(entrance)
        expect(bounds.min.distanceTo(sourceBounds.min)).toBeLessThan(.0001)
        expect(bounds.max.distanceTo(sourceBounds.max)).toBeLessThan(.0001)
        const runtimeObjects = []
        rebuilt.scene.traverse(object => { if (object.userData.runtimeOnly) runtimeObjects.push(object) })
        expect(runtimeObjects).toHaveLength(0)
        const model = bindWarehouse(rebuilt.scene)
        expect(model.floors).toHaveLength(7)
        for (const floor of model.floors) {
          for (const role of ['shell', 'structure', 'interior', 'fire']) expect(floor[role].children.length).toBeGreaterThan(0)
        }
        model.setState({ mode: 'interior', floor: 5 })
        model.update(2)
        expect(model.floors.filter(floor => floor.group.visible).map(floor => floor.group.userData.floor)).toEqual([5])
        expect(model.floors[4].interior.visible && model.floors[4].fire.visible).toBe(true)
        model.dispose()
      }
    } finally {
      await rm(directory, { recursive: true, force: true })
    }
  }, 30000)
})
