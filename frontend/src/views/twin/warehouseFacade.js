import * as THREE from 'three'
import { Font } from 'three/addons/loaders/FontLoader.js'
import { TextGeometry } from 'three/addons/geometries/TextGeometry.js'
import fontData from 'three/examples/fonts/helvetiker_bold.typeface.json' with { type: 'json' }

// The four physical-model photographs define the facade, not an office curtain wall.
// Keep dedicated material names: the runtime landscape finish pass must not recolor it.
export function createFacadeMaterials(material) {
  material('facadeIvory', '#edeee8', { roughness: .82 })
  material('facadeRecess', '#62686a', { roughness: .9 })
  material('facadeGlazing', '#202d31', { roughness: .34, metalness: .22 })
  material('facadeFrame', '#4c575a', { roughness: .66, metalness: .18 })
  material('facadeTeal', '#419e9c', { roughness: .65, metalness: .06 })
  material('facadeRib', '#d1d6d0', { roughness: .85 })
}

const font = new Font(fontData)
// Unequal strips and white interruptions follow the long and short elevations.
const frontSlots = [
  [[-48, -27], [-17, 11], [18, 48]], [[-40, -8], [4, 31], [39, 48]],
  [[-48, -35], [-25, 3], [13, 39]], [[-33, -3], [8, 30], [39, 48]],
  [[-48, -19], [-10, 19], [29, 48]], [[-40, -13], [-4, 23], [33, 48]],
  [[-48, -34], [-24, 4], [14, 40]], [[-37, -7], [3, 30], [40, 48]],
  [[-48, -21], [-11, 17], [26, 48]], [[-43, -15], [-5, 22], [33, 48]],
  [[-48, -32], [-22, 7], [17, 43]], [[-37, -8], [2, 31], [40, 48]],
]
const sideSlots = [
  [[-27, -12], [-3, 20]], [[-18, 5], [14, 27]],
  [[-27, -5], [5, 27]], [[-18, 9], [17, 27]],
  [[-27, -13], [-4, 20]], [[-20, 3], [12, 27]],
]

function lettering(group, materials, { text = 'DIPARK', x, y, z, size, rotation = 0, type = 'facadeTeal' }) {
  const geometry = new TextGeometry(text, { font, size, depth: .055, curveSegments: 3, bevelEnabled: false })
  // The facade batcher combines indexed meshes. Extruded text is non-indexed.
  geometry.setIndex(Array.from({ length: geometry.attributes.position.count }, (_, index) => index))
  const mesh = new THREE.Mesh(geometry, materials[type])
  mesh.position.set(x, y, z)
  mesh.rotation.y = rotation
  mesh.name = 'reference-dipark-lettering'
  group.add(mesh)
}

function slitWall(group, { index, height, box, side, sign, base = 0, wallHeight = height }) {
  const extent = side ? 54 : 96
  const at = side ? 48 : 27
  const face = (type, along, y, length, tall, offset = 0, thick = .5) => side
    ? box(group, type, sign * (at + offset), y, along, thick, tall, length)
    : box(group, type, along, y, sign * (at + offset), length, tall, thick)
  // Solid white spandrels surround two fine recessed ribbons per functional level.
  const rows = [base + wallHeight * .28, base + wallHeight * .77]
  const slotHeight = Math.min(.82, wallHeight * .115)
  let low = base
  for (const [row, y] of rows.entries()) {
    const lowerEdge = y - slotHeight / 2
    face('facadeIvory', 0, (low + lowerEdge) / 2, extent, lowerEdge - low)
    face('facadeRecess', 0, y, extent, slotHeight, -.06, .3)
    const patterns = side ? sideSlots : frontSlots
    const segments = patterns[((index - 1) * 2 + row + (sign < 0 ? 1 : 0)) % patterns.length]
    for (const [start, end] of segments) {
      face('facadeGlazing', (start + end) / 2, y, end - start, slotHeight - .04, .115, .035)
      for (let pane = start + .65; pane < end - .1; pane += .82) {
        face('facadeFrame', pane, y, .045, slotHeight, .15, .06)
      }
    }
    // White bridges at differing positions break the otherwise continuous grey slot.
    const bridges = side ? [-17 + (index % 3) * 8] : [-35 + (index % 3) * 11, 2 + (index % 2) * 17]
    for (const along of bridges) face('facadeIvory', along, y, side ? 5.3 : 6.4, slotHeight + .08, .17, .22)
    low = y + slotHeight / 2
  }
  face('facadeIvory', 0, (low + base + wallHeight) / 2, extent, base + wallHeight - low)
}

