#!/usr/bin/env python3
"""Placeholder bolt textures for The Lord of the Rings: Revival (ticket #121).

Draws our own procedural art, standard library only:

* `textures/item/bolt.png` (16x16): a short iron-tipped shaft with a feather fletching.
* `textures/entity/projectiles/bolt.png` (32x32): the in-flight bolt, laid out for the arrow-style
  model (shaft strip 16x5 at the top left, nock end cap 5x5 below it).

Run from the repository root:

    python3 -I tools/gen_bolt_textures.py

Final art replaces these through the art agents (docs/tooling.md).
"""

import os
import struct
import sys
import zlib

OUT = os.path.join("src", "main", "resources", "assets", "lotrrevival", "textures")

OUTLINE = (0x2A, 0x1A, 0x0C)
WOOD_DARK = (0x5A, 0x3A, 0x1C)
WOOD_MID = (0x82, 0x58, 0x2C)
WOOD_LIGHT = (0xA8, 0x7A, 0x42)
METAL_DARK = (0x6E, 0x70, 0x76)
METAL_MID = (0xA5, 0xA8, 0xAE)
METAL_LIGHT = (0xD6, 0xD8, 0xDC)
FEATHER_DARK = (0x9C, 0x9C, 0xA4)
FEATHER_LIGHT = (0xE8, 0xE8, 0xEC)


def png(width, height, px):
    raw = bytearray()
    for row in px:
        raw.append(0)
        for p in row:
            raw.extend((0, 0, 0, 0) if p is None else (p[0], p[1], p[2], 255))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def grid(w, h):
    return [[None] * w for _ in range(h)]


def line(px, x0, y0, x1, y1, color):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        if 0 <= y0 < len(px) and 0 <= x0 < len(px[0]):
            px[y0][x0] = color
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def item_sprite():
    px = grid(16, 16)
    line(px, 3, 12, 11, 4, WOOD_MID)          # shaft
    line(px, 4, 12, 12, 4, WOOD_DARK)
    line(px, 2, 11, 5, 14, FEATHER_LIGHT)     # fletching
    line(px, 1, 11, 4, 14, FEATHER_DARK)
    line(px, 2, 10, 4, 12, FEATHER_LIGHT)
    px[3][12] = METAL_LIGHT                   # head
    px[4][12] = METAL_MID
    px[3][11] = METAL_MID
    px[4][13] = METAL_DARK
    px[2][13] = METAL_LIGHT
    px[3][13] = METAL_MID
    px[2][12] = METAL_MID
    px[1][14] = METAL_LIGHT
    px[2][14] = METAL_DARK
    # outline
    add = []
    for y in range(16):
        for x in range(16):
            if px[y][x] is None:
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < 16 and 0 <= ny < 16 and px[ny][nx] is not None:
                        add.append((x, y))
                        break
    for x, y in add:
        px[y][x] = OUTLINE
    return png(16, 16, px)


def entity_sprite():
    px = grid(32, 32)
    # shaft strip, 16 wide x 5 tall: fletching left, wood middle, metal head right
    for y in range(5):
        for x in range(16):
            if x < 4:
                px[y][x] = FEATHER_LIGHT if (x + y) % 2 == 0 else FEATHER_DARK
            elif x < 12:
                px[y][x] = (WOOD_LIGHT, WOOD_MID, WOOD_MID, WOOD_MID, WOOD_DARK)[y]
            else:
                px[y][x] = (METAL_LIGHT, METAL_MID, METAL_MID, METAL_DARK, METAL_DARK)[y]
    # nock end cap, 5 wide x 5 tall below the strip
    for y in range(5, 10):
        for x in range(5):
            px[y][x] = WOOD_MID if (x + y) % 2 == 0 else WOOD_DARK
    return png(32, 32, px)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def main():
    write(os.path.join(OUT, "item", "bolt.png"), item_sprite())
    write(os.path.join(OUT, "entity", "projectiles", "bolt.png"), entity_sprite())
    return 0


if __name__ == "__main__":
    sys.exit(main())
