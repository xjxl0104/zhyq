import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'

// Small, deterministic local textures: no remote assets on the model's loading path.
export function createLandscapeTextures(own) {
  const size = 256
  const noise = (x, y) => {
    const n = Math.sin(x * 127.1 + y * 311.7) * 43758.5453
    return n - Math.floor(n)
  }
  const texture = (data, color = false, repeat = true) => {
    const map = own(new THREE.DataTexture(data, size, size, THREE.RGBAFormat))
    if (color) map.colorSpace = THREE.SRGBColorSpace
    map.wrapS = map.wrapT = repeat ? THREE.RepeatWrapping : THREE.ClampToEdgeWrapping
    map.generateMipmaps = true; map.minFilter = THREE.LinearMipmapLinearFilter
    map.magFilter = THREE.LinearFilter; map.anisotropy = 4; map.needsUpdate = true
    return map
  }
  function woodSurface() {
    const colors = new Uint8Array(size * size * 4), heights = new Uint8Array(colors.length)
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const u = x / size, v = y / size
      const bend = Math.sin(v * Math.PI * 2) * .9 + Math.sin(v * Math.PI * 6 + u * 5) * .3
      const grain = Math.sin(u * Math.PI * 60 + bend * 3)
      const fine = noise(x, y)
      const groove = Math.pow(Math.abs(grain), 12)
      const value = .95 - groove * .37 + (fine - .5) * .12
      const rgb = [221, 169, 99]
      const offset = (y * size + x) * 4
      for (let c = 0; c < 3; c++) {
        colors[offset + c] = Math.round(rgb[c] * value)
        heights[offset + c] = Math.round((1 - groove) * 210)
      }
      colors[offset + 3] = heights[offset + 3] = 255
    }
    return { map: texture(colors, true), bumpMap: texture(heights) }
  }
  return { wood: woodSurface() }
}

export function createTreeGeometries(level = 0) {
  // Three overlapping solid crowns match the site's existing cartoon trees.
  // Keep their overall bounds at every LOD; only facets and hidden branches simplify.
  const { detail, sides, branches: branchCount } = [
    { detail: 1, sides: 8, branches: 2 },
    { detail: 0, sides: 6, branches: 2 },
    { detail: 0, sides: 4, branches: 0 },
  ][level]
  const branches = [new THREE.CylinderGeometry(.14, .35, 7.8, sides).translate(0, 3.9, 0)]
  const up = new THREE.Vector3(0, 1, 0)
  for (let branch = 0; branch < branchCount; branch++) {
    const start = new THREE.Vector3(0, 3.9, 0)
    const end = new THREE.Vector3(branch === 0 ? -1.45 : 1.4, 7.1, branch === 0 ? .2 : -.3)
    const delta = end.clone().sub(start)
    const geometry = new THREE.CylinderGeometry(.06, .15, delta.length(), level === 0 ? 5 : 4)
    geometry.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(up, delta.normalize()))
    geometry.translate(...start.add(end).multiplyScalar(.5).toArray()); branches.push(geometry)
  }
  const crownUnit = new THREE.IcosahedronGeometry(1, detail)
  crownUnit.computeBoundingBox()
  // Icosahedron subdivisions have different extrema. Normalize them so trees
  // keep their height/width when zooming across a detail boundary.
  const extent = crownUnit.boundingBox.getSize(new THREE.Vector3())
  crownUnit.scale(2 / extent.x, 2 / extent.y, 2 / extent.z)
  const crowns = [
    [-1.45, 7.0, .2, 2.25, 2.7, 2.25],
    [1.4, 7.2, -.3, 2.25, 2.65, 2.25],
    [0, 9.0, -.65, 2.35, 3.0, 2.3],
  ].map(([x, y, z, sx, sy, sz]) => crownUnit.clone().scale(sx, sy, sz).translate(x, y, z))
  const trunk = mergeGeometries(branches), crown = mergeGeometries(crowns)
  crownUnit.dispose()
  branches.forEach(g => g.dispose()); crowns.forEach(g => g.dispose())
  return { trunk, crown }
}
