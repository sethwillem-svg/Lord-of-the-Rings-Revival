"""Minimal BVH reader + forward kinematics (numpy/scipy only)."""
import numpy as np
from scipy.spatial.transform import Rotation as R


class BVH:
    def __init__(self, path):
        self.names, self.parent, self.offset, self.chan = [], [], [], []
        stack, cur = [], -1
        lines = open(path).read().split('\n')
        i = 0
        while i < len(lines):
            t = lines[i].split()
            i += 1
            if not t:
                continue
            if t[0] in ('ROOT', 'JOINT'):
                self.names.append(t[1])
                self.parent.append(stack[-1] if stack else -1)
                self.offset.append(np.zeros(3))
                self.chan.append([])
                cur = len(self.names) - 1
            elif t[0] == 'End':
                self.names.append(self.names[stack[-1]] + '_End')
                self.parent.append(stack[-1])
                self.offset.append(np.zeros(3))
                self.chan.append([])
                cur = len(self.names) - 1
            elif t[0] == '{':
                stack.append(cur)
            elif t[0] == '}':
                stack.pop()
            elif t[0] == 'OFFSET':
                self.offset[cur] = np.array(t[1:4], float)
            elif t[0] == 'CHANNELS':
                self.chan[cur] = t[2:2 + int(t[1])]
            elif t[0] == 'MOTION':
                break
        self.n = int(lines[i].split()[1])
        self.dt = float(lines[i + 1].split()[2])
        rows = [list(map(float, l.split())) for l in lines[i + 2:i + 2 + self.n] if l.strip()]
        self.data = np.array(rows)
        self.offset = np.array(self.offset)
        self.idx = {k: j for j, k in enumerate(self.names)}
        self.cols, c = [], 0
        for ch in self.chan:
            self.cols.append(list(range(c, c + len(ch))))
            c += len(ch)

    def local(self, j):
        """(local position (F,3), local rotation) for joint j."""
        F = self.n
        pos = np.tile(self.offset[j], (F, 1))
        seq, ang = '', []
        for c, k in zip(self.cols[j], self.chan[j]):
            v = self.data[:, c]
            if k.endswith('position'):
                pos[:, 'XYZ'.index(k[0])] = v
            else:
                seq += k[0]
                ang.append(v)
        rot = R.from_euler(seq, np.stack(ang, 1), degrees=True) if seq else R.identity(F)
        return pos, rot

    def fk(self):
        """World positions (F,J,3) and world rotations (list of J Rotation)."""
        F, J = self.n, len(self.names)
        P = np.zeros((F, J, 3))
        Rw = [None] * J
        for j in range(J):
            pos, rot = self.local(j)
            p = self.parent[j]
            if p < 0:
                P[:, j], Rw[j] = pos, rot
            else:
                P[:, j] = P[:, p] + Rw[p].apply(pos)
                Rw[j] = Rw[p] * rot
        return P, Rw
