import bpy, math, os
from mathutils import Vector
from math import sin, cos, pi

OUT=os.path.abspath("artifacts")
os.makedirs(OUT,exist_ok=True)
BLEND=os.path.join(OUT,"Talking_Musa_Rigged.blend")
FBX=os.path.join(OUT,"Talking_Musa_Rigged.fbx")
GLB=os.path.join(OUT,"Talking_Musa_Rigged.glb")

# Clean scene
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
for datablocks in (bpy.data.meshes,bpy.data.curves,bpy.data.armatures,bpy.data.actions):
    pass

def mat(name,color,rough=.72,metal=0):
    m=bpy.data.materials.new(name); m.diffuse_color=(*color,1); m.roughness=rough; m.metallic=metal
    return m
skin=mat("Skin • warm natural",(.48,.27,.17))
jacket=mat("Jacket • olive fabric",(.16,.20,.105))
jacket_dark=mat("Jacket seams",(.075,.095,.05))
black=mat("Black knit cap",(.018,.022,.025))
beard=mat("Dense dark beard",(.035,.027,.022))
white=mat("Eye whites",(.88,.82,.69))
iris=mat("Dark hazel eyes",(.12,.075,.035))
pupil=mat("Pupils",(.008,.006,.004))
mouthmat=mat("Mouth",(.075,.018,.015))
zipmat=mat("Zipper",(.48,.43,.30),.35,.45)

parts=[]

def make_mesh(name, verts, faces, material, bone, smooth=True):
    mesh=bpy.data.meshes.new(name+'_QuadTopology')
    mesh.from_pydata(verts,[],faces); mesh.materials.append(material); mesh.update()
    obj=bpy.data.objects.new(name,mesh); bpy.context.collection.objects.link(obj)
    if smooth:
        for poly in mesh.polygons: poly.use_smooth=True
    parts.append((obj,bone)); return obj

def uv(name,loc,scale,material,bone,seg=32,rings=20):
    # Hand-built quad-dominant organic surface; no Blender sphere primitives.
    sx,sy,sz=scale; cx,cy,cz=loc
    verts=[]; faces=[]
    nr=max(10,rings); ns=max(16,seg)
    for j in range(1,nr):
        phi=-pi/2 + pi*j/nr
        yshape=sin(phi)
        radial=max(0.0,cos(phi))
        for i in range(ns):
            th=2*pi*i/ns
            # Superelliptic cross-section: flatter planes and more readable game-art silhouettes,
            # instead of a mathematically perfect sphere. Keep quad loops for deformation.
            rawx=cos(th); rawz=sin(th)
            x=(1 if rawx>=0 else -1)*abs(rawx)**0.82
            z=(1 if rawz>=0 else -1)*abs(rawz)**0.82
            front=max(0.0,z)
            # The front plane is flatter; the rear stays rounder. Tiny name-based shaping
            # gives head, beard, clothing and facial parts distinct silhouettes.
            depth=1.0-.16*front
            width=1.0-.045*yshape
            if name.startswith("Eye") or name.startswith("Iris") or name.startswith("Pupil"):
                depth *= .62
            if name.startswith("Brow"):
                depth *= .82
            if name.startswith("Nose"):
                depth *= 1.12
            verts.append((cx+sx*x*radial*width, cy+sy*yshape, cz+sz*z*radial*depth))
    bottom=len(verts); verts.append((cx,cy-sy,cz))
    top=len(verts); verts.append((cx,cy+sy,cz))
    for j in range(nr-2):
        row=j*ns; nxt=(j+1)*ns
        for i in range(ns):
            ni=(i+1)%ns; faces.append((row+i,row+ni,nxt+ni,nxt+i))
    for i in range(ns):
        ni=(i+1)%ns; faces.append((bottom,ni,i))
        row=(nr-2)*ns; faces.append((top,row+i,row+ni))
    obj=make_mesh(name,verts,faces,material,bone)
    sub=obj.modifiers.new('Quad Surface Smoothing','SUBSURF'); sub.levels=1; sub.render_levels=2
    return obj

