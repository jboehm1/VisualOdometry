package dev.jboehm.vectorvio;

import android.Manifest;
import android.content.Intent;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import android.widget.ScrollView;
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
import android.media.ImageReader;
import android.media.Image;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import dev.jboehm.vectorvio.nativevio.FactoryCameraCalibration;
import dev.jboehm.vectorvio.nativevio.CustomVioSession;

/**
 * Separate camera-only, manually controlled optical experiment.
 *
 * Deliberately EXCLUSIVE from ARCore. Opens when MainActivity.onPause has
 * released its Camera2 device via ARCore pause. Does not fake ARCore tracking,
 * does not change existing GPX/odometers and is stopped on activity pause.
 * This is the genuinely available Camera2 shutter/ISO control path, while
 * This exclusive session owns its repeating requests independently of ARCore.
 */
public final class Camera2ManualActivity extends Activity implements TextureView.SurfaceTextureListener {
    public static final String EXTRA_CUSTOM_VIO="custom_vio";
    private boolean customMode;
    private FactoryCameraCalibration factoryCalibration;
    private ImageReader imageReader;
    private volatile CustomVioSession customSession;
    private volatile java.io.File lastCustomDirectory;
    private java.io.File pendingCustomExport;
    private boolean recoverPendingExport;
    private TextView customStatus;
    private Sensor accelSensor;
    private static final int EXPORT_CUSTOM_ZIP=221;
    private SensorEventListener customImuListener;
    private volatile java.util.concurrent.CompletableFuture<Void> customClosing=java.util.concurrent.CompletableFuture.completedFuture(null);
    private final Runnable updateCustomStatus=new Runnable(){
        @Override public void run(){
            if(!foreground||!customMode)return;
            CustomVioSession s=customSession;
            if(s!=null&&customStatus!=null)customStatus.setText(s.status());
            ui.postDelayed(this,500);
        }
    };
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
    private volatile boolean foreground;
    private volatile int captureGeneration=0;
    private SeekBar shutterBar,isoBar;
    private int isoProgress=40;
    private int actualWidth=1280,actualHeight=720;
    private static final int EXPORT_OPTICAL_CSV=220;
    private static final int MAX_SAMPLES=20000; // <=~40 min bike at 8 Hz; bounded memory
    private final StringBuilder opticalCsv=new StringBuilder(
        "time_elapsed_ns,profile,auto,ambient_lux,p10,p50,p90,dark_fraction,clipped_fraction,"+
        "edge_fraction,coherent_edge_fraction,feature_median,gyro_rad_s,actual_exposure_ns,actual_iso,shutter_cap_ns,"+
        "selected_exposure_ns,selected_iso,iso_ceiling,quality_status,"+
        "exposure_method,region_gradient_score,gradient_slope_ev,gradient_step_ev,"+
        "optic_flow_px_s_full,optic_flow_inliers,optic_flow_matches\n");
    private int opticalRows;
    private final CameraExposureOptimizer optimizer=new CameraExposureOptimizer();
    private final RegionGradientExposureController gradientController=new RegionGradientExposureController();
    private boolean useRegionGradient=true;
    private int[] lastCameraThumbnail;
    private long lastCameraThumbnailNs;
    private Button gradientButton;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private boolean autoFast=true,manualCapable,opening;
    private long actualExposureNs=8_333_333L;
    private int actualIso=400;
    private int isoCeiling=1600,targetLuma=90;
    private Button autoButton,motionButton;
    private CameraExposureOptimizer.MotionMode motionMode=CameraExposureOptimizer.MotionMode.WALK;
    private Sensor gyroSensor;
    private volatile double gyroSpeedRadS=Double.NaN;
    private volatile long lastGyroNs=Long.MIN_VALUE;
    private final SensorEventListener gyroListener=new SensorEventListener(){
        @Override public void onSensorChanged(SensorEvent e){
            if(e==null||e.values.length<3)return;
            double w=Math.sqrt((double)e.values[0]*e.values[0]+
                (double)e.values[1]*e.values[1]+(double)e.values[2]*e.values[2]);
            if(!Double.isFinite(w))return;
            double prev=gyroSpeedRadS;
            gyroSpeedRadS=Double.isFinite(prev)?prev*.60+w*.40:w;
            lastGyroNs=e.timestamp;
        }
        @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    };
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
                    thumbnail=preview.getBitmap(160,90);
                    if(thumbnail!=null){
                        int[] pixels=new int[160*90];
                        thumbnail.getPixels(pixels,0,160,0,0,160,90);
                        CameraImageQuality.Metrics m=CameraImageQuality.analyze(pixels,160,90);
                        CameraExposureOptimizer.Decision decision=null;
                        long nowNs=SystemClock.elapsedRealtimeNanos();
                        boolean gyroFresh=lastGyroNs>0&&nowNs>=lastGyroNs
                            &&nowNs-lastGyroNs<=500_000_000L;
                        double w=gyroFresh?gyroSpeedRadS:Double.NaN;
                        CameraPreviewFlow.Result flow=CameraPreviewFlow.estimate(
                            lastCameraThumbnail,pixels,160,90,
                            lastCameraThumbnailNs>0&&nowNs>lastCameraThumbnailNs
                                &&nowNs-lastCameraThumbnailNs<1_200_000_000L?
                                    nowNs-lastCameraThumbnailNs:-1L,
                            actualWidth/160.0);
                        lastCameraThumbnail=pixels;
                        lastCameraThumbnailNs=nowNs;
                        RegionWeightedGradient.Result gradient=useRegionGradient?
                            RegionWeightedGradient.evaluate(pixels,160,90):null;
                        RegionGradientExposureController.Decision regionDecision=null;
                        if(manualCapable&&autoFast&&shutterRange!=null&&isoRange!=null){
                            CameraExposureOptimizer.Limits bounds=new CameraExposureOptimizer.Limits(
                                shutterRange.getLower(),Math.min(shutterRange.getUpper(),frameDuration),
                                isoRange.getLower(),isoRange.getUpper());
                            if(useRegionGradient){
                                regionDecision=gradientController.update(nowNs,gradient,m,
                                    actualExposureNs,actualIso,bounds,isoCeiling,w,motionMode,flow,
                                    targetLuma);
                                if(regionDecision.changed){
                                    desiredExposure=regionDecision.exposureNs;
                                    desiredIso=regionDecision.iso;
                                    submitRequest();
                                }
                            }else{
                                decision=optimizer.updateMotionAware(nowNs,m,
                                    actualExposureNs,actualIso,bounds,isoCeiling,targetLuma,
                                    w,motionMode);
                                if(decision.changed){
                                    desiredExposure=decision.exposureNs;
                                    desiredIso=decision.iso;
                                    submitRequest();
                                }
                            }
                        }
                        if(opticalRows<MAX_SAMPLES){
                            opticalCsv.append(String.format(Locale.US,
                                "%d,%s,%b,%s,%d,%d,%d,%.5f,%.5f,%.5f,%.5f,%d,%s,%d,%d,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%d,%d\n",
                                nowNs,motionMode,autoFast,
                                Float.isFinite(ambientLux)?Float.toString(ambientLux):"",
                                m.p10,m.p50,m.p90,m.darkFraction,m.clippedFraction,m.edgeFraction,
                                m.coherentEdgeFraction,m.featureMedian,
                                Double.isFinite(w)?Double.toString(w):"",
                                actualExposureNs,actualIso,
                                regionDecision!=null?regionDecision.shutterCapNs:
                                    decision==null?0L:decision.shutterCeilingNs,
                                regionDecision!=null?regionDecision.exposureNs:
                                    decision==null?desiredExposure:decision.exposureNs,
                                regionDecision!=null?regionDecision.iso:
                                    decision==null?desiredIso:decision.iso,
                                isoCeiling,regionDecision!=null?regionDecision.status.replace(',',' '):
                                    decision==null?"MANUAL_OR_SYSTEM_AE":decision.status.replace(',',' '),
                                autoFast?(useRegionGradient?"REGION_GRADIENT":"P50_BASELINE"):"MANUAL",
                                gradient==null?"":Double.toString(gradient.quality),
                                gradient==null?"":Double.toString(gradient.derivativePerEv),
                                regionDecision==null?"":Double.toString(regionDecision.stepEv),
                                flow.reliable?Double.toString(flow.fullFramePixelsPerSecond):"",
                                flow.inliers,flow.candidates));
                            opticalRows++;
                        }
                        String lux=Float.isFinite(ambientLux)?String.format(Locale.US,"%.0f lux",ambientLux):"lux sensor N/A";
                        String quality=m.lacksTexture()?"LOW TEXTURE":
                            m.dark()?"DARK":"IMAGE LIGHT OK?";
                        quality+=String.format(Locale.US,
                            " · textured P50 %d · coherent gradients %.1f%%",
                            m.featureMedian,100*m.coherentEdgeFraction);
                        if(regionDecision!=null){
                            quality+=String.format(Locale.US,
                                " · %s · Q %.1f · dQ/dEV %+.1f · step %+.2fEV",
                                regionDecision.status,regionDecision.gradientScore,
                                regionDecision.gradientSlope,regionDecision.stepEv);
                            quality+=String.format(Locale.US," · blur cap 1/%.0fs",
                                1e9/regionDecision.shutterCapNs);
                        }
                        if(flow.reliable){
                            quality+=String.format(Locale.US," · optical flow %.0f px/s (%d matches)",
                                flow.fullFramePixelsPerSecond,flow.inliers);
                        }else quality+=" · optical flow unavailable/ambiguous";
                        if(decision!=null){
                            quality+=" · "+decision.status;
                            quality+=String.format(Locale.US,
                                " · gyro %s · shutter cap 1/%.0f s · angular smear %s",
                                Double.isFinite(w)?String.format(Locale.US,"%.0f deg/s",Math.toDegrees(w)):"N/A",
                                1e9/decision.shutterCeilingNs,
                                Double.isFinite(decision.predictedAngularBlurDeg)?
                                    String.format(Locale.US,"%.2f deg",decision.predictedAngularBlurDeg):"N/A");
                        }
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
            // At bike speed a 430ms interval often exceeds ±14px patch search.
            // Sample more frequently in BIKE mode, but hardware requests still
            // obey independent 650ms photometric settling limits.
            if(foreground)ui.postDelayed(this,
                motionMode==CameraExposureOptimizer.MotionMode.BIKE?120:250);
        }
    };


    private static TextView text(Activity a,String content,int size){
        TextView t=new TextView(a);t.setText(content);t.setTextSize(size);
        t.setTextColor(Color.WHITE);t.setPadding(14,12,14,12);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        customMode=getIntent().getBooleanExtra(EXTRA_CUSTOM_VIO,false);
        if(customMode)factoryCalibration=FactoryCameraCalibration.load(this);
        if(state!=null){
            String name=state.getString("basalt_export_directory");
            if(name!=null&&name.matches("[0-9]{10,17}-[0-9a-f]{8}"))pendingCustomExport=new java.io.File(new java.io.File(getFilesDir(),"custom-vio"),name);
            recoverPendingExport=state.getBoolean("basalt_export_recovery",false);
        }
        manager=(CameraManager)getSystemService(CAMERA_SERVICE);
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        if(sensorManager!=null){
            lightSensor=sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroSensor=sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            accelSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
        android.content.SharedPreferences prefs=getSharedPreferences("camera2_optical_lab",MODE_PRIVATE);
        autoFast=prefs.getBoolean("auto_fast",true);
        isoCeiling=prefs.getInt("iso_ceiling",1600);
        targetLuma=prefs.getInt("target_luma",90);
        useRegionGradient=prefs.getBoolean("region_gradient",true);
        motionMode="BIKE".equals(prefs.getString("motion_mode","WALK"))?
            CameraExposureOptimizer.MotionMode.BIKE:CameraExposureOptimizer.MotionMode.WALK;
        ScrollView scroller=new ScrollView(this);scroller.setFillViewport(false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101820);
        root.addView(text(this,customMode?"VIO CUSTOM · BASALT · EXPERIMENTAL":"CAMERA2 · MANUAL SENSOR LAB (NOT ARCore)",17));
        root.addView(text(this,customMode?
            "Independent image + IMU estimator. ARCore is paused. Factory calibration bootstrap is UNVERIFIED; distance is experimental. No images are saved.":
            "Shutter/ISO are controlled in this separate camera preview. ARCore / 3D GPX tracking is paused. No video, PDR pose or route data are modified.",12));
        if(customMode){
            customStatus=text(this,factoryCalibration==null?
                "No factory bootstrap. Return to ARCore, select a fixed-focus camera profile and wait for tracking. Optional metadata may be unavailable on this phone.":
                "Ready to attempt Basalt using factory geometry. Keep phone still initially, then move slowly with visible texture. Calibration and accuracy remain unverified.",13);
            root.addView(customStatus);
            Button startCustom=new Button(this);startCustom.setText("START NEW BASALT RUN");root.addView(startCustom);
            startCustom.setOnClickListener(v->{Handler h=handler;if(h==null)return;h.post(()->{
                if(!foreground||handler!=h||customSession!=null)return;
                if(!customClosing.isDone()){runOnUiThread(()->customStatus.setText("Closing previous run; please retry shortly"));return;}
                if(factoryCalibration==null||capture==null||gyroSensor==null||accelSensor==null){runOnUiThread(()->customStatus.setText("Camera, bootstrap, gyroscope and accelerometer required"));return;}
                try{
                    CustomVioSession s=new CustomVioSession(this,factoryCalibration);lastCustomDirectory=s.directory;
                    if(foreground&&handler==h)customSession=s;else s.close();
                }
                catch(Exception e){runOnUiThread(()->customStatus.setText("Basalt unavailable: "+e.getMessage()));}
            });});
            Button stopCustom=new Button(this);stopCustom.setText("STOP + EXPORT BASALT RUN (ZIP)");root.addView(stopCustom);
            stopCustom.setOnClickListener(v->{Handler h=handler;if(h==null)return;final String optical=opticalCsv.toString();h.post(()->{
                if(!foreground||handler!=h)return;
                closeCustomSession(optical);java.io.File dir=lastCustomDirectory;
                runOnUiThread(()->{
                    if(dir==null){customStatus.setText("No Basalt recording yet");return;}
                    customStatus.setText("Run stopped; ZIP includes poses, raw/interpolated IMU, capture metadata and resources.");
                    launchCustomExport(dir,false);
                });
            });});
            Button history=new Button(this);history.setTag("basalt_history");history.setText("EXPORT PREVIOUS / INTERRUPTED BASALT RUN");root.addView(history);
            history.setOnClickListener(v->customHistory());
        }
        preview=new TextureView(this);preview.setSurfaceTextureListener(this);
        // Keep preview visible even as diagnostics/ISO controls grow on small screens.
        root.addView(preview,new LinearLayout.LayoutParams(-1,Math.round(250*getResources().getDisplayMetrics().density)));
        shutterLabel=text(this,"Shutter",14);root.addView(shutterLabel);
        shutterBar=new SeekBar(this);shutterBar.setMax(exposureChoicesNs.length-1);shutterBar.setProgress(2);
        root.addView(shutterBar);
        isoLabel=text(this,"ISO",14);root.addView(isoLabel);
        isoBar=new SeekBar(this);isoBar.setMax(100);isoBar.setProgress(isoProgress);
        root.addView(isoBar);
        autoButton=new Button(this);
        root.addView(autoButton);
        gradientButton=new Button(this);
        root.addView(gradientButton);
        gradientButton.setOnClickListener(v->{
            useRegionGradient=!useRegionGradient;
            optimizer.reset();gradientController.reset();
            lastCameraThumbnail=null;lastCameraThumbnailNs=0;
            getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                .putBoolean("region_gradient",useRegionGradient).apply();
            configureAdaptiveControls();
        });
        motionButton=new Button(this);
        root.addView(motionButton);
        motionButton.setOnClickListener(v->{
            motionMode=motionMode==CameraExposureOptimizer.MotionMode.BIKE?
                CameraExposureOptimizer.MotionMode.WALK:CameraExposureOptimizer.MotionMode.BIKE;
            optimizer.reset();gradientController.reset();
            getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                .putString("motion_mode",motionMode.name()).apply();
            configureAdaptiveControls();
        });
        autoButton.setOnClickListener(v->{
            if(!manualCapable)return;
            autoFast=!autoFast;
            optimizer.reset();gradientController.reset();
            getSharedPreferences("camera2_optical_lab",MODE_PRIVATE).edit()
                .putBoolean("auto_fast",autoFast).apply();
            configureAdaptiveControls();
            if(!autoFast){
                desiredExposure=exposureChoicesNs[shutterBar.getProgress()];
                if(isoRange!=null)desiredIso=isoRange.getLower()+(int)Math.round(
                    (isoRange.getUpper()-isoRange.getLower())*(isoBar.getProgress()/100.0));
                submitRequest();
            }
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
        Button exportOptical=new Button(this);
        exportOptical.setText("EXPORT CAMERA2 QUALITY TRACE (CSV)");
        root.addView(exportOptical);
        exportOptical.setOnClickListener(v->{
            if(opticalRows==0){
                android.widget.Toast.makeText(this,"No optical samples yet",android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_TITLE,"vector-camera2-fast-exposure-"+
                System.currentTimeMillis()+".csv");
            try{startActivityForResult(intent,EXPORT_OPTICAL_CSV);}
            catch(Exception e){fail("Export document picker unavailable");}
        });
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
        scroller.addView(root);setContentView(scroller);
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
        if(gradientButton!=null){
            gradientButton.setText(useRegionGradient?
                "EXPOSURE: REGION GRADIENT (EXPERIMENTAL)":
                "EXPOSURE: P50/GYRO BASELINE");
            gradientButton.setEnabled(manualCapable&&autoFast);
        }
        if(motionButton!=null){
            motionButton.setEnabled(manualCapable&&autoFast);
            motionButton.setText("Motion / blur profile: "+motionMode.name()+
                (gyroSensor==null?" (gyro unavailable)":" (gyro active)"));
        }
        if(shutterBar!=null)shutterBar.setEnabled(manualCapable&&!autoFast);
        if(isoBar!=null)isoBar.setEnabled(manualCapable&&!autoFast);
        if(isoCeilingBar!=null)isoCeilingBar.setEnabled(manualCapable&&autoFast);
        if(targetBar!=null)targetBar.setEnabled(manualCapable&&autoFast);
        if(isoCeilingLabel!=null)isoCeilingLabel.setText("ISO NOISE CEILING: "+isoCeiling);
        if(targetLabel!=null)targetLabel.setText("Brightness goal (preview median): "+targetLuma+" / 255");
    }
    @Override public void onResume(){
        super.onResume();foreground=true;optimizer.reset();gradientController.reset();
        lastCameraThumbnail=null;lastCameraThumbnailNs=0;
        worker=new HandlerThread("manual-camera2");worker.start();handler=new Handler(worker.getLooper());
        if(customMode){
            final Handler owner=handler;
            customImuListener=new SensorEventListener(){
                @Override public void onSensorChanged(SensorEvent event){
                    CustomVioSession s=customSession;
                    if(foreground&&handler==owner&&s!=null)s.sensor(event);
                }
                @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
            };
            ui.post(updateCustomStatus);
            if(accelSensor!=null)sensorManager.registerListener(customImuListener,accelSensor,5000,0,handler);
            if(gyroSensor!=null)sensorManager.registerListener(customImuListener,gyroSensor,5000,0,handler);
        }
        if(lightSensor!=null)try{
            sensorManager.registerListener(lightListener,lightSensor,SensorManager.SENSOR_DELAY_NORMAL);
        }catch(RuntimeException ignored){}
        gyroSpeedRadS=Double.NaN;lastGyroNs=Long.MIN_VALUE;
        if(gyroSensor!=null)try{
            sensorManager.registerListener(gyroListener,gyroSensor,SensorManager.SENSOR_DELAY_GAME);
        }catch(RuntimeException ignored){}
        ui.removeCallbacks(observePreview);ui.postDelayed(observePreview,700);
        if(preview!=null&&preview.isAvailable())openIfReady();
    }
    @Override public void onPause(){
        foreground=false;
        ui.removeCallbacks(observePreview);
        ui.removeCallbacks(updateCustomStatus);
        lastCameraThumbnail=null;lastCameraThumbnailNs=0;
        if(sensorManager!=null){
            sensorManager.unregisterListener(lightListener);
            sensorManager.unregisterListener(gyroListener);
            if(customImuListener!=null)sensorManager.unregisterListener(customImuListener);
        }
        synchronized(this){captureGeneration++;opening=false;if(capture!=null){capture.close();capture=null;}
            if(device!=null){device.close();device=null;}
            if(output!=null){output.release();output=null;}}
        stopCustomAsync();
        if(worker!=null){worker.quitSafely();worker=null;handler=null;}
        super.onPause();
    }
    private String selectManualCamera() throws CameraAccessException {
        if(manager==null)return null;
        if(customMode){
            if(factoryCalibration==null)return null;
            CameraCharacteristics c=manager.getCameraCharacteristics(factoryCalibration.cameraId);
            Integer clock=c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE);
            if(clock==null||clock!=CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME){fail("Basalt requires a REALTIME camera clock");return null;}
            StreamConfigurationMap stream=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size expected=new Size(factoryCalibration.width,factoryCalibration.height);
            Size[] yuv=stream==null?null:stream.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] texture=stream==null?null:stream.getOutputSizes(SurfaceTexture.class);
            if(yuv==null||texture==null||!Arrays.asList(yuv).contains(expected)||!Arrays.asList(texture).contains(expected)){
                fail("Factory CPU image dimensions are not available through Camera2");return null;
            }
            shutterRange=c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            isoRange=c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            int[] caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            manualCapable=false;if(caps!=null)for(int cap:caps)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)manualCapable=true;
            manualCapable=manualCapable&&shutterRange!=null&&isoRange!=null;
            actualWidth=expected.getWidth();actualHeight=expected.getHeight();
            return factoryCalibration.cameraId;
        }
        String fallback=null;
        StreamConfigurationMap fallbackStream=null;
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
            Size[] previewOptions=stream.getOutputSizes(SurfaceTexture.class);
            if(previewOptions==null||previewOptions.length==0)continue;
            if(fallback==null){fallback=id;fallbackStream=stream;}
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
        if(fallbackStream!=null){
            Size[] sizes=fallbackStream.getOutputSizes(SurfaceTexture.class);
            if(sizes!=null&&sizes.length>0){
                Size nearest=sizes[0];double score=Double.POSITIVE_INFINITY;
                for(Size candidate:sizes){
                    if((long)candidate.getWidth()*candidate.getHeight()>2_100_000L)continue;
                    double cost=Math.abs((double)candidate.getWidth()/candidate.getHeight()-16.0/9)
                        +Math.abs(candidate.getWidth()-1280)/1280.0;
                    if(cost<score){score=cost;nearest=candidate;}
                }
                actualWidth=nearest.getWidth();actualHeight=nearest.getHeight();
            }
        }
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
                if(!customMode)diagnostics.setText("No usable rear camera found.");return;
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
        }catch(CameraAccessException|SecurityException|IllegalArgumentException ex){
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
            java.util.List<Surface> surfaces=new java.util.ArrayList<>();surfaces.add(surface);
            if(customMode){
                imageReader=ImageReader.newInstance(actualWidth,actualHeight,ImageFormat.YUV_420_888,3);
                imageReader.setOnImageAvailableListener(reader->{
                    try(Image image=reader.acquireLatestImage()){
                        CustomVioSession s=customSession;
                        if(image!=null&&s!=null&&foreground&&generation==captureGeneration)s.image(image);
                    }catch(IllegalStateException|IllegalArgumentException error){fail("Raw image acquisition failed: "+error.getMessage());}
                },handler);
                surfaces.add(imageReader.getSurface());
            }
            c.createCaptureSession(surfaces,new CameraCaptureSession.StateCallback(){
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
        if(!foreground||camera==null||session==null||target==null||work==null)return;
        long shutter=manualCapable?Camera2ExposurePolicy.clampShutter(desiredExposure,
            shutterRange.getLower(),shutterRange.getUpper(),frameDuration):0;
        int iso=manualCapable?Camera2ExposurePolicy.clampIso(desiredIso,isoRange.getLower(),isoRange.getUpper()):0;
        try{
            CaptureRequest.Builder req=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            req.addTarget(target);
            if(customMode&&imageReader!=null)req.addTarget(imageReader.getSurface());
            req.set(CaptureRequest.CONTROL_AE_MODE,manualCapable?
                CaptureRequest.CONTROL_AE_MODE_OFF:CaptureRequest.CONTROL_AE_MODE_ON);
            if(manualCapable){
                req.set(CaptureRequest.SENSOR_EXPOSURE_TIME,shutter);
                req.set(CaptureRequest.SENSOR_SENSITIVITY,iso);
                req.set(CaptureRequest.SENSOR_FRAME_DURATION,frameDuration);
            }
            if(customMode){
                req.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);
                req.set(CaptureRequest.LENS_FOCUS_DISTANCE,factoryCalibration.focus);
                req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,factoryCalibration.ois);
                req.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,factoryCalibration.videoStabilization);
                int[] crop=factoryCalibration.crop;
                req.set(CaptureRequest.SCALER_CROP_REGION,new Rect(crop[0],crop[1],crop[0]+crop[2],crop[1]+crop[3]));
            }else req.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            session.setRepeatingRequest(req.build(),new CameraCaptureSession.CaptureCallback(){
                private long lastUiMs;
                @Override public void onCaptureCompleted(CameraCaptureSession ses,
                    CaptureRequest request,TotalCaptureResult result){
                    if(!foreground||ses!=capture)return;
                    CustomVioSession nativeSession=customSession;
                    if(nativeSession!=null&&foreground)nativeSession.result(result);
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
        }catch(CameraAccessException|IllegalStateException|IllegalArgumentException exception){
            if(manualCapable){
                // Some phones falsely advertise manual control or reject requests
                // at runtime. Keep preview usable with native system AE.
                manualCapable=false;optimizer.reset();
                runOnUiThread(()->{
                    configureAdaptiveControls();
                    if(diagnostics!=null)diagnostics.setText(
                        "Manual request unsupported; automatic Camera2 AE fallback");
                });
                submitRequest();
            }else fail("Camera2 AE failed: "+exception.getClass().getSimpleName());
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==EXPORT_CUSTOM_ZIP&&result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingCustomExport!=null){
            final java.io.File dir=pendingCustomExport;final android.net.Uri uri=data.getData();final boolean recover=recoverPendingExport;
            new Thread(()->{
                try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                    if(stream==null)throw new java.io.IOException("No output stream");
                    CustomVioSession.export(dir,stream,null,recover);
                    runOnUiThread(()->android.widget.Toast.makeText(this,"Basalt session exported",android.widget.Toast.LENGTH_LONG).show());
                }catch(Exception e){fail("Basalt ZIP export failed: "+e.getMessage());}
            },"basalt-export").start();return;
        }
        if(request!=EXPORT_OPTICAL_CSV||result!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
            if(stream==null)throw new java.io.IOException("No output stream");
            stream.write(opticalCsv.toString().getBytes(StandardCharsets.UTF_8));
            android.widget.Toast.makeText(this,
                "Exported "+opticalRows+" camera-only samples (no images / GPS)",
                android.widget.Toast.LENGTH_LONG).show();
        }catch(Exception e){fail("Optical CSV export failed: "+e.getClass().getSimpleName());}
    }
    private void fail(String message){runOnUiThread(()->{if(diagnostics!=null)diagnostics.setText(message);});}
    private void closeCustomSession(String optical){
        CustomVioSession s=customSession;customSession=null;
        if(s!=null){s.close();try{CustomVioSession.persistOptical(s.directory,optical);}catch(java.io.IOException e){fail("Optical trace persistence failed: "+e.getMessage());}}
    }
    private void launchCustomExport(java.io.File directory,boolean recover){
        if(!foreground||isFinishing())return;
        pendingCustomExport=directory;recoverPendingExport=recover;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"vector-basalt-"+directory.getName()+".zip");
        try{startActivityForResult(intent,EXPORT_CUSTOM_ZIP);}catch(Exception e){fail("Export picker unavailable");}
    }
    private void customHistory(){
        if(customSession!=null||!customClosing.isDone()){
            customStatus.setText("Stop the active recording before exporting history");return;
        }
        java.io.File root=new java.io.File(getFilesDir(),"custom-vio");
        java.io.File[] runs=root.listFiles(f->f.isDirectory()&&new java.io.File(f,"custom_metadata.json").isFile());
        if(runs==null||runs.length==0){customStatus.setText("No previous Basalt recordings");return;}
        java.util.Arrays.sort(runs,(a,b)->b.getName().compareTo(a.getName()));
        String[] labels=new String[runs.length];for(int i=0;i<runs.length;i++)labels[i]=runs[i].getName();
        new android.app.AlertDialog.Builder(this).setTitle("Export saved Basalt run · interrupted runs are marked incomplete")
            .setItems(labels,(dialog,which)->launchCustomExport(runs[which],true)).setNegativeButton("Cancel",null).show();
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(pendingCustomExport!=null){state.putString("basalt_export_directory",pendingCustomExport.getName());state.putBoolean("basalt_export_recovery",recoverPendingExport);}
        super.onSaveInstanceState(state);
    }
    private void stopCustomAsync(){
        // Capture the old generation's objects; a rapid resume may create new
        // objects before this worker has finished native teardown.
        CustomVioSession old=customSession;customSession=null;
        ImageReader reader=imageReader;imageReader=null;
        if(old==null&&reader==null)return;
        java.util.concurrent.CompletableFuture<Void> done=new java.util.concurrent.CompletableFuture<>();customClosing=done;
        final String optical=opticalCsv.toString();
        Runnable close=()->{try{if(reader!=null)reader.close();if(old!=null){old.close();CustomVioSession.persistOptical(old.directory,optical);}}
            catch(java.io.IOException e){fail("Optical trace persistence failed: "+e.getMessage());}finally{done.complete(null);}};
        Handler h=handler;if(h==null||!h.post(close))new Thread(close,"basalt-close").start();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==209&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)openIfReady();
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture st,int width,int height){openIfReady();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st){
        captureGeneration++;
        if(capture!=null){capture.close();capture=null;}
        if(device!=null){device.close();device=null;}
        if(output!=null){output.release();output=null;}
        stopCustomAsync();
        return true;
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st,int width,int height){}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st){}
}
