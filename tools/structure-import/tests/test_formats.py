import gzip
import importlib.util
import random
import unittest

from helpers import FIXTURES, TOOL, S, grid_of
from sinr import formats, nbt, versions
from sinr.formats import litematic, sponge, vanilla
from sinr.grid import Grid

_spec = importlib.util.spec_from_file_location("make_placeholder", TOOL / "placeholder" / "make_placeholder.py")
make_placeholder = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(make_placeholder)


class FormatTests(unittest.TestCase):
    def test_every_format_gives_the_same_grid(self):
        expected = make_placeholder.fixture().canonical()
        seen = {}
        for name in ("fixture.v2.schem", "fixture.v3.schem", "fixture.litematic", "fixture.nbt"):
            fmt, g = formats.read_file(FIXTURES / name)
            seen[fmt] = g
            self.assertEqual(g.canonical(), expected, name)
        self.assertEqual(set(seen), {"sponge_v2", "sponge_v3", "litematic", "vanilla"})

    def test_committed_fixtures_are_current(self):
        f = make_placeholder.fixture()
        raw = lambda root: nbt.dumps(root, compress=False)
        self.assertEqual(gzip.decompress((FIXTURES / "fixture.v3.schem").read_bytes()), raw(sponge.write(f, 3)))
        self.assertEqual(gzip.decompress((FIXTURES / "fixture.litematic").read_bytes()),
                         raw(litematic.write(make_placeholder.split_for_litematic(f), 3955)))

    def test_sponge_index_order_on_asymmetric_box(self):
        g = Grid(3, 4, 5, data_version=3955)
        g.set(2, 0, 0, S("stone"))   # x
        g.set(0, 3, 0, S("dirt"))    # y
        g.set(0, 0, 4, S("sand"))    # z
        root = sponge.write(g, 2)
        pal = {int(v): k for k, v in root["Palette"].items()}
        ids = sponge.varints_decode(root["BlockData"], 60)
        self.assertEqual(pal[ids[2 + 0 * 3 + 0 * 15]], "minecraft:stone")
        self.assertEqual(pal[ids[0 + 4 * 3 + 0 * 15]], "minecraft:sand")      # z * Width
        self.assertEqual(pal[ids[0 + 0 * 3 + 3 * 15]], "minecraft:dirt")      # y * Width * Length
        self.assertEqual(sponge.read(root).canonical(), g.canonical())

    def test_varints_over_127(self):
        vals = [0, 1, 127, 128, 300, 16384, 70000]
        self.assertEqual(sponge.varints_decode(sponge.varints_encode(vals), len(vals)), vals)

    def test_litematic_bits_span_longs(self):
        rnd = random.Random(7)
        for size in (2, 3, 17, 300):
            bits = litematic.bits_for(size)
            self.assertEqual(bits, {2: 2, 3: 2, 17: 5, 300: 9}[size])
            vals = [rnd.randrange(size) for _ in range(257)]
            self.assertEqual(litematic.unpack(litematic.pack(vals, bits), bits, len(vals)), vals)

    def test_litematic_palette_of_300(self):
        g = Grid(10, 3, 10, data_version=3955)
        for i, p in enumerate(g.positions()):
            g.set(*p, S(f"test:block_{i}"))
        root = litematic.write([("r", (0, 0, 0), (10, 3, 10), g)], 3955)
        self.assertEqual(len(root["Regions"]["r"]["BlockStatePalette"]), 301)
        self.assertEqual(litematic.read(root).canonical(), g.canonical())

    def test_litematic_negative_region(self):
        g = grid_of(2, 2, 3, "minecraft:stone")
        g.set(1, 1, 2, S("dirt"))
        root = litematic.write([("neg", (5, 5, 5), (-2, -2, -3), g)], 3955)
        back = litematic.read(root)
        self.assertEqual(back.size, (2, 2, 3))
        self.assertEqual(str(back.get(1, 1, 2)), "minecraft:dirt")

    def test_vanilla_void_round_trip(self):
        g = grid_of(2, 1, 1, "minecraft:stone")
        g.set(1, 0, 0, S("structure_void"))
        root = vanilla.write(g)
        self.assertEqual(len(root["blocks"]), 1)
        self.assertEqual(str(vanilla.read(root).get(1, 0, 0)), "minecraft:structure_void")

    def test_refused_formats(self):
        mcedit = nbt.dumps({"Width": nbt.Short(1), "Height": nbt.Short(1), "Length": nbt.Short(1),
                            "Materials": "Alpha", "Blocks": nbt.ByteArray([1]), "Data": nbt.ByteArray([0])})
        with self.assertRaisesRegex(formats.FormatError, "1.13"):
            formats.read_bytes(mcedit, "old.schematic")
        with self.assertRaisesRegex(formats.FormatError, "Bedrock"):
            formats.read_bytes(b"\x0a\x00\x00\x00", "thing.mcstructure")


class VersionTests(unittest.TestCase):
    def test_versions(self):
        self.assertEqual(versions.classify(3955), "direct")
        self.assertEqual(versions.classify(3953), "direct")
        self.assertEqual(versions.classify(3700), "upgrade")
        for dv in (1400, 4100):
            with self.assertRaises(versions.VersionError):
                versions.classify(dv)


if __name__ == "__main__":
    unittest.main()
