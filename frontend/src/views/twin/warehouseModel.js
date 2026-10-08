import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { MODEL, floorBase, floorHeight } from './twinData.js'
import { PLAN_BUILDING, PLAN_FLOORS } from './floorPlanData.js'
import { createPlanFloor } from './floorPlanGeometry.js'
import { bindWarehouse } from './warehouseController.js'
import { createWarehouseSite } from './siteGeometry.js'
import { addReferenceFacade, addReferenceRoof, createFacadeMaterials } from './warehouseFacade.js'

// Every visible surface is geometry: the model is orbitable, separable and selectable.
export function createWarehouse() {
  const root = new THREE.Group()
  const building = new THREE.Group()
  const site = new THREE.Group()
  root.name = 'dipark-warehouse'
  building.scale.setScalar(PLAN_BUILDING.sceneScale)
  building.userData.planRevision = PLAN_BUILDING.revision
  building.name = 'warehouse-building'; building.userData.twinRole = 'building'
  site.name = 'warehouse-site'; site.userData.twinRole = 'site'
  root.add(site, building)
  const materials = {}
  const material = (name, color, options = {}) => (materials[name] = new THREE.MeshStandardMaterial({ name, color, roughness: .75, ...options }))
  material('white', '#edf0f0')
  material('edge', '#b8c5c9')
  material('concrete', '#a5afb2')
  material('dark', '#39484f')
  material('glass', '#354e60', { metalness: .4, roughness: .22 })
  material('glassLight', '#63899e', { metalness: .38, roughness: .2 })
  material('glassPale', '#91acb7', { metalness: .45, roughness: .19 })
  material('teal', '#038fa7', { metalness: .2, roughness: .38 })
  material('road', '#60727b')
  material('paving', '#dfe6e1')
  material('grass', '#9cb58b')
  material('tree', '#597c60')
  material('treeLight', '#779771')
  material('flowers', '#bc7390')
  material('bark', '#7b7568')
  material('line', '#eaf0e9')
  material('yellow', '#dfba60')
  material('red', '#b95347')
  material('steel', '#bdccc8', { metalness: .28 })
  material('siteLamp', '#fff0d2', { emissive: '#ffcf83', emissiveIntensity: .45, roughness: .3 })
  material('storage', '#d5b992')
  material('rack', '#6e9195')
  material('zoneA', '#cadfce')
  material('zoneB', '#c7dce1')
  material('zoneC', '#ded7e7')
  createFacadeMaterials(material)
  const boxGeometry = new THREE.BoxGeometry(1, 1, 1)
  const glazingGeometry = new THREE.PlaneGeometry(1, 1)
  const cylinderGeometry = new THREE.CylinderGeometry(1, 1, 1, 10)
  const crownGeometry = new THREE.IcosahedronGeometry(1, 1)
  const ringGeometry = new THREE.TorusGeometry(1, .055, 4, 20).rotateX(Math.PI / 2)
  const sharedGeometries = [boxGeometry, glazingGeometry, cylinderGeometry, crownGeometry, ringGeometry]
  const box = (group, type, x, y, z, w, h, d) => {
    const glass = materials[type].userData.surfaceRole === 'architectural-glass'
    const mesh = new THREE.Mesh(glass ? glazingGeometry : boxGeometry, materials[type])
    mesh.position.set(x, y, z)
    if (glass) {
      // A single outward-facing pane avoids two transparent box faces tinting
      // each other, and keeps the merged glazing stable while orbiting.
      if (w > d) {
        mesh.position.z += Math.sign(z) * d / 2
        mesh.rotation.y = z < 0 ? Math.PI : 0
        mesh.scale.set(w, h, 1)
      } else {
        mesh.position.x += Math.sign(x) * w / 2
        mesh.rotation.y = Math.sign(x) * Math.PI / 2
        mesh.scale.set(d, h, 1)
      }
    } else mesh.scale.set(w, h, d)
    mesh.castShadow = true; mesh.receiveShadow = true
    group.add(mesh)
    return mesh
  }
  const cylinder = (group, type, x, y, z, radius, height) => {
    const mesh = new THREE.Mesh(cylinderGeometry, materials[type])
    mesh.position.set(x, y, z); mesh.scale.set(radius, height, radius)
    mesh.castShadow = true; mesh.receiveShadow = true
    group.add(mesh)
    return mesh
  }
  const tube = (group, type, from, to, radius = .045) => {
    const start = new THREE.Vector3(...from), end = new THREE.Vector3(...to)
    const delta = end.clone().sub(start)
    const center = start.clone().add(end).multiplyScalar(.5)
    const mesh = cylinder(group, type, ...center.toArray(), radius, delta.length())
    mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), delta.normalize())
    return mesh
  }
  const paint = (group, type, x1, z1, x2, z2, width = .11, y = .20) => {
    const mesh = box(group, type, (x1 + x2) / 2, y, (z1 + z2) / 2,
      Math.hypot(x2 - x1, z2 - z1), .022, width)
    mesh.rotation.y = -Math.atan2(z2 - z1, x2 - x1)
  }
  const hatch = (group, x, z, width, depth) => {
    const left = x - width / 2, back = z - depth / 2
    for (let offset = -width; offset < depth; offset += 1.3) {
      const start = Math.max(0, -offset), end = Math.min(width, depth - offset)
      if (end > start) paint(group, 'yellow', left + start, back + start + offset, left + end, back + end + offset)
    }
    for (const dx of [-width / 2, width / 2]) paint(group, 'yellow', x + dx, back, x + dx, back + depth)
    for (const dz of [-depth / 2, depth / 2]) paint(group, 'yellow', left, z + dz, left + width, z + dz)
  }
  // Merge static architecture per material, retaining floor-level selection.
  const batch = group => {
    group.updateMatrixWorld(true)
    const chunks = new Map()
    group.children.forEach(mesh => {
      if (!mesh.isMesh) return
      const geo = mesh.geometry.clone().applyMatrix4(mesh.matrix)
      // These finishes use no image textures; omit unused UVs from the web asset.
      geo.deleteAttribute('uv')
      if (!sharedGeometries.includes(mesh.geometry)) mesh.geometry.dispose()
      if (!chunks.has(mesh.material)) chunks.set(mesh.material, [])
      chunks.get(mesh.material).push(geo)
    })
    group.clear()
    chunks.forEach((geometries, mat) => {
      const mesh = new THREE.Mesh(mergeGeometries(geometries, false), mat)
      mesh.name = group.name + '-' + mat.name
      mesh.castShadow = true; mesh.receiveShadow = true
      group.add(mesh)
      geometries.forEach(geometry => geometry.dispose())
    })
  }
  createWarehouseSite(site, { box, cylinder, tube, paint, hatch, materials, crownGeometry, batch })

  const floors = []
  for (const definition of PLAN_FLOORS) {
    const index = definition.floor
    const group = new THREE.Group()
    group.name = 'warehouse-floor-' + index
    group.userData.floor = index
    group.userData.twinRole = 'floor'
    group.userData.spaceKey = 'warehouse-01/floor-' + index
    group.position.y = floorBase(index)
    building.add(group)
    const shell = new THREE.Group()
    const { structure, interior, fire } = createPlanFloor(index)
    group.userData.planFileId = definition.fileId
    group.userData.planFloorId = definition.floorId
    group.userData.planDrawing = definition.drawing
    for (const [role, part] of Object.entries({ shell, structure, interior, fire })) {
      part.name = 'floor-' + index + '-' + role
      part.userData.twinRole = role
    }
    group.add(shell, structure, interior, fire)
    const height = floorHeight(index)
    if (index > 0) {
      addReferenceFacade(shell, { index, height, box, cylinder, tube, materials })
      batch(shell)
      // Photo-derived facade is fitted to the measured plan envelope; interiors are metres.
      shell.scale.set(PLAN_BUILDING.width / 96, 1, PLAN_BUILDING.depth / 54)
    }
    group.traverse(object => { object.userData.floor = index })
    interior.visible = false; fire.visible = false
    floors.push({ group, shell, structure, interior, fire })
  }
  const roof = new THREE.Group()
  roof.name = 'warehouse-roof'; roof.userData.twinRole = 'roof'
  roof.scale.set(PLAN_BUILDING.width / 96, 1, PLAN_BUILDING.depth / 54)
  roof.position.y = floorBase(MODEL.floors) + floorHeight(MODEL.floors)
  building.add(roof)
  addReferenceRoof(roof, { box, materials, opening: [-46.2, -18, -25.5, 18] })
  batch(roof)

  return bindWarehouse(root, sharedGeometries, Object.values(materials))
}
