#!/usr/bin/env python3
"""Placeholder textures for the alloy forge and orc forge (our own procedural sprites).

Standard library only. Run from the repository root:

    python3 -I tools/gen_forge_textures.py

Writes block faces to `textures/block/`, the menu background to `textures/gui/container/forge.png` and the flame and
progress sprites to `textures/gui/sprites/container/forge/`. Final art replaces them through the art agents.
"""

import os
import struct
import sys
import zlib

ASSETS = os.path.join("src", "main", "resources", "assets", "lotrrevival", "textures")

TRANSPARENT = (0, 0, 0, 0)


class Image:
    def __init__(self, width, height, fill=TRANSPARENT):
        self.width = width
        self.height = height
        self.px = [[fill for _ in range(width)] for _ in range(height)]

    def set(self, x, y, color):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.px[y][x] = color

    def rect(self, x, y, w, h, color):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.set(xx, yy, color)

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        raw = b""
        for row in self.px:
            raw += b"\x00" + b"".join(bytes(c) for c in row)

        def chunk(kind, data):
            body = kind + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

        png = (
            b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b"")
        )
        with open(path, "wb") as f:
            f.write(png)


def shade(color, delta):
    return tuple(max(0, min(255, c + delta)) for c in color[:3]) + (255,)


# Per-variant palettes: base brick colour, mortar colour.
VARIANTS = {
    "alloy_forge": {"brick": (126, 126, 130, 255), "mortar": (82, 82, 86, 255)},
    "orc_forge": {"brick": (58, 52, 60, 255), "mortar": (30, 26, 32, 255)},
}


def bricks(palette):
    img = Image(16, 16, palette["mortar"])
    for row in range(4):
        y = row * 4
        offset = 0 if row % 2 == 0 else 4
        for start in range(-8 + offset, 16, 8):
            for yy in range(y, y + 3):
                for xx in range(start, start + 7):
                    # Slight per-brick variation so the face is not flat.
                    tone = ((start * 7 + row * 13) % 5 - 2) * 3
                    img.set(xx, yy, shade(palette["brick"], tone))
    return img


def top(palette):
    img = Image(16, 16, shade(palette["brick"], -10))
    img.rect(0, 0, 16, 1, palette["mortar"])
    img.rect(0, 15, 16, 1, palette["mortar"])
    img.rect(0, 0, 1, 16, palette["mortar"])
    img.rect(15, 0, 1, 16, palette["mortar"])
    # A dark flue opening.
    img.rect(5, 5, 6, 6, (20, 18, 20, 255))
    img.rect(6, 6, 4, 4, (36, 32, 36, 255))
    return img


def front(palette, lit):
    img = bricks(palette)
    # Firebox: a dark arch opening, glowing when lit.
    img.rect(3, 6, 10, 8, (18, 16, 18, 255))
    img.rect(4, 5, 8, 1, (18, 16, 18, 255))
    if lit:
        img.rect(4, 9, 8, 4, (222, 112, 28, 255))
        img.rect(5, 8, 6, 1, (244, 168, 52, 255))
        img.rect(6, 10, 4, 2, (255, 214, 96, 255))
    else:
        img.rect(4, 11, 8, 2, (44, 40, 44, 255))
    # Lintel above the opening.
    img.rect(2, 4, 12, 1, shade(palette["mortar"], -12))
    return img


def slot(img, x, y):
    """A vanilla-style 18x18 slot frame whose item square starts at (x, y)."""
    dark, light, inner = (55, 55, 55, 255), (255, 255, 255, 255), (139, 139, 139, 255)
    img.rect(x - 1, y - 1, 18, 18, inner)
    img.rect(x - 1, y - 1, 17, 1, dark)
    img.rect(x - 1, y - 1, 1, 17, dark)
    img.rect(x, y + 16, 17, 1, light)
    img.rect(x + 16, y, 1, 17, light)


def background():
    img = Image(176, 166, (198, 198, 198, 255))
    black, white, shadow = (0, 0, 0, 255), (255, 255, 255, 255), (85, 85, 85, 255)
    # Outer frame, light top-left bevel and dark bottom-right bevel.
    img.rect(0, 0, 176, 1, black)
    img.rect(0, 165, 176, 1, black)
    img.rect(0, 0, 1, 166, black)
    img.rect(175, 0, 1, 166, black)
    img.rect(1, 1, 174, 2, white)
    img.rect(1, 1, 2, 164, white)
    img.rect(3, 163, 172, 2, shadow)
    img.rect(173, 3, 2, 161, shadow)
    # The rounded corners of the vanilla frame.
    for (x, y) in ((0, 0), (175, 0), (0, 165), (175, 165)):
        img.set(x, y, TRANSPARENT)

    lane_x = [44 + 22 * i for i in range(4)]
    for x in lane_x:
        slot(img, x, 16)  # alloy
        slot(img, x, 36)  # input
        slot(img, x, 63)  # output
        img.rect(x, 55, 16, 6, (92, 92, 92, 255))  # progress trough
    slot(img, 14, 40)  # fuel
    img.rect(15, 22, 14, 14, (92, 92, 92, 255))  # flame trough

    for row in range(3):
        for col in range(9):
            slot(img, 8 + col * 18, 84 + row * 18)
    for col in range(9):
        slot(img, 8 + col * 18, 142)
    return img


def flame():
    img = Image(14, 14)
    rows = [
        "......rr......",
        "......rro.....",
        ".....rroo.....",
        ".....rroor....",
        "....rrooor....",
        "...rrooooor...",
        "...rroooyyor..",
        "..rrooyyyyor..",
        "..rroyyyyyor..",
        "..rrooyyyyor..",
        "..rrooyywyor..",
        "...rroyyyor...",
        "....rrooor....",
        ".....rrrr.....",
    ]
    pal = {
        ".": TRANSPARENT,
        "r": (176, 52, 16, 255),
        "o": (232, 120, 28, 255),
        "y": (252, 196, 64, 255),
        "w": (255, 240, 170, 255),
    }
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            img.set(x, y, pal[ch])
    return img


def progress():
    img = Image(16, 6, (240, 150, 40, 255))
    img.rect(0, 0, 16, 1, (255, 214, 96, 255))
    img.rect(0, 5, 16, 1, (176, 80, 16, 255))
    return img


def main(root):
    for name, palette in VARIANTS.items():
        block = os.path.join(root, "block")
        bricks(palette).save(os.path.join(block, name + "_side.png"))
        top(palette).save(os.path.join(block, name + "_top.png"))
        front(palette, False).save(os.path.join(block, name + "_front.png"))
        front(palette, True).save(os.path.join(block, name + "_front_on.png"))
    background().save(os.path.join(root, "gui", "container", "forge.png"))
    sprites = os.path.join(root, "gui", "sprites", "container", "forge")
    flame().save(os.path.join(sprites, "flame.png"))
    progress().save(os.path.join(sprites, "progress.png"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else ASSETS)
