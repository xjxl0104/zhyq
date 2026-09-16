import * as THREE from 'three'
import { mergeVertices } from 'three/addons/utils/BufferGeometryUtils.js'
import { createReferenceVehicles } from './referenceVehicles.js'

// Surrounding roads and the south entrance are photo-informed schematic placements.
// Building footprint and floor dimensions are defined independently in twinData.
export function createWarehouseSite(rootSite, helpers) {
  const { box, cylinder, tube, paint, hatch, materials, crownGeometry, batch } = helpers
  const site = new THREE.Group()
  site.name = 'warehouse-site-yard'
  site.userData.sitePart = 'yard'
  rootSite.add(site)
  // At-grade yard paving extends naturally into the landscape and access roads.
  box(site, 'paving', 0, -.15, 0, 148, .22, 116)
  box(site, 'road', 0, -.04, 0, 133, .18, 96)
  box(site, 'paving', 0, .06, 0, 114, .22, 70)
  for (const z of [-44, 44]) {
    const strips = z > 0 ? [[-27.5, 64], [51, 17]] : [[0, 119]]
    for (const [center, width] of strips) {
      box(site, 'grass', center, .12, z, width, .25, 5)
      for (const dz of [-2.55, 2.55]) box(site, 'edge', center, .17, z + dz, width, .3, .24)
    }
    for (let x = -54, treeIndex = 0; x <= 54; x += 9, treeIndex++) {
      if (z > 0 && x > 3 && x < 42) continue
      const treeHeight = 3.0 + (treeIndex % 3) * .35
      cylinder(site, 'bark', x, 1.6, z, .17, 3.0)
      for (const [dx, dz] of [[-.6, -.2], [.65, .15], [0, .3]]) {
        const tree = new THREE.Mesh(crownGeometry, materials[(treeIndex + Math.round(dx)) % 2 ? 'tree' : 'treeLight'])
        tree.position.set(x + dx, treeHeight + (dx === 0 ? 1 : 0), z + dz)
        tree.scale.set(1.4, 1.65, 1.35); tree.rotation.y = treeIndex * .73
        tree.castShadow = true; site.add(tree)
      }
      box(site, 'bark', x, .265, z, 1.2, .025, 1.2)
      for (const dx of [-.42, .42]) tube(site, 'bark', [x + dx, .4, z + .35], [x, 1.6, z], .035)
    }
    for (let x = -44; x < 50; x += 23.5) {
      if (z > 0 && x > 3 && x < 42) continue
      for (let offset = -2; offset <= 2; offset++) {
        const hedge = new THREE.Mesh(crownGeometry, materials.treeLight)
        hedge.position.set(x + offset * .9, .6, z - Math.sign(z) * 1.6)
        hedge.scale.set(.65, .55, .6); site.add(hedge)
      }
    }
    // Low clipped shrubs and small flower drifts follow the completed-site photo.
    for (const x of [-43, -16, 18, 44]) {
      if (z > 0 && x > 3 && x < 42) continue
      for (let plant = 0; plant < 9; plant++) {
        const angle = plant * 2.4
        const shrub = new THREE.Mesh(crownGeometry, materials[plant % 3 === 0 ? 'flowers' : 'treeLight'])
        shrub.position.set(x + Math.cos(angle) * (plant / 6), .43 + (plant % 2) * .09, z + Math.sin(angle) * .6)
        shrub.scale.set(.78, .37, .64); shrub.rotation.y = angle
        shrub.castShadow = true; site.add(shrub)
      }
    }
  }
  // Curbs, storm drains and slab joints give the apron a human scale.
  for (const z of [-34.9, 34.9]) {
    box(site, 'edge', 0, .18, z, 113.5, .24, .25)
    for (let x = -47; x <= 47; x += 11.75) {
      paint(site, 'edge', x, z - Math.sign(z) * 7.1, x, z, .035, .189)
      box(site, 'dark', x + 1.6, .205, z - Math.sign(z) * .65, 1.2, .025, .38)
      for (let offset = -.45; offset <= .45; offset += .15) box(site, 'steel', x + 1.6 + offset, .225, z - Math.sign(z) * .65, .055, .025, .35)
    }
    for (const x of [-29.375, -5.875, 17.625, 41.125]) hatch(site, x, Math.sign(z) * 32.1, 8, 4.7)
  }
  for (const x of [-63, 63]) {
    for (let z = -34; z < 36; z += 9) {
      box(site, 'line', x, .09, z, .25, .08, 3.5)
    }
  }
  for (let x = -53; x < 55; x += 7) {
    if (x < 4 || x > 41) box(site, 'line', x, .09, 50, 3.5, .08, .22)
    box(site, 'line', x, .09, -50, 3.5, .08, .22)
  }
  // Photo reference: perpendicular car spaces along the east short elevation.
  for (let z = -24.75; z <= 24.25; z += 3.5) {
    paint(site, 'line', 50.7, z, 58.2, z, .12, .21)
  }
  paint(site, 'line', 50.7, -24.75, 50.7, 24.25, .12, .21)
  // One visitor space west of the entry throat leaves its full width open.
  for (const z of [36.7, 39.7]) paint(site, 'line', -5.7, z, -.3, z, .11, .21)
  for (const x of [-63, 63]) {
    for (const z of [-24, 25]) {
      cylinder(site, 'steel', x, 3.5, z, .10, 6.9)
      tube(site, 'steel', [x, 6.9, z], [x - Math.sign(x) * 1.7, 7.2, z], .08)
      box(site, 'siteLamp', x - Math.sign(x) * 1.8, 7.15, z, 1.05, .14, .46)
      cylinder(site, 'concrete', x, .2, z, .3, .35)
    }
    // Lane arrows are geometry too, with no texture or font dependency.
    const direction = Math.sign(x)
    paint(site, 'line', x, -4 * direction, x, 3 * direction, .2, .09)
    paint(site, 'line', x, 3 * direction, x - 1.2, .8 * direction, .22, .09)
    paint(site, 'line', x, 3 * direction, x + 1.2, .8 * direction, .22, .09)
  }
  for (const x of [-46, 47]) {
    cylinder(site, 'red', x, .65, 40.5, .2, 1.15)
    cylinder(site, 'red', x, 1.27, 40.5, .27, .16)
    tube(site, 'red', [x - .36, .92, 40.5], [x + .36, .92, 40.5], .115)
    for (const dx of [-.75, .75]) cylinder(site, 'yellow', x + dx, .52, 40.5, .07, .95)
  }
  batch(site)
  createReferenceVehicles(rootSite, materials)
  createRoadEnvironment(rootSite, helpers)
  createEntrance(rootSite, helpers)
}

