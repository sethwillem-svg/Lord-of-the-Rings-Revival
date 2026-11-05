"""Limits (spec "Limits"). ``check`` returns (errors, warnings); any error refuses the build."""
from __future__ import annotations

from .grid import Grid

FOOTPRINT_WARN = 48
FOOTPRINT_MAX = 96
HEIGHT_MAX = 64
BLOCKS_MAX = 60_000
BLOCK_ENTITIES_MAX = 256
ENTITIES_MAX = 32
OUTPUT_MAX = 256 * 1024
SOURCE_MAX = 1024 * 1024
NESTS_MAX = 16
NPCS_MAX = 24
NEST_DISTANCE_MAX = 20.0


def check_grid(g: Grid) -> tuple[list, list]:
    errs, warns = [], []
    for axis, n in (("x", g.sx), ("z", g.sz)):
        if n > FOOTPRINT_MAX:
            errs.append(f"footprint {axis} {n} > {FOOTPRINT_MAX}")
        elif n > FOOTPRINT_WARN:
            warns.append(f"footprint {axis} {n} > {FOOTPRINT_WARN} (recommended)")
    if g.sy > HEIGHT_MAX:
        errs.append(f"height {g.sy} > {HEIGHT_MAX}")
    blocks = g.non_air_count()
    if blocks > BLOCKS_MAX:
        errs.append(f"{blocks:,} non-air blocks > {BLOCKS_MAX:,}")
    if len(g.block_entities) > BLOCK_ENTITIES_MAX:
        errs.append(f"{len(g.block_entities)} block entities > {BLOCK_ENTITIES_MAX}")
    if len(g.entities) > ENTITIES_MAX:
        errs.append(f"{len(g.entities)} entities > {ENTITIES_MAX}")
    return errs, warns


def check_output(size_bytes: int) -> list:
    return [f"output {size_bytes:,} bytes > {OUTPUT_MAX:,} gzipped"] if size_bytes > OUTPUT_MAX else []


def check_source(size_bytes: int) -> list:
    return ([f"source file {size_bytes:,} bytes > {SOURCE_MAX:,}: keep only the URL, archive and hash (source_file: null)"]
            if size_bytes > SOURCE_MAX else [])
