#!/usr/bin/env python3
"""Writes blocks.json, the hand-written stand-in for the block dump (same format as structures/dev/BlockDump.java).

Lists only the blocks the placeholder and the tests use. The real dump comes from
./gradlew runGameTestServer -PmeJar=<jar> -PblockDump=tools/structure-import/blocks.json (git-ignored).
"""
import json
from pathlib import Path

B = ["true", "false"]
H4 = ["north", "south", "west", "east"]
STAIRS = {"facing": (H4, "north"), "half": (["top", "bottom"], "bottom"),
          "shape": (["straight", "inner_left", "inner_right", "outer_left", "outer_right"], "straight"),
          "waterlogged": (B, "false")}
SLAB = {"type": (["top", "bottom", "double"], "bottom"), "waterlogged": (B, "false")}
WALL = {"east": (["none", "low", "tall"], "none"), "north": (["none", "low", "tall"], "none"),
        "south": (["none", "low", "tall"], "none"), "west": (["none", "low", "tall"], "none"),
        "up": (B, "true"), "waterlogged": (B, "false")}
SIMPLE = ["air", "cave_air", "void_air", "structure_void", "stone", "dirt", "sand", "gravel", "cobblestone",
          "mossy_cobblestone", "stone_bricks", "oak_planks", "torch", "snow_block", "coarse_dirt"]

blocks = {}


def add(name, props=None):
    props = props or {}
    blocks[name if ":" in name else "minecraft:" + name] = {
        "properties": {k: v[0] for k, v in props.items()},
        "default": {k: v[1] for k, v in props.items()},
    }


for n in SIMPLE:
    add(n)
add("grass_block", {"snowy": (B, "false")})
add("stone_brick_stairs", STAIRS)
add("oak_stairs", STAIRS)
add("stone_brick_slab", SLAB)
add("stone_brick_wall", WALL)
add("ladder", {"facing": (H4, "north"), "waterlogged": (B, "false")})
add("wall_torch", {"facing": (H4, "north")})
add("oak_door", {"facing": (H4, "north"), "half": (["upper", "lower"], "lower"), "hinge": (["left", "right"], "left"),
                 "open": (B, "false"), "powered": (B, "false")})
add("chest", {"facing": (H4, "north"), "type": (["single", "left", "right"], "single"), "waterlogged": (B, "false")})
add("barrel", {"facing": (H4 + ["up", "down"], "north"), "open": (B, "false")})
add("oak_leaves", {"distance": ([str(i) for i in range(1, 8)], "7"), "persistent": (B, "false"), "waterlogged": (B, "false")})
add("middle-earth:limestone_bricks")
add("middle-earth:cracked_limestone_bricks")
add("middle-earth:mossy_limestone_bricks")
add("middle-earth:cobbled_limestone")
add("middle-earth:limestone_brick_stairs", STAIRS)
add("middle-earth:limestone_brick_slab", SLAB)
add("middle-earth:limestone_brick_wall", WALL)
add("middle-earth:structure_manager", {"facing": (H4, "north")})
add("middle-earth:orc_structure_manager", {"facing": (H4, "north")})
add("middle-earth:structure_nest", {"enabled": (B, "false"), "waterlogged": (B, "false")})

out = Path(__file__).with_name("blocks.json")
out.write_text(json.dumps({"source": "hand-written test fixture (tests/fixtures/make_blocks_fixture.py)",
                           "blocks": dict(sorted(blocks.items()))}, indent=1) + "\n", encoding="utf-8")
print(f"wrote {out}")