def loft(name, profile, material, bone, sides=32, subd=1):
    # Anatomical quad-loop surface. profile rows: y, half_width, half_depth, z_center.
    verts=[]; faces=[]
    for y,w,d,zc in profile:
        for i in range(sides):
            a=2*pi*i/sides
            rawx=cos(a); rawz=sin(a)
            # Superellipse loops create readable planar regions while retaining quad topology.
            x=(1 if rawx>=0 else -1)*abs(rawx)**.88
            z=(1 if rawz>=0 else -1)*abs(rawz)**.88
            front=max(0.0,z)
            verts.append((w*x, y, zc+d*z*(1.0-.13*front)))
    for j in range(len(profile)-1):
        for i in range(sides):
            ni=(i+1)%sides
            faces.append((j*sides+i,j*sides+ni,(j+1)*sides+ni,(j+1)*sides+i))
    faces.append(tuple(reversed(tuple(range(sides)))))
    last=(len(profile)-1)*sides
    faces.append(tuple(last+i for i in range(sides)))
    obj=make_mesh(name,verts,faces,material,bone)
    if subd:
        mod=obj.modifiers.new("Anatomical Quad Subdivision","SUBSURF")
        mod.levels=subd; mod.render_levels=subd+1
    return obj

def head_surface():
    # Chin, jaw, cheekbones, temples and crown are shaped by the profile rather than a sphere.
    profile=[
        # Denser support rings define the chin, jaw angle, cheek plane, temples and crown.
        (1.635,.065,.095,.075),(1.665,.125,.145,.065),(1.705,.205,.195,.050),
        (1.755,.255,.225,.035),(1.815,.292,.250,.025),(1.885,.322,.270,.018),
        (1.965,.350,.292,.012),(2.055,.370,.305,.008),(2.145,.378,.310,.004),
        (2.225,.370,.305,.002),(2.295,.352,.292,-.002),(2.355,.325,.276,-.004),
        (2.405,.285,.245,-.005),(2.445,.230,.205,-.005),(2.475,.155,.150,-.005),
        (2.495,.075,.090,-.005),(2.505,.025,.035,-.005)
    ]
    return loft("Head • sculpted quad topology",profile,skin,"head",40,2)

def torso_surface():
    # A continuous jacket silhouette: pelvis, waist, chest, shoulders and upper collar.
    # Keep this as a quad-loop surface; do not replace it with stacked primitive objects.
    profile=[
        # Tailored silhouette with shoulder/chest/waist support loops for cleaner deformation.
        (.32,.255,.185,0),(.36,.295,.210,0),(.42,.350,.235,0),
        (.52,.385,.250,0),(.66,.405,.260,0),(.82,.425,.270,0),
        (.98,.445,.282,0),(1.12,.470,.292,0),(1.24,.495,.292,0),
        (1.33,.505,.285,0),(1.39,.495,.275,0),(1.46,.455,.250,0),
        (1.52,.385,.225,0),(1.57,.305,.190,0),(1.62,.205,.155,0)
    ]
    return loft("Jacket torso • tailored quad topology",profile,jacket,"spine",40,2)

def cyl(name,a,b,r1,r2,material,bone,sides=24):
    # Quad-loop anatomical tube, with flatter front/back planes and dense support rings.
    a,b=Vector(a),Vector(b); axis=(b-a).normalized()
    ref=Vector((0,0,1)) if abs(axis.z)<.92 else Vector((0,1,0))
    u=axis.cross(ref).normalized(); v=axis.cross(u).normalized()
    # Extra rings preserve shape around elbows, wrists, knees, ankles and finger joints.
    rings=[(0.0,r1*.84),(0.035,r1*.94),(0.08,r1),(0.16,r1*.99),
           (0.30,r1*.95),(0.48,r1*.86),(0.68,r2*1.06),
           (0.84,r2),(0.93,r2*.98),(0.975,r2*.93),(1.0,r2*.86)]
    verts=[]; faces=[]; n=max(16, int(sides))
    for t,r in rings:
        center=a+(b-a)*t
        for i in range(n):
            ang=2*pi*i/n
            # Superellipse-like section: less circular, more game-art sculpted.
            rawx=cos(ang); rawy=sin(ang)
            x=(1 if rawx>=0 else -1)*abs(rawx)**.88
            y=(1 if rawy>=0 else -1)*abs(rawy)**.88
            p=center+r*(x*u+y*v)
            verts.append(tuple(p))
    for j in range(len(rings)-1):
        for i in range(n):
            ni=(i+1)%n; faces.append((j*n+i,j*n+ni,(j+1)*n+ni,(j+1)*n+i))
    faces.append(tuple(reversed(tuple(range(n)))))
    faces.append(tuple((len(rings)-1)*n+i for i in range(n)))
    return make_mesh(name,verts,faces,material,bone)

