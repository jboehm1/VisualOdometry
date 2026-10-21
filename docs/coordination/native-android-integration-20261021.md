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


## Live adapter implementation (not yet cross-build or device validated)

The Android acquisition path now has JNI ingestion of owned grayscale images,
actual capture timestamps/exposure/skew and synchronized accelerometer/gyroscope.
Timestamp join buffers are bounded; missing geometry/timing rejects a frame.
Acceleration is interpolated only between real brackets, maximum gap 30 ms.
A larger paired-IMU gap stops inference instead of hiding missing motion.
Native output retains absolute pose timestamps; an availability heuristic based
on feature/landmark counts is not covariance or an accuracy certificate.
Teardown runs on the acquisition worker, with camera EOS preceding IMU EOS to
avoid blocking against a full IMU queue before the first image.

One app retains the previous ARCore/PDR/GPX path and adds Settings > Camera &
overlays > VIO custom · Basalt (experimental). Custom recording is explicit;
stop/export creates a ZIP with derived poses, raw sensor events, interpolated
pairs, actual matched capture metadata, common resources, optical quality and
calibration provenance. No images or ARCore trajectory are fed into custom VIO.

The initial field path is explicitly UNVERIFIED SDK factory geometry bootstrap:
ARCore CPU-image intrinsics plus relative camera/Android-sensor transform, only
with fixed focus and actual crop/focus/stabilization metadata. Camera2 must
match camera ID, dimensions, focus, crop, stabilization and REALTIME clock.
Camera/IMU offset is assumed zero, IMU noise uses upstream defaults, the model
is pinhole without measured distortion, and rolling shutter is timestamped at
its middle row without motion compensation. This is an experimental path,
not a bypass claiming NativeVioInputGate.verified=true. That original production
contract remains fail-closed. Optional metadata absence blocks this bootstrap.
An externally measured calibration/import path remains future work.

Version advanced to 0.15.0-alpha1 / 16 to distinguish this combined candidate
from alpha2 / 15. Do not distribute until combined Java/lint/native/package
checks pass, native dependencies/notices are inspected, and identity matches.
Even a successful cross-build does not establish physical Android inference or
reliable metric accuracy. The Android first-run test remains explicit.
