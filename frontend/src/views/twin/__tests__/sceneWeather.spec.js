// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'
import * as THREE from 'three'
import { createSceneWeather } from '../sceneWeather'

function setup(reducedMotion = false) {
  const scene = new THREE.Scene()
  scene.background = new THREE.Color('#80909a')
  scene.fog = new THREE.Fog('#a0a0a0', 50, 900)
  scene.environment = new THREE.Texture()
  scene.environmentIntensity = .37
  const renderer = { toneMappingExposure: 1.17 }
  const sunlight = new THREE.DirectionalLight('#ffddbb', 2.7)
  sunlight.position.set(30, 90, -20)
  const fill = new THREE.DirectionalLight('#bbccff', .7)
  const hemisphere = new THREE.HemisphereLight('#d5e8ff', '#717466', 1.3)
  const root = new THREE.Group()
  const road = new THREE.MeshStandardMaterial({ name: 'road.001', color: '#90999c', roughness: .82, metalness: .03 })
  const glass = new THREE.MeshStandardMaterial({ name: 'glassLight.001', color: '#597d90', emissive: '#010204', emissiveIntensity: .2 })
  const lamp = new THREE.MeshStandardMaterial({ name: 'siteLamp.001', emissive: '#101010', emissiveIntensity: .15 })
  const wall = new THREE.MeshStandardMaterial({ name: 'white', color: '#eeeeee' })
  for (const material of [road, glass, lamp, wall]) root.add(new THREE.Mesh(new THREE.BoxGeometry(), material))
  scene.add(root, sunlight, fill, hemisphere)
  const baseline = {
    background: scene.background, fog: scene.fog, environment: scene.environment,
    environmentIntensity: scene.environmentIntensity, exposure: renderer.toneMappingExposure,
    sunlightColor: sunlight.color.clone(), sunlightPosition: sunlight.position.clone(),
    fillColor: fill.color.clone(), hemisphereColor: hemisphere.color.clone(), groundColor: hemisphere.groundColor.clone(),
    roadColor: road.color.clone(), glassEmissive: glass.emissive.clone(), lampEmissive: lamp.emissive.clone(),
    children: [...scene.children],
  }
  const weather = createSceneWeather({ scene, renderer, model: { root }, sunlight, fill, hemisphere, reducedMotion })
  return { scene, renderer, root, sunlight, fill, hemisphere, road, glass, lamp, wall, baseline, weather }
}

describe('local scene weather', () => {
  it('switches daylight and window illumination with sunny fallback for removed weather', () => {
    const { scene, sunlight, road, glass, lamp, wall, baseline, weather } = setup()
    const sunnyIntensity = sunlight.intensity
    weather.setWeather('night')
    expect(sunlight.intensity).toBeLessThan(sunnyIntensity * .3)
    expect(glass.emissive.equals(baseline.glassEmissive)).toBe(false)
    expect(lamp.emissiveIntensity).toBeGreaterThan(1)
    expect(wall.emissive.getHex()).toBe(0)
    weather.setWeather('rain')
    expect(sunlight.intensity).toBe(sunnyIntensity)
    expect(road.color.equals(baseline.roadColor)).toBe(true)
    expect(road.roughness).toBe(.82)
    expect(glass.emissive.equals(baseline.glassEmissive)).toBe(true)
    expect(scene.getObjectByName('weather-rain')).toBeUndefined()
    weather.dispose()
  })

  it('uses the same horizon colour and light direction for the sky and scene', () => {
    const { scene, sunlight, weather } = setup()
    for (const mode of ['sunny', 'night']) {
      weather.setWeather(mode)
      const sky = scene.getObjectByName('weather-sky').material.uniforms
      expect(scene.fog.color.equals(sky.horizonColor.value)).toBe(true)
      expect(sky.sunDirection.value.dot(sunlight.position.clone().sub(sunlight.target.position).normalize())).toBeCloseTo(1)
      expect(scene.fog.near).toBeGreaterThan(150)
      expect(scene.fog.far).toBeLessThan(1800)
    }
    weather.dispose()
  })

  it('restores the borrowed scene and releases only its own resources exactly once', () => {
    const { scene, renderer, root, sunlight, fill, hemisphere, road, glass, lamp, baseline, weather } = setup()
    const originalDisposed = vi.fn()
    root.traverse(object => { object.geometry?.addEventListener('dispose', originalDisposed); object.material?.addEventListener('dispose', originalDisposed) })
    baseline.environment.addEventListener('dispose', originalDisposed)
    const ownedResources = new Set()
    scene.children.filter(child => !baseline.children.includes(child)).forEach(child => child.traverse(object => {
      if (object.geometry) ownedResources.add(object.geometry)
      if (object.material) ownedResources.add(object.material)
    }))
    const ownedDisposed = vi.fn()
    ownedResources.forEach(resource => resource.addEventListener('dispose', ownedDisposed))
    expect(ownedResources.size).toBeGreaterThanOrEqual(2)
    weather.setWeather('night')
    weather.setWeather('rain')
    weather.dispose()
    weather.dispose()
    weather.setWeather('night')
    expect(scene.children).toEqual(baseline.children)
    expect(scene.background).toBe(baseline.background)
    expect(scene.fog).toBe(baseline.fog)
    expect(scene.environment).toBe(baseline.environment)
    expect(scene.environmentIntensity).toBe(baseline.environmentIntensity)
    expect(renderer.toneMappingExposure).toBe(baseline.exposure)
    expect(sunlight.intensity).toBe(2.7)
    expect(sunlight.color.equals(baseline.sunlightColor)).toBe(true)
    expect(sunlight.position.equals(baseline.sunlightPosition)).toBe(true)
    expect(sunlight.castShadow).toBe(false)
    expect(fill.intensity).toBe(.7)
    expect(fill.color.equals(baseline.fillColor)).toBe(true)
    expect(hemisphere.intensity).toBe(1.3)
    expect(hemisphere.color.equals(baseline.hemisphereColor)).toBe(true)
    expect(hemisphere.groundColor.equals(baseline.groundColor)).toBe(true)
    expect(road.roughness).toBe(.82)
    expect(road.metalness).toBe(.03)
    expect(road.color.equals(baseline.roadColor)).toBe(true)
    expect(glass.emissive.equals(baseline.glassEmissive)).toBe(true)
    expect(glass.emissiveIntensity).toBe(.2)
    expect(lamp.emissive.equals(baseline.lampEmissive)).toBe(true)
    expect(lamp.emissiveIntensity).toBe(.15)
    expect(originalDisposed).not.toHaveBeenCalled()
    expect(ownedDisposed).toHaveBeenCalledTimes(ownedResources.size)
  })
})
