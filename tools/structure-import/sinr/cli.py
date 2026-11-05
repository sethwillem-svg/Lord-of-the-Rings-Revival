"""Command line: python3 -I tools/structure-import/import.py <command> (see the spec's table of commands)."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import formats, manifest, nests, pipeline
from .paths import Repo


def cmd_inspect(repo: Repo, a) -> int:
    fmt, g = formats.read_file(a.file)
    print(f"format: {fmt}")
    print(f"DataVersion: {g.data_version}")
    print(f"size: {g.sx} x {g.sy} x {g.sz} ({g.non_air_count():,} non-air)")
    print("palette:")
    for st, n in sorted(g.palette_counts().items(), key=lambda kv: (-kv[1], str(kv[0]))):
        print(f"  {n:>8,}  {st}")
    print(f"block entities: {len(g.block_entities)}")
    for p, t in sorted(g.block_entities.items()):
        print(f"  {p[0]},{p[1]},{p[2]}  {t.get('id', '?')}  keys={sorted(k for k in t if k != 'id')}")
    print(f"entities: {len(g.entities)}")
    for e in g.entities:
        print(f"  {e.pos[0]:.2f},{e.pos[1]:.2f},{e.pos[2]:.2f}  {e.type}")
    return 0


def _manifest(repo: Repo, bid: str) -> dict:
    p = repo.builds / bid / "build.json"
    if not p.exists():
        raise SystemExit(f"no manifest {p}")
    return manifest.load(p)


def cmd_check(repo: Repo, a) -> int:
    m = _manifest(repo, a.id)
    errs = manifest.gate(m, repo.builds / a.id)
    if errs:
        for e in errs:
            print(f"REFUSED {a.id}: {e}")
        return 1
    print(f"ok {a.id}: gate passes ({m['licence']}, status {m['status']})")
    return 0


def cmd_convert(repo: Repo, a) -> int:
    m = _manifest(repo, a.id)
    log = []
    _, cls = pipeline.convert(repo, m, log)
    for line in log:
        print(line)
    print(f"wrote {repo.work / a.id / 'raw.nbt'} ({cls})")
    return 0


def cmd_build(repo: Repo, a) -> int:
    if not a.all and not a.id:
        raise SystemExit("build <id> or build --all")
    files = pipeline.build_all(repo, only=None if a.all else a.id)
    pipeline.write_files(repo.root, files)
    print(f"wrote {len(files)} files")
    return 0


def cmd_extract(repo: Repo, a) -> int:
    path = repo.builds / a.id / "build.json"
    m = _manifest(repo, a.id)
    _, g = formats.read_file(a.saved)
    mgr, ns = nests.extract(g)
    if mgr is None:
        print("no structure manager in the saved template")
        return 1
    m["manager"] = mgr
    m["nests"] = ns
    manifest.save(path, m)
    print(f"{a.id}: manager at {mgr['pos']} pool {mgr['pool']}, {len(ns)} nests")
    return 0


def cmd_docs(repo: Repo, a) -> int:
    files = pipeline.docs_files(repo, pipeline.load_manifests(repo))
    pipeline.write_files(repo.root, files)
    print(f"wrote {', '.join(sorted(files))}")
    return 0


def cmd_verify(repo: Repo, a) -> int:
    problems = pipeline.verify(repo)
    if problems:
        print(f"verify: {len(problems)} problem(s)")
        return 1
    print("verify: generated structure files are up to date")
    return 0


def main(argv=None, repo: Repo | None = None) -> int:
    repo = repo or Repo.default()
    ap = argparse.ArgumentParser(prog="import.py", description="Licensed structure builds -> Middle-earth add-on data")
    sub = ap.add_subparsers(dest="cmd", required=True)
    p = sub.add_parser("inspect")
    p.add_argument("file", type=Path)
    for name in ("check", "convert"):
        sub.add_parser(name).add_argument("id")
    p = sub.add_parser("build")
    p.add_argument("id", nargs="?")
    p.add_argument("--all", action="store_true")
    p = sub.add_parser("extract-nests")
    p.add_argument("id")
    p.add_argument("saved", type=Path)
    sub.add_parser("docs")
    sub.add_parser("verify")
    a = ap.parse_args(argv)
    fn = {"inspect": cmd_inspect, "check": cmd_check, "convert": cmd_convert, "build": cmd_build,
          "extract-nests": cmd_extract, "docs": cmd_docs, "verify": cmd_verify}[a.cmd]
    try:
        return fn(repo, a)
    except (pipeline.BuildError, formats.FormatError, nests.NestError, ValueError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
