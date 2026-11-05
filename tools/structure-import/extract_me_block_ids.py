#!/usr/bin/env python3
"""Lists the block ids Middle-earth registers, read from its source tree (registry names only, no other data).

    python3 -I tools/structure-import/extract_me_block_ids.py <middle-earth source> [--out me_block_ids.json]

<middle-earth source> is a checkout of Middle-earth (Jukoz, branch ``neoforge-fixes``): either the repo root or its
``middle-earth`` sub-project. The output is a sorted list of ``middle-earth:<path>`` ids that the palettes, templates and
``import.py verify`` check against (specs/addon/structures.md, Palette remapping). It holds ids only: Middle-earth is All
Rights Reserved and we name its ids as integration points, never copy its code or data.

How ids are found (the source registers most blocks through name-building helpers, so a plain grep is not enough):
- literal names given to Middle-earth's block registration helpers;
- names built by its block-set helpers: the set name, the prefix and suffix of each set type, and the suffixes each
  helper appends (read from the helper bodies, so new suffixes are picked up), with the mossy/cracked flags of each set;
- ore sets: the rock prefix times the ores each set lists;
- helpers that reuse a vanilla block when one has that name are filtered with ``vanilla_block_ids.json``.

``--check-lang`` compares the result with the ``block.middle-earth.*`` keys of Middle-earth's committed lang files and
prints the differences (a sanity check only: lang files lag behind the code; nothing from them is written).
Standard library only.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
NS = "middle-earth"
JAVA_PKG = Path("src/main/java/net/sevenstars/middleearth")

# Helpers whose first argument is a block registry name that is always registered under Middle-earth's namespace.
DIRECT_HELPERS = ("registerBlock", "registerMiscBlock", "registerStoneBlock", "registerWoodBlock", "registerTablessBlock",
                  "registerBlockWithItem", "registerPottedBlock", "registerCrossBlock", "registerSimpleSapling",
                  "registerVariantSapling", "registerWeightedSapling")
# Helper that registers only when vanilla has no block of that name.
VANILLA_OR_NEW = "getVanillaOrCreateNew"
# Files that register blocks (ModBlockEntities registers block entity types, not blocks).
BLOCK_FILES = ("block/registration/ModBlocks.java", "block/registration/ModDecorativeBlocks.java",
               "block/registration/ModNatureBlocks.java", "block/registration/GenericBlockSets.java",
               "block/registration/WoodBlockSets.java", "block/registration/StoneBlockSets.java",
               "block/registration/OreRockSets.java", "item/ResourceItemsME.java")


class ExtractError(RuntimeError):
    pass


# ---------------------------------------------------------------------------------------------------------- Java text

def strip_comments(src: str) -> str:
    """Removes // and /* */ comments outside string and char literals (commented-out registrations do not count)."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        if c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            out.append(src[i:j + 1])
            i = j + 1
        elif src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            i = n if j < 0 else j + 2
        else:
            out.append(c)
            i += 1
    return "".join(out)


def balanced(src: str, open_at: int) -> int:
    """Index just past the bracket closing the one at ``open_at`` (string literals skipped)."""
    pairs = {"(": ")", "{": "}", "[": "]"}
    stack, i, n = [], open_at, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            i += 1
            while i < n and src[i] != '"':
                i += 2 if src[i] == "\\" else 1
        elif c in pairs:
            stack.append(pairs[c])
        elif c in ")}]":
            if not stack or stack.pop() != c:
                raise ExtractError(f"unbalanced {c!r} at {i}")
            if not stack:
                return i + 1
        i += 1
    raise ExtractError(f"no closing bracket for {src[open_at]!r} at {open_at}")


def split_args(s: str) -> list:
    """Top-level comma split of an argument list (without its parentheses)."""
    out, depth, cur, i = [], 0, [], 0
    while i < len(s):
        c = s[i]
        if c == '"':
            j = i + 1
            while j < len(s) and s[j] != '"':
                j += 2 if s[j] == "\\" else 1
            cur.append(s[i:j + 1])
            i = j + 1
            continue
        if c in "([{<":
            depth += 1
        elif c in ")]}>":
            depth -= 1
        if c == "," and depth == 0:
            out.append("".join(cur).strip())
            cur = []
        else:
            cur.append(c)
        i += 1
    if "".join(cur).strip():
        out.append("".join(cur).strip())
    return out


