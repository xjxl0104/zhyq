"""Blender authoring pass over the photo-derived scaffold; saves .blend and web GLB.

blender --background --python scripts/blender/prepare_warehouse.py -- seed.glb output.blend output.glb
Re-running rebuilds the generated authoring file. Save hand edits under a different name.
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

seed_path, blend_path, glb_path = [Path(value).resolve() for value in sys.argv[sys.argv.index('--') + 1:]]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(seed_path))

scene = bpy.context.scene
scene.unit_settings.system = 'METRIC'
scene['model_status'] = 'Photo reference model; storey heights from supplied leasing image; footprint illustrative, not surveyed BIM'
scene['source_photos'] = '11 user supplied images, including DIPARK completed exterior, leasing specification and entrance photograph'
scene['authoring_tool'] = bpy.app.version_string
scene['editing_notes'] = 'Keep twinRole, floor, spaceKey custom properties and floor/system parenting for web interaction.'
scene['business_mapping'] = 'spaceKey is a local model key, not a database ID. Real business IDs remain unbound.'
scene['architectural_detail'] = 'Panel joints and reveals; curtain-wall transoms and opening lights; returned balcony railings; dock shutters, bumpers, levellers and stairs; roof fans, louvers, ducts and service walk; landscape and logistics paint geometry.'
floor_objects = [obj for obj in bpy.data.objects if obj.get('twinRole') == 'floor']
scene['storey_count'] = len(floor_objects)
scene['storey_heights_m'] = '12, 9.5, 6.6, 6.6, 6.6, 6.6, 6.6'
roof_object = next(obj for obj in bpy.data.objects if obj.get('twinRole') == 'roof')
model_top = roof_object.matrix_world.translation.z
scene['main_roof_height_m'] = model_top
scene['site_layout_status'] = 'South gate and surrounding roads are illustrative placements; no surveyed overall site plan was supplied.'

# The newly supplied completed-building photograph shows physical DIPARK lettering.
# Author the sign as editable text, then convert it to real exportable geometry.
top_floor = max(floor_objects, key=lambda obj: int(obj['floor']))
sign_parent = next(obj for obj in top_floor.children if obj.get('twinRole') == 'shell')
font_path = Path('C:/Windows/Fonts/msyh.ttc')
sign_font = bpy.data.fonts.load(str(font_path)) if font_path.exists() else None

def facade_text(name, body, size, location, material_name, parent=None,
                rotation=(math.pi / 2, 0, math.pi / 2), extrude=0.022):
    curve = bpy.data.curves.new(name, 'FONT')
    curve.body = body
    curve.size = size
    curve.extrude = extrude
    curve.bevel_depth = 0.004
    curve.bevel_resolution = 0
    curve.resolution_u = 3
    if sign_font:
        curve.font = sign_font
    obj = bpy.data.objects.new(name, curve)
    scene.collection.objects.link(obj)
    obj.location = location
    obj.rotation_euler = rotation
    curve.materials.append(bpy.data.materials[material_name])
    bpy.context.view_layer.objects.active = obj
    obj.select_set(True)
    bpy.ops.object.convert(target='MESH')
    obj.select_set(False)
    matrix = obj.matrix_world.copy()
    obj.parent = parent or sign_parent
    obj.matrix_world = matrix
    if parent is None:
        obj['floor'] = int(top_floor['floor'])
    obj['source_text'] = body
    return obj

bpy.ops.object.select_all(action='DESELECT')
di_letters = facade_text('DIPARK DI lettering', 'DI', 3.05, (48.78, -25.3, model_top - 4.45), 'teal')
bpy.context.view_layer.update()
park_start = max((di_letters.matrix_world @ vertex.co).y for vertex in di_letters.data.vertices) + .10
facade_text('DIPARK PARK lettering', 'PARK', 3.05, (48.78, park_start, model_top - 4.45), 'dark')
facade_text('DIPARK Chinese subtitle', '数智云仓产业园', .73, (48.79, -25.15, model_top - 5.25), 'dark')

# Stand-off letters above the real gate fascia, facing the south arrival road.
gate_parent = next(obj for obj in bpy.data.objects if obj.get('sitePart') == 'entrance')
gate_rotation = (math.pi / 2, 0, 0)
gate_di = facade_text('Entrance DIPARK DI', 'DI', 2.65, (13.1, -59.67, 7.13), 'teal',
                      parent=gate_parent, rotation=gate_rotation, extrude=.055)
bpy.context.view_layer.update()
gate_park_x = max((gate_di.matrix_world @ vertex.co).x for vertex in gate_di.data.vertices) + .1
gate_park = facade_text('Entrance DIPARK PARK', 'PARK', 2.65, (gate_park_x, -59.67, 7.13), 'steel',
                        parent=gate_parent, rotation=gate_rotation, extrude=.055)
bpy.context.view_layer.update()
gate_subtitle_x = max((gate_park.matrix_world @ vertex.co).x for vertex in gate_park.data.vertices) + .65
facade_text('Entrance Chinese park name', '数智云仓产业园', 1.13, (gate_subtitle_x, -59.67, 7.18), 'steel',
            parent=gate_parent, rotation=gate_rotation, extrude=.04)

# Keep each sign's material batches compact while retaining its native parent.
sign_batches = {}
for obj in list(bpy.data.objects):
    if obj.type == 'MESH' and obj.get('source_text') and obj.data.materials:
        sign_batches.setdefault((obj.parent, obj.data.materials[0]), []).append(obj)
for members in sign_batches.values():
    if len(members) < 2:
        continue
    text_bodies = [obj['source_text'] for obj in members]
    bpy.ops.object.select_all(action='DESELECT')
    for obj in members:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = members[0]
    bpy.ops.object.join()
    members[0]['source_text'] = ' / '.join(text_bodies)
bpy.ops.object.select_all(action='DESELECT')

# Organize real Blender collections without changing the GLB parent hierarchy.
collection_names = {'site': '00 Site', 'roof': f'{len(floor_objects) + 1:02d} Roof'}
for obj in list(bpy.data.objects):
    role = obj.get('twinRole')
    if role not in ('floor', 'site', 'roof'):
        continue
    name = f'{int(obj["floor"]):02d} Floor {int(obj["floor"])}' if role == 'floor' else collection_names[role]
    collection = bpy.data.collections.new(name)
    scene.collection.children.link(collection)
    for child in [obj, *obj.children_recursive]:
        for old_collection in list(child.users_collection):
            old_collection.objects.unlink(child)
        collection.objects.link(child)

# Refine PBR finishes in Blender. Tiny facade details remain batched for web performance.
for mat in bpy.data.materials:
    if not mat.use_nodes:
        continue
    bsdf = next((node for node in mat.node_tree.nodes if node.type == 'BSDF_PRINCIPLED'), None)
    if bsdf is None:
        continue
    if mat.name.startswith('glass'):
        bsdf.inputs['Roughness'].default_value = 0.20
        bsdf.inputs['Metallic'].default_value = 0.42
    elif mat.name.startswith('teal'):
        bsdf.inputs['Roughness'].default_value = 0.38
        bsdf.inputs['Metallic'].default_value = 0.18
    elif mat.name.startswith('white'):
        bsdf.inputs['Roughness'].default_value = 0.64

# A light architectural chamfer is authored as editable Blender modifiers.
for obj in list(bpy.data.objects):
    if obj.type != 'MESH' or not obj.data.materials:
        continue
    material_name = obj.data.materials[0].name
    if not material_name.startswith(('white', 'teal')):
        continue
    weld = obj.modifiers.new('Weld scaffold corners', 'WELD')
    weld.merge_threshold = 0.0001
    bevel = obj.modifiers.new('Architectural edge detail', 'BEVEL')
    bevel.width = 0.025
    bevel.segments = 1
    bevel.limit_method = 'ANGLE'
    bevel.angle_limit = math.radians(35)

# Interior systems remain editable and exportable, but are hidden for the opening exterior view.
interiors = [obj for obj in bpy.data.objects if obj.get('twinRole') in ('interior', 'fire')]

# A native Blender camera and studio rig make the source file useful immediately.
def aim(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat('-Z', 'Y').to_euler()

bpy.ops.object.camera_add(location=(176, -184, 116))
camera = bpy.context.object
camera.name = 'Presentation camera'
camera.data.type = 'PERSP'
camera.data.lens = 41
aim(camera, (0, 0, 23))
scene.camera = camera
for name, position, energy, size in [
    ('Key daylight', (-55, -70, 120), 160000, 48),
    ('Soft fill', (80, 70, 80), 65000, 70),
]:
    bpy.ops.object.light_add(type='AREA', location=position)
    light = bpy.context.object
    light.name = name
    light.data.energy = energy
    light.data.shape = 'DISK'
    light.data.size = size
    aim(light, (0, 0, 18))
scene.world = bpy.data.worlds.new('Architectural studio')
scene.world.use_nodes = True
scene.world.node_tree.nodes['Background'].inputs[0].default_value = (0.70, 0.78, 0.86, 1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value = 0.45
bpy.ops.object.light_add(type='SUN', location=(-40, -70, 110))
sun = bpy.context.object
sun.name = 'Facade relief daylight'
sun.data.energy = 1.15
sun.data.angle = math.radians(12)
aim(sun, (0, 0, 15))
scene.render.engine = 'CYCLES'
scene.cycles.samples = 24
scene.cycles.use_denoising = True
scene.render.resolution_x = 1600
scene.render.resolution_y = 1100
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.view_settings.view_transform = 'AgX'
scene.view_settings.look = 'AgX - Medium High Contrast'

# Additional native cameras make the physical detail easy to review in Blender.
detail_cameras = []
for name, position, target, scale, suffix in [
    ('Facade and dock detail', (86, -105, 42), (24, -22, 20), 79, '-detail'),
    ('Roof service detail', (80, -87, 110), (6, -1, model_top + 1), 99, '-roof'),
    ('Entrance and arrival detail', (78, -115, 31), (21, -56, 6.2), 65, '-entrance'),
]:
    bpy.ops.object.camera_add(location=position)
    detail_camera = bpy.context.object
    detail_camera.name = name
    detail_camera.data.type = 'ORTHO'
    detail_camera.data.ortho_scale = scale
    aim(detail_camera, target)
    detail_cameras.append((detail_camera, suffix))

for target in (blend_path, glb_path):
    target.parent.mkdir(parents=True, exist_ok=True)
# Export all systems (visibility is controlled at runtime), evaluated modifiers, and stable extras.
bpy.ops.export_scene.gltf(filepath=str(glb_path), export_format='GLB', export_extras=True,
    export_apply=True, export_cameras=False, export_lights=False,
    use_visible=False, use_renderable=False)

for parent in interiors:
    for obj in [parent, *parent.children_recursive]:
        obj.hide_set(True)
        obj.hide_render = True
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == 'VIEW_3D':
            area.spaces.active.region_3d.view_perspective = 'CAMERA'
            area.spaces.active.shading.type = 'MATERIAL'
bpy.ops.object.select_all(action='DESELECT')
bpy.ops.wm.save_as_mainfile(filepath=str(blend_path))
scene.render.filepath = str(blend_path.with_suffix('.png'))
bpy.ops.render.render(write_still=True)
for detail_camera, suffix in detail_cameras:
    scene.camera = detail_camera
    scene.render.filepath = str(blend_path.with_name(blend_path.stem + suffix).with_suffix('.png'))
    bpy.ops.render.render(write_still=True)
scene.camera = camera
print('WAREHOUSE_MODEL_STATS', 'floors', len(floor_objects), 'roof_height', model_top,
      'meshes', sum(obj.type == 'MESH' for obj in bpy.data.objects), 'glb_bytes', glb_path.stat().st_size)
print('WAREHOUSE_AUTHORING_COMPLETE', blend_path, glb_path)
