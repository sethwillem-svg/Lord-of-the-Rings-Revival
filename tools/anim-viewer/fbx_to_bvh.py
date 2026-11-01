#!/usr/bin/env python3
"""Convert animation FBX files (Unity Asset Store packs and similar) to BVH for the viewer's local library.

The output goes to tools/anim-viewer/library/local/, which git ignores: most store licences let you use the
animations in a game but not redistribute the files, so keep them out of the public repo.

  blender -b -P tools/anim-viewer/fbx_to_bvh.py -- PACK_DIR [--race orc] [--out DIR]
  python3 tools/anim-viewer/fbx_to_bvh.py PACK_DIR ...          (with the bpy module: pip install bpy, Python 3.11+)

PACK_DIR may be one .fbx/.bvh file or a folder (searched recursively). Each animation take in an FBX becomes
one BVH named after the take; --race orc prefixes the names (orc.<take>.bvh) so the viewer lists them under that
race. Rename files to orc.<clip key>.bvh (for example orc.attack.sword.3.bvh) to give them spec clip keys.
BVH inputs are copied with renamed joints (no Blender needed).

Joint names are rewritten to the Mixamo names the retargeter reads (LeftUpLeg, LeftLeg, LeftFoot, LeftArm,
LeftForeArm, LeftHand, Spine..Spine2, Neck, Head), from the common naming schemes: Mixamo, Unity humanoid
(LeftUpperLeg, Chest, UpperChest), 3ds Max Biped (Bip01 L Thigh, L Calf), Unreal (thigh_l, calf_l, spine_03).
A rig the script cannot read is skipped with the joint names it found.
"""
import argparse
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, 'library', 'local')

# limb part names (lowercase, separators removed) -> Mixamo part
PARTS = {
    'upleg': 'UpLeg', 'upperleg': 'UpLeg', 'thigh': 'UpLeg',
    'leg': 'Leg', 'lowerleg': 'Leg', 'calf': 'Leg', 'shin': 'Leg', 'knee': 'Leg',
    'foot': 'Foot', 'ankle': 'Foot',
    'toebase': 'ToeBase', 'toes': 'ToeBase', 'toe': 'ToeBase', 'toe0': 'ToeBase', 'ball': 'ToeBase',
    'shoulder': 'Shoulder', 'clavicle': 'Shoulder', 'collar': 'Shoulder',
    'arm': 'Arm', 'upperarm': 'Arm',
    'forearm': 'ForeArm', 'lowerarm': 'ForeArm', 'elbow': 'ForeArm',
    'hand': 'Hand', 'wrist': 'Hand',
}
LIMB = ['UpLeg', 'Leg', 'Foot', 'Arm', 'ForeArm', 'Hand']   # the retargeter needs these on both sides


def strip(name):
    """Drop namespaces (mixamorig:, Armature|) and Biped/DEF prefixes."""
    n = re.split(r'[:|]', name)[-1]
    return re.sub(r'^(bip0*1|def|b|bn|jnt|j)[\s_.-]+', '', n, flags=re.I).strip()


def side_and_part(name):
    n = strip(name)
    m = re.match(r'^(left|right)[\s_.-]*(.+)$', n, re.I) or re.match(r'^([lr])[\s_.-]+(.+)$', n, re.I)
    if m:
        s, rest = m.group(1), m.group(2)
    else:
        m = re.match(r'^(.+?)[\s_.-]+(left|right|l|r)$', n, re.I)
        if not m:
            return None, re.sub(r'[\s_.-]', '', n).lower()
        rest, s = m.group(1), m.group(2)
    return ('Left' if s[0] in 'lL' else 'Right'), re.sub(r'[\s_.-]', '', rest).lower()


def parse_hierarchy(text):
    """Joint names (End Sites excluded) and parent indices from a BVH header."""
    names, parent, stack, cur = [], [], [], -1
    for line in text.split('MOTION', 1)[0].splitlines():
        t = line.split()
        if not t:
            continue
        if t[0] in ('ROOT', 'JOINT'):
            names.append(line.split(None, 1)[1].strip())
            parent.append(stack[-1] if stack else -1)
            cur = len(names) - 1
        elif t[0] == 'End':
            cur = None
        elif t[0] == '{':
            stack.append(cur)
        elif t[0] == '}':
            stack.pop()
    return names, parent   # End Site blocks push None, which no joint has as parent


