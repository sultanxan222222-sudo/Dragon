import bpy
from pathlib import Path

source = Path("assets/male-base-mesh-base_male.glb")
out = Path("artifacts")
out.mkdir(exist_ok=True)
if not source.is_file() or source.stat().st_size < 100000:
    raise RuntimeError("CC0 male base mesh download missing or unexpectedly small")

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(source))

meshes = [o for o in bpy.context.scene.objects if o.type == "MESH"]
rigs = [o for o in bpy.context.scene.objects if o.type == "ARMATURE"]
if not meshes or not rigs:
    raise RuntimeError("Imported base mesh must contain meshes and an armature")

bones = sorted({b.name for rig in rigs for b in rig.data.bones})
if len(bones) < 150:
    raise RuntimeError("Expected 163-bone base skeleton, got " + str(len(bones)))

lines = [
    "MUSA_MALE_BASE_BODY_VALIDATION_OK",
    "Source: Innerscene / MakeHuman CC0 male base mesh",
    "License: CC0",
    "Format: GLB",
    "Mesh objects: " + str(len(meshes)),
    "Armatures: " + str(len(rigs)),
    "Total vertices: " + str(sum(len(o.data.vertices) for o in meshes)),
    "Triangles: " + str(sum(sum(len(p.vertices)-2 for p in o.data.polygons) for o in meshes)),
    "Bones: " + str(len(bones)),
    "Objects and bounds:",
    *[
        f"{o.name} | type={o.type} | location={tuple(round(v, 5) for v in o.location)} | dimensions={tuple(round(v, 5) for v in o.dimensions)} | local_bounds={((tuple(round(min(v.co[i] for v in o.data.vertices), 5) for i in range(3)), tuple(round(max(v.co[i] for v in o.data.vertices), 5) for i in range(3))) if o.type == 'MESH' else 'n/a')} | vertices={len(o.data.vertices) if o.type == 'MESH' else 0} | groups={','.join(g.name for g in o.vertex_groups)}"
        for o in bpy.context.scene.objects
    ],
    "Bone names:",
    *bones,
]
(out / "Musa_BaseBody_Inspection.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
bpy.ops.wm.save_as_mainfile(filepath=str(out / "Musa_BaseBody_Source.blend"))
print("\n".join(lines))
