"""Blender 4.5.6 --factory-startup --background --disable-autoexec --python this_file.
Only an in-memory copy of outline groups is changed; source blend is never saved.
"""
import bpy
import json
import pathlib
import math
import struct
import zlib
import zipfile
import sys
from mathutils import Vector

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / ".local/ba/delivery"
OUT.mkdir(parents=True, exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=r"D:\download\blender\Arisu_4.5-halo_bending_re.blend", load_ui=False, use_scripts=False)
scene=bpy.context.scene

def png_color(rgb):
    def chunk(name,data): return struct.pack('>I',len(data))+name+data+struct.pack('>I',zlib.crc32(name+data)&0xffffffff)
    def srgb(v): return 12.92*v if v<=.0031308 else 1.055*v**(1/2.4)-.055
    pixels=bytes([0]+[round(min(1,max(0,srgb(v)))*255) for v in rgb]+[255])
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(pixels))+chunk(b'IEND',b'')

textures={
    'CH0334_Body':'ch0334_body', 'CH0334_Body_Arms':'ch0334_body',
    'CH0334_Face':'ch0334_face', 'CH0334_Eyebrow':'ch0334_face',
    'CH0334_EyeMouth':'ch0334_eyemouth', 'CH0334_Hair':'ch0334_hair',
    'BA_Halo_Emission':'ch0334_halo', 'CH0334_Weapon01':'ch0334_weapon01',
    'Mouth':'ch0334_eyemouth',
}
parts={'CH0334_Body':'body','CH0334_Body_Arms':'arms','CH0334_Face':'face',
    'CH0334_Eyebrow':'eyebrow','CH0334_EyeMouth':'eyemouth','CH0334_Hair':'hair',
    'BA_Halo_Emission':'halo','CH0334_Weapon01':'weapon01','Mouth':'mouth',
    'body_outline':'body_outline','face_outline':'face_outline','hair_outline':'hair_outline','weapon_outline':'weapon_outline'}

# The active node graph joins original mesh and outline instances. Realize those
# instances at the active geometry output for export, rather than inventing a shell.
for obj in scene.objects:
    if obj.type!='MESH': continue
    for modifier in obj.modifiers:
        if modifier.type!='NODES' or not modifier.node_group: continue
        group=modifier.node_group.copy(); modifier.node_group=group
        output=next(n for n in group.nodes if n.type=='GROUP_OUTPUT' and n.is_active_output)
        socket=next(s for s in output.inputs if s.type=='GEOMETRY')
        if socket.is_linked:
            source=socket.links[0].from_socket
            node=group.nodes.new('GeometryNodeRealizeInstances')
            group.links.new(source,node.inputs['Geometry']); group.links.new(node.outputs['Geometry'],socket)

scene.frame_set(scene.frame_current)
deps=bpy.context.evaluated_depsgraph_get()
triangles={}; inventory=[]; bounds=[]
for obj in scene.objects:
    if obj.type!='MESH' or obj.hide_render or not obj.name.startswith('CH0334_'): continue
    evaluated=obj.evaluated_get(deps)
    mesh=evaluated.to_mesh(preserve_all_data_layers=True,depsgraph=deps)
    if not mesh: continue
    mesh.calc_loop_triangles()
    normal_matrix=evaluated.matrix_world.to_3x3().inverted().transposed()
    uv=mesh.uv_layers.active
    counts={}
    for tri in mesh.loop_triangles:
        material=mesh.materials[tri.material_index]
        name=material.name if material else 'missing'
        if name not in parts: raise RuntimeError('Unmapped actual material '+name)
        rows=[]
        for loop_id in tri.loops:
            loop=mesh.loops[loop_id]
            p=evaluated.matrix_world@mesh.vertices[loop.vertex_index].co
            n=(normal_matrix@mesh.corner_normals[loop_id].vector).normalized()
            t=uv.data[loop_id].uv if uv else Vector((0,0))
            rows.append(((p.x,p.z,-p.y),(t.x,t.y),(n.x,n.z,-n.y)))
            bounds.append((p.x,p.z,-p.y))
        triangles.setdefault(name,[]).append(rows)
        counts[name]=counts.get(name,0)+1
    inventory.append({'object':obj.name,'triangles':counts})
    evaluated.to_mesh_clear()

