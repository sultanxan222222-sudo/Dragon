import bpy, math, os
from mathutils import Vector
from math import sin, cos, pi

OUT=os.path.abspath("artifacts")
os.makedirs(OUT,exist_ok=True)
BLEND=os.path.join(OUT,"Talking_Musa_Rigged.blend")
FBX=os.path.join(OUT,"Talking_Musa_Rigged.fbx")

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
        radial=cos(phi)
        for i in range(ns):
            th=2*pi*i/ns
            # Slight anatomical asymmetry and front/back shaping avoid perfect balls.
            x=radial*cos(th); z=radial*sin(th)
            front=max(0.0,z)
            verts.append((cx+sx*x*(1-.035*yshape), cy+sy*yshape, cz+sz*z*(1-.10*front)))
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

def cyl(name,a,b,r1,r2,material,bone,verts=24):
    # Tapered quad-loop tube with support rings and closed polygon caps.
    a,b=Vector(a),Vector(b); axis=(b-a).normalized()
    ref=Vector((0,0,1)) if abs(axis.z)<.92 else Vector((0,1,0))
    u=axis.cross(ref).normalized(); v=axis.cross(u).normalized()
    rings=[(0.0,r1*.88),(0.08,r1),(0.28,r1*.96),(0.72,r2*1.04),(0.92,r2),(1.0,r2*.9)]
    verts=[]; faces=[]; n=max(12, int(verts))
    for t,r in rings:
        center=a+(b-a)*t
        for i in range(n):
            ang=2*pi*i/n; p=center+r*(cos(ang)*u+sin(ang)*v)
            verts.append(tuple(p))
    for j in range(len(rings)-1):
        for i in range(n):
            ni=(i+1)%n; faces.append((j*n+i,j*n+ni,(j+1)*n+ni,(j+1)*n+i))
    faces.append(tuple(reversed(tuple(range(n)))))
    faces.append(tuple((len(rings)-1)*n+i for i in range(n)))
    return make_mesh(name,verts,faces,material,bone)

# Torso / jacket silhouette and hood
uv("Jacket torso",(0,1.02,0),(.49,.68,.29),jacket,"spine")
uv("Shoulder left",(-.39,1.43,0),(.30,.24,.29),jacket,"spine")
uv("Shoulder right",(.39,1.43,0),(.30,.24,.29),jacket,"spine")
uv("Neck",(0,1.76,0),(.17,.20,.17),skin,"neck")
uv("Head",(0,2.10,0),(.39,.48,.34),skin,"head",40,28)
uv("Ear L",(-.39,2.10,0),(.085,.13,.075),skin,"head")
uv("Ear R",(.39,2.10,0),(.085,.13,.075),skin,"head")
# beard wraps jaw and cheeks
uv("Beard main",(0,1.88,.20),(.34,.32,.23),beard,"head",40,24)
uv("Beard chin",(0,1.68,.19),(.26,.20,.22),beard,"head")
uv("Mustache L",(-.105,1.98,.295),(.16,.075,.075),beard,"head")
uv("Mustache R",(.105,1.98,.295),(.16,.075,.075),beard,"head")
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
acts={a.name for a in bpy.data.actions}
for a in ["Musa_Idle_Breath_Loop","Musa_Blink_Loop","Musa_Hood_Hit_Return","Musa_Head_Reaction","Musa_Wave_L","Musa_Talking_Loop","Musa_Laugh_Loop"]:
    assert a in acts, "Missing animation "+a
# Export FBX with mesh and rig, including actions
bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
for o in bpy.data.objects:
    if o.type=="MESH": o.select_set(True)
bpy.context.view_layer.objects.active=arm
bpy.ops.export_scene.fbx(filepath=FBX, use_selection=True, object_types={'ARMATURE','MESH'}, add_leaf_bones=False, bake_anim=True, bake_anim_use_all_actions=True, path_mode='COPY')
assert os.path.getsize(FBX)>10000, "FBX export unexpectedly small"
# Re-import FBX into clean scene to validate exported data
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.fbx(filepath=FBX, automatic_bone_orientation=False)
imported_arm=[o for o in bpy.data.objects if o.type=="ARMATURE"]
imported_mesh=[o for o in bpy.data.objects if o.type=="MESH"]
assert imported_arm, "FBX re-import contains no armature"
assert imported_mesh, "FBX re-import contains no mesh"
assert len(imported_arm[0].data.bones)>=len(required), "FBX re-import rig has too few bones"
print("MUSA_3D_VALIDATION_OK",len(imported_mesh),len(imported_arm[0].data.bones),len(acts),os.path.getsize(FBX))
