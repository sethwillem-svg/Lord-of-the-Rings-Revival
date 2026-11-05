"""Middle-earth's structure manager and nest blocks: insertion from the manifest and ``extract-nests``.

The block entity keys below are Middle-earth's public save keys, used as an interface (spec "Residents"). They are flat
keys with a dot in the name, not nested compounds.
"""
from __future__ import annotations

import json
import math
from pathlib import Path

from . import limits, nbt
from .grid import BlockState, Grid

MANAGER_BLOCKS = ("middle-earth:structure_manager", "middle-earth:orc_structure_manager")
NEST_BLOCK = "middle-earth:structure_nest"
M_ID, M_INIT, M_ENABLED = "structure_manager.Identifier", "structure_manager.ToInitialize", "structure_manager.Enabled"
N_MANAGER, N_NEST, N_RADIUS, N_ENABLED = ("structure_nest.ManagerId", "structure_nest.NestId", "structure_nest.SpawnRadius",
                                          "structure_nest.IsEnabled")


class NestError(ValueError):
    pass


def pool_path(pools_dir: Path, pool: str) -> Path | None:
    ns, path = pool.split(":", 1)
    if ns != "lotr_revival":
        return None
    return Path(pools_dir) / f"{path}.json"


def load_pool(pools_dir: Path, pool: str) -> dict | None:
    p = pool_path(pools_dir, pool)
    if p is None:
        return None
    if not p.exists():
        raise NestError(f"pool {pool} has no file {p}")
    return json.loads(p.read_text(encoding="utf-8"))


def nest_max(nest_def: dict) -> int:
    """Most NPCs one nest can hold: Middle-earth picks one weighted entry, then amount..max_amount."""
    return max((max(int(e.get("amount", 1)), int(e.get("max_amount", 0))) for e in nest_def.get("npc_pool", [])), default=0)


def insert(g: Grid, manifest: dict, pools_dir: Path) -> tuple[list, int]:
    """Places the manager and nests in ``g``. Returns (changes lines, NPC maximum)."""
    mgr = manifest.get("manager")
    nests = manifest.get("nests", []) or []
    if not mgr:
        if nests:
            raise NestError("nests without a manager")
        return [], 0
    if len(nests) > limits.NESTS_MAX:
        raise NestError(f"{len(nests)} nests > {limits.NESTS_MAX}")
    pool = mgr["pool"]
    pool_def = load_pool(pools_dir, pool)
    by_id = {n["id"]: n for n in pool_def.get("spawn_nest", [])} if pool_def else None
    mx, my, mz = mgr["pos"]
    if not g.inside(mx, my, mz):
        raise NestError(f"manager at {mx},{my},{mz} is outside the {g.sx} x {g.sy} x {g.sz} template")
    g.set(mx, my, mz, BlockState.of(mgr["block"], {"facing": mgr.get("facing", "north")}), {
        "id": mgr["block"],
        M_ID: pool,
        M_INIT: nbt.Byte(1),
        M_ENABLED: nbt.Byte(0),
    })
    total = 0
    for n in nests:
        x, y, z = n["pos"]
        if not g.inside(x, y, z):
            raise NestError(f"nest {n['nest']} at {x},{y},{z} is outside the template")
        d = math.dist((x, y, z), (mx, my, mz))
        if d > limits.NEST_DISTANCE_MAX:
            raise NestError(f"nest {n['nest']} is {d:.1f} blocks from the manager (limit {limits.NEST_DISTANCE_MAX:g})")
        if by_id is not None:
            if n["nest"] not in by_id:
                raise NestError(f"nest {n['nest']} is not in pool {pool}")
            total += nest_max(by_id[n["nest"]])
        g.set(x, y, z, BlockState.of(NEST_BLOCK, {"enabled": "true", "waterlogged": "false"}), {
            "id": NEST_BLOCK,
            N_MANAGER: pool,
            N_NEST: n["nest"],
            N_RADIUS: nbt.Int(int(n["radius"])),
            N_ENABLED: nbt.Byte(1),
        })
    if total > limits.NPCS_MAX:
        raise NestError(f"up to {total} NPCs > {limits.NPCS_MAX}")
    word = "nest" if len(nests) == 1 else "nests"
    return [f"added a structure manager and {len(nests)} {word}"], total


def extract(g: Grid) -> tuple[dict | None, list]:
    """Reads manager and nests back from a template (for example one saved in game after the authoring flow)."""
    manager, nests = None, []
    for p in g.positions():
        st = g.get(*p)
        tag = g.block_entities.get(p, {})
        if st.name in MANAGER_BLOCKS:
            if manager is not None:
                raise NestError("more than one structure manager")
            manager = {"pos": list(p), "block": st.name, "facing": st.prop_dict.get("facing", "north"),
                       "pool": str(tag.get(M_ID, ""))}
        elif st.name == NEST_BLOCK:
            nests.append({"pos": list(p), "nest": str(tag.get(N_NEST, "")), "radius": int(tag.get(N_RADIUS, 0))})
    return manager, nests