# Torso / jacket silhouette and hood
torso_surface()
uv("Shoulder left",(-.39,1.43,0),(.30,.24,.29),jacket,"spine")
uv("Shoulder right",(.39,1.43,0),(.30,.24,.29),jacket,"spine")
uv("Neck",(0,1.76,0),(.17,.20,.17),skin,"neck")
head_surface()
uv("Ear L",(-.39,2.10,0),(.085,.13,.075),skin,"head")
uv("Ear R",(.39,2.10,0),(.085,.13,.075),skin,"head")
# Sculpted beard shell follows cheek, jaw and chin contours with quad support loops.
beard_profile=[
    (1.645,.105,.105,.205),(1.675,.175,.145,.205),(1.715,.225,.165,.205),
    (1.765,.265,.178,.205),(1.825,.292,.180,.205),(1.885,.300,.168,.205),
    (1.935,.278,.145,.205),(1.975,.235,.112,.205),(2.005,.175,.080,.205),
    (2.025,.105,.050,.205)
]
loft("Beard • sculpted jaw shell",beard_profile,beard,"head",40,2)
uv("Mustache L",(-.105,1.99,.315),(.16,.068,.055),beard,"head")
uv("Mustache R",(.105,1.99,.315),(.16,.068,.055),beard,"head")
# facial features
for side in (-1,1):
    uv("Eye white "+str(side),(side*.145,2.20,.300),(.075,.052,.035),white,"eye."+("L" if side<0 else "R"),24,16)
    uv("Iris "+str(side),(side*.145,2.195,.332),(.030,.034,.014),iris,"eye."+("L" if side<0 else "R"),20,12)
    uv("Pupil "+str(side),(side*.145,2.195,.344),(.014,.021,.008),pupil,"eye."+("L" if side<0 else "R"),16,10)
    uv("Brow "+str(side),(side*.15,2.29,.29),(.105,.028,.04),beard,"head",20,12)
uv("Nose",(0,2.08,.34),(.075,.09,.095),skin,"head")
uv("Mouth",(0,1.99,.303),(.075,.025,.018),mouthmat,"jaw",20,12)
# cap
uv("Black beanie",(0,2.49,0),(.405,.19,.355),black,"head",40,24)
uv("Beanie rim",(0,2.40,.01),(.407,.065,.36),black,"head",40,16)
# hood: sculpted padded ring/back shell, controlled by separate hood bone
uv("Hood shell",(0,2.12,-.19),(.49,.54,.23),jacket,"hood",40,28)
uv("Hood left rim",(-.36,2.12,.015),(.12,.40,.12),jacket,"hood")
uv("Hood right rim",(.36,2.12,.015),(.12,.40,.12),jacket,"hood")
# jacket details
cyl("Front zipper",(0,.58,.294),(0,1.55,.294),.012,.012,zipmat,"spine",12)
for y in (.86,1.12):
    uv("Pocket button L",(-.30,y,.27),(.025,.025,.018),zipmat,"spine",12,8)
    uv("Pocket button R",(.30,y,.27),(.025,.025,.018),zipmat,"spine",12,8)
