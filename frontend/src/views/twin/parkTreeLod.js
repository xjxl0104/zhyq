import * as THREE from 'three'
import { createTreeGeometries } from './parkVegetation.js'

const sectorSize = 96
const distances = [100, 230]
const hysteresis = .12
const referenceLens = 1 / Math.tan(THREE.MathUtils.degToRad(21))

// Two draws per spatial sector; all sectors share the same six geometries and
// materials. Switching detail changes geometry references, never instance data.
export function createParkTreeLod(group, positions, materials, own) {
  const geometries = [0, 1, 2].map(level => {
    const pair = createTreeGeometries(level)
    own(pair.trunk); own(pair.crown)
    return pair
  })
  const sectors = new Map()
  positions.forEach(([x, z, scale], index) => {
    const key = `${Math.floor(x / sectorSize)},${Math.floor(z / sectorSize)}`
    if (!sectors.has(key)) sectors.set(key, { key, trees: [], level: 0 })
    sectors.get(key).trees.push({ x, z, scale, index })
  })
  const dummy = new THREE.Object3D(), colour = new THREE.Color()
  for (const sector of sectors.values()) {
    for (const [part, material, name] of [
      ['trunk', materials.trunk, 'park-tree-trunks'],
      ['crown', materials.leaf, 'park-canopy'],
    ]) {
      const mesh = own(new THREE.InstancedMesh(geometries[0][part], material, sector.trees.length))
      mesh.name = `${name}-${sector.key}`
      mesh.castShadow = true; mesh.receiveShadow = true
      sector.trees.forEach(({ x, z, scale, index }, instance) => {
        dummy.position.set(x, 0, z); dummy.scale.setScalar(scale); dummy.rotation.set(0, index * .73, 0); dummy.updateMatrix()
        mesh.setMatrixAt(instance, dummy.matrix)
        if (part === 'crown') mesh.setColorAt(instance, colour.setHSL(.25 + (index % 4) * .018, .22, .63 + (index % 5) * .05))
      })
      mesh.instanceMatrix.needsUpdate = true
      if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true
      // A fixed union contains every detail level, preventing edge popping
      // when a larger distant leaf card replaces several smaller close cards.
      const bounds = new THREE.Sphere().makeEmpty()
      for (const pair of geometries) {
        mesh.geometry = pair[part]
        mesh.computeBoundingSphere()
        bounds.union(mesh.boundingSphere)
      }
      mesh.geometry = geometries[0][part]
      mesh.boundingSphere.copy(bounds)
      sector[part] = mesh
      group.add(mesh)
    }
  }
  const previousPosition = new THREE.Vector3(Infinity, Infinity, Infinity)
  const cameraPosition = new THREE.Vector3()
  let previousLens = null
  return {
    update(camera) {
      camera.getWorldPosition(cameraPosition)
      const lens = camera.projectionMatrix.elements[5]
      if (cameraPosition.equals(previousPosition) && lens === previousLens) return false
      previousPosition.copy(cameraPosition); previousLens = lens
      const perspectiveScale = referenceLens / lens
      let changed = false
      for (const sector of sectors.values()) {
        // Use the closest/largest tree, keeping the entire sector sufficiently
        // detailed. Lens scaling includes both FOV changes and camera.zoom.
        let distance = Infinity
        for (const tree of sector.trees) {
          const dx = cameraPosition.x - tree.x, dy = cameraPosition.y - 7.8 * tree.scale, dz = cameraPosition.z - tree.z
          distance = Math.min(distance, Math.hypot(dx, dy, dz) / tree.scale * perspectiveScale)
        }
        let level = sector.level
        while (level < 2 && distance > distances[level] * (1 + hysteresis)) level++
        while (level > 0 && distance < distances[level - 1] * (1 - hysteresis)) level--
        if (level === sector.level) continue
        sector.level = level
        sector.trunk.geometry = geometries[level].trunk
        sector.crown.geometry = geometries[level].crown
        changed = true
      }
      return changed
    },
  }
}
