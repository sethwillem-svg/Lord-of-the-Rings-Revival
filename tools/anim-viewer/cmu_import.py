#!/usr/bin/env python3
"""Import the CMU Motion Capture Database takes used by the clip library into library/cmu/.

Source: the BVH conversion of the CMU database by Bruce Hahne (cgspeed), mirrored at
https://github.com/Shriinivas/cmubvh. "The data used in this project was obtained from mocap.cs.cmu.edu.
The database was created with funding from NSF EIA-0196217." CMU places no restrictions on the data and the
conversion adds none, so the cut clips are committed to the repo (library/cmu/LICENSE.txt).

Each take is cut to a short window and resampled from 120 to 30 fps, so the library stays small:
  - the window is TAKES[id] seconds long; it starts at the given second, or (None) where the body moves most
  - the first frame of every take (the calibration T-pose) is never used

  python3 tools/anim-viewer/cmu_import.py               all takes in TAKES (skips ones already imported)
  python3 tools/anim-viewer/cmu_import.py 17_08 54_02   only these (re-imports)
  python3 tools/anim-viewer/cmu_import.py --force       re-import everything
  python3 tools/anim-viewer/cmu_import.py --src DIR     use a local clone of the mirror instead of downloading

Downloads go through raw.githubusercontent.com; if that is blocked, a sparse git clone of the mirror is used
(cached in ~/.cache/lotr-anim-viewer/cmubvh). Needs only the Python standard library (and git for the fallback).
"""
import argparse
import io
import math
import os
import subprocess
import sys
import urllib.request
import zipfile

MIRROR = 'https://github.com/Shriinivas/cmubvh'
RAW = 'https://raw.githubusercontent.com/Shriinivas/cmubvh/main/'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'library', 'cmu')
CACHE = os.path.join(os.path.expanduser('~'), '.cache', 'lotr-anim-viewer', 'cmubvh')
FPS = 30

