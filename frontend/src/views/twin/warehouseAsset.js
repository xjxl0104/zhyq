import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js'
import { bindWarehouse } from './warehouseController.js'

export const WAREHOUSE_ASSET_URL = '/models/dipark-warehouse.glb?v=202510-plan-1'

export async function loadWarehouse() {
  const gltf = await new GLTFLoader().loadAsync(WAREHOUSE_ASSET_URL)
  return bindWarehouse(gltf.scene)
}
