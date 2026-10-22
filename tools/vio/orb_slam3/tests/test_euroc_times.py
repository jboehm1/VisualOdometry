import csv
import tempfile
import unittest
from pathlib import Path

from tools.vio.orb_slam3.euroc_times import make_times


class OrbEuRoCTimesTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        images = self.root / "mav0/cam0/data"
        images.mkdir(parents=True)
        with (self.root / "mav0/cam0/data.csv").open("w", newline="") as stream:
            writer = csv.writer(stream)
            writer.writerow(["#timestamp [ns]", "filename"])
            for stamp in (1_000_000_000, 1_050_000_000, 1_100_000_000):
                (images / f"{stamp}.png").write_bytes(b"png")
                writer.writerow((stamp, f"{stamp}.png"))

    def tearDown(self):
        self.temp.cleanup()

    def test_writes_nanosecond_times_without_reference_access(self):
        output = self.root / "out/times.txt"
        result = make_times(self.root, output)
        self.assertEqual(output.read_text(), "1000000000\n1050000000\n1100000000\n")
        self.assertEqual(result["count"], 3)
        self.assertEqual(result["unit"], "nanoseconds")

    def test_rejects_non_timestamp_image_names(self):
        path = self.root / "mav0/cam0/data.csv"
        path.write_text("#timestamp [ns],filename\n1000000000,frame_1.png\n")
        with self.assertRaisesRegex(ValueError, "timestamp-named"):
            make_times(self.root, self.root / "times.txt")


if __name__ == "__main__":
    unittest.main()