# id: (folder in the mirror, CMU description, window seconds (None: whole take), window start seconds (None: auto))
TAKES = {
    # orc
    '17_08': ('Sequence-015-019/17', "muscular, heavyset person's walk", 4, None),
    '17_01': ('Sequence-015-019/17', 'walk with anger, frustration', 4, None),
    '120_02': ('Sequence-113-128/120', 'Gorilla', 5, None),
    '13_17': ('Sequence-010-014/13', 'boxing', 4, None),
    '144_20': ('Sequence-141-144/144', 'Punch_Sequence', 5, None),
    '143_24': ('Sequence-141-144/143', 'Kicking', 4, None),
    '55_23': ('Sequence-046-056/55', 'devil', 5, None),
    '18_10': ('Sequence-015-019/18', 'quarrel - angry hand gestures (2 subjects - subject A)', 5, None),
    '142_22': ('Sequence-141-144/142', 'Sneaky', 5, None),
    '79_04': ('Sequence-076-080/79', 'digging', 5, None),
    # troll
    '54_02': ('Sequence-046-056/54', 'bear (human subject)', 5, None),
    '55_12': ('Sequence-046-056/55', 'dancing bear (human subject)', 5, None),
    '104_41': ('Sequence-102-111/104', 'ZombieWalk', 5, None),
    '79_45': ('Sequence-076-080/79', 'bear', 4, None),
    '79_63': ('Sequence-076-080/79', 'tiger', 5, None),
    '79_25': ('Sequence-076-080/79', 'moving heavy box', None, None),
    '140_01': ('Sequence-136-140/140', 'Get Up Face Down', None, None),
    '77_19': ('Sequence-076-080/77', 'limping, hurt right leg', 5, None),
    '136_01': ('Sequence-136-140/136', 'Walk Bent Forward', 5, None),
    '80_59': ('Sequence-076-080/80', 'panther', 5, None),
    # olog-hai
    '137_42': ('Sequence-136-140/137', 'Strong Man Walk', 5, None),
    '137_41': ('Sequence-136-140/137', 'Strong Man Wait', 6, None),
    '79_94': ('Sequence-076-080/79', 'flexing', 5, None),
    '79_93': ('Sequence-076-080/79', 'weight lifting', 6, None),
    '80_10': ('Sequence-076-080/80', 'boxing', 4, None),
    '142_11': ('Sequence-141-144/142', 'Marching', 4, None),
    '144_05': ('Sequence-141-144/144', 'Front_Kicking', 5, None),
    '120_22': ('Sequence-113-128/120', 'Zombie', 5, None),
    '80_48': ('Sequence-076-080/80', 'arguing', 5, None),
    '80_49': ('Sequence-076-080/80', 'gorilla', 5, None),
    # half-troll
    '137_16': ('Sequence-136-140/137', 'Drunk Walk', 5, None),
    '142_02': ('Sequence-141-144/142', 'Clumsy', 5, None),
    '132_54': ('Sequence-131-135/132', 'Walk With Wild Arms', 4, None),
    '20_08': ('Sequence-020-029/20', 'zombie march (2 subjects - subject A)', None, None),
    '14_01': ('Sequence-010-014/14', 'boxing', 4, None),
    '143_23': ('Sequence-141-144/143', 'Punching', 4, None),
    '140_03': ('Sequence-136-140/140', 'Get Up Laying on Side', None, None),
    '132_14': ('Sequence-131-135/132', 'Walk Duck Footed', 4, None),
    '77_09': ('Sequence-076-080/77', 'duck to avoid flying object', 4, None),
    '144_07': ('Sequence-141-144/144', 'Left_Blocks', 5, None),
    # man
    '138_01': ('Sequence-136-140/138', 'Marching', 4, None),
    '21_06': ('Sequence-020-029/21', 'soldiers march (2 subjects - subject B)', None, None),
    '02_07': ('Sequence-001-009/02', 'swordplay', 5, None),
    '02_08': ('Sequence-001-009/02', 'swordplay', 5, None),
    '13_26': ('Sequence-010-014/13', 'direct traffic, wave', 5, None),
    '82_09': ('Sequence-081-085/82', 'confident walk forward', 4, None),
    '77_03': ('Sequence-076-080/77', 'ready stance', 5, None),
    '137_32': ('Sequence-136-140/137', 'Old Man Wait', 6, None),
    '02_05': ('Sequence-001-009/02', 'punch/strike', 4, None),
    '02_09': ('Sequence-001-009/02', 'swordplay', 5, None),
    '124_07': ('Sequence-113-128/124', 'Baseball Swing', 5, None),
    '64_01': ('Sequence-060-075/64', 'Swing', None, None),
    '76_02': ('Sequence-076-080/76', 'walk backwards, feign a few attacks, then attack', None, None),
    '76_01': ('Sequence-076-080/76', 'walk backwards then attack with a punch', None, None),
    '144_11': ('Sequence-141-144/144', 'Left_Lunges', 4, None),
    '111_33': ('Sequence-102-111/111', 'Throwing', 5, None),
    '22_20': ('Sequence-020-029/22', 'violence - A picks up high stool, threatens to strike B (2 subjects - subject A)', None, None),
    # elf
    '137_24': ('Sequence-136-140/137', 'Graceful Lady Walk', 5, None),
    '79_86': ('Sequence-076-080/79', 'shooting bow and arrow', None, None),
    '135_07': ('Sequence-131-135/135', 'Mawashigeri', 4, None),
    '135_11': ('Sequence-131-135/135', 'Yokogeri', 4, None),
    '135_10': ('Sequence-131-135/135', 'Syutouuke', 4, None),
    '135_09': ('Sequence-131-135/135', 'Oiduki', 4, None),
    '135_05': ('Sequence-131-135/135', 'Gedanbarai', 4, None),
    '139_29': ('Sequence-136-140/139', 'Sneaking', 5, None),
    '143_25': ('Sequence-141-144/143', 'Waving', 4, None),
    '86_01': ('Sequence-086-094/86', 'jumps kicks and punches', 6, None),
    # dwarf
    '79_01': ('Sequence-076-080/79', 'chopping wood', None, None),
    '80_71': ('Sequence-076-080/80', 'chopping wood', 6, None),
    '62_07': ('Sequence-060-075/62', 'hammering a nail', 5, None),
    '62_08': ('Sequence-060-075/62', 'hammering a nail', 5, None),
    '80_05': ('Sequence-076-080/80', 'digging a hole', 6, None),
    '132_37': ('Sequence-131-135/132', 'Marching', 4, None),
    '80_19': ('Sequence-076-080/80', 'pushing broom', 5, None),
    '144_13': ('Sequence-141-144/144', 'Left_Punch_Sequence001', 5, None),
    '144_26': ('Sequence-141-144/144', 'Right_Blocks', 5, None),
    # weapon classes (extras next to the KayKit attacks)
    '62_10': ('Sequence-060-075/62', 'hammering sequence: one-handed overhead strikes (axe)', 5, None),
    '124_08': ('Sequence-113-128/124', 'Baseball Bunt: both hands on a level shaft held forward (spear guard)', 4, None),
    '144_17': ('Sequence-141-144/144', 'Lunges (spear footwork)', 4, None),
    '76_04': ('Sequence-076-080/76', 'defensive guard pose', 3.5, None),
    '79_69': ('Sequence-076-080/79', 'very happy', 5, None),
    # hobbit
    '139_31': ('Sequence-136-140/139', 'Sneaking', 5, None),
    '136_31': ('Sequence-136-140/136', 'Quail, quick little steps', 4, None),
    '79_26': ('Sequence-076-080/79', 'planting a tree', None, None),
    '79_87': ('Sequence-076-080/79', 'raking', 5, None),
    '136_09': ('Sequence-136-140/136', 'Walk Crouched', 5, None),
    '77_29': ('Sequence-076-080/77', 'creeping walk', 5, None),
    '79_73': ('Sequence-076-080/79', 'scared', 5, None),
    '137_33': ('Sequence-136-140/137', 'Old Man Walk', 5, None),
    '139_09': ('Sequence-136-140/139', 'Ducking', 4, None),
    '79_34': ('Sequence-076-080/79', 'fishing', None, None),
}


