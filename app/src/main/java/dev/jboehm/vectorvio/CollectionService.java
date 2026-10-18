package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.hardware.*;
import android.hardware.camera2.*;
import android.location.*;
import android.media.MediaRecorder;
import android.os.*;
import android.util.*;
import android.view.Surface;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Independent Camera2 capture; never starts ARCore or computes a map/odometer. */
public final class CollectionService extends Service implements SensorEventListener2,LocationListener {
    static volatile boolean running;
    static volatile String status="Stopped";
    static volatile File currentDirectory;
    static final String STOP="dev.jboehm.vectorvio.COLLECTION_STOP";
    private HandlerThread cameraThread,dataThread;private Handler cameraHandler,dataHandler;
    private CameraDevice camera;private CameraCaptureSession capture;private MediaRecorder video;
    private SensorManager sensors;private LocationManager locations;private PowerManager.WakeLock wake;
    private BufferedWriter imu,gps,frames,events;
    private volatile boolean stopping;private volatile boolean videoStarted;private int part,bitrate,width,height;private String partName;
    private CaptureProfile profile=CaptureProfile.FULL;
    private Range<Integer> fpsRange;
    private int effectiveFps;
    private final ArrayList<Sensor> registeredSensors=new ArrayList<>();
    private final HashSet<Integer> pendingFlush=new HashSet<>();
    private volatile long stopAtNs=Long.MAX_VALUE;
    private boolean logsClosed;
    private String stopReason;
    private int batteryStart;
    private int generation;private long imuCount,frameCount,gpsCount,startNs;
    private static final int NOTIFICATION=31;
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopCapture("USER_STOP");return START_NOT_STICKY;}
        if(running||stopping)return START_NOT_STICKY;
        currentDirectory=null;
        try{
            boolean locationAllowed=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            locations=(LocationManager)getSystemService(LOCATION_SERVICE);
            boolean useLocation=locationAllowed&&(locations.isProviderEnabled(LocationManager.GPS_PROVIDER)||locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("collection","Trip data capture",NotificationManager.IMPORTANCE_LOW));
            Intent stop=new Intent(this,CollectionService.class).setAction(STOP);
            PendingIntent stopIntent=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            PendingIntent open=PendingIntent.getActivity(this,1,new Intent(this,CollectionActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification notification=new Notification.Builder(this,"collection").setSmallIcon(android.R.drawable.presence_video_online).setContentTitle("VECTOR · camera + IMU capture").setContentText("Recording on this phone; screen may be switched off").setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,"STOP",stopIntent).build()).build();
            if(Build.VERSION.SDK_INT>=29){int types=useLocation?ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION:0;if(Build.VERSION.SDK_INT>=30)types|=ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;startForeground(NOTIFICATION,notification,types);}else startForeground(NOTIFICATION,notification);
            cameraThread=new HandlerThread("CollectorCamera");cameraThread.start();cameraHandler=new Handler(cameraThread.getLooper());
            dataThread=new HandlerThread("CollectorData");dataThread.start();dataHandler=new Handler(dataThread.getLooper());
            running=true;status="Starting camera…";startNs=SystemClock.elapsedRealtimeNanos();
            profile=CaptureProfile.from(intent==null?null:intent.getStringExtra("profile"));
            // Keep compatibility with older controllers.
            if(intent!=null&&intent.getBooleanExtra("high",false))profile=CaptureProfile.DETAIL;
            width=profile.width;height=profile.height;bitrate=profile.bitrate;
            batteryStart=((BatteryManager)getSystemService(BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            File root=new File(getFilesDir(),"collections");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create capture folder");
            if(root.getUsableSpace()<256L*1024*1024)throw new IOException("Need at least 256 MiB free");
            SimpleDateFormat format=new SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
            currentDirectory=new File(root,"capture_"+format.format(new Date()));if(!currentDirectory.mkdir())throw new IOException("Cannot create capture session");
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"VECTOR:TripCapture");wake.acquire(24L*60*60*1000);
            final boolean gpsEnabled=useLocation;
            dataHandler.post(()->{try{
                imu=CollectionFiles.log(currentDirectory,"imu.csv.gz","sensor_time_ns,type,x,y,z");
                gps=CollectionFiles.log(currentDirectory,"gps.csv.gz","wall_ms,elapsed_ns,provider,latitude,longitude,altitude_m,accuracy_m,speed_mps,bearing_deg");
                frames=CollectionFiles.log(currentDirectory,"frames.csv.gz","part,frame_number,sensor_time_ns,elapsed_ns,exposure_ns,rolling_shutter_ns,iso,focal_mm");
                events=CollectionFiles.log(currentDirectory,"events.csv.gz","elapsed_ns,wall_ms,event");event("START");
                sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
                for(int type:new int[]{Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE}){Sensor sensor=sensors.getDefaultSensor(type);if(sensor!=null){
                    int batch=profile.batchingUs(sensor.getFifoMaxEventCount());
                    if(sensors.registerListener(this,sensor,profile.imuPeriodUs,batch,dataHandler))registeredSensors.add(sensor);
                    else event("SENSOR_REGISTER_FAILED_"+type);
                    event("SENSOR_"+type+"_FIFO_"+sensor.getFifoMaxEventCount()+"_BATCH_US_"+batch);
                }else event("MISSING_SENSOR_"+type);}
                if(gpsEnabled)startLocation();else event("GPS_UNAVAILABLE_OR_PERMISSION_DENIED");
                dataHandler.postDelayed(maintenance,5000);cameraHandler.post(this::openCamera);
            }catch(Exception e){fail(e);}});
        }catch(Exception e){fail(e);}
        return START_NOT_STICKY;
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void startLocation(){
        try{boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            String provider=fine&&locations.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
            if(locations.isProviderEnabled(provider))locations.requestLocationUpdates(provider,profile.gpsPeriodMs,0,this,dataThread.getLooper());else event("GPS_PROVIDER_UNAVAILABLE");
        }catch(Exception e){event("GPS_ERROR_"+e.getClass().getSimpleName());}
    }
    @android.annotation.SuppressLint("MissingPermission")
    private void openCamera(){
        if(stopping)return;
        try{
            CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);String id=null;CameraCharacteristics characteristics=null;
            for(String candidate:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(candidate);Integer lens=c.get(CameraCharacteristics.LENS_FACING);if(lens!=null&&lens==CameraCharacteristics.LENS_FACING_BACK){id=candidate;characteristics=c;break;}}
            if(id==null)throw new IOException("No rear camera");
            Size[] supported=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(MediaRecorder.class);boolean found=false;
            for(Size s:supported)if(s.getWidth()==width&&s.getHeight()==height)found=true;
            if(!found)throw new IOException("Selected video size unsupported; try the other profile");
            Range<Integer>[] ranges=characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            int[][] advertised=ranges==null?null:new int[ranges.length][2];
            if(ranges!=null)for(int n=0;n<ranges.length;n++){advertised[n][0]=ranges[n].getLower();advertised[n][1]=ranges[n].getUpper();}
            int chosen=CaptureProfile.selectFpsRange(advertised,profile.fps,profile==CaptureProfile.ULTRA);
            fpsRange=chosen<0?null:ranges[chosen];
            if(fpsRange==null)throw new IOException("Selected camera FPS unsupported; choose Complete profile");
            effectiveFps=fpsRange.getUpper();
            JSONObject metadata=new JSONObject();metadata.put("format","VECTOR capture-only v1");metadata.put("camera_id",id);metadata.put("device",Build.MANUFACTURER+" "+Build.MODEL);metadata.put("width",width);metadata.put("height",height);metadata.put("target_fps",effectiveFps);metadata.put("requested_fps",profile.fps);metadata.put("fps_fallback",effectiveFps!=profile.fps);metadata.put("profile",profile.id);metadata.put("ae_fps_range",fpsRange.toString());metadata.put("imu_max_batch_us",250000);metadata.put("log_flush_seconds",5);metadata.put("target_bitrate",bitrate);metadata.put("imu_requested_hz",200);metadata.put("camera_timestamp_source",characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE));metadata.put("sensor_orientation",characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
            metadata.put("intrinsic_calibration",array(characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)));metadata.put("lens_distortion",Build.VERSION.SDK_INT>=28?array(characteristics.get(CameraCharacteristics.LENS_DISTORTION)):JSONObject.NULL);metadata.put("camera_imu_extrinsic",JSONObject.NULL);metadata.put("camera_imu_time_offset_ns",JSONObject.NULL);
            metadata.put("notes","No ARCore, map or pose. Android device-axis IMU; accelerometer includes gravity. Camera frame timestamps are Camera2 sensor times, not explicitly linked to decoded MP4 frame PTS. Stabilization disabled when supported; AE/AF automatic. Intrinsics may require crop/scale correction. Timestamp source, rolling shutter and unknown extrinsics/time offset must be checked before training. Video compression is lossy. Segment boundaries can have video gaps; sensor logs continue. Screen-off operation requires field verification on this device.");
            int orientation=characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            try(FileOutputStream out=new FileOutputStream(new File(currentDirectory,"metadata.json"))){out.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));}
            manager.openCamera(id,new CameraDevice.StateCallback(){public void onOpened(CameraDevice c){if(stopping){c.close();return;}camera=c;startPart(orientation);}public void onDisconnected(CameraDevice c){c.close();fail(new IOException("Camera disconnected"));}public void onError(CameraDevice c,int error){c.close();fail(new IOException("Camera error "+error));}},cameraHandler);
        }catch(Exception e){fail(e);}
    }
    private static Object array(float[] values)throws JSONException{if(values==null)return JSONObject.NULL;JSONArray a=new JSONArray();for(float v:values)a.put((double)v);return a;}
    private void startPart(int orientation){
        if(stopping)return;
        try{
            final int token=++generation;partName=String.format(Locale.US,"part%04d.mp4",++part);final String name=partName;
            video=new MediaRecorder();video.setVideoSource(MediaRecorder.VideoSource.SURFACE);video.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);video.setVideoEncoder(MediaRecorder.VideoEncoder.H264);video.setVideoSize(width,height);video.setVideoFrameRate(effectiveFps);video.setVideoEncodingBitRate(bitrate);video.setOrientationHint(orientation);video.setOutputFile(new File(currentDirectory,name).getAbsolutePath());video.setMaxDuration(10*60*1000);video.setMaxFileSize(256L*1024*1024);
            video.setOnErrorListener((r,what,extra)->fail(new IOException("Encoder error "+what+"/"+extra)));
            video.setOnInfoListener((r,what,extra)->{if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED||what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED)cameraHandler.post(()->{if(!stopping&&token==generation){closePart();startPart(orientation);}});});
            video.prepare();Surface target=video.getSurface();
            camera.createCaptureSession(Collections.singletonList(target),new CameraCaptureSession.StateCallback(){
                public void onConfigured(CameraCaptureSession session){if(stopping||token!=generation){session.close();return;}capture=session;try{
                    CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);request.addTarget(target);request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRange);request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
                    capture.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback(){@Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest request,TotalCaptureResult result){if(stopping||token!=generation)return;long elapsed=SystemClock.elapsedRealtimeNanos();String row=name+","+result.getFrameNumber()+","+value(result.get(CaptureResult.SENSOR_TIMESTAMP))+","+elapsed+","+value(result.get(CaptureResult.SENSOR_EXPOSURE_TIME))+","+value(result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW))+","+value(result.get(CaptureResult.SENSOR_SENSITIVITY))+","+value(result.get(CaptureResult.LENS_FOCAL_LENGTH));dataHandler.post(()->{if(!stopping){write(frames,row);frameCount++;}});}},cameraHandler);
                    video.start();videoStarted=true;dataHandler.post(()->event("VIDEO_START_"+name));status="REC · "+width+"×"+height+" · camera target "+effectiveFps+" FPS"+(effectiveFps!=profile.fps?" (fallback from "+profile.fps+")":"")+" · IMU 200 Hz requested";
                }catch(Exception e){fail(e);}}
                public void onConfigureFailed(CameraCaptureSession s){s.close();fail(new IOException("Camera session configuration failed"));}
            },cameraHandler);
        }catch(Exception e){fail(e);}
    }
    private static String value(Object value){return value==null?"":value.toString();}
    private void closePart(){
        if(capture!=null){try{capture.stopRepeating();}catch(Exception ignored){}capture.close();capture=null;}
        if(video!=null){if(videoStarted)try{video.stop();}catch(Exception e){final String name=partName;dataHandler.post(()->event("VIDEO_FINALIZE_ERROR_"+name));}try{video.reset();}catch(Exception ignored){}try{video.release();}catch(Exception ignored){}video=null;videoStarted=false;}
        final String name=partName;if(name!=null)dataHandler.post(()->event("VIDEO_END_"+name));
    }
    private final Runnable maintenance=new Runnable(){public void run(){if(stopping)return;try{for(BufferedWriter w:new BufferedWriter[]{imu,gps,frames,events})if(w!=null)w.flush();if(currentDirectory.getUsableSpace()<128L*1024*1024){stopCapture("LOW_STORAGE");return;}long seconds=(SystemClock.elapsedRealtimeNanos()-startNs)/1_000_000_000L;if(seconds>=24*60*60){stopCapture("24_HOUR_LIMIT");return;}status=(videoStarted?"REC · ":"STARTING CAMERA · ")+seconds+" s · "+frameCount+" camera frames · "+imuCount+" IMU · "+gpsCount+" GPS";dataHandler.postDelayed(this,5000);}catch(Exception e){fail(e);}}};
    private void write(BufferedWriter writer,String row){if(writer==null||logsClosed)return;try{writer.write(row);writer.newLine();}catch(Exception e){fail(e);}}
    private void event(String message){write(events,SystemClock.elapsedRealtimeNanos()+","+System.currentTimeMillis()+","+message);}
    private void fail(Exception e){android.util.Log.e("VectorCollect","Capture failed",e);status="ERROR: "+e.getMessage();stopCapture("ERROR_"+e.getClass().getSimpleName());}
    private synchronized void stopCapture(String reason){
        if(stopping)return;stopAtNs=SystemClock.elapsedRealtimeNanos();stopping=true;stopReason=reason;if(!status.startsWith("ERROR"))status="Finalizing · "+reason;
        Runnable close=()->{try{closePart();if(camera!=null){camera.close();camera=null;}}finally{
            Runnable finish=()->{
                if(locations!=null)try{locations.removeUpdates(this);}catch(Exception ignored){}
                if(sensors!=null&&!registeredSensors.isEmpty()){
                    for(Sensor sensor:registeredSensors)pendingFlush.add(sensor.getType());
                    boolean flushing=false;try{flushing=sensors.flush(this);}catch(RuntimeException e){event("IMU_FLUSH_ERROR_"+e.getClass().getSimpleName());}
                    if(flushing){
                        dataHandler.postDelayed(()->{if(!logsClosed){event("IMU_FLUSH_TIMEOUT_POSSIBLE_TAIL_LOSS");finishLogs();}},2000);
                        return;
                    }
                    event("IMU_FLUSH_UNAVAILABLE_POSSIBLE_TAIL_LOSS");
                }
                finishLogs();
            };
            if(dataHandler!=null)dataHandler.post(finish);else finish.run();}};
        if(cameraHandler!=null)cameraHandler.post(close);else close.run();
    }
    @Override public void onFlushCompleted(Sensor sensor){
        pendingFlush.remove(sensor.getType());
        if(stopping&&pendingFlush.isEmpty()&&!logsClosed)finishLogs();
    }
    private void finishLogs(){
        if(logsClosed)return;
        if(sensors!=null)try{sensors.unregisterListener(this);}catch(RuntimeException ignored){}
        try{
            event(stopReason);
            if(currentDirectory!=null){JSONObject summary=new JSONObject();summary.put("stop_reason",stopReason);summary.put("profile",profile.id);summary.put("imu_samples",imuCount);summary.put("camera_callbacks",frameCount);summary.put("gps_fixes",gpsCount);summary.put("duration_ns",stopAtNs-startNs);summary.put("battery_start_percent",batteryStart);summary.put("battery_end_percent",((BatteryManager)getSystemService(BATTERY_SERVICE)).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
                try(FileOutputStream out=new FileOutputStream(new File(currentDirectory,"summary.json"))){out.write(summary.toString(2).getBytes(StandardCharsets.UTF_8));}}
        }catch(Exception ignored){}
        for(BufferedWriter w:new BufferedWriter[]{imu,gps,frames,events})if(w!=null)try{w.close();}catch(Exception ignored){}
        logsClosed=true;
        if(!status.startsWith("ERROR"))status="Stopped · "+stopReason;
        if(wake!=null&&wake.isHeld())wake.release();
        new Handler(Looper.getMainLooper()).post(()->{stopForeground(true);running=false;stopSelf();});
        if(cameraThread!=null)cameraThread.quitSafely();if(dataThread!=null)dataThread.quitSafely();
    }
    @Override public void onSensorChanged(SensorEvent e){if(!logsClosed&&(!stopping||e.timestamp<=stopAtNs)){write(imu,e.timestamp+","+e.sensor.getType()+","+e.values[0]+","+e.values[1]+","+e.values[2]);imuCount++;}}
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override public void onLocationChanged(Location l){if(!stopping){write(gps,l.getTime()+","+l.getElapsedRealtimeNanos()+","+l.getProvider()+","+l.getLatitude()+","+l.getLongitude()+","+(l.hasAltitude()?l.getAltitude():"")+","+(l.hasAccuracy()?l.getAccuracy():"")+","+(l.hasSpeed()?l.getSpeed():"")+","+(l.hasBearing()?l.getBearing():""));gpsCount++;}}
    @Override public void onProviderDisabled(String provider){event("LOCATION_DISABLED_"+provider);}
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String provider,int value,Bundle extras){}
    @Override public void onDestroy(){stopCapture("SERVICE_DESTROYED");super.onDestroy();}
}
