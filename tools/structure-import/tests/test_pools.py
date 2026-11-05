"""Our resident pools, nest names and region palettes (spec "Residents", ticket 8; "Regions")."""
import json
import unittest

from helpers import TOOL  # noqa: F401  (puts the tool on sys.path)

from sinr import limits, nests, remap  # noqa: E402
from sinr.paths import Repo  # noqa: E402

REPO = Repo.default()
NPC_IDS = json.loads((TOOL / "me_npc_ids.json").read_text(encoding="utf-8"))
LANG = json.loads((REPO.root / "addon/src/main/resources/assets/lotr_revival/lang/en_us.json").read_text(encoding="utf-8"))
REGIONS = json.loads((TOOL / "regions.json").read_text(encoding="utf-8"))["regions"]
POOL_FILES = sorted(REPO.pools.glob("*.json"))
ENTITY_TYPES = {"middle-earth:npc", "middle-earth:warg"}


def pools():
    return [(p, json.loads(p.read_text(encoding="utf-8"))) for p in POOL_FILES]


class PoolTest(unittest.TestCase):
    def test_npc_id_list(self):
        self.assertIn("middle-earth:gondor.soldier", NPC_IDS["npc_types"])
        self.assertIn("middle-earth:longbeards.erebor", NPC_IDS["factions"])
        for npc, faction in NPC_IDS["npc_types"].items():
            self.assertIn(faction, NPC_IDS["factions"], npc)

    def test_every_pool_is_well_formed(self):
        self.assertGreaterEqual(len(POOL_FILES), 3)
        for path, pool in pools():
            with self.subTest(pool=path.name):
                self.assertEqual(pool["id"], f"lotr_revival:{path.stem}")
                ids = [n["id"] for n in pool["spawn_nest"]]
                self.assertEqual(len(ids), len(set(ids)), "duplicate nest ids")
                self.assertTrue(ids)
                for n in pool["spawn_nest"]:
                    self.assertTrue(n["id"].startswith(pool["id"] + "."), n["id"])
                    self.assertEqual(n["respawn_tick_delay"], 72000)
                    self.assertGreater(n["bed_radius"], 0)
                    self.assertTrue(n["npc_pool"])
                    for e in n["npc_pool"]:
                        self.assertIn(e["entity_type"], ENTITY_TYPES)
                        self.assertGreater(e["weight"], 0)
                        self.assertGreaterEqual(e["amount"], 1)
                        self.assertGreaterEqual(e.get("max_amount", e["amount"]), e["amount"])
                        if e["entity_type"] == "middle-earth:npc":
                            npc = e["npc_identifier"]
                            self.assertIn(npc, NPC_IDS["npc_types"], f"{n['id']}: unknown NPC type")
                            self.assertEqual(e["faction_key"], NPC_IDS["npc_types"][npc], f"{n['id']}: faction of {npc}")
                        else:
                            self.assertNotIn("npc_identifier", e)
                            self.assertNotIn("faction_key", e)

    def test_one_build_using_every_nest_stays_under_the_npc_limit(self):
        for path, pool in pools():
            total = sum(nests.nest_max(n) for n in pool["spawn_nest"])
            self.assertLessEqual(total, limits.NPCS_MAX, path.name)

    def test_every_faction_region_has_pools_of_its_faction(self):
        factions = {}
        for _, pool in pools():
            for n in pool["spawn_nest"]:
                for e in n["npc_pool"]:
                    if "faction_key" in e:
                        factions.setdefault(pool["id"], set()).add(e["faction_key"])
        for r in REGIONS:
            if r["faction"] is None:
                continue
            with self.subTest(region=r["id"]):
                mine = [p for p, f in factions.items() if f == {r["faction"]}]
                self.assertTrue(mine, f"no pool for {r['faction']}")
                self.assertTrue(all(p.startswith(f"lotr_revival:{r['id']}_") for p in mine), mine)

    def test_nest_names(self):
        nest_ids = {n["id"] for _, pool in pools() for n in pool["spawn_nest"]}
        keys = {f"structure_nest.lotr_revival.{i.split(':', 1)[1]}" for i in nest_ids}
        lang_keys = {k for k in LANG if k.startswith("structure_nest.")}
        self.assertEqual(sorted(keys - lang_keys), [], "nests without a lang entry")
        self.assertEqual(sorted(lang_keys - keys), [], "lang entries for nests that no pool has")
        for k in keys:
            self.assertTrue(LANG[k].strip(), k)

    def test_manifest_nests_exist_in_their_pools(self):
        for path in sorted(REPO.builds.glob("*/build.json")):
            m = json.loads(path.read_text(encoding="utf-8"))
            if not m.get("manager"):
                continue
            pool = nests.load_pool(REPO.pools, m["manager"]["pool"])
            if pool is None:
                continue
            have = {n["id"] for n in pool["spawn_nest"]}
            for n in m.get("nests", []):
                self.assertIn(n["nest"], have, f"{path.parent.name}: {n['nest']}")


class RegionPaletteTest(unittest.TestCase):
    def test_every_region_palette_loads_and_extends_base(self):
        for r in REGIONS:
            with self.subTest(region=r["id"]):
                pal = remap.Palette.load(r["palette"], REPO.palettes)
                self.assertEqual(pal.chain[-1]["id"], "base")
                self.assertIsNotNone(pal.valuable_fallback)

    def test_faction_palettes_swap_the_common_stone(self):
        for r in REGIONS:
            pal = remap.Palette.load(r["palette"], REPO.palettes)
            for block in ("minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                          "minecraft:stone_brick_wall", "minecraft:cobblestone", "minecraft:cracked_stone_bricks",
                          "minecraft:mossy_stone_bricks", "minecraft:mossy_cobblestone", "minecraft:cobblestone_stairs",
                          "minecraft:cobblestone_slab", "minecraft:cobblestone_wall"):
                self.assertIsNotNone(pal.rule(block), f"{r['palette']} has no rule for {block}")


if __name__ == "__main__":
    unittest.main()