function corner(group, { index, height, box }) {
  // Dark, continuous wraparound glazing and one broad green fascia per floor.
  // This is deliberately narrower than the previous blue checkerboard curtain wall.
  box(group, 'facadeGlazing', 40, height / 2, 27.42, 16.6, height, .2)
  box(group, 'facadeGlazing', 48.42, height / 2, 20.6, .2, height, 13.8)
  for (let x = 31.9; x <= 48; x += .92) box(group, 'facadeFrame', x, height / 2, 27.56, .055, height, .075)
  for (let z = 13.8; z <= 27; z += .92) box(group, 'facadeFrame', 48.56, height / 2, z, .075, height, .055)
  for (let y = 1.6; y < height; y += 1.6) {
    box(group, 'facadeFrame', 40, y, 27.56, 16.6, .065, .075)
    box(group, 'facadeFrame', 48.56, y, 20.6, .075, .065, 13.8)
  }
  box(group, 'facadeTeal', 39.4, .76, 27.88, 19.0, 1.15, .85)
  box(group, 'facadeTeal', 48.88, .76, 20.35, .85, 1.15, 15.9)
  box(group, 'facadeTeal', 39.4, 1.34, 27.88, 19.15, .09, 1.0)
  box(group, 'facadeTeal', 48.88, 1.34, 20.35, 1.0, .09, 16.0)
}

function balconies(group, { height, box, tube, cylinder }) {
  for (const sign of [-1, 1]) for (const x of [-34, -10, 15]) {
    const y = height * .35, front = sign * 28.1
    box(group, 'facadeIvory', x, y, sign * 27.55, 2.5, .22, 1.35)
    box(group, 'facadeGlazing', x, y + .9, sign * 27.3, .88, 1.65, .07)
    tube(group, 'facadeFrame', [x - 1.2, y + .9, front], [x + 1.2, y + .9, front], .025)
    for (let dx = -1.2; dx <= 1.21; dx += .3) cylinder(group, 'facadeFrame', x + dx, y + .5, front, .02, .82)
    for (const dx of [-1.2, 1.2]) {
      tube(group, 'facadeFrame', [x + dx, y + .9, sign * 27.3], [x + dx, y + .9, front], .025)
      cylinder(group, 'facadeFrame', x + dx, y + .5, sign * 27.6, .02, .82)
    }
  }
}

