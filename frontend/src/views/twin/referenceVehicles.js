import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

// The supplied scale-model photos show box trucks at the loading apron and a
// small row of cars on the east side. Positions are illustrative, not surveyed.
// Each complete vehicle is one coloured mesh, with its own persistent identity.
export function createReferenceVehicles(rootSite, materials) {
  const finish = materials.vehicleFinish ??= new THREE.MeshStandardMaterial({
    name: 'vehicleFinish', vertexColors: true, roughness: .46, metalness: .12,
  })
  const fleet = new THREE.Group()
  fleet.name = 'warehouse-photo-vehicles'
  fleet.userData.sitePart = 'vehicles'
  fleet.userData.reference = 'Four supplied photographs of the physical DIPARK scale model'
  rootSite.add(fleet)
  for (const spec of [
    { id: 'loading-blue-west', kind: 'truck', x: -43.6, z: 31.6, body: '#17608e', cargo: '#154b73' },
    { id: 'loading-red-centre', kind: 'truck', x: -19.6, z: 31.6, body: '#ede8da', cargo: '#633c39' },
    { id: 'loading-blue-east', kind: 'truck', x: 28.4, z: 31.6, body: '#23658c', cargo: '#254855' },
    { id: 'parking-red', kind: 'car', x: 55.2, z: 19, rotation: Math.PI / 2, body: '#8e3d30' },
    { id: 'parking-green', kind: 'car', x: 55.2, z: 12, rotation: Math.PI / 2, body: '#214f50' },
    { id: 'parking-charcoal', kind: 'car', x: 55.2, z: 5, rotation: Math.PI / 2, body: '#424b4c' },
    { id: 'parking-white-front', kind: 'car', x: -3, z: 38.2, rotation: Math.PI / 2, body: '#e3e4df' },
  ]) {
    const group = new THREE.Group()
    group.name = `reference-vehicle-${spec.id}`
    group.userData = { vehicleId: spec.id, vehicleKind: spec.kind, referenceBodyColour: spec.body }
    group.position.set(spec.x, .19, spec.z)
    group.rotation.y = spec.rotation || 0
    const builder = vehicleBuilder()
    if (spec.kind === 'truck') truck(builder, spec.body, spec.cargo)
    else car(builder, spec.body)
    const mesh = new THREE.Mesh(builder.finish(), finish)
    mesh.name = `${group.name}-geometry`
    mesh.castShadow = true
    mesh.receiveShadow = true
    group.add(mesh)
    fleet.add(group)
  }
  return fleet
}

const rubber = '#202728'
const glazing = '#243c46'
const trim = '#465356'
const chrome = '#afb9b9'
const headlamp = '#eee9d2'
const taillamp = '#aa372d'

