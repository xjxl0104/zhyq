import * as THREE from 'three'
import { addReferenceWordmark } from './referenceWordmark.js'

const WORDMARK_VERSION = 'dipark-six-letter-v2'

function labelEntranceLetters(wordmark) {
  wordmark.children.forEach(mesh => {
    mesh.name = `entrance-reference-wordmark-${mesh.userData.wordmarkPart}`
    mesh.userData.referenceWordmark = 'entrance'
  })
}

function trianglesOnChineseSide(geometry) {
  const position = geometry.getAttribute('position')
  const index = geometry.index
  const count = index?.count ?? position.count
  const chinese = [], latin = []
  for (let offset = 0; offset < count; offset += 3) {
    const triangle = [0, 1, 2].map(corner => index ? index.getX(offset + corner) : offset + corner)
    const sides = triangle.map(vertex => position.getX(vertex) >= 0)
    if (sides.some(Boolean) && !sides.every(Boolean)) {
      throw new Error('Entrance lettering crosses its Chinese origin; cannot safely split the old PARK batch')
    }
    if (sides[0]) chinese.push(...triangle)
    else latin.push(...triangle)
  }
  if (!chinese.length || !latin.length) throw new Error('Expected separate PARK and Chinese geometry in the legacy entrance batch')
  return { chinese, latin }
}

// Compact the retained attributes, rather than merely changing the index: unused
// PARK vertices would otherwise remain in GLB bounds and future rebuilds. Copy
// raw values in original order to preserve the Chinese letter normals exactly.
function retainGeometry(source, indices) {
  const used = [...new Set(indices)].sort((a, b) => a - b)
  const remap = new Map(used.map((index, position) => [index, position]))
  const geometry = new THREE.BufferGeometry()
  geometry.name = source.name
  geometry.userData = structuredClone(source.userData)
  for (const [name, attribute] of Object.entries(source.attributes)) {
    const original = attribute.isInterleavedBufferAttribute ? attribute.data.array : attribute.array
    const stride = attribute.isInterleavedBufferAttribute ? attribute.data.stride : attribute.itemSize
    const offset = attribute.isInterleavedBufferAttribute ? attribute.offset : 0
    const array = new original.constructor(used.length * attribute.itemSize)
    used.forEach((sourceIndex, targetIndex) => {
      for (let component = 0; component < attribute.itemSize; component++) {
        array[targetIndex * attribute.itemSize + component] = original[sourceIndex * stride + offset + component]
      }
    })
    geometry.setAttribute(name, new THREE.BufferAttribute(array, attribute.itemSize, attribute.normalized))
  }
  geometry.setIndex(indices.map(index => remap.get(index)))
  geometry.computeBoundingBox()
  geometry.computeBoundingSphere()
  return geometry
}

function includeGeometry(bounds, entranceInverse, mesh, indices) {
  const transform = new THREE.Matrix4().multiplyMatrices(entranceInverse, mesh.matrixWorld)
  const position = mesh.geometry.getAttribute('position')
  const point = new THREE.Vector3()
  const vertices = indices ? new Set(indices) : Array.from({ length: position.count }, (_, index) => index)
  for (const index of vertices) bounds.expandByPoint(point.fromBufferAttribute(position, index).applyMatrix4(transform))
}

/**
 * Upgrade the preserved Blender entrance during GLB generation. This operates
 * entirely in entrance-local coordinates, so it can run before OR after the
 * rebuild script bakes an inherited site transform into the entrance matrix.
 * Caller supplies the shared facadeLogoTeal / facadeLogoBlue materials.
 */
