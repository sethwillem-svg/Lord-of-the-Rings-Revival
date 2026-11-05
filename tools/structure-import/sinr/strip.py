"""Stripping unsafe content (spec "Stripping", always on). Runs before the palette swap."""
from __future__ import annotations

import fnmatch

from .grid import AIR_STATE, BlockState, Grid

TO_AIR = {"command_block", "chain_command_block", "repeating_command_block", "structure_block", "jigsaw", "barrier",
          "light", "spawner", "trial_spawner", "vault", "fire", "soul_fire", "tnt", "player_head", "player_wall_head"}
STOP = {"bedrock", "end_portal_frame", "end_portal", "nether_portal", "end_gateway", "reinforced_deepslate", "lava"}
VALUABLE = {"beacon", "conduit", "dragon_egg", "diamond_block", "emerald_block", "netherite_block", "gold_block",
            "iron_block", "ancient_debris"}
ALLOWED_ENTITIES = {"painting", "item_frame", "glow_item_frame", "armor_stand"}
MAX_KEPT_ENTITIES = 32
REDSTONE = ("redstone_wire", "repeater", "comparator", "piston", "sticky_piston", "observer", "lever", "*_button",
            "*_pressure_plate", "dispenser", "dropper", "hopper", "daylight_detector", "redstone_torch",
            "redstone_wall_torch", "target", "tripwire", "tripwire_hook", "note_block", "redstone_lamp")
BE_REMOVE = ("Items", "LootTable", "LootTableSeed", "Book", "RecordItem", "item", "CustomName", "Lock", "lock",
             "sherds")
ENTITY_EMPTY = ("Item", "Items", "ArmorItems", "HandItems", "body_armor_item", "CustomName", "Passengers")


class StripError(ValueError):
    pass


def short(name: str) -> str:
    return name.split(":", 1)[1] if name.startswith("minecraft:") else name


def _vanilla(name: str) -> str | None:
    return name[len("minecraft:"):] if name.startswith("minecraft:") else None


def is_valuable(name: str) -> bool:
    v = _vanilla(name)
    return v is not None and (v in VALUABLE or v.endswith("_ore"))


def _plural(n: int, word: str) -> str:
    return f"{n:,} {word}" + ("" if n == 1 else "s")


def strip(grid: Grid, manifest: dict, palette=None) -> tuple[list, list]:
    """Strips ``grid`` in place. Returns (changes lines, report lines). ``palette`` (a remap.Palette) lets a build keep a
    stop-listed block when the palette maps it explicitly."""
    changes, report = [], []
    removed, valuables, shulkers, emptied, signs = {}, 0, 0, 0, 0
    fallback = BlockState.of((palette.valuable_fallback if palette else None) or "minecraft:stone_bricks")
    stopped = set()
    redstone = {}
    for p in grid.positions():
        st = grid.get(*p)
        v = _vanilla(st.name)
        if v is None:
            continue
        if v in TO_AIR:
            removed[v] = removed.get(v, 0) + 1
            grid.set(*p, AIR_STATE)
        elif v in STOP:
            if not (palette and palette.maps_explicitly(st.name)):
                stopped.add(st.name)
        elif is_valuable(st.name):
            valuables += 1
            grid.set(*p, fallback)
        elif v.endswith("shulker_box"):
            shulkers += 1
            facing = st.prop_dict.get("facing", "up")
            grid.set(*p, BlockState.of("minecraft:barrel", {"facing": facing, "open": "false"}), {"id": "minecraft:barrel"})
        elif v.endswith("_leaves"):
            props = st.prop_dict
            if "persistent" in props and props["persistent"] != "true":
                props["persistent"] = "true"
                grid.blocks[grid._i(*p)] = st.with_props(props)
        if any(fnmatch.fnmatchcase(v, pat) for pat in REDSTONE):
            redstone[v] = redstone.get(v, 0) + 1
    if stopped:
        raise StripError("blocks that stop the build (map them in the palette or remove them): " + ", ".join(sorted(stopped)))
    if removed:
        changes.append(f"removed {_plural(sum(removed.values()), 'block')} ("
                       + ", ".join(f"{k} x{n}" for k, n in sorted(removed.items())) + ")")
    if valuables:
        changes.append(f"replaced {_plural(valuables, 'valuable block')} with {short(fallback.name)}")
    if shulkers:
        changes.append(f"replaced {_plural(shulkers, 'shulker box')} with empty barrels")

    keep_sign = bool(manifest.get("keep_sign_text"))
    for p, tag in list(grid.block_entities.items()):
        if grid.get(*p).is_empty:
            del grid.block_entities[p]
            continue
        had = any(k in tag and tag[k] not in ([], {}, "") for k in ("Items", "LootTable", "Book", "RecordItem", "item"))
        for k in BE_REMOVE:
            tag.pop(k, None)
        if had:
            emptied += 1
        if not keep_sign and ("front_text" in tag or "back_text" in tag or "Text1" in tag):
            if _sign_has_text(tag):
                signs += 1
            for k in ("front_text", "back_text", "Text1", "Text2", "Text3", "Text4"):
                tag.pop(k, None)
    if emptied:
        changes.append(f"emptied {_plural(emptied, 'container')}")
    if signs:
        changes.append(f"cleared the text of {_plural(signs, 'sign')}")

    keep = {short(k) for k in manifest.get("keep_entities", [])} & ALLOWED_ENTITIES
    kept, dropped = [], 0
    for e in grid.entities:
        t = short(e.type)
        if t in keep:
            for k in ENTITY_EMPTY:
                e.nbt.pop(k, None)
            e.nbt.pop("UUID", None)
            kept.append(e)
        else:
            dropped += 1
    if len(kept) > MAX_KEPT_ENTITIES:
        raise StripError(f"{len(kept)} kept entities (limit {MAX_KEPT_ENTITIES})")
    grid.entities = kept
    if dropped:
        changes.append(f"removed {_plural(dropped, 'entity')}".replace("entitys", "entities"))
    if redstone:
        report.append("redstone kept (review): " + ", ".join(f"{k} x{n}" for k, n in sorted(redstone.items())))
    return changes, report


def _sign_has_text(tag: dict) -> bool:
    for side in ("front_text", "back_text"):
        msgs = tag.get(side, {}).get("messages", [])
        if any(m not in ('""', "", '{"text":""}') for m in msgs):
            return True
    return any(tag.get(k) not in (None, '""', "") for k in ("Text1", "Text2", "Text3", "Text4"))
