// @vitest-environment node
import { createHash } from 'node:crypto'
import { describe, expect, it, vi } from 'vitest'
import * as THREE from 'three'
import { createParkLandscape } from '../parkLandscape.js'
import { createParkTreeLod } from '../parkTreeLod.js'

const canopies = landscape => {
  const meshes = []
  landscape.group.traverse(object => {
    if (object.isInstancedMesh && object.material.name === 'contextLeaf') meshes.push(object)
  })
  return meshes
}
const treeTriangles = landscape => {
  let triangles = 0
  landscape.group.traverse(object => {
    if (object.isInstancedMesh && ['contextLeaf', 'contextBark'].includes(object.material.name)) {
      triangles += object.geometry.index.count / 3 * object.count
    }
  })
  return triangles
}

describe('park tree distance detail', () => {
  it('preserves all existing tree transforms and colours while giving sectors tight culling bounds', () => {
    const landscape = createParkLandscape(new THREE.Scene(), { root: new THREE.Group() })
    try {
      const trees = canopies(landscape)
      const matrix = new THREE.Matrix4(), colour = new THREE.Color(), placements = []
      for (const mesh of trees) {
        for (let index = 0; index < mesh.count; index++) {
          mesh.getMatrixAt(index, matrix); mesh.getColorAt(index, colour)
          placements.push([...matrix.elements, ...colour.toArray()].map(value => Number(value.toFixed(6))))
        }
      }
      placements.sort((a, b) => a[12] - b[12] || a[14] - b[14])
      expect(placements).toHaveLength(292)
      // Captured from the delivered scene before sectoring. Reordering instances
      // must not accidentally reseed each sector's rotations or leaf colours.
      expect(createHash('sha256').update(JSON.stringify(placements)).digest('hex'))
        .toBe('e132dbbcf29e03c6793c55e9ddecb230690de8df7aba131b6ef7703d2208c31e')
      expect(trees.length).toBeGreaterThan(1)
      for (const mesh of trees) {
        expect(mesh.frustumCulled).toBe(true)
        expect(mesh.boundingSphere.radius).toBeLessThan(90)
      }
    } finally { landscape.dispose() }
  })

  it('reduces distant geometry, restores it on zoom, and does not dirty a stationary scene', () => {
    const landscape = createParkLandscape(new THREE.Scene(), { root: new THREE.Group() })
    const camera = new THREE.PerspectiveCamera(42, 1.6, .2, 1800)
    camera.position.set(0, 100, 1500); camera.lookAt(0, 0, 0); camera.updateMatrixWorld()
    try {
      expect(typeof landscape.update).toBe('function')
      const originalTriangles = treeTriangles(landscape)
      expect(landscape.update(camera)).toBe(true)
      const distantTriangles = treeTriangles(landscape)
      expect(distantTriangles).toBeLessThan(originalTriangles * .3)
      expect(landscape.update(camera)).toBe(false)
      camera.zoom = 40; camera.updateProjectionMatrix()
      expect(landscape.update(camera)).toBe(true)
      expect(treeTriangles(landscape)).toBe(originalTriangles)
      expect(landscape.update(camera)).toBe(false)
    } finally { landscape.dispose() }
  })

  it('uses hysteresis at each boundary without rewriting instance matrices or colours', () => {
    const group = new THREE.Group(), resources = new Set()
    const trunk = new THREE.MeshStandardMaterial(), leaf = new THREE.MeshStandardMaterial()
    const trees = createParkTreeLod(group, [[0, 0, 1]], { trunk, leaf }, resource => { resources.add(resource); return resource })
    const mesh = group.children[1], original = mesh.geometry
    const matrixVersion = mesh.instanceMatrix.version, colourVersion = mesh.instanceColor.version
    const camera = new THREE.PerspectiveCamera(42, 1, .2, 1800)
    const at = distance => { camera.position.set(0, 7.8, distance); return trees.update(camera) }
    try {
      expect(at(111)).toBe(false)
      expect(mesh.geometry).toBe(original)
      expect(at(113)).toBe(true)
      const middle = mesh.geometry
      expect(at(110)).toBe(false)
      expect(at(89)).toBe(false)
      expect(mesh.geometry).toBe(middle)
      expect(at(87)).toBe(true)
      expect(mesh.geometry).toBe(original)
      expect(at(260)).toBe(true)
      const distant = mesh.geometry
      expect(at(210)).toBe(false)
      expect(mesh.geometry).toBe(distant)
      expect(at(201)).toBe(true)
      expect(mesh.geometry).toBe(middle)
      expect(mesh.instanceMatrix.version).toBe(matrixVersion)
      expect(mesh.instanceColor.version).toBe(colourVersion)
    } finally {
      resources.forEach(resource => resource.dispose()); trunk.dispose(); leaf.dispose()
    }
  })

  it('shares every detail geometry across sectors and releases inactive levels only once', () => {
    const landscape = createParkLandscape(new THREE.Scene(), { root: new THREE.Group() })
    const resources = new Set()
    const collect = () => landscape.group.traverse(object => {
      if (object.isInstancedMesh && ['contextLeaf', 'contextBark'].includes(object.material.name)) {
        resources.add(object); resources.add(object.geometry)
      }
    })
    const camera = new THREE.PerspectiveCamera(42, 1, .2, 1800)
    try {
      collect()
      camera.position.set(0, 100, 1500)
      landscape.update(camera); collect()
      expect(new Set(canopies(landscape).map(mesh => mesh.geometry)).size).toBe(1)
      camera.zoom = 8; camera.updateProjectionMatrix()
      landscape.update(camera); collect()
      expect([...resources].filter(resource => resource.isBufferGeometry)).toHaveLength(6)
      const released = new Map([...resources].map(resource => [resource, vi.fn()]))
      released.forEach((listener, resource) => resource.addEventListener('dispose', listener))
      landscape.dispose(); landscape.dispose()
      released.forEach(listener => expect(listener).toHaveBeenCalledOnce())
      expect(landscape.update(camera)).toBe(false)
    } finally { landscape.dispose() }
  })
})