def call_args(src: str, name_end: int) -> tuple[list, int]:
    """Arguments of the call whose ``(`` is at or after ``name_end``; returns (args, index past ``)``)."""
    open_at = src.index("(", name_end)
    close = balanced(src, open_at)
    return split_args(src[open_at + 1:close - 1]), close


def eval_name(expr: str, env: dict) -> str:
    """Evaluates a string concatenation of literals and known names (``name``, ``set.setName``, ``x.getPrefix()``...)."""
    parts = []
    for tok in split_plus(expr):
        tok = tok.strip()
        if tok.startswith('"') and tok.endswith('"'):
            parts.append(tok[1:-1])
        elif tok in env:
            parts.append(env[tok])
        else:
            raise ExtractError(f"cannot evaluate {tok!r} in {expr!r}")
    return "".join(parts)


def split_plus(expr: str) -> list:
    out, cur, depth, i = [], [], 0, 0
    while i < len(expr):
        c = expr[i]
        if c == '"':
            j = i + 1
            while j < len(expr) and expr[j] != '"':
                j += 2 if expr[j] == "\\" else 1
            cur.append(expr[i:j + 1])
            i = j + 1
            continue
        if c == "(":
            depth += 1
        elif c == ")":
            depth -= 1
        if c == "+" and depth == 0:
            out.append("".join(cur))
            cur = []
        else:
            cur.append(c)
        i += 1
    out.append("".join(cur))
    return out


# ---------------------------------------------------------------------------------------------------------- extraction