# arms and hands
for s,label in [(-1,"L"),(1,"R")]:
    shoulder=(s*.43,1.43,0); elbow=(s*.61,.99,.02); wrist=(s*.68,.59,.08); hand=(s*.69,.45,.12)
    cyl("Upper sleeve "+label,shoulder,elbow,.19,.145,jacket,"upper_arm."+label)
    uv("Elbow "+label,elbow,(.15,.15,.15),jacket,"forearm."+label)
    cyl("Lower sleeve "+label,elbow,wrist,.145,.105,jacket,"forearm."+label)
    uv("Hand "+label,hand,(.105,.15,.075),skin,"hand."+label)
    for k in range(4):
        x=s*.69+(k-1.5)*.035
        cyl("Finger "+label+str(k),(x,.43,.16),(x,.35,.16),.018,.014,skin,"hand."+label,12)
# legs and boots
for s,label in [(-1,"L"),(1,"R")]:
    hip=(s*.22,.48,0); knee=(s*.23,-.05,.01); ankle=(s*.23,-.53,.015)
    cyl("Trouser upper "+label,hip,knee,.20,.15,jacket,"thigh."+label)
    uv("Knee "+label,knee,(.155,.17,.16),jacket,"shin."+label)
    cyl("Trouser lower "+label,knee,ankle,.15,.11,jacket,"shin."+label)
    uv("Boot "+label,(s*.23,-.64,.10),(.16,.13,.27),black,"foot."+label)


# Unity-oriented texture preparation: UVs, tangent-space normal detail and mask map.
# Keep this procedural and lightweight; no shell/fur shader is used.
def make_texture(name, pixels, size=512):
    from array import array
    image=bpy.data.images.new(name,width=size,height=size,alpha=False, float_buffer=False)
    image.colorspace_settings.name='Non-Color'
    image.pixels.foreach_set(pixels)
    image.file_format='PNG'
    filepath=os.path.join(OUT,name)
    image.filepath_raw=filepath
    image.save()
    return image

def prepare_unity_textures():
    from array import array
    size=512
    normal=array('f')
    mask=array('f')
    for y in range(size):
        v=y/size
        for x in range(size):
            u=x/size
            # Fine directional micro-relief resembling short hair/fabric fibres.
            nx=0.5 + 0.022*sin(u*2*pi*83 + 0.7*sin(v*2*pi*7))
            ny=0.5 + 0.020*cos(v*2*pi*97 + 0.6*sin(u*2*pi*5))
            normal.extend((nx,ny,0.998,1.0))
            # Unity-style packed channels per the supplied brief:
            # R metallic (non-metal by default), G baked-AO approximation,
            # B smoothness (low for matte character surfaces).
            seam=max(0.0, 1.0-abs(sin(u*2*pi*9))*34.0)
            ao=max(0.62, 0.88 - seam*0.12 - 0.025*(0.5+0.5*sin(v*2*pi*13)))
            mask.extend((0.0,ao,0.22,1.0))
    normal_img=make_texture('Musa_Normal.png',normal,size)
    mask_img=make_texture('Musa_Mask.png',mask,size)

    # Smart UV unwrap every mesh independently; the image files are also delivered
    # separately so Unity artists can replace/atlas them later without rebuilding the rig.
    for obj,_bone in parts:
        if obj.type!='MESH' or not obj.data.polygons:
            continue
        bpy.ops.object.select_all(action='DESELECT')
        obj.select_set(True)
        bpy.context.view_layer.objects.active=obj
        if not obj.data.uv_layers:
            obj.data.uv_layers.new(name='UVMap')
        bpy.ops.object.mode_set(mode='EDIT')
        bpy.ops.mesh.select_all(action='SELECT')
        bpy.ops.uv.smart_project(island_margin=0.018)
        bpy.ops.object.mode_set(mode='OBJECT')
        obj['Unity_UV_Ready']=True

    # Hook the tangent-space normal map into the character materials. The packed
    # mask remains an explicit Unity delivery texture; material roughness stays
    # per-material in Blender so glossy eyes/nose are not flattened by one shared map.
    material_list=[skin,jacket,jacket_dark,black,beard,white,iris,pupil,mouthmat,zipmat]
    smoothness={
        'Skin • warm natural':0.32,'Jacket • olive fabric':0.18,
        'Jacket seams':0.20,'Black knit cap':0.16,'Dense dark beard':0.12,
        'Eye whites':0.72,'Dark hazel eyes':0.92,'Pupils':0.98,
        'Mouth':0.55,'Zipper':0.82
    }
    for material in material_list:
        material.use_nodes=True
        nodes=material.node_tree.nodes
        links=material.node_tree.links
        principled=next((n for n in nodes if n.type=='BSDF_PRINCIPLED'),None)
        if not principled:
            continue
        # Ensure rendered/exported materials use the authored character palette.
        # diffuse_color alone is only a viewport hint once nodes are enabled.
        principled.inputs['Base Color'].default_value=material.diffuse_color
        principled.inputs['Roughness'].default_value=material.roughness
        principled.inputs['Metallic'].default_value=material.metallic
        normal_tex=nodes.new('ShaderNodeTexImage')
        normal_tex.name='Musa Tangent Normal (OpenGL Y+)'
        normal_tex.label='Musa_Normal.png • Tangent Space • OpenGL Y+'
        normal_tex.image=normal_img
        normal_tex.interpolation='Linear'
        normal_tex.image.colorspace_settings.name='Non-Color'
        normal_node=nodes.new('ShaderNodeNormalMap')
        normal_node.space='TANGENT'
        normal_node.inputs['Strength'].default_value=0.28
        links.new(normal_tex.outputs['Color'],normal_node.inputs['Color'])
        links.new(normal_node.outputs['Normal'],principled.inputs['Normal'])
        mask_tex=nodes.new('ShaderNodeTexImage')
        mask_tex.name='Musa Unity Mask Map'
        mask_tex.label='Musa_Mask.png • R Metallic / G AO / B Smoothness'
        mask_tex.image=mask_img
        mask_tex.interpolation='Linear'
        mask_tex.image.colorspace_settings.name='Non-Color'
        material['Unity_Mask_Map']='Musa_Mask.png'
        material['Unity_Smoothness_Target']=smoothness.get(material.name,0.2)
        material['Unity_Normal_Map']='Musa_Normal.png (Tangent Space, OpenGL Y+)'
    return normal_img,mask_img

