import json
import sys
import tempfile
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"datasets"))
from dataset_inspector import catalog, inspect


class DatasetCatalogTest(unittest.TestCase):
    CATALOG=Path(__file__).resolve().parents[1]/"datasets"/"catalog.json"

    def test_curated_sources_and_licenses(self):
        data=catalog(self.CATALOG)
        self.assertGreaterEqual(len(data["entries"]),5)
        for item in data["entries"]:
            self.assertTrue(item["official_data_url"].startswith("https://"))
            self.assertTrue(item["ground_truth"])
            self.assertTrue(item["license"])
        advio=next(e for e in data["entries"] if e["id"]=="advio")
        self.assertTrue(advio["commercial_restriction"])

    def test_no_dataset_stays_missing_even_with_catalog(self):
        with tempfile.TemporaryDirectory() as temp:
            r=inspect(self.CATALOG,"advio",temp)
            self.assertEqual("MISSING_DATA",r["status"])
            self.assertIsNone(r["evaluated_ate"])

    def test_layout_not_confused_with_benchmark(self):
        with tempfile.TemporaryDirectory() as temp:
            t=Path(temp)
            for name in ["advio-01/ground-truth/poses.csv","advio-01/iphone/platform-location.csv"]:
                f=t/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text("test\n")
            r=inspect(self.CATALOG,"advio",temp)
            self.assertEqual("LAYOUT_FOUND_NOT_BENCHMARKED",r["status"])
            self.assertIsNone(r["evaluated_ate"])


if __name__=="__main__":
    unittest.main()
