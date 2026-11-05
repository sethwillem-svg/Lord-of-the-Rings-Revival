"""Palette remapping (spec "Palette remapping"). Every block must match a rule; nothing is swapped silently."""
from __future__ import annotations

import fnmatch
import hashlib
import json
from pathlib import Path

from .grid import EMPTY_NAMES, BlockState, Grid

PALETTE_DIR = Path(__file__).resolve().parent.parent / "palettes"


class RemapError(ValueError):
    def __init__(self, msg, unmatched=()):
        super().__init__(msg)
        self.unmatched = sorted(unmatched)


class Palette:
    def __init__(self, chain: list):
        self.chain = chain                      # child first
        self.id = chain[0]["id"]

    @staticmethod
    def load(name: str, directory: Path = PALETTE_DIR, _seen=None) -> "Palette":
        seen = _seen or set()
        if name in seen:
            raise RemapError(f"palette {name} extends itself")
        seen.add(name)
        path = Path(directory) / f"{name}.json"
        if not path.exists():
            raise RemapError(f"no palette {name} ({path})")
        data = json.loads(path.read_text(encoding="utf-8"))
        chain = [data]
        if data.get("extends"):
            chain += Palette.load(data["extends"], directory, seen).chain
        return Palette(chain)

    @staticmethod
    def of(*dicts) -> "Palette":
        return Palette(list(dicts))

    @property
    def valuable_fallback(self):
        for p in self.chain:
            if p.get("valuable_fallback"):
                return p["valuable_fallback"]
        return None

    def maps_explicitly(self, name: str) -> bool:
        return any(name in p.get("blocks", {}) for p in self.chain)

    def rule(self, name: str):
        """(kind, target) for a block id: target is an id, or a weighted list for ``random``; None if unmatched."""
        if name in EMPTY_NAMES:
            return "empty", name
        for p in self.chain:
            if name in p.get("blocks", {}):
                return "blocks", p["blocks"][name]
            for fam in p.get("families", []):
                for variant, src in fam["from"].items():
                    if src == name:
                        if variant not in fam["to"]:
                            raise RemapError(f"palette {p['id']}: family has no target for {variant}")
                        return "family", fam["to"][variant]
            if name in p.get("random", {}):
                return "random", p["random"][name]
            if any(fnmatch.fnmatchcase(name, pat) for pat in p.get("keep", [])):
                return "keep", name
        return None


def load_dump(path) -> dict | None:
    p = Path(path)
    if not p.exists():
        return None
    return json.loads(p.read_text(encoding="utf-8"))["blocks"]


def pick(options, build_id: str, pos) -> str:
    total = sum(int(w) for _, w in options)
    h = int.from_bytes(hashlib.sha256(f"{build_id}:{pos[0]},{pos[1]},{pos[2]}".encode()).digest()[:8], "big")
    r = h % total
    for target, w in options:
        r -= int(w)
        if r < 0:
            return target
    return options[-1][0]


def transfer(src: BlockState, target: str, dump: dict | None, warnings: set) -> BlockState:
    """Copies properties by name where the target has that property and value; others take the target's default."""
    if dump is None:
        return BlockState.of(target, src.prop_dict)
    info = dump.get(target)
    if info is None:
        raise RemapError(f"{target} is not in the block dump")
    allowed = info.get("properties", {})
    default = info.get("default", {})
    out, lost = {}, []
    sp = src.prop_dict
    for k, values in allowed.items():
        if k in sp and sp[k] in values:
            out[k] = sp[k]
        else:
            out[k] = default.get(k, values[0] if values else "")
            if k in sp:
                lost.append(f"{k}={sp[k]}")
    lost += [f"{k}={v}" for k, v in sp.items() if k not in allowed]
    if lost and (src.name, target) not in {(w[0], w[1]) for w in warnings}:
        warnings.add((src.name, target, ", ".join(sorted(lost))))
    return BlockState.of(target, out)


def remap(grid: Grid, palette: Palette, dump: dict | None, build_id: str) -> tuple[list, list]:
    """Remaps ``grid`` in place. Returns (changes lines, warning lines). Raises RemapError listing unmatched blocks."""
    unmatched = set()
    for st in set(grid.blocks):
        if palette.rule(st.name) is None:
            unmatched.add(st.name)
    if unmatched:
        raise RemapError("blocks no palette rule matches: " + ", ".join(sorted(unmatched)), unmatched)
    warnings = set()
    cache = {}
    to_me, other = 0, 0
    for p in grid.positions():
        st = grid.get(*p)
        kind, target = palette.rule(st.name)
        if kind == "empty":
            continue
        if kind == "random":
            target = pick(target, build_id, p)
            new = transfer(st, target, dump, warnings)
        else:
            key = (st, target)
            if key not in cache:
                cache[key] = transfer(st, target, dump, warnings)
            new = cache[key]
        if new.name != st.name:
            if new.name.startswith("middle-earth:"):
                to_me += 1
            else:
                other += 1
        grid.blocks[grid._i(*p)] = new
        be = grid.block_entities.get(p)
        if be is not None and new.name != st.name and be.get("id") == st.name:
            be["id"] = new.name
    changes = []
    if to_me:
        changes.append(f"palette: {to_me:,} blocks swapped to Middle-earth blocks ({palette.id} palette)")
    if other:
        changes.append(f"palette: {other:,} blocks swapped to other vanilla blocks ({palette.id} palette)")
    warn = [f"{a} -> {b}: {lost} not carried, target default used" for a, b, lost in sorted(warnings)]
    return changes, warn


def validate(grid: Grid, dump: dict | None) -> list:
    """Every output id and property must exist in the block dump."""
    if dump is None:
        return []
    errs = []
    for st in sorted(set(grid.blocks)):
        info = dump.get(st.name)
        if info is None:
            errs.append(f"unknown block {st.name}")
            continue
        for k, v in st.props:
            if v not in info.get("properties", {}).get(k, []):
                errs.append(f"{st.name}: property {k}={v} does not exist")
    return errs
