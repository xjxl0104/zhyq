import { addReferenceWordmark } from './referenceWordmark.js'

// The four physical-model photographs define the facade, not an office curtain wall.
// Keep dedicated material names: the runtime landscape finish pass must not recolor it.
export function createFacadeMaterials(material) {
  material('facadeIvory', '#edeee8', { roughness: .82 })
  material('facadeRecess', '#62686a', { roughness: .9 })
  // The photographs show clear glazing in shadow, not black opaque cladding.
  // Alpha glazing reuses the existing environment reflections without adding
  // a second full-scene refraction render on every orbit frame.
  const glazing = material('facadeGlazing', '#d9e9e7', {
    roughness: .12, metalness: .05, transparent: true, opacity: .24,
    depthWrite: false, envMapIntensity: 1.25,
  })
  glazing.userData.surfaceRole = 'architectural-glass'
  glazing.userData.facadeDetail = { kind: 'glass-grid', pitch: [.95, 0], width: [.055, 0], color: '#59666a' }
  const cornerGlazing = material('facadeCornerGlazing', '#d9e9e7', {
    roughness: .12, metalness: .05, transparent: true, opacity: .24, depthWrite: false,
  })
  cornerGlazing.userData.surfaceRole = 'architectural-glass'
  cornerGlazing.userData.facadeDetail = { kind: 'glass-grid', pitch: [1.15, 1.6], width: [.055, .065], color: '#59666a' }
  material('facadeFrame', '#4c575a', { roughness: .66, metalness: .18 })
  material('facadeTeal', '#419e9c', { roughness: .65, metalness: .06 })
  material('facadeRib', '#d1d6d0', { roughness: .85 })
  material('facadeLogoTeal', '#409995', { roughness: .65 })
  material('facadeLogoBlue', '#277da7', { roughness: .65 })
  material('facadeLogoWhite', '#f5f5ee', { roughness: .65 })
  const sign = material('facadeSignIvory', '#dce1dd', { roughness: .85 })
  sign.userData.facadeDetail = { kind: 'corrugation', pitch: .29, contrast: .11 }
  const shutter = material('facadeShutter', '#606c70', { roughness: .7 })
  shutter.userData.facadeDetail = { kind: 'corrugation', pitch: .28, contrast: .08, axis: 'y' }
}

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

function slitWall(group, { index, height, box, side, sign, base = 0, wallHeight = height }) {
  const extent = side ? 54 : 96
  const at = side ? 48 : 27
  // Stop the solid elevation where the wraparound glass begins. Leaving the
  // original wall behind a transparent pane would still look opaque.
  const startOfWall = -extent / 2
  const endOfWall = sign > 0 && index > 1 && index < 7 ? (side ? 13.7 : 31.7) : extent / 2
  const face = (type, along, y, length, tall, offset = 0, thick = .5) => {
    const start = Math.max(startOfWall, along - length / 2)
    const end = Math.min(endOfWall, along + length / 2)
    if (end <= start) return
    let rectangles = [[start, end, y - tall / 2, y + tall / 2]]
    if (!side && index > 1 && type === 'facadeIvory') {
      // The little balcony doors are glass too. Cut their openings through the
      // white spandrels/bridges instead of putting a transparent pane on a wall.
      const bottom = height * .35 + .075, top = height * .35 + 1.725
      for (const x of [-34, -10, 15]) {
        const next = []
        for (const [left, right, low, high] of rectangles) {
          const cutLeft = Math.max(left, x - .44), cutRight = Math.min(right, x + .44)
          const cutLow = Math.max(low, bottom), cutHigh = Math.min(high, top)
          if (cutLeft >= cutRight || cutLow >= cutHigh) { next.push([left, right, low, high]); continue }
          if (left < cutLeft) next.push([left, cutLeft, low, high])
          if (cutRight < right) next.push([cutRight, right, low, high])
          if (low < cutLow) next.push([cutLeft, cutRight, low, cutLow])
          if (cutHigh < high) next.push([cutLeft, cutRight, cutHigh, high])
        }
        rectangles = next
      }
    }
    for (const [left, right, low, high] of rectangles) {
      if (side) box(group, type, sign * (at + offset), (low + high) / 2, (left + right) / 2, thick, high - low, right - left)
      else box(group, type, (left + right) / 2, (low + high) / 2, sign * (at + offset), right - left, high - low, thick)
    }
  }
  // Solid white spandrels surround two fine recessed ribbons per functional level.
  const rows = [base + wallHeight * .28, base + wallHeight * .77]
  const slotHeight = Math.min(.82, wallHeight * .115)
  let low = base
  for (const [row, y] of rows.entries()) {
    const lowerEdge = y - slotHeight / 2
    face('facadeIvory', 0, (low + lowerEdge) / 2, extent, lowerEdge - low)
    const patterns = side ? sideSlots : frontSlots
    const segments = patterns[((index - 1) * 2 + row + (sign < 0 ? 1 : 0)) % patterns.length]
    // The dark-looking infill in the photos is also clear glass in shade.
    // Keep every ribbon segment open; only the white bridges are opaque.
    let recessStart = startOfWall
    for (const [start, end] of segments) {
      if (start > recessStart) face('facadeGlazing', (recessStart + start) / 2, y, start - recessStart, slotHeight - .04, .115, .035)
      face('facadeGlazing', (start + end) / 2, y, end - start, slotHeight - .04, .115, .035)
      recessStart = end
    }
    if (recessStart < extent / 2) face('facadeGlazing', (recessStart + extent / 2) / 2, y, extent / 2 - recessStart, slotHeight - .04, .115, .035)
    // White bridges at differing positions break the otherwise continuous grey slot.
    const bridges = side ? [-17 + (index % 3) * 8] : [-35 + (index % 3) * 11, 2 + (index % 2) * 17]
    for (const along of bridges) face('facadeIvory', along, y, side ? 5.3 : 6.4, slotHeight + .08, .17, .22)
    low = y + slotHeight / 2
  }
  face('facadeIvory', 0, (low + base + wallHeight) / 2, extent, base + wallHeight - low)
}

