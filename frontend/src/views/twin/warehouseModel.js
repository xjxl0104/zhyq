import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { MODEL, floorBase, floorHeight } from './twinData.js'
import { bindWarehouse } from './warehouseController.js'
import { createWarehouseSite } from './siteGeometry.js'

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
  const boxGeometry = new THREE.BoxGeometry(1, 1, 1)
  const cylinderGeometry = new THREE.CylinderGeometry(1, 1, 1, 10)
  const crownGeometry = new THREE.IcosahedronGeometry(1, 1)
  const ringGeometry = new THREE.TorusGeometry(1, .055, 4, 20).rotateX(Math.PI / 2)
  const sharedGeometries = [boxGeometry, cylinderGeometry, crownGeometry, ringGeometry]
  const box = (group, type, x, y, z, w, h, d) => {
    const mesh = new THREE.Mesh(boxGeometry, materials[type])
    mesh.position.set(x, y, z); mesh.scale.set(w, h, d)
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
        box(interior, 'white', x, height / 2, z, .75, height, .75)
        box(interior, 'concrete', x, height - .65, z, .82, .6, 10.9)
      }
      box(interior, 'concrete', x, height - .35, 0, .6, .5, D - 1)
    }
    if (index === 1) {
      for (const z of [-27, 27]) {
        const outward = Math.sign(z)
        for (let column = 0; column <= 8; column++) {
          const x = -47 + column * 11.75
          box(shell, 'dark', x, 3, z, 1.4, 6, 1.25)
          box(shell, 'concrete', x, .42, z + outward * .68, 1.44, .64, .14)
          // Paired rainwater services seen beside the deep ground-floor piers.
          for (const dx of [-.38, -.13]) cylinder(shell, 'edge', x + dx, 3.2, z + outward * .72, .06, 5.8)
          for (const y of [1.1, 3.1, 5.1]) box(shell, 'steel', x - .25, y, z + outward * .76, .55, .055, .045)
        }
        for (let bay = 0; bay < 8; bay++) {
          const x = -41.125 + bay * 11.75
          const doorZ = z - outward * .8
          box(shell, 'dark', x, 5.48, z - outward * .2, 10.35, 1.8, .75)
          for (const dx of [-4.55, 4.55]) box(shell, 'dark', x + dx, 2.55, z - outward * .2, 1.25, 5.1, .8)
          box(shell, 'dark', x, 2.65, doorZ - outward * .16, 7, 4.35, .18)
          box(shell, 'steel', x, 2.65, doorZ, 6.5, 4.05, .12)
          for (let y = .74; y < 4.7; y += .24) box(shell, 'edge', x, y, doorZ + outward * .075, 6.42, .038, .04)
          for (const dx of [-3.35, 3.35]) box(shell, 'steel', x + dx, 2.7, doorZ + outward * .04, .14, 4.35, .23)
          box(shell, 'white', x, 4.81, doorZ, 6.85, .17, .32)
          for (let pane = -2.6; pane <= 2.6; pane += 1.04) box(shell, 'dark', x + pane, 2.7, doorZ + outward * .09, .72, .20, .035)
          box(shell, 'steel', x + 2.5, 1.18, doorZ + outward * .10, .5, .10, .08)
          // Clerestory lights, canopy edge, projecting brackets and dock shelter.
          box(shell, 'glass', x, 5.42, z + outward * .2, 6.8, .67, .12)
          for (let mullion = -3.3; mullion <= 3.3; mullion += 1.1) box(shell, 'steel', x + mullion, 5.42, z + outward * .28, .055, .69, .07)
          box(shell, 'white', x, 6.05, z + outward * .92, 11.7, .24, 2.9)
          box(shell, 'edge', x, 5.9, z + outward * 2.35, 11.7, .12, .07)
          for (const dx of [-3.8, 3.8]) {
            tube(shell, 'steel', [x + dx, 5.2, z + outward * .2], [x + dx, 5.9, z + outward * 1.8], .055)
          }
          box(shell, 'concrete', x, .44, z + outward * .45, 7.25, .8, 2.9)
          box(shell, 'dark', x, .865, z + outward * .55, 4.0, .08, 2.8)
          box(shell, 'steel', x, .925, z + outward * 1.88, 3.95, .045, .23)
          for (const dx of [-3.18, 3.18]) {
            box(shell, 'dark', x + dx, .67, z + outward * 1.95, .34, .7, .25)
            box(shell, 'steel', x + dx, .67, z + outward * 2.095, .20, .5, .04)
            cylinder(shell, 'yellow', x + dx * 1.16, .8, z + outward * 2.02, .09, 1.4)
            cylinder(shell, 'dark', x + dx * 1.16, .92, z + outward * 2.02, .093, .23)
          }
          // Pedestrian doors and real stair treads beside selected dock bays.
          if (bay % 2 === 0) {
            const stairX = x + 4.45
            box(shell, 'steel', stairX, 1.9, z + outward * .24, 1.0, 2.3, .10)
            box(shell, 'glass', stairX, 2.25, z + outward * .30, .68, .73, .04)
            box(shell, 'dark', stairX - .32, 1.75, z + outward * .32, .05, .25, .04)
            box(shell, 'concrete', stairX, .45, z + outward * 1.0, 1.75, .85, 1.8)
            for (let step = 0; step < 5; step++) {
              const stairZ = z + outward * (2.08 + step * .3)
              box(shell, 'concrete', stairX, .43 - step * .07, stairZ, 1.75, .86 - step * .14, .32)
              box(shell, 'edge', stairX, .87 - step * .14, stairZ + outward * .14, 1.78, .035, .045)
            }
            for (const dx of [-.86, .86]) {
              tube(shell, 'steel', [stairX + dx, 1.78, z + outward * .65], [stairX + dx, 1.78, z + outward * 1.82], .038)
              tube(shell, 'steel', [stairX + dx, 1.78, z + outward * 1.82], [stairX + dx, 1.08, z + outward * 3.36], .038)
              for (const [run, top] of [[.65, 1.78], [1.82, 1.78], [3.36, 1.08]]) cylinder(shell, 'steel', stairX + dx, top - .48, z + outward * run, .032, .96)
            }
          }
          // Compact traffic lamps are geometry, with no invented dock signage.
          box(shell, 'dark', x - 3.8, 3.05, z + outward * .47, .25, .6, .15)
          cylinder(shell, 'red', x - 3.8, 3.2, z + outward * .57, .072, .035).rotation.x = Math.PI / 2
          cylinder(shell, 'treeLight', x - 3.8, 2.93, z + outward * .57, .072, .035).rotation.x = Math.PI / 2
        }
      }
      for (const x of [-48, 48]) {
        box(shell, 'dark', x, 3, 0, .8, 6, D)
        for (let z = -17; z <= 17; z += 17) {
          const outward = Math.sign(x)
          box(shell, 'steel', x + outward * .5, 2.5, z, .1, 4.3, 9)
          for (let y = .45; y < 4.7; y += .24) box(shell, 'edge', x + outward * .57, y, z, .045, .04, 8.9)
          for (const dz of [-4.6, 4.6]) box(shell, 'concrete', x + outward * .6, 2.5, z + dz, .25, 4.45, .16)
          box(shell, 'white', x + outward * .75, 5.1, z, 1.8, .22, 10)
          box(shell, 'glass', x + outward * .45, 5.6, z, .08, .5, 7)
        }
      }
      // The published 12 m first storey includes facade above the human-scale dock.
      // Keep shutters, stairs and barriers at their working dimensions.
      const upperDockHeight = height - 6.4
      if (upperDockHeight > 0) {
        for (const z of [-27, 27]) {
          const outward = Math.sign(z)
          box(shell, 'white', 0, 7.4, z, W, 2, .6)
          box(shell, 'glass', 0, 9.0, z, W, 1.15, .3)
          box(shell, 'white', 0, 10.45, z, W, 1.8, .6)
          box(shell, 'glass', 0, height - .5, z, W, 1.0, .3)
          for (const y of [8.42, 9.6, height - 1]) box(shell, 'edge', 0, y, z + outward * .32, W, .07, .15)
          for (let x = -47; x < 48; x += 1.4) {
            box(shell, 'dark', x, 9.0, z + outward * .21, .07, 1.15, .12)
            box(shell, 'dark', x, height - .5, z + outward * .21, .07, 1.0, .12)
          }
          for (let x = -47; x <= 47; x += 5.875) {
            box(shell, 'edge', x, 7.4, z + outward * .31, .028, 1.9, .025)
            box(shell, 'edge', x, 10.45, z + outward * .31, .028, 1.7, .025)
          }
        }
        for (const x of [-48, 48]) {
          const outward = Math.sign(x)
          box(shell, 'white', x, 7.4, 0, .6, 2, D)
          box(shell, 'glass', x, 9.0, 0, .3, 1.15, D)
          box(shell, 'white', x, 10.45, 0, .6, 1.8, D)
          box(shell, 'glass', x, height - .5, 0, .3, 1.0, D)
          for (const y of [8.42, 9.6, height - 1]) box(shell, 'edge', x + outward * .32, y, 0, .15, .07, D)
          for (let z = -26; z < 27; z += 1.4) {
            box(shell, 'dark', x + outward * .21, 9.0, z, .12, 1.15, .07)
            box(shell, 'dark', x + outward * .21, height - .5, z, .12, 1.0, .07)
          }
        }
      }
    } else {
      const extendedHeight = height - 5.6
      const middleHeight = 1.8 + extendedHeight
      const middleY = 4.05 + extendedHeight / 2
      const upperY = 5.1 + extendedHeight
      for (const z of [-D / 2, D / 2]) {
        const outward = Math.sign(z)
        box(shell, 'white', 0, 1.0, z, W, 2.0, .55)
        box(shell, 'glass', 0, 2.6, z, W, 1.15, .3)
        // Leave a readable upper ribbon below the next slab (the old 9.5 cm slit aliased at overview scale).
        box(shell, 'white', 0, middleY, z, W, middleHeight, .6)
        box(shell, 'glass', 0, upperY, z, W, 1.0, .25)
        for (let x = -47, pane = 0; x < 48; x += 1.4, pane++) {
          box(shell, 'dark', x, 2.6, z + outward * .21, .07, 1.2, .12)
          box(shell, 'dark', x, upperY, z + outward * .19, .07, 1.0, .1)
          if ((pane + index * 2) % 7 === 0) {
            box(shell, 'glassLight', x + .68, 2.6, z + outward * .155, 1.26, 1.03, .035)
          }
          if ((pane + index) % 13 === 0) {
            // A few top-hung vent lights break up the identical glazing rhythm.
            const vent = box(shell, 'glassLight', x + .7, 2.77, z + outward * .29, 1.22, .62, .065)
            vent.rotation.x = -outward * .12
            box(shell, 'dark', x + .7, 2.44, z + outward * .32, 1.24, .065, .08)
          }
        }
        // Projecting sill/head profiles read at overview, while narrow joints resolve on zoom.
        for (const y of [2.02, 3.16, upperY - .51]) box(shell, 'edge', 0, y, z + outward * .31, W, .07, .15)
        box(shell, 'edge', 0, middleY, z + outward * .305, W, .027, .024)
        for (let x = -47; x <= 47; x += 5.875) {
          box(shell, 'edge', x, 1.03, z + outward * .285, .028, 1.84, .028)
          box(shell, 'edge', x, middleY, z + outward * .313, .028, middleHeight - .12, .028)
        }
        if (index === 2) {
          box(shell, 'glass', 0, 5.65, z + outward * .33, W, 1.15, .055)
          for (let x = -47; x < 48; x += 1.4) box(shell, 'dark', x, 5.65, z + outward * .38, .07, 1.16, .07)
          for (const y of [5.06, 6.24]) box(shell, 'edge', 0, y, z + outward * .39, W, .07, .11)
          for (const x of [-31, -6, 15]) box(shell, 'white', x, 5.65, z + outward * .4, 8, 1.16, .12)
        }
        for (let part = 0; part < 6; part++) {
          const x = -40 + part * 14 + ((index + part) % 3) * 2
          box(shell, 'white', x, 2.6, z + Math.sign(z) * .19, 3 + ((index + part) % 4) * 2, 1.2, .2)
        }
        for (const x of [-23, 4, 25]) {
          const railZ = z + outward * 2.5
          box(shell, 'white', x, 1.2, z + outward * 1.3, 3.8, .3, 2.6)
          box(shell, 'edge', x, 1.37, z + outward * 1.3, 3.7, .055, 2.5)
          for (const y of [1.51, 2.4]) {
            tube(shell, 'steel', [x - 1.84, y, railZ], [x + 1.84, y, railZ], .035)
            for (const dx of [-1.84, 1.84]) tube(shell, 'steel', [x + dx, y, z + outward * .38], [x + dx, y, railZ], .035)
          }
          for (let rail = -1.8; rail <= 1.8; rail += .3) cylinder(shell, 'steel', x + rail, 1.93, railZ, .023, .96)
          for (const dx of [-1.84, 1.84]) {
            for (let rail = .4; rail < 2.5; rail += .3) cylinder(shell, 'steel', x + dx, 1.93, z + outward * rail, .023, .96)
            box(shell, 'white', x + dx * .66, .81, z + outward * .91, .18, .5, 1.4)
          }
        }
        for (const x of [-12, 18]) {
          cylinder(shell, 'edge', x, height / 2, z + outward * .48, .065, height)
          for (const y of [.4, 3.4]) box(shell, 'steel', x, y, z + outward * .52, .28, .05, .045)
        }
        if (index === MODEL.floors) {
          for (const x of [-36, -9, 17]) {
            box(shell, 'dark', x, 4, z + outward * .34, 1.4, 1.35, .09)
            for (let y = 3.42; y < 4.65; y += .17) box(shell, 'steel', x, y, z + outward * .40, 1.32, .04, .05)
          }
        }
      }
      for (const x of [-W / 2, W / 2]) {
        const outward = Math.sign(x)
        box(shell, 'white', x, 1, 0, .55, 2, D)
        box(shell, 'glass', x, 2.6, 0, .3, 1.15, D)
        box(shell, 'white', x, middleY, 0, .6, middleHeight, D)
        box(shell, 'glass', x, upperY, 0, .25, 1.0, D)
        for (let z = -26, pane = 0; z < 27; z += 1.4, pane++) {
          box(shell, 'dark', x + outward * .21, 2.6, z, .12, 1.15, .07)
          box(shell, 'dark', x + outward * .19, upperY, z, .1, 1.0, .07)
          if ((pane + index) % 7 === 0) box(shell, 'glassLight', x + outward * .155, 2.6, z + .68, .035, 1.03, 1.26)
        }
        for (const y of [2.02, 3.16, upperY - .51]) box(shell, 'edge', x + outward * .31, y, 0, .15, .07, D)
        box(shell, 'edge', x + outward * .305, middleY, 0, .024, .027, D)
        for (let z = -27; z <= 27; z += 6.75) {
          box(shell, 'edge', x + outward * .285, 1.03, z, .028, 1.84, .028)
          box(shell, 'edge', x + outward * .313, middleY, z, .028, middleHeight - .12, .028)
        }
        if (index === 2) {
          box(shell, 'glass', x + outward * .33, 5.65, 0, .055, 1.15, D)
          for (let z = -26; z < 27; z += 1.4) box(shell, 'dark', x + outward * .38, 5.65, z, .07, 1.16, .07)
          for (const y of [5.06, 6.24]) box(shell, 'edge', x + outward * .39, y, 0, .11, .07, D)
          box(shell, 'white', x + outward * .4, 5.65, 0, .12, 1.16, 13)
        }
        box(shell, 'white', x + Math.sign(x) * .2, 2.6, -12 + (index % 3) * 8, .2, 1.2, 14)
      }
      // Signature turquoise corner: glass curtain wall and wrapping horizontal bands.
      if (index < MODEL.floors) {
        const paneRows = Math.ceil(height / 1.85)
        const paneHeight = height / paneRows
        box(shell, 'glass', 38, height / 2, 27.45, 20.8, height - .1, .18)
        box(shell, 'glass', 48.45, height / 2, 20.5, .18, height - .1, 13.9)
        for (let x = 28, pane = 0; x < 48; x += 1.35, pane++) {
          box(shell, 'dark', x, height / 2, 27.65, .095, height, .14)
          for (let row = 0; row < paneRows; row++) {
            const type = (pane + row + index) % 5 === 0 ? 'glassPale' : 'glassLight'
            box(shell, type, x + .65, (row + .5) * paneHeight, 27.56, 1.22, paneHeight - .1, .035)
          }
        }
        for (let z = 14, pane = 0; z < 27; z += 1.3, pane++) {
          box(shell, 'dark', 48.65, height / 2, z, .14, height, .095)
          for (let row = 0; row < paneRows; row++) {
            const type = (pane + row + index) % 4 === 0 ? 'glassPale' : 'glassLight'
            box(shell, type, 48.56, (row + .5) * paneHeight, z + .62, .035, paneHeight - .1, 1.17)
          }
        }
        for (let row = 1; row <= paneRows; row++) {
          const y = row * paneHeight - .035
          box(shell, 'dark', 38, y, 27.65, 21, .085, .14)
          box(shell, 'dark', 48.65, y, 20.5, .14, .085, 14)
        }
        box(shell, 'steel', 48.63, height / 2, 27.63, .2, height, .2)
        box(shell, 'teal', 37, .8, 27.85, 23.5, 1.05, .8)
        box(shell, 'teal', 48.85, .8, 20.1, .8, 1.05, 16.3)
        box(shell, 'teal', 37, 1.35, 27.95, 23.55, .09, 1.02)
        box(shell, 'teal', 48.95, 1.35, 20.1, 1.02, .09, 16.35)
        for (let x = 27.7; x < 48; x += 4.1) box(shell, 'dark', x, .8, 28.258, .025, 1, .015)
      } else {
        box(shell, 'white', 39, height / 2, 27.4, 19, height - .1, .5)
        box(shell, 'white', 48.4, height / 2, 9, .5, height - .1, 36)
        for (let x = 32.1; x < 48; x += 5.3) box(shell, 'edge', x, height / 2, 27.657, .026, height - .2, .018)
        for (let z = -7; z < 27; z += 5.3) box(shell, 'edge', 48.657, height / 2, z, .018, height - .2, .026)
      }
    }
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
  box(roof, 'white', 0, 0, 0, 97, .5, 55)
  box(roof, 'concrete', 0, .27, 0, 95.4, .08, 53.4)
  for (const z of [-27, 27]) box(roof, 'white', 0, 1, z, 97, 2, .55)
  for (const x of [-48, 48]) box(roof, 'white', x, 1, 0, .55, 2, 55)
  for (const z of [-27, 27]) box(roof, 'edge', 0, 2.04, z, 97.2, .11, .74)
  for (const x of [-48, 48]) box(roof, 'edge', x, 2.04, 0, .74, .11, 55.2)
  for (let x = -42; x <= 42; x += 7) box(roof, 'edge', x, .322, 0, .035, .018, 52.8)
  for (let z = -21; z <= 21; z += 7) box(roof, 'edge', 0, .322, z, 94.6, .018, .035)
  for (const x of [-32, -4, 27]) {
    box(roof, 'dark', x, .43, -9, 16.7, .27, 12.7)
    box(roof, 'white', x, 1.8, -9, 16, 3.5, 12)
    box(roof, 'edge', x, 3.65, -9, 16.5, .22, 12.5)
    box(roof, 'white', x, 3.82, -9, 15.6, .13, 11.6)
    box(roof, 'dark', x - 1.9, 2, -2.95, 8, 1.75, .14)
    for (let y = 1.2; y < 2.9; y += .19) box(roof, 'steel', x - 1.9, y, -2.84, 7.85, .065, .15)
    box(roof, 'steel', x + 5.85, 1.7, -2.94, 1.6, 2.5, .12)
    box(roof, 'dark', x + 6.32, 1.65, -2.84, .065, .25, .08)
    for (const dx of [-7.95, 7.95]) box(roof, 'edge', x + dx, 1.84, -9, .12, 3.38, 12)
    // Maintenance access ladder continues onto the equipment-house roof.
    for (const dx of [-.43, .43]) cylinder(roof, 'steel', x + 7.05 + dx, 2.13, -2.62, .038, 4.1)
    for (let y = .52; y < 3.9; y += .3) tube(roof, 'steel', [x + 6.62, y, -2.62], [x + 7.48, y, -2.62], .03)
    for (const z of [-13.4, -5.7]) {
      cylinder(roof, 'steel', x - 5.8, 4.01, z, .43, .42)
      cylinder(roof, 'dark', x - 5.8, 4.26, z, .58, .12)
    }
    for (const dx of [-3.5, 3.5]) {
      box(roof, 'concrete', x + dx, .49, 2.3, 1.8, .33, 7.5)
      box(roof, 'steel', x + dx, 1.04, 2.3, 1.35, .8, 8.0)
      for (let z = -1; z < 6.4; z += 1.3) box(roof, 'edge', x + dx, 1.045, z, 1.43, .9, .12)
      box(roof, 'steel', x + dx, 1.1, 6.2, 3.0, .9, 1.35)
    }
  }
  for (let x = -36; x <= 36; x += 12) {
    for (const dx of [-1.75, 1.75]) box(roof, 'concrete', x + dx, .5, 11, .65, .4, 5.7)
    box(roof, 'steel', x, 1.25, 11, 5.2, 1.15, 5.1)
    box(roof, 'edge', x, 1.84, 11, 5.35, .1, 5.25)
    for (const z of [8.43, 13.57]) {
      box(roof, 'dark', x, 1.22, z, 4.75, .88, .045)
      for (let y = .87; y < 1.62; y += .13) box(roof, 'steel', x, y, z + Math.sign(z - 11) * .025, 4.8, .037, .075)
    }
    for (const dx of [-1.32, 1.32]) {
      cylinder(roof, 'steel', x + dx, 2.0, 11, 1.06, .35)
      cylinder(roof, 'dark', x + dx, 2.20, 11, .94, .055)
      cylinder(roof, 'steel', x + dx, 2.24, 11, .23, .10)
      for (const radius of [.6, .95]) {
        const ring = new THREE.Mesh(ringGeometry, materials.steel)
        ring.position.set(x + dx, 2.27, 11); ring.scale.setScalar(radius); roof.add(ring)
      }
      for (let blade = 0; blade < 4; blade++) {
        const angle = blade * Math.PI / 2
        const vane = box(roof, 'edge', x + dx + Math.cos(angle) * .4, 2.25, 11 + Math.sin(angle) * .4, .76, .045, .28)
        vane.rotation.y = -angle + .3
      }
    }
  }
  // A raised service walk and real rail posts connect the grouped equipment.
  box(roof, 'edge', 0, .42, 17.7, 84, .18, 2.4)
  for (let x = -41; x <= 41; x += 1.5) box(roof, 'steel', x, .52, 17.7, .055, .035, 2.3)
  for (const y of [1.02, 1.62]) tube(roof, 'steel', [-42, y, 18.85], [42, y, 18.85], .042)
  for (let x = -42; x <= 42; x += 3) {
    cylinder(roof, 'steel', x, 1.07, 18.85, .038, 1.1)
    box(roof, 'concrete', x, .43, 18.85, .3, .22, .3)
  }
  for (const x of [-43, 43]) {
    for (const z of [-20, 21]) {
      cylinder(roof, 'steel', x, 1.05, z, .31, 1.45)
      cylinder(roof, 'dark', x, 1.85, z, .47, .14)
      cylinder(roof, 'edge', x, .45, z, .55, .18)
    }
  }
  batch(roof)

  return bindWarehouse(root, sharedGeometries, Object.values(materials))
}
