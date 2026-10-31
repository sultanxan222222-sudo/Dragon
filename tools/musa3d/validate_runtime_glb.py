#!/usr/bin/env python3
"""Fail-closed validation for the actual Talking Musa runtime GLB."""
import json
import os
import struct
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "artifacts/Talking_Musa_Rigged.glb"
if not os.path.isfile(path) or os.path.getsize(path) < 100_000:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: file missing or unexpectedly small")

with open(path, "rb") as stream:
    magic, version, total_length = struct.unpack("<4sII", stream.read(12))
    if magic != b"glTF" or version != 2 or total_length != os.path.getsize(path):
        raise SystemExit("MUSA_RUNTIME_GLB_INVALID: bad GLB header")
    chunk_length, chunk_type = struct.unpack("<I4s", stream.read(8))
    if chunk_type != b"JSON":
        raise SystemExit("MUSA_RUNTIME_GLB_INVALID: JSON chunk missing")
    document = json.loads(stream.read(chunk_length).decode("utf-8").rstrip("\\x00 "))

nodes = document.get("nodes", [])
meshes = document.get("meshes", [])
skins = document.get("skins", [])
animations = document.get("animations", [])
node_names = {node.get("name", "") for node in nodes}
mesh_nodes = [node for node in nodes if "mesh" in node]
skinned_nodes = [node for node in nodes if "mesh" in node and "skin" in node]
required_animations = {
    "Musa_Idle_Breath_Loop", "Musa_Blink_Loop", "Musa_Hood_Hit_Return",
    "Musa_Head_Reaction", "Musa_Wave_L", "Musa_Talking_Loop", "Musa_Laugh_Loop"
}
animation_names = {animation.get("name", "") for animation in animations}
missing_animations = sorted(required_animations - animation_names)
if "Musa • anatomical base body" not in node_names:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: anatomical base body node missing")
if len(mesh_nodes) < 15 or not meshes:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: character mesh set is incomplete")
if not skins or not skinned_nodes:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: no skinned mesh is connected to a skin")
if missing_animations:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: missing animations: " + ", ".join(missing_animations))
legacy = [name for name in node_names if name.startswith(("Head • sculpted", "Neck", "Ear ", "Shoulder ", "Elbow ", "Knee ", "Hand ", "Finger "))]
if legacy:
    raise SystemExit("MUSA_RUNTIME_GLB_INVALID: legacy duplicate anatomy remains: " + ", ".join(legacy))
print("MUSA_RUNTIME_GLB_VALIDATION_OK", "nodes", len(nodes), "meshes", len(meshes),
      "skinned_nodes", len(skinned_nodes), "skins", len(skins),
      "animations", len(animation_names), "bytes", os.path.getsize(path))
