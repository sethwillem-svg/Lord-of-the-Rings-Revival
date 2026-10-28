#!/usr/bin/env python3
"""Placeholder gear textures for The Lord of the Rings: Revival.

Draws our own 16x16 item sprites per weapon class / tool / armour piece / ranged weapon and our own
64x32 armour layers, recoloured from a per-material palette. Everything here is original and
procedural; final art replaces these files through the art agents (docs/tooling.md).

Standard library only. Run from the repository root:

    python3 -I tools/gen_gear_textures.py weapons gondor gondor sword dagger spear pike battleaxe hammer lance
    python3 -I tools/gen_gear_textures.py tools bronze bronze pickaxe axe shovel hoe
    python3 -I tools/gen_gear_textures.py armor gondor gondor
    python3 -I tools/gen_gear_textures.py bow gondor_bow gondor
    python3 -I tools/gen_gear_textures.py crossbow iron_crossbow iron
    python3 -I tools/gen_gear_textures.py sling sling leather
    python3 -I tools/gen_gear_textures.py list

Arguments are `<command> <set-or-id> <palette> [classes...]`. Item sprites go to
`src/main/resources/assets/lotrrevival/textures/item/<set>_<class>.png`, armour pieces to
`.../item/<set>_<piece>.png` and layers to `.../models/armor/<set>_layer_1.png` / `_layer_2.png`.
Use `--out DIR` to write somewhere else.
"""

import os
import struct
import sys
import zlib

DEFAULT_OUT = os.path.join("src", "main", "resources", "assets", "lotrrevival", "textures")

# name: (outline, dark, mid, light). Metals first, then cloth.
PALETTES = {
    "bronze": ((0x3B, 0x24, 0x10), (0x8C, 0x55, 0x1E), (0xC0, 0x7A, 0x2E), (0xE8, 0xB0, 0x5C)),
    "iron": ((0x2A, 0x2A, 0x2E), (0x6E, 0x70, 0x76), (0xA5, 0xA8, 0xAE), (0xD6, 0xD8, 0xDC)),
    "ranger": ((0x1E, 0x28, 0x22), (0x4F, 0x5E, 0x54), (0x7E, 0x90, 0x84), (0xA8, 0xBC, 0xAE)),
    "gondor": ((0x26, 0x2B, 0x38), (0x70, 0x78, 0x8C), (0xB4, 0xBC, 0xCE), (0xEA, 0xEE, 0xF6)),
    "orc_steel": ((0x16, 0x10, 0x12), (0x3A, 0x30, 0x34), (0x5E, 0x4A, 0x4E), (0x8E, 0x3A, 0x34)),
    "mithril": ((0x1E, 0x2C, 0x3C), (0x5A, 0x7E, 0xA2), (0x9C, 0xC4, 0xE4), (0xE6, 0xF6, 0xFF)),
    "leather": ((0x2C, 0x1A, 0x0E), (0x6B, 0x42, 0x26), (0x9A, 0x62, 0x38), (0xC4, 0x8C, 0x56)),
    "wool": ((0x70, 0x70, 0x78), (0xB8, 0xB8, 0xC0), (0xE2, 0xE2, 0xE8), (0xFF, 0xFF, 0xFF)),
    "shire": ((0x24, 0x30, 0x14), (0x4C, 0x66, 0x2C), (0x74, 0x92, 0x44), (0xA0, 0xBC, 0x68)),
    "bree": ((0x2A, 0x1C, 0x14), (0x5C, 0x44, 0x34), (0x86, 0x68, 0x4E), (0xB0, 0x92, 0x72)),
}
HANDLE = ((0x2A, 0x1A, 0x0C), (0x5A, 0x3A, 0x1C), (0x82, 0x58, 0x2C), (0xA8, 0x7A, 0x42))


class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]

    def set(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = color

    def line(self, x0, y0, x1, y1, color):
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx = 1 if x0 < x1 else -1
        sy = 1 if y0 < y1 else -1
        err = dx + dy
        while True:
            self.set(x0, y0, color)
            if x0 == x1 and y0 == y1:
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x0 += sx
            if e2 <= dx:
                err += dx
                y0 += sy

    def rect(self, x0, y0, x1, y1, color):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, color)

    def outline(self, color):
        """Adds a 1px outline around opaque pixels (4-neighbourhood)."""
        add = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is None:
                    for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                        if 0 <= nx < self.w and 0 <= ny < self.h and self.px[ny][nx] is not None:
                            add.append((x, y))
                            break
        for x, y in add:
            self.px[y][x] = color

    def png(self):
        raw = bytearray()
        for row in self.px:
            raw.append(0)
            for p in row:
                raw.extend((0, 0, 0, 0) if p is None else (p[0], p[1], p[2], 255))
        def chunk(tag, data):
            body = tag + data
            return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)
        return (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", struct.pack(">IIBBBBB", self.w, self.h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
                + chunk(b"IEND", b""))


