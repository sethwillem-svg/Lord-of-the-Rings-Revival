"""Vanilla structure template ``.nbt``: ``palette`` (or the first of ``palettes``), ``blocks``, ``entities``.

Cells missing from ``blocks`` are structure void (vanilla's own convention), so ``write`` leaves out structure_void cells
and ``read`` fills them back in as structure_void.
"""
from __future__ import annotations

from .. import nbt
from ..grid import VOID_STATE, BlockState, Entity, Grid


def is_vanilla(root: dict) -> bool:
    return "blocks" in root and ("palette" in root or "palettes" in root) and "size" in root


def _state(entry: dict) -> BlockState:
    return BlockState.of(str(entry["Name"]), {k: str(v) for k, v in entry.get("Properties", {}).items()})


def read(root: dict) -> Grid:
    size = [int(c) for c in root["size"]]
    pal_src = root.get("palette")
    if pal_src is None:
        pals = root.get("palettes", [])
        if not pals:
            raise ValueError("structure has no palette")
        pal_src = pals[0]
    palette = [_state(e) for e in pal_src]
    g = Grid(*size, data_version=int(root.get("DataVersion", 0)))
    g.blocks = [VOID_STATE] * (g.sx * g.sy * g.sz)
    for b in root["blocks"]:
        x, y, z = (int(c) for c in b["pos"])
        g.blocks[g._i(x, y, z)] = palette[int(b["state"])]
        if "nbt" in b:
            g.block_entities[(x, y, z)] = dict(b["nbt"])
    for e in root.get("entities", []):
        g.entities.append(Entity(tuple(float(c) for c in e["pos"]), dict(e.get("nbt", {}))))
    return g


def write(g: Grid) -> dict:
    index, palette, blocks = {}, [], []
    for p in g.positions():
        st = g.get(*p)
        if st == VOID_STATE:
            continue
        if st not in index:
            index[st] = len(palette)
            palette.append(st)
        b = {"pos": nbt.TagList([nbt.Int(c) for c in p], nbt.INT), "state": nbt.Int(index[st])}
        if p in g.block_entities:
            b["nbt"] = g.block_entities[p]
        blocks.append(b)

    def entry(s):
        e = {"Name": s.name}
        if s.props:
            e["Properties"] = {k: v for k, v in s.props}
        return e

    ents = []
    for e in g.entities:
        bp = [int(c // 1) for c in e.pos]
        ents.append({"pos": nbt.TagList([nbt.Double(c) for c in e.pos], nbt.DOUBLE),
                     "blockPos": nbt.TagList([nbt.Int(c) for c in bp], nbt.INT), "nbt": e.nbt})
    return {
        "DataVersion": nbt.Int(g.data_version),
        "size": nbt.TagList([nbt.Int(g.sx), nbt.Int(g.sy), nbt.Int(g.sz)], nbt.INT),
        "palette": nbt.TagList([entry(s) for s in palette], nbt.COMPOUND),
        "blocks": nbt.TagList(blocks, nbt.COMPOUND),
        "entities": nbt.TagList(ents, nbt.COMPOUND),
    }