def add_face_blendshapes():
    head=next((o for o,_ in parts if o.name.startswith('Head • sculpted')),None)
    if not head or not head.data.vertices:
        raise RuntimeError('Head mesh unavailable for facial blendshapes')
    basis=head.shape_key_add(name='Basis')
    smile=head.shape_key_add(name='Musa_Smile')
    brow=head.shape_key_add(name='Musa_BrowRaise')
    for i,vert in enumerate(head.data.vertices):
        x,y,z=vert.co
        # Head mesh is built in character coordinates; subtly lift mouth corners
        # and raise the forehead region without changing the base expression.
        if 1.91 <= y <= 2.03 and z > 0.12:
            weight=min(1.0,abs(x)/0.34)
            smile.data[i].co.z += 0.018*weight
        if y >= 2.25 and z > 0.08:
            brow.data[i].co.z += 0.014
    head['Blendshapes']='Musa_Smile;Musa_BrowRaise'

prepare_unity_textures()
add_face_blendshapes()

# Armature with connected hierarchy
arm_data=bpy.data.armatures.new("Musa_Skeleton")
arm=bpy.data.objects.new("Musa_Rig",arm_data); bpy.context.collection.objects.link(arm)
bpy.context.view_layer.objects.active=arm; arm.select_set(True); bpy.ops.object.mode_set(mode='EDIT')
bones=[
("root",(0,0,0),(0,0.15,0),None),
("spine",(0,.65,0),(0,1.52,0),"root"),
("neck",(0,1.52,0),(0,1.83,0),"spine"),
("head",(0,1.83,0),(0,2.45,0),"neck"),
("jaw",(0,2.02,.12),(0,1.88,.28),"head"),
("eye.L",(-.14,2.18,.25),(-.14,2.22,.31),"head"),
("eye.R",(.14,2.18,.25),(.14,2.22,.31),"head"),
("hood",(0,1.8,-.2),(0,2.35,-.2),"neck"),
]
for s,l in [(-1,"L"),(1,"R")]:
    bones += [(("upper_arm."+l),(s*.4,1.43,0),(s*.61,.99,.02),"spine"),
              (("forearm."+l),(s*.61,.99,.02),(s*.68,.59,.08),"upper_arm."+l),
              (("hand."+l),(s*.68,.59,.08),(s*.69,.40,.12),"forearm."+l),
              (("thigh."+l),(s*.22,.55,0),(s*.23,-.05,.01),"root"),
              (("shin."+l),(s*.23,-.05,.01),(s*.23,-.53,.015),"thigh."+l),
              (("foot."+l),(s*.23,-.53,.015),(s*.23,-.70,.12),"shin."+l)]