def blade(c, x0, y0, x1, y1, pal):
    """A two-pixel-wide diagonal blade: light edge, mid body."""
    c.line(x0, y0, x1, y1, pal[2])
    c.line(x0 + 1, y0, x1 + 1, y1 - 0, pal[1])
    c.line(x0, y0 - 1, x1, y1 - 1, pal[3])


def shaft(c, x0, y0, x1, y1):
    c.line(x0, y0, x1, y1, HANDLE[2])
    c.line(x0 + 1, y0, x1 + 1, y1, HANDLE[1])


# ---------------------------------------------------------------- weapon sprites

def draw_sword(c, pal):
    blade(c, 5, 10, 13, 2, pal)
    c.line(3, 9, 7, 13, pal[1])          # guard
    c.line(4, 9, 8, 13, pal[2])
    shaft(c, 1, 14, 4, 11)
    c.set(1, 14, pal[1])


def draw_dagger(c, pal):
    blade(c, 7, 9, 12, 4, pal)
    c.line(5, 9, 8, 12, pal[1])
    shaft(c, 2, 13, 5, 10)


def head_spear(c, pal, tipx, tipy):
    c.set(tipx, tipy, pal[3])
    c.set(tipx - 1, tipy + 1, pal[2])
    c.set(tipx, tipy + 1, pal[2])
    c.set(tipx - 1, tipy + 2, pal[1])
    c.set(tipx - 2, tipy + 2, pal[2])
    c.set(tipx - 2, tipy + 3, pal[1])


def draw_spear(c, pal):
    shaft(c, 1, 14, 11, 4)
    head_spear(c, pal, 14, 1)


def draw_polearm(c, pal):
    shaft(c, 1, 14, 11, 4)
    head_spear(c, pal, 14, 1)
    c.line(9, 3, 13, 7, pal[2])          # side blade
    c.line(10, 3, 13, 6, pal[1])
    c.set(9, 3, pal[3])


def draw_pike(c, pal):
    shaft(c, 0, 15, 12, 3)
    c.line(13, 2, 15, 0, pal[3])
    c.line(12, 2, 14, 0, pal[2])
    c.set(13, 3, pal[1])


def draw_lance(c, pal):
    shaft(c, 0, 15, 12, 3)
    c.line(13, 2, 15, 0, pal[3])
    c.line(12, 2, 14, 0, pal[2])
    for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 1), (1, -1), (-1, 2), (2, -1)):  # vamplate
        c.set(4 + dx, 11 + dy, pal[1] if dx + dy > 1 else pal[2])
    c.set(3, 12, pal[3])


def draw_battleaxe(c, pal):
    shaft(c, 2, 14, 11, 5)
    c.line(8, 1, 13, 6, pal[2])
    c.line(9, 1, 13, 5, pal[1])
    c.line(7, 2, 12, 7, pal[2])
    c.line(7, 3, 11, 7, pal[3])
    c.set(8, 1, pal[3])
    c.set(13, 6, pal[3])


def draw_hammer(c, pal):
    shaft(c, 2, 14, 10, 6)
    c.rect(8, 1, 14, 5, pal[2])
    c.rect(8, 1, 14, 2, pal[3])
    c.rect(12, 3, 14, 5, pal[1])
    c.line(7, 6, 11, 6, pal[1])


def draw_throwing_axe(c, pal):
    shaft(c, 2, 13, 9, 6)
    c.line(8, 3, 12, 7, pal[2])
    c.line(9, 3, 12, 6, pal[1])
    c.line(8, 4, 11, 7, pal[3])


def draw_pickaxe(c, pal):
    shaft(c, 2, 14, 10, 6)
    c.line(4, 4, 12, 4, pal[2])
    c.line(5, 3, 11, 3, pal[3])
    c.line(3, 5, 4, 4, pal[1])
    c.line(12, 4, 13, 5, pal[1])
    c.line(12, 5, 13, 6, pal[1])


