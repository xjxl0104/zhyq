import * as THREE from 'three'

// Only architectural/context finishes are recolored. Brand marks, fire systems,
// zone colors, vehicles and runtime selection materials keep their source color.
const PALETTE = {
  facadeIvory: '#faf8ee', facadeSignIvory: '#edf0e6', facadeRib: '#d7ded2',
  facadeTeal: '#77bcb1', facadeGlazing: '#ddeeed', facadeCornerGlazing: '#ddeeed',
  facadeFrame: '#889b9b', facadeRecess: '#8a9c9b', facadeShutter: '#a1afaa',
  white: '#f4f2e7', concrete: '#d0d1c2', edge: '#c5d0c5',
  road: '#a6afa8', paving: '#dedccb', grass: '#aec59a',
  bark: '#9b917a', tree: '#94b486', treeLight: '#b2c99a',
  glass: '#c9e1e0', glassLight: '#dcecea', glassPale: '#e2eeeb',
  contextGround: '#d6deca', contextLawn: '#b6cda1', contextGrove: '#99b68b',
  contextDistantTerrain: '#bfd3ba', contextBuilding: '#eee8d6', contextRoof: '#afbcb4',
  siteRoadContext: '#a6afa8', sitePavementContext: '#dedccb', sitePavementWalk: '#e8e1cb',
  contextCurb: '#dce4d8', contextLaneLine: '#f6f3df', contextLaneCentre: '#e3ce9f',
  windowContext: '#c9e1e0', contextBark: '#dfdac7', contextLeaf: '#d5dfb9',
}

const TOON_PROPERTIES = [
  'emissiveIntensity', 'bumpScale', 'normalMapType', 'displacementScale', 'displacementBias',
  'lightMapIntensity', 'aoMapIntensity', 'wireframe', 'wireframeLinewidth',
  'wireframeLinecap', 'wireframeLinejoin', 'fog', 'flatShading',
]

/**
 * Borrow the existing scene; change its finish without changing the scene graph.
 * Install after landscape generation and before weather captures its materials.
 * Dispose after weather and before landscape/model resource owners.
 */
