"""Static contract for the cumulative offline Atlas (not an Android device test)."""
import json
import re
import unittest
from html.parser import HTMLParser
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
ATLAS=ROOT/"docs"/"VECTOR_VIO_ATLAS_INTERACTIF_v5.html"


class Structure(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids=[]
        self.links=[]
        self.sections=0
        self.catalog_indices=[]
    def handle_starttag(self,tag,attrs):
        options=dict(attrs)
        if tag=="section":
            self.sections+=1
        if "id" in options:
            self.ids.append(options["id"])
        if tag=="a" and options.get("href","").startswith("#"):
            self.links.append(options["href"][1:])
        if "data-catalog-index" in options:
            self.catalog_indices.append(int(options["data-catalog-index"]))


class AtlasPrecisionOpportunityContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.html=ATLAS.read_text(encoding="utf-8")
        cls.parser=Structure()
        cls.parser.feed(cls.html)

    def test_historical_sections_and_editable_library_survive(self):
        for fragment in ("01 / Vue d'ensemble","08 / Terrain difficile",
                         "08 bis / Fumée extrêmement dense","17 / Itération 2",
                         "18 / Suivi des 41 sous-tâches","19 / Nouvelle direction"):
            self.assertIn(fragment,self.html)
        for anchor in ("ideas","terrain","fumee","gpxfusion","segmentation",
                       "branches","journal","usages","execution","experiences",
                       "iteration4","androidbaseline","editor","ideaGrid"):
            self.assertEqual(1,self.parser.ids.count(anchor),anchor)
        for old in ("localStorage.getItem('vectorAtlasIdeas')","function exportJSON()",
                    "async function importJSON(","function saveIdea()"):
            self.assertIn(old,self.html)
        self.assertGreaterEqual(self.parser.sections,22)

    def test_new_adaptive_modes_always_distinguish_proposal_from_existing(self):
        for anchor in ("adaptive","precisioncases","precisionlab","professionalCatalog","catalogStatus"):
            self.assertEqual(1,self.parser.ids.count(anchor),anchor)
        for phrase in ("ARCHITECTURE PROPOSÉE","NON CODÉE DANS L’APK",
                       "PDR « pas » invalide à vélo","POSITION INCONNUE",
                       "INSUFFICIENT_LIGHT","EXCESSIVE_MOTION","INSUFFICIENT_FEATURES",
                       "GNSS_REFERENCE","PDR_PEDESTRIAN_EXPERIMENTAL",
                       "UNKNOWN","30 poses/s ARCore visées"):
            self.assertIn(phrase,self.html)
        self.assertNotIn("précision de 10 cm garantie",self.html.lower())

    def test_business_catalog_covers_many_professions_and_non_useful_cases(self):
        for phrase in ("Technicien de maintenance","Inspection bâtiment",
                       "Maintenance tunnel","Entrepôt","Forestiers",
                       "VTT / trail","Mines / carrières","Fumée / secours",
                       "peu utile","topographie","mesuré"):
            self.assertIn(phrase,self.html)
        self.assertEqual(list(range(14)),self.parser.catalog_indices)
        self.assertIn("function saveCatalogIdea(i)",self.html)
        self.assertIn("ideas.some(x=>x.title===item.title)",self.html)
        self.assertIn("persist();",self.html)

    def test_javascript_catalog_json_has_complete_copyable_cards(self):
        match=re.search(r'const opportunityCatalog=(\[[^;]+\]);\s*function saveCatalogIdea',self.html)
        self.assertIsNotNone(match)
        catalog=json.loads(match.group(1))
        self.assertEqual(14,len(catalog))
        for item in catalog:
            self.assertEqual({"title","desc","status","next"},set(item))
            self.assertGreater(len(item["desc"]),45)
            self.assertGreater(len(item["next"]),30)
            self.assertIn(item["status"],{"Idée","À étudier","En cours","Prototype","Validé"})
        titles=[c["title"] for c in catalog]
        self.assertEqual(len(titles),len(set(titles)))

    def test_precision_claims_remain_unqualified(self):
        for phrase in ("objectif expérimental","Non disponible",
                       "non démontré","vérité indépendante",
                       "erreur de fermeture","coordonnées absolues",
                       "même objet","point de départ","abstention",
                       "caméra couverte"):
            self.assertIn(phrase.lower(),self.html.lower())
        self.assertIn("PR #11 a été fusionnée",self.html)
        self.assertIn("PDR et GPS+VIO de B1–B8 restent",self.html)

    def test_unique_ids_and_valid_fragment_navigation(self):
        self.assertEqual(len(self.parser.ids),len(set(self.parser.ids)))
        self.assertFalse(set(self.parser.links)-set(self.parser.ids),
                         set(self.parser.links)-set(self.parser.ids))
        for link in ("adaptive","precisioncases","precisionlab"):
            self.assertIn(link,self.parser.links)

if __name__=="__main__":
    unittest.main()
