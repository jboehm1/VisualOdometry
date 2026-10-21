#include <jni.h>
#include <basalt/vi_estimator/vio_estimator.h>
#include <mutex>
#include <thread>
#include <array>
#include <algorithm>
#include <stdexcept>
#include <cmath>
#include <chrono>
#include <tbb/global_control.h>
#include <opencv2/core.hpp>

namespace {
struct Engine {
  tbb::global_control threads{tbb::global_control::max_allowed_parallelism,2};
  basalt::Calibration<double> calibration;
  basalt::VioEstimatorBase::Ptr vio;
  basalt::OpticalFlowBase::Ptr flow;
  tbb::concurrent_bounded_queue<basalt::PoseVelBiasState<double>::Ptr> states;
  tbb::concurrent_bounded_queue<basalt::VioVisualizationData::Ptr> visuals;
  std::thread state_thread,visual_thread;
  std::mutex mutex;
  std::array<double,7> pose{};
  int64_t time=0,last_imu=0,last_frame=0;
  int features=0,landmarks=0;
  int64_t visual_time=0;
  int width=0,height=0;
  bool stopped=false;

  Engine(const double* p,const double* imu,int64_t imu_time) {
    width=static_cast<int>(p[0]);height=static_cast<int>(p[1]);
    if(width<32||height<32||width>4096||height>4096||p[2]<=0||p[3]<=0||p[4]<0||p[4]>=width||p[5]<0||p[5]>=height||imu_time<=0
       ||p[22]<=0||p[23]<=0||p[24]<=0||p[25]<=0||p[26]<50||p[26]>1000
       ||Eigen::Vector3d(imu[0],imu[1],imu[2]).norm()<1)
      throw std::invalid_argument("Invalid calibrated dimensions/intrinsics/IMU");
    Eigen::Matrix3d r;Eigen::Vector3d t;
    for(int i=0;i<3;i++){for(int j=0;j<3;j++)r(i,j)=p[6+4*i+j];t[i]=p[6+4*i+3];}
    if((r.transpose()*r-Eigen::Matrix3d::Identity()).norm()>1e-4||std::abs(r.determinant()-1)>1e-4)
      throw std::invalid_argument("Non-rigid camera to IMU calibration");
    calibration.T_i_c.emplace_back(Sophus::SO3d(r),t);
    auto camera=basalt::GenericCamera<double>::fromString("pinhole");
    camera.setFromInit(Eigen::Vector4d(p[2],p[3],p[4],p[5]));
    calibration.intrinsics.push_back(camera);calibration.resolution.emplace_back(width,height);
    calibration.gyro_noise_std.setConstant(p[22]);calibration.accel_noise_std.setConstant(p[23]);
    calibration.gyro_bias_std.setConstant(p[24]);calibration.accel_bias_std.setConstant(p[25]);
    calibration.imu_update_rate=p[26];
    cv::setNumThreads(2);
    basalt::VioConfig config;
    config.vio_debug=false;config.vio_extended_logging=false;config.vio_enforce_realtime=true;
    flow=basalt::OpticalFlowFactory::getOpticalFlow(config,calibration);
    flow->input_queue.set_capacity(2);
    vio=basalt::VioEstimatorFactory::getVioEstimator(config,calibration,basalt::constants::g,true,false);
    vio->out_state_queue=&states;vio->out_vis_queue=&visuals;
    flow->output_queue=&vio->vision_data_queue;
    states.set_capacity(128);visuals.set_capacity(32);
    vio->initialize(Eigen::Vector3d::Zero(),Eigen::Vector3d::Zero());
    push_imu(imu_time,imu);
    try {
    state_thread=std::thread([this]{
      basalt::PoseVelBiasState<double>::Ptr state;
      while(true){states.pop(state);if(!state)break;
        // Basalt world Z-up -> app world Y-up; camera CV -> GL optical axes.
        Eigen::Matrix3d world;world<<1,0,0,0,0,1,0,-1,0;
        Eigen::Matrix3d optical;optical<<1,0,0,0,-1,0,0,0,-1;
        auto camera=state->T_w_i*calibration.T_i_c[0];
        Eigen::Vector3d position=world*camera.translation();
        Eigen::Quaterniond q(world*camera.rotationMatrix()*optical);
        if(!position.allFinite()||!q.coeffs().allFinite())continue;
        std::lock_guard<std::mutex> lock(mutex);time=state->t_ns;
        pose={position.x(),position.y(),position.z(),q.x(),q.y(),q.z(),q.w()};
      }
    });
    visual_thread=std::thread([this]{
      basalt::VioVisualizationData::Ptr visual;
      while(true){visuals.pop(visual);if(!visual)break;
        std::lock_guard<std::mutex> lock(mutex);visual_time=visual->t_ns;
        landmarks=static_cast<int>(visual->points.size());features=0;
        if(visual->opt_flow_res&&!visual->opt_flow_res->observations.empty())
          features=static_cast<int>(visual->opt_flow_res->observations[0].size());
      }
    });
    } catch (...) { stop(); throw; }
  }
  bool push_imu(int64_t t,const double* x) {
    if(stopped||t<=last_imu||vio->finished)return false;
    auto data=std::make_shared<basalt::ImuData<double>>();data->t_ns=t;
    data->accel=Eigen::Vector3d(x[0],x[1],x[2]);data->gyro=Eigen::Vector3d(x[3],x[4],x[5]);
    if(!vio->imu_data_queue.try_push(data))return false;last_imu=t;return true;
  }
  bool push_frame(int64_t t,const uint8_t* pixels,double exposure) {
    if(stopped||t<=last_frame||vio->finished)return false;
    auto input=std::make_shared<basalt::OpticalFlowInput>();input->t_ns=t;input->img_data.resize(1);
    input->img_data[0].img.reset(new basalt::ManagedImage<uint16_t>(width,height));
    for(int y=0;y<height;y++)for(int x=0;x<width;x++)
      (*input->img_data[0].img)(x,y)=static_cast<uint16_t>(pixels[y*width+x])<<8;
    input->img_data[0].exposure=exposure;
    if(!flow->input_queue.try_push(input))return false;last_frame=t;return true;
  }
  void stop() {
    if(stopped)return;stopped=true;
    // Camera EOS first: an IMU queue can be full while the estimator waits
    // for its first image. Blocking on IMU EOS first would deadlock teardown.
    if(vio->finished)vio->drain_input_queues();
    flow->input_queue.push(nullptr);
    // The estimator can exit on camera EOS without consuming a full IMU queue.
    // Never block an EOS push while its consumer is about to exit.
    while(!vio->finished&&!vio->imu_data_queue.try_push(nullptr))
      std::this_thread::sleep_for(std::chrono::milliseconds(1));
    vio->maybe_join();vio->drain_input_queues();flow.reset();vio->drain_input_queues();
    if(state_thread.joinable())state_thread.join();
    if(visual_thread.joinable())visual_thread.join();
    vio.reset();
  }
  ~Engine(){stop();}
};
void fail(JNIEnv* env,const char* message){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),message);}
Engine* get(jlong h){return reinterpret_cast<Engine*>(h);}
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeVersion(JNIEnv* env,jclass) {
  return env->NewStringUTF("Basalt-0f3b2b52-float-mono-VIO-android");
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeStart(JNIEnv* env,jclass,jdoubleArray params,jlong first_time,jdoubleArray first_imu) {
  if(env->GetArrayLength(params)!=27||env->GetArrayLength(first_imu)!=6){fail(env,"Native parameter dimensions");return 0;}
  double p[27],x[6];env->GetDoubleArrayRegion(params,0,27,p);env->GetDoubleArrayRegion(first_imu,0,6,x);
  for(double v:p)if(!std::isfinite(v)){fail(env,"Nonfinite calibration");return 0;}
  for(double v:x)if(!std::isfinite(v)){fail(env,"Nonfinite initial IMU");return 0;}
  try{return reinterpret_cast<jlong>(new Engine(p,x,first_time));}
  catch(const std::exception& e){fail(env,e.what());return 0;}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeImu(JNIEnv* env,jclass,jlong handle,jlong time,jdoubleArray values) {
  if(!handle||env->GetArrayLength(values)!=6)return false;
  double x[6];env->GetDoubleArrayRegion(values,0,6,x);for(double v:x)if(!std::isfinite(v))return false;
  try{return get(handle)->push_imu(time,x);}
  catch(const std::exception& ex){fail(env,ex.what());return false;}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeFrame(JNIEnv* env,jclass,jlong handle,jlong time,jbyteArray pixels,jlong exposure) {
  // Zero is permitted for dataset replay with unavailable exposure metadata.
  // Live Camera2 ingestion separately rejects absent/nonpositive exposure.
  if(!handle||exposure<0)return false;auto* e=get(handle);
  if(env->GetArrayLength(pixels)!=e->width*e->height)return false;
  jbyte* p=env->GetByteArrayElements(pixels,nullptr);if(!p)return false;
  bool ok=false;
  try{ok=e->push_frame(time,reinterpret_cast<uint8_t*>(p),exposure*1e-9);}
  catch(const std::exception& ex){fail(env,ex.what());}
  env->ReleaseByteArrayElements(pixels,p,JNI_ABORT);return ok;
}
extern "C" JNIEXPORT jint JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativePoll(JNIEnv* env,jclass,jlong handle,jlongArray timestamps,jdoubleArray values) {
  if(!handle||env->GetArrayLength(timestamps)<1||env->GetArrayLength(values)<9)return 0;
  auto* e=get(handle);std::lock_guard<std::mutex> lock(e->mutex);if(e->time<=0)return 0;
  double data[9];std::copy(e->pose.begin(),e->pose.end(),data);data[7]=e->features;data[8]=e->landmarks;
  jlong t=e->time;env->SetLongArrayRegion(timestamps,0,1,&t);env->SetDoubleArrayRegion(values,0,9,data);
  return !e->vio->finished&&e->features>=30&&e->landmarks>=15&&std::abs(e->time-e->visual_time)<200000000?2:1;
}
extern "C" JNIEXPORT void JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeStop(JNIEnv*,jclass,jlong handle) {if(handle)delete get(handle);}