def draw_axe(c, pal):
    shaft(c, 2, 14, 10, 6)
    c.rect(8, 2, 12, 6, pal[2])
    c.line(8, 2, 12, 2, pal[3])
    c.line(12, 2, 12, 6, pal[1])
    c.line(7, 3, 7, 5, pal[1])


def draw_shovel(c, pal):
    shaft(c, 2, 14, 9, 7)
    c.rect(9, 2, 12, 6, pal[2])
    c.line(9, 2, 12, 2, pal[3])
    c.line(12, 2, 12, 6, pal[1])


def draw_hoe(c, pal):
    shaft(c, 2, 14, 10, 6)
    c.line(6, 3, 12, 3, pal[2])
    c.line(7, 2, 11, 2, pal[3])
    c.rect(6, 3, 7, 6, pal[1])


WEAPONS = {
    "sword": draw_sword, "dagger": draw_dagger, "spear": draw_spear, "polearm": draw_polearm,
    "pike": draw_pike, "lance": draw_lance, "battleaxe": draw_battleaxe, "hammer": draw_hammer,
    "throwing_axe": draw_throwing_axe,
}
TOOLS = {"pickaxe": draw_pickaxe, "axe": draw_axe, "shovel": draw_shovel, "hoe": draw_hoe}


# ---------------------------------------------------------------- armour pieces

def draw_helmet(c, pal):
    c.rect(3, 4, 12, 7, pal[2])
    c.rect(3, 4, 12, 4, pal[3])
    c.rect(3, 8, 5, 12, pal[1])
    c.rect(10, 8, 12, 12, pal[1])
    c.line(4, 8, 11, 8, pal[1])
    c.line(7, 5, 8, 5, pal[3])


def draw_chestplate(c, pal):
    c.rect(2, 2, 5, 6, pal[1])
    c.rect(10, 2, 13, 6, pal[1])
    c.rect(4, 3, 11, 13, pal[2])
    c.rect(4, 3, 11, 4, pal[3])
    c.rect(6, 3, 9, 5, pal[2])
    c.line(5, 13, 10, 13, pal[1])
    c.line(7, 6, 7, 12, pal[1])


def draw_leggings(c, pal):
    c.rect(3, 2, 12, 5, pal[2])
    c.rect(3, 2, 12, 2, pal[3])
    c.rect(3, 6, 6, 13, pal[2])
    c.rect(9, 6, 12, 13, pal[2])
    c.rect(3, 6, 3, 13, pal[3])
    c.rect(9, 6, 9, 13, pal[3])
    c.rect(6, 6, 6, 13, pal[1])
    c.rect(12, 6, 12, 13, pal[1])


def draw_boots(c, pal):
    c.rect(2, 5, 6, 10, pal[2])
    c.rect(9, 5, 13, 10, pal[2])
    c.rect(2, 11, 6, 13, pal[1])
    c.rect(9, 11, 13, 13, pal[1])
    c.rect(2, 11, 4, 11, pal[3])
    c.rect(9, 11, 11, 11, pal[3])
    c.rect(2, 5, 6, 5, pal[3])
    c.rect(9, 5, 13, 5, pal[3])


ARMOR = {"helmet": draw_helmet, "chestplate": draw_chestplate, "leggings": draw_leggings, "boots": draw_boots}


def armor_layer(pal, inner):
    """64x32 biped armour layer filled over the standard UV regions with a simple trim."""
    c = Canvas(64, 32)
    regions = [(16, 16, 39, 31), (0, 16, 15, 31)] if inner else [
        (0, 0, 31, 15), (16, 16, 39, 31), (40, 16, 55, 31), (0, 16, 15, 31)]
    for x0, y0, x1, y1 in regions:
        c.rect(x0, y0, x1, y1, pal[2])
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if (x + y) % 7 == 0:
                    c.set(x, y, pal[1])
                elif (x * 3 + y) % 11 == 0:
                    c.set(x, y, pal[3])
        c.line(x0, y0, x1, y0, pal[3])
        c.line(x0, y1, x1, y1, pal[1])
    return c


# ---------------------------------------------------------------- ranged

