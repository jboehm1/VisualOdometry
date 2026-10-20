package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
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

    private static TextView text(Activity a,String content,int size){
        TextView t=new TextView(a);t.setText(content);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(14,12,14,12);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        manager=(CameraManager)getSystemService(CAMERA_SERVICE);
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
        diagnostics=text(this,"Checking Camera2 manual sensor capability…",12);
        root.addView(diagnostics);
        TextView done=text(this,"← BACK TO ARCORE",15);root.addView(done);done.setOnClickListener(v->finish());
        shutterBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){
                desiredExposure=exposureChoicesNs[Math.max(0,Math.min(exposureChoicesNs.length-1,p))];
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
                refreshLabels();if(user)submitRequest();
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        setContentView(root);
        refreshLabels();
    }
    private void refreshLabels(){
        if(shutterLabel!=null)shutterLabel.setText("Requested shutter: 1/"+Math.round(1e9/desiredExposure)+" s"+(shutterRange==null?"":" · hardware bounded"));
        if(isoLabel!=null)isoLabel.setText("Requested ISO "+desiredIso);
    }
    @Override public void onResume(){
        super.onResume();foreground=true;
        worker=new HandlerThread("manual-camera2");worker.start();handler=new Handler(worker.getLooper());
        if(preview!=null&&preview.isAvailable())openIfReady();
    }
    @Override public void onPause(){
        foreground=false;
        synchronized(this){captureGeneration++;if(capture!=null){capture.close();capture=null;}
            if(device!=null){device.close();device=null;}
            if(output!=null){output.release();output=null;}}
        if(worker!=null){worker.quitSafely();worker=null;handler=null;}
        super.onPause();
    }
    private String selectManualCamera() throws CameraAccessException {
        if(manager==null)return null;
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
            if(!manual||e==null||gain==null||stream==null)continue;
            shutterRange=e;isoRange=gain;
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
        return null;
    }
    private void openIfReady(){
        if(!foreground||!preview.isAvailable()||handler==null)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            diagnostics.setText("Grant camera permission in Android settings.");
            requestPermissions(new String[]{Manifest.permission.CAMERA},209);return;
        }
        try{
            cameraId=selectManualCamera();
            if(cameraId==null){
                diagnostics.setText("Manual SENSOR controls unsupported on available rear cameras. ARCore remains unchanged.");return;
            }
            desiredIso=Camera2ExposurePolicy.clampIso(desiredIso,isoRange.getLower(),isoRange.getUpper());
            final int generation=++captureGeneration;
            diagnostics.setText("Opening manual sensor "+cameraId+"...");
            manager.openCamera(cameraId,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice c){
                    if(!foreground||generation!=captureGeneration){c.close();return;}
                    synchronized(Camera2ManualActivity.this){device=c;}
                    createSession(generation);
                }
                @Override public void onDisconnected(CameraDevice c){c.close();if(generation==captureGeneration)fail("Camera disconnected");}
                @Override public void onError(CameraDevice c,int code){c.close();if(generation==captureGeneration)fail("Camera2 open error "+code);}
            },handler);
        }catch(CameraAccessException|SecurityException ex){fail("Manual camera unavailable: "+ex.getClass().getSimpleName());}
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
        if(camera==null||session==null||target==null||work==null||shutterRange==null||isoRange==null)return;
        long shutter=Camera2ExposurePolicy.clampShutter(desiredExposure,
            shutterRange.getLower(),shutterRange.getUpper(),frameDuration);
        int iso=Camera2ExposurePolicy.clampIso(desiredIso,isoRange.getLower(),isoRange.getUpper());
        try{
            CaptureRequest.Builder req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(target);
            req.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            req.set(CaptureRequest.SENSOR_EXPOSURE_TIME,shutter);
            req.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
            req.set(CaptureRequest.SENSOR_FRAME_DURATION,frameDuration);
            req.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            session.setRepeatingRequest(req.build(),new CameraCaptureSession.CaptureCallback(){
                private long lastUiMs;
                @Override public void onCaptureCompleted(CameraCaptureSession ses,
                    CaptureRequest request,TotalCaptureResult result){
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(now-lastUiMs<350)return;lastUiMs=now;
                    Long exposure=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                    Integer sensitivity=result.get(CaptureResult.SENSOR_SENSITIVITY);
                    runOnUiThread(()->{
                        if(!foreground||diagnostics==null)return;
                        diagnostics.setText(String.format(Locale.US,
                            "LIVE hardware feedback • shutter: %s ms • ISO %s • %dx%d • manual AE OFF",
                            exposure==null?"unknown":String.format(Locale.US,"%.3f",exposure/1e6),
                            sensitivity==null?"unknown":sensitivity.toString(),
                            actualWidth,actualHeight));
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
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){return true;}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int width,int height){}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
}
