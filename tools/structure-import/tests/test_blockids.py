"""Block id lists and validation (spec "Palette remapping", Validation), and the Middle-earth id extractor."""
import json
import tempfile
import unittest
from pathlib import Path

from helpers import TOOL  # noqa: F401  (puts the tool on sys.path)

import extract_me_block_ids as ex  # noqa: E402
from sinr import blockids, pipeline  # noqa: E402
from sinr.paths import Repo  # noqa: E402

KNOWN = blockids.load_known(TOOL)


class IdListTest(unittest.TestCase):
    def test_lists_are_sorted_namespaced_ids(self):
        for name, ns in ((blockids.VANILLA_FILE, "minecraft:"), (blockids.ME_FILE, "middle-earth:")):
            data = json.loads((TOOL / name).read_text(encoding="utf-8"))
            self.assertEqual(data["ids"], sorted(set(data["ids"])), name)
            self.assertEqual(data["count"], len(data["ids"]))
            self.assertTrue(all(i.startswith(ns) for i in data["ids"]), name)
            self.assertEqual(sorted(data), ["count", "description", "ids", "source"] + (["version"] if ns == "minecraft:" else []))

    def test_known_ids(self):
        for i in ("minecraft:stone_bricks", "minecraft:air", "minecraft:structure_void", "minecraft:tuff_bricks",
                  "middle-earth:structure_manager", "middle-earth:orc_structure_manager", "middle-earth:structure_nest",
                  "middle-earth:limestone_bricks", "middle-earth:limestone_brick_stairs", "middle-earth:cobbled_limestone"):
            self.assertIn(i, KNOWN)
        # 1.21.1 has no pale oak (1.21.2+), and Middle-earth's set helpers shorten "_bricks" to "_brick" before suffixes.
        self.assertNotIn("minecraft:pale_oak_log", KNOWN)
        self.assertNotIn("middle-earth:limestone_bricks_stairs", KNOWN)
        # helpers that reuse a vanilla block of the same name do not register one of their own
        self.assertNotIn("middle-earth:stone_bricks", KNOWN)


class PaletteValidationTest(unittest.TestCase):
    def test_committed_palettes_and_templates_are_valid(self):
        self.assertEqual(blockids.palettes_problems(TOOL / "palettes", KNOWN), [])
        self.assertEqual(pipeline.id_problems(Repo.default()), [])

    def test_wrong_ids_are_listed(self):
        pal = {
            "id": "bad", "extends": "nope",
            "blocks": {"minecraft:stone_bricks": "middle-earth:limestone_brick"},
            "families": [{"from": {"base": "minecraft:stone_bricks", "stairs": "minecraft:stone_brick_stairs"},
                          "to": {"base": "middle-earth:limestone_bricks"}}],
            "random": {"minecraft:cobblestone": [["middle-earth:cobbled_limestone", 3], ["limestone", 1]]},
            "keep": ["minecraft:*_nonsense", "minecraft:torch", "minecraft:no_such_block", "*"],
            "valuable_fallback": "minecraft:stone_brick",
        }
        errs = blockids.palette_problems(pal, KNOWN, {"bad", "base"})
        text = "\n".join(errs)
        self.assertIn("extends unknown palette nope", text)
        self.assertIn("unknown block middle-earth:limestone_brick", text)
        self.assertIn("no target for stairs", text)
        self.assertIn("'limestone' is not a namespaced block id", text)
        self.assertIn("pattern minecraft:*_nonsense matches no known block", text)
        self.assertIn("unknown block minecraft:no_such_block", text)
        self.assertIn("valuable_fallback: unknown block minecraft:stone_brick", text)
        self.assertNotIn("minecraft:torch", text)
        self.assertEqual(len(errs), 7)

    def test_grid_problems(self):
        self.assertEqual(blockids.grid_problems(["minecraft:stone", "middle-earth:limestone"], KNOWN), [])
        self.assertEqual(blockids.grid_problems(["middle-earth:gondor_brick", "middle-earth:gondor_brick"], KNOWN),
                         ["unknown block middle-earth:gondor_brick"])

    def test_verify_reports_a_wrong_palette_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            tool = root / "tools/structure-import"
            (tool / "palettes").mkdir(parents=True)
            for name in (blockids.VANILLA_FILE, blockids.ME_FILE):
                (tool / name).write_bytes((TOOL / name).read_bytes())
            (tool / "palettes/base.json").write_text(json.dumps(
                {"id": "base", "keep": ["minecraft:torch"], "blocks": {"minecraft:stone": "middle-earth:limestone_brik"}}))
            errs = pipeline.id_problems(Repo(root))
            self.assertEqual(errs, ["palette base blocks[minecraft:stone]: unknown block middle-earth:limestone_brik"])
            (tool / blockids.ME_FILE).unlink()
            self.assertIn("missing", pipeline.id_problems(Repo(root))[0])


