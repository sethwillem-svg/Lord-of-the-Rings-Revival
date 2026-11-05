"""Remap, strip, fit and limits."""
import unittest

from helpers import FIXTURES, S, grid_of, manifest
from sinr import fit, limits, nbt, remap, strip
from sinr.grid import Entity

DUMP = remap.load_dump(FIXTURES / "blocks.json")
GONDORISH = {
    "id": "t",
    "blocks": {"minecraft:sand": "minecraft:gravel"},
    "families": [{"from": {"base": "minecraft:stone_bricks", "stairs": "minecraft:stone_brick_stairs",
                           "slab": "minecraft:stone_brick_slab", "wall": "minecraft:stone_brick_wall"},
                  "to": {"base": "middle-earth:limestone_bricks", "stairs": "middle-earth:limestone_brick_stairs",
                         "slab": "middle-earth:limestone_brick_slab", "wall": "middle-earth:limestone_brick_wall"}}],
    "random": {"minecraft:cobblestone": [["middle-earth:cobbled_limestone", 3], ["minecraft:mossy_cobblestone", 1]]},
    "keep": ["minecraft:oak_*", "minecraft:stone"],
    "valuable_fallback": "minecraft:stone_bricks",
}


class RemapTests(unittest.TestCase):
    def pal(self):
        return remap.Palette.of(GONDORISH)

    def test_exact_rule(self):
        g = grid_of(1, 1, 1, "minecraft:sand")
        changes, _ = remap.remap(g, self.pal(), DUMP, "b")
        self.assertEqual(str(g.get(0, 0, 0)), "minecraft:gravel")
        self.assertIn("other vanilla", changes[0])

    def test_family_carries_properties(self):
        g = grid_of(2, 1, 1)
        st = {"facing": "west", "half": "top", "shape": "outer_left", "waterlogged": "true"}
        g.set(0, 0, 0, S("stone_brick_stairs", st))
        g.set(1, 0, 0, S("stone_brick_slab", {"type": "double", "waterlogged": "true"}))
        _, warns = remap.remap(g, self.pal(), DUMP, "b")
        self.assertEqual(g.get(0, 0, 0), S("middle-earth:limestone_brick_stairs", st))
        self.assertEqual(g.get(1, 0, 0).prop_dict["type"], "double")
        self.assertEqual(warns, [])

    def test_random_is_deterministic(self):
        def run(bid):
            g = grid_of(6, 1, 6, "minecraft:cobblestone")
            remap.remap(g, self.pal(), DUMP, bid)
            return [str(s) for s in g.blocks]
        a = run("x")
        self.assertEqual(a, run("x"))
        self.assertEqual(set(a), {"middle-earth:cobbled_limestone", "minecraft:mossy_cobblestone"})

    def test_unmatched_stops_and_lists(self):
        g = grid_of(2, 1, 1, "minecraft:diorite")
        g.set(1, 0, 0, S("minecraft:granite"))
        with self.assertRaises(remap.RemapError) as cm:
            remap.remap(g, self.pal(), DUMP, "b")
        self.assertEqual(cm.exception.unmatched, ["minecraft:diorite", "minecraft:granite"])

    def test_missing_property_falls_back_with_one_warning(self):
        pal = remap.Palette.of({"id": "p", "blocks": {"minecraft:oak_stairs": "middle-earth:limestone_bricks"}})
        g = grid_of(3, 1, 1)
        for x in range(3):
            g.set(x, 0, 0, S("oak_stairs", {"facing": "east", "half": "bottom", "shape": "straight", "waterlogged": "false"}))
        _, warns = remap.remap(g, pal, DUMP, "b")
        self.assertEqual(len(warns), 1)
        self.assertEqual(str(g.get(0, 0, 0)), "middle-earth:limestone_bricks")

    def test_target_default_used(self):
        pal = remap.Palette.of({"id": "p", "blocks": {"minecraft:stone": "middle-earth:limestone_brick_slab"}})
        g = grid_of(1, 1, 1, "minecraft:stone")
        remap.remap(g, pal, DUMP, "b")
        self.assertEqual(g.get(0, 0, 0).prop_dict, {"type": "bottom", "waterlogged": "false"})

    def test_validate_against_dump(self):
        g = grid_of(1, 1, 1, "minecraft:nonexistent")
        self.assertEqual(remap.validate(g, DUMP), ["unknown block minecraft:nonexistent"])

    def test_palette_files_load(self):
        p = remap.Palette.load("gondor")
        self.assertEqual(p.rule("minecraft:stone_bricks"), ("family", "middle-earth:limestone_bricks"))
        self.assertEqual(p.rule("minecraft:oak_door")[0], "keep")
        self.assertEqual(remap.Palette.load("neutral").rule("minecraft:anything")[0], "keep")


