// Traced from the uploaded JS-1-0301…0308 architectural drawings (2025.10).
// Plan coordinates are METRES: origin Q/7, +x towards A, +z towards axis 1.
// Raster anchors exclude the title block and detached detail drawings.
export const PLAN_BUILDING = Object.freeze({ projectId: 3, buildingId: 5, width: 160, depth: 72, sceneScale: .6, revision: '2025.10-trace-1' })
export const COLUMN_X = [0, 12.5, 25, 37.5, 49.5, 61.5, 73.5, 86, 86.28, 98.5, 110.5, 122.5, 135, 147.5, 160]
export const COLUMN_Z = [0, 12, 24, 36, 48, 60, 72]
// Measured on a 2048px-wide rendition; source images are 7168 × 4108.
const standardAnchor = (top, bottom) => [181.5 / 2048, top / (4108 / 3.5), 1711.5 / 2048, bottom / (4108 / 3.5)]
export const PLAN_FLOORS = [
  { floor: 1, floorId: 13, fileId: 67, drawing: 'JS-1-0302', title: '停车库与分拣区', base: 0, height: 12, anchors: [250 / 1886, 457 / 1344, 1498 / 1886, 1018 / 1344] },
  { floor: 2, floorId: 14, fileId: 68, drawing: 'JS-1-0303', title: '储存区与货梯核心筒', base: 12, height: 9.5, anchors: standardAnchor(247, 936) },
  { floor: 3, floorId: 15, fileId: 69, drawing: 'JS-1-0304', title: '储存区与货梯核心筒', base: 21.5, height: 6.6, anchors: standardAnchor(236, 925) },
  { floor: 4, floorId: 16, fileId: 70, drawing: 'JS-1-0305', title: '储存区与货梯核心筒', base: 28.1, height: 6.6, anchors: standardAnchor(221, 910) },
  { floor: 5, floorId: 17, fileId: 71, drawing: 'JS-1-0306', title: '储存区与货梯核心筒', base: 34.7, height: 6.6, anchors: standardAnchor(221, 910) },
  { floor: 6, floorId: 18, fileId: 72, drawing: 'JS-1-0307', title: '储存区与货梯核心筒', base: 41.3, height: 6.6, anchors: standardAnchor(221, 910) },
  { floor: 7, floorId: 19, fileId: 73, drawing: 'JS-1-0308', title: '储存区与上人屋面', base: 47.9, height: 6.6, anchors: standardAnchor(228, 917) },
  { floor: -1, floorId: 20, fileId: 66, drawing: 'JS-1-0301', title: '消防水池与泵房', base: -5.6, height: 5.6, anchors: [655 / 1875, 340 / 1344, 1022 / 1875, 913 / 1344], rotated: true },
]
export const planFloor = number => PLAN_FLOORS.find(item => item.floor === Number(number))

/** Never guess for another building, an unknown drawing version or a title-block mark. */
export function planPointToLocal({ projectId, buildingId, floorId, floorPlanFileId, planX, planY }) {
  if (Number(projectId) !== PLAN_BUILDING.projectId || Number(buildingId) !== PLAN_BUILDING.buildingId) return null
  const floor = PLAN_FLOORS.find(item => item.floorId === Number(floorId) && item.fileId === Number(floorPlanFileId))
  if (!floor || planX == null || planY == null || planX === '' || planY === '') return null
  const x = Number(planX), y = Number(planY), [left, top, right, bottom] = floor.anchors
  if (![x, y].every(Number.isFinite) || x < left || x > right || y < top || y > bottom) return null
  const u = (x - left) / (right - left), v = (y - top) / (bottom - top)
  return { floor: floor.floor, position: floor.rotated ? [122.5 + v * 37.5 - 80, .45, 24 - u * 24 - 36] : [u * 160 - 80, .45, v * 72 - 36] }
}

