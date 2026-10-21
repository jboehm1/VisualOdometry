import tempfile
import unittest
from pathlib import Path

from tools.vio.openvins.freeze_calibration import FLAGS, freeze


class FreezeCalibrationTests(unittest.TestCase):
    def test_disables_every_online_calibration_flag_without_touching_other_values(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "in.yaml"
            output = Path(temp) / "out.yaml"
            source.write_text("\n".join(f"{flag}: true # preserve" for flag in FLAGS) + "\nfoo: 3\n")
            result = freeze(source, output)
            text = output.read_text()
            self.assertTrue(result["all_online_calibration_disabled"])
            self.assertEqual(text.count(": false # preserve"), len(FLAGS))
            self.assertIn("foo: 3", text)

    def test_rejects_missing_flag(self):
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "in.yaml"
            source.write_text("calib_cam_extrinsics: true\n")
            with self.assertRaisesRegex(ValueError, "missing or duplicated"):
                freeze(source, Path(temp) / "out.yaml")


if __name__ == "__main__":
    unittest.main()
