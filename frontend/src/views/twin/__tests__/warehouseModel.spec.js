import { describe, expect, it } from 'vitest'
import { createWarehouse } from '../warehouseModel'
import { MODEL, floorBase, floorHeight } from '../twinData'
import { bindWarehouse } from '../warehouseController'

describe('warehouse scene model', () => {
  it('reports geometry changes only until the floor animation has settled', () => {
    const model = createWarehouse()
    model.update(1)
    expect(model.update(1)).toBe(false)
    model.setState({ mode: 'exploded', floor: 3 })
    expect(model.update(1 / 60)).toBe(true)
    for (let frame = 0; frame < 180; frame++) model.update(1 / 60)
    expect(model.update(1 / 60)).toBe(false)
    model.setState({ mode: 'interior', floor: 5 })
    expect(model.update(1 / 60)).toBe(true)
    model.dispose()
  })
  it('restores floor interactions on a separately loaded model hierarchy', () => {
    const generated = createWarehouse()
    const importedRoot = generated.root.clone(true)
    const runtimeObjects = []
    importedRoot.traverse(object => { if (object.userData.runtimeOnly) runtimeObjects.push(object) })
    runtimeObjects.forEach(object => object.removeFromParent())
    const imported = bindWarehouse(importedRoot)
    imported.setState({ mode: 'interior', floor: 5 })
    imported.update(2)
    expect(imported.floors.filter(item => item.group.visible).map(item => item.group.userData.floor)).toEqual([5])
    expect(imported.floors[4].fire.visible).toBe(true)
    imported.setState({ mode: 'exploded', floor: 5 })
    imported.update(2)
    expect(imported.floors[4].group.position.y).toBeCloseTo(floorBase(5) + 16, 2)
    generated.dispose()
    imported.dispose()
  })
  it('exposes the selected floor interior and hides the roof and other floors', () => {
    const model = createWarehouse()
    model.setState({ mode: 'interior', floor: 5 })
    model.update(1)
    expect(model.floors.filter(item => item.group.visible).map(item => item.group.userData.floor)).toEqual([5])
    expect(model.floors[4].shell.visible).toBe(false)
    expect(model.floors[4].interior.visible).toBe(true)
    expect(model.floors[4].fire.visible).toBe(true)
    model.setState({ mode: 'exterior', floor: null })
    model.update(1)
    expect(model.floors.every(item => item.group.visible && item.shell.visible && !item.interior.visible)).toBe(true)
    model.dispose()
  })
  it('keeps expanded floors separate and batches the thousands of façade details', () => {
    const model = createWarehouse()
    model.setState({ mode: 'exploded', floor: 3 })
    model.update(1)
    for (let index = 1; index < MODEL.floors; index++) {
      expect(model.floors[index].group.position.y - model.floors[index - 1].group.position.y).toBeGreaterThan(floorHeight(index) + 3)
    }
    let visibleDraws = 0
    model.root.traverseVisible(object => { if (object.isMesh) visibleDraws++ })
    expect(visibleDraws).toBeLessThan(130)
    model.dispose()
  })
})
