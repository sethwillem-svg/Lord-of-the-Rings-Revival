#!/usr/bin/env python3
"""Generates the temporary Middle-earth placeholder maps (ticket #22).

A small round island around Hobbiton (block 0,0) with Shire land, an Old Forest patch to the
east, and open ocean everywhere else, at the full canvas size from specs/world/dimension-and-mapgen.md.
Ticket #23 replaces these with the real map pipeline.

Usage: python3 tools/world/gen_placeholder_maps.py   (run from the repo root; needs numpy and Pillow)
"""
import numpy as np
from PIL import Image

HM_W, HM_H = 6400, 8000
BM_W, BM_H = 3200, 4000
ORIGIN_U, ORIGIN_V = 1620, 1460  # heightmap px of block (0, 0)
VERTICAL_SCALE = 0.005  # y = value * 0.005

OCEAN = (0x1F, 0x3A, 0x93)
SHIRE = (0x78, 0xB0, 0x48)
OLD_FOREST = (0x1F, 0x4A, 0x24)

OUT = "src/main/resources/data/lotrrevival/novoatlas/"


def smoothstep(x):
    x = np.clip(x, 0.0, 1.0)
    return x * x * (3.0 - 2.0 * x)


def heightmap():
    # Only compute near the island, the rest is a constant ocean floor (y 40).
    arr = np.full((HM_H, HM_W), int(40 / VERTICAL_SCALE), dtype=np.uint16)
    r = 120
    ys, xs = np.mgrid[ORIGIN_V - r:ORIGIN_V + r, ORIGIN_U - r:ORIGIN_U + r]
    dist = np.hypot(xs + 0.5 - ORIGIN_U, ys + 0.5 - ORIGIN_V)
    y = 40 + 28 * (1 - smoothstep((dist - 70) / 40))
    arr[ORIGIN_V - r:ORIGIN_V + r, ORIGIN_U - r:ORIGIN_U + r] = np.round(y / VERTICAL_SCALE).astype(np.uint16)
    return arr


def biome_map():
    arr = np.empty((BM_H, BM_W, 3), dtype=np.uint8)
    arr[:] = OCEAN
    ou, ov = ORIGIN_U // 2, ORIGIN_V // 2
    r = 50
    ys, xs = np.mgrid[ov - r:ov + r, ou - r:ou + r]
    dist = np.hypot(xs + 0.5 - ou, ys + 0.5 - ov)
    patch = np.empty((2 * r, 2 * r, 3), dtype=np.uint8)
    patch[:] = OCEAN
    patch[dist < 38] = SHIRE
    forest = np.hypot(xs + 0.5 - (ou + 30), ys + 0.5 - ov) < 8
    patch[forest] = OLD_FOREST
    arr[ov - r:ov + r, ou - r:ou + r] = patch
    return arr


if __name__ == "__main__":
    Image.fromarray(heightmap()).save(  # uint16 array -> 16-bit grayscale
        OUT + "heightmap/middle_earth.png", optimize=True, compress_level=9)
    Image.fromarray(biome_map()).save(  # uint8 HxWx3 -> 8-bit RGB
        OUT + "biome_map/middle_earth.png", optimize=True, compress_level=9)
