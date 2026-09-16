// @vitest-environment node
import { describe, it, expect, vi } from 'vitest'
import * as THREE from 'three'
import { createAnimeSceneRendering } from '../sceneRendering.js'
import { createSceneWeather } from '../sceneWeather.js'
import { createAnimeSceneStyle } from '../animeSceneStyle.js'

describe('interactive anime render pipeline', () => {
  it('draws the original scene and camera once, without scheduling postprocessing frames', () => {
    const scene = new THREE.Scene(), camera = new THREE.PerspectiveCamera()
    const renderer = { render: vi.fn(), dispose: vi.fn(), getDrawingBufferSize: target => target.set(1500, 950) }
    const pipeline = createAnimeSceneRendering(renderer, scene, camera)
    pipeline.resize(1200, 760)
    pipeline.render(0, true)
    expect(renderer.render).toHaveBeenCalledTimes(1)
    expect(renderer.render).toHaveBeenCalledWith(scene, camera)
    expect(pipeline.needsRender(1000)).toBe(false)
    expect(pipeline.getInfo()).toEqual({ pipeline: 'direct toon', width: 1500, height: 950 })
    pipeline.dispose(); pipeline.dispose()
    expect(renderer.dispose).not.toHaveBeenCalled()
  })

  it('lights converted architectural glass at night and restores it without changing floor metadata', () => {
    const scene = new THREE.Scene(), sunlight = new THREE.DirectionalLight(), fill = new THREE.DirectionalLight(), hemisphere = new THREE.HemisphereLight()
    const source = new THREE.MeshStandardMaterial({ name: 'facadeGlazing', color: '#d9e9e7', transparent: true, opacity: .24, depthWrite: false })
    source.userData.surfaceRole = 'architectural-glass'
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(), source)
    mesh.userData.floor = 4; scene.add(mesh, sunlight, fill, hemisphere)
    const style = createAnimeSceneStyle(scene)
    const toon = mesh.material, sunnyColor = toon.color.clone(), sunnyEmission = toon.emissive.clone()
    const weather = createSceneWeather({ scene, renderer: { toneMappingExposure: 1 }, sunlight, fill, hemisphere, style: 'anime' })
    weather.setWeather('night')
    expect(toon.emissive.getHexString()).toBe('edc48c')
    expect(toon.opacity).toBe(.24)
    expect(toon.depthWrite).toBe(false)
    expect(mesh.userData.floor).toBe(4)
    weather.setWeather('sunny')
    expect(toon.color.equals(sunnyColor)).toBe(true)
    expect(toon.emissive.equals(sunnyEmission)).toBe(true)
    weather.dispose(); style.dispose()
    expect(mesh.material).toBe(source)
    mesh.geometry.dispose(); source.dispose()
  })
})
