import bpy, os
from pathlib import Path

source = Path("assets/human-base-rigged.glb")
out = Path("artifacts")
out.mkdir(exist_ok=True)
if not source.is_file() or source.stat().st_size < 100000:
    raise RuntimeError("CC0 base mesh download missing or unexpectedly small")

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(source))

meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
rigs = [o for o in bpy.context.scene.objects if o.type == "ARMATURE"]
if not meshes:
    raise RuntimeError("Base mesh GLB imported without mesh objects")
if not rigs:
    raise RuntimeError("Base mesh GLB imported without an armature")

bones = sorted({b.name for rig in rigs for b in rig.data.bones})
if len(bones) < 30:
    raise RuntimeError("Unexpectedly small base skeleton: " + str(len(bones)))

lines = [
    "MUSA_BASE_BODY_SOURCE_VALIDATION_OK",
    "Source: Innerscene / MakeHuman MPFB",
    "License: CC0",
    "Format: GLB",
    "Mesh objects: " + str(len(meshes)),
    "Armatures: " + str(len(rigs)),
    "Total vertices: " + str(sum(len(o.data.vertices) for o in meshes)),
    "Triangles: " + str(sum(sum(len(p.vertices)-2 for p in o.data.polygons) for o in meshes)),
    "Bones: " + str(len(bones)),
    "Bone names:",
    *bones,
]
(out / "Musa_BaseBody_Inspection.txt").write_text("\\n".join(lines) + "\\n", encoding="utf-8")
bpy.ops.wm.save_as_mainfile(filepath=str(out / "Musa_BaseBody_Source.blend"))
print("\\n".join(lines))