def mixamo_names(names, parent):
    """Map each joint to a Mixamo name where we can tell what it is. Returns {old: new} or raises ValueError."""
    ren, found = {}, {}
    for i, n in enumerate(names):
        side, part = side_and_part(n)
        part = part if part in PARTS else part.rstrip('0123456789')
        if side and part in PARTS:
            found.setdefault((side, PARTS[part]), []).append(i)
    # SOMA/Kimodo style: LeftLeg is the thigh when the same side also has a shin
    for s in ('Left', 'Right'):
        idx = {i: side_and_part(names[i])[1] for k, v in found.items() if k[0] == s for i in v}
        if 'shin' in idx.values():
            for i, p in idx.items():
                if p == 'leg':
                    found[(s, 'Leg')].remove(i)
                    found.setdefault((s, 'UpLeg'), []).insert(0, i)
    for (s, p), idx in found.items():
        ren[names[idx[0]]] = s + p                     # first match (closest to the root) wins
    missing = [s + p for s in ('Left', 'Right') for p in LIMB if (s, p) not in found]
    if missing:
        raise ValueError('no joint for ' + ', '.join(missing))
    # hips: the joint both thighs hang from (through hip/pelvis helper joints)
    def ancestors(i):
        out = []
        while parent[i] >= 0:
            i = parent[i]
            out.append(i)
        return out
    la, ra = ancestors(found[('Left', 'UpLeg')][0]), ancestors(found[('Right', 'UpLeg')][0])
    hips = next(i for i in la if i in ra)
    # neck: the joint both shoulders (or upper arms) hang from; spine: joints between hips and neck
    top = lambda s: found.get((s, 'Shoulder'), found[(s, 'Arm')])[0]
    la, ra = ancestors(top('Left')), ancestors(top('Right'))
    chest = next(i for i in la if i in ra)
    head = next((i for i, n in enumerate(names) if re.sub(r'[\s_.-]', '', strip(n)).lower() in ('head', 'head1')), None)
    if head is None:
        raise ValueError('no head joint')
    neck = [i for i in ancestors(head) if i not in ancestors(chest) and i != chest]   # joints from chest to head
    spine = []
    i = chest
    while i != hips and i >= 0:
        spine.append(i)
        i = parent[i]
    if not spine:
        raise ValueError('the arms hang from the hips: no spine joints')
    ren[names[hips]] = 'Hips'
    for new, j in zip(['Spine2', 'Spine1', 'Spine'], spine):     # top of the chain is Spine2 (the chest)
        ren[names[j]] = new
    if neck:
        ren[names[neck[-1]]] = 'Neck'
    ren[names[head]] = 'Head'
    # other joints keep their names without namespaces, but never collide with a name we assigned
    taken = set(ren.values())
    for n in names:
        if n not in ren:
            bare = re.split(r'[:|]', n)[-1]
            ren[n] = 'x_' + bare if bare in taken else bare
    return ren


def rename_bvh(text):
    names, parent = parse_hierarchy(text)
    ren = mixamo_names(names, parent)
    head, motion = text.split('MOTION', 1)
    out = []
    for line in head.splitlines():
        t = line.split(None, 1)
        if t and t[0] in ('ROOT', 'JOINT'):
            n = t[1].strip()
            line = line[:len(line) - len(line.lstrip())] + t[0] + ' ' + re.sub(r'\s+', '_', ren.get(n, n))
        out.append(line)
    return '\n'.join(out) + '\nMOTION' + motion


def safe(name):
    return re.sub(r'[^A-Za-z0-9_.-]+', '_', name).strip('_.') or 'clip'


def write(path, text):
    with open(path + '.part', 'w', newline='\n') as f:
        f.write(text)
    os.replace(path + '.part', path)


