import * as THREE from 'three'
import { EffectComposer } from 'three/addons/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/addons/postprocessing/RenderPass.js'
import { GTAOPass } from 'three/addons/postprocessing/GTAOPass.js'
import { OutputPass } from 'three/addons/postprocessing/OutputPass.js'

// Render the expensive contact detail once after an orbit/transition settles.
// Moving cameras skip AO but keep the same color/AA pipeline.
export function createSceneRendering(renderer, scene, camera) {
  const target = new THREE.WebGLRenderTarget(1, 1, { type: THREE.HalfFloatType, samples: 2 })
  const composer = new EffectComposer(renderer, target)
  const beauty = new RenderPass(scene, camera)
  const ao = new GTAOPass(scene, camera, 1, 1)
  ao.updateGtaoMaterial({ radius: 3.5, thickness: 2, distanceFallOff: 1, scale: 1, samples: 12 })
  ao.updatePdMaterial({ radius: 4, samples: 12 })
  ao.blendIntensity = 1.1
  const output = new OutputPass()
  composer.addPass(beauty); composer.addPass(ao); composer.addPass(output)
  let pending = true, settleAt = 0, disposed = false
  // A material override would turn contact planes, leaf cutouts and the sky into
  // opaque occluders. Hide those only for the AO normal/depth pass.
  const renderAO = ao.render.bind(ao)
  ao.render = (...args) => {
    const hidden = []
    scene.traverse(object => {
      const materials = Array.isArray(object.material) ? object.material : object.material ? [object.material] : []
      if (object.visible && materials.length && materials.every(mat => mat.depthWrite === false || mat.alphaTest > 0)) {
        hidden.push(object); object.visible = false
      }
    })
    try { renderAO(...args) } finally { hidden.forEach(object => { object.visible = true }) }
  }
  return {
    resize(width, height) {
      // Bound the temporary buffers independently of a high-DPI monitor.
      composer.setPixelRatio(Math.min(renderer.getPixelRatio(), 1.35, 1800 / Math.max(width, height)))
      composer.setSize(width, height); pending = true
    },
    needsRender(time) { return pending && time >= settleAt },
    render(time, moving) {
      ao.enabled = !moving
      if (moving) {
        pending = true; settleAt = time + 180
      } else {
        pending = false
      }
      composer.render(0)
    },
    dispose() {
      if (disposed) return
      disposed = true
      beauty.dispose(); ao.dispose(); output.dispose(); composer.dispose()
      // r180 GTAOPass.dispose omits these two owned shader materials.
      ao.gtaoMaterial.dispose(); ao.blendMaterial.dispose()
    },
  }
}
