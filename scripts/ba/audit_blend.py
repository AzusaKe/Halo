"""Run with Blender --background --factory-startup --disable-autoexec --python.
Reads the explicit reference; never runs embedded scripts or saves the blend.
"""
import bpy
import json
import hashlib
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = pathlib.Path(r"D:\download\blender\Arisu_4.5-halo_bending_re.blend")
OUT = ROOT / ".local/ba"
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(SOURCE), load_ui=False, use_scripts=False)

def value(socket):
    v = getattr(socket, "default_value", None)
    if v is None or isinstance(v, (str, bool, int, float)):
        return v
    try:
        return list(v)
    except TypeError:
        return str(v)

def tree(t):
    return {
        "name": t.name,
        "nodes": [{"name": n.name, "type": n.bl_idname, "mute": n.mute,
                   "active_output": getattr(n, "is_active_output", None),
                   "group": getattr(getattr(n, "node_tree", None), "name", None),
                   "image": getattr(getattr(n, "image", None), "name", None),
                   "settings": {k: getattr(n, k) for k in (
                       "operation", "blend_type", "data_type", "use_clamp", "clamp",
                       "clamp_factor", "clamp_result", "interpolation_type",
                       "rotation_type", "invert", "convert_from", "convert_to",
                       "vector_type", "interpolation", "extension") if hasattr(n, k)},
                   "inputs": [{"name": s.name, "id": s.identifier,
                               "enabled": s.enabled, "default": value(s)} for s in n.inputs],
                   "ramp": [(e.position, list(e.color)) for e in n.color_ramp.elements]
                           if hasattr(n, "color_ramp") else None}
                  for n in t.nodes],
        "links": [(l.from_node.name, l.from_socket.identifier,
                   l.to_node.name, l.to_socket.identifier) for l in t.links if l.is_valid]}

audit = {"source": str(SOURCE), "sha256": hashlib.file_digest(SOURCE.open("rb"), "sha256").hexdigest(),
         "blender": bpy.app.version_string, "engine": bpy.context.scene.render.engine,
         "frame": bpy.context.scene.frame_current,
         "view_transform": bpy.context.scene.view_settings.view_transform,
         "materials": {m.name: tree(m.node_tree) for m in bpy.data.materials if m.node_tree},
         "groups": {t.name: tree(t) for t in bpy.data.node_groups},
         "images": [{"name": i.name, "size": list(i.size), "colorspace": i.colorspace_settings.name,
                     "alpha_mode": i.alpha_mode, "packed": bool(i.packed_file)} for i in bpy.data.images],
         "objects": [{"name": o.name, "type": o.type, "hide_render": o.hide_render,
                      "world": [list(r) for r in o.matrix_world],
                      "materials": [s.material.name if s.material else None for s in o.material_slots]}
                     for o in bpy.data.objects]}
(OUT / "node-audit.json").write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf8")

mapping = {"body": "CH0334_Body", "arms": "CH0334_Body_Arms", "face": "CH0334_Face",
           "hair": "CH0334_Hair", "halo": "BA_Halo_Emission", "weapon": "CH0334_Weapon01",
           "unlit": "CH0334_EyeMouth"}
profiles = {}
for part, name in mapping.items():
    m = bpy.data.materials[name]
    outputs = [n for n in m.node_tree.nodes if n.type == "OUTPUT_MATERIAL" and n.is_active_output]
    node = outputs[0].inputs["Surface"].links[0].from_node
    profiles[part] = {s.name: value(s) for s in node.inputs if not s.is_linked and isinstance(value(s), (int, float, list))}
    if part == "hair":
        q=bpy.data.objects["hair_spec_normal"].matrix_world.to_quaternion()
        profiles[part]["SpecHelperRotation"]=[q.x,q.y,q.z,q.w]
target = ROOT / "core/src/main/resources/network/azusake/halo/ba/arisu-v1.json"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text(json.dumps({"version": 1, "source_sha256": audit["sha256"], "profiles": profiles}, indent=2), encoding="utf8")
print("BA audit and exact material instance parameters:", OUT, target)