def fbx_takes(path, tmpdir):
    """Import one FBX with Blender and export each take to a temporary BVH. Yields (take name, BVH text)."""
    import bpy
    bpy.ops.wm.read_factory_settings(use_empty=True)
    bpy.ops.import_scene.fbx(filepath=path, automatic_bone_orientation=False, ignore_leaf_bones=False)
    arms = [o for o in bpy.context.scene.objects if o.type == 'ARMATURE']
    if not arms:
        raise ValueError('no armature')
    arm = max(arms, key=lambda o: len(o.data.bones))
    actions = list(bpy.data.actions)
    if not actions:
        raise ValueError('no animation')
    bpy.context.view_layer.objects.active = arm
    for o in bpy.context.scene.objects:
        o.select_set(o == arm)
    # The BVH exporter writes armature space. Bake the object's transform plus Blender Z-up -> BVH Y-up
    # (-90 degrees about X) into the armature, so the clip stands up with +Z forward.
    import math
    from mathutils import Matrix
    arm.parent = None
    arm.matrix_world = Matrix.Rotation(-math.pi / 2, 4, 'X') @ arm.matrix_world
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    arm.animation_data_create()
    for act in actions:
        arm.animation_data.action = act
        if hasattr(arm.animation_data, 'action_slot') and getattr(act, 'slots', None):
            arm.animation_data.action_slot = act.slots[0]
        a, b = (int(round(x)) for x in act.frame_range)
        tmp = os.path.join(tmpdir, 'take.bvh')
        bpy.ops.export_anim.bvh(filepath=tmp, frame_start=a, frame_end=max(b, a + 1), root_transform_only=False)
        name = act.name.split('|')[-1]
        with open(tmp) as f:
            yield name, f.read()


def main(argv):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('input', help='an .fbx or .bvh file, or a folder of them')
    ap.add_argument('--race', help='prefix output names with <race>. so the viewer lists them under that race')
    ap.add_argument('--out', default=OUT, help='output folder (default: library/local)')
    args = ap.parse_args(argv)
    files = [args.input] if os.path.isfile(args.input) else sorted(
        os.path.join(d, f) for d, _, fs in os.walk(args.input) for f in fs if f.lower().endswith(('.fbx', '.bvh')))
    if not files:
        sys.exit('No .fbx or .bvh files in ' + args.input)
    os.makedirs(args.out, exist_ok=True)
    gi = os.path.join(args.out, '.gitignore')
    if os.path.abspath(args.out) == os.path.abspath(OUT) and not os.path.exists(gi):
        write(gi, '*\n!.gitignore\n!README.md\n')
    prefix = (args.race + '.') if args.race else ''
    tmpdir = os.path.join(args.out, '.tmp')
    os.makedirs(tmpdir, exist_ok=True)
    done = skipped = 0
    for p in files:
        base = os.path.splitext(os.path.basename(p))[0]
        try:
            if p.lower().endswith('.bvh'):
                takes = [(base, open(p, encoding='latin-1').read())]
            else:
                takes = list(fbx_takes(p, tmpdir))
                if len(takes) == 1:
                    takes = [(base, takes[0][1])]
            for name, text in takes:
                name = name if len(takes) == 1 else base + '_' + name
                write(os.path.join(args.out, prefix + safe(name) + '.bvh'), rename_bvh(text))
                done += 1
                print('ok  ', prefix + safe(name) + '.bvh')
        except ImportError:
            sys.exit('FBX needs Blender: blender -b -P fbx_to_bvh.py -- ' + ' '.join(argv) + '   (or pip install bpy)')
        except Exception as e:      # noqa: BLE001 - report and carry on with the rest of the pack
            skipped += 1
            print('skip', p, '-', e)
    try:
        os.remove(os.path.join(tmpdir, 'take.bvh'))
    except OSError:
        pass
    os.rmdir(tmpdir) if os.path.isdir(tmpdir) and not os.listdir(tmpdir) else None
    print('%d clips written to %s, %d files skipped.' % (done, args.out, skipped))


if __name__ == '__main__':
    main(sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:])