export function createAnimeSceneStyle(scene, { outlines = true } = {}) {
  const gradient = new THREE.DataTexture(new Uint8Array([80, 145, 204, 255]), 4, 1, THREE.RedFormat)
  gradient.name = 'anime-shared-light-ramp'
  gradient.minFilter = gradient.magFilter = THREE.NearestFilter
  gradient.generateMipmaps = false
  gradient.needsUpdate = true
  const converted = new Map(), originals = new Map(), assignments = [], linework = []
  let lineMaterial, outlineSegments = 0
  let disposed = false

  function convert(source) {
    if (!source || !(source.isMeshStandardMaterial || source.isMeshPhysicalMaterial || source.isMeshPhongMaterial || source.isMeshLambertMaterial)) return source
    if (converted.has(source)) return converted.get(source)
    const material = new THREE.MeshToonMaterial()
    // Cross-type MeshToonMaterial.copy() assumes Toon-only fields. Base copy
    // handles blending/depth/clipping/stencil/visibility and independent extras.
    THREE.Material.prototype.copy.call(material, source)
    material.color.copy(source.color)
    if (source.emissive) material.emissive.copy(source.emissive)
    if (source.normalScale) material.normalScale.copy(source.normalScale)
    for (const key of TOON_PROPERTIES) {
      if (source[key] !== undefined) material[key] = source[key]
    }
    // Texture ownership remains with the source/landscape. Unsupported PBR maps
    // stay available for consumers, but the Toon shader does not sample them.
    for (const [key, value] of Object.entries(source)) {
      if (value?.isTexture) material[key] = value
    }
    material.gradientMap = gradient
    const name = source.name.replace(/\.\d+$/, '')
    const color = source.userData.surfaceRole === 'architectural-glass' ? '#ddeeed' : PALETTE[name]
    if (color) material.color.set(color)

    // Material.clone/copy do not preserve shader callbacks. Reuse the already
    // installed facade/grain hook once, including its parameter-dependent key.
    // Installing facadeDetails again here would duplicate its varying/function.
    material.onBeforeCompile = source.onBeforeCompile
    material.customProgramCacheKey = () => 'anime-toon-v1:' + source.customProgramCacheKey()
    material.onBeforeRender = source.onBeforeRender
    converted.set(source, material)
    originals.set(material, source)
    return material
  }

  scene.traverse(object => {
    if (!object.isMesh || !object.material) return
    const original = object.material
    const replacement = Array.isArray(original) ? original.map(convert) : convert(original)
    const changed = Array.isArray(original) ? replacement.some((material, index) => material !== original[index]) : replacement !== original
    if (!changed) return
    assignments.push({ object, original })
    object.material = replacement
  })

  // A small amount of actual architectural linework follows the original floor
  // transforms. No extra full-scene render, glass edges, foliage or sign letters.
  if (outlines) {
    for (const { object } of assignments) {
      if (linework.length >= 25 || outlineSegments >= 6000) break
      if (object.isInstancedMesh) continue
      let ancestor = object, building = false, shell = false, roof = false
      while (ancestor) {
        const role = ancestor.userData.twinRole
        building ||= role === 'building'; shell ||= role === 'shell'; roof ||= role === 'roof'
        ancestor = ancestor.parent
      }
      if (!building || (!shell && !roof)) continue
      const materials = Array.isArray(object.material) ? object.material : [object.material]
      const allowed = shell ? ['facadeIvory', 'facadeSignIvory', 'facadeTeal'] : ['facadeIvory', 'white']
      if (!materials.every(material => !material.transparent && allowed.includes(material.name.replace(/\.\d+$/, '')))) continue
      const edges = new THREE.EdgesGeometry(object.geometry, 30), source = edges.attributes.position
      const positions = [], remaining = 6000 - outlineSegments
      for (let i = 0; i < source.count && positions.length / 6 < remaining; i += 2) {
        const dx = source.getX(i) - source.getX(i + 1), dy = source.getY(i) - source.getY(i + 1), dz = source.getZ(i) - source.getZ(i + 1)
        if (dx * dx + dy * dy + dz * dz < 1.5 * 1.5) continue
        positions.push(source.getX(i), source.getY(i), source.getZ(i), source.getX(i + 1), source.getY(i + 1), source.getZ(i + 1))
      }
      edges.dispose()
      if (!positions.length) continue
      if (!lineMaterial) lineMaterial = new THREE.LineBasicMaterial({ name: 'anime-architectural-ink', color: '#536d70', transparent: true, opacity: .22, depthWrite: false })
      const geometry = new THREE.BufferGeometry()
      geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
      const lines = new THREE.LineSegments(geometry, lineMaterial)
      lines.name = 'anime-outline-' + object.name
      lines.userData = { animeOutline: true, runtimeOnly: true }
      lines.raycast = () => {}
      // Push only the generated fill material slightly behind its linework;
      // canonical borrowed materials are untouched and restored on disposal.
      materials.forEach(material => { material.polygonOffset = true; material.polygonOffsetFactor = 1; material.polygonOffsetUnits = 1 })
      object.add(lines)
      linework.push(lines)
      outlineSegments += positions.length / 6
    }
  }

  return {
    stats: { convertedMaterials: converted.size, convertedMeshes: assignments.length, gradientSteps: 4, outlinedMeshes: linework.length, outlineSegments },
    prepareExport(snapshot) {
      // clone(true) freezes transforms but shares material references. Replace
      // only the snapshot's assignments; do not swap live materials mid-frame.
      const generatedLines = []
      snapshot.traverse(object => {
        if (object.userData.animeOutline) generatedLines.push(object)
        if (!object.material) return
        const canonical = material => originals.get(material) || material
        object.material = Array.isArray(object.material) ? object.material.map(canonical) : canonical(object.material)
      })
      generatedLines.forEach(object => object.removeFromParent())
      return snapshot
    },
    dispose() {
      if (disposed) return
      disposed = true
      linework.forEach(lines => { lines.removeFromParent(); lines.geometry.dispose() })
      lineMaterial?.dispose()
      linework.length = 0
      assignments.forEach(({ object, original }) => { object.material = original })
      converted.forEach(material => material.dispose())
      gradient.dispose()
      assignments.length = 0
      converted.clear(); originals.clear()
    },
  }
}
