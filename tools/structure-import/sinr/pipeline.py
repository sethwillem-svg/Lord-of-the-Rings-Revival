"""convert, build, docs and verify: the steps behind import.py's commands."""
from __future__ import annotations

import gzip
import json
import shutil
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

from . import docs, emit, fit, formats, limits, licences, manifest, nbt, nests, remap, strip, versions
from .formats import vanilla
from .paths import Repo


class BuildError(ValueError):
    pass


@dataclass
class Result:
    manifest: dict
    files: dict = field(default_factory=dict)     # repo-relative path -> bytes
    log: list = field(default_factory=list)


def manifest_paths(repo: Repo) -> list:
    return sorted(repo.builds.glob("*/build.json"))


def load_manifests(repo: Repo) -> list:
    out = []
    for p in manifest_paths(repo):
        m = manifest.load(p)
        if m.get("id") != p.parent.name:
            raise BuildError(f"{p}: id {m.get('id')!r} does not match its folder")
        out.append(m)
    return out


def load_regions(repo: Repo) -> dict:
    return {r["id"]: r for r in json.loads((repo.tool / "regions.json").read_text(encoding="utf-8"))["regions"]}


def load_venues_me(repo: Repo) -> dict:
    return json.loads((repo.tool / "venues_middle_earth.json").read_text(encoding="utf-8"))["kinds"]


def source_path(repo: Repo, m: dict) -> Path:
    if not m.get("source_file"):
        raise BuildError(f"{m['id']}: no source_file in the repo; the maintainer runs build locally with the source in "
                         f"work/{m['id']}/source")
    return repo.builds / m["id"] / m["source_file"]


def convert(repo: Repo, m: dict, log: list):
    """Reads the source into a Grid; writes work/<id>/raw.nbt. Returns (Grid, version class)."""
    src = source_path(repo, m)
    errs = limits.check_source(src.stat().st_size) if src.exists() else []
    if errs:
        raise BuildError("; ".join(errs))
    fmt, grid = formats.read_file(src)
    log.append(f"read {src.name}: {fmt}, DataVersion {grid.data_version}, {grid.sx} x {grid.sy} x {grid.sz}")
    cls = versions.classify(grid.data_version)
    work = repo.work / m["id"]
    work.mkdir(parents=True, exist_ok=True)
    (work / "raw.nbt").write_bytes(nbt.dumps(vanilla.write(grid)))
    return grid, cls


def build(repo: Repo, m: dict, dump: dict | None) -> Result:
    """Runs the whole pipeline for one manifest. Returns the per-build files and the updated manifest (not written)."""
    res = Result(manifest=json.loads(json.dumps(m)))
    m = res.manifest
    log = res.log
    errs = manifest.gate(m, repo.builds / m["id"])
    if errs:
        raise BuildError("licence gate: " + "; ".join(errs))
    grid, cls = convert(repo, m, log)
    if cls == "upgrade":
        up = repo.work / m["id"] / "upgraded.nbt"
        if not up.exists():
            raise BuildError(f"DataVersion {grid.data_version} needs upgrading: {versions.UPGRADE_HINT}")
        _, grid = formats.read_file(up)
        log.append(f"using upgraded.nbt (DataVersion {grid.data_version})")
    errs = manifest.content_gate(m, grid)
    if errs:
        raise BuildError("licence gate: " + "; ".join(errs))
    palette = remap.Palette.load(m["palette"], repo.palettes)
    changes = []
    c, report = strip.strip(grid, m, palette)
    changes += c
    log += report
    c, warns = remap.remap(grid, palette, dump, m["id"])
    changes += c
    log += [f"WARN {w}" for w in warns]
    grid, ground_y, c = fit.fit(grid, m.get("ground_y"))
    changes += c
    c, npc_max = nests.insert(grid, m, repo.pools)
    changes += c
    errs = remap.validate(grid, dump)
    if errs:
        raise BuildError("block dump: " + "; ".join(errs))
    errs, warns = limits.check_grid(grid)
    log += [f"WARN {w}" for w in warns]
    if errs:
        raise BuildError("limits: " + "; ".join(errs))
    data = nbt.dumps(vanilla.write(grid))
    errs = limits.check_output(len(data))
    if errs:
        raise BuildError("limits: " + "; ".join(errs))
    m["changes"] = changes
    m["output_licence"] = licences.output_licence(m["licence"])
    m["output_info"] = {"size": [grid.sx, grid.sy, grid.sz], "ground_offset": ground_y + 1,
                        "blocks": grid.non_air_count(), "block_entities": len(grid.block_entities), "npc_max": npc_max}
    if m["status"] == "approved":
        m["status"] = "imported"
    expected = emit.d(f"structure/build/{m['id']}.nbt")
    if m["output"] != expected:
        raise BuildError(f"output must be {expected}")
    res.files.update(emit.per_build(m, data))
    res.files[repo.rel(repo.builds / m["id"] / "build.json")] = (json.dumps(m, indent=2, ensure_ascii=False) + "\n").encode()
    log.append(f"built {m['id']}: {grid.sx} x {grid.sy} x {grid.sz}, {grid.non_air_count()} blocks, ground_y={ground_y}, "
               f"up to {npc_max} NPCs")
    log += [f"change: {x}" for x in changes]
    return res