# A tiny made-up source tree in the shape the extractor reads (our own text, not Middle-earth's).
FAKE = {
    "block/utils/StoneBlockTypes.java": """
        public enum StoneBlockTypes { BASE_BLOCKS("", ""), COBBLED_BLOCKS("cobbled_", ""), BRICK_BLOCKS("", "_bricks"),
            CHISELED_BLOCKS("chiseled_", ""); }""",
    "block/utils/WoodBlockTypes.java": """
        public enum WoodBlockTypes { LOG_BLOCKS("", ""), PLANK_BLOCKS("", "_planks"), LEAVES("", "_leaves"); }""",
    "block/utils/BlockSetRegistration.java": """
        class BlockSetRegistration {
            public static R createRegularSet(String name, float h, boolean pillar) {
                Block base = getVanillaOrCreateNew(name, Block::new, s, g);
                name = name.replaceAll("_bricks", "_brick");
                Block slab = getVanillaOrCreateNew(name + "_slab", SlabBlock::new, s, g);
                // Block gone = getVanillaOrCreateNew(name + "_commented", X::new, s, g);
                Block stairs = getVanillaOrCreateNew(name + "_stairs", (x) -> new StairBlock(base.defaultBlockState(), x), s, g);
            }
            public static R createMainStoneSet(String name, float h) {
                Block base = getVanillaOrCreateNew(name, Block::new, s, g);
                Block rocks = getVanillaOrCreateNew(name + "_rocks", RocksBlock::new, s, g);
            }
            public static R createStoneChiseledSet(String name, float h) {
                Block b = getVanillaOrCreateNew("chiseled_" + name, RotatedPillarBlock::new, s, g);
            }
            public static R createWoodSet(String name, float h) {
                Block log = getVanillaOrCreateNew(name + "_log", RotatedPillarBlock::new, s, g);
            }
            public static R createPlanksSet(String name, float h) {
                Block base = getVanillaOrCreateNew(name, Block::new, s, g);
                name = name.replaceAll("_planks", "");
                Block bench = ModBlocks.registerWoodBlock(name + "_bench", WoodBenchBlock::new, s, false);
            }
        }""",
    "block/registration/StoneBlockSets.java": """
        class StoneBlockSets {
            public static StoneBlockSetBuilder GREY = registerStoneSet(new StoneBlockSetBuilder("greystone",
                    1f, 2f, MapColor.STONE, NoteBlockInstrument.BASEDRUM, SoundType.STONE, true, false)
                    .addToSet(StoneBlockTypes.BASE_BLOCKS).addToSet(StoneBlockTypes.BRICK_BLOCKS)
                    .addToSet(StoneBlockTypes.CHISELED_BLOCKS));
            public static StoneBlockSetBuilder DIRTY = registerStoneSet(new StoneBlockSetBuilder("dirt",
                    1f, 2f, MapColor.STONE, NoteBlockInstrument.BASEDRUM, SoundType.STONE, false, true)
                    .addToSet(StoneBlockTypes.COBBLED_BLOCKS));
            private static StoneBlockSetBuilder registerStoneSet(StoneBlockSetBuilder set) {
                set.existingList.forEach((t) -> {
                    switch (t) {
                        case BASE_BLOCKS -> set.baseBlocks = BlockSetRegistration.createMainStoneSet(set.setName, 1f);
                        case COBBLED_BLOCKS -> {
                            set.a = BlockSetRegistration.createRegularSet(t.getPrefix() + set.setName + t.getSuffix(), 1f, false);
                            if(set.hasMossy) set.b = BlockSetRegistration.createRegularSet("mossy_" + t.getPrefix() + set.setName + t.getSuffix(), 1f, false);
                        }
                        case BRICK_BLOCKS -> {
                            set.c = BlockSetRegistration.createRegularSet(t.getPrefix() + set.setName + t.getSuffix(), 1f, false);
                            if(set.hasMossy) set.d = BlockSetRegistration.createRegularSet("mossy_" + t.getPrefix() + set.setName + t.getSuffix(), 1f, false);
                            if(set.hasCracked) set.e = BlockSetRegistration.createRegularSet("cracked_" + t.getPrefix() + set.setName + t.getSuffix(), 1f, false);
                        }
                        case CHISELED_BLOCKS -> {
                            if (set.existingList.contains(StoneBlockTypes.BRICK_BLOCKS)) set.f = BlockSetRegistration.createStoneChiseledSet(set.setName + "_bricks", 1f);
                            if (set.existingList.contains(StoneBlockTypes.COBBLED_BLOCKS)) set.g = BlockSetRegistration.createStoneChiseledSet("cobbled_" + set.setName, 1f);
                        }
                    }
                });
                return set;
            }
        }""",
    "block/registration/WoodBlockSets.java": """
        class WoodBlockSets {
            public static WoodBlockSetBuilder ELM = registerWoodSet(new WoodBlockSetBuilder("elm", 1f, 2f, MapColor.WOOD,
                    NoteBlockInstrument.BASS, SoundType.WOOD, null).addToSet(WoodBlockTypes.LOG_BLOCKS)
                    .addToSet(WoodBlockTypes.PLANK_BLOCKS).addToSet(WoodBlockTypes.LEAVES));
            public static WoodBlockSetBuilder OAK = registerWoodSet(new WoodBlockSetBuilder("oak", 1f, 2f, MapColor.WOOD,
                    NoteBlockInstrument.BASS, SoundType.WOOD, null).vanilla(true).addToSet(WoodBlockTypes.LOG_BLOCKS)
                    .addToSet(WoodBlockTypes.PLANK_BLOCKS));
            private static WoodBlockSetBuilder registerWoodSet(WoodBlockSetBuilder set) {
                set.existingList.forEach((w) -> {
                    switch (w) {
                        case LOG_BLOCKS -> set.logs = set.setName.equals("odd") ? createOddSet(w.getPrefix() + set.setName, 1f)
                                : BlockSetRegistration.createWoodSet(w.getPrefix() + set.setName + w.getSuffix(), 1f);
                        case PLANK_BLOCKS -> set.planks = BlockSetRegistration.createPlanksSet(w.getPrefix() + set.setName + w.getSuffix(), 1f);
                        case LEAVES -> {
                            if (set.setName.equals("mallorn")) {
                                set.leaves = getVanillaOrCreateNew(w.getPrefix() + set.setName + w.getSuffix(), X::new, s, g);
                            } else {
                                set.leaves = getVanillaOrCreateNew(w.getPrefix() + set.setName + w.getSuffix(), Y::new, s, g);
                            }
                        }
                    }
                });
                return set;
            }
            private static R createOddSet(String name, float h) {
                Block log = getVanillaOrCreateNew(name + "_log", RotatedPillarBlock::new, s, g);
            }
        }""",
    "block/registration/GenericBlockSets.java": """
        class GenericBlockSets {
            public static GenericBlockSetBuilder DAUB = registerBlockSet(new GenericBlockSetBuilder("daub", 1f, 2f,
                    MapColor.SAND, NoteBlockInstrument.BASS, SoundType.WOOD, false, List.of()));
            private static GenericBlockSetBuilder registerBlockSet(GenericBlockSetBuilder set){
                set.blockSet = BlockSetRegistration.createRegularSet(set.setName , 1f, false);
                return set;
            }
        }""",
    "block/registration/OreRockSets.java": """
        class OreRockSets {
            public static OreRockSet GREY = registerOreSet("greystone_", 2f, Arrays.asList(ORES.TIN_ORE, ORES.COAL_ORE), null);
            public static OreRockSet registerOreSet(String rockName, float mult, List<ORES> ores, Block origin) {
                if(ores.contains(ORES.COAL_ORE)){
                    coal = ModNatureBlocks.registerBlock(rockName + "coal_ore", Block::new, s, true);
                }
                if(ores.contains(ORES.TIN_ORE)) {
                    tin = ModNatureBlocks.registerBlock(
                            rockName + "tin_ore", Block::new, s, true);
                }
            }
        }""",
    "block/registration/ModBlocks.java": """
        class ModBlocks {
            public static final Block LAMP = registerMiscBlock("crystal_lamp", Block::new, s, false);
            /* public static final Block OLD = registerMiscBlock("removed_block", Block::new, s, false); */
            public static final Block DIRT_SLAB = registerMiscBlock("dirt_slab", SlabBlock::new, s, true);
        }""",
    "block/registration/ModDecorativeBlocks.java": """
        class ModDecorativeBlocks {
            public static final Block M = registerBlock("structure_manager", StructureManagerBlock::new, s);
            public static final Block P = registerPottedBlock("potted_elm_sapling", () -> null, s);
        }""",
    "block/registration/ModNatureBlocks.java": """
        class ModNatureBlocks {
            public static final Block S = registerSimpleSapling("elm_sapling", null);
            private static Block pale() { String name = "odd_sapling"; return null; }
        }""",
    "item/ResourceItemsME.java": """
        class ResourceItemsME { public static final Block REEDS = ModBlocks.registerBlock("reeds", Block::new, s, true); }""",
}


