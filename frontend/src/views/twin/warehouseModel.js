import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { MODEL, floorBase, floorHeight } from './twinData.js'
import { bindWarehouse } from './warehouseController.js'
import { createWarehouseSite } from './siteGeometry.js'
import { addReferenceFacade, addReferenceRoof, createFacadeMaterials } from './warehouseFacade.js'

// Every visible surface is geometry: the model is orbitable, separable and selectable.
export function createWarehouse() {
  const root = new THREE.Group()
  const building = new THREE.Group()
  const site = new THREE.Group()
  root.name = 'dipark-warehouse'
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
  for (let index = 1; index <= MODEL.floors; index++) {
    const group = new THREE.Group()
    group.name = 'warehouse-floor-' + index
    group.userData.floor = index
    group.userData.twinRole = 'floor'
    group.userData.spaceKey = 'warehouse-01/floor-' + index
    group.position.y = floorBase(index)
    building.add(group)
    const shell = new THREE.Group(), structure = new THREE.Group(), interior = new THREE.Group(), fire = new THREE.Group()
    for (const [role, part] of Object.entries({ shell, structure, interior, fire })) {
      part.name = 'floor-' + index + '-' + role
      part.userData.twinRole = role
    }
    group.add(shell, structure, interior, fire)
    const height = floorHeight(index)
    const W = MODEL.width, D = MODEL.depth
    box(structure, 'concrete', 0, .08, 0, W, .28, D)
    box(structure, 'white', 0, -.03, D / 2, W + .8, .45, .5)
    box(structure, 'white', 0, -.03, -D / 2, W + .8, .45, .5)
    box(structure, 'white', W / 2, -.03, 0, .5, .45, D)
    box(structure, 'white', -W / 2, -.03, 0, .5, .45, D)
    // Photo's repeating column grid and concrete beams.
    for (let x = -44; x <= 44; x += 11) {
      for (let z = -22; z <= 22; z += 11) {
        box(structure, 'white', x, height / 2, z, .75, height, .75)
        box(structure, 'concrete', x, height - .65, z, .82, .6, 10.9)
      }
      box(structure, 'concrete', x, height - .35, 0, .6, .5, D - 1)
    }
    addReferenceFacade(shell, { index, height, box, cylinder, tube, materials })
    // Interior systems are deliberately schematic and separate from façade geometry.
    for (const z of [-16, 16]) {
      box(interior, 'steel', 0, height - 1.25, z, 89, .65, 1.5)
      for (let x = -44; x <= 44; x += 3) box(interior, 'dark', x, height - 1.26, z, .06, .69, 1.55)
    }
    for (const x of [-33, 0, 33]) {
      tube(fire, 'red', [x, height - 1, -24], [x, height - 1, 24], .09)
      for (let z = -20; z <= 20; z += 8) {
        tube(fire, 'red', [x, height - 1, z], [x, height - 1.4, z], .06)
        tube(fire, 'red', [x, height - 1.4, z], [x + 10, height - 1.4, z], .06)
        cylinder(fire, 'red', x + 10, height - 1.6, z, .06, .45)
        cylinder(fire, 'steel', x + 10, height - 1.86, z, .12, .035)
        cylinder(fire, 'red', x, height - 1, z, .13, .24).rotation.x = Math.PI / 2
      }
      cylinder(fire, 'red', x + .8, height / 2, 22, .1, height)
      for (const y of [.45, 3.3]) cylinder(fire, 'red', x + .8, y, 22, .15, .13)
      box(fire, 'red', x + 1.2, 1.4, 22, .9, 1.3, .4)
      box(fire, 'white', x + 1.2, 1.4, 22.22, .6, .8, .025)
      box(fire, 'steel', x + 1.48, 1.4, 22.25, .045, .2, .035)
    }
    for (let zone = 0; zone < 3; zone++) {
      const x = -31 + zone * 31
      box(interior, ['zoneA', 'zoneB', 'zoneC'][zone], x, .26, 0, 28, .045, 39)
      // Warehouse racking leaves a generous circulation aisle.
      for (const z of [-9, 7]) for (let r = -8; r <= 8; r += 8) {
        box(interior, 'rack', x + r, .6, z, 5.8, .13, 3.5)
        box(interior, 'storage', x + r, 1.25, z, 5.4, 1.2, 3.1)
        box(interior, 'rack', x + r, 2, z, 5.8, .1, 3.5)
        box(interior, 'storage', x + r, 2.7, z, 5.4, 1.3, 3.1)
        for (const dx of [-2.9, 2.9]) box(interior, 'rack', x + r + dx, 1.7, z, .12, 3.3, 3.6)
      }
    }
    for (const part of [shell, structure, interior, fire]) batch(part)
    group.traverse(object => { object.userData.floor = index })
    interior.visible = false; fire.visible = false
    floors.push({ group, shell, structure, interior, fire })
  }
  const roof = new THREE.Group()
  roof.name = 'warehouse-roof'; roof.userData.twinRole = 'roof'
  roof.position.y = floorBase(MODEL.floors) + floorHeight(MODEL.floors)
  building.add(roof)
  addReferenceRoof(roof, { box })
  batch(roof)

  return bindWarehouse(root, sharedGeometries, Object.values(materials))
}
