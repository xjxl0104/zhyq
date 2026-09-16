// Rebuild the website's GLB from the photo-informed JavaScript model while
// retaining the existing gate and Chinese lettering, with the photo wordmark.
// Usage: node frontend/scripts/rebuild-warehouse-model.mjs [output.glb] [source.glb]
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { Matrix4 } from 'three'
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js'
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { createWarehouse } from '../src/views/twin/warehouseModel.js'
import { upgradeReferenceEntrance } from '../src/views/twin/referenceEntrance.js'

globalThis.FileReader ??= class {
  async readAsArrayBuffer(blob) {
    try {
      this.result = await blob.arrayBuffer()
      this.onloadend?.({ target: this })
    } catch (error) {
      this.error = error
      this.onerror?.({ target: this })
    }
  }
}

const defaultOutput = fileURLToPath(new URL('../public/models/dipark-warehouse.glb', import.meta.url))
const output = resolve(process.argv[2] || defaultOutput)
const source = resolve(process.argv[3] || output)

// Read and parse the whole source before touching output; source and output may
// be the same file, including on every subsequent rebuild.
const sourceBytes = await readFile(source)
const existing = await new GLTFLoader().parseAsync(
  sourceBytes.buffer.slice(sourceBytes.byteOffset, sourceBytes.byteOffset + sourceBytes.byteLength), '',
)
const entrances = []
existing.scene.traverse(object => {
  if (object.userData.sitePart === 'entrance') entrances.push(object)
})
if (entrances.length !== 1) throw new Error(`Expected exactly one existing entrance in ${source}; found ${entrances.length}`)

const model = createWarehouse()
const temporaryOutput = `${output}.tmp-${process.pid}`
try {
  const generatedEntrances = []
  model.site.traverse(object => {
    if (object.userData.sitePart === 'entrance') generatedEntrances.push(object)
  })
  if (generatedEntrances.length !== 1) throw new Error(`Expected one generated entrance; found ${generatedEntrances.length}`)
  generatedEntrances[0].removeFromParent()

  existing.scene.updateMatrixWorld(true)
  model.root.updateMatrixWorld(true)
  const entrance = entrances[0]
  const facadeMaterials = {}
  model.root.traverse(object => {
    for (const material of Array.isArray(object.material) ? object.material : object.material ? [object.material] : []) {
      if (['facadeLogoTeal', 'facadeLogoBlue'].includes(material.name)) facadeMaterials[material.name] = material
    }
  })
  upgradeReferenceEntrance(entrance, facadeMaterials)
  const localMatrix = new Matrix4().copy(model.site.matrixWorld).invert().multiply(entrance.matrixWorld)
  entrance.removeFromParent()
  // Bake its old parent transform into the new local matrix. Keeping the matrix
  // itself prevents losing imported transform precision during decomposition.
  entrance.matrix.copy(localMatrix)
  entrance.matrix.decompose(entrance.position, entrance.quaternion, entrance.scale)
  entrance.matrixAutoUpdate = false
  model.site.add(entrance)

  const helpers = []
  model.root.traverse(object => { if (object.userData.runtimeOnly) helpers.push(object) })
  helpers.forEach(object => object.removeFromParent())

  // Hidden interior/fire groups must survive so the loaded model can still
  // switch between exterior, exploded floors and an individual floor interior.
  const binary = await new GLTFExporter().parseAsync(model.root, { binary: true, onlyVisible: false })
  let meshes = 0, triangles = 0
  model.root.traverse(object => {
    if (!object.isMesh) return
    meshes++
    const count = object.geometry.index?.count ?? object.geometry.attributes.position.count
    triangles += count / 3 * (object.isInstancedMesh ? object.count : 1)
  })
  await mkdir(dirname(output), { recursive: true })
  await writeFile(temporaryOutput, Buffer.from(binary))
  await rename(temporaryOutput, output)
  console.log(JSON.stringify({ output, entranceSource: source, meshes, triangles, bytes: binary.byteLength }, null, 2))
} finally {
  await rm(temporaryOutput, { force: true })
  model.dispose()
}
