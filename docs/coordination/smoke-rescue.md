# Coordination — smoke rescue Option C

- Review: 2026-10-19; integration feature/slam-explorer @ 5f0105effd4fb2fb66b7d23620189c9605576de7.
- Read **AGENTS.md** blob 9e56be8eb4a4d4575399b2324daa6fad4b05db7d and **docs/DEVELOPMENT_COORDINATION.md** blob 61b55fcea7c9a2e241735f0c17505ecc8721404b.
- Work branch: feature/smoke-rescue-research, created from above exact integration SHA.
- Scope: firefighter zero-visibility research, cheaper physical BOM, credible smoke-capable dataset catalogue, architecture & safety study, standardized Python-only synthetic occupancy replay with clearly labeled thermal angular cues, tests and standalone workflow.
- **Shared files modified: NONE.** Android MainActivity / Manifest / DATA menus / GPS / PDR Java / existing pipelines / existing catalogues / Atlas / ROADMAP_CENTRAL intentionally untouched. Added .github/workflows/smoke-rescue-research.yml, which is a *new dedicated workflow*.
- Parallel work considered: PR #5 camera-free PDR; PR #15 Atlas V5 smoke use case + central roadmap; PR #16 modular dataset benchmark. Avoid conflicting claims, duplicate UI or unreviewed integration. No unpushed changes visible.
- One small Python JSONL schema is temporary and is not a claim to be the canonical benchmark format. Coordinate a future adapter with PR #16, with raw/data/truth and selected alignment kept strictly separate.
- Checks: isolated unit suite, synthetic demo HTML, on-PR CI intended. No device smoke/heat testing or real-sequence download claimed by this module. A successful synthetic demo is not a demonstration through smoke.
- Integration gate: re-check branch SHAs, licenses and all tests on combined SHA; keep firefighting functionality disabled from operational APK; identify experiment mode and uncertainty in UI. Never present unknown/free confusion as safe navigation.
- Next steps: (1) PR exact-SHA CI proof, (2) acquire/check minimal genuine smoke recording with independent provenance, (3) input adapter + dataset tests, (4) low-cost real radar and thermal acquisition only after BOM check, (5) measured clean-air rig before non-dangerous fog tests, (6) separate Android module integration.