export function upgradeReferenceEntrance(entrance, materials) {
  if (!materials.facadeLogoTeal || !materials.facadeLogoBlue) {
    throw new Error('Entrance wordmark requires the shared facadeLogoTeal and facadeLogoBlue materials')
  }
  const installed = []
  entrance.traverse(object => {
    if (object.userData.entranceWordmarkVersion) installed.push(object)
  })
  if (installed.length > 1) throw new Error('Entrance contains multiple reference wordmarks')
  if (installed.length === 1) {
    const wordmark = installed[0]
    // Rebuild the persisted, incorrect DIC+PARK sign at its saved height and
    // transform. Changing only the generator would leave the old GLB gate intact.
    if (wordmark.userData.entranceWordmarkVersion === 'reference-photo-v1') {
      const stem = wordmark.children.find(mesh => mesh.userData.wordmarkPart === 'stem')
      if (!stem?.geometry) throw new Error('Saved entrance DI stem is missing')
      stem.geometry.computeBoundingBox()
      const height = (stem.geometry.boundingBox.max.y - stem.geometry.boundingBox.min.y) / .92
      if (!Number.isFinite(height) || height <= 0) throw new Error('Saved entrance wordmark height is invalid')
      const replacement = new THREE.Group()
      addReferenceWordmark(replacement, materials, { x: 0, y: 0, z: 0, height, variant: 'roof' })
      wordmark.clear()
      wordmark.add(...replacement.children.slice())
      wordmark.userData.entranceWordmarkVersion = WORDMARK_VERSION
      wordmark.userData.referenceStyle = 'dipark-two-piece-di-and-light-park'
      labelEntranceLetters(wordmark)
    }
    // Each rebuild imports yesterday's sign into a freshly generated building.
    // Reuse today's facade materials instead of exporting duplicate same-name
    // finishes from the old GLB. Geometry and saved placement stay untouched.
    const monogramParts = new Set(['open-d', 'stem'])
    installed[0].traverse(object => {
      if (object.isMesh) object.material = monogramParts.has(object.userData.wordmarkPart)
        ? materials.facadeLogoTeal : materials.facadeLogoBlue
    })
    return installed[0]
  }

  const standaloneLatin = [], combinedChinese = []
  entrance.traverse(object => {
    if (!object.isMesh) return
    const text = object.userData.source_text
    if (/^Entrance[ _]DIPARK[ _](DI|PARK)$/.test(object.name) && ['DI', 'PARK'].includes(text)) standaloneLatin.push(object)
    if (/^Entrance[ _]Chinese[ _]park[ _]name$/.test(object.name) && text?.split(' / ').includes('PARK')) combinedChinese.push(object)
  })
  if (!standaloneLatin.some(mesh => mesh.userData.source_text === 'DI')) {
    throw new Error('Legacy entrance DI lettering is missing; refusing to guess the sign placement')
  }
  if (!combinedChinese.length && !standaloneLatin.some(mesh => mesh.userData.source_text === 'PARK')) {
    throw new Error('Legacy entrance PARK lettering is missing; refusing to alter the Chinese park name')
  }

  entrance.updateWorldMatrix(true, true)
  const entranceInverse = new THREE.Matrix4().copy(entrance.matrixWorld).invert()
  const bounds = new THREE.Box3()
  standaloneLatin.forEach(mesh => includeGeometry(bounds, entranceInverse, mesh))
  // Blender placed the Chinese origin at x = 0 and joined the earlier PARK
  // geometry into its negative X region. The actual source has a 0.652 m gap
  // here; no glyph crosses this plane. Never remove the combined mesh itself.
  const replacements = combinedChinese.map(mesh => {
    const split = trianglesOnChineseSide(mesh.geometry)
    includeGeometry(bounds, entranceInverse, mesh, split.latin)
    return { mesh, geometry: retainGeometry(mesh.geometry, split.chinese) }
  })
  // Preserve the height chosen for the original photo sign, while closing the
  // space formerly occupied by the erroneous C. The Chinese lettering stays put.
  const height = Math.min(bounds.max.y - bounds.min.y, (bounds.max.x - bounds.min.x) / 5.983)
  if (!Number.isFinite(height) || height <= 0) throw new Error('Legacy entrance lettering has invalid dimensions')
  const wordmark = new THREE.Group()
  wordmark.name = 'entrance-reference-wordmark'
  wordmark.position.set(bounds.min.x, bounds.min.y, (bounds.min.z + bounds.max.z) / 2 - .0175 * height)
  wordmark.userData = {
    entranceWordmarkVersion: WORDMARK_VERSION,
    source_text: 'DIPARK',
    referenceStyle: 'dipark-two-piece-di-and-light-park',
    placement: 'Original entrance Latin-sign footprint; Chinese park name retained',
  }
  addReferenceWordmark(wordmark, materials, { x: 0, y: 0, z: 0, height, variant: 'roof' })
  labelEntranceLetters(wordmark)

  // Mutation happens only after source recognition, safe splitting and new
  // geometry creation all succeed. Gate/guardhouse transforms are untouched.
  standaloneLatin.forEach(mesh => mesh.removeFromParent())
  replacements.forEach(({ mesh, geometry }) => {
    mesh.geometry = geometry
    mesh.userData.source_text = mesh.userData.source_text.split(' / ').filter(text => text !== 'PARK').join(' / ')
    mesh.userData.entranceLatinRemoved = WORDMARK_VERSION
  })
  entrance.add(wordmark)
  return wordmark
}
