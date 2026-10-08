import * as THREE from 'three'
import { MODEL, floorBase, floorHeight } from './twinData.js'
import { PLAN_BUILDING, PLAN_FLOORS } from './floorPlanData.js'
import { installFacadeDetails } from './facadeDetails.js'

// Stable extras survive Blender -> GLB. Mesh names are editable; roles identify systems.
export function bindWarehouse(root, extraGeometries = [], extraMaterials = []) {
  const roles = new Map()
  root.traverse(object => {
    const role = object.userData.twinRole
    if (role && ['building', 'site', 'roof'].includes(role)) roles.set(role, object)
    if (object.isMesh) {
      const materials = Array.isArray(object.material) ? object.material : [object.material]
      const glass = materials.every(material => material.userData.surfaceRole === 'architectural-glass')
      // GLTF preserves material extras and alpha blending, but not Three.js
      // depthWrite/shadow flags. Restore these on every load and model export.
      materials.forEach(material => {
        if (material.userData.surfaceRole === 'architectural-glass') material.depthWrite = false
        installFacadeDetails(material)
      })
      object.castShadow = !glass
      object.receiveShadow = !glass
    }
  })
  const building = roles.get('building'), site = roles.get('site'), roof = roles.get('roof')
  if (!building || !site || !roof) throw new Error('Warehouse asset is missing building, site or roof metadata')
  const floors = []
  for (const definition of PLAN_FLOORS) {
    const floor = definition.floor
    const group = building.children.find(object => object.userData.twinRole === 'floor' && object.userData.floor === floor)
    if (!group) throw new Error('Warehouse asset is missing floor ' + floor)
    const parts = Object.fromEntries(['shell', 'structure', 'interior', 'fire'].map(role => [role, group.children.find(object => object.userData.twinRole === role)]))
    if (Object.values(parts).some(part => !part)) throw new Error('Warehouse floor ' + floor + ' is missing a system group')
    // Fit the measured interior to the approved exterior only in overview/split views.
    // The selected interior uses a uniform scale, preserving its real plan proportions.
    for (const role of ['structure', 'interior', 'fire']) parts[role].scale.set(MODEL.width / PLAN_BUILDING.width, 1, MODEL.depth / PLAN_BUILDING.depth)
    group.traverse(object => { object.userData.floor = floor })
    floors.push({ group, ...parts })
  }
  const outline = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(MODEL.width + 1.5, 5.7, MODEL.depth + 1.5)), new THREE.LineBasicMaterial({ color: '#29bda3', transparent: true, opacity: .95 }))
  outline.userData.runtimeOnly = true
  building.add(outline)
  outline.visible = false
  const selection = new THREE.Mesh(new THREE.BoxGeometry(MODEL.width + 1.2, .12, MODEL.depth + 1.2), new THREE.MeshBasicMaterial({ color: '#31b499', transparent: true, opacity: .19, depthWrite: false }))
  selection.userData.runtimeOnly = true
  building.add(selection)
  selection.visible = false
  let current = { mode: 'exterior', floor: null, layer: 'all' }, stateChanged = true
  function setState(state) {
    current = { ...current, ...state }
    if (current.floor != null && !PLAN_FLOORS.some(item => item.floor === current.floor)) current.floor = null
    stateChanged = true
  }
  function update(delta) {
    let changed = stateChanged
    stateChanged = false
    const ease = 1 - Math.exp(-delta * 8)
    const selected = current.floor || 3
    for (let i = 0; i < floors.length; i++) {
      const item = floors[i], floor = item.group.userData.floor
      const inside = current.mode === 'interior'
      const expanded = current.mode === 'exploded'
      item.group.visible = floor === -1 ? inside && selected === -1 : !inside || floor === selected
      const targetY = inside ? .4 : floorBase(floor) + (expanded ? i * 4 : 0)
      const distance = targetY - item.group.position.y
      if (distance !== 0) {
        item.group.position.y = Math.abs(distance) < .001 ? targetY : item.group.position.y + distance * ease
        changed = true
      }
      for (const part of [item.structure, item.interior, item.fire]) {
        if (inside) part.scale.setScalar(PLAN_BUILDING.sceneScale)
        else part.scale.set(MODEL.width / PLAN_BUILDING.width, 1, MODEL.depth / PLAN_BUILDING.depth)
      }
      item.shell.visible = !inside
      item.interior.visible = inside || (expanded && floor === selected)
      item.structure.children.filter(child => child.userData.planRole === 'overhead').forEach(child => { child.visible = !inside })
      item.fire.visible = inside || (current.layer === 'fire' && expanded && floor === selected)
    }
    site.visible = current.mode !== 'interior'
    roof.visible = current.mode === 'exterior'
    outline.visible = current.floor != null && current.floor > 0 && current.mode !== 'interior'
    selection.visible = current.floor != null && current.floor > 0 && current.mode !== 'interior'
    if (current.floor) {
      const y = floors.find(item => item.group.userData.floor === current.floor).group.position.y
      outline.position.y = y + floorHeight(current.floor) / 2
      outline.scale.y = floorHeight(current.floor) / 5.7
      selection.position.y = y + .3
    }
    return changed
  }
  function localPointPosition(floor, position) {
    const item = floors.find(item => item.group.userData.floor === floor)
    if (!item) return null
    item.interior.updateWorldMatrix(true, false)
    return item.interior.localToWorld(new THREE.Vector3(...position))
  }
  function pointPosition(point) {
    if (point.localPosition) return localPointPosition(point.floor, point.localPosition)
    // Legacy illustrative exterior markers retain their scene-space anchors.
    // Never move these onto a selected floor and pretend that they are real devices.
    const vector = new THREE.Vector3(...point.position)
    const item = floors.find(item => item.group.userData.floor === point.floor)
    if (item) vector.y += item.group.position.y - floorBase(point.floor)
    return vector
  }
  function dispose() {
    const geometries = new Set(extraGeometries), mats = new Set(extraMaterials)
    root.traverse(object => { if (object.geometry) geometries.add(object.geometry); if (object.material) (Array.isArray(object.material) ? object.material : [object.material]).forEach(mat => mats.add(mat)) })
    geometries.forEach(geometry => geometry.dispose()); mats.forEach(mat => mat.dispose())
  }
  return { root, building, site, floors, setState, update, pointPosition, localPointPosition, dispose }
}
