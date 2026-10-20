import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"vision"))
from mask_metrics import score_pair,score_pair_from_counts

class SegmentationScoreTest(unittest.TestCase):
    def test_counts_and_iou_do_not_hide_false_negatives(self):
        result=score_pair([1,1,0,0,1,0],[1,0,0,1,0,0])
        self.assertEqual(1,result["true_positive"])
        self.assertEqual(1,result["false_positive"])
        self.assertEqual(2,result["false_negative"])
        self.assertEqual(.25,result["iou"])
        self.assertAlmostEqual(1/3,result["recall"],places=5)
    def test_ignore_labels_preserved(self):
        r=score_pair([255,1,0,255],[1,1,0,1],ignore=[255])
        self.assertEqual(2,r["ignored"])
        self.assertEqual(1.,r["iou"])
        self.assertEqual(.5,r["coverage_fraction"])
    def test_missing_class_is_unscored_not_perfect(self):
        r=score_pair([0,0],[0,0]);self.assertIsNone(r["iou"])
        self.assertIsNone(score_pair_from_counts({k:0 for k in
             ("true_positive","false_positive","true_negative","false_negative","ignored")})["recall"])
    def test_different_mask_sizes_error(self):
        with self.assertRaises(ValueError):score_pair([0],[0,1])

if __name__=="__main__":unittest.main()
