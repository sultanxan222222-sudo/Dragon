import bpy
import math
import os
import struct
import json
from mathutils import Vector

OUT = os.path.abspath("artifacts")
BASE = os.path.abspath("assets/male-base-mesh-base_male.glb")
BLEND = os.path.join(OUT, "Talking_Musa_Rigged.blend")
FBX = os.path.join(OUT, "Talking_Musa_Rigged.fbx")
GLB = os.path.join(OUT, "Talking_Musa_Rigged.glb")
PREVIEW = os.path.join(OUT, "Musa_Preview.png")
os.makedirs(OUT, exist_ok=True)

if not os.path.isfile(BASE) or os.path.getsize(BASE) < 100000:
    raise RuntimeError("CC0 anatomical base body is missing")
if not os.path.isfile(BLEND):
    raise RuntimeError("Generated Musa Blender project is missing")

bpy.ops.wm.open_mainfile(filepath=BLEND)
rig = bpy.data.objects.get("Musa_Rig")
if not rig or rig.type != "ARMATURE":
    raise RuntimeError("Existing Musa animation rig was not found")

# Keep the existing outfit, beard, hood, cap, animation actions and app-facing rig.
# Replace only the primitive exposed skin/face pieces with the anatomical base body.
remove_prefixes = (
    "Head • sculpted", "Neck", "Ear L", "Ear R", "Nose", "Eye white",
    "Iris", "Pupil", "Brow", "Mouth", "Hand ", "Finger "
)
removed = []
for obj in list(bpy.data.objects):
    if obj.type == "MESH" and obj.name.startswith(remove_prefixes):
        removed.append(obj.name)
        bpy.data.objects.remove(obj, do_unlink=True)

# Import the source mesh and its 163-bone skeleton.
bpy.ops.import_scene.gltf(filepath=BASE)
source_rig = next((o for o in bpy.context.scene.objects
                   if o.type == "ARMATURE" and o.name.startswith("base_male_body")), None)
body = bpy.data.objects.get("base_male_body")
if source_rig is None or body is None or body.type != "MESH":
    raise RuntimeError("Expected base_male_body mesh and its source armature")
if len(body.data.vertices) < 50000:
    raise RuntimeError("Anatomical base mesh has unexpectedly low topology")

# Convert imported Blender Z-up coordinates to the Y-up character coordinates
# already used by Musa/Three.js. Fit the real-world mesh to Musa's 3.18-unit height.
raw = [v.co.copy() for v in body.data.vertices]
converted = [Vector((p.x, p.z, -p.y)) for p in raw]
min_y = min(p.y for p in converted)
max_y = max(p.y for p in converted)
center_x = (min(p.x for p in converted) + max(p.x for p in converted)) * 0.5
center_z = (min(p.z for p in converted) + max(p.z for p in converted)) * 0.5
height = max_y - min_y
if not (1.4 < height < 2.2):
    raise RuntimeError("Unexpected source body height: " + str(height))
scale = 3.18 / height

def fit_point(p):
    return Vector(((p.x - center_x) * scale,
                   (p.y - min_y) * scale - 0.70,
                   (p.z - center_z) * scale))

# Source skeleton and body share the same imported coordinate system.
source_bones = source_rig.data.bones
def source_bone_point(bone_name, endpoint):
    bone = source_bones.get(bone_name)
    if bone is None:
        raise RuntimeError("Missing source bone: " + bone_name)
    return fit_point(bone.head_local if endpoint == "head" else bone.tail_local)

# Map the MakeHuman weights onto Musa's existing animation bones.
def target_bone(source_name):
    name = source_name
    side = ".L" if name.endswith(".L") else (".R" if name.endswith(".R") else "")
    stem = name[:-2] if side else name
    if stem in ("root", "pelvis"):
        return "root"
    if stem.startswith("upperleg"):
        return "thigh" + side
    if stem.startswith("lowerleg"):
        return "shin" + side
    if stem == "foot" or stem.startswith("toe"):
        return "foot" + side
    if stem.startswith(("spine", "breast", "clavicle", "shoulder")):
        return "spine"
    if stem.startswith("upperarm"):
        return "upper_arm" + side
    if stem.startswith("lowerarm"):
        return "forearm" + side
    if stem == "wrist" or stem.startswith(("finger", "metacarpal")):
        return "hand" + side
    if stem.startswith("neck"):
        return "neck"
    if stem == "head" or stem.startswith(("special", "temporalis", "levator", "orbicularis", "oculi")):
        return "head"
    if stem == "jaw" or stem.startswith(("oris", "risorius", "tongue")):
        return "jaw"
    if stem == "eye":
        return "eye" + side
    return "head"