function docks(group, { box }) {
  for (const sign of [-1, 1]) {
    // Recessed loading bays; turquoise utility walls bookend the grey shutters.
    box(group, 'facadeRecess', 0, 3.25, sign * 26.6, 96, 6.5, .55)
    for (const x of [-43, -31, -19, -7, 5, 17, 29, 41]) {
      const teal = x === -43 || x === 29
      box(group, teal ? 'facadeTeal' : 'facadeRecess', x, 3.2, sign * 26.95, 11.6, 6.4, .38)
      box(group, 'facadeGlazing', x - .6, 2.6, sign * 27.17, 7, 4.6, .08)
      box(group, 'facadeFrame', x - .6, 2.5, sign * 27.23, 6.7, 4.35, .06)
      for (let y = .5; y < 4.65; y += .28) box(group, 'facadeRecess', x - .6, y, sign * 27.28, 6.6, .035, .035)
      box(group, 'facadeRib', x + 4.0, 1.65, sign * 27.22, .95, 2.8, .1)
      box(group, 'facadeGlazing', x + 4.0, 2.15, sign * 27.29, .67, .65, .025)
      box(group, 'facadeIvory', x + 5.65, 3.2, sign * 27.15, .55, 6.4, 1.0)
      box(group, 'facadeGlazing', x - .6, 5.5, sign * 27.19, 6.7, .48, .04)
    }
    box(group, 'facadeIvory', 0, 6.55, sign * 27.55, 97.4, .38, 2.0)
    box(group, 'facadeRib', 0, 6.29, sign * 28.4, 97.4, .1, .18)
  }
  for (const sign of [-1, 1]) {
    box(group, 'facadeTeal', sign * 48, 3.2, 0, .65, 6.4, 54)
    for (let z = -21; z <= 21; z += 10.5) {
      box(group, 'facadeGlazing', sign * 48.35, 3.8, z, .08, 2.4, 6.7)
      for (let dz = -3; dz < 3.2; dz += .8) box(group, 'facadeFrame', sign * 48.41, 3.8, z + dz, .07, 2.4, .055)
      box(group, 'facadeRecess', sign * 48.42, 3.8, z, .08, .075, 6.7)
      box(group, 'facadeIvory', sign * 48.45, 1.4, z + 4, .1, 2.65, .95)
    }
    box(group, 'facadeIvory', sign * 48.3, 6.55, 0, 1.65, .38, 55.4)
  }
}

export function addReferenceFacade(group, options) {
  const { index, height, box, materials } = options
  group.userData.referenceStyle = 'photo-ivory-ribbon-warehouse'
  const base = index === 1 ? 6.75 : 0
  if (index === 1) docks(group, options)
  for (const sign of [-1, 1]) for (const side of [false, true]) {
    slitWall(group, { ...options, side, sign, base, wallHeight: height - base })
  }
  if (index > 1) balconies(group, options)
  if (index > 1 && index < 7) corner(group, options)
  if (index === 7) {
    // Projecting corrugated white sign box wraps around both faces of the corner.
    const bottom = -.9, tall = height + .8, center = bottom + tall / 2
    box(group, 'facadeIvory', 39, center, 27.7, 20, tall, 1.0)
    box(group, 'facadeIvory', 48.7, center, 9.5, 1.0, tall, 36.5)
    for (let x = 29.15; x <= 48.9; x += .29) box(group, 'facadeRib', x, center, 28.23, .055, tall - .18, .09)
    for (let z = -8.55; z <= 27.65; z += .29) box(group, 'facadeRib', 49.23, center, z, .09, tall - .18, .055)
    lettering(group, materials, { x: 32.0, y: 2.2, z: 28.33, size: 1.9 })
    lettering(group, materials, { x: 49.33, y: 2.2, z: 14.6, size: 1.9, rotation: Math.PI / 2 })
    lettering(group, materials, { x: -45, y: height - 2.0, z: 27.3, size: 1.6 })
  }
}

export function addReferenceRoof(group, { box }) {
  group.userData.referenceStyle = 'photo-flat-roof-service-blocks'
  box(group, 'facadeIvory', 0, 0, 0, 97, .5, 55)
  box(group, 'facadeRib', 0, .29, 0, 95.4, .08, 53.4)
  for (const z of [-27, 27]) box(group, 'facadeIvory', 0, .85, z, 97, 1.7, .6)
  for (const x of [-48, 48]) box(group, 'facadeIvory', x, .85, 0, .6, 1.7, 55)
  // Simple roof volumes seen in all four references, with unobtrusive service doors.
  for (const [x, z, w, d, h] of [[-34, -9, 14, 10, 4.2], [-7, -10, 24, 11, 4.5], [22, -9, 16, 10, 4.0], [40, -8, 6, 7, 3.6]]) {
    box(group, 'facadeIvory', x, h / 2 + .35, z, w, h, d)
    box(group, 'facadeRib', x, h + .4, z, w + .2, .15, d + .2)
    box(group, 'facadeRecess', x + w / 2 - 1.5, 1.55, z + d / 2 + .03, 1.25, 2.35, .05)
  }
}