def draw_bow(c, pal, pull=0):
    arc = [11, 10, 9, 8, 7, 6, 6, 7, 8, 9, 10, 11]
    for i, x in enumerate(arc):
        c.set(x, 2 + i, HANDLE[2])
        c.set(x + 1, 2 + i, HANDLE[1])
    string_x = 12 + pull
    c.line(11, 2, string_x, 8, (0xD8, 0xD8, 0xCC))
    c.line(string_x, 8, 11, 13, (0xD8, 0xD8, 0xCC))
    c.set(6, 7, pal[3])
    c.set(6, 8, pal[2])
    if pull:
        c.line(string_x, 8, 2, 8, HANDLE[3])
        c.set(2, 8, pal[3])


def draw_crossbow(c, pal, state="standby"):
    shaft(c, 1, 14, 10, 5)                        # stock
    c.line(6, 1, 14, 9, pal[2])                   # prod
    c.line(7, 1, 14, 8, pal[1])
    c.set(6, 1, pal[3])
    c.set(14, 9, pal[3])
    cord = (0xD8, 0xD8, 0xCC)
    if state in ("standby", "arrow", "firework"):
        c.line(6, 2, 13, 9, cord)
    else:
        c.line(6, 2, 8, 6, cord)
        c.line(8, 6, 13, 9, cord)
    if state in ("arrow", "firework"):
        c.line(7, 8, 13, 2, cord)
        if state == "firework":
            c.set(13, 2, (0xC8, 0x30, 0x30))
            c.set(12, 3, (0xC8, 0x30, 0x30))


def draw_sling(c, pal):
    c.line(4, 13, 7, 9, HANDLE[2])
    c.line(5, 13, 8, 9, HANDLE[1])
    c.line(7, 9, 4, 4, HANDLE[2])
    c.line(8, 9, 12, 4, HANDLE[2])
    c.rect(5, 2, 11, 3, pal[2])
    c.rect(6, 2, 10, 2, pal[3])


# ---------------------------------------------------------------- driver

def write(path, canvas):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(canvas.png())
    print("wrote", path)


def sprite(draw, pal, **kw):
    c = Canvas()
    draw(c, pal, **kw) if kw else draw(c, pal)
    c.outline(pal[0])
    return c


def main(argv):
    out = DEFAULT_OUT
    if "--out" in argv:
        i = argv.index("--out")
        out = argv[i + 1]
        del argv[i:i + 2]
    if not argv or argv[0] in ("-h", "--help"):
        print(__doc__)
        return 0
    cmd = argv[0]
    if cmd == "list":
        print("palettes:", ", ".join(sorted(PALETTES)))
        print("weapon classes:", ", ".join(WEAPONS))
        print("tools:", ", ".join(TOOLS))
        return 0
    if len(argv) < 3:
        print("usage: gen_gear_textures.py <command> <set-or-id> <palette> [classes...]")
        return 2
    name, palette = argv[1], argv[2]
    if palette not in PALETTES:
        print("unknown palette", palette, "- try `list`")
        return 2
    pal = PALETTES[palette]
    items = os.path.join(out, "item")
    if cmd in ("weapons", "tools"):
        table = WEAPONS if cmd == "weapons" else TOOLS
        for cls in argv[3:]:
            if cls not in table:
                print("unknown class", cls)
                return 2
            write(os.path.join(items, "%s_%s.png" % (name, cls)), sprite(table[cls], pal))
    elif cmd == "armor":
        for piece, draw in ARMOR.items():
            write(os.path.join(items, "%s_%s.png" % (name, piece)), sprite(draw, pal))
        for n, inner in ((1, False), (2, True)):
            write(os.path.join(out, "models", "armor", "%s_layer_%d.png" % (name, n)), armor_layer(pal, inner))
    elif cmd == "bow":
        write(os.path.join(items, name + ".png"), sprite(draw_bow, pal))
        for i in range(3):
            write(os.path.join(items, "%s_pulling_%d.png" % (name, i)), sprite(draw_bow, pal, pull=i + 1))
    elif cmd == "crossbow":
        write(os.path.join(items, name + ".png"), sprite(draw_crossbow, pal))
        for state in ("pulling_0", "pulling_1", "pulling_2", "arrow", "firework"):
            write(os.path.join(items, "%s_%s.png" % (name, state)), sprite(draw_crossbow, pal, state=state))
    elif cmd == "sling":
        write(os.path.join(items, name + ".png"), sprite(draw_sling, pal))
    else:
        print("unknown command", cmd)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
