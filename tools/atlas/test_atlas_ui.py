#!/usr/bin/env python3
"""Browser regression for the canonical, offline-compatible VECTOR VIO Atlas HTML.

Run: python -m pip install playwright && python -m playwright install chromium
     python tools/atlas/test_atlas_ui.py
The browser test uses no external network resources.
"""
import contextlib
import functools
import http.server
import json
from pathlib import Path
import threading
import unittest

ROOT = Path(__file__).resolve().parents[2]
ATLAS = "docs/VECTOR_VIO_ATLAS_INTERACTIF_v5.html"


class AtlasBrowserTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        from playwright.sync_api import sync_playwright
        cls.http = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(
            http.server.SimpleHTTPRequestHandler, directory=str(ROOT)))
        cls.http_thread = threading.Thread(target=cls.http.serve_forever, daemon=True)
        cls.http_thread.start()
        cls.playwright = sync_playwright().start()
        cls.browser = cls.playwright.chromium.launch(headless=True)
        cls.url = f"http://127.0.0.1:{cls.http.server_port}/{ATLAS}"

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.playwright.stop()
        cls.http.shutdown()
        cls.http.server_close()
        cls.http_thread.join(timeout=2)

    def setUp(self):
        self.context = self.browser.new_context(viewport={"width": 1440, "height": 900})
        self.page = self.context.new_page()
        self.errors = []
        self.page.on("pageerror", lambda err: self.errors.append(str(err)))
        self.page.goto(self.url, wait_until="load")
        self.page.locator("#marketGrid article.marketCard").first.wait_for()

    def tearDown(self):
        self.assertFalse(self.errors, f"Uncaught Javascript errors: {self.errors}")
        self.context.close()

    def test_filters_and_priorities(self):
        self.assertEqual(self.page.locator("#marketGrid article.marketCard").count(), 35)
        self.assertEqual(self.page.locator("#atlasFocusNav [aria-pressed=true]").count(), 1)
        self.assertTrue(self.page.locator("#opportunity-landscape").is_visible())
        self.assertFalse(self.page.locator("#terrain").is_visible())
        self.page.locator("#marketPriority").select_option("P0")
        self.assertEqual(self.page.locator("#marketGrid article.marketCard").count(), 10)
        self.page.locator("#marketPriority").select_option("")
        self.page.locator("#marketSearch").fill("pinces/rails")
        self.assertEqual(self.page.locator("#marketGrid article.marketCard").count(), 1)
        self.assertIn("pinces/rails", self.page.locator("#marketGrid").inner_text().lower())
        self.page.locator("#marketSearch").fill("")
        self.page.locator("#marketDomain").select_option("Construction")
        self.assertGreaterEqual(self.page.locator("#marketGrid article.marketCard").count(), 6)
        self.page.locator("#marketDomain").select_option("")
        self.page.screenshot(path="atlas-desktop.png", animations="disabled")

    def test_compare_and_copy_to_existing_ideas(self):
        buttons = self.page.locator("#marketGrid .marketCard button[aria-pressed]")
        self.assertEqual(buttons.count(), 35)
        buttons.nth(0).click()
        self.assertEqual(self.page.locator("#marketComparison .marketSideBySide article").count(), 1)
        buttons.nth(1).click()
        self.assertEqual(self.page.locator("#marketComparison .marketSideBySide article").count(), 2)
        text = self.page.locator("#marketComparison").inner_text()
        self.assertIn("Alternative sans VIO", text)
        self.assertIn("Expérience", text)
        self.page.locator("#marketComparison button").click()
        self.assertEqual(self.page.locator("#marketComparison .marketSideBySide article").count(), 0)

        self.page.locator("#marketGrid .marketCard").first.locator("button.catalogbtn").first.click()
        self.assertIn("Idée ajoutée", self.page.locator("#marketStatus").inner_text())
        saved = self.page.evaluate("JSON.parse(localStorage.getItem('vectorAtlasIdeas'))")
        self.assertTrue(any("Maintenance industrielle" in x.get("title", "") for x in saved))
        self.page.reload()
        saved_again = self.page.evaluate("JSON.parse(localStorage.getItem('vectorAtlasIdeas'))")
        self.assertEqual(saved, saved_again)
        self.page.get_by_role("button", name="🧩 Produit").click()
        self.assertTrue(self.page.locator("#ideas").is_visible())
        self.assertTrue(self.page.locator("#ideaGrid .card.idea").count() >= 1)
        self.page.locator("#atlasFocusNav button[data-atlas-focus='all']").click()
        self.assertTrue(self.page.locator("#terrain").is_visible())

    def test_legacy_navigation_and_market_visibility(self):
        self.page.get_by_role("button", name="🔬 Recherche").click()
        self.assertTrue(self.page.locator("#terrain").is_visible())
        self.assertFalse(self.page.locator("#market-deep-dives").is_visible())
        self.page.get_by_role("button", name="🎯 Marchés").click()
        self.assertTrue(self.page.locator("#market-deep-dives").is_visible())
        self.page.locator(".atlasHistoricLinks summary").click()
        self.page.locator('.atlasHistoricLinks a[href="#precisionlab"]').click()
        self.assertTrue(self.page.locator("#precisionlab").is_visible())
        self.assertTrue(self.page.locator("#opportunity-landscape").is_visible())
        self.assertEqual(self.page.locator("details#engineering-updates section").count(), 9)

    def test_mobile_layout_and_keyboard(self):
        self.page.set_viewport_size({"width": 390, "height": 844})
        self.page.screenshot(path="atlas-mobile.png", animations="disabled")
        metrics = self.page.evaluate("""() => ({
            innerWidth: window.innerWidth,
            body: document.body.scrollWidth,
            root: document.documentElement.scrollWidth,
            heading: !!document.querySelector('.atlasEditorial h1')
        })""")
        self.assertTrue(metrics["heading"])
        self.assertLessEqual(metrics["body"], metrics["innerWidth"] + 2, metrics)
        self.assertLessEqual(metrics["root"], metrics["innerWidth"] + 2, metrics)
        self.page.locator("#marketSearch").fill("photovoltaïque")
        self.assertGreater(self.page.locator("#marketGrid .marketCard").count(), 0)
        self.page.locator("#marketSearch").fill("totally impossible phrase for test")
        self.assertIn("Aucun résultat", self.page.locator("#marketGrid").inner_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