for name,head,tail,parent in bones:
    b=arm_data.edit_bones.new(name); b.head=head; b.tail=tail
    if parent: b.parent=arm_data.edit_bones[parent]
bpy.ops.object.mode_set(mode='OBJECT'); arm.select_set(False)

# Bind each sculpted mesh to its intended deform bone, preserving distinct anatomical pieces.
for obj,bone in parts:
    vg=obj.vertex_groups.new(name=bone); vg.add(list(range(len(obj.data.vertices))),1.0,'REPLACE')
    mod=obj.modifiers.new("Musa Armature","ARMATURE"); mod.object=arm
    obj.parent=arm
    obj.select_set(False)

# Animation helpers: reset every pose channel, smooth curves, and add true looping cycles.
def action(name, keys, loop=False):
    bpy.context.view_layer.objects.active=arm; arm.select_set(True)
    bpy.ops.object.mode_set(mode='POSE')
    for pb in arm.pose.bones:
        pb.rotation_mode='XYZ'; pb.rotation_euler=(0,0,0)
    act=bpy.data.actions.new(name); arm.animation_data.action=act
    for frame,poses in keys:
        for pb in arm.pose.bones:
            pb.rotation_euler=poses.get(pb.name,(0,0,0))
            pb.scale=poses.get(pb.name+'_scale',(1,1,1))
            pb.keyframe_insert(data_path='rotation_euler',frame=frame,group=pb.name)
            pb.keyframe_insert(data_path='scale',frame=frame,group=pb.name)
    for fc in act.fcurves:
        for kp in fc.keyframe_points:
            kp.interpolation='BEZIER'; kp.handle_left_type='AUTO_CLAMPED'; kp.handle_right_type='AUTO_CLAMPED'
        if loop: fc.modifiers.new('CYCLES')
    bpy.ops.object.mode_set(mode='OBJECT'); act.use_fake_user=True
    return act
