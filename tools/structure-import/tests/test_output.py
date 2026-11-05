"""Nests, emitted files, verify, docs and NOTICE."""
import json
import shutil
import tempfile
import unittest
from pathlib import Path

from helpers import TOOL, grid_of, manifest
from sinr import docs, emit, nbt, nests, pipeline
from sinr.formats import vanilla
from sinr.paths import Repo

REPO = Repo.default()


def with_nests(**over):
    m = manifest(manager={"pos": [0, 0, 0], "block": "middle-earth:structure_manager", "facing": "east",
                          "pool": "lotr_revival:gondor_watchtower"},
                 nests=[{"pos": [2, 0, 0], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 2},
                        {"pos": [0, 1, 2], "nest": "lotr_revival:gondor_watchtower.archer", "radius": 4}])
    m.update(over)
    return m


class NestTests(unittest.TestCase):
    def test_keys_and_values(self):
        g = grid_of(3, 2, 3)
        changes, npcs = nests.insert(g, with_nests(), REPO.pools)
        self.assertEqual(changes, ["added a structure manager and 2 nests"])
        self.assertEqual(npcs, 3)
        self.assertEqual(str(g.get(0, 0, 0)), "middle-earth:structure_manager[facing=east]")
        self.assertEqual(g.block_entities[(0, 0, 0)], {
            "id": "middle-earth:structure_manager", "structure_manager.Identifier": "lotr_revival:gondor_watchtower",
            "structure_manager.ToInitialize": 1, "structure_manager.Enabled": 0})
        self.assertIsInstance(g.block_entities[(0, 0, 0)]["structure_manager.ToInitialize"], nbt.Byte)
        self.assertEqual(str(g.get(2, 0, 0)), "middle-earth:structure_nest[enabled=true,waterlogged=false]")
        self.assertEqual(g.block_entities[(2, 0, 0)], {
            "id": "middle-earth:structure_nest", "structure_nest.ManagerId": "lotr_revival:gondor_watchtower",
            "structure_nest.NestId": "lotr_revival:gondor_watchtower.guard", "structure_nest.SpawnRadius": 2,
            "structure_nest.IsEnabled": 1})
        self.assertIsInstance(g.block_entities[(2, 0, 0)]["structure_nest.SpawnRadius"], nbt.Int)

    def test_distance_limit(self):
        g = grid_of(22, 1, 1)
        ok = with_nests(nests=[{"pos": [20, 0, 0], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 1}])
        nests.insert(g, ok, REPO.pools)
        far = with_nests(nests=[{"pos": [21, 0, 0], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 1}])
        with self.assertRaisesRegex(nests.NestError, "21.0 blocks"):
            nests.insert(grid_of(22, 1, 1), far, REPO.pools)

    def test_unknown_nest_and_too_many(self):
        with self.assertRaisesRegex(nests.NestError, "not in pool"):
            nests.insert(grid_of(3, 1, 1), with_nests(nests=[{"pos": [1, 0, 0], "nest": "lotr_revival:x.y", "radius": 1}]),
                         REPO.pools)
        many = [{"pos": [1, 0, 0], "nest": "lotr_revival:gondor_watchtower.archer", "radius": 1}] * 17
        with self.assertRaisesRegex(nests.NestError, "17 nests"):
            nests.insert(grid_of(3, 1, 1), with_nests(nests=many), REPO.pools)
        guards = [{"pos": [1, 0, 0], "nest": "lotr_revival:gondor_watchtower.guard", "radius": 1}] * 13   # 13 x 2 = 26
        with self.assertRaisesRegex(nests.NestError, "26 NPCs"):
            nests.insert(grid_of(3, 1, 1), with_nests(nests=guards), REPO.pools)

    def test_extract_reads_back_what_build_wrote(self):
        g = grid_of(3, 2, 3)
        m = with_nests()
        nests.insert(g, m, REPO.pools)
        saved = vanilla.read(nbt.loads(nbt.dumps(vanilla.write(g)))[1])
        mgr, ns = nests.extract(saved)
        self.assertEqual(mgr, m["manager"])
        self.assertEqual(sorted(ns, key=str), sorted(m["nests"], key=str))


def temp_repo(tmp: Path) -> Repo:
    """A copy of everything the tool reads and writes."""
    r = Repo(tmp)
    shutil.copytree(REPO.tool, r.tool, ignore=shutil.ignore_patterns("work", "__pycache__", "blocks.json"))
    shutil.copytree(REPO.data, r.data)
    r.docs.parent.mkdir(parents=True)
    shutil.copy(REPO.docs, r.docs)
    shutil.copy(REPO.notice, r.notice)
    return r


