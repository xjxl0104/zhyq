// @vitest-environment node
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { BoxGeometry, Group, Mesh, MeshStandardMaterial, ShaderLib, UniformsUtils } from 'three'
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { installFacadeDetails } from '../facadeDetails'
import { bindWarehouse } from '../warehouseController'
import { MODEL } from '../twinData'

const glazingDetail = () => ({ kind: 'glass-grid', pitch: [1.15, 1.6], width: [.055, .065], color: '#59666a' })
const ribDetail = () => ({ kind: 'corrugation', pitch: .29, contrast: .11 })
const glazing = () => {
  const material = new MeshStandardMaterial({ color: '#d3e6e8', transparent: true, opacity: .24, depthWrite: false })
  material.userData = { surfaceRole: 'architectural-glass', facadeDetail: glazingDetail() }
  return material
}
const compile = material => {
  const shader = {
    vertexShader: ShaderLib.standard.vertexShader,
    fragmentShader: ShaderLib.standard.fragmentShader,
    uniforms: UniformsUtils.clone(ShaderLib.standard.uniforms),
  }
  material.onBeforeCompile(shader)
  return shader
}

beforeAll(() => {
  vi.stubGlobal('FileReader', class {
    async readAsArrayBuffer(blob) {
      try {
        this.result = await blob.arrayBuffer()
        this.onloadend?.({ target: this })
      } catch (error) {
        this.error = error
        this.onerror?.({ target: this })
      }
    }
  })
})
afterAll(() => vi.unstubAllGlobals())

