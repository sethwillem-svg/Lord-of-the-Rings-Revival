"""Sponge schematic v2 and v3 (``.schem``). Index order x + z * Width + y * Width * Length; ``Offset`` is ignored."""
from __future__ import annotations

from .. import nbt
from ..grid import BlockState, Entity, Grid


def varints_decode(data, count: int) -> list:
    out, i, n = [], 0, len(data)
    while len(out) < count:
        value, shift = 0, 0
        while True:
            if i >= n:
                raise ValueError("BlockData ends early")
            b = data[i] & 0xFF
            i += 1
            value |= (b & 0x7F) << shift
            if not b & 0x80:
                break
            shift += 7
            if shift > 35:
                raise ValueError("varint too long")
        out.append(value)
    return out


def varints_encode(values) -> list:
    out = []
    for v in values:
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                break
    return [b - 256 if b > 127 else b for b in out]   # signed bytes for the ByteArray tag


def is_sponge(root: dict) -> bool:
    if "Schematic" in root and isinstance(root["Schematic"], dict):
        return True
    return "Palette" in root and "BlockData" in root


def read(root: dict) -> Grid:
    if "Schematic" in root and isinstance(root["Schematic"], dict):
        s = root["Schematic"]
        version = int(s.get("Version", 3))
    else:
        s = root
        version = int(s.get("Version", 2))
    w, h, l = int(s["Width"]) & 0xFFFF, int(s["Height"]) & 0xFFFF, int(s["Length"]) & 0xFFFF
    if version >= 3:
        blocks = s.get("Blocks", {})
        palette, data, bes = blocks.get("Palette", {}), blocks.get("Data", []), blocks.get("BlockEntities", [])
    else:
        palette, data, bes = s.get("Palette", {}), s.get("BlockData", []), s.get("BlockEntities", s.get("TileEntities", []))
    by_index = {int(i): BlockState.parse(k) for k, i in palette.items()}
    g = Grid(w, h, l, data_version=int(s.get("DataVersion", 0)))
    ids = varints_decode(data, w * h * l)
    for idx, pid in enumerate(ids):
        x = idx % w
        z = (idx // w) % l
        y = idx // (w * l)
        if pid not in by_index:
            raise ValueError(f"palette index {pid} missing")
        g.blocks[g._i(x, y, z)] = by_index[pid]
    for be in bes:
        x, y, z = (int(c) for c in be["Pos"])
        if version >= 3:
            tag = dict(be.get("Data", {}))
        else:
            tag = {k: v for k, v in be.items() if k not in ("Pos", "Id")}
        tag = {"id": str(be.get("Id", "")), **{k: v for k, v in tag.items() if k != "id"}}
        g.block_entities[(x, y, z)] = tag
    for e in s.get("Entities", []):
        pos = tuple(float(c) for c in e["Pos"])
        tag = dict(e.get("Data", {})) if version >= 3 else {k: v for k, v in e.items() if k not in ("Pos", "Id")}
        tag = {"id": str(e.get("Id", "")), **{k: v for k, v in tag.items() if k not in ("id", "Pos")}}
        g.entities.append(Entity(pos, tag))
    return g


def _palette(g: Grid):
    pal, ids = {}, []
    for p in g.positions():
        st = str(g.get(*p))
        if st not in pal:
            pal[st] = len(pal)
        ids.append(pal[st])
    return pal, ids


def write(g: Grid, version: int = 3) -> dict:
    """Builds a Sponge root compound (the caller gzips it with nbt.dumps)."""
    pal, ids = _palette(g)
    palette = {k: nbt.Int(v) for k, v in pal.items()}
    data = nbt.ByteArray(varints_encode(ids))
    if version >= 3:
        bes = nbt.TagList([{"Pos": nbt.IntArray(p), "Id": t.get("id", ""),
                            "Data": {k: v for k, v in t.items() if k != "id"}} for p, t in sorted(g.block_entities.items())],
                          nbt.COMPOUND)
        ents = nbt.TagList([{"Pos": nbt.TagList([nbt.Double(c) for c in e.pos], nbt.DOUBLE), "Id": e.type,
                             "Data": {k: v for k, v in e.nbt.items() if k != "id"}} for e in g.entities], nbt.COMPOUND)
        return {"Schematic": {
            "Version": nbt.Int(3), "DataVersion": nbt.Int(g.data_version),
            "Width": nbt.Short(g.sx), "Height": nbt.Short(g.sy), "Length": nbt.Short(g.sz),
            "Offset": nbt.IntArray([0, 0, 0]),
            "Blocks": {"Palette": palette, "Data": data, "BlockEntities": bes},
            "Entities": ents,
        }}
    bes = nbt.TagList([{"Pos": nbt.IntArray(p), "Id": t.get("id", ""), **{k: v for k, v in t.items() if k != "id"}}
                       for p, t in sorted(g.block_entities.items())], nbt.COMPOUND)
    ents = nbt.TagList([{"Pos": nbt.TagList([nbt.Double(c) for c in e.pos], nbt.DOUBLE), "Id": e.type,
                         **{k: v for k, v in e.nbt.items() if k != "id"}} for e in g.entities], nbt.COMPOUND)
    return {"Version": nbt.Int(2), "DataVersion": nbt.Int(g.data_version),
            "Width": nbt.Short(g.sx), "Height": nbt.Short(g.sy), "Length": nbt.Short(g.sz),
            "Offset": nbt.IntArray([0, 0, 0]), "PaletteMax": nbt.Int(len(pal)), "Palette": palette,
            "BlockData": data, "BlockEntities": bes, "Entities": ents}
