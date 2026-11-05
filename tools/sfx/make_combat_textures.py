#!/usr/bin/env python3
"""Draws the combat textures of specs/addon/combat-parry-warning.md from geometry (Python stdlib only: zlib, struct).

    python3 tools/sfx/make_combat_textures.py [assets_dir]

Writes under addon/src/main/resources/assets/lotr_revival/ (or assets_dir):
  textures/combat/warn_normal.png      white diamond with a dark rim (the normal swing marker)
  textures/combat/warn_heavy.png       red diamond with four spikes and a dark rim (heavy: the shape differs, not only colour)
  textures/combat/warn_ring.png        white ring that closes on the diamond (tinted, scaled by the renderer)
  textures/combat/warn_ring_heavy.png  the same, thicker
  textures/combat/offscreen_arrow.png  white arrowhead pointing up (rotated toward the attacker, tinted for heavy)
  textures/particle/parry_spark_0..3.png  a four-ray spark star shrinking over its life (white-gold, 8x8)
  textures/particle/heavy_warn_0..3.png   a red spiked mote fading over its life (8x8)
Every pixel is computed from shapes with 4x4 supersampling; nothing is traced or copied. Output is deterministic.
"""
import math
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
ASSETS = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'addon/src/main/resources/assets/lotr_revival')
SS = 4  # supersamples per axis


def png(path, w, h, pixels):
    """pixels: list of rows of (r, g, b, a) 0-255. Writes an RGBA PNG."""
    raw = b''.join(b'\x00' + bytes(c for px in row for c in px) for row in pixels)

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'wb') as f:
        f.write(data)
    print(os.path.relpath(path, ROOT))


def render(size, shade):
    """shade(x, y) -> (r, g, b, a) floats 0-1 or None, in [-1, 1] coordinates (y down); averaged over supersamples with
    premultiplied colour so edges do not darken."""
    rows = []
    for py in range(size):
        row = []
        for px in range(size):
            acc = [0.0, 0.0, 0.0, 0.0]
            for sy in range(SS):
                for sx in range(SS):
                    x = ((px + (sx + 0.5) / SS) / size) * 2 - 1
                    y = ((py + (sy + 0.5) / SS) / size) * 2 - 1
                    c = shade(x, y)
                    if c is None:
                        continue
                    r, g, b, a = c
                    acc[0] += r * a
                    acc[1] += g * a
                    acc[2] += b * a
                    acc[3] += a
            n = SS * SS
            a = acc[3] / n
            if a <= 0:
                row.append((0, 0, 0, 0))
            else:
                row.append(tuple(int(round(min(1, v / acc[3]) * 255)) for v in acc[:3]) + (int(round(a * 255)),))
        rows.append(row)
    return rows


def diamond(x, y):
    """Diamond distance: 1 on the edge of the unit diamond."""
    return abs(x) + abs(y)


def spiked(x, y, body, spike_len, spike_w):
    """Inside a diamond of half-diagonal body with four spikes (thin diamonds along the axes) reaching spike_len."""
    if diamond(x, y) <= body:
        return True
    for ax, ay in ((x, y), (y, x)):
        # a spike along the axis: a narrow diamond, width spike_w at the body edge, tip at spike_len
        along = abs(ax)
        if body * 0.5 <= along <= spike_len:
            half = spike_w * (spike_len - along) / (spike_len - body * 0.5)
            if abs(ay) <= half:
                return True
    return False


WHITE = (1.0, 1.0, 1.0)
RIM = (0.08, 0.08, 0.10)
RED = (0.92, 0.16, 0.12)
DARK_RED = (0.22, 0.02, 0.02)


def warn_normal(x, y):
    d = diamond(x, y)
    if d <= 0.62:
        shine = 0.9 + 0.1 * max(0.0, -(x + y))  # a faint highlight toward the top left
        return (min(1, shine), min(1, shine), min(1, shine), 1.0)
    if d <= 0.78:
        return RIM + (0.85,)
    return None