class ExtractorTest(unittest.TestCase):
    def run_fake(self, files=FAKE):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "middle-earth" / ex.JAVA_PKG
            for rel, text in files.items():
                p = root / rel
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text(text, encoding="utf-8")
            vanilla = {"oak_log", "oak_planks", "dirt_slab", "cobbled_dirt_rocks"}
            return [i.split(":", 1)[1] for i in ex.Extractor(ex.java_root(Path(tmp)), vanilla).run()]

    def test_fake_source(self):
        ids = self.run_fake()
        self.assertEqual(ids, sorted([
            # literals (the commented-out one is ignored; a literal is registered even if vanilla has the name)
            "crystal_lamp", "dirt_slab", "structure_manager", "potted_elm_sapling", "elm_sapling", "odd_sapling", "reeds",
            # stone set "greystone": base, bricks with mossy (no cracked), chiseled bricks only (no cobbled set)
            "greystone", "greystone_rocks",
            "greystone_bricks", "greystone_brick_slab", "greystone_brick_stairs",
            "mossy_greystone_bricks", "mossy_greystone_brick_slab", "mossy_greystone_brick_stairs",
            "chiseled_greystone_bricks",
            # stone set "dirt": cobbled, no mossy
            "cobbled_dirt", "cobbled_dirt_slab", "cobbled_dirt_stairs",
            # wood: elm (log helper, planks with the "_planks" rewrite, leaves); oak is vanilla except the bench
            "elm_log", "elm_planks", "elm_bench", "elm_leaves", "oak_bench",
            # generic set
            "daub", "daub_slab", "daub_stairs",
            # ores
            "greystone_coal_ore", "greystone_tin_ore",
        ]))

    def test_unknown_expression_stops(self):
        bad = dict(FAKE)
        bad["block/registration/GenericBlockSets.java"] = FAKE["block/registration/GenericBlockSets.java"].replace(
            'registerBlockSet(new GenericBlockSetBuilder("daub"', 'registerBlockSet(new GenericBlockSetBuilder(PREFIX + "daub"')
        with self.assertRaises(ex.ExtractError):
            self.run_fake(bad)

    def test_comments_and_strings(self):
        self.assertEqual(ex.strip_comments('a("x//y") // c\nb /* d */ e'), 'a("x//y") \nb  e')
        self.assertEqual(ex.split_args('"a, b", f(1, 2), x'), ['"a, b"', 'f(1, 2)', 'x'])
        self.assertEqual(ex.eval_name('"a" + name + "_b"', {"name": "n"}), "an_b")


if __name__ == "__main__":
    unittest.main()
