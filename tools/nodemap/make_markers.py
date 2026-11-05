#!/usr/bin/env python3
"""Draws the node map marker sheet (spec node-map-travel.md, N13): our own 9 x 9 pixel icons, one row per node kind
(town, stronghold, crossing, ruin, landmark, camp, in NodeKind order) and one column per state (known, rumoured, barred,
selected). Standard library only (zlib, struct). Run from anywhere:

    python3 tools/nodemap/make_markers.py

It writes addon/src/main/resources/assets/lotr_revival/textures/gui/nodemap/markers.png (64 x 64, unused cells clear).
NodeMapOverlay reads cell (column * 9, row * 9). Change the masks or colours here and run it again; never hand-edit the PNG.
"""
import os
import struct
import zlib

CELL = 9
SHEET = 64
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "addon", "src", "main", "resources", "assets",
                   "lotr_revival", "textures", "gui", "nodemap", "markers.png")

# K outline, F fill (kind colour), D darker fill, H lighter fill, W water, . clear
KINDS = [
    ("town", 0xE0D8B0, [
        "....K....",
        "...KFK...",
        "..KFFFK..",
        ".KFFFFFK.",
        "KKKKKKKKK",
        ".KFFFFFK.",
        ".KFFDFFK.",
        ".KFFDFFK.",
        ".KKKKKKK.",
    ]),
    ("stronghold", 0x9A9A9A, [
        "KKK.K.KKK",
        "KFKKFKKFK",
        "KFFFFFFFK",
        ".KFFFFFK.",
        ".KFFDFFK.",
        ".KFFFFFK.",
        ".KFDDDFK.",
        ".KFDDDFK.",
        ".KKKKKKK.",
    ]),
    ("crossing", 0xB0A890, [
        ".........",
        "KKKKKKKKK",
        "KFFFFFFFK",
        "KFKKKKKFK",
        "KFK...KFK",
        "KK.WWW.KK",
        "..WW.WW..",
        ".WW...WW.",
        ".........",
    ]),
    ("ruin", 0xB08A6A, [
        ".K.....K.",
        "KFK...KFK",
        "KFK.K.KFK",
        "KFKKFKKFK",
        "KFFFFFFFK",
        "KFDFFDFFK",
        "KFFFFFFFK",
        "KKKKKKKKK",
        ".........",
    ]),
    ("landmark", 0xB0D080, [
        "....K....",
        "...KFK...",
        "...KFK...",
        "..KFFFK..",
        "..KFHFK..",
        "..KFHFK..",
        "..KFFFK..",
        ".KKFFFKK.",
        "KKKKKKKKK",
    ]),
    ("camp", 0x60C060, [
        "....K....",
        "...KFK...",
        "..KFFFK..",
        "..KFDFK..",
        ".KFFDFFK.",
        ".KFDDDFK.",
        "KFFDDDFFK",
        "KKKKKKKKK",
        ".........",
    ]),
]
STATES = ["known", "rumoured", "barred", "selected"]

OUTLINE = (0x20, 0x18, 0x10)
RUMOUR = (0xA0, 0xA0, 0xA0)
GOLD = (0xE8, 0xC0, 0x40)
RED = (0xE0, 0x30, 0x30)
WATER = (0x7F, 0xB0, 0xE0)


def rgb(c):
    return (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF


def scale(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def grey(c):
    v = int(0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    return v, v, v


def cell(mask, colour, state):
    """9 x 9 RGBA pixels (None = clear) for one kind in one state."""
    fill = rgb(colour)
    px = [[None] * CELL for _ in range(CELL)]
    for y, row in enumerate(mask):
        assert len(row) == CELL, (mask, row)
        for x, ch in enumerate(row):
            c = {"K": OUTLINE, "F": fill, "D": scale(fill, 0.65), "H": scale(fill, 1.25), "W": WATER}.get(ch)
            if c is None:
                continue
            if state == "rumoured":
                c = RUMOUR if ch == "K" else None   # outline only
            elif state == "barred":
                c = c if ch == "K" else scale(grey(c), 0.8)
            elif state == "selected" and ch == "K":
                c = GOLD
            if c is not None:
                px[y][x] = c + (255,)
    if state == "barred":
        for i in range(1, CELL - 1):
            px[i][i] = RED + (255,)
            px[i][CELL - 1 - i] = RED + (255,)
    return px


def png(width, height, rows):
    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + bytes(v for p in row for v in p) for row in rows)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


def main():
    sheet = [[(0, 0, 0, 0)] * SHEET for _ in range(SHEET)]
    for r, (name, colour, mask) in enumerate(KINDS):
        for c, state in enumerate(STATES):
            px = cell(mask, colour, state)
            for y in range(CELL):
                for x in range(CELL):
                    if px[y][x] is not None:
                        sheet[r * CELL + y][c * CELL + x] = px[y][x]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(png(SHEET, SHEET, sheet))
    print("wrote", os.path.normpath(OUT), f"({len(KINDS)} kinds x {len(STATES)} states, {CELL} px cells)")


if __name__ == "__main__":
    main()
