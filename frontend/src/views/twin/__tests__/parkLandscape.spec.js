// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'
import * as THREE from 'three'
import { createParkLandscape } from '../parkLandscape.js'

describe('park landscape resource ownership', () => {
  it('restores borrowed materials and releases its own GPU resources when leaving the scene', () => {
    const scene = new THREE.Scene(), root = new THREE.Group()
    const geometry = new THREE.BoxGeometry(), material = new THREE.MeshStandardMaterial({ name: 'glass', color: '#354e60', roughness: .22 })
    root.add(new THREE.Mesh(geometry, material)); scene.add(root)
    const originalColor = material.color.clone(), originalShader = material.onBeforeCompile
    const originalDisposed = vi.fn()
    geometry.addEventListener('dispose', originalDisposed); material.addEventListener('dispose', originalDisposed)
    const landscape = createParkLandscape(scene, { root })
    const owned = new Set()
    landscape.group.traverse(object => {
      if (object.isInstancedMesh) owned.add(object)
      if (object.geometry) owned.add(object.geometry)
      for (const mat of Array.isArray(object.material) ? object.material : object.material ? [object.material] : []) {
        owned.add(mat)
        for (const value of Object.values(mat)) if (value?.isTexture) owned.add(value)
      }
    })
    const released = new Map([...owned].map(resource => [resource, vi.fn()]))
    released.forEach((fn, resource) => resource.addEventListener('dispose', fn))
    landscape.setWeather('night'); landscape.setMode('interior')
    landscape.dispose(); landscape.dispose()
    expect(scene.children).toEqual([root])
    expect(originalDisposed).not.toHaveBeenCalled()
    expect(material.color.equals(originalColor)).toBe(true)
    expect(material.roughness).toBe(.22)
    expect(material.onBeforeCompile).toBe(originalShader)
    expect(owned.size).toBeGreaterThan(0)
    released.forEach(fn => expect(fn).toHaveBeenCalledOnce())
  })
})
