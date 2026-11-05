import unittest

import helpers  # noqa: F401  (sys.path)
from sinr import nbt


def sample():
    return {
        "b": nbt.Byte(-5), "s": nbt.Short(-30000), "i": nbt.Int(2_000_000_000), "l": nbt.Long(-(1 << 62)),
        "f": nbt.Float(1.5), "d": nbt.Double(-2.25), "ba": nbt.ByteArray([1, -2, 127, -128]),
        "str": "héllo \u0000 wörld \U0001F600", "list": nbt.TagList([nbt.Int(1), nbt.Int(2)], nbt.INT),
        "empty": nbt.TagList([], nbt.END), "nested": {"x": {"y": nbt.TagList([{"z": "deep"}], nbt.COMPOUND)}},
        "ia": nbt.IntArray([1, -1, 1 << 30]), "la": nbt.LongArray([1, -1, (1 << 63) - 1, -(1 << 63)]),
    }


class NbtTests(unittest.TestCase):
    def test_round_trip_every_tag_gzip(self):
        data = nbt.dumps(sample(), name="root")
        self.assertEqual(data[:2], b"\x1f\x8b")
        name, back = nbt.loads(data)
        self.assertEqual(name, "root")
        self.assertEqual(back, sample())
        for k, v in sample().items():
            self.assertIs(type(back[k]), type(v) if not isinstance(v, dict) else dict, k)

    def test_round_trip_uncompressed(self):
        raw = nbt.dumps(sample(), compress=False)
        self.assertEqual(raw[0], 10)
        self.assertEqual(nbt.loads(raw)[1], sample())
        self.assertEqual(nbt.dumps(nbt.loads(raw)[1], compress=False), raw)

    def test_deterministic_gzip(self):
        self.assertEqual(nbt.dumps(sample()), nbt.dumps(sample()))

    def test_empty_list_keeps_type(self):
        back = nbt.loads(nbt.dumps({"l": nbt.TagList([], nbt.COMPOUND)}))[1]
        self.assertEqual(back["l"].elem, nbt.COMPOUND)

    def test_modified_utf8(self):
        self.assertEqual(nbt.encode_mutf8("\u0000"), b"\xc0\x80")
        self.assertEqual(nbt.encode_mutf8("A"), b"A")
        self.assertEqual(nbt.encode_mutf8("\U0001F600"), b"\xed\xa0\xbd\xed\xb8\x80")   # surrogate pair, 3 bytes each
        for s in ("", "plain", "\u0000x\u0000", "é€", "\U0001F600 ok", "￿"):
            self.assertEqual(nbt.decode_mutf8(nbt.encode_mutf8(s)), s)

    def test_plain_python_values(self):
        back = nbt.loads(nbt.dumps({"i": 3, "d": 0.5, "t": True, "l": [1, 2]}))[1]
        self.assertIsInstance(back["i"], nbt.Int)
        self.assertIsInstance(back["d"], nbt.Double)
        self.assertEqual(back["t"], 1)
        self.assertEqual(back["l"].elem, nbt.INT)

    def test_mixed_list_refused(self):
        with self.assertRaises(nbt.NbtError):
            nbt.dumps({"l": nbt.TagList([nbt.Int(1), "x"], nbt.INT)})

    def test_truncated(self):
        with self.assertRaises(nbt.NbtError):
            nbt.loads(nbt.dumps(sample(), compress=False)[:-3])


if __name__ == "__main__":
    unittest.main()
