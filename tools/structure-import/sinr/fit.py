"""Fitting to terrain (spec "Fitting to terrain"): trim, ground layer, natural ground and outside air to structure void."""
from __future__ import annotations

from collections import deque

from .grid import AIR, EMPTY_NAMES, VOID_STATE, Grid

NATURAL = {"minecraft:" + n for n in ("grass_block", "dirt", "coarse_dirt", "podzol", "rooted_dirt", "mud", "sand",
                                      "red_sand", "gravel", "stone", "snow_block", "dirt_path")}
GROUND_SHARE = 0.6
AIRS = {AIR, "minecraft:cave_air", "minecraft:void_air"}


def trim(g: Grid) -> Grid:
    """Crops to the bounding box of non-air cells (structure void counts as air)."""
    xs, ys, zs = [], [], []
    for p in g.positions():
        if g.get(*p).name not in EMPTY_NAMES:
            xs.append(p[0])
            ys.append(p[1])
            zs.append(p[2])
    if not xs:
        return Grid(0, 0, 0, data_version=g.data_version)
    x0, y0, z0 = min(xs), min(ys), min(zs)
    out = Grid(max(xs) - x0 + 1, max(ys) - y0 + 1, max(zs) - z0 + 1, data_version=g.data_version)
    for x, y, z in out.positions():
        out.blocks[out._i(x, y, z)] = g.get(x + x0, y + y0, z + z0)
    out.block_entities = {(x - x0, y - y0, z - z0): t for (x, y, z), t in g.block_entities.items()
                          if out.inside(x - x0, y - y0, z - z0)}
    for e in g.entities:
        e.pos = (e.pos[0] - x0, e.pos[1] - y0, e.pos[2] - z0)
        if out.inside(int(e.pos[0] // 1), int(e.pos[1] // 1), int(e.pos[2] // 1)):
            out.entities.append(e)
    return out


def detect_ground(g: Grid) -> int:
    """Highest layer where >= 60% of the footprint is natural ground and the layer above has less; 0 if none."""
    area = g.sx * g.sz
    if area == 0:
        return 0
    share = []
    for y in range(g.sy):
        n = sum(1 for x in range(g.sx) for z in range(g.sz) if g.get(x, y, z).name in NATURAL)
        share.append(n / area)
    best = None
    for y in range(g.sy):
        above = share[y + 1] if y + 1 < g.sy else 0.0
        if share[y] >= GROUND_SHARE and above < GROUND_SHARE:
            best = y
    return best if best is not None else 0


def ground_to_void(g: Grid, ground_y: int) -> int:
    n = 0
    for y in range(min(ground_y + 1, g.sy)):
        for z in range(g.sz):
            for x in range(g.sx):
                if g.get(x, y, z).name in NATURAL:
                    g.set(x, y, z, VOID_STATE)
                    n += 1
    return n


def outside_air_to_void(g: Grid) -> int:
    """Air connected to the box's sides or top through air (6-way) becomes structure void; enclosed air stays."""
    seen = set()
    q = deque()
    for x, y, z in g.positions():
        on_edge = x in (0, g.sx - 1) or z in (0, g.sz - 1) or y == g.sy - 1
        if on_edge and g.get(x, y, z).name in AIRS:
            seen.add((x, y, z))
            q.append((x, y, z))
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            p = (x + dx, y + dy, z + dz)
            if p not in seen and g.inside(*p) and g.get(*p).name in AIRS:
                seen.add(p)
                q.append(p)
    for p in seen:
        g.set(*p, VOID_STATE)
    return len(seen)


def fit(g: Grid, ground_y: int | None) -> tuple[Grid, int, list]:
    """Returns (fitted grid, ground_y used, changes lines). Positions in the manifest refer to the fitted grid."""
    changes = []
    before = g.size
    g = trim(g)
    if g.size != before:
        changes.append(f"trimmed to {g.sx} x {g.sy} x {g.sz}")
    gy = ground_y if ground_y is not None else detect_ground(g)
    n = ground_to_void(g, gy)
    if n:
        changes.append(f"natural ground at or below y={gy} made structure void ({n:,} blocks)")
    m = outside_air_to_void(g)
    if m:
        changes.append("outside air made structure void")
    return g, gy, changes