base={'pack.mcmeta':json.dumps({'pack':{'pack_format':15,'description':'Arisu actual Blender mesh BA verification base v1'}}).encode()}
overlay={'pack.mcmeta':json.dumps({'pack':{'pack_format':15,'description':'Halo BA v1 raw Mask/Spac companions only'}}).encode()}
for name, output in [('CH0334_Body.png','ch0334_body.png'),('CH0334_Face.png','ch0334_face.png'),
    ('CH0334_EyeMouth.png','ch0334_eyemouth.png'),('CH0334_Hair.png','ch0334_hair.png'),
    ('CH0334_Halo.png','ch0334_halo.png'),('CH0334_Weapon01.png','ch0334_weapon01.png')]:
    image=bpy.data.images[name]
    if not image.packed_file: raise RuntimeError('Expected actual packed source PNG '+name)
    base['assets/model/textures/'+output]=bytes(image.packed_file.data)
for name, output in [('CH0334_Body_Mask.png','ch0334_body_mask.png'),('CH0334_Hair_Mask.png','ch0334_hair_mask.png'),
    ('CH0334_Hair_Spec.png','ch0334_hair_spac.png'),('CH0334_Weapon01_Mask.png','ch0334_weapon01_mask.png')]:
    overlay['assets/model/textures/'+output]=bytes(bpy.data.images[name].packed_file.data)

layers=[]
for material, tris in triangles.items():
    part=parts[material]; stem='ch0334_'+part
    # Weapon is outside the user's revised visual acceptance scope.
    if part in ('weapon01','weapon_outline'): continue
    is_outline='outline' in part
    texture=textures.get(material,stem)
    if is_outline:
        m=bpy.data.materials[material]
        output=next(n for n in m.node_tree.nodes if n.type=='OUTPUT_MATERIAL' and n.is_active_output)
        source=output.inputs['Surface'].links[0].from_node
        color=list(source.inputs['Color'].default_value)[:3] if 'Color' in source.inputs else [0,0,0]
        base['assets/model/textures/'+texture+'.png']=png_color(color)
    lines=['# Explicit Arisu blend, world mesh axes (x,z,-y), evaluated outline nodes, corner normals.']
    index=1
    for tri in tris:
        for p,uv,n in tri:
            lines.extend(['v '+' '.join(format(v,'.9g') for v in p),
                'vt '+' '.join(format(v,'.9g') for v in uv), 'vn '+' '.join(format(v,'.9g') for v in n)])
        lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in range(index,index+3))); index+=3
    base['assets/model/models/'+stem+'.obj']='\n'.join(lines).encode()
    layers.append({'primitives':[{'type':'mesh','model':'model:models/'+stem+'.obj',
        'texture':'model:textures/'+texture+'.png','preserve_proportions':True,
        'scale':1.6,'glowing':False,'material':{'double_sided':not is_outline and part!='weapon01'}}]})
definition={'id':'model:arisu_ba','display_in_invisible':True,'orientation_mode':'sync','layers':[{'position':[0,-1.65,0],'children':layers}],
    'positioning':{'offset':[0,0,0],'scale':1},'damping':{'linearFactor':1,'angularFactor':1,'maxLinearDistance':0,'maxAngularDegrees':0}}
base['assets/model/halo_definitions/arisu_ba.json']=json.dumps(definition,indent=2).encode()
for filename,entries in [('Arisu BA Base v1.zip',base),('Arisu BA Companions v1.zip',overlay)]:
    with zipfile.ZipFile(OUT/filename,'w',zipfile.ZIP_DEFLATED) as archive:
        for path,data in entries.items(): archive.writestr(path,data)
manifest={'source':bpy.data.filepath,'axes':'Blender world(x,y,z) -> OBJ/MC(x,z,-y), right handed',
    'frame':scene.frame_current,'inventory':inventory,'bounds':{'min':[min(p[i] for p in bounds) for i in range(3)],
    'max':[max(p[i] for p in bounds) for i in range(3)]},'triangles':{k:len(v) for k,v in triangles.items()},
    'outlines':'Active outline output instances realized in memory; flipped winding retained',
    'limitations':'Static pose OBJ; no skeleton animation export. Existing runtime part transforms can rotate the entire group.'}
(OUT/'fixture-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
print('Exported actual split material fixture:',manifest)

# Reference image in a separate safe Blender process. Do not save the original.
scene.render.engine='BLENDER_EEVEE_NEXT'
scene.render.resolution_x=960; scene.render.resolution_y=960; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'; scene.render.filepath=str(OUT/'blender-reference.png')
if scene.camera and '--skip-render' not in sys.argv:
    bpy.ops.render.render(write_still=True)
else:
    print('Reference rendering skipped or no scene camera; existing reference retained')
