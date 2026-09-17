import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { describe, expect, it } from 'vitest'
import { addReferenceWordmark } from '../referenceWordmark.js'

const materials = Object.fromEntries(['facadeLogoTeal', 'facadeLogoBlue', 'facadeLogoWhite']
  .map(name => [name, new THREE.MeshStandardMaterial({ name })]))

function hasInk(mesh, x, y) {
  mesh.updateMatrixWorld(true)
  const ray = new THREE.Raycaster(new THREE.Vector3(x, y, 1), new THREE.Vector3(0, 0, -1))
  return ray.intersectObject(mesh).length > 0
}

describe('photographed facade wordmark', () => {
  it('spells DIPARK with only the open D and solid I before PARK', () => {
    const group = new THREE.Group()
    addReferenceWordmark(group, materials, { x: 0, y: 0, z: 0, height: 1, variant: 'roof' })
    const [d, i] = group.children
    expect(group.children.map(mesh => mesh.userData.wordmarkPart)).toEqual(['open-d', 'stem', 'P', 'A', 'R', 'K'])
    expect(hasInk(d, .1, .5)).toBe(false)
    expect(hasInk(d, .85, .5)).toBe(true)
    expect(hasInk(i, 1.28, .5)).toBe(true)
  })

  it('uses the entrance teal and blue on every sign, with open counters and mergeable meshes', () => {
    for (const variant of ['roof', 'box']) {
      const group = new THREE.Group()
      addReferenceWordmark(group, materials, { x: 0, y: 0, z: 0, height: 1, variant })
      expect(new Set(group.children.slice(0, 2).map(mesh => mesh.material.name))).toEqual(new Set(['facadeLogoTeal']))
      expect(new Set(group.children.slice(2).map(mesh => mesh.material.name)))
        .toEqual(new Set(['facadeLogoBlue']))
      const p = group.children[2]
      expect(hasInk(p, 1.67 + .28, .76)).toBe(false)
      expect(hasInk(p, 1.67 + .045, .3)).toBe(true)
      const geometries = group.children.map(mesh => {
        expect(mesh.geometry.index).not.toBeNull()
        const geometry = mesh.geometry.clone().deleteAttribute('uv')
        mesh.updateMatrix()
        return geometry.applyMatrix4(mesh.matrix)
      })
      const merged = mergeGeometries(geometries)
      expect(merged).not.toBeNull()
      merged.computeBoundingBox()
      expect(merged.boundingBox.max.x).toBeCloseTo(4.83, 1)
      expect(merged.boundingBox.max.y - merged.boundingBox.min.y).toBeCloseTo(1)
      merged.dispose()
      geometries.forEach(geometry => geometry.dispose())
      group.children.forEach(mesh => mesh.geometry.dispose())
    }
  })
})
