#!/usr/bin/env python3
"""Summarise addon/run/viewer_export (written by ./addon/bench.sh export) so problems can be spotted without the viewer:
NPC files, meshes per body part with their bounds (px, part space), and whether each texture has visible pixels.

    python3 tools/anim-viewer/check_export.py [how many NPCs to list, default 3]
"""
import json
import struct
import sys
import zlib
from pathlib import Path

DIR = Path(__file__).resolve().parents[2] / 'addon/run/viewer_export'


def png_alpha_share(path):
    """Share of pixels with alpha > 0 in an 8-bit RGBA PNG (what NativeImage writes); None if another format."""
    data = path.read_bytes()
    pos, idat = 8, b''
    w = h = 0
    ctype = depth = None
    while pos < len(data):
        n, kind = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + n]
        if kind == b'IHDR':
            w, h, depth, ctype = struct.unpack('>IIBB', body[:10])
        elif kind == b'IDAT':
            idat += body
        pos += 12 + n
    if depth != 8 or ctype != 6:
        return None
    raw, stride, prev, seen, rows = zlib.decompress(idat), w * 4, bytearray(w * 4), 0, []
    i = 0
    for _ in range(h):
        f, line = raw[i], bytearray(raw[i + 1:i + 1 + stride])
        i += 1 + stride
        for x in range(stride):
            a = line[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if f == 1: line[x] = (line[x] + a) & 255
            elif f == 2: line[x] = (line[x] + b) & 255
            elif f == 3: line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        seen += sum(1 for x in range(3, stride, 4) if line[x])
        prev = line
    return seen / max(1, w * h), w, h


def main():
    show = int(sys.argv[1]) if len(sys.argv) > 1 else 3
    index_file = DIR / 'index.json'
    if not index_file.exists():
        print(f'No export at {DIR}. Run: ./addon/bench.sh export')
        return
    index = json.loads(index_file.read_text())
    print(f"{DIR}: {len(index['npcs'])} NPCs, {len(index['textures'])} textures")
    for t, tex in enumerate(index['textures']):
        p = DIR / tex['file']
        r = png_alpha_share(p) if p.exists() else 'missing'
        if r is None: r = 'not 8-bit RGBA'
        elif isinstance(r, tuple): r = f'{r[1]}x{r[2]}, {r[0] * 100:.0f}% visible pixels'
        print(f"  tex_{t}: {tex['source']}  {r}")
    for entry in index['npcs'][:show]:
        npc = json.loads((DIR / entry['file']).read_text())
        print(f"\n{entry['file']}: {npc['label']}  race={npc['race']} scale={npc['scale']} width={npc['widthScale']}")
        parts = {}
        for m in npc['meshes']:
            pos = m['pos']
            if not pos:
                continue
            xs, ys, zs = pos[0::3], pos[1::3], pos[2::3]
            key = m['part'] + (' (held)' if m.get('hand') else '')
            p = parts.setdefault(key, {'meshes': 0, 'tris': 0, 'tex': set(), 'lo': [1e9] * 3, 'hi': [-1e9] * 3})
            p['meshes'] += 1
            p['tris'] += len(m['idx']) // 3
            p['tex'].add(m['tex'])
            p['lo'] = [min(p['lo'][0], min(xs)), min(p['lo'][1], min(ys)), min(p['lo'][2], min(zs))]
            p['hi'] = [max(p['hi'][0], max(xs)), max(p['hi'][1], max(ys)), max(p['hi'][2], max(zs))]
        for k, p in parts.items():
            lo = ','.join(f'{v:.1f}' for v in p['lo'])
            hi = ','.join(f'{v:.1f}' for v in p['hi'])
            print(f"  {k:18} {p['meshes']:3} meshes {p['tris']:5} triangles  textures {sorted(p['tex'])}  bounds {lo} .. {hi}")


if __name__ == '__main__':
    main()
