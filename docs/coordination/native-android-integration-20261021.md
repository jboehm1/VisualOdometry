# Basalt Android integration — active workstream

Starting integration checked: feature/slam-explorer at
3d251b8e06536a972b88bcf01bd34912ea5ceabd. AGENTS.md and live
DEVELOPMENT_COORDINATION.md read. Consolidated PR47 at
4874db08c90b6cfbc8e8ba911305d786b296e50e includes the preceding image
optimization, native contracts, actual host VIO, PDR and telemetry work.

Isolated local branch/worktree work/native-android-integration-20261021.
User requests one APK capable of comparing previous ARCore operation with
actual custom VIO using the optimized Camera2 path. Preserve all existing
GPX/PDR/settings/export features. No baseline replacement or whole-file merge.

Scope: lean pinned Basalt arm64 NDK library, live image/IMU/JNI adapter,
explicit calibrated input/clock/quality gates, separate engine sessions and
common export/metrics, selectable comparison UI. Shared files expected:
Android build workflow, manifest, settings catalog/activity dispatch,
Camera2 acquisition, app version and documentation. No pose sampling/view-rate
coupling and no invented calibration/metric accuracy. Do not enable a fake
native mode or label interface/loader smoke as VIO inference.

First concrete gate: cross-compile the actual estimator core without desktop,
ROS or GUI executables; pinned Basalt/vcpkg and versioned official OpenCV SDK.
Capture binary hashes and transitive copyright notices. JNI version probe
currently has no live adapter: this intermediate build is not a user release.
Follow with ingestion/lifecycle/calibration tests, actual inference and combined
Android checks before delivering a new comparison APK.

The 2025 publication full text is still unavailable via the publisher endpoint
(403 on this audit). Current gradient controller is an independent recreation
of abstract principles, not verified equation-exact reproduction. Never erase
that limitation or describe the current TextureView feedback as calibrated raw
camera frames. Device data may be required for calibration/clock qualification.
