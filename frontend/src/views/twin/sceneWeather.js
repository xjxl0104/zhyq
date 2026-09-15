import * as THREE from 'three'

// Local visual presets. These deliberately do not represent live weather observations.
const PRESETS = {
  sunny: { top: '#347fbc', horizon: '#bfd2dd', near: 260, far: 1200, disc: 1, exposure: .98, environment: .3, sun: ['#fff0d2', 4.2], fill: ['#b1cae3', .23], sky: ['#c6dff2', '#6d7060', .52] },
  night: { top: '#0c1029', horizon: '#29384a', near: 180, far: 1000, disc: .025, exposure: 1, environment: .085, sun: ['#a8c4ed', .32], fill: ['#779fcb', .12], sky: ['#799bc4', '#232a48', .32] },
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

/** Borrow scene, renderer and lights; dispose restores them without disposing them. */
export function createSceneWeather({ scene, renderer, sunlight, fill, hemisphere }) {
  const original = {
    background: scene.background, fog: scene.fog, environmentIntensity: scene.environmentIntensity,
    exposure: renderer.toneMappingExposure,
  }
  const lightSnapshots = [sunlight, fill, hemisphere].filter(Boolean).map(light => ({
    light, color: light.color.clone(), intensity: light.intensity, position: light.position.clone(),
    groundColor: light.groundColor?.clone(), castShadow: light.castShadow,
  }))
  const materialSnapshots = new Map()
  const windowMaterials = new Set(), lampMaterials = new Set()
  // Include locally generated windows and street lamps as well as the GLB materials.
  scene.traverse(object => {
    const materials = Array.isArray(object.material) ? object.material : object.material ? [object.material] : []
    for (const material of materials) {
      const name = material.name || ''
      if (/^(glass|window)/i.test(name)) windowMaterials.add(material)
      if (/^siteLamp/i.test(name)) lampMaterials.add(material)
      if (windowMaterials.has(material) || lampMaterials.has(material)) {
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
      sunDirection: { value: new THREE.Vector3() }, discStrength: { value: 0 },
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
      uniform vec3 sunDirection;
      uniform float discStrength;
      varying vec3 vSkyDirection;
      void main() {
        vec3 direction = normalize(vSkyDirection);
        // The complete lower hemisphere matches fog exactly. A pale horizon band
        // gradually becomes blue above it instead of meeting green at a hard edge.
        float elevation = pow(smoothstep(0.0, 0.28, max(direction.y, 0.0)), 0.45);
        vec3 color = mix(horizonColor, topColor, elevation);
        float alignment = clamp(dot(direction, sunDirection), -1.0, 1.0);
        float angleSquared = 2.0 * (1.0 - alignment);
        float halo = exp(-angleSquared / 0.014) * 0.22;
        float glow = exp(-angleSquared / 0.0009) * 0.55;
        float disc = smoothstep(cos(0.007), cos(0.0046), alignment);
        color += vec3(1.0, 0.83, 0.57) * (halo + glow + disc * 12.0) * discStrength;
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

  const nightLights = []
  for (const [x, z] of [[-72, 38], [72, 38], [-72, -38], [72, -38]]) {
    const light = new THREE.PointLight('#ffcc87', 0, 32, 2)
    light.name = 'weather-site-light'
    light.position.set(x, 6.5, z)
    light.castShadow = false
    nightLights.push(light)
    group.add(light)
  }
  const fog = new THREE.Fog()
  scene.add(group)
  let disposed = false, current = 'sunny'

  function setWeather(preset) {
    if (disposed) return
    current = Object.hasOwn(PRESETS, preset) ? preset : 'sunny'
    const config = PRESETS[current]
    scene.background = null
    scene.fog = fog
    fog.color.set(config.horizon); fog.near = config.near; fog.far = config.far
    scene.environmentIntensity = config.environment
    renderer.toneMappingExposure = config.exposure
    skyMaterial.uniforms.topColor.value.set(config.top)
    skyMaterial.uniforms.horizonColor.value.set(config.horizon)
    skyMaterial.uniforms.discStrength.value = config.disc
    if (sunlight) {
      sunlight.color.set(config.sun[0]); sunlight.intensity = config.sun[1]
      sunlight.position.set(-140, 35, 60)
      sunlight.castShadow = true
    }
    const sunDirection = skyMaterial.uniforms.sunDirection.value
    if (sunlight) sunDirection.copy(sunlight.position).sub(sunlight.target.position).normalize()
    else sunDirection.set(-140, 35, 60).normalize()
    if (fill) { fill.color.set(config.fill[0]); fill.intensity = config.fill[1] }
    if (hemisphere) {
      hemisphere.color.set(config.sky[0]); hemisphere.groundColor?.set(config.sky[1]); hemisphere.intensity = config.sky[2]
    }
    materialSnapshots.forEach(restoreMaterial)
    if (current === 'night') {
      windowMaterials.forEach(material => {
        if (material.emissive) { material.emissive.set('#edc48c'); material.emissiveIntensity = .16 }
      })
      lampMaterials.forEach(material => {
        if (material.emissive) { material.emissive.set('#ffe2a6'); material.emissiveIntensity = 2.4 }
      })
    }
    nightLights.forEach(light => { light.visible = current === 'night'; light.intensity = current === 'night' ? 85 : 0 })
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
    nightLights.forEach(light => light.dispose())
    group.clear()
    materialSnapshots.clear(); windowMaterials.clear(); lampMaterials.clear()
  }

  setWeather('sunny')
  return { setWeather, dispose }
}
