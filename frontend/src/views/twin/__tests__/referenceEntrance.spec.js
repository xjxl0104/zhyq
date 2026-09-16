import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { describe, expect, it } from 'vitest'
import { upgradeReferenceEntrance } from '../referenceEntrance.js'

const materials = Object.fromEntries(['facadeLogoTeal', 'facadeLogoBlue']
  .map(name => [name, new THREE.MeshStandardMaterial({ name })]))

function fixture() {
  const scene = new THREE.Group()
  scene.position.set(7, 2, -4)
  scene.rotation.y = .3
  scene.scale.set(1.2, .9, 1.1)
  const entrance = new THREE.Group()
  entrance.name = 'warehouse-site-entrance'
  entrance.userData.sitePart = 'entrance'
  scene.add(entrance)
  const di = new THREE.Mesh(new THREE.BoxGeometry(1.77, 1.56, .118))
  di.name = 'Entrance_DIPARK_DI'
  di.userData.source_text = 'DI'
  di.position.set(14.185, 7.906, 59.67)
  entrance.add(di)
  // The delivered Blender asset batches PARK and the Chinese name together.
  // Their common transform rotates the original text's local X/Z plane upright;
  // PARK has local x < 0 while the original Chinese origin remains x = 0.
  const park = new THREE.BoxGeometry(5.15, .118, 1.56).translate(-3.225, 0, -.726)
  const chinese = new THREE.BoxGeometry(6.06, .088, .87).translate(3.03, 0, -.3)
  const name = new THREE.Mesh(mergeGeometries([park, chinese]))
  name.name = 'Entrance_Chinese_park_name'
  name.userData.source_text = '数智云仓产业园 / PARK'
  name.position.set(21.17, 7.18, 59.67)
  name.rotation.x = Math.PI / 2
  entrance.add(name)
  const guardhouse = new THREE.Mesh(new THREE.BoxGeometry(8, 4, 5))
  guardhouse.name = 'guardhouse'
  guardhouse.position.set(7, 2, 56)
  entrance.add(guardhouse)
  scene.updateMatrixWorld(true)
  return { scene, entrance, name, guardhouse }
}

function cornersInEntrance(object, entrance) {
  entrance.updateWorldMatrix(true, true)
  const relative = new THREE.Matrix4().copy(entrance.matrixWorld).invert().multiply(object.matrixWorld)
  const geometry = object.geometry.clone().applyMatrix4(relative)
  geometry.computeBoundingBox()
  return geometry.boundingBox
}

describe('reference entrance wordmark upgrade', () => {
  it('replaces old Latin letters while retaining the Chinese mesh, normals and gate transforms', () => {
    const { entrance, name, guardhouse } = fixture()
    const nameTransform = name.matrixWorld.toArray()
    const gateTransform = guardhouse.matrixWorld.toArray()
    const originalChinese = name.geometry.attributes.position.array.slice(24 * 3)
    const originalNormals = name.geometry.attributes.normal.array.slice(24 * 3)
    const result = upgradeReferenceEntrance(entrance, materials)
    expect(entrance.getObjectByName('Entrance_DIPARK_DI')).toBeUndefined()
    expect(entrance.getObjectByName('Entrance_Chinese_park_name')).toBe(name)
    expect(name.userData.source_text).toBe('数智云仓产业园')
    expect(name.matrixWorld.toArray()).toEqual(nameTransform)
    expect(guardhouse.matrixWorld.toArray()).toEqual(gateTransform)
    expect(name.geometry.attributes.position.array).toEqual(originalChinese)
    expect(name.geometry.attributes.normal.array).toEqual(originalNormals)
    expect(result.children).toHaveLength(7)
    expect(result.children.slice(0, 3).map(child => child.userData.wordmarkPart)).toEqual(['open-d', 'stem', 'open-c'])
    expect(result.children.slice(3).every(child => child.material.name === 'facadeLogoBlue')).toBe(true)
    const signBounds = new THREE.Box3()
    result.children.forEach(child => signBounds.union(cornersInEntrance(child, entrance)))
    expect(signBounds.min.x).toBeCloseTo(13.3, 5)
    expect(signBounds.max.x).toBeCloseTo(20.52, 5)
    expect(signBounds.min.y).toBeCloseTo(7.126, 5)
    expect(signBounds.max.y).toBeLessThan(8.4)
    expect(signBounds.max.x).toBeLessThan(cornersInEntrance(name, entrance).min.x - .6)
    expect((signBounds.max.z + signBounds.min.z) / 2).toBeCloseTo(59.67, 5)
  })

  it('is idempotent after scene cloning and retains the saved placement', () => {
    const { entrance } = fixture()
    const first = upgradeReferenceEntrance(entrance, materials)
    const chinese = entrance.getObjectByName('Entrance_Chinese_park_name')
    const chineseGeometry = chinese.geometry
    expect(upgradeReferenceEntrance(entrance, materials)).toBe(first)
    expect(chinese.geometry).toBe(chineseGeometry)
    const clone = entrance.clone(true)
    const sharedMaterials = Object.fromEntries(['facadeLogoTeal', 'facadeLogoBlue']
      .map(name => [name, new THREE.MeshStandardMaterial({ name })]))
    const existingGeometry = clone.getObjectByName('entrance-reference-wordmark').children.map(mesh => mesh.geometry)
    const second = upgradeReferenceEntrance(clone, sharedMaterials)
    expect(clone.children.filter(child => child.userData.entranceWordmarkVersion)).toHaveLength(1)
    expect(second.children).toHaveLength(7)
    expect(second.position.toArray()).toEqual(first.position.toArray())
    expect(second.userData).toEqual(first.userData)
    expect(second.children.map(mesh => mesh.geometry)).toEqual(existingGeometry)
    expect(second.children.slice(0, 3).every(mesh => mesh.material === sharedMaterials.facadeLogoTeal)).toBe(true)
    expect(second.children.slice(3).every(mesh => mesh.material === sharedMaterials.facadeLogoBlue)).toBe(true)
  })
})