class EmitTests(unittest.TestCase):
    def test_committed_outputs_match_a_fresh_build(self):
        self.assertEqual(pipeline.verify(REPO, log=lambda s: None), [])

    def test_verify_fails_after_a_hand_edit(self):
        with tempfile.TemporaryDirectory() as tmp:
            r = temp_repo(Path(tmp))
            self.assertEqual(pipeline.verify(r, log=lambda s: None), [])
            p = r.data / "worldgen" / "structure_set" / "gondor.json"
            p.write_text(p.read_text().replace('"spacing": 24', '"spacing": 30'))
            problems = pipeline.verify(r, log=lambda s: None)
            self.assertTrue(any("out of date" in x and "gondor.json" in x for x in problems), problems)
            stale = r.data / "worldgen" / "structure" / "leftover.json"
            stale.write_text("{}")
            self.assertTrue(any("stale" in x for x in pipeline.verify(r, log=lambda s: None)))

    def test_placeholder_template(self):
        g = vanilla.read(nbt.load_file(REPO.data / "structure" / "build" / "placeholder_watchtower.nbt"))
        self.assertEqual(g.size, (7, 11, 7))
        names = {s.name for s in g.blocks}
        self.assertIn("middle-earth:structure_manager", names)
        self.assertNotIn("minecraft:stone_bricks", names)
        self.assertEqual(sum(1 for s in g.blocks if s.name == "middle-earth:structure_nest"), 2)

    def test_salt_is_stable(self):
        self.assertEqual(emit.salt("lotr_revival:gondor"), emit.salt("lotr_revival:gondor"))
        self.assertNotEqual(emit.salt("lotr_revival:gondor"), emit.salt("lotr_revival:rohan"))

    def test_venue_tags_merge_middle_earth(self):
        tag = json.loads((REPO.data / "tags/worldgen/structure/event_venues/watchtower.json").read_text())
        self.assertIn("lotr_revival:placeholder_watchtower", tag["values"])
        self.assertIn({"id": "middle-earth:gondor_watch_tower", "required": False}, tag["values"])

    def test_build_refuses_candidates(self):
        with tempfile.TemporaryDirectory() as tmp:
            r = temp_repo(Path(tmp))
            p = r.builds / "placeholder_watchtower" / "build.json"
            m = json.loads(p.read_text())
            m.update(own_work=False, licence="CC-BY-4.0", status="candidate")
            p.write_text(json.dumps(m))
            with self.assertRaisesRegex(pipeline.BuildError, "only the maintainer"):
                pipeline.build_all(r, only="placeholder_watchtower", log=lambda s: None)
            files = pipeline.build_all(r, log=lambda s: None)        # build --all skips candidates
            self.assertFalse(any(k.endswith("placeholder_watchtower.nbt") for k in files))


class DocsTests(unittest.TestCase):
    def test_notice_block_replaced_between_markers_only(self):
        before = "Header\n\nSome text.\n"
        after = "\nTrailer kept.\n"
        text = before + docs.BEGIN + "\nold\n" + docs.END + after
        out = docs.replace_notice(text, docs.BEGIN + "\nnew\n" + docs.END)
        self.assertEqual(out, before + docs.BEGIN + "\nnew\n" + docs.END + after)

    def test_notice_appended_once(self):
        block = docs.notice_block([manifest()])
        once = docs.replace_notice("Header\n", block)
        self.assertEqual(docs.replace_notice(once, block), once)
        self.assertTrue(once.startswith("Header\n\n" + docs.BEGIN))
        self.assertIn(docs.EXCEPTION, block)

    def test_docs_twice_no_diff(self):
        with tempfile.TemporaryDirectory() as tmp:
            r = temp_repo(Path(tmp))
            ms = pipeline.load_manifests(r)
            pipeline.write_files(r.root, pipeline.docs_files(r, ms))
            first = (r.notice.read_bytes(), r.docs.read_bytes())
            pipeline.write_files(r.root, pipeline.docs_files(r, ms))
            self.assertEqual(first, (r.notice.read_bytes(), r.docs.read_bytes()))

    def test_standard_lines(self):
        m = manifest(changes=["removed 3 entities"])
        line = docs.standard_line(m)
        self.assertIn('"Test Build" by Jane Doe (https://example.org/p/test) is licensed under CC BY 4.0', line)
        self.assertIn("Modified for LOTR Revival: removed 3 entities.", line)
        self.assertIn("does not endorse", line)
        self.assertEqual(docs.standard_line(manifest(licence="CC0-1.0")),
                         '"Test Build" by Jane Doe (https://example.org/p/test), CC0.')
        self.assertEqual(docs.standard_line(manifest(attribution="Built by JD")), "Built by JD")

    def test_log_lists_rejected(self):
        md = docs.licence_log([manifest(status="rejected", notes="repost", licence="CC-BY-NC-4.0")])
        self.assertIn("rejected: repost", md)


if __name__ == "__main__":
    unittest.main()
