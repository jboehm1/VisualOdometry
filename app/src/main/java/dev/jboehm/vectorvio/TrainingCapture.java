package dev.jboehm.vectorvio;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import com.google.ar.core.*;
import org.json.*;
import java.io.File;
import java.util.Locale;
/** ARCore session video with external diagnostic sidecars. Session operations: GL thread. */
final class TrainingCapture {
    private final CsvRecorder recorder;private final Context context;
    private File csv;private String part;private long lastFrame=-1;
    volatile String status="VIDEO OFF";volatile boolean active;
    TrainingCapture(Context context,CsvRecorder recorder){this.context=context;this.recorder=recorder;}
    void start(Session session,File csv){
        if(active)return;this.csv=csv;if(csv==null)return;
        if(csv.getParentFile().getUsableSpace()<64L*1024*1024)throw new IllegalStateException("Not enough storage for video");
        String stem=SessionBundle.stem(csv);int number=1;File video;
        do{video=new File(csv.getParentFile(),String.format(Locale.US,"%s_part%03d.mp4",stem,number++));}while(video.exists());
        try{
            session.startRecording(new RecordingConfig(session).setMp4DatasetUri(Uri.fromFile(video)).setAutoStopOnPause(true));
            active=true;part=video.getName();lastFrame=-1;status="VIDEO + IMU REC";
            recorder.event("VIDEO_START_"+part,System.currentTimeMillis());
            JSONObject meta=new JSONObject();meta.put("format","VECTOR dataset v1");meta.put("device",Build.MANUFACTURER+" "+Build.MODEL);meta.put("android",Build.VERSION.RELEASE);meta.put("app_version","0.10.0-alpha");
            meta.put("video_format","ARCore MP4 dataset, CPU camera stream + internal sensors, no microphone");meta.put("accelerometer_units","m/s^2 including gravity");meta.put("gyro_units","rad/s");meta.put("imu_axes","Android SensorManager device axes");meta.put("pose_reference","ARCore estimated local world, not ground truth");meta.put("camera_imu_extrinsic",JSONObject.NULL);meta.put("camera_imu_time_offset_ns",JSONObject.NULL);meta.put("rolling_shutter_readout_ns",JSONObject.NULL);meta.put("time_contract","frame_time_ns=ARCore; image_time_ns=CPU Image.timestamp if available; sensor_time_ns=SensorEvent.timestamp; elapsed_realtime_ns=Android boot clock; synchronization not calibrated");
            JSONArray sensors=new JSONArray();SensorManager sm=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);for(int type:new int[]{Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE}){Sensor sensor=sm.getDefaultSensor(type);if(sensor!=null){JSONObject item=new JSONObject();item.put("type",type);item.put("name",sensor.getName());item.put("vendor",sensor.getVendor());item.put("resolution",sensor.getResolution());item.put("maximum_range",sensor.getMaximumRange());sensors.put(item);}}meta.put("sensors",sensors);recorder.metadata(csv,meta.toString(2));
        }catch(Exception e){if(active)try{session.stopRecording();}catch(Exception ignored){}active=false;status="VIDEO ERROR";recorder.event("VIDEO_ERROR_"+e.getClass().getSimpleName(),System.currentTimeMillis());throw new IllegalStateException("Video capture failed: "+e.getClass().getSimpleName(),e);}
    }
    void stop(Session session){
        if(!active)return;
        try{session.stopRecording();status="VIDEO SAVED";recorder.event("VIDEO_STOP_"+part,System.currentTimeMillis());}
        catch(Exception e){status="VIDEO STOP ERROR";recorder.event("VIDEO_STOP_ERROR",System.currentTimeMillis());}
        finally{active=false;}
    }
    void frame(Session session,Frame frame){
        if(!active||frame.getTimestamp()==0||frame.getTimestamp()==lastFrame)return;
        if(session.getRecordingStatus()!=RecordingStatus.OK){active=false;status="VIDEO ERROR";recorder.event("VIDEO_STATUS_ERROR",System.currentTimeMillis());return;}
        lastFrame=frame.getTimestamp();long imageTime=-1;
        try(Image image=frame.acquireCameraImage()){imageTime=image.getTimestamp();}catch(Exception ignored){}
        Camera camera=frame.getCamera();CameraIntrinsics k=camera.getImageIntrinsics();float[] f=k.getFocalLength(),c=k.getPrincipalPoint();int[] size=k.getImageDimensions();Pose pose=camera.getPose();float[] t=pose.getTranslation(),q=pose.getRotationQuaternion();
        final long frameNs=lastFrame,imageNs=imageTime,elapsedNs=SystemClock.elapsedRealtimeNanos(),wallMs=System.currentTimeMillis();
        final String videoPart=part,tracking=camera.getTrackingState().toString();
        recorder.sidecar(csv,"_frames.csv","part,frame_time_ns,image_time_ns,elapsed_realtime_ns,wall_time_ms,tracking,width,height,fx,fy,cx,cy,x_m,y_m,z_m,qx,qy,qz,qw",()->String.format(Locale.US,"%s,%d,%d,%d,%d,%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.7f,%.7f,%.7f,%.7f",videoPart,frameNs,imageNs,elapsedNs,wallMs,tracking,size[0],size[1],f[0],f[1],c[0],c[1],t[0],t[1],t[2],q[0],q[1],q[2],q[3]));
    }
}
