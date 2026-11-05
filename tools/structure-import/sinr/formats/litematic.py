"""Litematica ``.litematic``. Every region is merged into one box; negative region sizes are normalised.

``BlockStates`` is a packed long array with ``max(2, ceil(log2(palette size)))`` bits per entry, entries spanning long
boundaries; cell index is ``(y * sizeZ + z) * sizeX + x`` inside the region. Tile entity and entity positions are relative
to the region's minimum corner. Pending ticks are dropped.
"""
from __future__ import annotations

from .. import nbt
from ..grid import AIR_STATE, BlockState, Entity, Grid

MASK64 = (1 << 64) - 1


def bits_for(palette_size: int) -> int:
    return max(2, (palette_size - 1).bit_length())


def unpack(longs, bits: int, count: int) -> list:
    arr = [v & MASK64 for v in longs]
    mask = (1 << bits) - 1
    out = []
    for i in range(count):
        start = i * bits
        a, off = start >> 6, start & 63
        end = (start + bits - 1) >> 6
        if a >= len(arr) or end >= len(arr):
            raise ValueError("BlockStates array too short")
        if a == end:
            out.append((arr[a] >> off) & mask)
        else:
            out.append(((arr[a] >> off) | (arr[end] << (64 - off))) & mask)
    return out


def pack(values, bits: int) -> list:
    n = (len(values) * bits + 63) // 64
    arr = [0] * n
    for i, v in enumerate(values):
        start = i * bits
        a, off = start >> 6, start & 63
        end = (start + bits - 1) >> 6
        arr[a] |= (v << off) & MASK64
        if end != a:
            arr[end] |= v >> (64 - off)
    return [v - (1 << 64) if v >= 1 << 63 else v for v in arr]


def is_litematic(root: dict) -> bool:
    return "Regions" in root and isinstance(root["Regions"], dict)


def _xyz(c: dict) -> tuple:
    return int(c["x"]), int(c["y"]), int(c["z"])


def _state(entry: dict) -> BlockState:
    return BlockState.of(str(entry["Name"]), {k: str(v) for k, v in entry.get("Properties", {}).items()})


def read(root: dict) -> Grid:
    regions = []
    for name, r in root["Regions"].items():
        pos, size = _xyz(r["Position"]), _xyz(r["Size"])
        lo = tuple(p + s + 1 if s < 0 else p for p, s in zip(pos, size))
        dims = tuple(abs(s) for s in size)
        regions.append((name, r, lo, dims))
    if not regions:
        raise ValueError("litematic has no regions")
    gmin = tuple(min(r[2][i] for r in regions) for i in range(3))
    gmax = tuple(max(r[2][i] + r[3][i] for r in regions) for i in range(3))
    g = Grid(*(gmax[i] - gmin[i] for i in range(3)), data_version=int(root.get("MinecraftDataVersion", 0)))
    for name, r, lo, (sx, sy, sz) in regions:
        palette = [_state(e) for e in r.get("BlockStatePalette", [])]
        vals = unpack(r.get("BlockStates", []), bits_for(len(palette)), sx * sy * sz)
        ox, oy, oz = (lo[i] - gmin[i] for i in range(3))
        for idx, v in enumerate(vals):
            x = idx % sx
            z = (idx // sx) % sz
            y = idx // (sx * sz)
            if v >= len(palette):
                raise ValueError(f"region {name}: palette index {v} out of range")
            st = palette[v]
            if st.name != "minecraft:air" or g.get(ox + x, oy + y, oz + z) == AIR_STATE:
                g.blocks[g._i(ox + x, oy + y, oz + z)] = st
        for te in r.get("TileEntities", []):
            x, y, z = int(te["x"]) + ox, int(te["y"]) + oy, int(te["z"]) + oz
            g.block_entities[(x, y, z)] = {k: v for k, v in te.items() if k not in ("x", "y", "z")}
        for e in r.get("Entities", []):
            p = e.get("Pos", [0.0, 0.0, 0.0])
            g.entities.append(Entity((float(p[0]) + ox, float(p[1]) + oy, float(p[2]) + oz),
                                     {k: v for k, v in e.items() if k != "Pos"}))
    return g


def write(regions, data_version: int, name: str = "fixture") -> dict:
    """``regions``: list of (region name, position (x, y, z), signed size (x, y, z), Grid of the absolute size)."""
    out = {}
    total = 0
    for rname, pos, size, g in regions:
        pal, ids = [], []
        index = {}
        pal.append(AIR_STATE)
        index[AIR_STATE] = 0
        for p in g.positions():
            st = g.get(*p)
            if st not in index:
                index[st] = len(pal)
                pal.append(st)
            ids.append(index[st])
        total += sum(1 for v in ids if v)
        entry = lambda s: {"Name": s.name, **({"Properties": {k: v for k, v in s.props}} if s.props else {})}
        out[rname] = {
            "Position": {"x": nbt.Int(pos[0]), "y": nbt.Int(pos[1]), "z": nbt.Int(pos[2])},
            "Size": {"x": nbt.Int(size[0]), "y": nbt.Int(size[1]), "z": nbt.Int(size[2])},
            "BlockStatePalette": nbt.TagList([entry(s) for s in pal], nbt.COMPOUND),
            "BlockStates": nbt.LongArray(pack(ids, bits_for(len(pal)))),
            "TileEntities": nbt.TagList([{"x": nbt.Int(p[0]), "y": nbt.Int(p[1]), "z": nbt.Int(p[2]), **t}
                                         for p, t in sorted(g.block_entities.items())], nbt.COMPOUND),
            "Entities": nbt.TagList([{"Pos": nbt.TagList([nbt.Double(c) for c in e.pos], nbt.DOUBLE), **e.nbt}
                                     for e in g.entities], nbt.COMPOUND),
            "PendingBlockTicks": nbt.TagList([], nbt.COMPOUND),
        }
    return {"MinecraftDataVersion": nbt.Int(data_version), "Version": nbt.Int(6),
            "Metadata": {"Name": name, "Author": "lotr_revival tests", "RegionCount": nbt.Int(len(regions)),
                         "TotalBlocks": nbt.Int(total)},
            "Regions": out}