function roundedBlock(group, materials, type, x, y, z, width, height, depth, radius) {
  const left = -width / 2, right = width / 2, bottom = -depth / 2, top = depth / 2
  const shape = new THREE.Shape()
  shape.moveTo(left + radius, bottom)
  shape.lineTo(right - radius, bottom)
  shape.quadraticCurveTo(right, bottom, right, bottom + radius)
  shape.lineTo(right, top - radius)
  shape.quadraticCurveTo(right, top, right - radius, top)
  shape.lineTo(left + radius, top)
  shape.quadraticCurveTo(left, top, left, top - radius)
  shape.lineTo(left, bottom + radius)
  shape.quadraticCurveTo(left, bottom, left + radius, bottom)
  const geometry = new THREE.ExtrudeGeometry(shape, { depth: height, bevelEnabled: false, curveSegments: 6, steps: 1 })
    .rotateX(-Math.PI / 2).translate(0, -height / 2, 0)
  const indexed = mergeVertices(geometry)
  geometry.dispose()
  const mesh = new THREE.Mesh(indexed, materials[type])
  mesh.position.set(x, y, z); mesh.castShadow = true; mesh.receiveShadow = true
  group.add(mesh)
  return mesh
}

function createRoadEnvironment(rootSite, helpers) {
  const { box, cylinder, tube, paint, materials, crownGeometry, batch } = helpers
  const group = new THREE.Group()
  group.name = 'warehouse-site-roads'
  group.userData.sitePart = 'roads'
  group.userData.positionStatus = 'Surrounding road alignment is illustrative; not a surveyed site plan.'
  rootSite.add(group)
  // Thin ground surfaces continue to the asset boundary; there is no raised display plinth.
  box(group, 'grass', 0, -.31, 0, 240, .12, 190)
  for (const x of [-86, 86]) box(group, 'road', x, -.09, 0, 14, .32, 190)
  for (const z of [-72, 78]) box(group, 'road', 0, -.089, z, 240, .32, 14)
  // The gate drive reaches both the loading-yard road and the public road.
  box(group, 'road', 24, -.088, 55.3, 26, .32, 39.6)
  box(group, 'paving', -33, .04, 56, 83, .14, 8)
  box(group, 'paving', 58.5, .04, 56, 31, .14, 8)
  for (const x of [-74, 74]) box(group, 'grass', x, -.01, 0, 6, .18, 108)
  box(group, 'grass', 0, -.02, -56, 148, .16, 7)

  // Sidewalks, individual curb modules and the cross-street intersection.
  for (const x of [-96, -76, 76, 96]) {
    for (const [center, length] of [[-87, 16], [3, 136], [90, 10]]) {
      box(group, 'paving', x, -.055, center, 4, .38, length)
    }
    for (let z = -94; z <= 93; z += 2) {
      if (Math.abs(z - 78) < 8 || Math.abs(z + 72) < 8) continue
      box(group, 'edge', x + (Math.abs(x) < 86 ? Math.sign(x) * 2 : -Math.sign(x) * 2), .07, z, .23, .28, 1.94)
    }
  }
  for (const z of [-82, -62, 68, 88]) {
    for (const [x, width] of [[-109, 22], [0, 148], [109, 22]]) {
      if (z === 68 && x === 0) {
        box(group, 'paving', -35.5, -.055, z, 77, .38, 4)
        box(group, 'paving', 57.5, -.055, z, 33, .38, 4)
      } else box(group, 'paving', x, -.055, z, width, .38, 4)
    }
    for (let x = -119; x <= 119; x += 2) {
      if (Math.abs(Math.abs(x) - 86) < 8 || (z === 68 && x > 3 && x < 42)) continue
      box(group, 'edge', x, .07, z + (z === -82 || z === 68 ? 2 : -2), 1.94, .28, .23)
    }
  }
  // White broken lane separators and a paired yellow centre line establish two-way traffic.
  for (const z of [-72, 78]) {
    for (let x = -119; x < 119; x += 8) {
      if (Math.abs(Math.abs(x) - 86) < 13 || (z === 78 && x > 3 && x < 42)) continue
      for (const dz of [-3.4, 3.4]) paint(group, 'line', x, z + dz, x + 4.0, z + dz, .13, .088)
      for (const dz of [-.19, .19]) paint(group, 'yellow', x, z + dz, Math.min(x + 8, 120), z + dz, .12, .089)
    }
  }
  for (const x of [-86, 86]) {
    for (let z = -94; z < 93; z += 8) {
      if (Math.abs(z - 78) < 14 || Math.abs(z + 72) < 14) continue
      for (const dx of [-3.4, 3.4]) paint(group, 'line', x + dx, z, x + dx, z + 4, .13, .088)
      for (const dx of [-.19, .19]) paint(group, 'yellow', x + dx, z, x + dx, Math.min(z + 8, 95), .12, .089)
    }
  }
  const crossing = (x, z, rotation = false) => {
    for (let offset = -5.9; offset <= 5.9; offset += 1.22) {
      box(group, 'line', rotation ? x : x + offset, .092, rotation ? z + offset : z,
        rotation ? 3.6 : .64, .025, rotation ? .64 : 3.6)
    }
  }
  // Mark crossings on all four arms of the east junction and across the entrance throat.
  for (const x of [-86, 86]) {
    crossing(x, 67); crossing(x, 89)
    crossing(x - 11, 78, true); crossing(x + 11, 78, true)
  }
  for (let x = 12.4; x <= 36; x += 1.3) box(group, 'line', x, .09, 65.6, .68, .026, 3.4)
  for (const [x, direction] of [[18, -1], [31, 1]]) {
    paint(group, 'line', x, 46, x, 51, .18, .089)
    const tip = direction < 0 ? 46 : 51
    paint(group, 'line', x, tip, x - .8, tip - direction * 1.5, .18, .089)
    paint(group, 'line', x, tip, x + .8, tip - direction * 1.5, .18, .089)
  }
  // Drainage slots sit along the curb rather than in the vehicle path.
  for (let x = -64; x <= 68; x += 22) {
    for (const z of [71.3, 84.7]) {
      box(group, 'dark', x, .084, z, 1.3, .028, .42)
      for (let grate = -.5; grate <= .5; grate += .2) box(group, 'steel', x + grate, .106, z, .06, .025, .37)
    }
  }

  const fence = (x1, z1, x2, z2) => {
    const length = Math.hypot(x2 - x1, z2 - z1), direction = new THREE.Vector3(x2 - x1, 0, z2 - z1).normalize()
    const horizontal = Math.abs(x2 - x1) > Math.abs(z2 - z1)
    box(group, 'concrete', (x1 + x2) / 2, .17, (z1 + z2) / 2,
      horizontal ? length : .43, .33, horizontal ? .43 : length)
    for (const y of [.65, 2.15]) tube(group, 'dark', [x1, y, z1], [x2, y, z2], .043)
    for (let offset = 0; offset <= length; offset += .65) {
      const x = x1 + direction.x * offset, z = z1 + direction.z * offset
      const post = Math.round(offset / .65) % 6 === 0
      box(group, 'dark', x, 1.23, z, post ? .11 : .033, post ? 2.12 : 1.75, post ? .11 : .033)
    }
  }
  fence(-74, 59.5, 3.5, 59.5); fence(43, 59.5, 74, 59.5)
  fence(-74, -59.5, 74, -59.5)
  for (const x of [-74, 74]) fence(x, -59.5, x, 59.5)

  const tree = (x, z, seed, size = 1) => {
    cylinder(group, 'bark', x, 2.3 * size, z, .14 * size, 4.6 * size)
    for (const [dx, dz, rise] of [[-1.1, .2, 0], [1.0, -.2, .1], [0, -.7, 1.3]]) {
      tube(group, 'bark', [x, 2.7 * size, z], [x + dx * size, (4.6 + rise) * size, z + dz * size], .075 * size)
      const crown = new THREE.Mesh(crownGeometry, materials[(seed + rise) % 2 ? 'tree' : 'treeLight'])
      crown.position.set(x + dx * size, (4.6 + rise) * size, z + dz * size)
      crown.scale.set(1.8 * size, 1.6 * size, 1.75 * size); crown.rotation.y = seed * .7
      crown.castShadow = true; group.add(crown)
    }
    box(group, 'bark', x, -.04, z, 1.4, .1, 1.4)
  }
  // Offset tree groups leave long sightlines to the gate and the building.
  for (const x of [-105, 105]) {
    for (let z = -48, seed = 0; z <= 43; z += 19, seed++) tree(x + (seed % 2) * 2, z, seed, 1.1)
  }
  for (const x of [-69.7, 69.7]) {
    for (let z = -43, seed = 0; z <= 38; z += 20, seed++) tree(x, z, seed, .76)
  }
  for (const [x, z, seed] of [[-57, 92, 2], [-30, 92, 1], [54, 92, 3], [-43, -88, 2], [3, -89, 1], [40, -88, 4]]) tree(x, z, seed, .9)
  const streetLamp = (x, z, direction) => {
    cylinder(group, 'concrete', x, .2, z, .29, .42)
    cylinder(group, 'steel', x, 4.45, z, .10, 8.6)
    tube(group, 'steel', [x, 8.55, z], [x, 8.9, z + direction * 2.4], .075)
    box(group, 'dark', x, 8.85, z + direction * 2.4, .55, .16, 1.25)
    box(group, 'siteLamp', x, 8.75, z + direction * 2.4, .43, .035, 1.10)
  }
  for (const x of [-112, -54, -4, 48, 112]) {
    streetLamp(x, 67, 1)
    streetLamp(x, -61, -1)
  }
  // Modest signal heads clarify the foreground junction without billboard clutter.
  for (const [x, z, rotation] of [[76, 67, 0], [96, 89, Math.PI]]) {
    cylinder(group, 'steel', x, 2.9, z, .075, 5.7)
    const signal = box(group, 'dark', x, 5.45, z, .37, 1.0, .25)
    signal.rotation.y = rotation
    for (const [y, type] of [[5.74, 'red'], [5.46, 'yellow'], [5.18, 'siteLamp']]) {
      cylinder(group, type, x, y, z + Math.cos(rotation) * .15, .102, .035).rotation.x = Math.PI / 2
    }
  }
  batch(group)
}