function vehicleBuilder() {
  const parts = []
  const add = (geometry, colour) => {
    const flat = geometry.index ? geometry.toNonIndexed() : geometry
    if (flat !== geometry) geometry.dispose()
    flat.deleteAttribute('uv')
    const rgb = new THREE.Color(colour)
    const colors = new Float32Array(flat.attributes.position.count * 3)
    for (let index = 0; index < colors.length; index += 3) {
      colors[index] = rgb.r; colors[index + 1] = rgb.g; colors[index + 2] = rgb.b
    }
    flat.setAttribute('color', new THREE.BufferAttribute(colors, 3))
    parts.push(flat)
  }
  const box = (colour, x, y, z, width, height, depth) =>
    add(new THREE.BoxGeometry(width, height, depth).translate(x, y, z), colour)
  const quad = (colour, a, b, c, d) => {
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.Float32BufferAttribute([...a, ...b, ...c, ...a, ...c, ...d], 3))
    geometry.computeVertexNormals()
    add(geometry, colour)
  }
  const rod = (colour, from, to, radius) => {
    const a = new THREE.Vector3(...from), b = new THREE.Vector3(...to)
    const delta = b.clone().sub(a)
    const geometry = new THREE.CylinderGeometry(radius, radius, delta.length(), 6)
    geometry.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0, 1, 0), delta.normalize()))
    geometry.translate(...a.add(b).multiplyScalar(.5).toArray())
    add(geometry, colour)
  }
  const wheel = (x, z, radius, width, y = radius) => {
    add(new THREE.CylinderGeometry(radius, radius, width, 16).rotateZ(Math.PI / 2).translate(x, y, z), rubber)
    const side = Math.sign(x)
    add(new THREE.CylinderGeometry(radius * .57, radius * .57, .026, 12)
      .rotateZ(Math.PI / 2).translate(x + side * (width / 2 + .008), y, z), chrome)
    add(new THREE.CylinderGeometry(radius * .24, radius * .24, .033, 10)
      .rotateZ(Math.PI / 2).translate(x + side * (width / 2 + .028), y, z), trim)
    for (let spoke = 0; spoke < 5; spoke++) {
      const angle = spoke * Math.PI * 2 / 5
      rod(trim, [x + side * (width / 2 + .029), y, z],
        [x + side * (width / 2 + .029), y + Math.sin(angle) * radius * .46, z + Math.cos(angle) * radius * .46], .023)
    }
  }
  // Stations form a bevelled body with a real bonnet / boot silhouette.
  const loft = (colour, stations) => {
    const rings = stations.map(([z, width, bottom, shoulder, top, roofWidth = width * .84]) => [
      [-width * .87, bottom, z], [width * .87, bottom, z],
      [width, bottom + .10, z], [width, shoulder, z],
      [roofWidth, top, z], [-roofWidth, top, z],
      [-width, shoulder, z], [-width, bottom + .10, z],
    ])
    for (let station = 0; station < rings.length - 1; station++) {
      for (let side = 0; side < 8; side++) {
        const next = (side + 1) % 8
        quad(colour, rings[station][side], rings[station][next], rings[station + 1][next], rings[station + 1][side])
      }
    }
    for (const [ring, reverse] of [[rings[0], false], [rings.at(-1), true]]) {
      for (let side = 1; side < 7; side++) {
        const points = reverse ? [ring[0], ring[side], ring[side + 1]] : [ring[0], ring[side + 1], ring[side]]
        const geometry = new THREE.BufferGeometry()
        geometry.setAttribute('position', new THREE.Float32BufferAttribute(points.flat(), 3))
        geometry.computeVertexNormals(); add(geometry, colour)
      }
    }
  }
  return {
    box, quad, rod, wheel, loft,
    finish() {
      const geometry = mergeGeometries(parts, false)
      parts.forEach(part => part.dispose())
      geometry.computeBoundingBox(); geometry.computeBoundingSphere()
      return geometry
    },
  }
}

function car(b, colour) {
  b.loft(colour, [
    [-2.23, .76, .40, .78, .88], [-1.87, .86, .36, .85, .99],
    [-1.02, .91, .35, .92, 1.02], [.98, .91, .35, .92, 1.01],
    [1.82, .84, .40, .81, .87], [2.23, .74, .45, .69, .77],
  ])
  b.loft(colour, [
    [-1.20, .82, .89, .99, 1.02, .78], [-.61, .82, .92, 1.06, 1.47, .64],
    [.54, .82, .92, 1.06, 1.49, .65], [1.30, .82, .90, .99, 1.01, .77],
  ])
  // Sloped front and rear windscreens; side glass follows the tapered cabin.
  b.quad(glazing, [-.75, 1.095, 1.20], [.75, 1.095, 1.20], [.63, 1.498, .56], [-.63, 1.498, .56])
  b.quad(glazing, [.63, 1.464, -.64], [.75, 1.099, -1.125], [-.75, 1.099, -1.125], [-.63, 1.464, -.64])
  for (const sign of [-1, 1]) {
    const window = (points) => {
      const vertices = points.map(([x, y, z]) => [x * sign, y, z])
      if (sign > 0) vertices.reverse()
      b.quad(glazing, ...vertices)
    }
    window([[.831, 1.075, .08], [.831, 1.075, 1.10], [.689, 1.429, .47], [.689, 1.429, .08]])
    window([[.831, 1.075, -1.05], [.831, 1.075, -.025], [.689, 1.429, -.025], [.680, 1.410, -.56]])
    b.rod(trim, [sign * .927, .50, -.025], [sign * .927, .92, -.025], .013)
    b.rod(trim, [sign * .895, .52, -.96], [sign * .926, .89, -1.03], .013)
    b.rod(trim, [sign * .927, .51, .99], [sign * .927, .92, .99], .013)
    b.box(chrome, sign * .930, .91, -.37, .025, .042, .17)
    b.box(chrome, sign * .930, .91, .69, .025, .042, .17)
    b.rod(colour, [sign * .83, 1.055, .91], [sign * .985, 1.075, .86], .035)
    b.box(colour, sign * 1.013, 1.085, .84, .16, .115, .21)
    b.box(glazing, sign * 1.014, 1.083, .727, .13, .081, .013)
    b.box(headlamp, sign * .54, .722, 2.209, .35, .105, .054)
    b.box(taillamp, sign * .55, .766, -2.215, .30, .11, .05)
    b.wheel(sign * .876, -1.40, .335, .21)
    b.wheel(sign * .876, 1.37, .335, .21)
  }
  b.box(rubber, 0, .45, 0, 1.70, .14, 3.98)
  b.box(trim, 0, .574, 2.215, 1.29, .10, .045)
  b.box(trim, 0, .50, -2.218, 1.37, .09, .045)
  b.box(rubber, 0, .718, 2.251, .43, .075, .018)
  b.box('#d6dcd8', 0, .594, 2.242, .27, .075, .02)
  b.box('#d6dcd8', 0, .623, -2.245, .28, .085, .02)
}

