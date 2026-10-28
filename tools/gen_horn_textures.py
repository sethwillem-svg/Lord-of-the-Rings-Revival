#!/usr/bin/env python3
"""Placeholder textures for the command horn and command sword (our own 16x16 sprites).

Standard library only. Run from the repository root:

    python3 -I tools/gen_horn_textures.py

Writes `command_horn.png` and `command_sword.png` to
`src/main/resources/assets/lotrrevival/textures/item/`. Final art replaces them through the art agents.
"""

import os
import struct
import sys
import zlib

OUT = os.path.join("src", "main", "resources", "assets", "lotrrevival", "textures", "item")

PAL = {
    ".": (0, 0, 0, 0),
    "o": (58, 40, 24, 255),
    "b": (176, 128, 60, 255),
    "B": (214, 170, 92, 255),
    "g": (232, 200, 96, 255),
    "i": (190, 196, 204, 255),
    "l": (120, 78, 40, 255),
}

HORN = [
    "................",
    "................",
    "..........ooo...",
    ".........obBBo..",
    "........obBBBo..",
    ".......obBBBgo..",
    "......obBBBgo...",
    ".....obBBBgo....",
    "....obBBBgo.....",
    "...obBBBgo......",
    "..obBBBgo.......",
    "..obBBgo........",
    "..oBBgo.........",
    "...ooo..........",
    "................",
    "................",
]

SWORD = [
    "................",
    "..............oo",
    ".............oii",
    "............oiio",
    "...........oiio.",
    "..........oiio..",
    ".........oiio...",
    "........oiio....",
    ".o.....oiio.....",
    ".ogo..oiio......",
    "..ogooiio.......",
    "...ogoio........",
    "...olgo.........",
    "..olo.go........",
    ".ol...oo........",
    "oo..............",
]


def write(path, rows):
    raw = b""
    for row in rows:
        raw += b"\x00" + b"".join(bytes(PAL[ch]) for ch in row)

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else OUT
    os.makedirs(out, exist_ok=True)
    write(os.path.join(out, "command_horn.png"), HORN)
    write(os.path.join(out, "command_sword.png"), SWORD)
