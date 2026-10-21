#include <jni.h>
#include <basalt/vi_estimator/vio_estimator.h>

extern "C" JNIEXPORT jstring JNICALL
Java_dev_jboehm_vectorvio_nativevio_BasaltBridge_nativeVersion(JNIEnv* env,jclass) {
  return env->NewStringUTF("Basalt-0f3b2b52-float-android; no live adapter yet");
}
