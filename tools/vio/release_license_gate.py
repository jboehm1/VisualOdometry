#!/usr/bin/env python3
"""Fail-closed inventory check for commercial redistribution (no legal opinion).

Usage: python3 tools/vio/release_license_gate.py tools/vio/third_party.json
Exit 0 means that the inventory contract is internally complete, NOT that a
lawyer has certified the build. A release must also scan the ACTUAL APK/SBOM.
"""
import argparse
import json
from pathlib import Path

def check_inventory(document, *, release=False):
    errors = []
    entries = document.get("components")
    if not isinstance(entries, list) or not entries:
        return ["components must be a nonempty list"]
    seen = set()
    for index, entry in enumerate(entries):
        n = "components[%d]" % index
        if not isinstance(entry, dict):
            errors.append(n + " must be an object")
            continue
        name = entry.get("name", "")
        if not isinstance(name, str) or not name.strip():
            errors.append(n + " has no name")
            continue
        if name in seen:
            errors.append(n + " duplicate " + name)
        seen.add(name)
        for field in ("spdx", "upstream", "revision", "distribution", "review_state"):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                errors.append(name + ": missing " + field)
        distribution = entry.get("distribution")
        state = entry.get("review_state")
        if distribution not in ("bundled", "service", "research_only", "excluded"):
            errors.append(name + ": invalid distribution")
        if state not in ("unreviewed", "needs_legal_review", "reviewed"):
            errors.append(name + ": invalid review_state")
        if release and distribution in ("bundled", "service"):
            if state != "reviewed":
                errors.append(name + ": commercial release blocked until reviewed")
            if entry.get("revision") in ("UNPINNED", "UNKNOWN", ""):
                errors.append(name + ": release needs pinned revision")
            if entry.get("spdx") in ("NOASSERTION", "UNKNOWN", ""):
                errors.append(name + ": release needs actual licensing terms")
            if not entry.get("notice_file"):
                errors.append(name + ": missing NOTICE/terms packaging decision")
    return errors

def main():
    p = argparse.ArgumentParser()
    p.add_argument("inventory", type=Path)
    p.add_argument("--release", action="store_true",
                   help="enforce review for bundled code and runtime services")
    args = p.parse_args()
    doc = json.loads(args.inventory.read_text(encoding="utf-8"))
    errors = check_inventory(doc, release=args.release)
    for error in errors:
        print("ERROR:", error)
    if not errors:
        print("PASS: inventory contract only; scan actual artifacts and obligations.")
    return 1 if errors else 0

if __name__ == "__main__":
    raise SystemExit(main())
