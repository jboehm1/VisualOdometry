# Basalt real host estimator — 21 October 2026

- Integration read live at `3d251b8e06536a972b88bcf01bd34912ea5ceabd`:
  AGENTS and DEVELOPMENT_COORDINATION restrictions understood.
- Base PR #44 `513bb614cd7aedd0649b1242d339fc2bc72fb8ba`, all five workflows success.
- Own isolated branch `work/native-vio-basalt-host-20261021`.
- Scope: new bounded host build/replay workflow and sensor-only EuRoC adapter,
  using the existing benchmark metric implementation after inference.
- Shared files: additive README, Architecture, Roadmap Central, native-engine
  document and Atlas. One NEW workflow only; existing workflows unchanged.
- MainActivity, manifest, settings, exports, PDR, GPX and camera runtime untouched.
- Basalt source pinned `0f3b2b52c807f70ff4e2973ce253c73329eea7bc` and vcpkg
  gitlink `1e199d32ad53aab1defda61ce41c380302e3f95c` verified from upstream tree.
- Engine receives only two camera directories, IMU and fixed upstream calibration.
  Docker has no network, no reference/project mount during inference. No GT scale
  or clock fitting. First-reference gauge and SE3 diagnostic reported separately.
- Host image digest, build options, dependency metadata, source, data and binary
  hashes must be retained. Research-only; no native binaries/datasets uploaded.
- Next: actual CI source build and real EuRoC V1_01 replay, inspect failures and
  iterate. No build, accuracy, Android JNI or transitive-license success presumed.

## First actual CI finding and acquisition adjustment
Official ETH V2_01 download timed out after 60 seconds in run 39554931508, before native build or any accuracy result. The next attempt uses the pinned HF V1_01 archive above (Git LFS metadata verified). This is an availability-driven sequence substitution, not a result-driven choice. Original V1_01 reference attitude is documented as inaccurate by OpenVINS; orientation and first-pose-gauge results are explicitly diagnostics, not authoritative field accuracy. Source: https://github.com/rpng/open_vins/blob/master/docs/gs-datasets.dox . Raw sources remain excluded from artifacts.

## Second actual CI finding
Run 39555347370 verified the 1,149,702,102-byte mirror archive, staged 1200 real stereo frames and IMU, fetched exact upstream source and pulled its tooling image. Configuration then failed because shallow vcpkg lacked the registry baseline commit. Restore full vcpkg history and verify `05442024...:versions/baseline.json` before configuration, as upstream CI does. Tooling image is now fixed to digest `sha256:3553facf6bf25baf283547fab7fad0dd74d47ba79fb9a9c9f9c11fb777d3a502`, observed in that run. No engine poses or accuracy result yet.
