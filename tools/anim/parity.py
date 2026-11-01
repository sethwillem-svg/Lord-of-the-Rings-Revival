"""Check that the browser retargeter (tools/anim-viewer/retarget.js) matches this Python reference.

Usage: python3 tools/anim/parity.py clip.bvh model.bbmodel
Needs node, numpy and scipy. Only covers rigs the Python reference supports (the muster-pack bone names)
and the default settings. Prints the largest per-bone rotation difference in degrees; exits 1 above 0.01.
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.spatial.transform import Rotation as R

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from retarget3 import retarget  # noqa: E402

bvh, model = sys.argv[1:3]
py, _ = retarget(bvh, model)
with tempfile.NamedTemporaryFile('r', suffix='.json') as out:
    js = os.path.join(HERE, '..', 'anim-viewer')
    script = (f"const A=require({json.dumps(js + '/retarget.js')}),M=require({json.dumps(js + '/rigs.js')}),fs=require('fs');"
              f"const b=A.parseBVH(fs.readFileSync({json.dumps(bvh)},'utf8'));"
              f"const r=A.parseRig(JSON.parse(fs.readFileSync({json.dumps(model)},'utf8')));"
              f"fs.writeFileSync({json.dumps(out.name)},JSON.stringify(A.toViewerClip(A.retarget(b,r,A.detectMap(r,M)),r)));")
    subprocess.run(['node', '-e', script], check=True)
    jsc = json.load(open(out.name))

worst = 0.0
for bone, ch in py['bones'].items():
    rb = np.array(ch['r']).reshape(-1, 3)
    ra = np.array(jsc['bones'][bone]['r']).reshape(-1, 3) if bone in jsc['bones'] else np.zeros_like(rb)
    d = np.degrees((R.from_euler('xyz', ra, degrees=True).inv() * R.from_euler('xyz', rb, degrees=True)).magnitude())
    worst = max(worst, float(d.max()))
    print(f'{bone:16s} {d.max():.4f} deg')
dp = np.abs(np.array(py['bones']['root']['p']) - np.array(jsc['bones']['root']['p'])).max()
print(f'root position max diff {dp:.5f} px\nworst rotation diff {worst:.4f} deg')
sys.exit(0 if worst < 0.01 and dp < 1e-3 else 1)
