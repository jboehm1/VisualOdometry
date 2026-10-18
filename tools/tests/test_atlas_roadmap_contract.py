"""Guard against losing historical Atlas material or misrepresenting the safety-roadmap."""
import json
import unittest
from html.parser import HTMLParser
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
HTML=ROOT/"docs"/"VECTOR_VIO_ATLAS_INTERACTIF_v5.html"
ROADMAP=ROOT/"docs"/"ROADMAP_CENTRAL.md"
TASKS=ROOT/"docs"/"ROADMAP_CENTRAL.json"

class Ids(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids=[]
        self.hrefs=[]
    def handle_starttag(self,tag,attrs):
        pairs=dict(attrs)
        if "id" in pairs:self.ids.append(pairs["id"])
        if tag=="a" and "href" in pairs:self.hrefs.append(pairs["href"])

class AtlasRoadmapContract(unittest.TestCase):
    def test_offline_html_keeps_prior_sections_and_interactive_editor(self):
        html=HTML.read_text(encoding="utf-8")
        p=Ids()
        p.feed(html)
        for anchor in ("ideas","terrain","gpxfusion","usages","execution","iteration4",
                       "fumee","androidbaseline","editor","ideaGrid"):
            self.assertIn(anchor,p.ids)
            self.assertEqual(1,p.ids.count(anchor),anchor)
        for title in ("01 / Vue d'ensemble","08 / Terrain difficile",
                      "17 / Itération 2","18 / Suivi des 41 sous-tâches"):
            self.assertIn(title,html)
        for task in ("B1.1","B8.5"):
            self.assertIn(task,html)
        self.assertIn("localStorage",html)
        self.assertIn("function exportJSON()",html)
        self.assertIn("function importJSON(",html)

    def test_firefighter_mode_is_explicitly_research_not_safety(self):
        html=HTML.read_text(encoding="utf-8")
        for phrase in ("Fumée extrêmement dense","pompier","POSITION INCONNUE",
                       "NON OPÉRATIONNEL","UWB","thermiques","rampement",
                       "aucun essai en incendie réel"):
            self.assertIn(phrase.lower(),html.lower())

    def test_central_roadmap_is_separate_and_cross_linked(self):
        html=HTML.read_text(encoding="utf-8")
        doc=ROADMAP.read_text(encoding="utf-8")
        self.assertIn("ROADMAP_CENTRAL.md",html)
        self.assertIn("ROADMAP_CENTRAL.json",html)
        self.assertIn("41 sous-tâches",doc)
        self.assertIn("master",doc)
        self.assertIn("feature/slam-explorer",doc)
        self.assertIn("NON OPÉRATIONNEL",html)
        self.assertIn("Google FLP",html)

    def test_json_has_unique_evidence_gated_tasks_and_no_smoke_certificate(self):
        obj=json.loads(TASKS.read_text(encoding="utf-8"))
        self.assertEqual(1,obj["schema_version"])
        self.assertFalse(obj["qualification"]["smoke_firefighter_operational_use_permitted"])
        self.assertFalse(obj["qualification"]["precision_certified"])
        ids=[]
        for track in obj["tracks"]:
            for task in track["tasks"]:
                self.assertIn(task["state"],obj["statuses"])
                self.assertGreater(len(task["acceptance"]),25)
                ids.append(task["id"])
        self.assertEqual(len(ids),len(set(ids)))
        for task in ("F01","F05","S01","S06","C03","T03"):
            self.assertIn(task,ids)
        smoke=next(t for t in obj["tracks"] if t["id"]=="SMOKE")
        self.assertIn("aucun système qualifié",smoke["stage"])

if __name__=="__main__":
    unittest.main()
