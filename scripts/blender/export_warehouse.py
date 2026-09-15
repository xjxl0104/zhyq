"""Export the currently opened, edited .blend without rebuilding the scaffold.

blender --background models/blender/dipark-warehouse.blend --python scripts/blender/export_warehouse.py -- frontend/public/models/dipark-warehouse.glb
"""
import bpy
import sys
from pathlib import Path

output = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
floors = [obj for obj in bpy.data.objects if obj.get('twinRole') == 'floor']
if sorted(obj.get('floor') for obj in floors) != list(range(1, 8)):
    raise RuntimeError('Expected floor metadata 1..7; update the web model configuration before changing the floor count.')
for floor in floors:
    if not {'shell', 'structure', 'interior', 'fire'}.issubset({child.get('twinRole') for child in floor.children}):
        raise RuntimeError(f'{floor.name} is missing a system group')
output.parent.mkdir(parents=True, exist_ok=True)
bpy.ops.export_scene.gltf(filepath=str(output), export_format='GLB', export_extras=True,
    export_apply=True, export_cameras=False, export_lights=False,
    use_visible=False, use_renderable=False)
print('EXPORTED_EDITED_WAREHOUSE', output)