class StripTests(unittest.TestCase):
    def run_strip(self, g, **m):
        return strip.strip(g, manifest(**m), remap.Palette.of(GONDORISH))

    def test_to_air_rows(self):
        names = sorted(strip.TO_AIR)
        g = grid_of(len(names), 1, 1)
        for i, n in enumerate(names):
            g.set(i, 0, 0, S(n))
        changes, _ = self.run_strip(g)
        self.assertTrue(all(s.name == "minecraft:air" for s in g.blocks))
        self.assertIn(f"removed {len(names)} blocks", changes[0])
        self.assertIn("command_block x1", changes[0])

    def test_stop_rows(self):
        for n in sorted(strip.STOP):
            g = grid_of(1, 1, 1, "minecraft:" + n)
            with self.assertRaises(strip.StripError):
                self.run_strip(g)
        pal = remap.Palette.of({"id": "p", "blocks": {"minecraft:bedrock": "minecraft:stone"}})
        strip.strip(grid_of(1, 1, 1, "minecraft:bedrock"), manifest(), pal)   # mapped explicitly: fine

    def test_valuables(self):
        g = grid_of(3, 1, 1, "minecraft:beacon")
        g.set(1, 0, 0, S("deepslate_diamond_ore"))
        g.set(2, 0, 0, S("gold_block"))
        changes, _ = self.run_strip(g)
        self.assertTrue(all(s.name == "minecraft:stone_bricks" for s in g.blocks))
        self.assertEqual(changes, ["replaced 3 valuable blocks with stone_bricks"])

    def test_shulker_to_empty_barrel(self):
        g = grid_of(1, 1, 1)
        g.set(0, 0, 0, S("red_shulker_box", {"facing": "up"}), {"id": "minecraft:shulker_box", "Items": [{"id": "x"}]})
        changes, _ = self.run_strip(g)
        self.assertEqual(g.get(0, 0, 0).name, "minecraft:barrel")
        self.assertEqual(g.block_entities[(0, 0, 0)], {"id": "minecraft:barrel"})
        self.assertIn("replaced 1 shulker box with empty barrels", changes)

    def test_containers_and_block_entity_keys(self):
        g = grid_of(2, 1, 1)
        g.set(0, 0, 0, S("chest"), {"id": "minecraft:chest", "Items": [{"id": "minecraft:diamond"}], "CustomName": "x",
                                    "Lock": "k"})
        g.set(1, 0, 0, S("barrel"), {"id": "minecraft:barrel", "LootTable": "minecraft:chests/x", "LootTableSeed": nbt.Long(3)})
        changes, _ = self.run_strip(g)
        self.assertEqual(g.block_entities[(0, 0, 0)], {"id": "minecraft:chest"})
        self.assertEqual(g.block_entities[(1, 0, 0)], {"id": "minecraft:barrel"})
        self.assertIn("emptied 2 containers", changes)

    def test_signs(self):
        tag = lambda: {"id": "minecraft:sign", "front_text": {"messages": ['"join my server"', '""', '""', '""']}}
        g = grid_of(1, 1, 1)
        g.set(0, 0, 0, S("oak_sign"), tag())
        changes, _ = self.run_strip(g)
        self.assertNotIn("front_text", g.block_entities[(0, 0, 0)])
        self.assertIn("cleared the text of 1 sign", changes)
        g.set(0, 0, 0, S("oak_sign"), tag())
        self.run_strip(g, keep_sign_text=True)
        self.assertIn("front_text", g.block_entities[(0, 0, 0)])

    def test_leaves_persistent(self):
        g = grid_of(1, 1, 1)
        g.set(0, 0, 0, S("oak_leaves", {"distance": "3", "persistent": "false", "waterlogged": "false"}))
        self.run_strip(g)
        self.assertEqual(g.get(0, 0, 0).prop_dict["persistent"], "true")

    def test_heads(self):
        g = grid_of(1, 1, 1, "minecraft:player_head")
        self.run_strip(g)
        self.assertEqual(g.get(0, 0, 0).name, "minecraft:air")

    def test_entities(self):
        g = grid_of(1, 1, 1)
        g.entities = [Entity((0.5, 0, 0.5), {"id": "minecraft:zombie"}),
                      Entity((0.5, 0, 0.5), {"id": "minecraft:item_frame", "Item": {"id": "minecraft:diamond"}})]
        changes, _ = self.run_strip(g, keep_entities=["item_frame"])
        self.assertEqual([e.type for e in g.entities], ["minecraft:item_frame"])
        self.assertNotIn("Item", g.entities[0].nbt)
        self.assertIn("removed 1 entity", changes)
        g.entities = [Entity((0, 0, 0), {"id": "minecraft:painting"}) for _ in range(33)]
        with self.assertRaises(strip.StripError):
            self.run_strip(g, keep_entities=["painting"])

    def test_redstone_reported(self):
        g = grid_of(1, 1, 1, "minecraft:lever")
        _, report = self.run_strip(g)
        self.assertIn("lever x1", report[0])


