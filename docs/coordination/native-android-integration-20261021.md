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
Capture binary hashes and transitive copyright notices. The initial JNI version
probe had no live adapter and was not a user release.
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

## Successful cross-build and final validation extension

Combined source fbf07c385a9251639ace9905158fe04441e8b8bb, tree
758584e7eb1f03b72af12c950cfac343fd704cce, Android run39645575760 passed
Java tests, lint, the actual arm64 Basalt cross-build, APK packaging and existing
regression replays. APK read-back confirms that exact source/tree, actual
libvector_basalt.so and transitive notices. This is compilation evidence;
it is not a physical phone trial. No integration merge has occurred.

Readelf inspection found OpenCV C++ components already linked statically into
the backend while its unused Java wrapper was also packaged/loaded. Remove
that duplicate 20.9MB library and keep selected static components. All native
dependencies retain notices. Pin OpenCV archive SHA256
464d55abe64b53af3aeb73197cf4620535abd909916d06e6a8b7c32712013966.

Forward changes additionally bind factory bootstrap to device fingerprint,
reject a claimed verified flag, test calibration ownership and export interval
filtering, and guard rapid Activity teardown/resume with captured session/reader
objects and per-worker sensor listeners. Resource comparison exposes a failed
custom acquisition instead of treating it as a valid trial.

Add Android emulator JNI tests: bounded-queue shutdown without images, then
actual monocular inference on the first400 real EuRoC images and bracketed IMU.
Only sensor inputs enter the test APK, no reference; images are rectified using
published lens calibration for the pinhole backend. Exposure is unknown and
sent as zero sentinel solely in replay; live Camera2 still requires measured
positive exposure. Dataset/test-APK bytes stay temporary and are not uploaded.
The emulator ABI uses an explicit portable opengv ISA flag. Its runtime proof
does not substitute for real Camera2 hardware/calibration/energy qualification.

One consolidated PR47 remains the delivery candidate. Field instructions are
in docs/TEST_BASALT_COMPARISON.md. Revalidate the final head including these
forward changes before distributing its comparison APK.

## Production readiness remains a separate gate

The user explicitly requires continuous self-assessment and production quality.
Compilation/fixture success is insufficient. No Basalt production label or
promotion is authorized by unverified factory bootstrap, generic noise or assumed
zero offset. Real Camera2/IMU compatibility, independently validated calibration,
untuned phone accuracy/availability and sustained thermal/resource behavior
still require physical phone evidence. The exact 2025 paper reproduction also
remains unverified without full text. An installable comparison candidate is
needed to obtain that evidence; it must not be described as production ready.

Further hardening adds saved-session discovery after Activity recreation,
persisted per-run optical traces, explicit partial recovery after interruption,
preserved original metadata and clean export-picker state restoration. Closing
I/O/native errors now mark the run failed. Recovery does not invent a clean end
timestamp or splice another boot/run's optical samples into a historical export.

Actual Android execution proof at source
a3de1dbe3c8cd7ecc907f53b0c48ed1dbcc603ca, run39661828252:
Android30 x86_64 emulator, first400 rectified real EuRoC cam0 images plus real
IMU, no reference available. The actual JNI backend produced400 observed poses,
379 passing its availability heuristic; both instrumentation tests passed with
zero failures/errors/skips, including full-IMU-queue shutdown before first image.
Wall receipt18691ms is an emulator replay observation, not S21 throughput/power.
The workflow's subsequent adb pull failed because UTP had uninstalled the app;
the unchanged runtime JSON was present in captured logcat and is now extracted
there directly. No threshold was relaxed and no failed test was disabled.
