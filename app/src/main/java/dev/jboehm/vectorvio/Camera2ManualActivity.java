package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Arrays;
import java.util.Locale;

/**
 * Separate camera-only, manually controlled optical experiment.
 *
 * Deliberately EXCLUSIVE from ARCore. Opens when MainActivity.onPause has
 * released its Camera2 device via ARCore pause. Does not fake ARCore tracking,
 * does not change existing GPX/odometers and is stopped on activity pause.
 * This is the genuinely available Camera2 shutter/ISO control path, while
 * ARCore SharedCamera forbids app repeating requests during tracking.
 */
public final class Camera2ManualActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final long[] exposureChoicesNs={
        33_333_333L,16_666_667L,8_333_333L,4_000_000L,
        2_000_000L,1_000_000L,500_000L};
    private TextureView preview;
    private TextView diagnostics;
    private TextView shutterLabel, isoLabel;
    private CameraManager manager;
    private HandlerThread worker;
    private Handler handler;
    private CameraDevice device;
    private CameraCaptureSession capture;
    private Surface output;
    private String cameraId;
    private Range<Long> shutterRange;
    private Range<Integer> isoRange;
    private long frameDuration=33_333_333L, desiredExposure=8_333_333L;
    private int desiredIso=400;
    private boolean foreground;
    private int captureGeneration=0;
    private SeekBar shutterBar,isoBar;
    private int isoProgress=40;
    private int actualWidth=1280,actualHeight=720;
    private final CameraExposureOptimizer optimizer=new CameraExposureOptimizer();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private boolean autoFast=true,manualCapable,opening;
    private long actualExposureNs=8_333_333L;
    private int actualIso=400;
    private int isoCeiling=1600,targetLuma=90;
    private Button autoButton;
    private TextView evidence,isoCeilingLabel,targetLabel;
    private SeekBar isoCeilingBar,targetBar;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private float ambientLux=Float.NaN;
    private final SensorEventListener lightListener=new SensorEventListener(){
        @Override public void onSensorChanged(SensorEvent e){
            if(e.values.length>0&&Float.isFinite(e.values[0]))ambientLux=e.values[0];
        }
        @Override public void onAccuracyChanged(Sensor sensor,int status){}
    };
    private final Runnable observePreview=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            if(capture!=null && preview!=null && preview.isAvailable()){
                Bitmap thumbnail=null;
                try{
                    thumbnail=preview.getBitmap(96,54);
                    if(thumbnail!=null){
                        int[] pixels=new int[96*54];
                        thumbnail.getPixels(pixels,0,96,0,0,96,54);
                        CameraImageQuality.Metrics m=CameraImageQuality.analyze(pixels,96,54);
                        CameraExposureOptimizer.Decision decision=null;
                        if(manualCapable&&autoFast&&shutterRange!=null&&isoRange!=null){
                            CameraExposureOptimizer.Limits bounds=new CameraExposureOptimizer.Limits(
                                shutterRange.getLower(),Math.min(shutterRange.getUpper(),frameDuration),
                                isoRange.getLower(),isoRange.getUpper());
                            decision=optimizer.update(SystemClock.elapsedRealtimeNanos(),m,
                                actualExposureNs,actualIso,bounds,isoCeiling,targetLuma);
                            if(decision.changed){
                                desiredExposure=decision.exposureNs;
                                desiredIso=decision.iso;
                                submitRequest();
                            }
                        }
                        String lux=Float.isFinite(ambientLux)?String.format(Locale.US,"%.0f lux",ambientLux):"lux sensor N/A";
                        String quality=m.lacksTexture()?"LOW TEXTURE":
                            m.dark()?"DARK":"IMAGE LIGHT OK?";
                        if(decision!=null)quality+=" · "+decision.status;
                        evidence.setText(String.format(Locale.US,
                            "FRAME BRIGHTNESS (ISP 0..255) P10 / MED / P90: %d / %d / %d"+
                            "\\nDark pixels: %.0f%% · clipped: %.0f%% · edges: %.1f%%"+
                            "\\n%s · ambient: %s · goal median: %d"+
                            "\\nNot physical lux, sensor noise or ARCore tracking quality.",
                            m.p10,m.p50,m.p90,100*m.darkFraction,100*m.clippedFraction,
                            100*m.edgeFraction,quality,lux,targetLuma));
                    }
                }catch(IllegalArgumentException|IllegalStateException e){
                    if(evidence!=null)evidence.setText("Preview brightness not available: "+e.getClass().getSimpleName());
                }finally{if(thumbnail!=null)thumbnail.recycle();}
            }
            if(foreground)ui.postDelayed(this,430);
        }
    };


    private static TextView text(Activity a,String content,int size){
        TextView t=new TextView(a);t.setText(content);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(14,12,14,12);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager!=null)lightSensor=sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        android.content.SharedPreferences prefs=getSharedPreferences("camera2_optical_lab",MODE_PRIVATE);
        autoFast=prefs.getBoolean("auto_fast",true);
        isoCeiling=prefs.getInt("iso_ceiling",1600);
        targetLuma=prefs.getInt("target_luma",90);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101820);
        root.addView(text(this,"CAMERA2 · MANUAL SENSOR LAB (NOT ARCore)",17));
        root.addView(text(this,"Shutter/ISO are controlled in this separate camera preview. ARCore / 3D GPX tracking is paused. No video, PDR pose or route data are modified.",12));
        preview=new TextureView(this);preview.setSurfaceTextureListener(this);
        root.addView(preview,new LinearLayout.LayoutParams(-1,0,1f));
        shutterLabel=text(this,"Shutter",14);root.addView(shutterLabel);
        shutterBar=new SeekBar(this);shutterBar.setMax(exposureChoicesNs.length-1);shutterBar.setProgress(2);
        root.addView(shutterBar);
        isoLabel=text(this,"ISO",14);root.addView(isoLabel);
        isoBar=new SeekBar(this);isoBar.setMax(100);isoBar.setProgress(isoProgress);
        root.addView(isoBar);
        autoButton=new Button(this);
        root.addView(autoButton);
        autoButton.setOnClickListener(v->{
            if(!manualCapable)return;
            autoFast=!autoFast;
            optimizer.reset();
            getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                .putBoolean("auto_fast",autoFast).apply();
            configureAdaptiveControls();
            if(!autoFast)submitRequest();
        });
        isoCeilingLabel=text(this,"Max acceptable ISO",13);
        root.addView(isoCeilingLabel);
        isoCeilingBar=new SeekBar(this);
        isoCeilingBar.setMax(100);
        root.addView(isoCeilingBar);
        targetLabel=text(this,"Target median brightness",13);
        root.addView(targetLabel);
        targetBar=new SeekBar(this);
        targetBar.setMax(95);
        targetBar.setProgress(Math.max(0,Math.min(95,targetLuma-55)));
        root.addView(targetBar);
        evidence=text(this,"Live scene illumination analysis starts with preview",12);
        root.addView(evidence);
        diagnostics=text(this,"Checking Camera2 manual sensor capability…",12);
        root.addView(diagnostics);
        TextView done=text(this,"← BACK TO ARCORE",15);root.addView(done);done.setOnClickListener(v->finish());
        shutterBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){
                desiredExposure=exposureChoicesNs[Math.max(0,Math.min(exposureChoicesNs.length-1,p))];
                if(user&&autoFast){autoFast=false;optimizer.reset();configureAdaptiveControls();}
                refreshLabels();if(user)submitRequest();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        isoBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){
                isoProgress=p;
                if(isoRange!=null)desiredIso=isoRange.getLower()+(int)Math.round(
                    (isoRange.getUpper()-isoRange.getLower())*(p/100.0));
                if(user&&autoFast){autoFast=false;optimizer.reset();configureAdaptiveControls();}
                refreshLabels();if(user)submitRequest();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        isoCeilingBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){
                int lo=isoRange==null?100:isoRange.getLower();
                int hi=isoRange==null?6400:isoRange.getUpper();
                // Log interpolation gives accurate low-ISO adjustment for image noise.
                double cap=Math.exp(Math.log(Math.max(1,lo))+
                    (Math.log(Math.max(hi,lo))/1.0-Math.log(Math.max(1,lo)))*p/100.0);
                isoCeiling=Math.max(lo,Math.min(hi,(int)Math.round(cap)));
                if(user){
                    optimizer.reset();
                    getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                        .putInt("iso_ceiling",isoCeiling).apply();
                }
                configureAdaptiveControls();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        targetBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){
                targetLuma=55+p;
                if(user){
                    optimizer.reset();
                    getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                        .putInt("target_luma",targetLuma).apply();
                }
                configureAdaptiveControls();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        setContentView(root);
        configureAdaptiveControls();
        refreshLabels();
    }
    private void refreshLabels(){
        if(shutterLabel!=null)shutterLabel.setText("Requested shutter: 1/"+Math.round(1e9/desiredExposure)+" s"+(shutterRange==null?"":" · hardware bounded"));
        if(isoLabel!=null)isoLabel.setText("Requested ISO "+desiredIso);
    }
    private void configureAdaptiveControls(){
        if(autoButton==null)return;
        autoButton.setText(!manualCapable?"SYSTEM AUTO EXPOSURE (hardware fallback)":
            autoFast?"AUTO FAST SHUTTER · ON":"AUTO FAST SHUTTER · OFF (MANUAL)");
        autoButton.setEnabled(manualCapable);
        if(shutterBar!=null)shutterBar.setEnabled(manualCapable&&!autoFast);
        if(isoBar!=null)isoBar.setEnabled(manualCapable&&!autoFast);
        if(isoCeilingBar!=null)isoCeilingBar.setEnabled(manualCapable&&autoFast);
        if(targetBar!=null)targetBar.setEnabled(manualCapable&&autoFast);
        if(isoCeilingLabel!=null)isoCeilingLabel.setText("ISO NOISE CEILING: "+isoCeiling);
        if(targetLabel!=null)targetLabel.setText("Brightness goal (preview median): "+targetLuma+" / 255");
    }
    @Override public void onResume(){
        super.onResume();foreground=true;optimizer.reset();
        worker=new HandlerThread("manual-camera2");worker.start();handler=new Handler(worker.getLooper());
        if(lightSensor!=null)try{
            sensorManager.registerListener(lightListener,lightSensor,SensorManager.SENSOR_DELAY_NORMAL);
        }catch(RuntimeException ignored){}
        ui.removeCallbacks(observePreview);ui.postDelayed(observePreview,700);
        if(preview!=null&&preview.isAvailable())openIfReady();
    }
    @Override public void onPause(){
        foreground=false;
        ui.removeCallbacks(observePreview);
        if(sensorManager!=null)sensorManager.unregisterListener(lightListener);
        synchronized(this){captureGeneration++;opening=false;if(capture!=null){capture.close();capture=null;}
            if(device!=null){device.close();device=null;}
            if(output!=null){output.release();output=null;}}
        if(worker!=null){worker.quitSafely();worker=null;handler=null;}
        super.onPause();
    }
    private String selectManualCamera() throws CameraAccessException {
        if(manager==null)return null;
        String fallback=null;
        for(String id:manager.getCameraIdList()){
            CameraCharacteristics c=manager.getCameraCharacteristics(id);
            Integer facing=c.get(CameraCharacteristics.LENS_FACING);
            if(facing==null||facing!=CameraCharacteristics.LENS_FACING_BACK)continue;
            int[] capabilities=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            boolean manual=false;
            if(capabilities!=null)for(int v:capabilities)
                if(v==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)manual=true;
            Range<Long> e=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            Range<Integer> gain=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            StreamConfigurationMap stream=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(stream==null)continue;
            if(fallback==null)fallback=id;
            if(!manual||e==null||gain==null)continue;
            manualCapable=true;shutterRange=e;isoRange=gain;
            Long maxFrame=c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION);
            frameDuration=Math.min(33_333_333L,maxFrame==null?33_333_333L:maxFrame);
            frameDuration=Math.max(frameDuration,Math.min(shutterRange.getUpper(),33_333_333L));
            Size[] options=stream.getOutputSizes(SurfaceTexture.class);
            if(options!=null&&options.length>0){
                Size choice=options[0];double diff=Double.POSITIVE_INFINITY;
                for(Size s:options){
                    if(s.getWidth()*s.getHeight()>2_073_600L)continue;
                    double d=Math.abs((double)s.getWidth()/s.getHeight()-16.0/9)
                        +Math.abs(s.getWidth()-1280)/1280.0;
                    if(d<diff){diff=d;choice=s;}
                }
                actualWidth=choice.getWidth();actualHeight=choice.getHeight();
            }
            return id;
        }
        // LIMITED/LEGACY phones can still preview using Android auto exposure.
        manualCapable=false;shutterRange=null;isoRange=null;
        return fallback;
    }
    private void openIfReady(){
        if(!foreground||!preview.isAvailable()||handler==null||opening||device!=null)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            diagnostics.setText("Grant camera permission in Android settings.");
            requestPermissions(new String[]{Manifest.permission.CAMERA},209);return;
        }
        try{
            cameraId=selectManualCamera();
            if(cameraId==null){
                diagnostics.setText("No usable rear camera found.");return;
            }
            if(manualCapable){
                desiredIso=Camera2ExposurePolicy.clampIso(desiredIso,isoRange.getLower(),isoRange.getUpper());
                isoCeiling=Math.max(isoRange.getLower(),Math.min(isoCeiling,isoRange.getUpper()));
                double fraction=(Math.log(isoCeiling)-Math.log(isoRange.getLower()))/
                    Math.max(.001,Math.log(isoRange.getUpper())-Math.log(isoRange.getLower()));
                isoCeilingBar.setProgress((int)Math.round(100*Math.max(0,Math.min(1,fraction))));
            }
            configureAdaptiveControls();
            final int generation=++captureGeneration;opening=true;
            diagnostics.setText(manualCapable?"Opening manual sensor "+cameraId+"...":
                "Manual ISO/shutter unsupported; native Camera2 AE fallback. Preview still measured.");
            manager.openCamera(cameraId,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){
                    if(!foreground||generation!=captureGeneration){c.close();return;}
                    synchronized(Camera2ManualActivity.this){device=c;opening=false;}
                    createSession(generation);
                }
                @Override public void onDisconnected(CameraDevice c){
                    c.close();opening=false;if(generation==captureGeneration)fail("Camera disconnected");
                }
                @Override public void onError(CameraDevice c,int code){
                    c.close();opening=false;if(generation==captureGeneration)fail("Camera2 open error "+code);
                }
            },handler);
        }catch(CameraAccessException|SecurityException ex){
            opening=false;fail("Camera2 unavailable: "+ex.getClass().getSimpleName());
        }
    }
    private void createSession(int generation){
        CameraDevice c=device;TextureView tv=preview;
        if(c==null||tv==null||!tv.isAvailable())return;
        SurfaceTexture t=tv.getSurfaceTexture();if(t==null)return;
        t.setDefaultBufferSize(actualWidth,actualHeight);
        Surface surface=new Surface(t);output=surface;
        try{
            c.createCaptureSession(Arrays.asList(surface),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession session){
                    if(!foreground||generation!=captureGeneration){session.close();return;}
                    synchronized(Camera2ManualActivity.this){capture=session;}
                    submitRequest();
                }
                @Override public void onConfigureFailed(CameraCaptureSession session){
                    if(generation==captureGeneration)fail("Camera2 session configuration failed");
                }
            },handler);
        }catch(CameraAccessException|IllegalArgumentException e){fail("Camera2 session failed: "+e.getClass().getSimpleName());}
    }
    private void submitRequest(){
        CameraDevice camera=device;CameraCaptureSession session=capture;
        Surface target=output;Handler work=handler;
        if(camera==null||session==null||target==null||work==null)return;
        long shutter=manualCapable?Camera2ExposurePolicy.clampShutter(desiredExposure,
            shutterRange.getLower(),shutterRange.getUpper(),frameDuration):0;
        int iso=manualCapable?Camera2ExposurePolicy.clampIso(desiredIso,isoRange.getLower(),isoRange.getUpper()):0;
        try{
            CaptureRequest.Builder req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(target);
            req.set(CaptureRequest.CONTROL_AE_MODE,manualCapable?
                CaptureRequest.CONTROL_AE_MODE_OFF:CaptureRequest.CONTROL_AE_MODE_ON);
            if(manualCapable){
                req.set(CaptureRequest.SENSOR_EXPOSURE_TIME,shutter);
                req.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
                req.set(CaptureRequest.SENSOR_FRAME_DURATION,frameDuration);
            }
            req.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            session.setRepeatingRequest(req.build(),new CameraCaptureSession.CaptureCallback(){
                private long lastUiMs;
                @Override public void onCaptureCompleted(CameraCaptureSession ses,
                    CaptureRequest request,TotalCaptureResult result){
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-lastUiMs<350)return;lastUiMs=now;
                    Long exposure=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer sensitivity=result.get(CaptureResult.SENSOR_SENSITIVITY);
                    if(exposure!=null&&exposure>0)actualExposureNs=exposure;
                    if(sensitivity!=null&&sensitivity>0)actualIso=sensitivity;
                    runOnUiThread(()->{
                        if(!foreground||diagnostics==null)return;
                        diagnostics.setText(String.format(Locale.US,
                            "LIVE hardware feedback • shutter: %s ms • ISO %s • %dx%d • AE %s",
                            exposure==null?"unknown":String.format(Locale.US,"%.3f",exposure/1e6),
                            sensitivity==null?"unknown":sensitivity.toString(),
                            actualWidth,actualHeight,manualCapable?"MANUAL":"SYSTEM AUTO"));
                    });
                }
            },work);
        }catch(CameraAccessException|IllegalStateException exception){
            fail("Camera2 request rejected: "+exception.getClass().getSimpleName());
        }
    }
    private void fail(String message){runOnUiThread(()->{if(diagnostics!=null)diagnostics.setText(message);});}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==209&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openIfReady();
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int width,int height){openIfReady();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){
        if(capture!=null){capture.close();capture=null;}
        if(device!=null){device.close();device=null;}
        if(output!=null){output.release();output=null;}
        return true;
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int width,int height){}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
}
