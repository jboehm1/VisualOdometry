package dev.jboehm.vectorvio.nativevio;

import android.app.Activity;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.os.SystemClock;
import dev.jboehm.vectorvio.AppIdentity;
import dev.jboehm.vectorvio.Odometer;
import dev.jboehm.vectorvio.metrics.SessionTelemetry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.JSONObject;

/** One foreground run; all input and close calls belong to the Camera2 worker.
 * Raw captures/sensors are separate from derived poses. Images are not persisted. */
public final class CustomVioSession implements AutoCloseable {
    private static final class Pixels {
        final byte[] y;final long time;Pixels(long t,byte[] y){time=t;this.y=y;}
    }
    private final FactoryCameraCalibration calibration;
    private final BasaltBridge bridge=new BasaltBridge();
    private final ImuSynchronizer sync;
    private final Odometer odometer=new Odometer();
    private final TreeMap<Long,Pixels> images=new TreeMap<>();
    private final TreeMap<Long,CaptureResult> results=new TreeMap<>();
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private static final class Pending {
        final Pixels pixels;final long mid,exposure;Pending(Pixels p,long t,long e){pixels=p;mid=t;exposure=e;}
    }
    public final File directory;
    private final BufferedWriter raw,paired,captures,poses;
    private SessionTelemetry metrics;
    private final ArrayList<BufferedWriter> ownedWriters=new ArrayList<>();
    private final JSONObject metadata;
    private long lastPair,lastPose,firstPair,frames,drops,poseRows;
    private int lastQuality;
    private boolean closed,failed;
    private volatile String status="Waiting for synchronized camera + IMU";
    private final long started=SystemClock.elapsedRealtimeNanos();
    public CustomVioSession(Activity activity,FactoryCameraCalibration calibration)throws Exception {
        this.calibration=calibration;
        if(BasaltBridge.unavailableReason()!=null)throw new IllegalStateException(BasaltBridge.unavailableReason());
        directory=new File(activity.getFilesDir(),"custom-vio/"+System.currentTimeMillis()+"-"+UUID.randomUUID().toString().substring(0,8));
        if(!directory.mkdirs())throw new IOException("Cannot create session directory");
        try {
        raw=writer("imu_raw.csv","sensor,time_ns,x,y,z,accuracy");
        paired=writer("imu_interpolated.csv","time_ns,ax,ay,az,gx,gy,gz");
        captures=writer("camera.csv","start_ns,mid_row_mid_exposure_ns,exposure_ns,iso,frame_duration_ns,rolling_shutter_skew_ns,status");
        poses=writer("poses.csv","time_ns,received_elapsed_ns,x,y,z,qx,qy,qz,qw,observed_features,landmarks,quality_heuristic,distance_m,odometer_result");
        metadata=new JSONObject().put("schema",1).put("engine",BasaltBridge.version())
            .put("started_elapsed_ns",started)
            .put("source_commit",AppIdentity.sourceCommit(activity)).put("version",AppIdentity.version(activity))
            .put("calibration",calibration.provenance).put("verified_calibration",false)
            .put("qualification","EXPERIMENTAL_FACTORY_BOOTSTRAP_NOT_DEVICE_VALIDATED")
            .put("images_recorded",false).put("arcore_trajectory_used",false)
            .put("coordinate_frame","camera optical GL pose in local Y-up world; metres, xyzw quaternion")
            .put("pose_quality","Heuristic: >=30 observed features and >=15 landmarks, state/visual difference <200ms; not optimizer inlier count or covariance")
            .put("clock","BOOTTIME; frame start + half exposure + half rolling skew; assumed zero camera/IMU offset")
            .put("imu_pairing","Real gyro timestamps, linearly bracketed acceleration, maximum acceleration gap 30ms; Android sensor axes")
            .put("limits","No rolling-shutter motion compensation, lens distortion calibration, measured IMU noise or independent phone accuracy validation.");
        writeMetadata();
        metrics=new SessionTelemetry(activity,new File(directory,"session"),"BASALT_CUSTOM_FACTORY_EXPERIMENTAL",metadata.toString(),activity.getWindow());
        metrics.resume();odometer.start(SystemClock.elapsedRealtime());
        sync=new ImuSynchronizer(this::pair);
        }catch(Exception e){
            if(metrics!=null)metrics.close();
            for(BufferedWriter writer:ownedWriters)try{writer.close();}catch(IOException ignored){}
            throw e;
        }
    }
    private BufferedWriter writer(String name,String header)throws IOException {
        BufferedWriter w=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(directory,name)),StandardCharsets.UTF_8),32768);
        ownedWriters.add(w);
        w.write(header);w.newLine();return w;
    }
    private static String csv(double[] x){StringBuilder b=new StringBuilder();for(double v:x)b.append(',').append(v);return b.toString();}
    private void pair(long t,double[] x){
        if(closed||failed)return;
        try {
            paired.write(t+csv(x)+"\n");
            if(lastPair>0&&t-lastPair>30_000_000L){fail("IMU gap >30ms; run stopped, reopen custom mode");return;}
            if(!bridge.running()){bridge.start(calibration.parameters(),t,x);firstPair=t;}
            else if(!bridge.imu(t,x)){fail("Native IMU queue rejected sample; run stopped");return;}
            lastPair=t;drainFrames();
        }catch(Exception e){fail("IMU: "+e.getMessage());}
    }
    public void sensor(SensorEvent event){
        if(closed||failed||event.values.length<3)return;
        int type=event.sensor.getType();if(type!=Sensor.TYPE_ACCELEROMETER&&type!=Sensor.TYPE_GYROSCOPE)return;
        metrics.sensor(type==Sensor.TYPE_ACCELEROMETER?"ACCEL":"GYRO",event.timestamp);
        try {raw.write(type+","+event.timestamp+","+event.values[0]+","+event.values[1]+","+event.values[2]+","+event.accuracy+"\n");
            sync.add(type==Sensor.TYPE_ACCELEROMETER,event.timestamp,event.values[0],event.values[1],event.values[2]);
        }catch(IOException e){fail("Raw sensor storage failed");}
    }
    /** Copies only the actual Y plane with row/pixel strides; caller always closes Image. */
    public void image(Image image){
        if(closed||failed)return;
        metrics.count("camera_y_images_received");
        if(image.getWidth()!=calibration.width||image.getHeight()!=calibration.height){fail("Image dimensions differ from factory bootstrap");return;}
        Rect crop=image.getCropRect();
        if(crop.left!=0||crop.top!=0||crop.width()!=calibration.width||crop.height()!=calibration.height){fail("Unexpected image crop");return;}
        Image.Plane plane=image.getPlanes()[0];
        byte[] y=GrayPlane.copy(plane.getBuffer(),calibration.width,calibration.height,plane.getRowStride(),plane.getPixelStride());
        images.put(image.getTimestamp(),new Pixels(image.getTimestamp(),y));join();
    }
    public void result(CaptureResult result){
        if(closed||failed)return;
        Long time=result.get(CaptureResult.SENSOR_TIMESTAMP);if(time==null)return;
        metrics.count("camera_capture_results_received");
        // Denominator includes results whose pixels/IMU will be dropped later,
        // just as baseline telemetry includes every distinct ARCore frame.
        metrics.frame(time,lastQuality==2&&lastPose>0&&time>=lastPose&&time-lastPose<500_000_000L,"INITIALIZING_OR_STALE");
        results.put(time,result);join();
    }
    private void join(){
        for(Iterator<Map.Entry<Long,Pixels>> it=images.entrySet().iterator();it.hasNext();){
            Map.Entry<Long,Pixels> e=it.next();CaptureResult r=results.remove(e.getKey());if(r==null)continue;
            Pixels p=e.getValue();it.remove();
            Long exposure=r.get(CaptureResult.SENSOR_EXPOSURE_TIME),duration=r.get(CaptureResult.SENSOR_FRAME_DURATION),skew=r.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
            Integer iso=r.get(CaptureResult.SENSOR_SENSITIVITY);
            Rect crop=r.get(CaptureResult.SCALER_CROP_REGION);Float focus=r.get(CaptureResult.LENS_FOCUS_DISTANCE);
            Integer ois=r.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE),video=r.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE);
            String reason="MATCHED";
            if(exposure==null||duration==null||skew==null||iso==null||exposure<=0||duration<exposure||skew<0||skew>duration)reason="INVALID_OR_MISSING_TIMING";
            else if(crop==null||focus==null||ois==null||video==null||crop.left!=calibration.crop[0]||crop.top!=calibration.crop[1]
                ||crop.width()!=calibration.crop[2]||crop.height()!=calibration.crop[3]
                ||Math.abs(focus-calibration.focus)>.01||ois!=calibration.ois||video!=calibration.videoStabilization)reason="GEOMETRY_CHANGED";
            long mid="MATCHED".equals(reason)?p.time+exposure/2+skew/2:-1;
            try{captures.write(p.time+","+mid+","+exposure+","+iso+","+duration+","+skew+","+reason+"\n");}
            catch(IOException err){fail("Capture storage failed");return;}
            if(mid>0){pending.addLast(new Pending(p,mid,exposure));}else{drops++;status=reason;}
        }
        while(images.size()>8){images.pollFirstEntry();drops++;metrics.count("unmatched_images_dropped");}
        while(results.size()>16){results.pollFirstEntry();drops++;metrics.count("unmatched_metadata_dropped");}
        while(pending.size()>8){pending.removeFirst();drops++;metrics.count("imu_wait_frames_dropped");}
        drainFrames();
    }
    private void drainFrames(){
        while(!pending.isEmpty()&&bridge.running()&&pending.peekFirst().mid<lastPair){
            Pending p=pending.removeFirst();
            if(p.mid<=firstPair||lastPair-p.mid>100_000_000L){drops++;continue;}
            long start=SystemClock.elapsedRealtimeNanos();
            if(!bridge.frame(p.mid,p.pixels.y,p.exposure)){drops++;metrics.count("native_image_queue_dropped");continue;}
            frames++;metrics.count("native_frames_enqueued");metrics.duration("native_frame_enqueue",SystemClock.elapsedRealtimeNanos()-start);
            poll(p.mid);
            if(start-started>1_800_000_000_000L){fail("30 minute recording limit; reopen to start a fresh run");return;}
        }
    }
    private void poll(long frameTime){
        long[] time=new long[1];double[] values=new double[9];int quality=bridge.poll(time,values);
        lastQuality=quality;
        boolean fresh=quality==2&&time[0]<=frameTime&&frameTime-time[0]<500_000_000L;
        if(!fresh)odometer.lostTracking();
        if(time[0]>lastPose){
            lastPose=time[0];Odometer.Result result=fresh?odometer.onPose(values[0],values[1],values[2],time[0]):Odometer.Result.INACTIVE;
            try{poses.write(time[0]+","+SystemClock.elapsedRealtimeNanos()+csv(values)+","+quality+","+odometer.snapshot(SystemClock.elapsedRealtime()).totalMeters+","+result+"\n");poseRows++;}
            catch(IOException e){fail("Pose storage failed");return;}
            metrics.count("pose_exports");
        }
        status=String.format(Locale.US,"BASALT · %s · %.2f m\n%d frames · %d poses · %d drops · %d IMU pairs\nFactory bootstrap UNVERIFIED · features %.0f · landmarks %.0f",
            fresh?"visual state available":"initializing / weak or stale vision",odometer.snapshot(SystemClock.elapsedRealtime()).totalMeters,frames,poseRows,drops,sync.paired,values[7],values[8]);
    }
    private void fail(String message){
        failed=true;status="STOPPED: "+message;odometer.lostTracking();metrics.count("fatal_acquisition_errors");metrics.pause();
        bridge.close();
    }
    public String status(){return status;}
    private void writeMetadata()throws Exception {
        try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(directory,"custom_metadata.json")),StandardCharsets.UTF_8)){w.write(metadata.toString(2));}
    }
    @Override public void close(){
        if(closed)return;closed=true;odometer.pause(SystemClock.elapsedRealtime());metrics.close();
        try{bridge.close();}catch(Exception e){status="Native shutdown failed: "+e.getMessage();}
        for(BufferedWriter w:new BufferedWriter[]{raw,paired,captures,poses})try{w.close();}catch(IOException ignored){}
        images.clear();results.clear();pending.clear();
        try{metadata.put("finished_elapsed_ns",SystemClock.elapsedRealtimeNanos()).put("frames_enqueued",frames)
            .put("pose_rows",poseRows).put("drops",drops).put("imu_pairs",sync.paired).put("imu_pairing_drops",sync.dropped)
            .put("status",status).put("failed",failed);writeMetadata();}catch(Exception ignored){}
    }
    public static void export(File directory,OutputStream output,String opticalCsv)throws IOException {
        final long start,end;
        try{
            JSONObject meta=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(directory,"custom_metadata.json").toPath()),StandardCharsets.UTF_8));
            start=meta.getLong("started_elapsed_ns");end=meta.getLong("finished_elapsed_ns");
        }catch(Exception e){throw new IOException("Session must be stopped before export",e);}
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            File[] files=directory.listFiles();if(files==null)throw new IOException("Session unavailable");
            byte[] buffer=new byte[16384];
            for(File f:files){if(!f.isFile())continue;zip.putNextEntry(new ZipEntry(f.getName()));
                try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();}
            zip.putNextEntry(new ZipEntry("optical_quality.csv"));
            String[] lines=opticalCsv.split("\n");
            if(lines.length>0)zip.write((lines[0]+"\n").getBytes(StandardCharsets.UTF_8));
            for(int i=1;i<lines.length;i++){
                int comma=lines[i].indexOf(',');if(comma<=0)continue;
                try{long time=Long.parseLong(lines[i].substring(0,comma));
                    if(time>=start&&time<=end)zip.write((lines[i]+"\n").getBytes(StandardCharsets.UTF_8));
                }catch(NumberFormatException ignored){}
            }
            zip.closeEntry();
        }
    }
}