source_group_names = {g.index: g.name for g in body.vertex_groups}
mapped_per_vertex = [dict() for _ in body.data.vertices]
arm_weight_per_vertex = {".L": [0.0] * len(body.data.vertices),
                         ".R": [0.0] * len(body.data.vertices)}
arm_source_tokens = ("upperarm", "lowerarm", "wrist", "finger", "metacarpal")
for index, vertex in enumerate(body.data.vertices):
    weights = mapped_per_vertex[index]
    for membership in vertex.groups:
        source_name = source_group_names.get(membership.group)
        if not source_name:
            continue
        mapped = target_bone(source_name)
        weights[mapped] = weights.get(mapped, 0.0) + membership.weight
        side = ".L" if source_name.endswith(".L") else (".R" if source_name.endswith(".R") else "")
        stem = source_name[:-2] if side else source_name
        if side and stem.startswith(arm_source_tokens):
            arm_weight_per_vertex[side][index] += membership.weight

# Rotate the source A-pose arms into Musa's lowered sleeve silhouette before binding.
# This is a weighted deformation, so the shoulder transition remains smooth.
for side in (".L", ".R"):
    src_shoulder = source_bone_point("upperarm01" + side, "head")
    src_wrist = source_bone_point("wrist" + side, "tail")
    dst_upper = rig.data.bones.get("upper_arm" + side)
    dst_hand = rig.data.bones.get("hand" + side)
    if dst_upper is None or dst_hand is None:
        raise RuntimeError("Musa rig is missing arm/hand target bones for " + side)
    dst_shoulder = dst_upper.head_local.copy()
    dst_wrist = dst_hand.tail_local.copy()
    src_direction = src_wrist - src_shoulder
    dst_direction = dst_wrist - dst_shoulder
    if src_direction.length < 0.01 or dst_direction.length < 0.01:
        raise RuntimeError("Invalid arm bone span for " + side)
    rotation = src_direction.normalized().rotation_difference(dst_direction.normalized())
    weights = arm_weight_per_vertex[side]
    for i, p in enumerate(converted):
        w = min(1.0, weights[i])
        if w <= 0.001:
            continue
        moved = dst_shoulder + rotation @ (p - src_shoulder)
        converted[i] = p.lerp(moved, w)

for vertex, point in zip(body.data.vertices, converted):
    vertex.co = point
body.data.update()

# Discard the source armature modifier; the final mesh uses Musa_Rig and remapped weights.
for modifier in list(body.modifiers):
    body.modifiers.remove(modifier)
for group in list(body.vertex_groups):
    body.vertex_groups.remove(group)
target_groups = {}
for vertex_index, weights in enumerate(mapped_per_vertex):
    total = sum(max(0.0, w) for w in weights.values())
    if total <= 1e-8:
        # The source is expected to be fully skinned; a fallback prevents unweighted islands.
        weights = {"root": 1.0}
        total = 1.0
    for bone_name, weight in weights.items():
        if weight <= 0.0001:
            continue
        if bone_name not in rig.data.bones:
            raise RuntimeError("Weight map references absent Musa bone: " + bone_name)
        group = target_groups.get(bone_name)
        if group is None:
            group = body.vertex_groups.new(name=bone_name)
            target_groups[bone_name] = group
        group.add([vertex_index], min(1.0, weight / total), "REPLACE")

# Use Musa's authored skin material and keep the source UVs for later texture refinement.
skin = bpy.data.materials.get("Skin • warm natural")
if skin is None:
    raise RuntimeError("Musa skin material missing")
body.data.materials.clear()
body.data.materials.append(skin)
for poly in body.data.polygons:
    poly.material_index = 0
body.name = "Musa • anatomical base body"
body["Source"] = "Innerscene / MakeHuman male base mesh"
body["License"] = "CC0"
body["Topology"] = str(len(body.data.vertices)) + " vertices; 109k source triangles"
body["Rig"] = "Musa_Rig (163-bone source weights remapped to legacy animation skeleton)"
body["Integrated"] = True
body.parent = rig
body.matrix_world.identity()
arm_mod = body.modifiers.new("Musa Anatomical Body Deform", "ARMATURE")
arm_mod.object = rig
arm_mod.use_vertex_groups = True