arm.animation_data_create()
action('Musa_Idle_Breath_Loop',[(1,{'spine':(0,0,0),'head':(0,0,0)}),(10,{'spine':(.018,0,0),'head':(-.008,0,.008)}),(20,{'spine':(.028,0,0),'head':(-.014,0,0)}),(30,{'spine':(.012,0,0),'head':(-.006,0,-.006)}),(40,{'spine':(0,0,0),'head':(0,0,0)})],True)
action('Musa_Blink_Loop',[(1,{'eye.L_scale':(1,1,1),'eye.R_scale':(1,1,1)}),(8,{'eye.L_scale':(1,.10,1),'eye.R_scale':(1,.10,1)}),(10,{'eye.L_scale':(1,.08,1),'eye.R_scale':(1,.08,1)}),(13,{'eye.L_scale':(1,1,1),'eye.R_scale':(1,1,1)}),(52,{'eye.L_scale':(1,1,1),'eye.R_scale':(1,1,1)})],True)
action('Musa_Hood_Hit_Return',[(1,{'hood':(0,0,0)}),(3,{'hood':(-.58,0,0)}),(6,{'hood':(-.48,0,0)}),(10,{'hood':(-.29,0,0)}),(16,{'hood':(-.12,0,0)}),(24,{'hood':(0,0,0)})])
action('Musa_Head_Reaction',[(1,{'head':(0,0,0),'spine':(0,0,0)}),(4,{'head':(.22,0,.16),'spine':(.035,0,0)}),(9,{'head':(-.08,0,-.08),'spine':(-.015,0,0)}),(16,{'head':(.035,0,.025)}),(26,{'head':(0,0,0),'spine':(0,0,0)})])
action('Musa_Wave_L',[(1,{'upper_arm.L':(0,0,0),'forearm.L':(0,0,0)}),(7,{'upper_arm.L':(-.35,0,-.72),'forearm.L':(-.25,0,0)}),(13,{'upper_arm.L':(-.35,0,-.72),'forearm.L':(.28,0,0)}),(19,{'upper_arm.L':(-.35,0,-.72),'forearm.L':(-.22,0,0)}),(25,{'upper_arm.L':(-.35,0,-.72),'forearm.L':(.28,0,0)}),(34,{'upper_arm.L':(0,0,0),'forearm.L':(0,0,0)})])
action('Musa_Talking_Loop',[(1,{'jaw':(0,0,0),'head':(0,0,0)}),(4,{'jaw':(.12,0,0),'head':(.025,0,.015)}),(8,{'jaw':(.025,0,0),'head':(-.018,0,-.012)}),(12,{'jaw':(.16,0,0),'head':(.02,0,.01)}),(16,{'jaw':(0,0,0),'head':(0,0,0)})],True)
action('Musa_Laugh_Loop',[(1,{'jaw':(0,0,0),'head':(0,0,0),'spine':(0,0,0)}),(5,{'jaw':(.23,0,0),'head':(-.06,0,.035),'spine':(.025,0,0)}),(9,{'jaw':(.035,0,0),'head':(.035,0,-.025),'spine':(-.01,0,0)}),(13,{'jaw':(.20,0,0),'head':(-.045,0,.025),'spine':(.02,0,0)}),(18,{'jaw':(0,0,0),'head':(0,0,0),'spine':(0,0,0)})],True)
# scene, camera-friendly orientation
arm.show_in_front=True
bpy.context.scene.frame_start=1; bpy.context.scene.frame_end=40; bpy.context.scene.frame_set(1)
# Studio preview render for visual QA (transparent background, full-body framing).
scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.device='CPU'
scene.cycles.samples=12
scene.render.resolution_percentage=75
scene.render.resolution_x=900
scene.render.resolution_y=900
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.render.film_transparent=True
scene.world.color=(0.055,0.055,0.055)
camera_data=bpy.data.cameras.new("Musa_Preview_Camera")
camera=bpy.data.objects.new("Musa_Preview_Camera",camera_data)
bpy.context.collection.objects.link(camera)
# The character is authored with Y as its vertical axis; use a straight-on
# camera with world Y as image-up so the preview is not rolled sideways.
camera.location=(0,1.05,8.0)
camera.rotation_euler=(0,0,0)
target=Vector((0,1.05,0))
camera_data.type='ORTHO'
camera_data.ortho_scale=3.85
scene.camera=camera
def area_light(name,location,power,size,color):
    data=bpy.data.lights.new(name,'AREA'); data.energy=power; data.shape='DISK'; data.size=size; data.color=color
    obj=bpy.data.objects.new(name,data); bpy.context.collection.objects.link(obj); obj.location=location
    obj.rotation_euler=(Vector((0,1.25,0))-obj.location).to_track_quat('-Z','Y').to_euler()
area_light("Musa_Key_Light",(4.0,4.5,5.0),850,4.0,(1.0,.82,.68))
area_light("Musa_Fill_Light",(-4.0,2.5,3.5),520,3.5,(.62,.76,1.0))
area_light("Musa_Rim_Light",(1.0,4.0,-3.5),950,3.0,(1.0,.72,.48))
scene.render.filepath=os.path.join(OUT,"Musa_Preview.png")
scene.view_settings.view_transform='AgX'
bpy.ops.render.render(write_still=True)
assert os.path.getsize(os.path.join(OUT,"Musa_Preview.png"))>10000, "Preview render missing or too small"