describe('screen-filtered facade details', () => {
  it('installs once without changing glass transparency or discarding another shader hook', () => {
    const material = glazing()
    const existingHook = vi.fn()
    material.onBeforeCompile = existingHook
    const tint = material.color.clone()
    installFacadeDetails(material)
    const hook = material.onBeforeCompile
    const version = material.version
    const key = material.customProgramCacheKey()
    installFacadeDetails(material)

    expect(material.onBeforeCompile).toBe(hook)
    expect(material.version).toBe(version)
    expect(material.customProgramCacheKey()).toBe(key)
    expect(material.transparent).toBe(true)
    expect(material.opacity).toBe(.24)
    expect(material.depthWrite).toBe(false)
    expect(material.color.equals(tint)).toBe(true)
    const shader = compile(material)
    expect(existingHook).toHaveBeenCalledOnce()
    expect(shader.vertexShader).not.toBe(ShaderLib.standard.vertexShader)
    expect(shader.fragmentShader).not.toBe(ShaderLib.standard.fragmentShader)
    // Screen derivatives and a smooth cutoff are needed when these details
    // become smaller than a pixel; alpha is still driven by glass coverage.
    expect(shader.fragmentShader).toMatch(/\bfwidth\s*\(/)
    expect(shader.fragmentShader).toMatch(/\bsmoothstep\s*\(/)
    expect(shader.fragmentShader).toMatch(/diffuseColor\.a/)
    material.dispose()
  })

  it('filters corrugation in screen space and leaves ordinary materials untouched', () => {
    const ordinary = new MeshStandardMaterial()
    const originalHook = ordinary.onBeforeCompile
    const originalVersion = ordinary.version
    installFacadeDetails(ordinary)
    expect(ordinary.onBeforeCompile).toBe(originalHook)
    expect(ordinary.version).toBe(originalVersion)

    const ribs = new MeshStandardMaterial()
    ribs.userData.facadeDetail = ribDetail()
    installFacadeDetails(ribs)
    const shader = compile(ribs)
    expect(shader.fragmentShader).not.toBe(ShaderLib.standard.fragmentShader)
    expect(shader.fragmentShader).toMatch(/\bfwidth\s*\(/)
    expect(shader.fragmentShader).toMatch(/\bsmoothstep\s*\(/)
    expect(ribs.transparent).toBe(false)
    expect(ribs.opacity).toBe(1)
    ordinary.dispose()
    ribs.dispose()
  })

  it('replaces a changed pattern without stacking duplicate shader declarations', () => {
    const material = glazing()
    const existingHook = vi.fn()
    material.onBeforeCompile = existingHook
    installFacadeDetails(material)
    const originalKey = material.customProgramCacheKey()
    material.userData.facadeDetail = { ...glazingDetail(), pitch: [1.8, 2.1] }
    installFacadeDetails(material)
    expect(material.customProgramCacheKey()).not.toBe(originalKey)
    const shader = compile(material)
    expect(existingHook).toHaveBeenCalledOnce()
    // Duplicate varying declarations make an otherwise successful GLB load
    // fail shader compilation after switching between facade configurations.
    for (const source of [shader.vertexShader, shader.fragmentShader]) {
      const declarations = source.match(/varying\s+vec2\s+vFacade\w*\s*;/g)
      expect(declarations).toHaveLength(1)
    }
    material.dispose()
  })

  it('uses distinct shader programs for different detail modes and appearance parameters', () => {
    const specs = [
      glazingDetail(),
      { ...glazingDetail(), pitch: [1.8, 1.6] },
      { ...glazingDetail(), width: [.08, .065] },
      { ...glazingDetail(), color: '#8b999f' },
      ribDetail(),
      { ...ribDetail(), pitch: .45 },
      { ...ribDetail(), contrast: .05 },
      { ...ribDetail(), axis: 'y' },
    ]
    const keys = specs.map(facadeDetail => {
      const material = new MeshStandardMaterial()
      material.userData.facadeDetail = facadeDetail
      installFacadeDetails(material)
      const key = material.customProgramCacheKey()
      material.dispose()
      return key
    })
    expect(new Set(keys).size).toBe(specs.length)
    const equivalent = new MeshStandardMaterial()
    equivalent.userData.facadeDetail = JSON.parse(JSON.stringify(glazingDetail()))
    installFacadeDetails(equivalent)
    expect(equivalent.customProgramCacheKey()).toBe(keys[0])
    equivalent.dispose()
  })

  it('restores both filtered surfaces from GLB material extras while preserving transparent glass', async () => {
    const root = new Group()
    const building = new Group(), site = new Group(), roof = new Group()
    building.userData.twinRole = 'building'
    site.userData.twinRole = 'site'
    roof.userData.twinRole = 'roof'
    root.add(building, site, roof)
    const glass = glazing()
    const ribs = new MeshStandardMaterial({ color: '#eceee8' })
    ribs.userData.facadeDetail = ribDetail()
    const geometry = new BoxGeometry(1, 1, .04)
    for (let floor = 1; floor <= MODEL.floors; floor++) {
      const group = new Group()
      group.userData = { twinRole: 'floor', floor }
      building.add(group)
      for (const twinRole of ['shell', 'structure', 'interior', 'fire']) {
        const part = new Group()
        part.userData.twinRole = twinRole
        group.add(part)
        if (twinRole === 'shell') {
          const glassMesh = new Mesh(geometry, glass), ribMesh = new Mesh(geometry, ribs)
          glassMesh.name = `test-glazing-${floor}`
          ribMesh.name = `test-ribs-${floor}`
          ribMesh.position.x = 2
          part.add(glassMesh, ribMesh)
        }
      }
    }
    installFacadeDetails(glass)
    installFacadeDetails(ribs)
    const sourceKey = glass.customProgramCacheKey()
    const binary = await new GLTFExporter().parseAsync(root, { binary: true, onlyVisible: false })
    const loaded = await new GLTFLoader().parseAsync(binary, '')
    const loadedGlassMesh = loaded.scene.getObjectByName('test-glazing-1')
    const loadedRibMesh = loaded.scene.getObjectByName('test-ribs-1')
    expect(loadedGlassMesh.material.userData).toEqual(glass.userData)
    expect(loadedRibMesh.material.userData).toEqual(ribs.userData)
    // JavaScript hooks are not serialized into glTF, so loading the material
    // alone cannot render the facade pattern. Binding must restore the hook.
    expect(compile(loadedGlassMesh.material).fragmentShader).toBe(ShaderLib.standard.fragmentShader)
    const model = bindWarehouse(loaded.scene)
    try {
      expect(loadedGlassMesh.material.customProgramCacheKey()).toBe(sourceKey)
      expect(loadedGlassMesh.material.transparent).toBe(true)
      expect(loadedGlassMesh.material.opacity).toBeCloseTo(.24, 6)
      expect(loadedGlassMesh.material.depthWrite).toBe(false)
      expect(loadedGlassMesh.castShadow).toBe(false)
      expect(loadedGlassMesh.receiveShadow).toBe(false)
      for (const component of ['r', 'g', 'b']) {
        expect(loadedGlassMesh.material.color[component]).toBeCloseTo(glass.color[component], 6)
      }
      for (const mesh of [loadedGlassMesh, loadedRibMesh]) {
        const shader = compile(mesh.material)
        expect(shader.vertexShader).not.toBe(ShaderLib.standard.vertexShader)
        expect(shader.fragmentShader).toMatch(/\bfwidth\s*\(/)
        expect(shader.fragmentShader).toMatch(/\bsmoothstep\s*\(/)
      }
    } finally {
      model.dispose()
      geometry.dispose()
      glass.dispose()
      ribs.dispose()
    }
  })
})