def zip_rel(take):
    folder = TAKES[take][0]
    return f'{folder}/Data/{take}.zip'


def fetch_http(take):
    with urllib.request.urlopen(RAW + zip_rel(take), timeout=60) as r:
        return r.read()


def fetch_git(takes):
    """Sparse, blobless clone of the mirror; checks out only the zips we need."""
    if not os.path.isdir(os.path.join(CACHE, '.git')):
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        subprocess.run(['git', 'clone', '-q', '--filter=blob:none', '--no-checkout', MIRROR, CACHE], check=True)
        subprocess.run(['git', '-C', CACHE, 'sparse-checkout', 'init', '--no-cone'], check=True)
    subprocess.run(['git', '-C', CACHE, 'sparse-checkout', 'add'] + ['/' + zip_rel(t) for t in takes], check=True)
    subprocess.run(['git', '-C', CACHE, 'checkout', '-q', 'main'], check=True)
    return CACHE


def read_bvh_from_zip(data, take):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        name = next(n for n in z.namelist() if n.lower().endswith('.bvh'))
        return z.read(name).decode('latin-1')


def wrap(d):
    return (d + 180.0) % 360.0 - 180.0


def cut(text, take):
    """Resample to 30 fps and keep the window; returns the new BVH text and a summary."""
    _, _, sec, start = TAKES[take]
    head, motion = text.split('MOTION', 1)
    lines = [l for l in motion.strip().splitlines() if l.strip()]
    dt = float(lines[1].split(':')[1])
    rows = [l.split() for l in lines[2:]]
    step = max(1, round((1.0 / FPS) / dt))
    rows = rows[1::step]                      # drop the calibration T-pose, then resample
    vals = [[float(x) for x in r] for r in rows]
    n = len(vals)
    win = n if sec is None else min(n, int(round(sec * FPS)))
    if start is not None:
        a = min(max(0, int(round(start * FPS))), n - win)
    else:                                     # window where the joints turn most (root position/rotation excluded)
        e = [0.0] + [sum(abs(wrap(b - c)) for b, c in zip(vals[i][6:], vals[i - 1][6:])) for i in range(1, n)]
        acc = [0.0]
        for x in e:
            acc.append(acc[-1] + x)
        a = max(range(0, n - win + 1), key=lambda i: acc[i + win] - acc[i])
    keep = rows[a:a + win]
    out = head + 'MOTION\nFrames: %d\nFrame Time: %.7f\n' % (len(keep), dt * step) + \
        '\n'.join(' '.join(r) for r in keep) + '\n'
    return out, '%s: %.1f-%.1f s of %.1f s' % (take, a / FPS, (a + win) / FPS, n / FPS)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('takes', nargs='*', help='take ids (default: all in TAKES)')
    ap.add_argument('--force', action='store_true', help='re-import takes that are already in library/cmu')
    ap.add_argument('--src', help='local clone of the cmubvh mirror')
    args = ap.parse_args()
    bad = [t for t in args.takes if t not in TAKES]
    if bad:
        sys.exit('Not in TAKES: %s (add them to the table first)' % ', '.join(bad))
    todo = [t for t in (args.takes or TAKES) if args.takes or args.force or
            not os.path.exists(os.path.join(OUT, t + '.bvh'))]
    if not todo:
        print('All takes already imported (use --force to redo).')
        return
    os.makedirs(OUT, exist_ok=True)
    src = args.src
    for t in todo:
        data = None
        if src:
            data = open(os.path.join(src, zip_rel(t)), 'rb').read()
        else:
            try:
                data = fetch_http(t)
            except Exception as e:          # raw downloads blocked (proxy): fall back to git
                print('HTTP download failed (%s); using a sparse git clone of the mirror.' % e)
                src = fetch_git(todo)
                data = open(os.path.join(src, zip_rel(t)), 'rb').read()
        bvh, info = cut(read_bvh_from_zip(data, t), t)
        tmp = os.path.join(OUT, t + '.bvh.part')
        with open(tmp, 'w', newline='\n') as f:
            f.write(bvh)
        os.replace(tmp, os.path.join(OUT, t + '.bvh'))
        print(info, '-', TAKES[t][1])


if __name__ == '__main__':
    main()
