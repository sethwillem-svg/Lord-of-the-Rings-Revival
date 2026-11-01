"""Convert HY-Motion 1.0 output (.npz, SMPL-H parameters) to BVH that the rig viewer and retargeter read.

Usage: python3 hymotion_to_bvh.py --hy-repo PATH/TO/HY-Motion-1.0 sample.npz [more.npz ...] [--out DIR]

HY-Motion saves per-joint axis-angle rotations ('poses', 52 joints) and root translation ('trans', metres) at 30 fps.
The skeleton (rest joint positions and parents) comes from the HY-Motion install itself
(scripts/gradio/static/assets/dump_wooden: j_template.bin, kintree.bin, joint_names.json; these are Git LFS files,
so run `git lfs pull` there first). Only the 22 body joints are written, renamed to the Kimodo SOMA names the
retargeter uses (Hips, LeftArm, LeftForeArm, LeftHand, ...). SMPL-H has no head-end or eye joints, so HeadEnd,
LeftEye and RightEye are added as fixed children of Head. Output is Y up, centimetres, like Kimodo's BVH.
"""
import argparse
import json
import os
import sys

import numpy as np
from scipy.spatial.transform import Rotation as R

TEMPLATE_DIR = os.path.join('scripts', 'gradio', 'static', 'assets', 'dump_wooden')
BODY = 22
RENAME = {
    'Pelvis': 'Hips', 'L_Hip': 'LeftLeg', 'R_Hip': 'RightLeg', 'Spine1': 'Spine1', 'L_Knee': 'LeftShin',
    'R_Knee': 'RightShin', 'Spine2': 'Spine2', 'L_Ankle': 'LeftFoot', 'R_Ankle': 'RightFoot', 'Spine3': 'Chest',
    'L_Foot': 'LeftToeBase', 'R_Foot': 'RightToeBase', 'Neck': 'Neck1', 'L_Collar': 'LeftShoulder',
    'R_Collar': 'RightShoulder', 'Head': 'Head', 'L_Shoulder': 'LeftArm', 'R_Shoulder': 'RightArm',
    'L_Elbow': 'LeftForeArm', 'R_Elbow': 'RightForeArm', 'L_Wrist': 'LeftHand', 'R_Wrist': 'RightHand',
}


def _read_bin(path, dtype):
    with open(path, 'rb') as f:
        data = f.read()
    if data.startswith(b'version https://git-lfs'):
        sys.exit(f'{path} is a Git LFS pointer, not the real file. Run `git lfs pull` in the HY-Motion repo.')
    return np.frombuffer(data, dtype=dtype)


def load_template(hy_repo):
    """Rest joint positions (J,3, metres), parents (J,) and joint names from the HY-Motion install."""
    d = os.path.join(hy_repo, TEMPLATE_DIR)
    if not os.path.isdir(d):
        sys.exit(f'No skeleton template at {d}. Is --hy-repo the HY-Motion-1.0 folder?')
    joints = _read_bin(os.path.join(d, 'j_template.bin'), np.float32).reshape(-1, 3).astype(float)
    parents = _read_bin(os.path.join(d, 'kintree.bin'), np.int32).astype(int)
    with open(os.path.join(d, 'joint_names.json')) as f:
        names = json.load(f)
    return joints, parents, names


def convert(npz_path, template, out_path):
    joints, parents, names = template
    d = np.load(npz_path)
    poses = d['poses'].reshape(len(d['poses']), -1, 3)[:, :BODY]       # (F, 22, 3) axis-angle
    trans = d['trans'].reshape(-1, 3)                                    # (F, 3) metres
    fps = float(d['mocap_framerate']) if 'mocap_framerate' in d.files else 30.0
    F = len(poses)
    J = joints[:BODY] * 100.0                                            # cm
    par = parents[:BODY]
    nm = [RENAME.get(n, n) for n in names[:BODY]]

    # extra joints under Head: HeadEnd along neck->head, eyes forward of it (SMPL rest faces +Z, left is +X)
    head, neck = names.index('Head'), names.index('Neck')
    up = J[head] - J[neck]
    up = up / np.linalg.norm(up)
    extras = [('HeadEnd', head, up * 16.0), ('LeftEye', head, up * 4 + np.array([3.2, 0, 9.0])),
              ('RightEye', head, up * 4 + np.array([-3.2, 0, 9.0]))]

    children = {i: [c for c in range(BODY) if par[c] == i] for i in range(BODY)}
    order, lines = [], ['HIERARCHY']

    def write(j, depth):
        pad = '  ' * depth
        order.append(j)
        lines.append(f'{pad}{"ROOT" if par[j] < 0 else "JOINT"} {nm[j]}')
        lines.append(pad + '{')
        off = np.zeros(3) if par[j] < 0 else J[j] - J[par[j]]
        lines.append(f'{pad}  OFFSET {off[0]:.6f} {off[1]:.6f} {off[2]:.6f}')
        if par[j] < 0:
            lines.append(f'{pad}  CHANNELS 6 Xposition Yposition Zposition Zrotation Yrotation Xrotation')
        else:
            lines.append(f'{pad}  CHANNELS 3 Zrotation Yrotation Xrotation')
        for c in children[j]:
            write(c, depth + 1)
        for name, p, off in extras:
            if p == j:
                order.append(name)
                lines.extend([f'{pad}  JOINT {name}', pad + '  {', f'{pad}    OFFSET {off[0]:.6f} {off[1]:.6f} {off[2]:.6f}',
                              f'{pad}    CHANNELS 3 Zrotation Yrotation Xrotation',
                              pad + '    End Site', pad + '    {', f'{pad}      OFFSET 0 0 0', pad + '    }', pad + '  }'])
        if not children[j] and not any(p == j for _, p, _ in extras):
            lines.extend([pad + '  End Site', pad + '  {', f'{pad}    OFFSET 0 0 0', pad + '  }'])
        lines.append(pad + '}')

    write(int(np.where(par < 0)[0][0]), 0)
    root = order[0]
    eul = R.from_rotvec(poses.reshape(-1, 3)).as_euler('ZYX', degrees=True).reshape(F, BODY, 3)
    lines += ['MOTION', f'Frames: {F}', f'Frame Time: {1.0 / fps:.8f}']
    for f in range(F):
        row = list((J[root] + trans[f] * 100.0))
        for j in order:
            row += [0.0, 0.0, 0.0] if isinstance(j, str) else list(eul[f, j])
        lines.append(' '.join(f'{v:.5f}' for v in row))
    tmp = out_path + '.part'
    with open(tmp, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    os.replace(tmp, out_path)
    return F


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('npz', nargs='+')
    ap.add_argument('--hy-repo', required=True, help='path to the HY-Motion-1.0 repo')
    ap.add_argument('--out', help='output folder (default: next to each .npz)')
    args = ap.parse_args()
    template = load_template(args.hy_repo)
    for p in args.npz:
        out = os.path.join(args.out or os.path.dirname(p), os.path.splitext(os.path.basename(p))[0] + '.bvh')
        print(f'{p} -> {out} ({convert(p, template, out)} frames)')


if __name__ == '__main__':
    main()