class Extractor:
    def __init__(self, java_root: Path, vanilla: set):
        self.root = java_root
        self.vanilla = vanilla
        self.ids: set = set()
        self.notes: list = []
        self._src: dict = {}
        self.helpers: dict = {}          # create function name -> body (with its ``String name`` parameter)
        self.enums: dict = {}            # enum name -> {constant: (prefix, suffix)}

    def src(self, rel: str) -> str:
        if rel not in self._src:
            p = self.root / rel
            if not p.exists():
                raise ExtractError(f"missing {p}")
            self._src[rel] = strip_comments(p.read_text(encoding="utf-8"))
        return self._src[rel]

    def add(self, path: str, vanilla_check: bool):
        if not re.fullmatch(r"[a-z0-9_./-]+", path):
            raise ExtractError(f"not a registry path: {path!r}")
        if vanilla_check and path in self.vanilla:
            return
        self.ids.add(f"{NS}:{path}")

    # -- enums and helper bodies

    def load_enum(self, rel: str, name: str):
        body = self.src(rel)
        self.enums[name] = {m.group(1): (m.group(2), m.group(3))
                            for m in re.finditer(r"\b([A-Z_]+)\(\s*\"([a-z_]*)\"\s*,\s*\"([a-z_]*)\"\s*\)", body)}
        if not self.enums[name]:
            raise ExtractError(f"no constants in enum {name}")

    def load_helpers(self, rel: str):
        s = self.src(rel)
        for m in re.finditer(r"\bstatic\s+[\w.<>]+\s+(create\w+)\s*\(\s*String\s+name\b", s):
            sig_close = balanced(s, s.index("(", m.end(1)))
            open_brace = s.index("{", sig_close)
            self.helpers[m.group(1)] = s[open_brace + 1:balanced(s, open_brace) - 1]

    def run_helper(self, fn: str, name: str):
        """Interprets one create helper: ``name`` rewrites, then every block it registers."""
        if fn not in self.helpers:
            raise ExtractError(f"unknown block-set helper {fn}")
        body = self.helpers[fn]
        events = []
        for m in re.finditer(r"name\s*=\s*name\.replaceAll\(\s*\"([^\"]*)\"\s*,\s*\"([^\"]*)\"\s*\)", body):
            events.append((m.start(), "replace", (m.group(1), m.group(2))))
        for m in re.finditer(r"if\s*\(\s*Objects\.equals\(\s*name\s*,\s*\"([^\"]*)\"\s*\)\s*\)\s*\{?\s*name\s*=\s*\"([^\"]*)\"", body):
            events.append((m.start(), "set_if", (m.group(1), m.group(2))))
        for m in re.finditer(r"\b(getVanillaOrCreateNew|(?:ModBlocks\.)?register\w*Block)\s*\(", body):
            args, _ = call_args(body, m.end(1))
            events.append((m.start(), "reg", (m.group(1) == VANILLA_OR_NEW, args[0])))
        cur = name
        for _, kind, val in sorted(events, key=lambda e: e[0]):
            if kind == "replace":
                cur = cur.replace(val[0], val[1])            # the patterns used are plain words, not regexes
            elif kind == "set_if":
                if cur == val[0]:
                    cur = val[1]
            else:
                self.add(eval_name(val[1], {"name": cur}), val[0])

    # -- literal registrations

    def literals(self):
        helper_re = re.compile(r"\b(" + "|".join(DIRECT_HELPERS + (VANILLA_OR_NEW,)) + r")\(\s*\"([a-z0-9_]+)\"")
        for rel in BLOCK_FILES:
            s = self.src(rel)
            for m in helper_re.finditer(s):
                self.add(m.group(2), m.group(1) == VANILLA_OR_NEW)
            for m in re.finditer(r"\bString\s+name\s*=\s*\"([a-z0-9_]+)\"\s*;", s):
                self.add(m.group(1), False)

    # -- block sets

    def builder_decls(self, s: str, registrar: str, builder: str):
        """Yields (constructor args, [set types]) for ``registrar(new builder(...).addToSet(T.X)...)``."""
        for m in re.finditer(r"\b" + registrar + r"\s*\(\s*new\s+" + builder + r"\s*\(", s):
            outer_open = s.index("(", m.start() + len(registrar))
            outer_close = balanced(s, outer_open)
            args, ctor_end = call_args(s, m.end() - 1)
            chain = s[ctor_end:outer_close]
            types = re.findall(r"\.addToSet\(\s*\w+\.([A-Z_]+)\s*\)", chain)
            yield args, types

    def case_segments(self, switch_body: str) -> list:
        """[(case constants, segment text)] of an arrow-form switch."""
        marks = list(re.finditer(r"\bcase\s+([A-Z_][A-Z_,\s]*)->", switch_body))
        out = []
        for i, m in enumerate(marks):
            end = marks[i + 1].start() if i + 1 < len(marks) else len(switch_body)
            out.append(([c.strip() for c in m.group(1).split(",")], switch_body[m.end():end]))
        return out

    def eval_cond(self, cond: str, flags: dict, types: list, set_name: str) -> bool:
        cond = cond.strip()
        if "||" in cond:
            return any(self.eval_cond(c, flags, types, set_name) for c in cond.split("||"))
        if "&&" in cond:
            return all(self.eval_cond(c, flags, types, set_name) for c in cond.split("&&"))
        while cond.startswith("(") and balanced(cond, 0) == len(cond):
            cond = cond[1:-1].strip()
        neg = cond.startswith("!")
        cond = cond.lstrip("!").strip()
        m = re.fullmatch(r"set\.(\w+)", cond)
        if m:
            if m.group(1) not in flags:
                raise ExtractError(f"unknown set flag {cond}")
            v = flags[m.group(1)]
        elif (m := re.fullmatch(r"set\.existingList\.contains\(\s*\w+\.([A-Z_]+)\s*\)", cond)):
            v = m.group(1) in types
        elif (m := re.fullmatch(r"set\.setName\.equals\(\s*\"([^\"]*)\"\s*\)", cond)):
            v = set_name == m.group(1)
        else:
            raise ExtractError(f"cannot evaluate condition {cond!r}")
        return v != neg

    def run_segment(self, seg: str, env: dict, flags: dict, types: list, set_name: str):
        """Runs the helper calls of one case segment; a call guarded by a false ``if`` is skipped."""
        call_re = re.compile(r"\b(?:BlockSetRegistration\.)?(create\w+|getVanillaOrCreateNew)\s*\(")
        pos = 0
        while True:
            m = call_re.search(seg, pos)
            if not m:
                break
            args, end = call_args(seg, m.end(1))
            stmt_start = max(seg.rfind(";", 0, m.start()), seg.rfind("{", 0, m.start()), seg.rfind("}", 0, m.start()))
            head = seg[stmt_start + 1:m.start()]
            guard = None
            g = re.match(r"\s*(?:else\s+)?if\s*\(", head)
            if g:
                close = balanced(head, g.end() - 1)
                guard = head[g.end():close - 1]
            name = eval_name(args[0], env)
            if guard is None or self.eval_cond(guard, flags, types, set_name):
                if m.group(1) == VANILLA_OR_NEW:
                    self.add(name, True)
                else:
                    self.run_helper(m.group(1), name)
            pos = end

    def switch_body(self, s: str, registrar: str) -> tuple[str, str]:
        m = re.search(r"\bstatic\s+\w+\s+" + registrar + r"\s*\(\s*\w+\s+set\s*\)", s)
        if not m:
            raise ExtractError(f"no {registrar}")
        body_open = s.index("{", m.end())
        body = s[body_open:balanced(s, body_open)]
        sw = re.search(r"switch\s*\(\s*(\w+)\s*\)\s*\{", body)
        if not sw:
            raise ExtractError(f"{registrar} has no switch")
        return body[sw.end() - 1:balanced(body, sw.end() - 1)], sw.group(1)

    def stone_sets(self):
        rel = "block/registration/StoneBlockSets.java"
        s = self.src(rel)
        body, var = self.switch_body(s, "registerStoneSet")
        segs = self.case_segments(body)
        enum = self.enums["StoneBlockTypes"]
        count = 0
        for args, types in self.builder_decls(s, "registerStoneSet", "StoneBlockSetBuilder"):
            set_name = eval_name(args[0], {})
            flags = {"hasMossy": args[6] == "true", "hasCracked": args[7] == "true"}
            for t in types:
                for consts, seg in segs:
                    if t in consts:
                        p, sfx = enum[t]
                        env = {f"{var}.getPrefix()": p, f"{var}.getSuffix()": sfx, "set.setName": set_name}
                        self.run_segment(seg, env, flags, types, set_name)
            count += 1
        self.notes.append(f"stone sets: {count}")

    def wood_sets(self):
        rel = "block/registration/WoodBlockSets.java"
        s = self.src(rel)
        body, var = self.switch_body(s, "registerWoodSet")
        segs = self.case_segments(body)
        enum = self.enums["WoodBlockTypes"]
        count = 0
        for args, types in self.builder_decls(s, "registerWoodSet", "WoodBlockSetBuilder"):
            set_name = eval_name(args[0], {})
            for t in types:
                for consts, seg in segs:
                    if t in consts:
                        p, sfx = enum[t]
                        env = {f"{var}.getPrefix()": p, f"{var}.getSuffix()": sfx, "set.setName": set_name}
                        self.run_segment(seg, env, {}, types, set_name)
            count += 1
        self.notes.append(f"wood sets: {count}")

    def generic_sets(self):
        rel = "block/registration/GenericBlockSets.java"
        s = self.src(rel)
        wrappers = {}
        for m in re.finditer(r"\bstatic\s+(\w+)\s+(register\w+)\s*\(\s*\w+\s+set\s*\)\s*\{", s):
            body = s[m.end() - 1:balanced(s, m.end() - 1)]
            c = re.search(r"BlockSetRegistration\.(create\w+)\(\s*set\.setName\s*,", body)
            if c:
                wrappers[(m.group(2), m.group(1))] = c.group(1)
        if not wrappers:
            raise ExtractError("no generic set registrars")
        count = 0
        for (registrar, builder), helper in wrappers.items():
            for args, _ in self.builder_decls(s, registrar, builder):
                self.run_helper(helper, eval_name(args[0], {}))
                count += 1
        self.notes.append(f"generic sets: {count}")

    def ore_sets(self):
        rel = "block/registration/OreRockSets.java"
        s = self.src(rel)
        m = re.search(r"\bstatic\s+\w+\s+registerOreSet\s*\(\s*String\s+(\w+)\s*,[^)]*List<\w+>\s+(\w+)", s)
        if not m:
            raise ExtractError("no registerOreSet")
        rock_var, ores_var = m.group(1), m.group(2)
        body_open = s.index("{", m.end())
        body = s[body_open:balanced(s, body_open)]
        ore_names = {}
        for g in re.finditer(r"if\s*\(\s*" + ores_var + r"\.contains\(\s*\w+\.([A-Z_]+)\s*\)\s*\)", body):
            r = re.search(r"\bregister\w*\(\s*([^,]+),", body[g.end():])
            ore_names[g.group(1)] = r.group(1)
        count = 0
        for d in re.finditer(r"\bregisterOreSet\s*\(\s*\"", s):
            args, _ = call_args(s, d.start() + len("registerOreSet"))
            rock = eval_name(args[0], {})
            for ore in re.findall(r"\w+\.([A-Z_]+)", args[2]):
                if ore not in ore_names:
                    raise ExtractError(f"ore {ore} has no registration")
                self.add(eval_name(ore_names[ore], {rock_var: rock}), False)
            count += 1
        self.notes.append(f"ore sets: {count}")

    def run(self) -> list:
        self.load_enum("block/utils/StoneBlockTypes.java", "StoneBlockTypes")
        self.load_enum("block/utils/WoodBlockTypes.java", "WoodBlockTypes")
        self.load_helpers("block/utils/BlockSetRegistration.java")
        self.load_helpers("block/registration/WoodBlockSets.java")
        self.literals()
        self.stone_sets()
        self.wood_sets()
        self.generic_sets()
        self.ore_sets()
        return sorted(self.ids)


