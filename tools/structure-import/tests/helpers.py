"""Shared test setup: puts the tool folder on sys.path (python -I does not) and small builders."""
import sys
from pathlib import Path

TOOL = Path(__file__).resolve().parents[1]
FIXTURES = Path(__file__).resolve().parent / "fixtures"
if str(TOOL) not in sys.path:
    sys.path.insert(0, str(TOOL))

from sinr.grid import BlockState, Grid  # noqa: E402

S = BlockState.of


def grid_of(sx, sy, sz, fill="minecraft:air") -> Grid:
    g = Grid(sx, sy, sz, data_version=3955)
    for p in g.positions():
        g.set(*p, S(fill))
    return g


def manifest(**over) -> dict:
    m = {
        "id": "test_build",
        "status": "approved",
        "title": "Test Build",
        "author": "Jane Doe",
        "author_url": None,
        "co_authors": [],
        "source_url": "https://example.org/p/test",
        "archive_url": "https://web.archive.org/web/2026/https://example.org/p/test",
        "licence": "CC-BY-4.0",
        "licence_url": "https://creativecommons.org/licenses/by/4.0/",
        "licence_evidence": "stated on the page",
        "attribution": None,
        "retrieved": "2026-11-05",
        "source_file": None,
        "source_sha256": None,
        "own_work": False,
        "approved_by": "sethwillem-svg",
        "approved_on": "2026-11-06",
        "region": "gondor",
        "kinds": ["ruin"],
        "faction": None,
        "palette": "neutral",
        "site": "land",
        "max_slope": 6,
        "ground_y": None,
        "weight": 1,
        "processors": None,
        "keep_sign_text": False,
        "keep_entities": [],
        "manager": None,
        "nests": [],
        "manual_changes": [],
        "changes": [],
        "output": "addon/src/main/resources/data/lotr_revival/structure/build/test_build.nbt",
        "output_licence": None,
        "notes": "",
    }
    m.update(over)
    return m