def docs_files(repo: Repo, manifests: list) -> dict:
    notice = repo.notice.read_text(encoding="utf-8") if repo.notice.exists() else ""
    shown = [m for m in manifests if manifest.buildable(m)]
    return {
        repo.rel(repo.docs): docs.licence_log(manifests).encode("utf-8"),
        repo.rel(repo.notice): docs.replace_notice(notice, docs.notice_block(shown)).encode("utf-8"),
    }


def build_all(repo: Repo, only: str | None = None, log=print) -> dict:
    """Every buildable manifest, then the aggregates and docs. Returns all generated files."""
    dump_path, real = repo.block_dump()
    dump = remap.load_dump(dump_path)
    if not real:
        log(f"note: no block dump at {repo.tool / 'blocks.json'}; validating against the test fixture {dump_path.name}")
    files = {}
    built = []
    manifests = load_manifests(repo)
    if only:
        asked = [m for m in manifests if m["id"] == only]
        if not asked:
            raise BuildError(f"no manifest {only}")
        if not manifest.buildable(asked[0]):
            raise BuildError(f"{only} is not buildable: " + "; ".join(manifest.gate(asked[0]) or [asked[0]["status"]]))
    for m in manifests:
        if not manifest.buildable(m):
            continue
        if only and m["id"] != only:
            built.append(m)       # keep the committed state for the aggregates
            continue
        r = build(repo, m, dump)
        files.update(r.files)
        built.append(r.manifest)
        for line in r.log:
            log(f"[{m['id']}] {line}")
        work = repo.work / m["id"]
        work.mkdir(parents=True, exist_ok=True)
        (work / "import.log").write_text("\n".join(r.log) + "\n", encoding="utf-8")
    files.update(emit.aggregates(built, load_regions(repo), load_venues_me(repo)))
    by_id = {m["id"]: m for m in built}
    files.update(docs_files(repo, [by_id.get(m["id"], m) for m in manifests]))
    return files


def write_files(root: Path, files: dict):
    for rel, data in files.items():
        p = Path(root) / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(data)


def generated_dirs() -> list:
    """Folders whose files are all generated (so a stale file left in them is an error)."""
    return [emit.d("structure/build"), emit.d("worldgen/structure"), emit.d("worldgen/template_pool/build"),
            emit.d("worldgen/structure_set"), emit.d("tags/worldgen/biome/region"),
            emit.d("tags/worldgen/structure/event_venues")]


def same_content(rel: str, a: bytes, b: bytes) -> bool:
    """Gzipped NBT is compared uncompressed: zlib builds may compress the same bytes differently."""
    if rel.endswith(".nbt") or rel.endswith(".schem") or rel.endswith(".litematic"):
        return gzip.decompress(a) == gzip.decompress(b)
    return a == b


def verify(repo: Repo, log=print) -> list:
    """Rebuilds everything in a temporary copy of the tool folder and compares with the committed files."""
    problems = []
    for m in load_manifests(repo):
        if manifest.buildable(m):
            errs = manifest.gate(m, repo.builds / m["id"])
            if errs:
                problems.append(f"{m['id']}: gate: {'; '.join(errs)}")
    with tempfile.TemporaryDirectory(prefix="sinr-verify-") as tmp:
        tmp_repo = Repo(Path(tmp))
        shutil.copytree(repo.tool, tmp_repo.tool, ignore=shutil.ignore_patterns("work", "__pycache__", "blocks.json"))
        if repo.pools.exists():
            shutil.copytree(repo.pools, tmp_repo.pools)
        if repo.notice.exists():
            shutil.copy(repo.notice, tmp_repo.notice)
        try:
            files = build_all(tmp_repo, log=lambda s: None)
        except Exception as e:  # noqa: BLE001
            return problems + [f"build failed: {e}"]
    for rel, data in sorted(files.items()):
        p = repo.root / rel
        if not p.exists():
            problems.append(f"missing generated file {rel}")
        elif not same_content(rel, p.read_bytes(), data):
            problems.append(f"out of date: {rel} (run: python3 -I tools/structure-import/import.py build --all)")
    for folder in generated_dirs():
        for p in sorted((repo.root / folder).glob("**/*")):
            if p.is_file() and repo.rel(p) not in files:
                problems.append(f"stale generated file {repo.rel(p)}")
    for line in problems:
        log(f"verify: {line}")
    return problems