function createEntrance(rootSite, helpers) {
  const { box, cylinder, tube, paint, materials, crownGeometry, batch } = helpers
  const group = new THREE.Group()
  group.name = 'warehouse-site-entrance'
  group.userData.sitePart = 'entrance'
  group.userData.reference = 'User supplied entrance photo; silver cantilever canopy, rounded glass guardhouse and vehicle barriers.'
  group.userData.positionStatus = 'South gate position is illustrative; site survey not provided.'
  group.userData.gateCenter = [22, 0, 56]
  rootSite.add(group)
  const rounded = (type, x, y, z, width, height, depth, radius) => roundedBlock(group, materials, type, x, y, z, width, height, depth, radius)
  // The compact booth anchors the broad, thin metal canopy seen in the photograph.
  rounded('paving', 8.4, .16, 56.8, 10.3, .32, 10.3, 1.65)
  rounded('white', 8.4, 1.95, 55.9, 6.8, 3.6, 4.7, 1.15)
  rounded('glassLight', 8.4, 2.2, 56.02, 6.9, 2.85, 4.79, 1.2)
  rounded('steel', 8.4, .72, 56.02, 7.03, .18, 4.89, 1.22)
  rounded('steel', 8.4, 3.66, 56.02, 7.07, .15, 4.94, 1.25)
  rounded('white', 8.4, 3.92, 56.05, 8.3, .4, 6.1, 1.8)
  rounded('dark', 8.4, 4.16, 56.05, 9.7, .16, 7.4, 2.0)
  rounded('steel', 8.4, 4.48, 56.05, 9.8, .53, 7.45, 2.05)
  rounded('white', 8.4, 4.76, 56.05, 9.84, .08, 7.50, 2.06)
  rounded('siteLamp', 8.4, 4.20, 56.05, 9.76, .028, 7.46, 2.04)
  for (const x of [6.55, 8.45, 10.3]) {
    for (const z of [53.62, 58.42]) box(group, 'steel', x, 2.2, z, .065, 2.85, .08)
  }
  for (const x of [4.94, 11.87]) for (const z of [54.85, 56.65]) box(group, 'steel', x, 2.2, z, .08, 2.85, .065)
  for (const z of [53.62, 58.42]) box(group, 'steel', 8.4, 2.77, z, 4.6, .07, .09)
  box(group, 'steel', 10.42, 1.96, 58.49, 1.08, 2.45, .08)
  box(group, 'glass', 10.42, 2.16, 58.55, .85, 1.72, .035)
  box(group, 'dark', 10.08, 1.75, 58.59, .04, .25, .06)
  for (const x of [12.9, 39.6]) {
    cylinder(group, 'white', x, 3.3, 56.6, .47, 6.5)
    cylinder(group, 'steel', x, .3, 56.6, .60, .36)
  }
  // Long silver fascia, slim trim, underside joints and recessed warm luminaires.
  box(group, 'steel', 24, 6.66, 56.6, 33.7, .72, 5.9)
  box(group, 'edge', 24, 6.22, 56.6, 33.5, .15, 5.65)
  box(group, 'white', 24, 7.065, 56.6, 33.86, .09, 5.98)
  for (const z of [53.60, 59.60]) {
    box(group, 'dark', 24, 6.53, z, 33.65, .12, .06)
    box(group, 'white', 24, 6.32, z, 33.67, .065, .09)
  }
  for (let x = 9; x <= 39; x += 3.2) {
    box(group, 'edge', x, 6.13, 56.6, .035, .025, 5.6)
    box(group, 'siteLamp', x, 6.11, 56.5, 1.1, .035, .21)
  }
  // A planted median separates inbound and outbound lanes under the gate.
  rounded('edge', 25, .19, 57.0, 1.9, .38, 13.2, .94)
  rounded('paving', 25, .39, 57.0, 1.69, .06, 12.8, .82)
  for (const x of [14, 37.6]) paint(group, 'line', x, 48, x, 65, .16, .092)
  for (let z = 50.8; z <= 62.5; z += 1.2) {
    box(group, 'yellow', 24.035, .23, z, .025, .2, .45)
    box(group, 'yellow', 25.965, .23, z, .025, .2, .45)
  }
  const barrier = (x, direction, open) => {
    rounded('dark', x, .84, 57.9, .58, 1.26, .68, .10)
    box(group, 'steel', x, .85, 58.255, .4, .8, .03)
    box(group, 'siteLamp', x, 1.35, 58.26, .32, .07, .045)
    cylinder(group, 'steel', x, 1.28, 57.9, .15, .81).rotation.x = Math.PI / 2
    const angle = open ? .86 : 0, length = 6.1
    const dx = Math.cos(angle) * direction, dy = Math.sin(angle)
    const arm = box(group, 'white', x + dx * length / 2, 1.34 + dy * length / 2, 57.92, length, .13, .17)
    arm.rotation.z = direction * angle
    for (let offset = .55; offset < length; offset += 1.1) {
      const stripe = box(group, 'red', x + dx * offset, 1.34 + dy * offset, 58.015, .43, .135, .025)
      stripe.rotation.z = direction * angle
    }
  }
  barrier(24.24, -1, false); barrier(25.76, 1, true)
  // Reader columns and compact bollards are legible at the entry viewpoint.
  for (const x of [13.6, 37.9]) {
    cylinder(group, 'steel', x, .96, 60.2, .11, 1.72)
    box(group, 'dark', x, 1.44, 60.3, .5, .48, .18)
    box(group, 'glass', x, 1.47, 60.40, .35, .3, .035)
    for (const z of [53.6, 62.4]) {
      cylinder(group, 'steel', x, .68, z, .095, 1.25)
      cylinder(group, 'yellow', x, .86, z, .098, .18)
    }
  }
  for (const [x, z, width, depth] of [[.1, 56, 4.7, 9.6], [44.6, 56.8, 4.8, 10.9]]) {
    rounded('edge', x, .26, z, width, .5, depth, 1.4)
    rounded('bark', x, .54, z, width - .32, .07, depth - .35, 1.25)
    for (let plant = 0; plant < 17; plant++) {
      const angle = plant * 2.39, radius = .5 + (plant % 4) * .45
      const shrub = new THREE.Mesh(crownGeometry, materials[plant % 4 === 0 ? 'flowers' : 'treeLight'])
      shrub.position.set(x + Math.cos(angle) * radius * .65, .91 + (plant % 2) * .13, z + Math.sin(angle) * radius * 1.75)
      shrub.scale.set(.72, .52, .80); shrub.rotation.y = angle
      shrub.castShadow = true; group.add(shrub)
    }
  }
  batch(group)
}
