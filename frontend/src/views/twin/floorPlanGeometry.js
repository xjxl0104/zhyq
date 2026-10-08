import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { COLUMN_X, COLUMN_Z, floorLayout } from './floorPlanData.js'

// Wall/door vertical detail is a visual convention, not a measurement from plan JPGs.
export const DETAIL = Object.freeze({ wall: .2, doorHeight: 2.4, interiorWallHeight: 3.2, column: .8 })
export function createPlanFloor(number) {
  const layout = floorLayout(number), structure = new THREE.Group(), interior = new THREE.Group(), fire = new THREE.Group()
  const overhead = new THREE.Group(); overhead.name = 'floor-' + number + '-overhead'; overhead.userData.planRole = 'overhead'; structure.add(overhead)
  const cube = new THREE.BoxGeometry(1, 1, 1), batches = new Map()
  const mats = Object.fromEntries(Object.entries({
    slab: '#deded4', column: '#eeece2', wall: '#e9e8df', wallCap: '#90a4a0',
    stairs: '#aebfba', rail: '#687f7c', doors: '#648d8a', lift: '#a6c6c3',
    reserved: '#d8d4c7', shaft: '#c6c9bd', lobby: '#c6dbce',
    zone1: '#e5ece2', zone2: '#e0ebe8', zone3: '#e7e9dc',
    terrace: '#c9d4c3', line: '#f9f8ec', parking: '#d1d9d4', water: '#83b7bf', utility: '#ddd4bc',
  }).map(([key, color]) => [key, new THREE.MeshStandardMaterial({ name: 'plan-' + key, color, roughness: .85 })]))
  function geometry(parent, kind, geo) {
    if (!batches.has(parent)) batches.set(parent, new Map())
    const batch = batches.get(parent)
    if (!batch.has(kind)) batch.set(kind, [])
    batch.get(kind).push(geo)
  }
  function box(parent, kind, x, y, z, w, h, d, angle = 0) {
    if (w <= 0 || h <= 0 || d <= 0) return
    const matrix = new THREE.Matrix4().compose(new THREE.Vector3(x - 80, y, z - 36), new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), angle), new THREE.Vector3(w, h, d))
    geometry(parent, kind, cube.clone().applyMatrix4(matrix))
  }
  function segment(parent, kind, from, to, height = DETAIL.interiorWallHeight, bottom = .18, thickness = DETAIL.wall) {
    const [x1, z1] = from, [x2, z2] = to
    box(parent, kind, (x1 + x2) / 2, bottom + height / 2, (z1 + z2) / 2, Math.hypot(x2 - x1, z2 - z1), height, thickness, -Math.atan2(z2 - z1, x2 - x1))
  }
  function line(x1, z1, x2, z2, kind = 'line', width = .12) { segment(interior, kind, [x1, z1], [x2, z2], .02, .22, width) }
  const roomEdges = room => {
    const [x1, z1, x2, z2] = room.bounds
    return { top: [[x1, z1], [x2, z1]], right: [[x2, z1], [x2, z2]], bottom: [[x1, z2], [x2, z2]], left: [[x1, z1], [x1, z2]] }
  }
  const openings = layout.rooms.map(room => {
    const [a, b] = roomEdges(room)[room.door], axis = a[0] === b[0] ? 1 : 0
    const centre = (a[axis] + b[axis]) / 2, width = Math.min(room.kind === 'freight' ? 2.6 : 1.4, b[axis] - a[axis] - .45)
    return { axis, fixed: a[1 - axis], start: centre - width / 2, end: centre + width / 2 }
  })
  const drawnEdges = new Set()
  function roomWall(from, to) {
    const key = JSON.stringify([from, to])
    if (drawnEdges.has(key)) return
    drawnEdges.add(key)
    const axis = from[0] === to[0] ? 1 : 0, start = from[axis], end = to[axis]
    const gaps = openings.filter(o => o.axis === axis && Math.abs(o.fixed - from[1 - axis]) < .01 && o.end > start && o.start < end).sort((a, b) => a.start - b.start)
    const at = value => axis ? [from[0], value] : [value, from[1]]
    let cursor = start
    for (const gap of gaps) {
      const left = Math.max(cursor, gap.start), right = Math.min(end, gap.end)
      if (left > cursor) segment(interior, 'wall', at(cursor), at(left))
      if (right > left) {
        segment(interior, 'wall', at(left), at(right), DETAIL.interiorWallHeight - DETAIL.doorHeight, .18 + DETAIL.doorHeight)
        segment(interior, 'doors', at(left), at(right), .035, .20, .3)
      }
      cursor = Math.max(cursor, right)
    }
    if (cursor < end) segment(interior, 'wall', at(cursor), at(end))
    segment(interior, 'wallCap', from, to, .07, DETAIL.interiorWallHeight + .18, .23)
  }
  const [l, t, r, b] = layout.footprint
  box(structure, 'slab', (l + r) / 2, -.02, (t + b) / 2, r - l + .4, .35, b - t + .4)
  // Actual grid positions: 12m/12.5m bays and the 280mm H/G expansion joint.
  const xs = number === -1 ? [122.5, 135, 147.5, 160] : COLUMN_X
  const zs = number === -1 ? [0, 12, 24] : COLUMN_Z
  for (const x of xs) for (const z of zs) {
    if (number === 7 && x > 3 && x < 37.5 && z >= 12 && z <= 60) continue
    box(structure, 'column', x, layout.height / 2, z, DETAIL.column, layout.height, DETAIL.column)
  }
  if (number !== -1) {
    for (const x of COLUMN_X) {
      if (number === 7 && x > 3 && x < 37.5) continue
      box(overhead, 'column', x, layout.height - .35, 36, .55, .6, 72)
    }
    for (const z of COLUMN_Z) {
      if (number === 7 && z >= 12 && z <= 60) {
        box(overhead, 'column', 1.5, layout.height - .35, z, 3, .6, .55)
        box(overhead, 'column', 98.75, layout.height - .35, z, 122.5, .6, .55)
      } else box(overhead, 'column', 80, layout.height - .35, z, 160, .6, .55)
    }
    // Low perimeter lets a cutaway read the footprint without hiding interiors.
    for (const [a, c] of [[[0, 0], [160, 0]], [[160, 0], [160, 72]], [[160, 72], [0, 72]], [[0, 72], [0, 0]]]) segment(interior, 'wall', a, c, .85)
  }
  for (const [index, zone] of layout.zones.entries()) {
    const [x1, z1, x2, z2] = zone.bounds
    const kind = zone.kind === 'terrace' ? 'terrace' : zone.kind === 'parking' ? 'parking' : 'zone' + (index % 3 + 1)
    box(interior, kind, (x1 + x2) / 2, .18, (z1 + z2) / 2, x2 - x1, .025, z2 - z1)
    if (zone.kind === 'terrace') {
      for (const [a, c] of [[[x1, z1], [x2, z1]], [[x2, z1], [x2, z2]], [[x2, z2], [x1, z2]], [[x1, z2], [x1, z1]]]) segment(interior, 'wall', a, c, 1.1)
    }
  }
  for (const partition of layout.partitions) {
    let z = partition.from
    for (const [start, end] of [...partition.gaps, [partition.to, partition.to]]) {
      segment(interior, 'wall', [partition.x, z], [partition.x, start], layout.height - .18)
      if (end > start) segment(interior, 'wall', [partition.x, start], [partition.x, end], layout.height - DETAIL.doorHeight - .18, DETAIL.doorHeight + .18)
      z = end
    }
  }
  for (const room of layout.rooms) {
    const [x1, z1, x2, z2] = room.bounds, cx = (x1 + x2) / 2, cz = (z1 + z2) / 2
    const freight = room.kind === 'freight', lift = freight || room.kind === 'passenger'
    const kind = room.reserved ? 'reserved' : lift ? 'lift' : room.kind === 'shaft' ? 'shaft' : room.kind === 'utility' ? 'utility' : 'lobby'
    box(interior, kind, cx, .21, cz, x2 - x1, .035, z2 - z1)
    for (const [a, c] of Object.values(roomEdges(room))) roomWall(a, c)
    if (lift) {
      const inset = .25
      // Lift shafts are open in cutaway. Dashed diagonal marks only denote future equipment.
      if (room.reserved) {
        line(x1 + inset, z1 + inset, x2 - inset, z2 - inset, 'rail', .09)
        line(x2 - inset, z1 + inset, x1 + inset, z2 - inset, 'rail', .09)
      } else {
        box(interior, 'doors', cx, .28, cz, x2 - x1 - .65, .12, z2 - z1 - .65)
        const horizontal = room.door === 'top' || room.door === 'bottom'
        const dz = room.door === 'top' ? z1 : z2, dx = room.door === 'left' ? x1 : x2
        if (horizontal) box(interior, 'lift', cx, 1.35, dz, freight ? 2.4 : 1.25, 2.3, .06)
        else box(interior, 'lift', dx, 1.35, cz, .06, 2.3, freight ? 2.4 : 1.25)
      }
    }
    if (room.kind.startsWith('stair')) {
      const alongX = room.kind === 'stair-x', length = (alongX ? x2 - x1 : z2 - z1) - 1.1
      const width = (alongX ? z2 - z1 : x2 - x1) - .55
      const stepCount = 12, rise = Math.min(layout.height / 2, 2.2) / stepCount
      for (let step = 0; step < stepCount; step++) {
        const distance = (step + .5) * length / stepCount
        for (const side of [-1, 1]) {
          const h = (side < 0 ? step + 1 : stepCount - step) * rise
          box(interior, 'stairs', alongX ? x1 + .55 + distance : cx + side * width / 4, .2 + h / 2, alongX ? cz + side * width / 4 : z1 + .55 + distance, alongX ? length / stepCount : width / 2 - .1, h, alongX ? width / 2 - .1 : length / stepCount)
        }
      }
      segment(interior, 'rail', alongX ? [x1 + .5, cz] : [cx, z1 + .5], alongX ? [x2 - .5, cz] : [cx, z2 - .5], .8, 1, .06)
    }
  }
  if (number === 1) {
    // Parking rows follow the main plan. Cars themselves are deliberately absent.
    for (const x of [1, 13, 21, 31, 37, 49.5, 62, 70, 82.5, 89, 98]) {
      if (x > 97) continue
      for (const start of [13, 25, 37, 49]) {
        if ((x === 21 || x === 49.5) && start === 49) continue
        for (let i = 0; i < 4; i++) {
          const z = start + i * 2.5
          line(x, z, x + 5, z); line(x + 5, z, x + 5, z + 2.5); line(x, z + 2.5, x + 5, z + 2.5)
        }
      }
    }
    for (const x of [7.8, 28.5, 57, 77, 103.5]) for (let z = 16; z < 62; z += 8) line(x, z, x, z + 3, 'line', .15)
  }
  for (const pool of layout.pools || []) {
    const shape = new THREE.Shape(pool.polygon.map(([x, z]) => new THREE.Vector2(x - 80, -(z - 36))))
    const geo = new THREE.ShapeGeometry(shape).rotateX(-Math.PI / 2).translate(0, 4, 0)
    geo.deleteAttribute('uv')
    geometry(interior, 'water', geo)
    pool.polygon.forEach((p, i) => segment(interior, 'wall', p, pool.polygon[(i + 1) % pool.polygon.length], 4.5))
  }
  for (const [parent, finishes] of batches) for (const [kind, geometries] of finishes) {
    geometries.forEach(geo => geo.deleteAttribute('uv'))
    const merged = mergeGeometries(geometries, false)
    const mesh = new THREE.Mesh(merged, mats[kind]); mesh.name = `plan-${number}-${kind}`
    mesh.castShadow = mesh.receiveShadow = true
    mesh.userData.planRevision = layout.drawing; mesh.userData.floor = number
    parent.add(mesh); geometries.forEach(geo => geo.dispose())
  }
  cube.dispose()
  const used = new Set(); for (const parent of [structure, interior, fire]) parent.traverse(o => { if (o.material) used.add(o.material) })
  Object.values(mats).filter(mat => !used.has(mat)).forEach(mat => mat.dispose())
  return { structure, interior, fire, layout }
}

export function floorLandmarks(number) {
  const layout = floorLayout(number)
  const zones = layout.zones.map(zone => ({ id: zone.id, name: zone.name, position: [(zone.bounds[0] + zone.bounds[2]) / 2 - 80, .55, (zone.bounds[1] + zone.bounds[3]) / 2 - 36] }))
  if (number === -1) return [...zones, ...layout.rooms.map(room => ({ id: room.id, name: room.name, position: [(room.bounds[0] + room.bounds[2]) / 2 - 80, 3.5, (room.bounds[1] + room.bounds[3]) / 2 - 36] })),
    { id: 'tank-1', name: '消防水池（一）', position: [61, 4, -16] }, { id: 'tank-2', name: '消防水池（二）', position: [52, 4, -28] }]
  return [...zones, ...layout.rooms.filter(room => room.kind.startsWith('stair') || ['lift-j1', 'lift-s0-1', 'lift-s1-1', 'lift-d7', 'lift-a5', 'lift-a3', 'generator'].includes(room.id)).map(room => ({
    id: room.id, name: room.name, position: [(room.bounds[0] + room.bounds[2]) / 2 - 80, 3.6, (room.bounds[1] + room.bounds[3]) / 2 - 36],
  }))]
}
