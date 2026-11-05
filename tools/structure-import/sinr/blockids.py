"""Block id validation against the committed id lists (spec "Palette remapping", Validation).

``vanilla_block_ids.json`` (vanilla 1.21.1) and ``me_block_ids.json`` (Middle-earth, from extract_me_block_ids.py) hold
registry ids only. They catch wrong names in palettes and templates even when the full block dump (``blocks.json``, with
properties) has not been generated; properties are still checked against the dump.
"""
from __future__ import annotations

import fnmatch
import json
from pathlib import Path

from . import nbt

VANILLA_FILE = "vanilla_block_ids.json"
ME_FILE = "me_block_ids.json"


def load_known(tool_dir: Path) -> set | None:
    """Every known block id, or None when either list is missing."""
    out = set()
    for name in (VANILLA_FILE, ME_FILE):
        p = Path(tool_dir) / name
        if not p.exists():
            return None
        out |= set(json.loads(p.read_text(encoding="utf-8"))["ids"])
    return out


def _check_id(where: str, block: str, known: set, out: list):
    if not isinstance(block, str) or ":" not in block:
        out.append(f"{where}: {block!r} is not a namespaced block id")
    elif block not in known:
        out.append(f"{where}: unknown block {block}")


def palette_problems(palette: dict, known: set, palette_ids: set) -> list:
    """Every id a palette names (inputs and outputs) must exist; every ``keep`` pattern must match a known id."""
    pid = palette.get("id", "?")
    out = []
    if palette.get("extends") and palette["extends"] not in palette_ids:
        out.append(f"palette {pid}: extends unknown palette {palette['extends']}")
    for src, dst in palette.get("blocks", {}).items():
        _check_id(f"palette {pid} blocks", src, known, out)
        _check_id(f"palette {pid} blocks[{src}]", dst, known, out)
    for i, fam in enumerate(palette.get("families", [])):
        for side in ("from", "to"):
            for variant, block in fam.get(side, {}).items():
                _check_id(f"palette {pid} families[{i}].{side}.{variant}", block, known, out)
        missing = set(fam.get("from", {})) - set(fam.get("to", {}))
        if missing:
            out.append(f"palette {pid} families[{i}]: no target for {', '.join(sorted(missing))}")
    for src, options in palette.get("random", {}).items():
        _check_id(f"palette {pid} random", src, known, out)
        for target, weight in options:
            _check_id(f"palette {pid} random[{src}]", target, known, out)
            if int(weight) <= 0:
                out.append(f"palette {pid} random[{src}]: weight {weight} for {target}")
    for pat in palette.get("keep", []):
        if pat == "*":
            continue
        if any(c in pat for c in "*?["):
            if not any(fnmatch.fnmatchcase(k, pat) for k in known):
                out.append(f"palette {pid} keep: pattern {pat} matches no known block")
        else:
            _check_id(f"palette {pid} keep", pat, known, out)
    if palette.get("valuable_fallback"):
        _check_id(f"palette {pid} valuable_fallback", palette["valuable_fallback"], known, out)
    return out


def palettes_problems(palette_dir: Path, known: set) -> list:
    files = sorted(Path(palette_dir).glob("*.json"))
    data = [json.loads(p.read_text(encoding="utf-8")) for p in files]
    ids = {d.get("id") for d in data}
    out = []
    for p, d in zip(files, data):
        if d.get("id") != p.stem:
            out.append(f"palette file {p.name}: id {d.get('id')!r} does not match its name")
        out += palette_problems(d, known, ids)
    return out


def template_problems(path: Path, known: set) -> list:
    """Every palette entry of a vanilla structure template (.nbt) must be a known block."""
    root = nbt.load_file(path)
    palettes = [root["palette"]] if "palette" in root else list(root.get("palettes", []))
    out = []
    for pal in palettes:
        for entry in pal:
            name = str(entry.get("Name", ""))
            if name not in known:
                out.append(f"template {Path(path).name}: unknown block {name}")
    return sorted(set(out))


def grid_problems(names, known: set) -> list:
    return [f"unknown block {n}" for n in sorted(set(names)) if n not in known]
