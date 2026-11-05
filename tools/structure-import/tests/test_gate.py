import hashlib
import tempfile
import unittest
from pathlib import Path

from helpers import S, grid_of, manifest
from sinr import licences
from sinr import manifest as mf


class LicenceGateTests(unittest.TestCase):
    def test_accepted_licences_pass(self):
        for spdx in ("CC0-1.0", "CC-BY-4.0", "CC-BY-3.0", "CC-BY-SA-4.0", "CC-BY-SA-3.0"):
            self.assertEqual(mf.gate(manifest(licence=spdx)), [], spdx)

    def test_output_licence_family(self):
        self.assertEqual(licences.output_licence("CC-BY-3.0"), "CC-BY-4.0")
        self.assertEqual(licences.output_licence("CC-BY-SA-3.0"), "CC-BY-SA-4.0")
        self.assertEqual(licences.output_licence("CC0-1.0"), "CC0-1.0")

    def test_refused_licences(self):
        for spdx, word in (("CC-BY-NC-4.0", "NonCommercial"), ("CC-BY-NC-SA-4.0", "NonCommercial"),
                           ("CC-BY-ND-4.0", "NoDerivatives"), ("CC-BY-NC-ND-4.0", "NoDerivatives"),
                           ("CC-BY-2.0", "2.x"), ("none", "no licence"), ("MIT-ish", "not in the accepted")):
            errs = mf.gate(manifest(licence=spdx))
            self.assertTrue(any(word in e for e in errs), (spdx, errs))

    def test_missing_fields_refused(self):
        for k in ("source_url", "archive_url", "retrieved", "licence_url"):
            errs = mf.gate(manifest(**{k: None}))
            self.assertIn(f"missing {k}", errs)
        self.assertTrue(mf.gate(manifest(author="")))   # schema minLength

    def test_status(self):
        self.assertTrue(any("only the maintainer" in e for e in mf.gate(manifest(status="candidate"))))
        self.assertTrue(mf.gate(manifest(status="rejected")))
        self.assertTrue(any("approved_by" in e for e in mf.gate(manifest(approved_by=None))))

    def test_own_work(self):
        m = manifest(own_work=True, licence=licences.OWN, source_url=None, archive_url=None, retrieved=None,
                     licence_url=None, approved_by=None, approved_on=None)
        self.assertEqual(mf.gate(m), [])
        self.assertTrue(mf.gate(dict(m, licence="CC-BY-4.0")))

    def test_sha_mismatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "source" / "b.schem"
            src.parent.mkdir()
            src.write_bytes(b"hello")
            good = hashlib.sha256(b"hello").hexdigest()
            self.assertEqual(mf.gate(manifest(source_file="source/b.schem", source_sha256=good), Path(tmp)), [])
            errs = mf.gate(manifest(source_file="source/b.schem", source_sha256="0" * 64), Path(tmp))
            self.assertIn("source_sha256 does not match the source file", errs)

    def test_lotr_block_refused(self):
        g = grid_of(1, 1, 1)
        g.set(0, 0, 0, S("lotr:gondor_brick"))
        self.assertTrue(any("LOTR" in e for e in mf.gate(manifest(), grid=g)))

    def test_middle_earth_manager_refused_unless_own_work(self):
        g = grid_of(1, 1, 1)
        g.set(0, 0, 0, S("middle-earth:structure_manager", {"facing": "north"}))
        self.assertTrue(any("export" in e for e in mf.gate(manifest(), grid=g)))
        own = manifest(own_work=True, licence=licences.OWN)
        self.assertEqual(mf.content_gate(own, g), [])

    def test_schema(self):
        self.assertTrue(any("unknown field" in e for e in mf.gate(dict(manifest(), surprise=1))))
        self.assertTrue(any("kinds" in e for e in mf.gate(manifest(kinds=["dungeon"]))))
        self.assertTrue(any("id" in e for e in mf.gate(manifest(id="Bad Id"))))


if __name__ == "__main__":
    unittest.main()
