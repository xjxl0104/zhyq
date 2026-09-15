import * as THREE from 'three'
import { MODEL, modelHeight } from './twinData.js'

// Local visual presets. These deliberately do not represent live weather observations.
const PRESETS = {
  sunny: { top: '#477fae', horizon: '#dbe8ed', fog: '#d5e4eb', density: .0009, cloud: .12, disc: .85, exposure: 1.03, environment: .4, sun: ['#fff1da', 2.9], fill: ['#d6e8ff', .6], sky: ['#d7eaff', '#8b9a80', 1.55] },
  rain: { top: '#3e5264', horizon: '#a7b7c2', fog: '#8fa4b4', density: .0031, cloud: .7, disc: 0, exposure: .88, environment: .22, sun: ['#b9cddd', .65], fill: ['#9db5cf', .4], sky: ['#b5cadb', '#52676b', 1.05] },
  night: { top: '#060e22', horizon: '#263b54', fog: '#172d43', density: .0019, cloud: .13, disc: .18, exposure: 1, environment: .085, sun: ['#a8c4ed', .32], fill: ['#779fcb', .12], sky: ['#799bc4', '#1b2c39', .32] },
}

function captureMaterial(material) {
  return {
    material, color: material.color?.clone(), roughness: material.roughness, metalness: material.metalness,
    emissive: material.emissive?.clone(), emissiveIntensity: material.emissiveIntensity,
  }
}

function restoreMaterial(snapshot) {
  const material = snapshot.material
  if (snapshot.color) material.color.copy(snapshot.color)
  if (snapshot.emissive) material.emissive.copy(snapshot.emissive)
  for (const key of ['roughness', 'metalness', 'emissiveIntensity']) {
    if (snapshot[key] !== undefined) material[key] = snapshot[key]
  }
}

