import * as THREE from 'three'
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js'
import { createLandscapeTextures } from './parkVegetation.js'
import { createParkTreeLod } from './parkTreeLod.js'

// Context outside the 240 × 190 m model is an illustrative landscape, not survey data.
// Everything is generated locally: no extra asset request or continuous animation.
export function createParkLandscape(scene, model) {
  const group = new THREE.Group()
  group.name = 'park-landscape-context'
  group.userData.positionStatus = 'Illustrative landscape, not a surveyed site plan.'
  scene.add(group)
  const resources = new Set(), borrowed = [], batches = new Map()
  const own = resource => { resources.add(resource); return resource }
  const textures = createLandscapeTextures(own)
  const material = (name, color, options = {}) => own(new THREE.MeshStandardMaterial({ name, color, roughness: .9, ...options }))
  const mats = {
    ground: material('contextGround', '#828b78'),
    lawn: material('contextLawn', '#556e3c'),
    grove: material('contextGrove', '#354e2d'),
    paving: material('sitePavementContext', '#beb9a7'),
    path: material('sitePavementWalk', '#c9c0a7'),
    road: material('siteRoadContext', '#404d52', { roughness: .92 }),
    curb: material('contextCurb', '#c5ced2'),
    line: material('contextLaneLine', '#c8d0cf'),
    yellow: material('contextLaneCentre', '#b6a278'),
    building: material('contextBuilding', '#e0d7be'),
    roof: material('contextRoof', '#586366'),
    glazing: material('windowContext', '#24576b', { metalness: .28, roughness: .25 }),
    timber: material('contextTimber', '#ffffff', { ...textures.wood, bumpScale: .035, roughness: .76 }),
    trunk: material('contextBark', '#9b917a'),
    leaf: material('contextLeaf', '#ffffff'),
    lamp: material('siteLampContext', '#dbe3db', { emissive: '#ffcf91', emissiveIntensity: .15 }),
  }

  // World-space grain stays at the same scale on long roads and small paving slabs.
  function addGrain(mat, scale, strength) {
    mat.onBeforeCompile = shader => {
      shader.vertexShader = 'varying vec3 vParkSurface;\n' + shader.vertexShader;
      shader.vertexShader = shader.vertexShader.replace('#include <begin_vertex>', '#include <begin_vertex>\nvParkSurface = (modelMatrix * vec4(position, 1.0)).xyz;')
      shader.fragmentShader = `varying vec3 vParkSurface;
        float parkGrain(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
      ` + shader.fragmentShader
      shader.fragmentShader = shader.fragmentShader.replace('#include <color_fragment>', `#include <color_fragment>
        float grain = parkGrain(floor(vParkSurface.xz * ${scale.toFixed(2)}));
        diffuseColor.rgb *= 1.0 + (grain - 0.5) * ${strength.toFixed(3)};
      `)
    }
    mat.customProgramCacheKey = () => 'park-grain-' + scale + '-' + strength
    mat.needsUpdate = true
  }
  addGrain(mats.road, 5, .06)
  addGrain(mats.ground, 2, .045)
  addGrain(mats.lawn, 3, .11)
  addGrain(mats.paving, 3, .08)

  const seen = new Set()
  model.root.traverse(object => {
    for (const mat of Array.isArray(object.material) ? object.material : object.material ? [object.material] : []) {
      if (!mat.isMeshStandardMaterial || seen.has(mat)) continue
      seen.add(mat)
      borrowed.push({ mat, color: mat.color.clone(), roughness: mat.roughness, metalness: mat.metalness, envMapIntensity: mat.envMapIntensity, onBeforeCompile: mat.onBeforeCompile, customProgramCacheKey: mat.customProgramCacheKey })
      const name = mat.name.split('.')[0]
      const finishes = {
        white: ['#e0ded5', .78, .02], edge: ['#9da4a3', .72, .08], concrete: ['#a09d90', .93, 0],
        road: ['#404d52', .95, .02], paving: ['#c5c0ac', .9, 0], grass: ['#556e3c', 1, 0],
        tree: ['#284d2d', 1, 0], treeLight: ['#486339', 1, 0],
        glass: ['#244b63', .19, .52], glassLight: ['#426b84', .22, .48], glassPale: ['#7297ae', .25, .4],
        teal: ['#087e99', .31, .3],
      }
      if (finishes[name]) {
        const [color, roughness, metalness] = finishes[name]
        mat.color.set(color); mat.roughness = roughness; mat.metalness = metalness
      }
      if (/^(glass|teal)/.test(name)) mat.envMapIntensity = 1.3
      if (/^(road|paving|concrete|grass)$/.test(name)) addGrain(mat, name === 'road' ? 5 : 3, .055)
    }
  })

  // Merge one static draw per finish; instance the more numerous organic elements.
  const box = (mat, x, y, z, w, h, d, angle = 0) => {
    const geometry = new THREE.BoxGeometry(w, h, d).rotateY(angle).translate(x, y, z)
    if (!batches.has(mat)) batches.set(mat, [])
    batches.get(mat).push(geometry)
  }
  const strip = (mat, x1, z1, x2, z2, width, y = .085) => {
    box(mat, (x1 + x2) / 2, y, (z1 + z2) / 2, width, .025, Math.hypot(x2 - x1, z2 - z1), Math.atan2(x2 - x1, z2 - z1))
  }
  const ground = new THREE.Mesh(own(new THREE.PlaneGeometry(6000, 6000)), mats.ground)
  ground.rotation.x = -Math.PI / 2; ground.position.y = -.36; ground.receiveShadow = true; group.add(ground)

  // A low, continuous terrain band gives the far view a natural silhouette. It
  // starts beyond the streets and fades into the same air colour as the sky.
  const rings = [[360, -.3], [510, 15], [710, 43], [980, 61], [1350, 36], [1950, -.3]]
  const positions = [], indices = [], segments = 160
  rings.forEach(([radius, height], ring) => {
    for (let index = 0; index <= segments; index++) {
      const angle = index / segments * Math.PI * 2
      const profile = .72 + .22 * Math.sin(angle * 3 + .8) + .19 * Math.cos(angle * 7 - .6) + .07 * Math.sin(angle * 13)
      positions.push(Math.cos(angle) * radius, height > 0 ? height * profile : height, Math.sin(angle) * radius)
      if (ring < rings.length - 1 && index < segments) {
        const a = ring * (segments + 1) + index, b = a + segments + 1
        indices.push(a, a + 1, b, b, a + 1, b + 1)
      }
    }
  })
  const terrainGeometry = own(new THREE.BufferGeometry())
  terrainGeometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
  terrainGeometry.setIndex(indices); terrainGeometry.computeVertexNormals()
  const terrain = new THREE.Mesh(terrainGeometry, material('contextDistantTerrain', '#7c8875'))
  terrain.name = 'park-distant-terrain'; group.add(terrain)

  function road(x, z, length, horizontal) {
    box(mats.road, x, -.09, z, horizontal ? length : 14, .32, horizontal ? 14 : length)
    for (const side of [-1, 1]) {
      box(mats.paving, x + (horizontal ? 0 : side * 10), -.055, z + (horizontal ? side * 10 : 0), horizontal ? length : 4, .38, horizontal ? 4 : length)
      box(mats.curb, x + (horizontal ? 0 : side * 7.95), .07, z + (horizontal ? side * 7.95 : 0), horizontal ? length : .24, .28, horizontal ? .24 : length)
      const dx = horizontal ? length / 2 : 0, dz = horizontal ? 0 : length / 2
      const ox = horizontal ? 0 : side * .19, oz = horizontal ? side * .19 : 0
      strip(mats.yellow, x + ox - dx, z + oz - dz, x + ox + dx, z + oz + dz, .12)
    }
    for (let offset = -length / 2 + 2; offset < length / 2 - 3; offset += 8) {
      for (const side of [-3.4, 3.4]) {
        box(mats.line, x + (horizontal ? offset : side), .089, z + (horizontal ? side : offset), horizontal ? 4 : .13, .025, horizontal ? .13 : 4)
      }
    }
  }
  // Existing roads terminate at x=±120 / z=±95. Continue exactly from those edges.
  for (const x of [-86, 86]) { road(x, -193.5, 197, false); road(x, 185, 180, false) }
  for (const z of [-72, 78]) { road(-205, z, 170, true); road(205, z, 170, true) }

  const treePositions = []
  const plant = (x, z, scale = 1, shadowY = -.115) => {
    // Leave the pavilion approach clear.
    const onApproach = x > 145 && x < 220 && z > 125 && z < 195
      && Math.abs((x - 146) * 67 - (z - 114) * 62) / Math.hypot(67, 62) < 11
    if (!onApproach) treePositions.push([x, z, scale, shadowY])
  }
  // An urban grove behind the warehouse; varied planting density leaves legible clearings.
  for (const [cx, cz, w, d] of [[0, -163, 138, 110], [-170, 1, 104, 120], [177, -157, 122, 110], [176, 6, 114, 115]]) {
    box(mats.lawn, cx, -.22, cz, w, .16, d)
    for (const side of [-1, 1]) {
      box(mats.path, cx + side * (w / 2 - 5), -.09, cz, 2.6, .16, d - 8)
      box(mats.path, cx, -.09, cz + side * (d / 2 - 5), w - 8, .16, 2.6)
    }
  }
  // Narrow promenade, planted beds and a pergola give the open north parcel a purpose.
  box(mats.path, 0, -.09, -132, 126, .17, 6)
  for (const x of [-43, -20, 20, 43]) {
    box(mats.grove, x, -.1, -153, 15, .28, 22)
    for (const dx of [-4, 4]) for (const dz of [-6, 4]) plant(x + dx, -153 + dz, 1.05 + (dx > 0 ? .16 : 0), .065)
    box(mats.timber, x, .6, -139, 4.5, .3, 1.15)
    for (const dx of [-1.6, 1.6]) box(mats.trunk, x + dx, .22, -139, .2, .65, .8)
  }
  box(mats.paving, 0, -.05, -169, 23, .23, 20)
  for (const x of [-8, 8]) for (const z of [-175, -164]) box(mats.timber, x, 1.7, z, .28, 3.7, .28)
  for (let x = -9; x <= 9; x += 1.25) box(mats.timber, x, 3.55, -169.5, .2, .28, 14)
  for (const x of [-108, 108]) for (let z = -256; z <= 258; z += 15) {
    if (Math.abs(z + 72) < 18 || Math.abs(z - 78) < 18) continue
    plant(x + Math.sin(z * .23) * 1.2, z, .8 + .17 * (1 + Math.sin(z)))
  }
  for (const z of [-96, 101]) for (let x = -258; x <= 256; x += 17) {
    if (Math.abs(x) < 124 || Math.abs(Math.abs(x) - 86) < 20) continue
    plant(x, z, .85 + .18 * (1 + Math.cos(x)))
  }
  for (let i = 0; i < 70; i++) {
    const x = Math.sin(i * 8.13) * 57, z = -192 - (i % 5) * 12
    plant(x, z, .78 + .16 * (i % 4))
  }
  // Tall edge groves frame the foreground; the logistics roads stay open.
  for (let i = 0; i < 55; i++) {
    const x = 125 + (i % 9) * 12 + Math.sin(i * 2.3) * 3
    const z = 119 + Math.floor(i / 9) * 14 + Math.cos(i * 1.7) * 3
    if (!(x > 130 && x < 161 && z < 139)) plant(x, z, 1.15 + (i % 4) * .12)
    if (i < 30) plant(-x, z, 1.05 + (i % 3) * .15)
  }
  // A small timber garden pavilion puts the requested warm material beside the grove.
  box(mats.timber, 145, .12, 120, 30, .35, 23)
  box(mats.building, 146, 2.6, 112, 20, 5, 11)
  box(mats.roof, 146, 5.2, 114, 22, .28, 16)
  box(mats.glazing, 146, 2.65, 117.53, 18.8, 4.5, .06)
  for (let x = 136; x <= 156; x += .8) box(mats.timber, x, 2.6, 106.42, .5, 5, .2)
  for (const x of [136, 156]) {
    box(mats.timber, x, 2.6, 112, .24, 5, 11)
    box(mats.timber, x, 2.6, 123, .24, 5, .24)
  }
  for (let x = 135; x <= 157; x += 1.15) box(mats.timber, x, 5.45, 121, .22, .32, 10)
  plant(130, 109, 1.35); plant(161, 114, 1.5)

  // Low architectural masses supply scale without competing with the 7-storey warehouse.
  for (const [x, z, w, h, d] of [[-171, -146, 58, 15, 30], [-172, -194, 42, 10, 27], [175, -160, 62, 12, 36], [-180, 24, 47, 9, 32]]) {
    box(mats.paving, x, -.17, z, w + 12, .25, d + 12)
    box(mats.building, x, h / 2, z, w, h, d)
    // Cedar screens have real thickness, so their shadows remain directional.
    for (const side of [-1, 1]) for (let dx = -w / 2 + .5; dx < w / 2; dx += .75) {
      box(mats.timber, x + dx, h / 2, z + side * (d / 2 + .16), .36, h, .25)
    }
    box(mats.roof, x, h + .17, z, w - 1.1, .45, d - 1.1)
    for (let y = 3; y < h - 1; y += 3.6) for (const side of [-1, 1]) {
      box(mats.glazing, x, y, z + side * (d / 2 + .025), w - 4, 1.05, .05)
    }
    for (let dx = -w / 2 + 8; dx < w / 2 - 4; dx += 9) box(mats.curb, x + dx, h + .6, z, 2.5, .7, 4)
    for (let dx = -w / 2; dx <= w / 2; dx += 12) plant(x + dx, z + d / 2 + 10, .78)
  }
  // A planted rain garden and stepping promenade at the east edge of the parcel.
  box(mats.grove, 169, -.09, 7, 29, .25, 66)
  for (let z = -26; z <= 38; z += 7) {
    box(mats.path, 188, -.035, z, 9, .15, 4.5)
    plant(148 + Math.sin(z) * 4, z, .95)
    plant(177 + Math.cos(z) * 3, z + 2, .8, .06)
  }

  const lampPositions = []
  for (const x of [-98, 98]) for (const z of [-126, -178, 122, 174]) {
    box(mats.trunk, x, 2.6, z, .13, 5.5, .13)
    box(mats.trunk, x - Math.sign(x) * 1.0, 5.34, z, 2.1, .13, .13)
    box(mats.lamp, x - Math.sign(x) * 1.9, 5.29, z, .8, .12, .4)
    lampPositions.push([x - Math.sign(x) * 1.9, z])
  }
  for (const [mat, chunks] of batches) {
    const mesh = new THREE.Mesh(own(mergeGeometries(chunks, false)), mat)
    chunks.forEach(geometry => geometry.dispose())
    mesh.receiveShadow = true
    // Only architectural context casts into the cached main shadow map.
    mesh.castShadow = mat === mats.building || mat === mats.roof || mat === mats.timber
    group.add(mesh)
  }
  const dummy = new THREE.Object3D()
  const instances = (geometry, mat, count) => {
    const mesh = own(new THREE.InstancedMesh(own(geometry), mat, count))
    mesh.receiveShadow = true; group.add(mesh); return mesh
  }
  const trees = createParkTreeLod(group, treePositions, mats, own)

  // Analytic contact shading follows the official contact-shadow principle, cached as
  // two tiny alpha textures. No secondary scene render or screen-space AO per frame.
  function contactTexture(rectangular) {
    const size = 128, data = new Uint8Array(size * size * 4)
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const u = (x + .5) / size * 2 - 1, v = (y + .5) / size * 2 - 1
      const distance = rectangular ? Math.hypot(Math.max(Math.abs(u) - .77, 0), Math.max(Math.abs(v) - .68, 0)) * 10 : Math.hypot(u, v) * 2.2
      const alpha = Math.exp(-distance * distance * 2.3) * (rectangular ? 175 : 95)
      const offset = (y * size + x) * 4
      data[offset] = 35; data[offset + 1] = 47; data[offset + 2] = 69; data[offset + 3] = Math.round(alpha)
    }
    const texture = own(new THREE.DataTexture(data, size, size, THREE.RGBAFormat))
    texture.magFilter = THREE.LinearFilter; texture.minFilter = THREE.LinearFilter; texture.colorSpace = THREE.SRGBColorSpace; texture.needsUpdate = true
    return texture
  }
  const contactMat = own(new THREE.MeshBasicMaterial({ map: contactTexture(true), transparent: true, depthWrite: false, toneMapped: false, opacity: .52 }))
  const contact = new THREE.Mesh(own(new THREE.PlaneGeometry(126, 80)), contactMat)
  contact.rotation.x = -Math.PI / 2; contact.position.y = .235; contact.renderOrder = 1; group.add(contact)
  const treeShadowMat = own(new THREE.MeshBasicMaterial({ map: contactTexture(false), transparent: true, depthWrite: false, toneMapped: false, opacity: .75 }))
  const shadowGeometry = new THREE.PlaneGeometry(10, 9).rotateX(-Math.PI / 2)
  const shadows = instances(shadowGeometry, treeShadowMat, treePositions.length)
  treePositions.forEach(([x, z, scale, shadowY], index) => {
    dummy.position.set(x + .9, shadowY, z - .6); dummy.scale.set(scale, 1, scale); dummy.rotation.set(0, 0, 0); dummy.updateMatrix(); shadows.setMatrixAt(index, dummy.matrix)
  })
  shadows.instanceMatrix.needsUpdate = true; shadows.computeBoundingSphere(); shadows.renderOrder = 1
  const glowMat = own(new THREE.MeshBasicMaterial({ color: '#ffc477', map: treeShadowMat.map, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, opacity: .55 }))
  const glows = instances(new THREE.PlaneGeometry(13, 13).rotateX(-Math.PI / 2), glowMat, lampPositions.length)
  lampPositions.forEach(([x, z], index) => { dummy.position.set(x, .145, z); dummy.scale.setScalar(1); dummy.updateMatrix(); glows.setMatrixAt(index, dummy.matrix) })
  glows.instanceMatrix.needsUpdate = true; glows.computeBoundingSphere(); glows.visible = false
  let disposed = false
  return {
    group,
    update(camera) { return !disposed && trees.update(camera) },
    setMode(mode) { contact.visible = mode === 'exterior' },
    setWeather(weather) {
      glows.visible = weather === 'night'
      contactMat.opacity = weather === 'night' ? .32 : .52
      treeShadowMat.opacity = weather === 'night' ? .28 : .75
    },
    dispose() {
      if (disposed) return
      disposed = true; group.removeFromParent()
      for (const { mat, color: originalColor, ...original } of borrowed) {
        mat.color.copy(originalColor); Object.assign(mat, original); mat.needsUpdate = true
      }
      resources.forEach(resource => resource.dispose()); resources.clear(); group.clear()
    },
  }
}
