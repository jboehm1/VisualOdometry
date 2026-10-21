# EuRoC reference quaternion order

`bench.parse_poses(path, "euroc")` now interprets standard ASL/EuRoC state CSV
quaternions as **qw,qx,qy,qz**. TUM input remains **qx,qy,qz,qw**. Neither
translation, clock, coordinate frame nor metric scale is altered.

Before the fix, a wxyz +90° yaw quaternion was interpreted as +90° roll.
Position-only ATE could still look correct while rotational RPE and local-frame
translation RPE were invalid. The original Euroc unit fixture incorrectly used
xyzw and only checked elapsed time, so it failed to detect this defect.

New regression compares equivalent EuRoC/TUM trajectories and explicitly checks
rotation of the x-axis onto y. It failed before the fix and passes afterwards;
all 15 benchmark/fetch tests pass locally. These are synthetic contract tests,
not a new real-dataset accuracy result. Re-score existing EuRoC results with
recorded raw poses before relying on their prior orientation/RPE figures.

The native Basalt adapter in separate PR #45 already converts wxyz itself;
its in-progress host build does not need to be restarted for this change.

Canonical dataset schema: https://projects.asl.ethz.ch/datasets/euroc-mav/ .
Isolated branch and shared-file risks are recorded in the coordination journal.
