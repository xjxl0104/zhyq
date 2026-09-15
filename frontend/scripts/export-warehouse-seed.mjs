// Geometry scaffold for Blender. Run from frontend; the delivered website loads Blender's GLB.
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { GLTFExporter } from 'three/addons/exporters/GLTFExporter.js'
import { createWarehouse } from '../src/views/twin/warehouseModel.js'

globalThis.FileReader = class {
  async readAsArrayBuffer(blob) {
    this.result = await blob.arrayBuffer()
    this.onloadend?.({ target: this })
  }
}
const output = resolve(process.argv[2] || '../artifacts/warehouse-seed.glb')
const model = createWarehouse()
const helpers = []
model.root.traverse(object => { if (object.userData.runtimeOnly) helpers.push(object) })
helpers.forEach(object => object.removeFromParent())
const binary = await new GLTFExporter().parseAsync(model.root, { binary: true, onlyVisible: false })
await mkdir(resolve(output, '..'), { recursive: true })
await writeFile(output, Buffer.from(binary))
model.dispose()
console.log('Blender scaffold exported:', output, binary.byteLength, 'bytes')
