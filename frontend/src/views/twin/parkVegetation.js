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
  function surface(bark) {
    const colors = new Uint8Array(size * size * 4), heights = new Uint8Array(colors.length)
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const u = x / size, v = y / size
      const bend = Math.sin(v * Math.PI * 2) * .9 + Math.sin(v * Math.PI * 6 + u * 5) * .3
      const grain = Math.sin(u * Math.PI * (bark ? 38 : 60) + bend * (bark ? 4 : 3))
      const fine = noise(x, y)
      const groove = bark ? Math.pow(Math.max(0, grain), 8) : Math.pow(Math.abs(grain), 12)
      const value = (bark ? .8 : .95) - groove * .37 + (fine - .5) * .12
      const rgb = bark ? [128, 112, 88] : [221, 169, 99]
      const offset = (y * size + x) * 4
      for (let c = 0; c < 3; c++) {
        colors[offset + c] = Math.round(rgb[c] * value)
        heights[offset + c] = Math.round((1 - groove) * 210)
      }
      colors[offset + 3] = heights[offset + 3] = 255
    }
    return { map: texture(colors, true), bumpMap: texture(heights) }
  }
  const leaves = new Uint8Array(size * size * 4)
  const sprays = []
  for (let i = 0; i < 20; i++) {
    const side = i % 2 ? 1 : -1
    sprays.push({ x: .12 + (i % 4) * .25, y: .1 + Math.floor(i / 4) * .2, angle: side * (.4 + noise(i, 3) * .6), length: .075 + .015 * noise(i, 5) })
  }
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    const u = x / size, v = y / size, offset = (y * size + x) * 4
    let alpha = 0, vein = 0
    for (const leaf of sprays) {
      const dx = u - leaf.x, dy = v - leaf.y
      const a = (dx * Math.cos(leaf.angle) + dy * Math.sin(leaf.angle)) / .047
      const b = (-dx * Math.sin(leaf.angle) + dy * Math.cos(leaf.angle)) / leaf.length
      const d = a * a + b * b
      if (d < 1) { alpha = Math.max(alpha, Math.min(1, (1 - d) * 15)); vein = Math.max(vein, Math.exp(-Math.abs(a) * 35) * .13) }
    }
    const shade = .78 + noise(x, y) * .18 + vein
    leaves[offset] = 166 * shade; leaves[offset + 1] = 204 * shade; leaves[offset + 2] = 118 * shade
    leaves[offset + 3] = alpha * 255
  }
  return { wood: surface(false), bark: surface(true), leaf: texture(leaves, true, false) }
}

export function createTreeGeometries() {
  const branches = [new THREE.CylinderGeometry(.12, .35, 8.3, 8).translate(0, 4.15, 0)]
  const up = new THREE.Vector3(0, 1, 0)
  for (let i = 0; i < 9; i++) {
    const angle = i * 2.39996, start = new THREE.Vector3(0, 3.2 + i * .42, 0)
    const end = new THREE.Vector3(Math.cos(angle) * 2.6, 6.7 + i * .36, Math.sin(angle) * 2.6)
    const delta = end.clone().sub(start)
    const geometry = new THREE.CylinderGeometry(.035, .14, delta.length(), 5)
    geometry.applyQuaternion(new THREE.Quaternion().setFromUnitVectors(up, delta.clone().normalize()))
    geometry.translate(...start.add(end).multiplyScalar(.5).toArray()); branches.push(geometry)
  }
  const cards = []
  for (let i = 0; i < 112; i++) {
    const angle = i * 2.39996, elevation = 1 - 2 * (i + .5) / 112
    const ring = Math.sqrt(1 - elevation * elevation)
    const radius = 2.8 + Math.sin(i * 1.73) * .55
    const centre = new THREE.Vector3(Math.cos(angle) * ring * radius, 7.8 + elevation * 3.1, Math.sin(angle) * ring * radius)
    const geometry = new THREE.PlaneGeometry(2.5, 2.6)
    geometry.rotateX(i * .71); geometry.rotateY(angle); geometry.rotateZ(i * .37)
    // Radial normals make leaf sprays read as a continuous canopy instead of flat cards.
    const normal = centre.clone().sub(new THREE.Vector3(0, 6.4, 0)).normalize()
    const normals = geometry.attributes.normal
    for (let vertex = 0; vertex < normals.count; vertex++) normals.setXYZ(vertex, normal.x, normal.y, normal.z)
    geometry.translate(...centre.toArray()); cards.push(geometry)
  }
  const trunk = mergeGeometries(branches), crown = mergeGeometries(cards)
  branches.forEach(g => g.dispose()); cards.forEach(g => g.dispose())
  return { trunk, crown }
}
