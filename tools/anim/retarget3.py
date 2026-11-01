"""Pass-1 retarget: Kimodo SOMA BVH -> orc cube rig (direction based, no Blender).

Usage: python3 retarget3.py in.bvh model.bbmodel out.json [name]
Orc space: +X = character's left (names-consistent, as in the viewer), +Y up, -Z forward.
"""
import json
import sys

import numpy as np
from scipy.spatial.transform import Rotation as R

from bvh import BVH

EPS = 1e-9


def norm(v):
    return v / np.maximum(np.linalg.norm(v, axis=-1, keepdims=True), EPS)


def frame_from(x, y):
    """Rotation matrices (F,3,3), columns x,y,z with y exact and z = x cross y (identity at rest)."""
    y = norm(y)
    x = norm(x - (x * y).sum(-1, keepdims=True) * y)
    return np.stack([x, y, np.cross(x, y)], -1)


def smoothstep(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def limb_frames(du, df, lat, prev_sign=1.0):
    """Hinge frames for a 2-bone limb (rest direction -Y, hinge about local X).

    du, df: (F,3) unit directions of upper and lower segment in the parent's frame.
    lat: (F,3) default hinge axis used when the limb is nearly straight.
    Returns Rup (F,3,3) and the hinge angle theta (F,) so that lower = Rup @ Rx(theta).
    """
    F = len(du)
    perp = df - (df * du).sum(-1, keepdims=True) * du
    plen = np.linalg.norm(perp, axis=-1)
    x_geo = np.cross(du, norm(perp))
    x_def = lat - (lat * du).sum(-1, keepdims=True) * du
    x_def = norm(x_def)
    w = smoothstep(0.08, 0.30, plen)[:, None]
    X = np.empty((F, 3))
    ref = np.array([1.0, 0.0, 0.0])
    last = ref * prev_sign
    for i in range(F):
        # align hinge sign with the previous frame (continuity), start from the default axis
        g = x_geo[i] if plen[i] > 1e-6 else x_def[i]
        if g @ last < 0:
            g = -g
        d = x_def[i] if x_def[i] @ last >= 0 else -x_def[i]
        v = w[i, 0] * g + (1 - w[i, 0]) * d
        n = np.linalg.norm(v)
        v = v / n if n > EPS else last
        X[i] = v
        last = v
    Y = -du
    Z = np.cross(X, Y)
    Rup = np.stack([X, Y, Z], -1)
    dl = np.einsum('fji,fj->fi', Rup, df)           # lower direction in the upper segment's frame
    theta = np.arctan2(-dl[:, 2], -dl[:, 1])
    return Rup, theta


def rx(theta):
    c, s = np.cos(theta), np.sin(theta)
    m = np.zeros((len(theta), 3, 3))
    m[:, 0, 0] = 1
    m[:, 1, 1], m[:, 1, 2], m[:, 2, 1], m[:, 2, 2] = c, -s, s, c
    return m


def euler_continuous(mats):
    """Euler XYZ-extrinsic degrees (matrix = Rz Ry Rx) per frame, continuous over time."""
    e = R.from_matrix(mats).as_euler('xyz', degrees=True)
    out = [e[0]]
    for i in range(1, len(e)):
        a = e[i]
        alt = np.array([a[0] + 180, 180 - a[1], a[2] + 180])
        cand = [a + 360 * np.round((out[-1] - a) / 360)]
        cand.append(alt + 360 * np.round((out[-1] - alt) / 360))
        out.append(min(cand, key=lambda c: np.abs(c - out[-1]).sum()))
    return np.array(out)


class OrcRig:
    """Bone hierarchy and rest data from a .bbmodel (groups = bones)."""

    def __init__(self, path):
        d = json.load(open(path))
        self.names, self.parent, self.origin, self.rest = [], [], [], []
        self.cubes = {}
        els = {e['uuid']: e for e in d['elements']}

        def walk(n, p):
            if isinstance(n, str):
                if p >= 0 and n in els:
                    e = els[n]
                    self.cubes.setdefault(self.names[p], []).append((np.array(e['from']), np.array(e['to'])))
                return
            self.names.append(n['name'])
            self.parent.append(p)
            self.origin.append(np.array(n.get('origin', [0, 0, 0]), float))
            self.rest.append(np.array(n.get('rotation', [0, 0, 0]), float))
            me = len(self.names) - 1
            for c in n.get('children', []):
                walk(c, me)

        for n in d['outliner']:
            walk(n, -1)
        self.idx = {k: i for i, k in enumerate(self.names)}

    def subtree(self, bone):
        out, stack = [], [self.idx[bone]]
        while stack:
            b = stack.pop()
            out.append(b)
            stack += [i for i, p in enumerate(self.parent) if p == b]
        return out

    def lowest_point(self, bone):
        pts = [c for b in self.subtree(bone) for cube in self.cubes.get(self.names[b], []) for c in cube]
        return min(p[1] for p in pts)

    def far_point(self, bone):
        """Cube corner farthest from the bone's pivot (weapon tip estimate), in world rest coords."""
        o = self.origin[self.idx[bone]]
        pts = [c for b in self.subtree(bone) for cube in self.cubes.get(self.names[b], [])
               for c in (cube[0], cube[1])]
        return max(pts, key=lambda p: np.linalg.norm(p - o))

    def fk(self, local_rot, root_pos):
        """local_rot: {bone: (F,3,3) full local rotation incl. rest}. Returns {bone: (pos (F,3), rot (F,3,3))}."""
        F = len(root_pos)
        world = {}
        for i, nm in enumerate(self.names):
            p = self.parent[i]
            Rl = local_rot.get(nm)
            if Rl is None:
                Rl = np.tile(R.from_euler('xyz', self.rest[i], degrees=True).as_matrix(), (F, 1, 1))
            if p < 0:
                world[nm] = (root_pos + 0.0, Rl)
            else:
                pp, pr = world[self.names[p]]
                off = self.origin[i] - self.origin[p]
                world[nm] = (pp + np.einsum('fij,j->fi', pr, off), np.einsum('fij,fjk->fik', pr, Rl))
        return world


def retarget(bvh_path, model_path, name='mm_new_slam'):
    b = BVH(bvh_path)
    P, _ = b.fk()
    n = b.names
    g = lambda k: P[:, n.index(k)]
    rig = OrcRig(model_path)
    F = b.n

    # heading frame from frame 0 -> orc space (+X char left, +Y up, -Z forward)
    up = np.array([0, 1.0, 0])
    l0 = g('LeftArm')[0] - g('RightArm')[0]
    l0[1] = 0
    l0 /= np.linalg.norm(l0)
    f0 = np.cross(l0, up)

    def o(v):
        return np.stack([v @ l0, v[..., 1], -(v @ f0)], -1)

    feet = [n.index(k) for k in ('LeftFoot', 'RightFoot', 'LeftToeBase', 'RightToeBase')]
    floor = P[:, feet, 1].min()
    hip_h = g('Hips')[0, 1] - floor
    s = rig.origin[rig.idx['right_leg']][1] / hip_h

    # body (upper torso): shoulder line + spine axis
    lat = o(g('LeftArm') - g('RightArm'))
    mid = (g('LeftArm') + g('RightArm')) / 2
    Rbody = frame_from(lat, o(mid - g('Hips')))
    # head
    Rhead_w = frame_from(o(g('LeftEye') - g('RightEye')), o(g('HeadEnd') - g('Head')))
    Rhead = np.einsum('fji,fjk->fik', Rbody, Rhead_w)

    local = {'root': np.tile(np.eye(3), (F, 1, 1)), 'body': Rbody, 'head': Rhead}
    bt = lambda v: np.einsum('fji,fj->fi', Rbody, v)         # vector -> body-local

    for side, S in (('right', 'Right'), ('left', 'Left')):
        sh, el, wr = g(S + 'Arm'), g(S + 'ForeArm'), g(S + 'Hand')
        du, df = bt(norm(o(el - sh))), bt(norm(o(wr - el)))
        Rup, th = limb_frames(du, df, np.tile([1.0, 0, 0], (F, 1)))
        local[side + '_arm'] = Rup
        local[side + '_forearm'] = rx(th)
        local[side + '_hand'] = np.tile(np.eye(3), (F, 1, 1))
        # legs hang from the (unrotated) root
        hp, kn, an = g(S + 'Leg'), g(S + 'Shin'), g(S + 'Foot')
        dt, ds = norm(o(kn - hp)), norm(o(an - kn))
        Rl, thl = limb_frames(dt, ds, np.tile([1.0, 0, 0], (F, 1)))
        local[side + '_leg'] = Rl
        local[side + '_shin'] = rx(thl)

    # weapon: blade continues along the forearm (hand -Y); orc rest is -70 deg about X
    wrest = rig.rest[rig.idx['right_weapon']]
    local['right_weapon'] = np.tile(R.from_euler('x', 180, degrees=True).as_matrix(), (F, 1, 1))

    # root: hips displacement (scaled), then lift so the feet never go below the floor
    hips = o(g('Hips') - g('Hips')[0]) * s
    root = np.stack([hips[:, 0], hips[:, 1], hips[:, 2]], -1)
    world = rig.fk(local, root)
    foot_low = {k: rig.lowest_point(k) - rig.origin[rig.idx[k]][1] for k in ('right_shin', 'left_shin')}
    foot_y = np.min([world[k][0][:, 1] + foot_low[k] for k in foot_low], axis=0)
    lift = np.maximum(-foot_y, 0)
    root[:, 1] += lift
    world = rig.fk(local, root)
    foot_y = np.min([world[k][0][:, 1] + foot_low[k] for k in foot_low], axis=0)

    # weapon tip trace (world)
    tip_rest = rig.far_point('right_weapon')
    wp, wr_ = world['right_weapon']
    wo = rig.origin[rig.idx['right_weapon']]
    tip_local = tip_rest - wo   # cube coords are pre-rotation; the fk rotation is the full local pose
    tip = wp + np.einsum('fij,j->fi', wr_, tip_local)

    # export in viewer format: anim Euler = full Euler - rest Euler
    T = (np.arange(F) * b.dt).round(4).tolist()
    bones = {}
    for nm in rig.names:
        if nm in local:
            e = euler_continuous(local[nm]) - rig.rest[rig.idx[nm]]
            bones[nm] = {'r': np.round(e, 3).ravel().tolist()}
    bones['root']['p'] = np.round(root, 4).ravel().tolist()
    hand_y = world['right_hand'][0][:, 1]
    hit_f = int(np.argmin(g('RightHand')[:, 1] + g('LeftHand')[:, 1]))
    clip = {'length': round((F - 1) * b.dt, 4), 'loop': False, 'events': [[round(hit_f * b.dt, 4), 'hit']],
            'hits': [round(hit_f * b.dt, 4)], 'T': T, 'bones': bones}
    qa = {'frames': F, 'scale': round(float(s), 4), 'hit_frame': hit_f,
          'foot_min_y_after_lift': round(float(foot_y.min()), 3), 'max_lift_px': round(float(lift.max()), 3),
          'right_hand_y_px': [round(float(hand_y.min()), 2), round(float(hand_y.max()), 2)],
          'tip_y_px_min': round(float(tip[:, 1].min()), 2), 'tip_y_at_hit': round(float(tip[hit_f, 1]), 2),
          'tip_y_max': round(float(tip[:, 1].max()), 2)}
    return clip, qa


if __name__ == '__main__':
    bvh_path, model_path, out = sys.argv[1:4]
    clip, qa = retarget(bvh_path, model_path, sys.argv[4] if len(sys.argv) > 4 else 'mm_new_slam')
    json.dump(clip, open(out, 'w'))
    print(json.dumps(qa, indent=1))
