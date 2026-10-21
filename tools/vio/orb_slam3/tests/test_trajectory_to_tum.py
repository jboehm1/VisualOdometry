import tempfile
import unittest
from pathlib import Path

from tools.vio.orb_slam3.trajectory_to_tum import convert


class OrbTrajectoryConversionTests(unittest.TestCase):
    def test_converts_nanoseconds_to_tum_seconds(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / "in.txt", Path(temp) / "out.tum"
            source.write_text("1000000000 1 2 3 0 0 0 1\n1050000000 2 3 4 0 0 0 1\n1100000000 3 4 5 0 0 0 1\n")
            self.assertEqual(convert(source, output), 3)
            self.assertTrue(output.read_text().startswith("1.000000000 1.000000000"))

    def test_rejects_nonmonotonic_output(self):
        with tempfile.TemporaryDirectory() as temp:
            source, output = Path(temp) / "in.txt", Path(temp) / "out.tum"
            source.write_text("1000000000 1 2 3 0 0 0 1\n1000000000 2 3 4 0 0 0 1\n")
            with self.assertRaisesRegex(ValueError, "timestamps must increase"):
                convert(source, output)


if __name__ == "__main__":
    unittest.main()
