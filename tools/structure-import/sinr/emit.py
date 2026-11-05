"""Generated data (spec "Data and APIs" / Generated data). Every function returns {repo-relative path: bytes}."""
from __future__ import annotations

import hashlib
import json

from . import licences
from .paths import DATA_REL

KINDS = ("outpost", "castle", "camp", "bridge", "ruin", "watchtower", "tower", "hideout", "village")
NS = "lotr_revival"


def dump_json(obj) -> bytes:
    return (json.dumps(obj, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def salt(set_id: str) -> int:
    """A stable hash of the set id (non-negative int, as vanilla's codec needs)."""
    return int.from_bytes(hashlib.sha256(set_id.encode("utf-8")).digest()[:4], "big") & 0x7FFFFFFF


def d(path: str) -> str:
    return (DATA_REL / path).as_posix()


def template_id(build_id: str) -> str:
    return f"{NS}:build/{build_id}"


def structure_id(build_id: str) -> str:
    return f"{NS}:{build_id}"


def set_name(m: dict) -> str:
    return m["region"] + ("_crossings" if m.get("site") == "river_crossing" else "")


def per_build(m: dict, nbt_bytes: bytes) -> dict:
    bid = m["id"]
    info = m.get("output_info") or {}
    structure = {
        "type": f"{NS}:build",
        "biomes": f"#{NS}:region/{m['region']}",
        "step": "surface_structures",
        "spawn_overrides": {},
        "terrain_adaptation": "beard_thin",
        "start_pool": template_id(bid),
        "build": bid,
        "template": template_id(bid),
        "size": info.get("size", [0, 0, 0]),
        "max_slope": m.get("max_slope", 6),
        "site": m.get("site", "land"),
        "ground_y": info.get("ground_offset", 1) - 1,
    }
    pool = {
        "fallback": "minecraft:empty",
        "elements": [{
            "weight": 1,
            "element": {
                "element_type": "minecraft:single_pool_element",
                "location": template_id(bid),
                "processors": m.get("processors") or "minecraft:empty",
                "projection": "rigid",
            },
        }],
    }
    return {
        d(f"structure/build/{bid}.nbt"): nbt_bytes,
        d(f"worldgen/structure/{bid}.json"): dump_json(structure),
        d(f"worldgen/template_pool/build/{bid}.json"): dump_json(pool),
    }


def credit(m: dict) -> dict:
    own = bool(m.get("own_work"))
    c = {"title": m["title"], "author": m["author"]}
    if m.get("source_url"):
        c["source_url"] = m["source_url"]
    c["licence"] = m.get("output_licence") or licences.output_licence(m["licence"])
    if not own:
        c["source_licence"] = m["licence"]
        c["licence_url"] = m.get("licence_url") or licences.url(m["licence"])
    summary = changes_summary(m)
    if summary:
        c["changes"] = summary
    if m.get("attribution"):
        c["attribution"] = m["attribution"]
    c["own_work"] = own
    return c


def changes_summary(m: dict) -> str:
    return "; ".join(list(m.get("manual_changes", [])) + list(m.get("changes", [])))


def aggregates(manifests: list, regions: dict, venues_me: dict) -> dict:
    """Sets, biome tags, venue tags and the structure_info data map, from every buildable manifest."""
    out = {}
    by_set = {}
    for m in manifests:
        by_set.setdefault(set_name(m), []).append(m)
    for name, ms in sorted(by_set.items()):
        region_id = ms[0]["region"]
        r = regions[region_id]
        crossings = name.endswith("_crossings")
        set_id = f"{NS}:{name}"
        placement = {
            "type": f"{NS}:configured_spread",
            "set": set_id,
            "region": region_id,
            "spacing": r.get("crossings_spacing", 12) if crossings else r["spacing"],
            "separation": r.get("crossings_separation", 4) if crossings else r["separation"],
            "salt": salt(set_id),
            "exclusions": [{"set": r["me_set"], "chunks": 3}] if r.get("me_set") else [],
            "own_chunks": 2,
        }
        out[d(f"worldgen/structure_set/{name}.json")] = dump_json({
            "structures": [{"structure": structure_id(m["id"]), "weight": m.get("weight", 1)}
                           for m in sorted(ms, key=lambda x: x["id"])],
            "placement": placement,
        })
    for rid, r in sorted(regions.items()):
        out[d(f"tags/worldgen/biome/region/{rid}.json")] = dump_json({
            "replace": False,
            "values": [{"id": f"#middle-earth:has_structure/{t}", "required": False} for t in r["me_biome_tags"]],
        })
    for kind in KINDS:
        ours = sorted(structure_id(m["id"]) for m in manifests if kind in m["kinds"])
        theirs = sorted(venues_me.get(kind, {}).keys())
        out[d(f"tags/worldgen/structure/event_venues/{kind}.json")] = dump_json({
            "replace": False,
            "values": ours + [{"id": f"middle-earth:{s}", "required": False} for s in theirs],
        })
    values = {}
    for m in sorted(manifests, key=lambda x: x["id"]):
        r = regions[m["region"]]
        faction = m.get("faction") or r.get("faction")
        entry = {}
        if faction:
            entry["faction"] = faction
        entry["residents"] = bool(m.get("manager"))
        entry["region"] = m["region"]
        entry["ground_offset"] = (m.get("output_info") or {}).get("ground_offset", 0)
        entry["build"] = m["id"]
        entry["credit"] = credit(m)
        values[structure_id(m["id"])] = entry
    for kind in KINDS:
        for s, info in sorted(venues_me.get(kind, {}).items()):
            key = f"middle-earth:{s}"
            if key in values:
                continue
            entry = {}
            if info.get("faction"):
                entry["faction"] = info["faction"]
            entry["residents"] = bool(info.get("residents"))
            entry["ground_offset"] = int(info.get("ground_offset", 0))
            values[key] = entry
    out[d("data_maps/worldgen/structure/structure_info.json")] = dump_json({"values": values})
    return out