// Rectangles [left, top, right, bottom] in plan metres. Openings are real gaps,
// not painted doors on continuous walls. Door/head heights await an elevation survey.
const room = (id, name, kind, bounds, door = 'bottom', reserved = false) => ({ id, name, kind, bounds, door, reserved })
const sharedRooms = () => [
  room('stair-q7', '楼梯 · Q/7', 'stair-x', [.8, .3, 7.2, 3.3]),
  room('lobby-q7', '楼梯前室', 'lobby', [7.2, .3, 10.3, 3.3]),
  room('shaft-q7', '水电井', 'shaft', [10.3, .3, 12.5, 3.3]),
  room('shaft-l7', '电井', 'shaft', [46.5, .3, 49.5, 3.4]),
  room('stair-l7', '楼梯 · L/7', 'stair-x', [49.5, .3, 55.3, 3.4]),
  room('lobby-l7', '楼梯前室', 'lobby', [55.3, .3, 58.9, 3.4]),
  room('shaft-k7', '水电井', 'shaft', [58.9, .3, 62.7, 3.4]),
  room('stair-a7', '楼梯 · A/7', 'stair-z', [157.1, .3, 159.7, 8.6], 'left'),
  room('lift-a7-1', '客梯', 'passenger', [154.5, .3, 157.1, 3.1], 'left'),
  room('lift-a7-2', '无障碍兼消防电梯', 'passenger', [154.5, 3.1, 157.1, 6.1], 'left'),
  room('lobby-a7', '合用前室', 'lobby', [151.2, .3, 154.5, 8.6], 'left'),
  room('lobby-a7-south', '前室通道', 'lobby', [154.5, 6.1, 157.1, 8.6], 'bottom'),
  room('shaft-d7', '电井', 'shaft', [122.5, .3, 125, 4.8]),
  room('lift-d7', '5T货梯', 'freight', [125, .3, 129, 4.8]),
  room('lift-c7-reserved', '5T货梯（后装）', 'freight', [129, .3, 134, 4.8], 'bottom', true),
  room('shaft-c7', '水井', 'shaft', [134, .3, 135.4, 4.8]),
  room('shaft-a5', '设备井', 'shaft', [155, 23.5, 159.7, 28]),
  room('lift-a5', '5T货梯', 'freight', [155, 28, 159.7, 32.5], 'left'),
  room('lift-a4-reserved', '5T货梯（后装）', 'freight', [155, 32.5, 159.7, 36.8], 'left', true),
  room('lift-a3-reserved', '5T货梯（后装）', 'freight', [155, 47.6, 159.7, 51.9], 'left', true),
  room('lift-a3', '5T货梯', 'freight', [155, 51.9, 159.7, 56.4], 'left'),
  room('shaft-a2', '设备井', 'shaft', [155, 56.4, 159.7, 60.8]),
  ...[24.5, 121].flatMap((x, i) => [
    room(`lift-s${i}-1`, '5T货梯', 'freight', [x, 67.2, x + 4.5, 71.7], 'top'),
    room(`lift-s${i}-2`, '5T货梯', 'freight', [x + 4.5, 67.2, x + 9, 71.7], 'top'),
    room(`lift-s${i}-reserved`, '5T货梯（后装）', 'freight', [x + 9, 67.2, x + 14.7, 71.7], 'top', true),
  ]),
  room('lift-j1', '8T货梯', 'freight', [70.5, 66.2, 76.5, 71.7], 'top'),
  room('lift-h1', '5T货梯', 'freight', [76.5, 67.2, 81, 71.7], 'top'),
  room('lift-h1-reserved', '5T货梯（后装）', 'freight', [81, 67.2, 86, 71.7], 'top', true),
  ...[49.5, 98.5].flatMap((x, i) => [
    room(`passenger-s${i}`, '客梯', 'passenger', [x, 64.8, x + 3.5, 67.6], 'bottom'),
    room(`lobby-s${i}`, '合用前室', 'lobby', [x, 67.6, x + 3.5, 71.7], 'left'),
    room(`stair-s${i}`, '楼梯 · 1轴', 'stair-z', [x + 3.5, 64.8, x + 6.7, 71.7], 'bottom'),
    room(`vestibule-s${i}`, '楼梯前室', 'lobby', [x + 6.7, 64.8, x + 9.5, 71.7], 'right'),
  ]),
]
export function floorLayout(number) {
  const floor = planFloor(number)
  if (!floor) throw new Error('No traced drawing for floor ' + number)
  if (number === -1) return basementLayout(floor)
  const rooms = sharedRooms()
  const zones = number === 1 ? [
    { id: 'parking-3', name: '停车库三', bounds: [.5, 5.5, 45, 64.5], kind: 'parking' },
    { id: 'parking-2', name: '停车库二', bounds: [46, 5.5, 97.5, 64.5], kind: 'parking' },
    { id: 'sorting', name: '分拣区', bounds: [99.5, 25, 154.5, 64.5], kind: 'storage' },
  ] : [
    { id: 'storage-3', name: '储存区三', bounds: [number === 7 ? 38 : .5, 4, 55.5, 64.5], kind: 'storage' },
    { id: 'storage-2', name: '储存区二', bounds: [56, 4, 105, 64.5], kind: 'storage' },
    { id: 'storage-1', name: '储存区一', bounds: [105.5, 9, 154.5, 64.5], kind: 'storage' },
  ]
  const partitions = number === 1
    ? [{ x: 45.1, from: 3.4, to: 64.5, gaps: [[6, 8], [57, 59]] }]
    : [55.5, 105.3].map(x => ({ x, from: 3.4, to: 64.8, gaps: [[4.5, 6.2], [58, 59.7]] }))
  if (number === 1) {
    rooms.push(room('generator', '发电机房', 'utility', [98.5, .4, 104.5, 12], 'left'))
    rooms.push(room('electrical-n1', '配电用房', 'utility', [107.4, .4, 114.2, 6], 'left'))
    rooms.push(room('electrical-n2', '配电用房', 'utility', [107.4, 6, 114.2, 12], 'left'))
    rooms.push(room('electrical-n3', '设备用房', 'utility', [114.2, .4, 118.5, 6], 'right'))
    rooms.push(room('electrical-n4', '设备用房', 'utility', [114.2, 6, 118.5, 12], 'right'))
  }
  if (number === 7) zones.push({ id: 'accessible-roof', name: '上人屋面', bounds: [3, 12, 37.5, 60], kind: 'terrace' })
  return { ...floor, rooms, zones, partitions, footprint: [0, 0, 160, 72] }
}
function basementLayout(floor) {
  // B1 drawing is rotated 90° relative to the upper floor sheets (axes 5→7 / D→A).
  const convert = ([u1, v1, u2, v2]) => [122.5 + v1, 24 - u2, 122.5 + v2, 24 - u1]
  const rooms = [
    room('fire-pump', '消防水泵房', 'utility', convert([8, 23.3, 14, 33.3]), 'right'),
    room('domestic-pump', '生活水泵房', 'utility', convert([15, 28.2, 23.7, 32.2]), 'bottom'),
    room('exhaust', '电梯基坑排水', 'utility', convert([18.2, 32.2, 23.7, 34.7]), 'right'),
    room('basement-stair', '楼梯 · A/7', 'stair-z', [157, .3, 159.7, 5.5], 'left'),
  ]
  const pools = [
    { id: 'tank-1', name: '消防水池（一）', polygon: [[0, 0], [12, 0], [12, 7], [8, 7], [8, 33.3], [12, 33.3], [12, 37.2], [0, 37.2]] },
    { id: 'tank-2', name: '消防水池（二）', polygon: [[12, 0], [24, 0], [24, 23.3], [8, 23.3], [8, 7], [12, 7]] },
  ].map(pool => ({ ...pool, polygon: pool.polygon.map(([u, v]) => [122.5 + v, 24 - u]) }))
  return { ...floor, rooms, pools, zones: [], partitions: [], footprint: [122.5, 0, 160, 24] }
}