# Select mesh + rig for export
bpy.ops.object.select_all(action='DESELECT'); arm.select_set(True)
for obj,_ in parts: obj.select_set(True)
bpy.context.view_layer.objects.active=arm
bpy.ops.wm.save_as_mainfile(filepath=BLEND)

# Reopen saved project and validate scene content
bpy.ops.wm.open_mainfile(filepath=BLEND)
arm=bpy.data.objects.get("Musa_Rig")
assert arm and arm.type=="ARMATURE"
required={"root","spine","neck","head","jaw","eye.L","eye.R","hood","upper_arm.L","forearm.L","hand.L","thigh.L","shin.L","foot.L"}
assert required.issubset(set(arm.data.bones.keys())), "Missing expected rig bones"
assert len([o for o in bpy.data.objects if o.type=="MESH"]) >= 20, "Character mesh incomplete"
assert os.path.exists(os.path.join(OUT,"Musa_Normal.png")), "Normal map missing"
assert os.path.exists(os.path.join(OUT,"Musa_Mask.png")), "Mask map missing"
head_for_keys=next((o for o in bpy.data.objects if o.type=="MESH" and o.name.startswith("Head • sculpted")),None)
assert head_for_keys and head_for_keys.data.shape_keys and {"Musa_Smile","Musa_BrowRaise"}.issubset({k.name for k in head_for_keys.data.shape_keys.key_blocks}), "Facial blendshapes missing"
acts={a.name for a in bpy.data.actions}
for a in ["Musa_Idle_Breath_Loop","Musa_Blink_Loop","Musa_Hood_Hit_Return","Musa_Head_Reaction","Musa_Wave_L","Musa_Talking_Loop","Musa_Laugh_Loop"]:
    assert a in acts, "Missing animation "+a
# Export FBX with mesh and rig, including actions
bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
for o in bpy.data.objects:
    if o.type=="MESH": o.select_set(True)
bpy.context.view_layer.objects.active=arm
bpy.ops.export_scene.fbx(filepath=FBX, use_selection=True, object_types={'ARMATURE','MESH'}, add_leaf_bones=False, use_mesh_modifiers=False, bake_anim=True, bake_anim_use_all_actions=True, path_mode='COPY')
assert os.path.getsize(FBX)>10000, "FBX export unexpectedly small"
# Binary glTF is the runtime format consumed by the Android Three.js app.
# This character is authored with Blender Y as its vertical axis (not Blender's
# usual Z-up axis). Preserve those coordinates in glTF so the Android/Three.js
# Y-up scene receives an upright character instead of a quarter-turned model.
bpy.ops.export_scene.gltf(filepath=GLB, export_format='GLB', use_selection=True,
    export_animations=True, export_skins=True, export_morph=True,
    export_materials='EXPORT', export_image_format='AUTO', export_yup=False)
assert os.path.getsize(GLB)>10000, "GLB export unexpectedly small"
# Re-import FBX into clean scene to validate exported data
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.fbx(filepath=FBX, automatic_bone_orientation=False)
imported_arm=[o for o in bpy.data.objects if o.type=="ARMATURE"]
imported_mesh=[o for o in bpy.data.objects if o.type=="MESH"]
assert imported_arm, "FBX re-import contains no armature"
assert imported_mesh, "FBX re-import contains no mesh"
assert len(imported_arm[0].data.bones)>=len(required), "FBX re-import rig has too few bones"
imported_shape_keys=sum(1 for o in imported_mesh if o.data.shape_keys for k in o.data.shape_keys.key_blocks if k.name not in {"Basis"})
assert imported_shape_keys >= 2, "FBX round-trip lost facial blendshapes"
for texture_name in ("Musa_Normal.png","Musa_Mask.png"):
    assert os.path.getsize(os.path.join(OUT,texture_name)) > 1000, "Texture export invalid: "+texture_name
print("MUSA_3D_VALIDATION_OK",len(imported_mesh),len(imported_arm[0].data.bones),len(acts),imported_shape_keys,os.path.getsize(FBX))
