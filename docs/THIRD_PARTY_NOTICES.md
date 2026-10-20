# Third-party notices — NOT APPROVED FOR PRODUCT DISTRIBUTION

This file is a **placeholder for a required, verified release artifact**.
It is not a substitute for the licenses and attribution notices bundled
with the actual dependencies. Do not claim that its existence is legal
clearance or use it to mark entries in `tools/vio/third_party.json`
as `reviewed`.

## Current Gradle runtime components (inventory to audit)

- `com.google.ar:core:1.56.0` — ARCore binary governed by Google ARCore
  Additional Terms; app must include user-facing Google Play Services for AR
  disclosure. See https://developers.google.com/ar/develop/terms and
  https://github.com/google-ar/arcore-android-sdk/blob/main/LICENSE
- `com.google.android.gms:play-services-location:21.4.0` — review provider
  terms, redistributable notices and transitive dependencies before release.
- `Basalt`, `Kimera-VIO`, `OKVIS2` — **not included in the Android
  binary** as of this branch. Their source and dependency copyrights,
  licenses and notices need auditing if a native engine is added.

## Release process required

1. Lock all Java, Gradle, NDK and C++ third-party versions/hashes; export
   actual assembled AAB/APK SBOM including transitive binaries.
2. Keep verbatim notices/attribution as required by each license and
   provider terms, with exact version and source reference.
3. Link disclosure/attribution from a discoverable in-app legal screen.
4. Check Google Play terms and each component's redistribution conditions.
5. Independent human/legal signoff per inventory entry before setting
   `review_state=reviewed` and passing the `--release` structural check.

Until those checks pass, commercial distribution is **blocked**.