class FitTests(unittest.TestCase):
    def plot(self):
        """5 x 6 x 5: 2 layers of grass/dirt, a 3 x 3 room with walls on top (enclosed air in the middle)."""
        g = grid_of(5, 6, 5)
        for x in range(5):
            for z in range(5):
                g.set(x, 0, z, S("dirt"))
                g.set(x, 1, z, S("grass_block", {"snowy": "false"}))
        for x in range(1, 4):
            for z in range(1, 4):
                for y in range(2, 5):
                    edge = x in (1, 3) or z in (1, 3) or y in (2, 4)
                    g.set(x, y, z, S("stone_bricks") if edge else S("air"))
        return g

    def test_trim(self):
        g = grid_of(5, 5, 5)
        g.set(1, 2, 3, S("stone"))
        g.set(2, 3, 3, S("stone"))
        t = fit.trim(g)
        self.assertEqual(t.size, (2, 2, 1))

    def test_ground_detection_two_layers(self):
        self.assertEqual(fit.detect_ground(self.plot()), 1)

    def test_outside_air_void_room_stays(self):
        g, gy, changes = fit.fit(self.plot(), None)
        self.assertEqual(gy, 1)
        self.assertEqual(g.get(2, 3, 2).name, "minecraft:air")              # inside the room
        self.assertEqual(g.get(0, 3, 0).name, "minecraft:structure_void")   # outside
        self.assertEqual(g.get(0, 0, 0).name, "minecraft:structure_void")   # natural ground
        self.assertEqual(g.get(1, 2, 1).name, "minecraft:stone_bricks")
        self.assertTrue(any("outside air" in c for c in changes))

    def test_ground_y_from_manifest(self):
        g, gy, _ = fit.fit(self.plot(), 0)
        self.assertEqual(gy, 0)
        self.assertEqual(g.get(0, 1, 0).name, "minecraft:grass_block")


class LimitTests(unittest.TestCase):
    def test_footprint_and_height(self):
        self.assertEqual(limits.check_grid(grid_of(96, 64, 1))[0], [])
        self.assertTrue(limits.check_grid(grid_of(97, 1, 1))[0])
        self.assertTrue(limits.check_grid(grid_of(1, 65, 1))[0])
        self.assertTrue(limits.check_grid(grid_of(49, 1, 1))[1])
        self.assertFalse(limits.check_grid(grid_of(48, 1, 1))[1])

    def test_counts(self):
        self.assertEqual(limits.check_grid(grid_of(50, 24, 50, "minecraft:stone"))[0], [])   # 60,000 blocks
        g = grid_of(50, 25, 50, "minecraft:stone")
        for i in range(2499):
            g.blocks[i] = g.blocks[i].of("minecraft:air")
        self.assertEqual(g.non_air_count(), 60_001)
        self.assertTrue(any("non-air" in e for e in limits.check_grid(g)[0]))
        g = grid_of(16, 1, 16)
        for p in g.positions():
            g.block_entities[p] = {"id": "minecraft:chest"}
        self.assertEqual(limits.check_grid(g)[0], [])
        g2 = grid_of(257, 1, 1)
        for p in g2.positions():
            g2.block_entities[p] = {"id": "minecraft:chest"}
        self.assertTrue(limits.check_grid(g2)[0])
        g3 = grid_of(1, 1, 1)
        g3.entities = [Entity((0, 0, 0), {"id": "minecraft:painting"})] * 32
        self.assertEqual(limits.check_grid(g3)[0], [])
        g3.entities = g3.entities + [g3.entities[0]]
        self.assertTrue(limits.check_grid(g3)[0])

    def test_files(self):
        self.assertEqual(limits.check_output(limits.OUTPUT_MAX), [])
        self.assertTrue(limits.check_output(limits.OUTPUT_MAX + 1))
        self.assertEqual(limits.check_source(limits.SOURCE_MAX), [])
        self.assertTrue(limits.check_source(limits.SOURCE_MAX + 1))


if __name__ == "__main__":
    unittest.main()
