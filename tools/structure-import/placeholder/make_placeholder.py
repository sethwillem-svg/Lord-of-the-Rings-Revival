#!/usr/bin/env python3
"""Writes our own placeholder build and the format fixtures (spec ticket 2). Own work, ASSETS_LICENSE.

    python3 -I tools/structure-import/placeholder/make_placeholder.py

Outputs:
  builds/placeholder_watchtower/source/placeholder_watchtower.schem   Sponge v3, a 7 x 11 x 7 stone watchtower on 2 layers
                                                                      of dirt and grass
  builds/placeholder_watchtower/build.json                            created if missing; source_sha256 always refreshed
  tests/fixtures/fixture.{v2.schem,v3.schem,litematic,nbt}            one asymmetric 3 x 4 x 5 build in every input format
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL))

from sinr import manifest, nbt  # noqa: E402
from sinr.formats import litematic, sponge, vanilla  # noqa: E402
from sinr.grid import BlockState, Entity, Grid  # noqa: E402

DATA_VERSION = 3955
S = BlockState.of


def watchtower() -> Grid:
    g = Grid(7, 11, 7, data_version=DATA_VERSION)
    for x in range(7):
        for z in range(7):
            g.set(x, 0, z, S("dirt"))
            g.set(x, 1, z, S("grass_block", {"snowy": "false"}))
            g.set(x, 2, z, S("stone_bricks"))           # foundation floor, sits on the terrain's top block
            g.set(x, 8, z, S("stone_bricks"))           # top platform
    for y in range(3, 8):
        for x in range(7):
            for z in range(7):
                if x in (0, 6) or z in (0, 6):
                    corner = x in (0, 6) and z in (0, 6)
                    g.set(x, y, z, S("cobblestone") if (y == 3 or corner) else S("stone_bricks"))
    # door on the north side (z = 0), opening into the tower
    g.set(3, 3, 0, S("oak_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"}))
    g.set(3, 4, 0, S("oak_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"}))
    # ladder against the south wall, up through the platform
    for y in range(3, 9):
        g.set(3, y, 5, S("ladder", {"facing": "north", "waterlogged": "false"}))
    # a step and a bench inside, a torch by the door
    g.set(1, 3, 3, S("stone_brick_stairs", {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false"}))
    g.set(5, 3, 3, S("stone_brick_slab", {"type": "bottom", "waterlogged": "false"}))
    g.set(3, 5, 1, S("wall_torch", {"facing": "south"}))
    g.set(5, 3, 1, S("oak_planks"))
    # battlements: walls on the rim, posts with torches at the corners
    for x in range(7):
        for z in range(7):
            if x in (0, 6) or z in (0, 6):
                g.set(x, 9, z, S("stone_brick_wall", {"east": "none", "north": "none", "south": "none", "west": "none",
                                                     "up": "true", "waterlogged": "false"}))
    for x, z in ((0, 0), (0, 6), (6, 0), (6, 6)):
        g.set(x, 10, z, S("torch"))
    return g


def fixture() -> Grid:
    """Asymmetric 3 x 4 x 5 box: every cell distinct enough that a wrong index order shows."""
    g = Grid(3, 4, 5, data_version=DATA_VERSION)
    names = ["stone", "dirt", "oak_planks", "cobblestone", "stone_bricks", "sand", "gravel"]
    for x, y, z in g.positions():
        k = (x * 7 + y * 3 + z * 5) % 9
        if k < len(names):
            g.set(x, y, z, S(names[k]))
    g.set(2, 3, 4, S("chest", {"facing": "west", "type": "single", "waterlogged": "false"}),
          {"id": "minecraft:chest", "Items": nbt.TagList([], nbt.COMPOUND)})
    g.set(0, 1, 2, S("oak_stairs", {"facing": "east", "half": "top", "shape": "straight", "waterlogged": "false"}))
    g.entities.append(Entity((0.5, 1.0, 0.5), {"id": "minecraft:armor_stand", "Invisible": nbt.Byte(1)}))
    return g


def split_for_litematic(g: Grid):
    """Region A: x 0..1 with a positive size; region B: x 2 with a negative size (position at its max corner)."""
    a = Grid(2, g.sy, g.sz)
    b = Grid(1, g.sy, g.sz)
    for x, y, z in g.positions():
        st = g.get(x, y, z)
        tgt, ox = (a, 0) if x < 2 else (b, 2)
        tgt.set(x - ox, y, z, st, g.block_entities.get((x, y, z)))
    a.entities = [Entity(e.pos, dict(e.nbt)) for e in g.entities]
    return [("a", (10, 64, 20), (2, g.sy, g.sz), a),
            ("b", (12, 64 + g.sy - 1, 20 + g.sz - 1), (1, -g.sy, -g.sz), b)]


def write_all(tool: Path = TOOL) -> list:
    written = []
    src = tool / "builds" / "placeholder_watchtower" / "source" / "placeholder_watchtower.schem"
    src.parent.mkdir(parents=True, exist_ok=True)
    src.write_bytes(nbt.dumps(sponge.write(watchtower(), version=3)))
    written.append(src)
    fx = tool / "tests" / "fixtures"
    fx.mkdir(parents=True, exist_ok=True)
    f = fixture()
    for name, data in (("fixture.v2.schem", nbt.dumps(sponge.write(f, version=2))),
                       ("fixture.v3.schem", nbt.dumps(sponge.write(f, version=3))),
                       ("fixture.litematic", nbt.dumps(litematic.write(split_for_litematic(f), DATA_VERSION))),
                       ("fixture.nbt", nbt.dumps(vanilla.write(f)))):
        (fx / name).write_bytes(data)
        written.append(fx / name)
    mpath = tool / "builds" / "placeholder_watchtower" / "build.json"
    m = json.loads(mpath.read_text(encoding="utf-8")) if mpath.exists() else default_manifest()
    m["source_sha256"] = manifest.sha256(src)
    manifest.save(mpath, m)
    written.append(mpath)
    return written


def default_manifest() -> dict:
    return {
        "id": "placeholder_watchtower",
        "status": "approved",
        "title": "Placeholder Watchtower",
        "author": "LOTR Revival contributors",
        "author_url": None,
        "co_authors": [],
        "source_url": None,
        "archive_url": None,
        "licence": "LicenseRef-ASSETS_LICENSE",
        "licence_url": None,
        "licence_evidence": "Own work, generated by tools/structure-import/placeholder/make_placeholder.py",
        "attribution": None,
        "retrieved": None,
        "source_file": "source/placeholder_watchtower.schem",
        "source_sha256": None,
        "own_work": True,
        "approved_by": None,
        "approved_on": None,
        "region": "gondor",
        "kinds": ["watchtower"],
        "faction": None,
        "palette": "gondor",
        "site": "land",
        "max_slope": 6,
        "ground_y": None,
        "weight": 1,
        "processors": None,
        "keep_sign_text": False,
        "keep_entities": [],
        "manager": {"pos": [1, 3, 1], "block": "middle-earth:structure_manager", "facing": "south",
                    "pool": "lotr_revival:gondor_watchtower"},
        "nests": [{"pos": [3, 9, 3], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 2},
                  {"pos": [1, 3, 5], "nest": "lotr_revival:gondor_watchtower.archer", "radius": 3}],
        "manual_changes": [],
        "changes": [],
        "output": "addon/src/main/resources/data/lotr_revival/structure/build/placeholder_watchtower.nbt",
        "output_licence": None,
        "output_info": None,
        "notes": "Placeholder for tickets 1-9; replaced by licensed builds from ticket 12.",
    }


if __name__ == "__main__":
    for p in write_all():
        print(f"wrote {p.relative_to(TOOL)}")
