import * as THREE from 'three'
import { MODEL, floorBase, floorHeight } from './twinData.js'

// Stable extras survive Blender -> GLB. Mesh names are editable; roles identify systems.
export function bindWarehouse(root, extraGeometries = [], extraMaterials = []) {
  const roles = new Map()
  root.traverse(object => {
    const role = object.userData.twinRole
    if (role && ['building', 'site', 'roof'].includes(role)) roles.set(role, object)
    if (object.isMesh) { object.castShadow = true; object.receiveShadow = true }
  })
  const building = roles.get('building'), site = roles.get('site'), roof = roles.get('roof')
  if (!building || !site || !roof) throw new Error('Warehouse asset is missing building, site or roof metadata')
  const floors = []
  for (let floor = 1; floor <= MODEL.floors; floor++) {
    const group = building.children.find(object => object.userData.twinRole === 'floor' && object.userData.floor === floor)
    if (!group) throw new Error('Warehouse asset is missing floor ' + floor)
    const parts = Object.fromEntries(['shell', 'structure', 'interior', 'fire'].map(role => [role, group.children.find(object => object.userData.twinRole === role)]))
    if (Object.values(parts).some(part => !part)) throw new Error('Warehouse floor ' + floor + ' is missing a system group')
    group.traverse(object => { object.userData.floor = floor })
    floors.push({ group, ...parts })
  }
  const outline = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(97.5, 5.7, 55.5)), new THREE.LineBasicMaterial({ color: '#29bda3', transparent: true, opacity: .95 }))
  outline.userData.runtimeOnly = true
  building.add(outline)
  outline.visible = false
  const selection = new THREE.Mesh(new THREE.BoxGeometry(97.2, .12, 55.2), new THREE.MeshBasicMaterial({ color: '#31b499', transparent: true, opacity: .19, depthWrite: false }))
  selection.userData.runtimeOnly = true
  building.add(selection)
  selection.visible = false
  let current = { mode: 'exterior', floor: null, layer: 'all' }, stateChanged = true
  function setState(state) { current = { ...current, ...state }; stateChanged = true }
  function update(delta) {
    let changed = stateChanged
    stateChanged = false
    const ease = 1 - Math.exp(-delta * 8)
    const selected = current.floor || 3
    for (let i = 0; i < floors.length; i++) {
      const item = floors[i], floor = i + 1
      const inside = current.mode === 'interior'
      const expanded = current.mode === 'exploded'
      item.group.visible = !inside || floor === selected
      const targetY = inside ? .4 : floorBase(floor) + (expanded ? i * 4 : 0)
      const distance = targetY - item.group.position.y
      if (distance !== 0) {
        item.group.position.y = Math.abs(distance) < .001 ? targetY : item.group.position.y + distance * ease
        changed = true
      }
      item.shell.visible = !inside
      item.interior.visible = inside || (expanded && floor === selected)
      item.fire.visible = inside || (current.layer === 'fire' && expanded && floor === selected)
    }
    roof.visible = current.mode === 'exterior'
    outline.visible = current.floor != null && current.mode !== 'interior'
    selection.visible = current.floor != null && current.mode !== 'interior'
    if (current.floor) {
      const y = floors[current.floor - 1].group.position.y
      outline.position.y = y + floorHeight(current.floor) / 2
      outline.scale.y = floorHeight(current.floor) / 5.7
      selection.position.y = y + .3
    }
    return changed
  }
  function pointPosition(point) {
    if (current.mode === 'interior') {
      const locations = { fire: [33, 3, 22], camera: [46, 4, 20], contract: [-25, 1, 10], property: [-30, 1, 24], park: [0, 1, -18], energy: [35, 2, -20] }
      return new THREE.Vector3(...locations[point.module])
    }
    const vector = new THREE.Vector3(...point.position)
    vector.y += floors[point.floor - 1].group.position.y - floorBase(point.floor)
    return vector
  }
  function dispose() {
    const geometries = new Set(extraGeometries), mats = new Set(extraMaterials)
    root.traverse(object => { if (object.geometry) geometries.add(object.geometry); if (object.material) (Array.isArray(object.material) ? object.material : [object.material]).forEach(mat => mats.add(mat)) })
    geometries.forEach(geometry => geometry.dispose()); mats.forEach(mat => mat.dispose())
  }
  return { root, building, site, floors, setState, update, pointPosition, dispose }
}
