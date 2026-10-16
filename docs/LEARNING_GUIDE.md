# Learning guide — from (u, v, 1) to a six-degree-of-freedom odometer

A hands-on tutorial connected directly to this Android app and to the original 2019 VO.ipynb notebook.

## 1. What information is in a camera frame?

A camera observes 2D pixels. Let a feature appear at pixel (u,v). In *homogeneous coordinates*, the point is p = [u,v,1]^T. The last component equals **one by convention**, not because the point is 1 metre away.

The calibrated ray is:

    r = inverse(K) * p
    P_camera = Z * r

K is the camera intrinsic matrix, r is a bearing ray, and Z is unknown depth along the camera's optical axis. Infinite positive Z values put points along the same ray and project to the **same pixel**.

If the homogeneous vector were [2u,2v,2] instead, it represents exactly the same image location. Its third component is **not a depth measurement**.

## 2. Two frames give rotation and translation direction

Track textured features from camera A to camera B. For calibrated cameras, matched image bearings x and x' satisfy:

    x'^T E x = 0
    E = [t]_x R

E is the essential matrix; [t]_x is the skew-symmetric matrix that represents cross products with t. Without additional metric information, if t fits the images then s*t for positive s also fits.

The rotation R is geometrically meaningful, while the translation **magnitude** is ambiguous. Thus monocular essential-matrix-based VO can reconstruct a trajectory only up to scale, even with excellent pixel correspondences.

Natural features (rocks, bark, leaf textures) are usable; recognisable semantic classes (car, tree, table) are *not required*. A uniform sky, smooth wall or motion-blurred surface provides fewer reliable constraints.

## 3. Why camera + IMU helps

A gyroscope gives angular rate. An accelerometer gives **specific force** (linear acceleration minus gravity, in sensor coordinates) affected by sensor bias and noise. A simplified measurement model is:

    gyro = true_angular_rate + gyro_bias + gyro_noise
    accelerometer = R_world_to_body * (world_acceleration - gravity) + accel_bias + accel_noise

A calibrated estimator propagates velocity and position over time with the IMU and corrects them by checking how projected 3D features move across the images.

Unlike camera projection, acceleration is measured in **metres per second squared**. Under sufficiently exciting movements, this helps make scale observable. It does not make scale perfectly known in every motion regime.

Important degeneracies:
- Hovering motionless: little translational excitation and no information about position.
- Pure rotation: many useful orientation features, very little metric translation information.
- Repeated nearly constant velocity: weak inertial constraints on metric scale, depending on initialization and other conditions.
- Strong vibration, rolling shutter, motion blur or poorly calibrated camera–IMU timing: larger error.

**IMU-only integration is not enough** for reliable position. Constant acceleration bias `b` creates a quadratic position error approximately (1/2)*b*t², before accounting for orientation error. Camera landmarks constrain the drift.

## 4. What a real visual-inertial estimator does

A typical estimator state contains:

    position p
    orientation quaternion q (or R)
    velocity v
    accelerometer bias b_a
    gyroscope bias b_g
    possibly camera–IMU extrinsics and time offset

Data association tracks observations of the *same* 3D feature across frames. A residual compares an observed pixel to the projected pixel of the estimated 3D point. IMU preintegration adds constraints between camera keyframes.

Modern VIO approaches use an EKF/MSCKF, a sliding-window optimizer, or related architectures. Robust systems handle feature outliers, initialization, gauge freedoms, dynamic objects and possible relocalization.

**VECTOR VIO intentionally delegates this complex estimator to ARCore**, which outputs a metric camera pose and a tracking-state flag. The app then performs transparent distance integration and visualization. ARCore's precise estimator is proprietary; its internal covariance and implementation are not exposed here.

For an open, researcher-modifiable backend, investigate **OpenVINS**, **VINS-Mono**, **ORB-SLAM3**, and visual–inertial benchmarks such as **EuRoC MAV**.

## 5. SE(3) pose, relative transforms and your previous notebook

A rigid transform from coordinate frame B to A is:

    T_A←B = [ R_A←B   t_A←B ]
            [ 0 0 0      1   ]

For poses T_world←cameraA and T_world←cameraB, the **relative camera transformation** is:

    T_cameraA←cameraB = inverse(T_world←cameraA) * T_world←cameraB

This rotation is NOT quaternion subtraction. Relative quaternion composition involves an inverse/conjugate and quaternion multiplication in the matching convention:

    q_A←B = inverse(q_world←A) ⊗ q_world←B

Similarly, simply subtracting global translations returns a global-frame displacement, not the local relative camera translation.

In the original 2019 notebook, a quaternion difference was used as an R target and global-position differences as T targets. They may correlate with motion but they are not generally the correct camera-relative rigid transform.

### Why our Android code uses position differences anyway

ARCore provides **camera centers in a consistent local world frame while tracking is valid**. We want the *length of physical displacement*, not a camera-frame motion label:

    distance_increment = || camera_world_position_next - camera_world_position_previous ||

Length is invariant to rotations of the global frame. The app **intentionally** uses successive world-position differences for this different objective. It rejects jumps after tracking gaps rather than stitching uncertain frames together.

## 6. Position error, path error and scale error are distinct

Imagine you walk straight out 5 m and return 5 m:

- Real path length: 10 m.
- Real displacement from start: approximately 0 m.
- A drifting estimator could report 10.8 m path and 0.9 m displacement.

Accurate-looking speed does not prove correct scale; a network might learn typical scenery dimensions and still fail on drone imagery. Even a VIO odometer that gives metres can exhibit scale drift.

For experiments, compute:
- path-length relative error using known physical distance;
- closure error after a loop;
- vertical error on known steps;
- stationary false distance per minute;
- failure counts and lost intervals.

**Visual odometry is an estimate. It is not ground truth.**

## 7. Read the application in this order

1. `Odometer.java`: see pose differences, deadband, timestamp checks, gap handling, path and displacement metrics.
2. `OdometerTest.java`: understand each synthetic reference trajectory and failure mode.
3. `MainActivity.java`: where the live ARCore camera pose and tracking state are obtained.
4. `CameraBackground.java`: transform image UVs into the correct display orientation; shader renders only the camera feed.
5. `HudView.java`: real-time, screen-space overlays and X/Z top-down path. The reticle is NOT a real-world 3D point.
6. `CsvRecorder.java`: logs ARCore camera positions and orientation quaternions without storing camera video.
7. `tools/plot_session.py`: load data independently and plot it.

## 8. Three small exercises

**Exercise A — stationary:** Leave the phone motionless in a well-lit textured room for 60 seconds. Compare path distance and displacement. Why might path be nonzero while displacement stays small?

**Exercise B — circle:** Walk a closed 10 m loop and return to the exact starting camera center. Which is more sensitive to drift: path or displacement?

**Exercise C — occlusion:** Record while moving, cover the lens, move to another place, then recover tracking. Check that the app counts a tracking gap and does not invent a straight-line path across the unobserved movement.

For all exercises, save the CSV and analyze it with the Python plotting utility. Never assume the app's measurement matches the physical trajectory just because it uses two decimal places.