# Remove source-only helper objects; keep the full anatomical body mesh.
for obj in list(bpy.context.scene.objects):
    if obj == source_rig:
        bpy.data.objects.remove(obj, do_unlink=True)
    elif obj.type == "MESH" and obj != body and (
        obj.name.startswith("base_male_body.high-poly") or obj.name == "Icosphere"
    ):
        bpy.data.objects.remove(obj, do_unlink=True)

# Validate outfit and Musa identity layers remain in the scene.
required_kept = ("Jacket torso", "Upper sleeve L", "Upper sleeve R",
                 "Beard • sculpted jaw shell", "Black beanie", "Hood shell")
scene_names = {o.name for o in bpy.data.objects}
missing = [prefix for prefix in required_kept
           if not any(name.startswith(prefix) for name in scene_names)]
if missing:
    raise RuntimeError("Character identity/outfit layer missing after integration: " + ", ".join(missing))
if len(body.data.vertices) < 50000 or len(target_groups) < 10:
    raise RuntimeError("Integrated body topology or skin weights are incomplete")

# Re-render the final integrated character for visual inspection.
scene = bpy.context.scene
scene.render.filepath = PREVIEW
scene.render.resolution_percentage = 100
bpy.ops.render.render(write_still=True)
if not os.path.isfile(PREVIEW) or os.path.getsize(PREVIEW) < 10000:
    raise RuntimeError("Integrated character preview render failed")

# Save integrated scene, then export the runtime GLB and artist FBX.
bpy.ops.object.select_all(action="DESELECT")
rig.select_set(True)
body.select_set(True)
for obj in bpy.context.scene.objects:
    if obj.type == "MESH":
        obj.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.wm.save_as_mainfile(filepath=BLEND)
bpy.ops.export_scene.gltf(filepath=GLB, export_format="GLB", use_selection=True,
    export_animations=True, export_skins=True, export_morph=True,
    export_materials="EXPORT", export_image_format="AUTO", export_yup=False)
bpy.ops.export_scene.fbx(filepath=FBX, use_selection=True,
    object_types={"ARMATURE", "MESH"}, add_leaf_bones=False,
    use_mesh_modifiers=False, bake_anim=True, bake_anim_use_all_actions=True,
    path_mode="COPY")

for path in (GLB, FBX):
    if not os.path.isfile(path) or os.path.getsize(path) < 100000:
        raise RuntimeError("Integrated export is missing or too small: " + path)

# Inspect the GLB JSON chunk without disturbing the saved Blender scene.
with open(GLB, "rb") as stream:
    header = stream.read(12)
    magic, version, total_length = struct.unpack("<4sII", header)
    if magic != b"glTF" or version != 2 or total_length < 100000:
        raise RuntimeError("Integrated GLB header validation failed")
    chunk_length, chunk_type = struct.unpack("<I4s", stream.read(8))
    if chunk_type != b"JSON":
        raise RuntimeError("Integrated GLB JSON chunk missing")
    document = json.loads(stream.read(chunk_length).decode("utf-8").rstrip("\\x00 "))
node_names = {node.get("name", "") for node in document.get("nodes", [])}
if "Musa • anatomical base body" not in node_names:
    raise RuntimeError("Integrated anatomical body missing from exported GLB")
if not document.get("skins"):
    raise RuntimeError("Integrated GLB has no skin rig")
actions = {a.name for a in bpy.data.actions}
expected_actions = {
    "Musa_Idle_Breath_Loop", "Musa_Blink_Loop", "Musa_Hood_Hit_Return",
    "Musa_Head_Reaction", "Musa_Wave_L", "Musa_Talking_Loop", "Musa_Laugh_Loop"
}
if not expected_actions.issubset(actions):
    raise RuntimeError("Existing Musa animation actions were lost during integration")
print("MUSA_ANATOMICAL_BODY_INTEGRATION_OK",
      len(body.data.vertices), len(body.data.polygons), len(target_groups),
      len(rig.data.bones), len(actions), len(removed), os.path.getsize(GLB))
