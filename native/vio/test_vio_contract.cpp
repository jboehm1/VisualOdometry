#include "VioEngine.hpp"
#include <cassert>
#include <memory>
#include <type_traits>

using namespace vector_vio::native;

class NoEstimator final : public VioEngine {
public:
    bool initialize(const Calibration& c) override {
        initialized = c.calibrated && c.width > 0 && c.height > 0;
        return initialized;
    }
    void on_imu(const ImuSample&) override {}
    std::optional<Pose> on_frame(const GrayFrame&) override {
        return std::nullopt; // Intentional: NEVER invent a VIO estimate.
    }
    void reset() override { initialized = false; }
private:
    bool initialized = false;
};

int main() {
    static_assert(std::is_abstract_v<VioEngine>);
    static_assert(!std::is_copy_constructible_v<VioEngine>);
    GrayFrame a;
    a.width = 2; a.height = 2;
    a.pixels = {0, 25, 130, 255};
    auto b = a;
    b.pixels[0] = 255;
    assert(a.pixels[0] == 0); // native frame must not alias Android memory.
    Calibration c;
    c.camera_id="rear0";c.version="never-use-as-field-calibration";
    c.width=2; c.height=2; c.calibrated=false;
    std::unique_ptr<VioEngine> adapter=std::make_unique<NoEstimator>();
    assert(!adapter->initialize(c));
    c.calibrated=true;
    assert(adapter->initialize(c));
    assert(!adapter->on_frame(a).has_value()); // contract != implementation
    adapter->reset();
    Pose p;
    assert(p.scale==Scale::Unknown);
    assert(!p.pose_covariance_se3.has_value()); // unknown is NOT zero certainty
}
