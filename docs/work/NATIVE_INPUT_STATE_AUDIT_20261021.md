# Native input state audit — 21 October 2026

Scope: future acquisition guard only. It is still disconnected from Camera2,
JNI and a real VIO estimator. This fix does not produce poses or validate a phone.

## Reproduced failure

After a valid 10 s frame, supplying an invalid 12 s frame with missing IMU
incorrectly reset the session before input validation. A stale 9 s frame then
passed, violating accepted timestamp monotonicity:

| Input | Before | After |
|---|---|---|
| Valid frame at 10 s | ACCEPTED, segment 1 | ACCEPTED, segment 1 |
| Frame at 12 s, missing IMU | INVALID_IMU_WINDOW, segment **2** | INVALID_IMU_WINDOW, segment **1** |
| Stale frame at 9 s | **ACCEPTED** | NON_MONOTONIC_FRAME |

The exact Java class was compiled before and after on Java 17 and produced these
decisions. State commit/segmentation now occurs only after successful validation.
Overflowing future capture times also leave the previous watermark intact.
The corrected IMU mid-exposure timestamp must increase as well as frame start;
otherwise a changing exposure could send reverse-time frames to a future engine.

Three JUnit tests cover invalid-future state preservation, overflow, midpoint
reversal and successful recovery/real gap segmentation. No changes to camera
acquisition, calibration truth, existing ARCore runtime or VIO output policy.
Full JUnit/Android evidence must be checked at this iteration's exact Git SHA.