def warn_heavy(x, y):
    if spiked(x, y, 0.58, 0.98, 0.2):
        inner = spiked(x, y, 0.46, 0.86, 0.12)
        if inner:
            shine = 1.0 + 0.15 * max(0.0, -(x + y))
            return (min(1, RED[0] * shine), RED[1], RED[2], 1.0)
        return DARK_RED + (0.95,)
    return None


def ring(thickness):
    def shade(x, y):
        r = math.hypot(x, y)
        outer = 0.97
        if outer - thickness <= r <= outer:
            return WHITE + (1.0,)
        return None
    return shade


def arrow(x, y):
    # an arrowhead pointing up (toward -y): a triangle with a notch at the back, plus a dark rim
    def inside(x, y, grow):
        if y < -0.85 - grow or y > 0.75 + grow:
            return False
        t = (y + 0.85) / 1.6  # 0 at the tip, 1 at the base
        if abs(x) > t * 0.8 + grow:
            return False
        return y <= 0.75 - max(0.0, 0.4 - abs(x) * 0.5) + grow  # the back edge is notched toward the middle
    if inside(x, y, 0.0):
        return WHITE + (1.0,)
    if inside(x, y, 0.12):
        return RIM + (0.8,)
    return None


def spark(frame):
    # a four-ray star with a hot core; rays shorten and the glow cools over four frames
    length = [0.98, 0.8, 0.58, 0.36][frame]
    core = [0.32, 0.26, 0.2, 0.14][frame]
    warm = [0.0, 0.25, 0.5, 0.75][frame]
    col = (1.0, 1.0 - 0.15 * warm, 0.75 - 0.45 * warm)

    def shade(x, y):
        r = math.hypot(x, y)
        a = 0.0
        if r <= core:
            a = 1.0
        for ax, ay in ((x, y), (y, x)):
            if abs(ax) <= length:
                w = 0.16 * (1 - abs(ax) / length)
                if abs(ay) <= w:
                    a = max(a, 1.0)
        # the diagonals, shorter
        for ax, ay in (((x + y) / math.sqrt(2), (x - y) / math.sqrt(2)), ((x - y) / math.sqrt(2), (x + y) / math.sqrt(2))):
            if abs(ax) <= length * 0.55:
                w = 0.09 * (1 - abs(ax) / (length * 0.55))
                if abs(ay) <= w:
                    a = max(a, 0.8)
        return col + (a,) if a > 0 else None
    return shade


def heavy_mote(frame):
    body = [0.5, 0.46, 0.4, 0.32][frame]
    spike = [0.98, 0.86, 0.72, 0.56][frame]
    alpha = [1.0, 0.9, 0.75, 0.55][frame]

    def shade(x, y):
        if spiked(x, y, body, spike, 0.16):
            inner = diamond(x, y) <= body * 0.55
            c = (1.0, 0.55, 0.45) if inner else RED
            return c + (alpha,)
        return None
    return shade


def main():
    tex = os.path.join(ASSETS, 'textures')
    png(os.path.join(tex, 'combat/warn_normal.png'), 32, 32, render(32, warn_normal))
    png(os.path.join(tex, 'combat/warn_heavy.png'), 32, 32, render(32, warn_heavy))
    png(os.path.join(tex, 'combat/warn_ring.png'), 64, 64, render(64, ring(0.09)))
    png(os.path.join(tex, 'combat/warn_ring_heavy.png'), 64, 64, render(64, ring(0.16)))
    png(os.path.join(tex, 'combat/offscreen_arrow.png'), 16, 16, render(16, arrow))
    for i in range(4):
        png(os.path.join(tex, f'particle/parry_spark_{i}.png'), 8, 8, render(8, spark(i)))
        png(os.path.join(tex, f'particle/heavy_warn_{i}.png'), 8, 8, render(8, heavy_mote(i)))


if __name__ == '__main__':
    main()