/** Borrow scene, renderer, model and lights; dispose restores them without disposing them. */
export function createSceneWeather({ scene, renderer, model, sunlight, fill, hemisphere, reducedMotion = false }) {
  const original = {
    background: scene.background, fog: scene.fog, environmentIntensity: scene.environmentIntensity,
    exposure: renderer.toneMappingExposure,
  }
  const lightSnapshots = [sunlight, fill, hemisphere].filter(Boolean).map(light => ({
    light, color: light.color.clone(), intensity: light.intensity, position: light.position.clone(),
    groundColor: light.groundColor?.clone(), castShadow: light.castShadow,
  }))
  const materialSnapshots = new Map()
  const wetMaterials = new Set(), windowMaterials = new Set(), lampMaterials = new Set()
  ;(model?.root || model)?.traverse(object => {
    const materials = Array.isArray(object.material) ? object.material : object.material ? [object.material] : []
    for (const material of materials) {
      const name = material.name || ''
      if (/^(road|paving|asphalt|siteRoad|sitePavement)/i.test(name)) wetMaterials.add(material)
      if (/^(glass|window)/i.test(name)) windowMaterials.add(material)
      if (/^siteLamp/i.test(name)) lampMaterials.add(material)
      if (wetMaterials.has(material) || windowMaterials.has(material) || lampMaterials.has(material)) {
        if (!materialSnapshots.has(material)) materialSnapshots.set(material, captureMaterial(material))
      }
    }
  })

  const group = new THREE.Group()
  group.name = 'scene-weather'
  const skyMaterial = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, depthTest: false, toneMapped: false,
    uniforms: {
      topColor: { value: new THREE.Color() }, horizonColor: { value: new THREE.Color() },
      cloudAmount: { value: 0 }, discStrength: { value: 0 },
    },
    vertexShader: `
      varying vec3 vSkyDirection;
      void main() {
        vSkyDirection = normalize(position);
        // Removing camera translation keeps the sky centred while orbiting or panning.
        vec4 projected = projectionMatrix * vec4(mat3(viewMatrix) * position, 1.0);
        gl_Position = projected.xyww;
      }
    `,
    fragmentShader: `
      uniform vec3 topColor;
      uniform vec3 horizonColor;
      uniform float cloudAmount;
      uniform float discStrength;
      varying vec3 vSkyDirection;
      void main() {
        vec3 direction = normalize(vSkyDirection);
        float elevation = smoothstep(-0.08, 0.85, direction.y);
        vec3 color = mix(horizonColor, topColor, elevation);
        float cloud = sin(direction.x * 8.0 + direction.z * 4.0)
          * sin(direction.z * 13.0 - direction.y * 7.0);
        cloud = smoothstep(-0.15, 0.8, cloud) * smoothstep(0.0, 0.3, direction.y);
        color = mix(color, horizonColor * 1.08, cloud * cloudAmount);
        float disc = smoothstep(0.997, 0.9997, dot(direction, normalize(vec3(-0.55, 0.78, 0.3))));
        color += vec3(1.0, 0.91, 0.72) * disc * discStrength;
        gl_FragColor = vec4(color, 1.0);
        #include <colorspace_fragment>
      }
    `,
  })
  const sky = new THREE.Mesh(new THREE.SphereGeometry(450, 32, 16), skyMaterial)
  sky.name = 'weather-sky'
  sky.frustumCulled = false
  sky.renderOrder = -1000
  group.add(sky)

  // Two vertices per drop. Both the vertex and velocity buffers are retained for all frames.
  const dropCount = 1500, positions = new Float32Array(dropCount * 6), speeds = new Float32Array(dropCount)
  const roofLevel = modelHeight() + 3
  const rainFloor = (x, z) => Math.abs(x) < MODEL.width / 2 + 2 && Math.abs(z) < MODEL.depth / 2 + 2 ? roofLevel : -.3
  function writeDrop(index, x, y, z) {
    const offset = index * 6
    positions[offset] = x; positions[offset + 1] = y; positions[offset + 2] = z
    positions[offset + 3] = x + .22; positions[offset + 4] = y + 1.8; positions[offset + 5] = z - .06
  }
  for (let index = 0; index < dropCount; index++) {
    const x = (Math.random() - .5) * 280, z = (Math.random() - .5) * 230
    const bottom = rainFloor(x, z)
    writeDrop(index, x, bottom + Math.random() * (110 - bottom), z)
    speeds[index] = 31 + Math.random() * 17
  }
  const rainGeometry = new THREE.BufferGeometry()
  const rainAttribute = new THREE.BufferAttribute(positions, 3).setUsage(THREE.DynamicDrawUsage)
  rainGeometry.setAttribute('position', rainAttribute)
  const rainMaterial = new THREE.LineBasicMaterial({ color: '#c3d9e9', transparent: true, opacity: .38, depthWrite: false })
  const rain = new THREE.LineSegments(rainGeometry, rainMaterial)
  rain.name = 'weather-rain'
  rain.frustumCulled = false
  group.add(rain)

  const nightLights = []
  for (const [x, z] of [[-72, 38], [72, 38], [-72, -38], [72, -38]]) {
    const light = new THREE.PointLight('#ffcc87', 0, 32, 2)
    light.name = 'weather-site-light'
    light.position.set(x, 6.5, z)
    light.castShadow = false
    nightLights.push(light)
    group.add(light)
  }
  const fog = new THREE.FogExp2()
  scene.add(group)
  let disposed = false, current = 'sunny'

  function setWeather(preset) {
    if (disposed) return
    current = Object.hasOwn(PRESETS, preset) ? preset : 'sunny'
    const config = PRESETS[current]
    scene.background = null
    scene.fog = fog
    fog.color.set(config.fog); fog.density = config.density
    scene.environmentIntensity = config.environment
    renderer.toneMappingExposure = config.exposure
    skyMaterial.uniforms.topColor.value.set(config.top)
    skyMaterial.uniforms.horizonColor.value.set(config.horizon)
    skyMaterial.uniforms.cloudAmount.value = config.cloud
    skyMaterial.uniforms.discStrength.value = config.disc
    if (sunlight) {
      sunlight.color.set(config.sun[0]); sunlight.intensity = config.sun[1]
      sunlight.position.set(-75, 130, 70)
      sunlight.castShadow = current !== 'rain'
    }
    if (fill) { fill.color.set(config.fill[0]); fill.intensity = config.fill[1] }
    if (hemisphere) {
      hemisphere.color.set(config.sky[0]); hemisphere.groundColor?.set(config.sky[1]); hemisphere.intensity = config.sky[2]
    }
    materialSnapshots.forEach(restoreMaterial)
    if (current === 'rain') {
      wetMaterials.forEach(material => {
        material.color?.multiplyScalar(.72)
        if (material.roughness !== undefined) material.roughness = .2
        if (material.metalness !== undefined) material.metalness = .12
      })
    }
    if (current === 'night') {
      windowMaterials.forEach(material => {
        if (material.emissive) { material.emissive.set('#edc48c'); material.emissiveIntensity = .16 }
      })
      lampMaterials.forEach(material => {
        if (material.emissive) { material.emissive.set('#ffe2a6'); material.emissiveIntensity = 2.4 }
      })
    }
    rain.visible = current === 'rain' && !reducedMotion
    nightLights.forEach(light => { light.visible = current === 'night'; light.intensity = current === 'night' ? 85 : 0 })
  }

  function update(delta) {
    if (disposed || !rain.visible || !Number.isFinite(delta) || delta <= 0) return
    const step = Math.min(delta, .05)
    for (let index = 0; index < dropCount; index++) {
      const offset = index * 6
      let x = positions[offset] - step * 5
      let y = positions[offset + 1] - step * speeds[index]
      let z = positions[offset + 2] + step * 1.2
      if (x < -140) x += 280
      if (z > 115) z -= 230
      if (y < rainFloor(x, z)) y = 110
      writeDrop(index, x, y, z)
    }
    rainAttribute.needsUpdate = true
  }

  function dispose() {
    if (disposed) return
    disposed = true
    scene.background = original.background
    scene.fog = original.fog
    scene.environmentIntensity = original.environmentIntensity
    renderer.toneMappingExposure = original.exposure
    for (const snapshot of lightSnapshots) {
      snapshot.light.color.copy(snapshot.color); snapshot.light.intensity = snapshot.intensity
      snapshot.light.position.copy(snapshot.position); snapshot.light.castShadow = snapshot.castShadow
      if (snapshot.groundColor) snapshot.light.groundColor.copy(snapshot.groundColor)
    }
    materialSnapshots.forEach(restoreMaterial)
    group.removeFromParent()
    sky.geometry.dispose(); skyMaterial.dispose()
    rainGeometry.dispose(); rainMaterial.dispose()
    nightLights.forEach(light => light.dispose())
    group.clear()
    materialSnapshots.clear(); wetMaterials.clear(); windowMaterials.clear(); lampMaterials.clear()
  }

  setWeather('sunny')
  return { setWeather, update, dispose }
}