# ---------------------------------------------------------------------------------------------------------- command

def java_root(src: Path) -> Path:
    for cand in (src / "middle-earth" / JAVA_PKG, src / JAVA_PKG):
        if cand.is_dir():
            return cand
    raise ExtractError(f"no {JAVA_PKG} under {src} or {src / 'middle-earth'}")


def source_revision(src: Path) -> str | None:
    """The checked-out commit, read from .git without running git (None if unknown)."""
    git = src / ".git"
    if not git.is_dir():
        git = src.parent / ".git"
    try:
        head = (git / "HEAD").read_text(encoding="utf-8").strip()
        if not head.startswith("ref: "):
            return head
        ref = head[5:]
        p = git / ref
        if p.exists():
            return p.read_text(encoding="utf-8").strip()
        for line in (git / "packed-refs").read_text(encoding="utf-8").splitlines():
            if line.endswith(" " + ref):
                return line.split()[0]
    except OSError:
        return None
    return None


def load_vanilla(path: Path) -> set:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return {i.split(":", 1)[1] for i in data["ids"]}


def lang_ids(src: Path) -> set:
    lang = java_root(src).parents[3] / "resources" / "assets" / NS / "lang"
    out = set()
    for f in sorted(lang.glob("*.json")):
        for k in json.loads(f.read_text(encoding="utf-8")):
            if k.startswith(f"block.{NS}."):
                out.add(f"{NS}:{k[len('block.' + NS + '.'):]}")
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("source", type=Path, help="Middle-earth source checkout (read only)")
    ap.add_argument("--vanilla", type=Path, default=HERE / "vanilla_block_ids.json")
    ap.add_argument("--out", type=Path, default=HERE / "me_block_ids.json")
    ap.add_argument("--check-lang", action="store_true", help="print differences with Middle-earth's lang keys")
    a = ap.parse_args(argv)
    try:
        ex = Extractor(java_root(a.source), load_vanilla(a.vanilla))
        ids = ex.run()
    except ExtractError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    rev = source_revision(a.source)
    out = {
        "description": "Block registry ids Middle-earth registers (ids only, integration points; no other data). "
                       "Generated by tools/structure-import/extract_me_block_ids.py; do not edit by hand.",
        "source": "Middle-earth (Jukoz), branch neoforge-fixes" + (f", commit {rev}" if rev else ""),
        "count": len(ids),
        "ids": ids,
    }
    a.out.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
    print(f"wrote {a.out} ({len(ids)} ids; {', '.join(ex.notes)})")
    if a.check_lang:
        lang = lang_ids(a.source)
        only_lang = sorted(lang - set(ids))
        only_code = sorted(set(ids) - lang)
        print(f"lang check: {len(lang)} block keys; {len(only_lang)} only in lang, {len(only_code)} only in code")
        for i in only_lang:
            print(f"  only in lang: {i}")
        for i in only_code:
            print(f"  only in code: {i}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
