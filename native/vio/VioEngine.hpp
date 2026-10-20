#pragma once
// Experimental portable adapter API; no backend is included or linked.
// C++17 only. Callers must handle calibration and image/IMU clock domains.
#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace vector_vio::native {

enum class Tracking { Initializing, Tracking, Degraded, Lost };
enum class Scale { Unknown, MetricVerified };

struct Calibration {
    std::string camera_id;
    std::string version;
    int width = 0;
    int height = 0;
    // Full-resolution calibrated intrinsics (NOT ARCore preview K).
    double fx = 0, fy = 0, cx = 0, cy = 0;
    // Row-major T_imu_from_camera, a rigid 4x4 transformation.
    std::array<double, 16> t_imu_from_camera{};
    std::int64_t camera_to_imu_offset_ns = 0;
    std::int64_t rolling_shutter_readout_ns = 0;
    bool calibrated = false;
};

struct ImuSample {
    std::int64_t boottime_ns = 0;
    // Both types required by a real VIO integrator; do not fabricate them.
    enum class Kind { GyroscopeRadPerSecond, AccelerometerMetersPerSecond2 };
    Kind kind = Kind::GyroscopeRadPerSecond;
    std::array<double, 3> xyz{};
};

struct GrayFrame {
    std::int64_t start_exposure_boottime_ns = 0;
    std::int64_t exposure_ns = 0;
    int iso = 0;
    int width = 0, height = 0;
    // Owns a frame copy independently of Android Image.close()/JNI lifetime.
    // Row-major pixels, 1 channel; size MUST equal width*height.
    std::vector<std::uint8_t> pixels;
};

struct Pose {
    std::string engine_id;           // e.g. "basalt-<pinned-sha>".
    std::string world_frame_id;      // local VIO world, not GPS/ARCore.
    std::uint64_t tracking_segment_id = 0;
    std::int64_t boottime_ns = 0;
    Tracking tracking = Tracking::Initializing;
    Scale scale = Scale::Unknown;
    std::array<double, 3> position_m{};
    // Quaternion camera-to-world as (x,y,z,w), frame convention explicit.
    std::array<double, 4> orientation_xyzw{0, 0, 0, 1};
    std::optional<std::array<double, 36>> pose_covariance_se3;
    int feature_inliers = -1;        // -1 if engine provides no count.
};

class VioEngine {
public:
    virtual ~VioEngine() = default;
    // Must reject uncalibrated configuration; never invent metric scale.
    virtual bool initialize(const Calibration& calibration) = 0;
    virtual void on_imu(const ImuSample& sample) = 0;
    // Nullopt means unavailable pose. No extrapolation across gaps.
    virtual std::optional<Pose> on_frame(const GrayFrame& frame) = 0;
    // Must abandon current world/segment on camera switch or lost clock.
    virtual void reset() = 0;
};

} // namespace vector_vio::native
