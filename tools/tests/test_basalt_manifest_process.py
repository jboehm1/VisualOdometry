"""Source-contract regression for native Basalt process isolation.

Robolectric's shadow PackageManager does not expose all manifest processName
values. Parse the manifest source here; Android build additionally merges it.
"""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
ANDROID = "{http://schemas.android.com/apk/res/android}"

class BasaltProcessIsolationTest(unittest.TestCase):
    def test_basalt_camera_process_isolated_and_never_exported(self):
        root = ET.parse(MANIFEST).getroot()
        activities = root.findall("./application/activity")
        def get(name):
            matches=[a for a in activities if a.attrib.get(ANDROID+"name")==name]
            self.assertEqual(1,len(matches),f"Expected one {name} activity")
            return matches[0]
        baseline=get(".MainActivity")
        custom=get(".Camera2ManualActivity")
        self.assertIsNone(baseline.attrib.get(ANDROID+"process"),
                          "ARCore must remain in main process")
        self.assertEqual(":basalt",custom.attrib.get(ANDROID+"process"))
        self.assertEqual("false",custom.attrib.get(ANDROID+"exported"),
                         "Native child activity must not be externally callable")

if __name__=="__main__":
    unittest.main()