function truck(b, cabColour, cargoColour) {
  b.box(rubber, 0, .56, -.12, 1.83, .27, 6.74)
  b.box(cargoColour, 0, 2.03, -1.02, 2.31, 2.69, 4.73)
  b.box(chrome, 0, .76, -1.02, 2.38, .14, 4.80)
  b.box(chrome, 0, 3.39, -1.02, 2.36, .075, 4.80)
  // Cab roof slopes into the windshield instead of a rectangular glass block.
  b.loft(cabColour, [
    [1.48, 1.07, .69, 2.55, 2.77, .99], [2.08, 1.075, .65, 2.58, 2.77, .99],
    [2.70, 1.06, .65, 1.82, 2.63, .965], [3.41, 1.015, .68, 1.57, 1.79, .94],
  ])
  b.quad(glazing, [-.86, 1.864, 3.365], [.86, 1.864, 3.365], [.85, 2.568, 2.77], [-.85, 2.568, 2.77])
  b.rod(rubber, [-.66, 1.892, 3.37], [-.18, 1.982, 3.286], .022)
  b.rod(rubber, [.12, 1.892, 3.37], [.63, 1.975, 3.289], .022)
  for (const sign of [-1, 1]) {
    const points = [[sign * 1.083, 1.69, 1.64], [sign * 1.083, 1.69, 2.79],
      [sign * 1.028, 2.42, 2.63], [sign * 1.084, 2.52, 1.64]]
    if (sign > 0) points.reverse()
    b.quad(glazing, ...points)
    b.box(cabColour, sign * 1.089, 1.52, 2.14, .038, .12, 1.06)
    b.rod(trim, [sign * 1.089, .85, 1.63], [sign * 1.089, 1.64, 1.63], .015)
    b.rod(trim, [sign * 1.089, .85, 2.79], [sign * 1.089, 1.64, 2.79], .015)
    b.box(trim, sign * 1.119, 1.45, 1.84, .04, .07, .19)
    b.box(chrome, sign * 1.075, .71, 2.21, .20, .12, .74)
    b.rod(trim, [sign * 1.055, 2.10, 2.78], [sign * 1.36, 2.04, 2.87], .037)
    b.box(trim, sign * 1.37, 2.07, 2.88, .17, .40, .13)
    b.box(chrome, sign * 1.37, 2.07, 2.803, .133, .335, .024)
    for (let z = -3.19; z <= 1.20; z += .27) {
      b.box(cargoColour, sign * 1.169, 2.05, z, .035, 2.49, .057)
    }
    b.box('#c39350', sign * 1.192, 1.08, -2.96, .031, .085, .18)
    b.box('#c39350', sign * 1.192, 1.08, .94, .031, .085, .18)
    b.box(headlamp, sign * .70, 1.055, 3.432, .36, .19, .041)
    b.box('#d9973e', sign * .91, 1.25, 3.414, .13, .12, .04)
    b.wheel(sign * 1.041, 2.44, .48, .28)
    b.wheel(sign * 1.041, -2.16, .48, .31)
    b.box(rubber, sign * 1.023, .61, -2.73, .38, .57, .08)
  }
  b.box(trim, 0, .82, 3.44, 2.11, .21, .11)
  b.box(rubber, 0, 1.375, 3.436, 1.28, .28, .055)
  for (const y of [1.29, 1.38, 1.47]) b.box(chrome, 0, y, 3.47, 1.12, .024, .027)
  b.box('#c5d2d9', 0, .86, 3.504, .38, .13, .02)
  // Rear split cargo doors, locking bars and tail lamps.
  for (const sign of [-1, 1]) {
    b.box(cargoColour, sign * .582, 2.04, -3.405, 1.115, 2.49, .035)
    b.rod(chrome, [sign * .53, .90, -3.445], [sign * .53, 3.15, -3.445], .027)
    for (const y of [1.15, 2.95]) b.box(chrome, sign * 1.05, y, -3.445, .16, .08, .035)
    b.box(taillamp, sign * .83, .695, -3.475, .27, .13, .039)
  }
  b.box(trim, 0, 2.04, -3.431, .022, 2.57, .03)
  b.box(chrome, 0, .50, -3.45, 2.20, .12, .12)
}
