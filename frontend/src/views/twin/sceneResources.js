// WebGLRenderer.dispose does not own the render targets attached to light shadows.
export function disposeSceneExtras(scene, modelRoot) {
  scene?.children.filter(child => child !== modelRoot).forEach(child => {
    child.traverse(object => {
      object.geometry?.dispose()
      const materials = object.material ? (Array.isArray(object.material) ? object.material : [object.material]) : []
      materials.forEach(material => material.dispose())
      if (object.isLight) object.dispose?.()
    })
  })
}
