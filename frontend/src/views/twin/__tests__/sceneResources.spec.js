import { describe, it, expect, vi } from 'vitest'
import { Scene, DirectionalLight, WebGLRenderTarget, Group } from 'three'
import { disposeSceneExtras } from '../sceneResources'

describe('scene resource ownership', () => {
  it('releases the shadow texture when the homepage scene is removed', () => {
    const scene = new Scene(), root = new Group(), sunlight = new DirectionalLight()
    scene.add(root, sunlight)
    sunlight.shadow.map = new WebGLRenderTarget(2048, 2048)
    const disposed = vi.fn()
    sunlight.shadow.map.addEventListener('dispose', disposed)
    disposeSceneExtras(scene, root)
    expect(disposed).toHaveBeenCalledOnce()
  })
})