function corner(group, { index, height, box }) {
  // Clear wraparound glazing and one broad green fascia per floor.
  box(group, 'facadeCornerGlazing', 40, height / 2, 27.42, 16.6, height, .2)
  box(group, 'facadeCornerGlazing', 48.42, height / 2, 20.6, .2, height, 13.8)
  // Keep the physical perimeter, but filter the fine mullions in the material.
  for (const x of [31.7, 48.5]) box(group, 'facadeFrame', x, height / 2, 27.56, .12, height, .1)
  box(group, 'facadeFrame', 48.56, height / 2, 13.7, .1, height, .12)
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
    for (const dx of [-.48, .48]) box(group, 'facadeFrame', x + dx, y + .9, sign * 27.36, .08, 1.81, .075)
    for (const dy of [-.865, .865]) box(group, 'facadeFrame', x, y + .9 + dy, sign * 27.36, .88, .08, .075)
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
    for (const x of [-43, -31, -19, -7, 5, 17, 29, 41]) {
      const teal = x === -43 || x === 29
      const wall = teal ? 'facadeTeal' : 'facadeRecess'
      box(group, wall, x, 2.63, sign * 26.95, 11.6, 5.26, .38)
      box(group, wall, x, 6.07, sign * 26.95, 11.6, .66, .38)
      box(group, wall, x - 4.875, 5.5, sign * 26.95, 1.85, .48, .38)
      box(group, wall, x + 4.275, 5.5, sign * 26.95, 3.05, .48, .38)
      box(group, 'facadeRecess', x - .6, 2.6, sign * 27.17, 7, 4.6, .08)
      box(group, 'facadeShutter', x - .6, 2.5, sign * 27.23, 6.7, 4.35, .06)
      box(group, 'facadeRib', x + 4.0, 1.65, sign * 27.22, .95, 2.8, .1)
      box(group, 'facadeFrame', x + 4.0, 2.15, sign * 27.29, .67, .65, .025)
      box(group, 'facadeIvory', x + 5.65, 3.2, sign * 27.15, .55, 6.4, 1.0)
      box(group, 'facadeGlazing', x - .6, 5.5, sign * 27.19, 6.7, .48, .04)
    }
    box(group, 'facadeIvory', 0, 6.55, sign * 27.55, 97.4, .38, 2.0)
    box(group, 'facadeRib', 0, 6.29, sign * 28.4, 97.4, .1, .18)
  }
  for (const sign of [-1, 1]) {
    box(group, 'facadeTeal', sign * 48, 1.3, 0, .65, 2.6, 54)
    box(group, 'facadeTeal', sign * 48, 5.7, 0, .65, 1.4, 54)
    let solidStart = -27
    for (let z = -21; z <= 21; z += 10.5) {
      const windowStart = z - 3.35, windowEnd = z + 3.35
      box(group, 'facadeTeal', sign * 48, 3.8, (solidStart + windowStart) / 2, .65, 2.4, windowStart - solidStart)
      box(group, 'facadeGlazing', sign * 48.35, 3.8, z, .08, 2.4, 6.7)
      box(group, 'facadeRecess', sign * 48.42, 3.8, z, .08, .075, 6.7)
      box(group, 'facadeIvory', sign * 48.45, 1.4, z + 4, .1, 2.65, .95)
      solidStart = windowEnd
    }
    box(group, 'facadeTeal', sign * 48, 3.8, (solidStart + 27) / 2, .65, 2.4, 27 - solidStart)
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
    box(group, 'facadeSignIvory', 39, center, 27.7, 20, tall, 1.0)
    box(group, 'facadeSignIvory', 48.7, center, 9.5, 1.0, tall, 36.5)
    addReferenceWordmark(group, materials, { x: 33.3, y: 2.2, z: 28.21, height: 1.9, variant: 'box' })
    addReferenceWordmark(group, materials, { x: 49.21, y: 2.2, z: 14.6, height: 1.9, rotation: Math.PI / 2, variant: 'box' })
  }
}

export function addReferenceRoof(group, { box, materials }) {
  group.userData.referenceStyle = 'photo-flat-roof-service-blocks'
  box(group, 'facadeIvory', 0, 0, 0, 97, .5, 55)
  box(group, 'facadeRib', 0, .29, 0, 95.4, .08, 53.4)
  for (const z of [-27, 27]) box(group, 'facadeIvory', 0, .85, z, 97, 1.7, .6)
  for (const x of [-48, 48]) box(group, 'facadeIvory', x, .85, 0, .6, 1.7, 55)
  // The blue PARK sign sits on the solid top parapet in the front photograph.
  addReferenceWordmark(group, materials, { x: -45, y: .21, z: 27.31, height: 1.3, variant: 'roof' })
  // Simple roof volumes seen in all four references, with unobtrusive service doors.
  for (const [x, z, w, d, h] of [[-34, -9, 14, 10, 4.2], [-7, -10, 24, 11, 4.5], [22, -9, 16, 10, 4.0], [40, -8, 6, 7, 3.6]]) {
    box(group, 'facadeIvory', x, h / 2 + .35, z, w, h, d)
    box(group, 'facadeRib', x, h + .4, z, w + .2, .15, d + .2)
    box(group, 'facadeRecess', x + w / 2 - 1.5, 1.55, z + d / 2 + .03, 1.25, 2.35, .05)
  }
}
