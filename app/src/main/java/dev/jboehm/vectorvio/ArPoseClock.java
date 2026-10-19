package dev.jboehm.vectorvio;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import com.google.ar.core.Frame;

/** GPS pairing must use Android camera time with a declared REALTIME source.
 * Frame.getTimestamp() has no specified timebase and is deliberately not used here. */
final class ArPoseClock {
    private boolean realtime;
    void configure(Context context,String cameraId){
        realtime=false;
        try{
            CameraManager manager=(CameraManager)context.getSystemService(Context.CAMERA_SERVICE);
            Integer source=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
            realtime=source!=null&&source==CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME;
        }catch(Exception unavailable){realtime=false;}
    }
    long elapsedRealtimeNs(Frame frame){
        if(!realtime)return 0;
        try{return frame.getAndroidCameraTimestamp();}catch(RuntimeException unavailable){return 0;}
    }
}
