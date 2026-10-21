import csv
import tempfile
import unittest
from pathlib import Path

from tools.vio.openvins.euroc_rosbag import read_sensor_records


class EurocRosbagInputTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for camera in ("cam0", "cam1"):
            folder = self.root / "mav0" / camera
            (folder / "data").mkdir(parents=True)
            with (folder / "data.csv").open("w", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["#timestamp [ns]", "filename"])
                for stamp in (1_000_000_000, 1_050_000_000, 1_100_000_000):
                    name = f"{stamp}.png"
                    (folder / "data" / name).write_bytes(b"png")
                    writer.writerow((stamp, name))
        imu = self.root / "mav0/imu0"
        imu.mkdir(parents=True)
        (imu / "data.csv").write_text(
            "#timestamp [ns],w_x,w_y,w_z,a_x,a_y,a_z\n"
            "1000000000,0,0,0,0,0,9.81\n"
            "1050000000,0,0,0,0,0,9.81\n"
            "1100000000,0,0,0,0,0,9.81\n"
        )

    def tearDown(self):
        self.temp.cleanup()

    def test_reads_only_synchronized_stereo_and_bracketed_imu(self):
        cameras, imu = read_sensor_records(self.root)
        self.assertEqual([x[0] for x in cameras[0]], [x[0] for x in cameras[1]])
        self.assertEqual(len(cameras[0]), 3)
        self.assertEqual(len(imu), 3)

    def test_rejects_stereo_timestamp_mismatch(self):
        path = self.root / "mav0/cam1/data.csv"
        lines = path.read_text().replace("1050000000", "1050000001")
        path.write_text(lines)
        image = self.root / "mav0/cam1/data/1050000000.png"
        image.rename(image.with_name("1050000001.png"))
        with self.assertRaisesRegex(ValueError, "identical camera timestamps"):
            read_sensor_records(self.root)

    def test_rejects_imu_that_does_not_cover_images(self):
        path = self.root / "mav0/imu0/data.csv"
        path.write_text("#timestamp [ns],w_x,w_y,w_z,a_x,a_y,a_z\n1050000000,0,0,0,0,0,9.81\n")
        with self.assertRaisesRegex(ValueError, "does not cover"):
            read_sensor_records(self.root)


if __name__ == "__main__":
    unittest.main()
