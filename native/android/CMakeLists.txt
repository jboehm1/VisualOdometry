cmake_minimum_required(VERSION 3.24)
project(vector_basalt_android LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
if(NOT BASALT_SOURCE)
  message(FATAL_ERROR "BASALT_SOURCE must identify the checked pinned upstream tree")
endif()
find_package(Eigen3 CONFIG REQUIRED)
find_package(TBB CONFIG REQUIRED)
find_package(fmt CONFIG REQUIRED)
find_package(magic_enum CONFIG REQUIRED)
find_package(nlohmann_json CONFIG REQUIRED)
find_package(basalt-headers CONFIG REQUIRED)
find_package(opengv CONFIG REQUIRED)
set(OpenCV_STATIC ON)
find_package(OpenCV CONFIG REQUIRED COMPONENTS core imgproc features2d)
set(BASALT_CORE
  src/linearization/landmark_block.cpp
  src/linearization/linearization_abs_qr.cpp
  src/linearization/linearization_abs_sc.cpp
  src/linearization/linearization_base.cpp
  src/linearization/linearization_rel_sc.cpp
  src/optical_flow/optical_flow.cpp
  src/utils/keypoints.cpp
  src/utils/system_utils.cpp
  src/utils/time_utils.cpp
  src/utils/vio_config.cpp
  src/vi_estimator/ba_base.cpp
  src/vi_estimator/landmark_database.cpp
  src/vi_estimator/marg_helper.cpp
  src/vi_estimator/sc_ba_base.cpp
  src/vi_estimator/sqrt_ba_base.cpp
  src/vi_estimator/sqrt_keypoint_vio.cpp
  src/vi_estimator/sqrt_keypoint_vo.cpp
  src/vi_estimator/vio_estimator.cpp)
list(TRANSFORM BASALT_CORE PREPEND "${BASALT_SOURCE}/")
add_library(vector_basalt SHARED ${BASALT_CORE} bridge.cpp)
target_include_directories(vector_basalt PRIVATE "${BASALT_SOURCE}/include" ${OpenCV_INCLUDE_DIRS})
target_link_libraries(vector_basalt PRIVATE Eigen3::Eigen TBB::tbb fmt::fmt magic_enum::magic_enum
  nlohmann_json::nlohmann_json basalt::basalt-headers opengv ${OpenCV_LIBS} log)
target_compile_definitions(vector_basalt PRIVATE BASALT_INSTANTIATIONS_FLOAT EIGEN_DONT_PARALLELIZE)
target_compile_options(vector_basalt PRIVATE -O1 -g0 -Wno-deprecated-declarations -frelaxed-template-template-args)
target_link_options(vector_basalt PRIVATE -Wl,--no-undefined -Wl,-z,max-page-size=16384)
