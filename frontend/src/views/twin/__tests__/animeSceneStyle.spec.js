// @vitest-environment node
import { describe, expect, it } from 'vitest'
import * as THREE from 'three'
import { createAnimeSceneStyle } from '../animeSceneStyle'
import { installFacadeDetails } from '../facadeDetails'

function compile(material) {
  const shader = {
    vertexShader: THREE.ShaderLib.toon.vertexShader,
    fragmentShader: THREE.ShaderLib.toon.fragmentShader,
    uniforms: THREE.UniformsUtils.clone(THREE.ShaderLib.toon.uniforms),
  }
  material.onBeforeCompile(shader)
  return shader
}

describe('anime scene material adapter', () => {
  it('preserves shared and array materials, instance transforms, geometry and picking metadata', () => {
    const scene = new THREE.Scene(), geometry = new THREE.BoxGeometry()
    const shared = new THREE.MeshStandardMaterial({ name: 'unknownFinish', color: '#73889a', vertexColors: true })
    const basic = new THREE.MeshBasicMaterial({ color: '#31b499', transparent: true, opacity: .19, depthWrite: false })
    const originals = [shared, basic, new THREE.MeshPhysicalMaterial(), new THREE.MeshPhongMaterial(), new THREE.MeshLambertMaterial()]
    const mesh = new THREE.Mesh(geometry, originals)
    mesh.userData = { floor: 3, twinRole: 'shell' }
    const instances = new THREE.InstancedMesh(geometry, shared, 2)
    instances.setMatrixAt(1, new THREE.Matrix4().makeTranslation(11, 2, 9))
    instances.setColorAt(1, new THREE.Color('#97b17c'))
    scene.add(mesh, instances)
    const matrix = instances.instanceMatrix, colors = instances.instanceColor, metadata = mesh.userData
    const style = createAnimeSceneStyle(scene)
    expect(mesh.material[0]).toBe(instances.material)
    expect(mesh.material[0]).not.toBe(shared)
    expect(mesh.material[1]).toBe(basic)
    for (const index of [0, 2, 3, 4]) expect(mesh.material[index].isMeshToonMaterial).toBe(true)
    expect(mesh.material[0].color.equals(shared.color)).toBe(true)
    expect(mesh.material[0].vertexColors).toBe(true)
    expect(mesh.geometry).toBe(geometry)
    expect(mesh.userData).toBe(metadata)
    expect(instances.instanceMatrix).toBe(matrix)
    expect(instances.instanceColor).toBe(colors)
    expect(style.stats.convertedMaterials).toBe(4)
    style.dispose()
    expect(mesh.material).toBe(originals)
    expect(instances.material).toBe(shared)
    geometry.dispose(); originals.forEach(material => material.dispose())
  })

  it('keeps filtered glass detail and an existing shader hook exactly once on the real toon shader', () => {
    const scene = new THREE.Scene(), geometry = new THREE.BoxGeometry()
    const source = new THREE.MeshStandardMaterial({ name: 'facadeGlazing', color: '#d9e9e7', transparent: true, opacity: .24, depthWrite: false, side: THREE.DoubleSide })
    source.userData = { surfaceRole: 'architectural-glass', facadeDetail: { kind: 'glass-grid', pitch: [1.15, 1.6], width: [.055, .065], color: '#59666a' } }
    let previousCalls = 0
    source.onBeforeCompile = shader => { previousCalls++; shader.uniforms.previousValue = { value: 7 } }
    installFacadeDetails(source)
    const sourceKey = source.customProgramCacheKey()
    const mesh = new THREE.Mesh(geometry, source)
    scene.add(mesh)
    const style = createAnimeSceneStyle(scene)
    const material = mesh.material, shader = compile(material)
    expect(previousCalls).toBe(1)
    expect(shader.uniforms.previousValue.value).toBe(7)
    expect(shader.vertexShader.match(/varying vec2 vFacadeSurface;/g)).toHaveLength(1)
    expect(shader.fragmentShader.match(/varying vec2 vFacadeSurface;/g)).toHaveLength(1)
    expect(shader.fragmentShader).toContain('fwidth(coordinate)')
    expect(shader.fragmentShader).toContain('diffuseColor.a = combinedAlpha')
    expect(material.customProgramCacheKey()).toContain(sourceKey)
    expect(material.userData).toEqual(source.userData)
    expect(material.userData).not.toBe(source.userData)
    expect(material.transparent).toBe(true)
    expect(material.opacity).toBe(.24)
    expect(material.depthWrite).toBe(false)
    expect(material.side).toBe(THREE.DoubleSide)
    expect(Math.min(material.color.r, material.color.g, material.color.b)).toBeGreaterThan(.6)
    compile(material)
    expect(previousCalls).toBe(2)
    style.dispose(); geometry.dispose(); source.dispose()
  })

  it('retains grain, leaf cutouts, texture references and visibility while leaving overlays untouched', () => {
    const scene = new THREE.Scene(), geometry = new THREE.PlaneGeometry(), map = new THREE.Texture(), bump = new THREE.Texture()
    const source = new THREE.MeshStandardMaterial({ name: 'contextLeaf', map, bumpMap: bump, alphaTest: .2, side: THREE.DoubleSide, emissive: '#203040', emissiveIntensity: .15 })
    source.visible = false
    source.onBeforeCompile = shader => { shader.fragmentShader = shader.fragmentShader.replace('#include <color_fragment>', '#include <color_fragment>\nfloat testGrain = 0.5;') }
    source.customProgramCacheKey = () => 'park-grain-3-0.11'
    const basic = new THREE.MeshBasicMaterial(), shaderMaterial = new THREE.ShaderMaterial(), lineMaterial = new THREE.LineBasicMaterial()
    const mesh = new THREE.Mesh(geometry, source), overlay = new THREE.Mesh(geometry, basic), sky = new THREE.Mesh(geometry, shaderMaterial), line = new THREE.LineSegments(geometry, lineMaterial)
    scene.add(mesh, overlay, sky, line)
    const style = createAnimeSceneStyle(scene)
    expect(mesh.material.map).toBe(map)
    expect(mesh.material.bumpMap).toBe(bump)
    expect(mesh.material.alphaTest).toBe(.2)
    expect(mesh.material.visible).toBe(false)
    expect(mesh.material.emissive.equals(source.emissive)).toBe(true)
    expect(mesh.material.emissive).not.toBe(source.emissive)
    expect(mesh.material.emissiveIntensity).toBe(.15)
    expect(compile(mesh.material).fragmentShader.match(/float testGrain =/g)).toHaveLength(1)
    expect(mesh.material.customProgramCacheKey()).toContain('park-grain-3-0.11')
    expect(overlay.material).toBe(basic)
    expect(sky.material).toBe(shaderMaterial)
    expect(line.material).toBe(lineMaterial)
    style.dispose(); geometry.dispose(); map.dispose(); bump.dispose()
    for (const material of [source, basic, shaderMaterial, lineMaterial]) material.dispose()
  })

  it('keeps brand and fire colors while using one shared, nearest-filtered light ramp', () => {
    const scene = new THREE.Scene(), geometry = new THREE.BoxGeometry()
    const originals = [
      new THREE.MeshStandardMaterial({ name: 'facadeLogoTeal', color: '#409995' }),
      new THREE.MeshStandardMaterial({ name: 'facadeLogoBlue', color: '#277da7' }),
      new THREE.MeshStandardMaterial({ name: 'red', color: '#d83129' }),
      new THREE.MeshStandardMaterial({ name: 'facadeIvory', color: '#edeee8' }),
      new THREE.MeshStandardMaterial({ name: 'facadeTeal', color: '#419e9c' }),
    ]
    const mesh = new THREE.Mesh(geometry, originals)
    scene.add(mesh)
    const style = createAnimeSceneStyle(scene)
    for (const index of [0, 1, 2]) expect(mesh.material[index].color.equals(originals[index].color)).toBe(true)
    expect(mesh.material[3].color.r).toBeGreaterThan(.75)
    expect(mesh.material[4].color.g).toBeGreaterThan(mesh.material[4].color.r)
    const ramp = mesh.material[0].gradientMap
    expect(new Set(mesh.material.map(material => material.gradientMap)).size).toBe(1)
    expect(ramp.image.width).toBeGreaterThanOrEqual(3)
    expect(ramp.image.width).toBeLessThanOrEqual(4)
    expect(ramp.minFilter).toBe(THREE.NearestFilter)
    expect(ramp.magFilter).toBe(THREE.NearestFilter)
    expect(ramp.colorSpace).toBe(THREE.NoColorSpace)
    style.dispose(); geometry.dispose(); originals.forEach(material => material.dispose())
  })

  it('exports canonical materials on a snapshot without touching the live animated scene', () => {
    const scene = new THREE.Scene(), geometry = new THREE.BoxGeometry()
    const original = new THREE.MeshStandardMaterial({ name: 'facadeIvory' }), overlayMaterial = new THREE.MeshBasicMaterial()
    const mesh = new THREE.Mesh(geometry, [original, overlayMaterial])
    mesh.name = 'floor-three'; mesh.position.y = 31; mesh.userData.floor = 3
    scene.add(mesh)
    const style = createAnimeSceneStyle(scene), liveMaterials = mesh.material
    const snapshot = scene.clone(true)
    style.prepareExport(snapshot)
    const frozen = snapshot.getObjectByName('floor-three')
    expect(frozen.material).toEqual([original, overlayMaterial])
    expect(mesh.material).toBe(liveMaterials)
    expect(mesh.material[0].isMeshToonMaterial).toBe(true)
    expect(frozen.position.y).toBe(31)
    expect(frozen.userData.floor).toBe(3)
    style.dispose(); geometry.dispose(); original.dispose(); overlayMaterial.dispose()
  })

  it('restores original assignments and disposes each owned resource once without disposing borrowed data', () => {
    const scene = new THREE.Scene(), geometry = new THREE.BoxGeometry(), map = new THREE.Texture()
    const original = new THREE.MeshStandardMaterial({ map }), other = new THREE.MeshLambertMaterial()
    const materials = [original, other], mesh = new THREE.Mesh(geometry, materials), sibling = new THREE.Mesh(geometry, original)
    scene.add(mesh, sibling)
    let borrowedDisposals = 0
    for (const resource of [geometry, map, original, other]) resource.addEventListener('dispose', () => borrowedDisposals++)
    const style = createAnimeSceneStyle(scene)
    const generated = [...new Set([...mesh.material, mesh.material[0].gradientMap])]
    const disposals = generated.map(resource => {
      const record = { count: 0 }
      resource.addEventListener('dispose', () => record.count++)
      return record
    })
    style.dispose(); style.dispose()
    expect(disposals.map(record => record.count)).toEqual([1, 1, 1])
    expect(borrowedDisposals).toBe(0)
    expect(mesh.material).toBe(materials)
    expect(sibling.material).toBe(original)
    for (const resource of [geometry, map, original, other]) resource.dispose()
  })

  it('attaches sparse linework only to architectural shells, following their floor and visibility', () => {
    const scene = new THREE.Scene(), building = new THREE.Group(), floor = new THREE.Group(), shell = new THREE.Group(), interior = new THREE.Group()
    building.userData.twinRole = 'building'; floor.userData.twinRole = 'floor'
    shell.userData.twinRole = 'shell'; interior.userData.twinRole = 'interior'
    scene.add(building); building.add(floor); floor.add(shell, interior)
    const geometry = new THREE.BoxGeometry(4, 2, .3), tinyGeometry = new THREE.BoxGeometry(.2, .2, .2)
    const ivory = new THREE.MeshStandardMaterial({ name: 'facadeIvory' }), glass = new THREE.MeshStandardMaterial({ name: 'facadeGlazing', transparent: true })
    const wall = new THREE.Mesh(geometry, ivory), pane = new THREE.Mesh(geometry, glass), tiny = new THREE.Mesh(tinyGeometry, ivory), inside = new THREE.Mesh(geometry, ivory)
    wall.name = 'outlined-wall'
    shell.add(wall, pane, tiny); interior.add(inside)
    const style = createAnimeSceneStyle(scene)
    expect(style.stats.outlinedMeshes).toBe(1)
    expect(style.stats.outlineSegments).toBe(8)
    const line = wall.children.find(object => object.userData.animeOutline)
    expect(line.isLineSegments).toBe(true)
    expect(line.userData.runtimeOnly).toBe(true)
    expect(line.material.depthWrite).toBe(false)
    expect(line.material.opacity).toBeLessThan(.3)
    expect(wall.material.polygonOffset).toBe(true)
    expect(ivory.polygonOffset).toBe(false)
    expect(pane.children).toHaveLength(0)
    expect(tiny.children).toHaveLength(0)
    expect(inside.children).toHaveLength(0)
    floor.position.y = 18; scene.updateMatrixWorld(true)
    expect(line.getWorldPosition(new THREE.Vector3()).y).toBe(18)
    const positions = line.geometry.attributes.position
    for (let i = 0; i < positions.count; i += 2) {
      const a = new THREE.Vector3().fromBufferAttribute(positions, i), b = new THREE.Vector3().fromBufferAttribute(positions, i + 1)
      expect(a.distanceTo(b)).toBeGreaterThanOrEqual(1.5)
    }
    const originalRuntime = new THREE.LineSegments(geometry, new THREE.LineBasicMaterial())
    originalRuntime.userData.runtimeOnly = true
    wall.add(originalRuntime)
    const snapshot = scene.clone(true)
    style.prepareExport(snapshot)
    expect(snapshot.getObjectByName('outlined-wall').children).toHaveLength(1)
    expect(snapshot.getObjectByName('outlined-wall').children[0].userData.animeOutline).toBeUndefined()
    expect(wall.children).toContain(line)
    let geometryDisposals = 0, materialDisposals = 0
    line.geometry.addEventListener('dispose', () => geometryDisposals++)
    line.material.addEventListener('dispose', () => materialDisposals++)
    style.dispose(); style.dispose()
    expect(wall.children).toEqual([originalRuntime])
    expect(wall.material).toBe(ivory)
    expect(geometryDisposals).toBe(1)
    expect(materialDisposals).toBe(1)
    geometry.dispose(); tinyGeometry.dispose(); ivory.dispose(); glass.dispose(); originalRuntime.material.dispose()
  })

  it('bounds architectural outline draws and allows disabling them', () => {
    const scene = new THREE.Scene(), building = new THREE.Group(), shell = new THREE.Group()
    building.userData.twinRole = 'building'; shell.userData.twinRole = 'shell'
    scene.add(building); building.add(shell)
    const geometry = new THREE.BoxGeometry(4, 4, 4), material = new THREE.MeshStandardMaterial({ name: 'facadeIvory' })
    for (let i = 0; i < 30; i++) shell.add(new THREE.Mesh(geometry, material))
    const style = createAnimeSceneStyle(scene)
    expect(style.stats.outlinedMeshes).toBeLessThanOrEqual(25)
    expect(style.stats.outlineSegments).toBeLessThanOrEqual(6000)
    style.dispose()
    const withoutLines = createAnimeSceneStyle(scene, { outlines: false })
    expect(withoutLines.stats.outlinedMeshes).toBe(0)
    withoutLines.dispose(); geometry.dispose(); material.dispose()
  })
})
