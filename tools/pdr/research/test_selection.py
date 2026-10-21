import importlib.util
from pathlib import Path
import unittest

s=importlib.util.spec_from_file_location("research",Path(__file__).with_name("evaluate_candidates.py"))
m=importlib.util.module_from_spec(s);s.loader.exec_module(m)


def metric(f1,count_error):
    return {"f1":f1,"count_error_pct":count_error,"count":100,"reference":100,
            "false_positives":0,"missed_steps":0}


class SelectionTests(unittest.TestCase):
    def test_lower_count_error_cannot_hide_worse_step_f1(self):
        rows=[{"metrics":{"current":metric(.95,10),"smooth":metric(.90,0),"adaptive":metric(.95,10)}}]
        self.assertEqual("current",m.select(rows)[0])

    def test_good_average_cannot_hide_bad_single_placement(self):
        rows=[{"metrics":{"current":metric(.9,10),"smooth":metric(a,0),"adaptive":metric(.9,10)}}
              for a in (.84,.99,.99)]
        self.assertEqual("current",m.select(rows)[0])

    def test_eligible_gain_is_selected_and_empty_cohort_rejected(self):
        rows=[{"metrics":{"current":metric(.9,10),"smooth":metric(.92,8),"adaptive":metric(.93,7)}}]
        self.assertEqual("adaptive",m.select(rows)[0])
        with self.assertRaises(ValueError):m.select([])


if __name__=="__main__":unittest.main()
