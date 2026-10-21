# Build identity — 2026-10-21

Integration rechecked at `3d251b8e06536a972b88bcf01bd34912ea5ceabd`;
AGENTS.md and current integration DEVELOPMENT_COORDINATION.md reviewed.
PR47 starts at `53b70f07e755855355653f3203fae790266c04f7`.
Isolated branch/worktree: `work/build-identity-20261021`.

The metrics APK correctly contained source53b70f07 but retained versionName
0.14.0-alpha1 / versionCode14. Thus that label could not identify an old APK.
Bump to 0.14.0-alpha2 / versionCode15 and show embedded source commit in
Settings, with an explicit unknown/local fallback for builds without provenance.
Replace the obsolete 0.10 training-export version literal with installed package
version and source identity. Keep the existing HUD reading package version.

Shared scope: app/build.gradle, SettingsActivity header, TrainingCapture metadata.
No manifest, menus, MainActivity, PDR, camera or estimator behavior changes.
Preserve all PR47 work; publish with an expected-head lease and no force push.
Run combined Android unit/lint/build/replay checks, inspect packaged version
and provenance, then deliver one clearly named APK. These are identity/build
checks, not physical S21 accuracy qualification or Basalt Android integration.
