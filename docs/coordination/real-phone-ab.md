# Coordination — real Mobile-GVIO same-signal A/B

**Starting integration HEAD inspected:** `feature/slam-explorer` at `32dc708b90f691b10b7ae411d6d70ec6d0c8c39f` on 2026-10-19. Read `AGENTS.md`, `docs/DEVELOPMENT_COORDINATION.md`, `docs/coordination/git-consolidation-20261019.md`, V3 architecture and existing Mobile-GVIO raw/fetch/score + tests.

**Branch:** `feature/real-phone-vio-ab-profile`; isolated from contemporaneous #3 B1–B8, #5 PDR and #17 smoke research. Those branches touch Android app, menus, some benchmarks/workflows: no merge/delete of their changes.

**Shared modified sources:** `tools/campaigns/mobile_gvio_raw.py` (optional fixed tracker profile, CPU/wall timing, optional already-decoded recording, unchanged balanced default), `tools/campaigns/mobile_gvio_score.py` (additional optional shared clock offset with explicit reference-fit classification). The generic V3 suite and benchmark engine registry remain unchanged.

**New owned files:** `tools/campaigns/mobile_gvio_ab.py`, `tools/campaigns/tests/test_mobile_gvio_ab.py`, `.github/workflows/mobile-vio-ab.yml`, `docs/REAL_PHONE_AB_AND_FIELD.md`, this coordination record.

**Strict scientific boundary:** Real camera and gyro drive an experimental monocular estimator; no metric scale; no Android ARCore or S21; GT is accessed only after all pose inference, fitted reference clock is shared across variants; posthoc Sim3 shape cannot be presented as metric error. Runtime is Python host CPU/wall, not device thermal/power. Frozen A/B tracker configurations avoid tuned-on-truth benchmarking.

**Next proof:** run offline tests + original checksum-verified Mobile-GVIO A/B and Android regression workflows on exact PR head, inspect combined SHA on integration before/after merge. Any physical S21 field or true metric VIO gate cannot be honestly closed by GitHub alone.
