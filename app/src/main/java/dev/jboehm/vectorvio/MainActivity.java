package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.PlaybackStatus;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;

import java.io.File;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * On-device VIO recorder with optional foreground location and ARCore MP4 capture.
 * ARCore fuses camera and inertial sensors; GNSS remains an independent measurement.
 * Tracking is deliberately suspended across app backgrounding / relocalization.
 */
public final class MainActivity extends Activity implements GLSurfaceView.Renderer {
    private static final int CAMERA_REQUEST=140, EXPORT_REQUEST=141, IMPORT_MAP_REQUEST=142, LOCATION_REQUEST=143, IMPORT_REPLAY_REQUEST=144, IMPORT_GPX_AR_REQUEST=145, GPX_AR_LOCATION_REQUEST=146, SETTINGS_REQUEST=147;
    private static final String EXTRA_REPLAY_PATH="dev.jboehm.vectorvio.replay.path";
    private File playbackFile;
    private boolean playbackMode=false,playbackFinished=false;
    private long playbackFrames=0,playbackTrackedFrames=0;
    private final Odometer odometer = new Odometer();
    /** B2 experimental geographic diagnostic, never GPX/odometer authority. */
    private final dev.jboehm.vectorvio.geo.GeoFusionTracker geoFusion=
        new dev.jboehm.vectorvio.geo.GeoFusionTracker();
    private volatile String geoDiagnostics="GEO WAIT";
    private long lastTrackingQualityMs;
    private final AnchoredOdometry anchoredOdometry=new AnchoredOdometry();
    private final TraceGuide traceGuide=new TraceGuide();
    private volatile boolean returnTraceRequested;
    private volatile String guideText="";
    private GLSurfaceView surface;
    private HudView hud;
    private LiveFrameScheduler frameScheduler;
    private android.os.HandlerThread cadenceThread;
    private CoalescingTask trackingTask;
    // Accessed only on the GL thread, shared by update events and presentation.
    private Frame latestFrame;
    private Odometer.Result latestPoseResult=Odometer.Result.INACTIVE;
    private final FrameRateMeter poseRate=new FrameRateMeter();
    private TextView powerButton;
    private int boostPointer=-1;
    private ImuMonitor imu;
    private final SceneMapper mapper=new SceneMapper();
    private volatile boolean estimatedGround;
    private WorldOverlayRenderer worldOverlay;
    private OverlayPolicy overlayPolicy=new OverlayPolicy(false,false);
    private long lastOverlayErrorMs;
    private final MapCapture mapCapture=new MapCapture();
    private final java.util.concurrent.ExecutorService mapIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.ExecutorService elevationIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean elevationBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean photoKeyframes=false;
    private volatile int savedKeyframes=0,capturedImages=0,savedPois=0;
    private volatile boolean mapFull=false;
    private volatile float keyframeIntervalSeconds=1f,keyframeMinDistance=.75f,keyframeMinRotation=20f,pointConfidenceThreshold=.2f;
    private volatile PoiRequest pendingPoi;
    private volatile String autoMapName="map_"+System.currentTimeMillis()+"_0.vmap";
    private volatile boolean clearMap=false;
    private long lastSceneMs=0;
    private long previousFrameNs=0;
    private float cameraHz=0;
    private long lastPoseReceivedMs;
    private TextView recordButton;
    private TextView torchButton;
    private CameraBackground background;
    private CsvRecorder recorder;
    private TrainingCapture training;
    private GpsMonitor gps;
    private final AnchoredOdometry gpxReference=new AnchoredOdometry();
    private volatile boolean resetGpxReference;
    private volatile int gpxDisplayFlags=dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.ALL;
    /** Optional occlusion only; depth NEVER becomes absolute terrain or GNSS truth. */
    private volatile boolean gpxDepthEnabled;
    private volatile boolean gpxDepthSupported;
    private final ArPoseClock gpxPoseClock=new ArPoseClock();
    private final dev.jboehm.vectorvio.gpxar.ArRouteGuide gpxAr=new dev.jboehm.vectorvio.gpxar.ArRouteGuide();
    private dev.jboehm.vectorvio.location.LocationSource gpxLocation;
    private dev.jboehm.vectorvio.gpxar.GpxArRouteStore gpxRouteStore;
    private dev.jboehm.vectorvio.location.LocationSource.Mode gpxLocationMode=dev.jboehm.vectorvio.location.LocationSource.Mode.AUTO;
    private volatile boolean gpxArEnabled;
    /** True only in the intentional, app-local GPS drop-out test. */
    private volatile boolean gpxLocationMuted;
    private boolean gpxManualMode;
    private LinearLayout gpxManualToolbar;
    private float manualTouchX,manualTouchY;
    private long manualTouchMs;
    private volatile dev.jboehm.vectorvio.gps.GpsRoute selectedArRoute;
    private volatile long gpxRouteRevision;
    private String lastJournaledRoute="";
    private boolean foreground;
    private volatile String gpxArText="",gpxLocationStatus="LOCATION OFF";
    private volatile int gpxGeneration=1;
    private long lastGpxDiagnosticNs;

    private boolean trainingEnabled=false,exportDataset=false;
    private volatile Session arSession;
    private volatile int width=0,height=0;
    private volatile boolean textureAssigned=false;
    private volatile boolean rendererRunning=false;
    private volatile boolean torchEnabled=false;
    private volatile boolean recordingStartPending=false;
    private volatile boolean mapSaveInFlight=false;
    private volatile int autoSavedKeyframes=0,autoSavedPois=0;
    private volatile long mapRetryAfterMs;
    private volatile int mapSaveFailures;
    private long lastSlowFrameLogMs;
    private long lastAutoSaveAttemptMs=0;
    private volatile String message="Tap START for AR tracking, or SIM for offline replay";
    private volatile String trackingLabel="SIM READY";
    private long lastHudUpdateMs=0,lastLoggedFrameNs=0;
    // GPS badge freshness must expire even while the camera/renderer is stopped.
    private final android.os.Handler gpsBadgeHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable gpsBadgeRefresh=new Runnable(){
        @Override public void run(){
            if(!foreground)return;
            if(hud!=null&&gps!=null)hud.updateGps(gps.reading(SystemClock.elapsedRealtimeNanos()));
            gpsBadgeHandler.postDelayed(this,1000L);
        }
    };
    private boolean installRequested=false,needsNewFile=true,liveModeRequested=false;
    private int markerCount=0;
    private File exportFile;
    private static final class PoiRequest {final String type,label;PoiRequest(String type,String label){this.type=type;this.label=label;}}

    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        String requestedReplay=getIntent().getStringExtra(EXTRA_REPLAY_PATH);
        if(requestedReplay!=null){
            File requested=new File(requestedReplay);
            File sessions=new File(getFilesDir(),"sessions");
            File imported=new File(getFilesDir(),"replay_import");
            try {
                File resolved=requested.getCanonicalFile();
                if(resolved.isFile() && resolved.length()>0 && (resolved.getParentFile().equals(sessions.getCanonicalFile()) || resolved.getParentFile().equals(imported.getCanonicalFile()))){
                    playbackFile=resolved;playbackMode=true;
                }
            } catch(java.io.IOException ignored) { }
        }
        recorder = new CsvRecorder(this);
        training=new TrainingCapture(this,recorder);
        gps=new GpsMonitor(this,(location,status)->{
            if(odometer.isRecording())recorder.sidecar(recorder.currentFile(),"_gps.csv","wall_time_ms,elapsed_realtime_ns,provider,latitude_deg,longitude_deg,altitude_m,horizontal_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg",String.format(Locale.US,"%d,%d,%s,%.9f,%.9f,%s,%.3f,%s,%s,%s",location.getTime(),location.getElapsedRealtimeNanos(),location.getProvider(),location.getLatitude(),location.getLongitude(),location.hasAltitude()?Double.toString(location.getAltitude()):"",location.getAccuracy(),location.hasSpeed()?Float.toString(location.getSpeed()):"",location.hasSpeedAccuracy()?Float.toString(location.getSpeedAccuracyMetersPerSecond()):"",location.hasBearing()?Float.toString(location.getBearing()):""));
            if(odometer.isRecording()){
                dev.jboehm.vectorvio.geo.GeoFusionTracker.Report report=geoFusion.onFix(
                    location.getElapsedRealtimeNanos(),location.getLatitude(),
                    location.getLongitude(),location.getAccuracy());
                switch(report.status){
                    case CANDIDATE:geoDiagnostics=String.format(Locale.US,
                        "GEO CANDIDATE · fit %.1f m (UNVERIFIED)",report.rmsMeters);break;
                    case CLOCK_MISMATCH:geoDiagnostics="GEO CLOCK UNKNOWN";break;
                    case NEED_BASELINE:geoDiagnostics="GEO NEED MOTION";break;
                    case INCONSISTENT:geoDiagnostics="GEO INCONSISTENT";break;
                    default:geoDiagnostics="GEO WAIT";break;
                }
                File log=recorder.currentFile();
                if(log!=null)recorder.sidecar(log,"_geo_alignment.csv",
                    "gps_time_ns,segment,status,paired_fixes,baseline_m,fit_rms_m,projected_lat_deg,projected_lon_deg",
                    String.format(Locale.US,"%d,%d,%s,%d,%.3f,%.3f,%s,%s",
                        location.getElapsedRealtimeNanos(),report.segment,report.status,report.matchedFixes,
                        report.baselineMeters,report.rmsMeters,
                        report.hasCoordinate()?Double.toString(report.latitude):"",
                        report.hasCoordinate()?Double.toString(report.longitude):""));
            }
            refreshHud();
        });
        gpxRouteStore=new dev.jboehm.vectorvio.gpxar.GpxArRouteStore(getFilesDir());
        gpxLocation=new dev.jboehm.vectorvio.location.AndroidLocationSource(this,new dev.jboehm.vectorvio.location.LocationSource.Listener(){
            @Override public void onStatus(String status){gpxLocationStatus=status;}
            @Override public void onSample(dev.jboehm.vectorvio.location.LocationSample sample){
                if(!gpxArEnabled)return;
                boolean accepted=gpxAr.onFix(sample);
                long revision=gpxRouteRevision;
                if(odometer.isRecording())recorder.sidecar(recorder.currentFile(),"_gpx_ar_fixes.csv",
                    dev.jboehm.vectorvio.gpxar.GpxArJournal.FIX_HEADER,()->dev.jboehm.vectorvio.gpxar.GpxArJournal.fixRow(revision,sample,accepted));
            }
        });
        mapIo.execute(()->{try{dev.jboehm.vectorvio.gps.GpsRoute route=gpxRouteStore.load();if(route!=null)installGpxArRoute(route);}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Saved AR GPX unavailable; import again",Toast.LENGTH_LONG).show());}});
        SharedPreferences preferences=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
        gps.enabled=preferences.getBoolean("gps_speed_enabled",true);
        gpxDisplayFlags=preferences.getInt("gpx_display_flags",dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.ALL);
        gpxDepthEnabled=preferences.getBoolean("gpx_depth_occlusion",false);
        estimatedGround=preferences.getBoolean("estimated_ground",false);
        keyframeIntervalSeconds=preferences.getFloat("keyframe_interval",1f);
        keyframeMinDistance=preferences.getFloat("keyframe_distance",.75f);
        keyframeMinRotation=preferences.getFloat("keyframe_rotation",20f);
        pointConfidenceThreshold=preferences.getFloat("point_confidence",.2f);
        imu=new ImuMonitor(this);
        imu.setListener((type,t,x,y,z)->{
            if(odometer.isRecording())recorder.imu(type,t,x,y,z);
        });
        FrameLayout root=new FrameLayout(this);
        root.setBackgroundColor(0xFF091723);
        surface=new GLSurfaceView(this);
        surface.setEGLContextClientVersion(2);
        surface.setPreserveEGLContextOnPause(true);
        surface.setRenderer(this);
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        cadenceThread=new android.os.HandlerThread("VioCadence");cadenceThread.start();
        trackingTask=new CoalescingTask(surface::queueEvent,this::processFrame);
        frameScheduler=new LiveFrameScheduler(new android.os.Handler(cadenceThread.getLooper()),trackingTask::offer,surface::requestRender);
        frameScheduler.setMode(LiveFrameScheduler.Mode.from(preferences.getString("live_power","full")));
        surface.setOnTouchListener(this::cameraTouch);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));

        hud=new HudView(this);
        hud.setLayers(preferences.getInt("overlay_layers",0));
        hud.setBikeHints(preferences.getBoolean("bike_view_hints",true));
        try{hud.setVersion(getPackageManager().getPackageInfo(getPackageName(),0).versionName);}
        catch(PackageManager.NameNotFoundException ignored){hud.setVersion("unknown");}
        root.addView(hud,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(8),0,dp(8),0);
        recordButton=action("START",0xFF70FADE,0xFF061723);
        TextView mark=action("MARK",0xFF213946,Color.WHITE);
        TextView save=action("SAVE MAP",0xFF213946,Color.WHITE);
        save.setTextSize(10f);
        TextView reset=action("RESET",0xFF213946,Color.WHITE);
        TextView export=action("EXPORT",0xFF213946,Color.WHITE);
        actions.addView(recordButton,weighted());
        actions.addView(mark,weighted());
        actions.addView(save,weighted());
        actions.addView(reset,weighted());
        actions.addView(export,weighted());
        FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM);
        controls.leftMargin=dp(9);controls.rightMargin=dp(9);controls.bottomMargin=dp(8);
        root.addView(actions,controls);
        LinearLayout modes=new LinearLayout(this);
        TextView layers=action("LAYERS",0xCC102D39,Color.WHITE);
        TextView sensors=action("MENU",0xCC102D39,Color.WHITE);
        TextView map=action("MAPS",0xCC102D39,Color.WHITE);
        TextView simulation=action("GPX",0xCC102D39,Color.WHITE);
        torchButton=action("FLASH OFF",0xCC102D39,Color.WHITE);
        modes.addView(layers,weighted());modes.addView(sensors,weighted());modes.addView(map,weighted());modes.addView(simulation,weighted());modes.addView(torchButton,weighted());
        FrameLayout.LayoutParams modeLayout=new FrameLayout.LayoutParams(-1,dp(40),Gravity.TOP);
        modeLayout.topMargin=dp(72);modeLayout.leftMargin=dp(12);modeLayout.rightMargin=dp(12);
        root.addView(modes,modeLayout);
        gpxManualToolbar=new LinearLayout(this);
        gpxManualToolbar.setGravity(Gravity.CENTER_VERTICAL);
        gpxManualToolbar.setPadding(dp(6),0,dp(6),0);
        gpxManualToolbar.setBackgroundColor(0xF00C2B3C);
        TextView dragTitle=new TextView(this);dragTitle.setText("ALIGN GPX • DRAG");
        dragTitle.setTextSize(12);dragTitle.setTextColor(Color.WHITE);
        gpxManualToolbar.addView(dragTitle,new LinearLayout.LayoutParams(0,-2,1f));
        TextView clearManual=action("RESET",0xFF294957,Color.WHITE);
        TextView doneManual=action("DONE",0xFF70FADE,0xFF061723);
        gpxManualToolbar.addView(clearManual,new LinearLayout.LayoutParams(dp(62),dp(40)));
        gpxManualToolbar.addView(doneManual,new LinearLayout.LayoutParams(dp(62),dp(40)));
        clearManual.setOnClickListener(v->{gpxAr.clearVisualShift();surface.requestRender();});
        doneManual.setOnClickListener(v->endGpxManualMode());
        FrameLayout.LayoutParams dragParams=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);
        dragParams.topMargin=dp(190);dragParams.leftMargin=dp(14);dragParams.rightMargin=dp(14);
        root.addView(gpxManualToolbar,dragParams);
        gpxManualToolbar.setVisibility(View.GONE);
        powerButton=action("",0xBB102D39,Color.WHITE);powerButton.setTextSize(10);
        FrameLayout.LayoutParams powerLayout=new FrameLayout.LayoutParams(dp(164),dp(32),Gravity.TOP|Gravity.RIGHT);
        powerLayout.topMargin=dp(146);powerLayout.rightMargin=dp(12);
        root.addView(powerButton,powerLayout);powerButton.setOnClickListener(v->powerMenu());updatePowerLabel();
        layers.setOnClickListener(v->{hud.cycleLayers();preferences.edit().putInt("overlay_layers",hud.layers()).apply();});
        layers.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("Explore your local map")
                .setMessage("LAYERS cycles planes, point cloud and trail. Cyan = horizontal planes, violet = vertical planes, gold = measured scene points. These are geometric surfaces, not recognized objects.\n\nThe orange trail is displayed only over detected upward surfaces by default. Unknown ground creates gaps (NO FLOOR / FLOOR PARTIAL); it is not a surveyed terrain model. DATA can enable an approximate ribbon 1.5 m below the camera where ground is unknown (FLOOR EST.); raising/lowering the phone can distort that approximation. The recorded camera trajectory is unchanged. Turn around to see where you walked. It is a see-through overlay, with no real-world occlusion.\n\nMAPS opens the detailed live scene map, 3D inspection, export, loop checks and settings. The current map auto-saves after captured keyframes and POIs; SAVE MAP creates an extra named snapshot. POIs need a tracked plane and are saved with the map.\n\nThe first START launches ARCore and recording together. PAUSE excludes movement until RESUME. Unknown motion across tracking gaps is excluded. DATA opens GPS, training capture and IMU display. IMU shows device-axis acceleration (including gravity) and angular velocity.\n\nSIM runs a deterministic generated indoor video and synchronized synthetic IMU without camera permission or ARCore; it is for repeatable debug tests, not measured sensor data.\n\nAccuracy is unmeasured. ARCore manages a local map; saved snapshots can be reopened in 3D, but do not restore the private ARCore tracking map. CSV includes diagnostic IMU samples, not calibrated camera–IMU research data.")
                .setPositiveButton("GOT IT",null).show();return true;
        });
        sensors.setOnClickListener(v->dataMenu());
        map.setOnClickListener(v->mapMenu());
        simulation.setOnClickListener(v->gpxArMenu());
        torchButton.setOnClickListener(v->toggleTorch());
        torchButton.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Camera torch")
            .setMessage("The torch is available during live AR tracking when the active ARCore camera configuration supports a flash. It can improve visibility in dim rooms, but may cause glare on reflective surfaces and can increase heat and battery use. SIM uses generated images and does not activate the phone LED.")
            .setPositiveButton("OK",null).show();return true;});
        setContentView(root);
        if(playbackMode){liveModeRequested=true;recordingStartPending=true;message="ARCore replay: "+playbackFile.getName()+" (estimated poses, no independent truth)";}
        if(getIntent().getBooleanExtra("resume_after_collection",false)){
            liveModeRequested=true;recordingStartPending=true;
            getIntent().removeExtra("resume_after_collection");
        }

        recordButton.setOnClickListener(v->toggleRecording());
        mark.setOnClickListener(v->mark());
        save.setOnClickListener(v->saveMap());
        reset.setOnClickListener(v->askReset());
        export.setOnClickListener(v->pickExport());
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
        refreshHud();
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(40),1);
        p.setMargins(dp(4),0,dp(4),0);
        return p;
    }
    private TextView action(String text,int bg,int fg) {
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(fg);v.setTextSize(10f);
        v.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        v.setGravity(Gravity.CENTER);
        v.setAllCaps(true);
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(bg);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1),0x6649A1AB);
        v.setBackground(shape);
        v.setClickable(true);
        return v;
    }

    private void toggleRecording() {
        if (arSession==null || !rendererRunning) {
            liveModeRequested=true;
            recordingStartPending=true;
            recordButton.setText("STARTING");
            recordButton.setContentDescription("Starting live tracking and recording");
            message="Starting camera, map and odometer";
            refreshHud();
            startAr();
            return;
        }
        if(odometer.snapshot(SystemClock.elapsedRealtime()).recording) {
            long now=SystemClock.elapsedRealtime();
            odometer.pause(now);
            recorder.event("PAUSE",System.currentTimeMillis());
            recordButton.setText("RESUME");
            recordButton.setContentDescription("Resume recording");
            message="Paused — movement during pause is excluded";
            clearMap=true;
            surface.queueEvent(()->{
                training.stop(arSession);
                mapCapture.finishSegment();
                saveCurrentAutoMap();
            });
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            beginRecording();
        }
        refreshHud();
    }

    private void beginRecording() {
        if(arSession==null||!rendererRunning||odometer.isRecording())return;
        if(needsNewFile){recorder.newSession();needsNewFile=false;}
        long now=SystemClock.elapsedRealtime();
        odometer.start(now);
        journalGpxArRoute();
        recorder.event("START",System.currentTimeMillis());
        recorder.event("LIVE_POWER_"+frameScheduler.mode().id+"_VIEW_"+frameScheduler.fps()+"_TRACK_TARGET_30",System.currentTimeMillis());
        recorder.event("DISTANCE_ANCHOR_RELATIVE_V4_NO_DEADBAND",System.currentTimeMillis());
        recordButton.setText("PAUSE");
        recordButton.setContentDescription("Pause recording");
        message=trainingEnabled?"Recording IMU, map and odometer — starting video":"Recording IMU, map and odometer — VIDEO OFF (enable in DATA)";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        recordingStartPending=false;
        if(playbackMode){recorder.event("PLAYBACK_SOURCE_"+playbackFile.getName(),System.currentTimeMillis());}
        if(trainingEnabled&&!playbackMode){surface.queueEvent(()->{try{training.start(arSession,recorder.currentFile());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}runOnUiThread(this::refreshHud);});}
        refreshHud();
    }

    private void updatePowerLabel(){
        if(powerButton==null)return;
        String label=frameScheduler.mode()==LiveFrameScheduler.Mode.HOLD
            ?(frameScheduler.pressed()?"VIEW · 30 FPS":"HOLD VIEW · 5 → 30 FPS")
            :"VIEW · "+frameScheduler.fps()+" FPS";
        powerButton.setText(label);
        powerButton.setContentDescription(label+". Choose display rate. Tracking targets 30 updates per second in every mode. Hold the camera image to boost in hold mode.");
    }
    private boolean powerTouch(View view,MotionEvent event){
        if(frameScheduler.mode()!=LiveFrameScheduler.Mode.HOLD)return false;
        boolean previous=frameScheduler.pressed();
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){boostPointer=event.getPointerId(0);frameScheduler.setPressed(true);}
        else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||
            (action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==boostPointer)){
            boostPointer=-1;frameScheduler.setPressed(false);if(action==MotionEvent.ACTION_UP)view.performClick();
        }
        if(previous!=frameScheduler.pressed())updatePowerLabel();return true;
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&frameScheduler!=null){boostPointer=-1;frameScheduler.setPressed(false);updatePowerLabel();}}
    private void powerMenu(){
        String[] choices={"Full · view 30 FPS","Balanced · view 15 FPS","Very low · view 5 FPS","Hold image · view 5 → 30 FPS"};
        new AlertDialog.Builder(this).setTitle("Live power profile")
            .setSingleChoiceItems(choices,frameScheduler.mode().ordinal(),(dialog,index)->{
                frameScheduler.setMode(LiveFrameScheduler.Mode.values()[index]);boostPointer=-1;
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putString("live_power",frameScheduler.mode().id).apply();
                recorder.event("LIVE_POWER_"+frameScheduler.mode().id,System.currentTimeMillis());updatePowerLabel();dialog.dismiss();
            })
            .setNeutralButton("HOW IT WORKS",(d,w)->powerHelp())
            .setPositiveButton("CLOSE",null).show();
    }
    private void powerHelp(){new AlertDialog.Builder(this).setTitle("Power · limits")
        .setMessage("Profiles change presentation only: camera preview, overlay preparation and HUD refresh. Tracking/odometry, map capture and frame metadata keep a 30 updates/s target in every profile. The actual rate can be lower if the device is busy; the HUD reports received pose cadence. No new pose-distance filter is applied.\n\nARCore opens the camera itself at a 30 FPS target and retains its internal tracking. Display at 5 FPS does not mean camera capture at 5 FPS. In Hold mode, press the image for a 30 FPS view; release for 5. The same latest-frame GL pipeline is used at every display rate.\n\nFor maximum autonomy, GPS ROUTES avoids the camera; COLLECT ONLY records video without live mapping. Battery savings require measurement on your phone.").setPositiveButton("OK",null).show();}
    /** Manual visual adjustment requires an existing geographic/VIO alignment. */
    private void beginGpxManualMode(){
        dev.jboehm.vectorvio.gpxar.ArRouteGuide.Snapshot state=gpxAr.snapshot(SystemClock.elapsedRealtimeNanos());
        if(!gpxArEnabled||!rendererRunning||!gpxAr.hasRoute()||
          (state.state!=dev.jboehm.vectorvio.gpxar.ArRouteGuide.State.CANDIDATE &&
           state.state!=dev.jboehm.vectorvio.gpxar.ArRouteGuide.State.VIO_HOLD)){
            Toast.makeText(this,"First enable GPX AR and walk until tracking + alignment are ready",Toast.LENGTH_LONG).show();return;
        }
        new AlertDialog.Builder(this).setTitle("MANUAL GPX ALIGNMENT")
            .setMessage("Stand where you can see the real path. Slide the camera view with one finger to move the GPX line horizontally. Fine positioning is approximate; it does NOT correct GPS or create surveyed ground truth.\n\nTap DONE to keep the shift during this AR session or RESET to remove it. Locking the phone or losing tracking discards this adjustment. The route file and odometer are never changed.")
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("START DRAG",(d,w)->{
                gpxManualMode=true;
                gpxManualToolbar.setVisibility(View.VISIBLE);
                Toast.makeText(this,"Drag on the camera view · RESET / DONE above",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void endGpxManualMode(){
        gpxManualMode=false;
        if(gpxManualToolbar!=null)gpxManualToolbar.setVisibility(View.GONE);
    }
    /**
     * Drag gesture is deliberately active ONLY when the manual toolbar is on.
     * Camera orientation converts screen motion to a horizontal offset in the
     * stable current VIO reference, not a replacement GPS or odometry pose.
     */
    private boolean cameraTouch(View view,MotionEvent event){
        if(!gpxManualMode)return powerTouch(view,event);
        if(event.getPointerCount()!=1&&event.getActionMasked()!=MotionEvent.ACTION_UP)return true;
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
            manualTouchX=event.getX();manualTouchY=event.getY();manualTouchMs=0;return true;
        }
        if(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP){
            long now=SystemClock.elapsedRealtime();
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&now-manualTouchMs<35)return true;
            final float beforeX=manualTouchX,beforeY=manualTouchY;
            final float afterX=event.getX(),afterY=event.getY();
            float dx=afterX-beforeX,dy=afterY-beforeY;
            manualTouchX=afterX;manualTouchY=afterY;manualTouchMs=now;
            if(Math.hypot(dx,dy)<.5)return true;
            surface.queueEvent(()->{
                if(!gpxManualMode||!rendererRunning||latestFrame==null)return;
                if(latestFrame.getCamera().getTrackingState()!=TrackingState.TRACKING)return;
                Pose reference=gpxReference.worldFromMetric();
                if(reference==null)return;
                Pose camera=latestFrame.getCamera().getPose();
                float[] right=camera.getXAxis(),forward=camera.getZAxis();
                double rn=Math.hypot(right[0],right[2]),fn=Math.hypot(forward[0],forward[2]);
                if(rn<.3||fn<.3)return;
                // Where available, derive displacement from TWO hits on the
                // SAME ARCore-observed upward plane. This respects slope and
                // camera pitch rather than assuming metres per pixel.
                float[] observed=manualGroundDisplacement(latestFrame,beforeX,beforeY,afterX,afterY);
                double scale=10.0/Math.max(320,width);
                double mx=observed==null?(right[0]/rn*dx+forward[0]/fn*dy)*scale:observed[0];
                double mz=observed==null?(right[2]/rn*dx+forward[2]/fn*dy)*scale:observed[2];
                double magnitude=Math.hypot(mx,mz);
                if(magnitude>2){mx*=2/magnitude;mz*=2/magnitude;}
                Pose metricFromWorld=reference.inverse();
                float[] base=metricFromWorld.transformPoint(new float[]{0,0,0});
                float[] shifted=metricFromWorld.transformPoint(new float[]{(float)mx,0,(float)mz});
                if(gpxAr.shiftVisual(shifted[0]-base[0],shifted[2]-base[2]))surface.requestRender();
            });
            return true;
        }
        return true;
    }

    /** Difference between ray hits on the same detected ground patch. */
    private float[] manualGroundDisplacement(Frame frame,float ax,float ay,float bx,float by){
        try{
            java.util.List<com.google.ar.core.HitResult> first=frame.hitTest(ax,ay);
            java.util.List<com.google.ar.core.HitResult> second=frame.hitTest(bx,by);
            for(com.google.ar.core.HitResult a:first){
                if(!(a.getTrackable() instanceof com.google.ar.core.Plane))continue;
                com.google.ar.core.Plane plane=(com.google.ar.core.Plane)a.getTrackable();
                if(plane.getTrackingState()!=TrackingState.TRACKING ||
                    plane.getType()!=com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING ||
                    !plane.isPoseInPolygon(a.getHitPose()))continue;
                for(com.google.ar.core.HitResult b:second){
                    if(!plane.equals(b.getTrackable())||!plane.isPoseInPolygon(b.getHitPose()))continue;
                    float[] u=a.getHitPose().getTranslation(),v=b.getHitPose().getTranslation();
                    return new float[]{v[0]-u[0],v[1]-u[1],v[2]-u[2]};
                }
            }
        }catch(RuntimeException missingGround){/* Manual approximate drag still available. */}
        return null;
    }

    /**
     * Semantic DATA menu contract: all existing module entries live in the
     * catalog, not fixed positions that collide across parallel branches.
     * GPS ROUTES — import GPX / follow / record; GpsRoutesActivity.class
     * GPS ↔ FUSED — Android location A/B (experimental); LocationComparisonActivity.class
     * ARCORE REPLAY — evaluate saved video with same odometer; playbackPicker();
     */
    private String getVersionLabel(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
        catch(PackageManager.NameNotFoundException ignored){return "unknown";}
    }
    private void dataMenu(){
        startActivityForResult(new Intent(this,SettingsActivity.class),SETTINGS_REQUEST);
    }
    private String settingsValue(SettingsCatalog.Action a){
        switch(a){
            case GPX_AR:return gpxArEnabled?"ON":"OFF";
            case GPS_MANUAL_ALIGN:return gpxManualMode?"DRAGGING":gpxAr.visualShifted()?"MANUAL":"OFF";
            case RETURN_TRACE:return returnTraceRequested?"ON":"OFF";
            case GPS_TOGGLE:return gps.enabled?"ON":"OFF";
            case BIKE_VIEW_HINTS:return getSharedPreferences("spatial_explorer",MODE_PRIVATE).getBoolean("bike_view_hints",true)?"ON":"OFF";
            case UNKNOWN_GROUND:return estimatedGround?"ON":"OFF";
            case VIDEO_TRAINING:return trainingEnabled?"ARMED":"OFF";
            case POWER:return frameScheduler.mode().id.toUpperCase(Locale.US);
            default:return "";
        }
    }
    private void performSettingsAction(SettingsCatalog.Action a){
        switch(a){
            case GPX_AR:gpxArMenu();break;
            case GPS_MANUAL_ALIGN:beginGpxManualMode();break;
            case GPS_ROUTES:startActivity(new Intent(this,GpsRoutesActivity.class));break;
            case PDR:
                // No camera in PDR foreground mode.
                startActivity(new Intent(this,dev.jboehm.vectorvio.pdr.PdrActivity.class));break;
            case COURSES:
                startActivity(new Intent(this,StorePlannerActivity.class));break;
            case RETURN_TRACE:
                if(!returnTraceRequested&&!odometer.isRecording())
                    Toast.makeText(this,"Start recording to follow one continuous trace",Toast.LENGTH_LONG).show();
                else returnTraceRequested=!returnTraceRequested;
                break;
            case GPS_TOGGLE:
                boolean locationPermitted=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
                if(gps.enabled&&locationPermitted)gps.disable();
                else if(locationPermitted){
                    gps.enabled=true;gps.start();
                }else{gps.enabled=true;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);}
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putBoolean("gps_speed_enabled",gps.enabled).apply();
                refreshHud();break;
            case GPS_COMPARISON:
                if(odometer.isRecording()){
                    Toast.makeText(this,"Pause recording before comparing location providers",Toast.LENGTH_LONG).show();
                }else startActivity(new Intent(this,LocationComparisonActivity.class));
                break;
            case IMU_TOGGLE:hud.toggleSensors();break;
            case LAYERS:
                hud.cycleLayers();getSharedPreferences("spatial_explorer",MODE_PRIVATE)
                    .edit().putInt("overlay_layers",hud.layers()).apply();break;
            case BIKE_VIEW_HINTS:
                boolean bikeHints=!getSharedPreferences("spatial_explorer",MODE_PRIVATE).getBoolean("bike_view_hints",true);
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putBoolean("bike_view_hints",bikeHints).apply();
                hud.setBikeHints(bikeHints);break;
            case UNKNOWN_GROUND:
                estimatedGround=!estimatedGround;
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit()
                    .putBoolean("estimated_ground",estimatedGround).apply();break;
            case OVERLAY_RENDER:overlaySettings();break;
            case VIDEO_TRAINING:
                if(odometer.isRecording()){
                    Toast.makeText(this,"Pause before changing video capture",Toast.LENGTH_LONG).show();break;
                }
                if(trainingEnabled){trainingEnabled=false;refreshHud();}
                else new AlertDialog.Builder(this).setTitle("Capture camera + IMU video?")
                    .setMessage("Opt-in training video creates ARCore MP4 dataset parts, stored privately. This uses extra power and storage. No audio.")
                    .setNegativeButton("CANCEL",null)
                    .setPositiveButton("ARM CAPTURE",(d,w)->{trainingEnabled=true;training.status="VIDEO ARMED";refreshHud();}).show();
                break;
            case COLLECT_ONLY:startActivity(new Intent(this,CollectionActivity.class));break;
            case ARCORE_REPLAY:playbackPicker();break;
            case EXPORT:pickExport();break;
            case CAPTURE_INFO:
                new AlertDialog.Builder(this).setTitle("What is captured?")
                    .setMessage("ARCore poses are estimates, not independent ground truth. Odometer distance uses camera-based motion, not GPS. GPS coordinates and visual GPX placement are separate.\n\nRecording only saves video when explicitly armed; exported ZIPs may contain images and private location. Locking the screen stops live AR tracking.")
                    .setPositiveButton("OK",null).show();break;
            case MAP_VIEW:mapMenu();break;
            case MAP_SAVED:openMaps();break;
            case MAP_SETTINGS:mappingSettings();break;
            case MAP_PLACE_POI:placePoi();break;
            case POWER:powerMenu();break;
            case SIMULATION:startActivity(new Intent(this,SimulationActivity.class));break;
            case HELP:
                new AlertDialog.Builder(this).setTitle("VECTOR VIO · guide")
                    .setMessage("Navigation & GPX: imported tracks, PDR without camera, and manual store planning.\nLocation: GPS/Fused/IMU.\nCamera: overlays and depth.\nRecording: files, capture and export.\nMaps: points of interest and 3D scenes.\nPerformance: FPS and tests.\n\nManual GPX adjustment is not a new GPS observation. GPS-free visual continuation can drift.")
                    .setPositiveButton("OK",null).show();break;
            default:throw new IllegalArgumentException("Unknown menu action "+a);
        }
    }

    private void installGpxArRoute(dev.jboehm.vectorvio.gps.GpsRoute route){gpxAr.setRoute(route);selectedArRoute=route;gpxRouteRevision++;}
    private void journalGpxArRoute(){
        dev.jboehm.vectorvio.gps.GpsRoute route=selectedArRoute;File file=recorder.currentFile();long revision=gpxRouteRevision;
        if(!gpxArEnabled||route==null||file==null)return;
        String key=file.getName()+":"+revision;if(key.equals(lastJournaledRoute))return;lastJournaledRoute=key;
        recorder.sidecar(file,"_gpx_ar_route.csv",dev.jboehm.vectorvio.gpxar.GpxArJournal.ROUTE_HEADER,()->dev.jboehm.vectorvio.gpxar.GpxArJournal.routeRows(revision,route));
    }
    private void postGpxUi(Runnable task){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())task.run();});}
    private void gpxArMenu(){
        String[] actions={gpxArEnabled?"PAUSE AR GPX":"ENABLE AR GPX", "IMPORT GPX", "LOCATION SOURCE", "REVERSE SELECTED ROUTE", "RESET ALIGNMENT", "REMOVE SELECTED ROUTE", "HELP / ACCURACY", "DISPLAY OPTIONS", "ADD ALTITUDE · EU-DEM (ONLINE)", gpxDepthEnabled?"RAW DEPTH OCCLUSION · ON":"RAW DEPTH OCCLUSION · OFF", "MANUAL GPX SLIDE · match the path", gpxLocationMuted?"GPS AID · MUTED (TEST)":"GPS AID · ON"};
        new AlertDialog.Builder(this).setTitle("GPX AR · import / follow / options").setItems(actions,(d,i)->{
            if(i==0){if(gpxArEnabled)setGpxArEnabled(false);else if(!gpxAr.hasRoute())Toast.makeText(this,"Import a GPX first",Toast.LENGTH_LONG).show();
                else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},GPX_AR_LOCATION_REQUEST);
                else setGpxArEnabled(true);}
            if(i==1){Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(picker,IMPORT_GPX_AR_REQUEST);}
            if(i==2)new AlertDialog.Builder(this).setTitle("Location source").setSingleChoiceItems(new String[]{"AUTO · Google Fused, GPS fallback","GPS native","Google Fused only"},gpxLocationMode.ordinal(),(dialog,index)->{
                gpxLocationMode=dev.jboehm.vectorvio.location.LocationSource.Mode.values()[index];gpxAr.trackingLost();if(gpxArEnabled&&foreground&&!gpxLocationMuted)gpxLocation.start(gpxLocationMode);dialog.dismiss();}).show();
            if(i==3){setGpxArEnabled(false);mapIo.execute(()->{try{dev.jboehm.vectorvio.gps.GpsRoute old=gpxRouteStore.load();if(old!=null){dev.jboehm.vectorvio.gps.GpsRoute reversed=old.reverse();gpxRouteStore.save(reversed);installGpxArRoute(reversed);postGpxUi(()->Toast.makeText(this,"AR route reversed · enable to start",Toast.LENGTH_LONG).show());}}catch(Exception e){postGpxUi(()->Toast.makeText(this,"Cannot reverse saved route",Toast.LENGTH_LONG).show());}});}
            if(i==4)gpxAr.trackingLost();
            if(i==5){setGpxArEnabled(false);mapIo.execute(()->{gpxRouteStore.clear();installGpxArRoute(null);});}
            if(i==7)gpxDisplayMenu();
            if(i==8)gpxEnrichElevation();
            if(i==10)beginGpxManualMode();
            if(i==11){
                if(!gpxArEnabled){Toast.makeText(this,"Enable GPX AR first",Toast.LENGTH_LONG).show();return;}
                gpxLocationMuted=!gpxLocationMuted;
                if(gpxLocationMuted)gpxLocation.stop();
                else if(foreground&&!playbackMode)gpxLocation.start(gpxLocationMode);
                Toast.makeText(this,gpxLocationMuted?
                    "GPX GPS input intentionally muted • VIO-only test; enable again to resume GPS":
                    "GPX GNSS assistance resumed",Toast.LENGTH_LONG).show();
            }
            if(i==9){
                gpxDepthEnabled=!gpxDepthEnabled;
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putBoolean("gpx_depth_occlusion",gpxDepthEnabled).apply();
                Toast.makeText(this,gpxDepthEnabled
                    ?"Raw Depth requested (optional occlusion, not 3D terrain). Lock/unlock screen to apply. Unavailable devices stay in plane-only mode."
                    :"Raw Depth off after lock/unlock; plane-ground ribbon remains.",Toast.LENGTH_LONG).show();
            }
            if(i==6)new AlertDialog.Builder(this).setTitle("GPX in the camera · limits")
                .setMessage("Import one continuous GPX segment, enable location, START/RESUME and walk with the camera. Alignment needs several fresh positions and a baseline larger than GPS noise; rotating on the spot cannot establish it. Blue = imported GPX candidate; orange = measured local trail. No automatic snapping to the GPX.\n\nBlue uses bounded nearby ARCore anchors, with observed upward planes within 30 m; unknown ground stays hidden. GPX elevation is retained but is not trusted as AR ground height. This is a see-through overlay, without obstacle occlusion. GPS bias and route errors can shift the line; the allowance shown is diagnostic, not a calibrated confidence interval.\n\nAUTO uses Google Fused when available and falls back to native GPS on unavailable/stale Fused. Both can share GNSS. Pause/background, tracking loss and reset require alignment again. No change to odometer distance. Imported route stays on this phone; enable is explicit after app restart. DISPLAY OPTIONS toggles estimated direction, 25 m markers, endpoints and local map POIs. POI distances are straight-line local AR distances; route markers use distance along the GPX. Imported named GPX waypoints are not shown in this iteration.")
                .setPositiveButton("OK",null).show();
        }).show();
    }
    /** Sends sampled track coordinates to a third-party DEM only after consent.
     * Network I/O is never on the UI, map, GL or pose-acquisition thread. */
    private void gpxEnrichElevation(){
        dev.jboehm.vectorvio.gps.GpsRoute route=selectedArRoute;
        if(route==null){Toast.makeText(this,"Import a GPX first",Toast.LENGTH_LONG).show();return;}
        if(odometer.isRecording()||gpxArEnabled){
            Toast.makeText(this,"Pause recording and GPX AR before changing altitude",Toast.LENGTH_LONG).show();return;
        }
        int missing=dev.jboehm.vectorvio.gpxar.DemElevation.missing(route);
        if(missing==0){Toast.makeText(this,"GPX already contains elevation; original heights preserved",Toast.LENGTH_LONG).show();return;}
        if(missing!=route.points.size()){
            Toast.makeText(this,"Partial GPX elevations: datum unknown; cannot safely mix with EU-DEM",Toast.LENGTH_LONG).show();return;
        }
        if(elevationBusy.get()){Toast.makeText(this,"Altitude lookup already running",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("Add estimated GPX altitude?")
            .setMessage("Optional online EU-DEM 25 m (EVRS2000) elevation for a GPX with NO altitude. Up to 1001 sampled route coordinates will be sent to the independent api.opentopodata.org service. The route's geographic coordinates are shared with that provider. Accuracy is limited (EU-DEM ~7 m vertical RMSE); cliffs, bridges, floors, and stairs are NOT resolved. Existing GPX elevations are never overwritten.\n\nThe camera ribbon still uses locally observed ARCore ground rather than assuming these DEM heights are exact. Requires an internet connection. No lookup runs without your confirmation.")
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("SHARE ROUTE & FETCH",(dialog,which)->{
                if(!elevationBusy.compareAndSet(false,true))return;
                Toast.makeText(this,"Fetching approximate terrain elevation...",Toast.LENGTH_LONG).show();
                try{elevationIo.execute(()->{
                    try{
                        dev.jboehm.vectorvio.gps.GpsRoute enriched=dev.jboehm.vectorvio.gpxar.DemElevation.fill(route,
                            new dev.jboehm.vectorvio.gpxar.DemElevation.OpenTopoData());
                        mapIo.execute(()->{
                            try{
                                if(selectedArRoute!=route || odometer.isRecording() || gpxArEnabled){
                                    postGpxUi(()->Toast.makeText(this,"Route changed or recording started; altitude not replaced",Toast.LENGTH_LONG).show());
                                    return;
                                }
                                gpxRouteStore.save(enriched);
                                installGpxArRoute(enriched);
                                postGpxUi(()->Toast.makeText(this,"Estimated EU-DEM altitude saved; re-enable GPX AR",Toast.LENGTH_LONG).show());
                            }catch(Exception error){
                                postGpxUi(()->Toast.makeText(this,"Altitude save failed: "+error.getMessage(),Toast.LENGTH_LONG).show());
                            }finally{elevationBusy.set(false);}
                        });
                    }catch(Exception error){
                        elevationBusy.set(false);
                        postGpxUi(()->Toast.makeText(this,"Altitude lookup failed; original GPX kept: "+error.getMessage(),Toast.LENGTH_LONG).show());
                    }
                });}catch(java.util.concurrent.RejectedExecutionException closed){elevationBusy.set(false);}
            }).show();
    }
    private void gpxDisplayMenu(){
        String[] labels={"Estimated direction arrow","Distance markers every 25 m","Start / finish markers","Local map POIs + distance","Remaining distance in banner"};
        boolean[] selected=new boolean[5];for(int i=0;i<5;i++)selected[i]=(gpxDisplayFlags&(1<<i))!=0;
        new AlertDialog.Builder(this).setTitle("GPX AR · visible information").setMultiChoiceItems(labels,selected,(dialog,index,checked)->{
            if(checked)gpxDisplayFlags|=1<<index;else gpxDisplayFlags&=~(1<<index);
            getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putInt("gpx_display_flags",gpxDisplayFlags).apply();
        }).setPositiveButton("DONE",null).show();
    }
    private void setGpxArEnabled(boolean enabled){
        if(enabled&&(playbackMode||CollectionService.running)){Toast.makeText(this,"GPX AR needs live camera mode",Toast.LENGTH_LONG).show();return;}
        gpxArEnabled=enabled;gpxAr.setEnabled(enabled);gpxArText="";resetGpxReference=true;
        if(enabled&&odometer.isRecording())journalGpxArRoute();
        if(enabled&&foreground&&!playbackMode&&!gpxLocationMuted)gpxLocation.start(gpxLocationMode);else gpxLocation.stop();
        // Quick-view mode never forces recording or camera access in the background.
        // On screen unlock GPS is reacquired; the old ARCore reference is not reused.
    }
    private void importGpxAr(Uri source){
        setGpxArEnabled(false);
        mapIo.execute(()->{try(java.io.InputStream input=getContentResolver().openInputStream(source)){
            if(input==null)throw new java.io.IOException("Cannot open GPX");
            java.util.List<dev.jboehm.vectorvio.gps.GpsRoute> routes=dev.jboehm.vectorvio.gps.GpxIO.read(input);
            postGpxUi(()->{
                String[] names=new String[routes.size()];for(int i=0;i<names.length;i++)names[i]=routes.get(i).name+String.format(Locale.US," · %.0f m",routes.get(i).length);
                new AlertDialog.Builder(this).setTitle("Choose continuous AR route segment").setItems(names,(d,i)->{
                    dev.jboehm.vectorvio.gps.GpsRoute route=routes.get(i);
                    mapIo.execute(()->{try{gpxRouteStore.save(route);installGpxArRoute(route);postGpxUi(()->{Toast.makeText(this,"GPX saved · enable AR GPX to start",Toast.LENGTH_LONG).show();gpxArMenu();});}
                    catch(Exception e){postGpxUi(()->Toast.makeText(this,"AR GPX failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}});
                }).setNegativeButton("CANCEL",null).show();
            });
        }catch(Exception e){postGpxUi(()->Toast.makeText(this,"GPX import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}});
    }
    private String describeGpxAr(dev.jboehm.vectorvio.gpxar.ArRouteGuide.Snapshot snap){
        if(!gpxArEnabled)return "";
        String visual=snap.manualAdjusted?" · MANUAL ALIGNMENT (NOT GPS VERIFIED)":"";
        // After screen unlock a fresh GPS fix can recover approximate route
        // progress BEFORE walking far enough to establish absolute AR yaw.
        // This banner is GPS-only: never draw an apparently anchored blue line.
        if(snap.state!=dev.jboehm.vectorvio.gpxar.ArRouteGuide.State.CANDIDATE){
            switch(snap.navigationStatus){
                case FOLLOWING:case ARRIVED:case OFF_ROUTE:
                    return String.format(Locale.US,"GPX GPS ONLY · approx %.0f m left · %s · WALK TO ALIGN AR",
                        snap.remainingMeters,snap.navigationStatus);
                case AMBIGUOUS:return "GPX GPS ONLY · PATH AMBIGUOUS · WALK TO ALIGN AR";
                case POOR_ACCURACY:return "GPX GPS WEAK · WAIT FOR LOCATION";
                default:break;
            }
        }
        switch(snap.state){
            case CANDIDATE:
                if(snap.navigationStatus==dev.jboehm.vectorvio.gps.GpsNavigator.Status.AMBIGUOUS)return "GPX · AMBIGUOUS PATH · NO DIRECTION";
                String remaining=(gpxDisplayFlags&dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.REMAINING)!=0?String.format(Locale.US," · %.0f m left",snap.remainingMeters):"";
                return String.format(Locale.US,"GPX ~%.0f m · %s%s · %s%s",snap.horizontalAllowanceMeters,snap.navigationStatus,remaining,mapper.gpxGroundEdges>0?"ANCHORED FLOOR":mapper.gpxAnchorCount>0?"NO FLOOR":"FLOOR / ANCHOR WAIT",visual);
            case VIO_HOLD:return String.format(Locale.US,"GPX VIO ONLY · NO GPS %.0f s · drift UNCERTAIN (≥ %.0f m diagnostic)%s",snap.holdSeconds,snap.horizontalAllowanceMeters,visual);
            case NEED_BASELINE:return "GPX · WALK TO ALIGN ("+snap.samples+" fixes)";
            case STALE:return "GPX HIDDEN · STALE LOCATION / POSE";
            case INCONSISTENT:return "GPX HIDDEN · INCONSISTENT ALIGNMENT";
            case CLOCK_MISMATCH:return "GPX HIDDEN · UNVERIFIED / MISMATCHED CLOCK";
            default:return "GPX · "+snap.state.name().replace('_',' ')+" · "+gpxLocationStatus;
        }
    }

    /** Replay uses the exact MainActivity ARCore tracking, AnchoredOdometry and Odometer.
     *  ARCore's own MP4 recordings are valid; regular H264 MP4 and EuRoC videos are NOT.
     */
    private void playbackPicker(){
        if(odometer.isRecording()){
            Toast.makeText(this,"PAUSE the current session before choosing a replay",Toast.LENGTH_LONG).show();return;
        }
        File directory=new File(getFilesDir(),"sessions");
        File[] captures=directory.listFiles((dir,name)->name.matches("vector_vio_[0-9]{8}_[0-9]{6}_[0-9]{3}_part[0-9]{3}\\.mp4"));
        if(captures==null)captures=new File[0];
        java.util.Arrays.sort(captures,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        final File[] existing=captures;
        String[] options=new String[Math.min(existing.length,15)+1];
        options[0]="Import an ARCore MP4 (Files / Drive offline copy)";
        for(int j=1;j<options.length;j++)
            options[j]=existing[j-1].getName()+" · "+(existing[j-1].length()/1024/1024)+" MiB";
        new AlertDialog.Builder(this).setTitle("ARCore MP4 only · not ordinary videos")
            .setItems(options,(d,index)->{
                if(index==0){
                    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);picker.setType("video/mp4");
                    startActivityForResult(picker,IMPORT_REPLAY_REQUEST);
                }else launchReplay(existing[index-1]);
            }).setNegativeButton("CANCEL",null).show();
    }
    private void launchReplay(File source){
        if(source==null||!source.isFile()||source.length()==0){
            Toast.makeText(this,"Missing replay MP4",Toast.LENGTH_LONG).show();return;
        }
        // A clean Activity creates a fresh ARCore Session; no live camera/recording
        // state is inherited. Existing CSV/map assets remain in private storage.
        Intent restart=new Intent(this,MainActivity.class);
        restart.putExtra(EXTRA_REPLAY_PATH,source.getAbsolutePath());
        restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(restart);finish();
    }
    private void importReplay(Uri document){
        Toast.makeText(this,"Importing MP4 into private, seekable storage…",Toast.LENGTH_LONG).show();
        mapIo.execute(()->{
            File dir=new File(getFilesDir(),"replay_import");
            File target=null;
            try {
                if(!dir.exists()&&!dir.mkdirs())throw new java.io.IOException("Cannot create replay folder");
                target=new File(dir,"arcore_"+System.currentTimeMillis()+".mp4");
                long free=dir.getUsableSpace();
                long limit=Math.min(1500L*1024*1024,Math.max(0,free-128L*1024*1024));
                if(limit<1024)throw new java.io.IOException("Not enough free space");
                long used=0;
                try(java.io.InputStream in=getContentResolver().openInputStream(document);
                    java.io.OutputStream out=new java.io.FileOutputStream(target)){
                    if(in==null)throw new java.io.IOException("Cannot read MP4");
                    byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1){
                        used+=n;
                        if(used>limit)throw new java.io.IOException("File too large or device storage low");
                        out.write(buffer,0,n);
                    }
                }
                if(used==0)throw new java.io.IOException("Empty replay file");
                File loaded=target;
                runOnUiThread(()->launchReplay(loaded));
            }catch(Exception error){
                if(target!=null)target.delete();
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Replay import failed")
                    .setMessage(error.toString()).setPositiveButton("OK",null).show());
            }
        });
    }
    private void endPlayback(){
        if(!playbackMode||playbackFinished)return;
        playbackFinished=true;
        frameScheduler.stop();
        long time=System.currentTimeMillis();
        if(odometer.isRecording())odometer.pause(SystemClock.elapsedRealtime());
        recorder.event("PLAYBACK_END_FRAMES_"+playbackFrames+"_TRACKED_"+playbackTrackedFrames,time);
        Odometer.Snapshot snap=odometer.snapshot(SystemClock.elapsedRealtime());
        message=String.format(Locale.US,"REPLAY FINISHED · %.1f m estimated · %d/%d tracking frames · export CSV",snap.totalMeters,playbackTrackedFrames,playbackFrames);
        trackingLabel="REPLAY DONE";
        runOnUiThread(()->{recordButton.setText("FINISHED");refreshHud();});
    }

    private void stopTraceGuide(){
        if(traceGuide.active()||returnTraceRequested){traceGuide.clear();returnTraceRequested=false;guideText="RETURN STOPPED — reference lost";}
    }
    private void updateTraceGuide(Pose metric){
        if(!returnTraceRequested){traceGuide.clear();if(!guideText.startsWith("RETURN STOPPED"))guideText="";return;}
        Odometer.Point p=new Odometer.Point(metric.tx(),metric.ty(),metric.tz(),odometer.currentSegment());
        if(!traceGuide.active()&&!traceGuide.start(odometer.snapshot(SystemClock.elapsedRealtime()).metricTrail,p)){
            returnTraceRequested=false;guideText="RETURN — record a longer trace first";return;
        }
        TraceGuide.Guidance g=traceGuide.update(p);
        if(g==null){stopTraceGuide();return;}
        float[] axis=metric.getZAxis();
        double dx=g.x-p.x,dz=g.z-p.z;
        double angle=Math.atan2(dx,dz)-Math.atan2(-axis[0],-axis[2]);
        angle=Math.atan2(Math.sin(angle),Math.cos(angle))*180/Math.PI;
        String turn=Math.hypot(axis[0],axis[2])<.2?"level camera":Math.abs(angle)<15?"ahead":(angle>0?"left ":"right ")+Math.round(Math.abs(angle))+"°";
        guideText=g.arrived?"RETURN — near start (estimated)":String.format(Locale.US,"%s · %.1f m left · trace %.1f m · %s",g.offRoute>1?"REJOIN":"RETURN",g.remaining,g.offRoute,turn);
    }

    private void toggleTorch() {
        Session session=arSession;
        if(session==null||!rendererRunning){
            Toast.makeText(this,"Start live AR tracking before enabling the flash",Toast.LENGTH_SHORT).show();
            return;
        }
        final boolean enable=!torchEnabled;
        surface.queueEvent(()->{
            Session current=arSession;
            if(current==null||!rendererRunning)return;
            try {
                if(enable){
                    CameraManager manager=(CameraManager)getSystemService(CAMERA_SERVICE);
                    CameraCharacteristics characteristics=manager.getCameraCharacteristics(current.getCameraConfig().getCameraId());
                    Boolean supported=characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if(!Boolean.TRUE.equals(supported)){
                        runOnUiThread(()->Toast.makeText(this,"Flash is not available for this AR camera",Toast.LENGTH_LONG).show());
                        return;
                    }
                }
                Config config=current.getConfig();
                config.setFlashMode(enable?Config.FlashMode.TORCH:Config.FlashMode.OFF);
                current.configure(config);
                torchEnabled=enable;
                runOnUiThread(()->{
                    torchButton.setText(enable?"FLASH ON":"FLASH OFF");
                    torchButton.setTextColor(enable?0xFF70FADE:Color.WHITE);
                    Toast.makeText(this,enable?"Camera flash on":"Camera flash off",Toast.LENGTH_SHORT).show();
                });
            } catch(Exception e) {
                runOnUiThread(()->Toast.makeText(this,"Could not change flash: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show());
            }
        });
    }

    private void mark() {
        if(!odometer.snapshot(SystemClock.elapsedRealtime()).recording) {
            Toast.makeText(this,"Start recording to place a marker",Toast.LENGTH_SHORT).show();
            return;
        }
        markerCount++;
        recorder.event("MARK_"+markerCount,System.currentTimeMillis());
        Toast.makeText(this,"Marker "+markerCount+" logged",Toast.LENGTH_SHORT).show();
        refreshHud();
    }

    private void askReset() {
        new AlertDialog.Builder(this)
            .setTitle("Reset the odometer?")
            .setMessage("The current metrics will reset. Existing CSV files are preserved in app storage. Export a recording before uninstalling the app.")
            .setNegativeButton("CANCEL",null)
            .setPositiveButton("RESET",(d,w)->{
                if(odometer.snapshot(SystemClock.elapsedRealtime()).recording)
                    recorder.event("RESET",System.currentTimeMillis());
                odometer.reset();
                clearMap=true;
                String previousAutoName=autoMapName;
                surface.queueEvent(()->{training.stop(arSession);MapArchive previous=mapCapture.snapshot();if(!previous.frames.isEmpty()||!previous.pois.isEmpty())persistMap(previous,previousAutoName,false);mapCapture.reset();});
                autoMapName="map_"+System.currentTimeMillis()+"_0.vmap";
                autoSavedKeyframes=autoSavedPois=0;
                mapSaveInFlight=false;
                needsNewFile=true;
                markerCount=0;
                recordButton.setText("START");
                recordButton.setContentDescription("Start live tracking and recording");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                message="Ready — press START for a new measurement";
                refreshHud();
            }).show();
    }

    private void pickExport() {
        if(odometer.isRecording()){toggleRecording();Toast.makeText(this,"Paused to finalize recording before export",Toast.LENGTH_SHORT).show();}
        new AlertDialog.Builder(this).setTitle("Export recording").setItems(new String[]{
            "Current session — complete ZIP (video if recorded + IMU)",
            "Older session — complete ZIP", "Current session — CSV only", "Older session — CSV only"},(d,i)->{
                exportDataset=i<2;
                if(i==0||i==2){File current=recorder.currentFile();if(current==null){Toast.makeText(this,"No current recording; choose an older session",Toast.LENGTH_LONG).show();return;}launchSessionExport(current);}
                else pickSessionExport();
            }).show();
    }
    private void pickSessionExport() {
        File[] candidates=recorderSessions();
        if(candidates.length==0){Toast.makeText(this,"No recorded sessions yet",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[candidates.length];
        for(int i=0;i<candidates.length;i++){
            String stem=SessionBundle.stem(candidates[i]);
            names[i]=stem.substring("vector_vio_".length()).replace('_',' ')+" UTC · "+SessionBundle.videoParts(candidates[i])+" video part(s)";
        }
        new AlertDialog.Builder(this).setTitle("Choose a session — date/time UTC")
            .setItems(names,(dialog,which)->launchSessionExport(candidates[which])).show();
    }
    private void launchSessionExport(File session){
        exportFile=session;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(exportDataset?"application/zip":"text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,exportDataset?SessionBundle.stem(exportFile)+"_dataset.zip":exportFile.getName());
        try{startActivityForResult(intent,EXPORT_REQUEST);}catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private File[] recorderSessions() {
        File directory=new File(getFilesDir(),"sessions");
        File[] files=directory.listFiles((dir,name)->SessionBundle.isSessionCsv(name));
        if(files==null) return new File[0];
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        return files;
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==SETTINGS_REQUEST){
            if(result==RESULT_OK&&data!=null){
                String name=data.getStringExtra(SettingsActivity.EXTRA_ACTION);
                if(name!=null)try{performSettingsAction(SettingsCatalog.Action.valueOf(name));}
                catch(IllegalArgumentException invalid){Toast.makeText(this,"Réglage inconnu",Toast.LENGTH_SHORT).show();}
            }
            return;
        }
        if(request==IMPORT_GPX_AR_REQUEST){if(result==RESULT_OK&&data!=null&&data.getData()!=null)importGpxAr(data.getData());return;}
        if(request==IMPORT_MAP_REQUEST && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri source=data.getData();
            mapIo.execute(()->{
                try(java.io.InputStream input=getContentResolver().openInputStream(source)) {
                    if(input==null)throw new java.io.IOException("Cannot open map ZIP");
                    MapArchive imported=MapArchive.importZip(input);
                    File directory=new File(getFilesDir(),"maps");
                    if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create map folder");
                    File target=new File(directory,"map_"+System.currentTimeMillis()+".vmap");
                    File temporary=new File(directory,target.getName()+".tmp");
                    try {
                        imported.write(temporary);
                        if(!temporary.renameTo(target))throw new java.io.IOException("Cannot finalize imported map");
                    } finally {if(temporary.exists())temporary.delete();}
                    runOnUiThread(()->startActivity(new Intent(this,MapActivity.class).putExtra("map",target.getName())));
                } catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}
            });
            return;
        }
        if(request==IMPORT_REPLAY_REQUEST){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null)importReplay(data.getData());
            return;
        }
        if(request!=EXPORT_REQUEST || result!=RESULT_OK || data==null||data.getData()==null||exportFile==null) return;
        Uri uri=data.getData();
        if(exportDataset)recorder.exportBundle(this,exportFile,uri,status->Toast.makeText(this,status,Toast.LENGTH_LONG).show());
        else recorder.export(this,exportFile,uri,status->Toast.makeText(this,status,Toast.LENGTH_LONG).show());
    }

    private void mapMenu() {
        String[] choices={"Expand / collapse live map","Save snapshot ("+savedKeyframes+" keyframes · "+savedPois+" POI)"+(mapFull?" — capture full; save/reset":""),
            "Open saved 3D maps / export", "Return-to-start loop check", "Photo keyframes: "+(photoKeyframes?"ON":"OFF"), "Import saved map ZIP",
            "Mapping & overlay settings", "Place a point of interest", "Open offline simulation"};
        new AlertDialog.Builder(this).setTitle("MAPS · local spatial snapshots")
            .setItems(choices,(dialog,index)->{
                if(index==0)hud.toggleMap();
                if(index==1)saveMap();
                if(index==2)openMaps();
                if(index==3 && rendererRunning)surface.queueEvent(()->{
                    String result=mapCapture.loopCheck();
                    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Physical loop verification").setMessage(result).setPositiveButton("OK",null).show());
                });
                if(index==5) {
                    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip");
                    try{startActivityForResult(picker,IMPORT_MAP_REQUEST);}catch(Exception e){Toast.makeText(this,"No document provider",Toast.LENGTH_LONG).show();}
                }
                if(index==6)mappingSettings();
                if(index==7)placePoi();
                if(index==8)startActivity(new Intent(this,SimulationActivity.class));
                if(index==4) {
                    if(photoKeyframes){photoKeyframes=false;Toast.makeText(this,"Future photo capture disabled; existing photos retained",Toast.LENGTH_LONG).show();}
                    else new AlertDialog.Builder(this).setTitle("Save real camera keyframes?")
                        .setMessage("Optional JPEG photos are saved with map snapshots on this phone and included when exporting a map ZIP. They may contain people or private surroundings. No upload occurs. Maximum 64 anchored keyframes per uninterrupted segment and 128 total. DATA training video is a separate option.")
                        .setNegativeButton("CANCEL",null).setPositiveButton("ENABLE PHOTOS",(d,w)->photoKeyframes=true).show();
                }
            }).show();
    }
    private void saveMap() {
        if(!rendererRunning){Toast.makeText(this,"Open tracking first",Toast.LENGTH_SHORT).show();return;}
        surface.queueEvent(()->{
            MapArchive archive=mapCapture.snapshot();
            if(archive.frames.isEmpty()&&archive.pois.isEmpty()){runOnUiThread(()->Toast.makeText(this,"Start recording and scan a scene first",Toast.LENGTH_LONG).show());return;}
            persistMap(archive,"map_"+System.currentTimeMillis()+".vmap",true);
        });
    }
    private void mappingSettings() {
        float[] values={keyframeIntervalSeconds,keyframeMinDistance,keyframeMinRotation,pointConfidenceThreshold};
        String[] names={"Keyframe interval","Minimum movement per keyframe","Minimum camera rotation","Point-cloud confidence cutoff"};
        String[] units={"s","m","°",""};
        int[] maxima={70,70,40,80};
        float[] minima={.25f,.25f,5f,0f},steps={.025f,.025f,1f,.01f};
        LinearLayout content=new LinearLayout(this);content.setPadding(dp(20),dp(8),dp(20),dp(4));content.setOrientation(LinearLayout.VERTICAL);
        TextView[] labels=new TextView[values.length];SeekBar[] bars=new SeekBar[values.length];
        for(int i=0;i<values.length;i++) {
            final int index=i;TextView title=new TextView(this);title.setTextColor(Color.WHITE);title.setTextSize(13);content.addView(title);labels[i]=title;
            SeekBar bar=new SeekBar(this);bar.setMax(maxima[i]);bar.setProgress(Math.max(0,Math.min(maxima[i],Math.round((values[i]-minima[i])/steps[i]))));content.addView(bar);bars[i]=bar;
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar b,int progress,boolean user){values[index]=minima[index]+progress*steps[index];labels[index].setText(names[index]+"  ·  "+String.format(Locale.US,"%.2f %s",values[index],units[index]).trim());}
                public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
            });
            values[i]=minima[i]+bar.getProgress()*steps[i];title.setText(names[i]+"  ·  "+String.format(Locale.US,"%.2f %s",values[i],units[i]).trim());
        }
        new AlertDialog.Builder(this).setTitle("LIVE MAP SETTINGS").setView(content)
            .setMessage("Settings apply to new geometry. Fewer points or wider keyframe spacing can improve speed on older phones.")
            .setNegativeButton("CANCEL",null).setPositiveButton("SAVE",(dialog,which)->{
                keyframeIntervalSeconds=values[0];keyframeMinDistance=values[1];keyframeMinRotation=values[2];pointConfidenceThreshold=values[3];
                getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putFloat("keyframe_interval",values[0]).putFloat("keyframe_distance",values[1])
                    .putFloat("keyframe_rotation",values[2]).putFloat("point_confidence",values[3]).apply();
                Toast.makeText(this,"Mapping settings saved",Toast.LENGTH_SHORT).show();
            }).show();
    }
    private void placePoi() {
        if(!rendererRunning||!odometer.isRecording()) {Toast.makeText(this,"Start recording and wait for TRACKING first",Toast.LENGTH_LONG).show();return;}
        LinearLayout content=new LinearLayout(this);content.setPadding(dp(20),dp(8),dp(20),0);content.setOrientation(LinearLayout.VERTICAL);
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("e.g. front door, hazard, landmark");name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(MapArchive.MAX_POI_LABEL_CHARS)});content.addView(name);
        Spinner category=new Spinner(this);String[] types={"LANDMARK","DOOR","HAZARD","NOTE"};category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));content.addView(category);
        new AlertDialog.Builder(this).setTitle("PLACE POINT OF INTEREST").setView(content)
            .setMessage("Aim the center reticle at a tracked plane. The marker is your label, anchored to the scene; the app does not recognize objects automatically.")
            .setNegativeButton("CANCEL",null).setPositiveButton("PLACE AT RETICLE",(dialog,which)->{
                String label=name.getText().toString().trim();if(label.isEmpty()){Toast.makeText(this,"Enter a short name",Toast.LENGTH_SHORT).show();return;}
                pendingPoi=new PoiRequest(types[category.getSelectedItemPosition()],label);
                Toast.makeText(this,"Marker queued — keep the target under the center reticle",Toast.LENGTH_LONG).show();
            }).show();
    }
    private void persistMap(MapArchive archive,String name,boolean notify) {
        persistMap(archive,name,notify,false,archive.frames.size(),archive.pois.size());
    }
    private void persistMap(MapArchive archive,String name,boolean notify,boolean autosave) {
        persistMap(archive,name,notify,autosave,archive.frames.size(),archive.pois.size());
    }
    private void persistMap(MapArchive archive,String name,boolean notify,boolean autosave,int sourceFrames,int sourcePois) {
        if(mapIo.isShutdown())return;
        try { mapIo.execute(()->{
            File directory=new File(getFilesDir(),"maps");
            File target=new File(directory,name),temporary=new File(directory,name+".tmp");
            String result;
            try {
                if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create map folder");
                archive.write(temporary);
                try {
                    java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch(java.nio.file.AtomicMoveNotSupportedException e) {
                    java.nio.file.Files.move(temporary.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                result="Saved "+archive.frames.size()+" keyframes and "+archive.pois.size()+" markers. Open MAPS to inspect in 3D.";
            }catch(Exception e){temporary.delete();result="Map save failed: "+e.getMessage();}
            String message=result;
            boolean succeeded=!message.startsWith("Map save failed");
            if(autosave)runOnUiThread(()->{
                mapSaveInFlight=false;
                if(!name.equals(autoMapName))return;
                if(succeeded){autoSavedKeyframes=sourceFrames;autoSavedPois=sourcePois;mapSaveFailures=0;mapRetryAfterMs=0;}
                else {
                    mapSaveFailures++;
                    mapRetryAfterMs=SystemClock.elapsedRealtime()+Math.min(60_000L,5_000L*(1L<<Math.min(4,mapSaveFailures-1)));
                    android.util.Log.e("VectorMap",message);
                    if(mapSaveFailures==1)Toast.makeText(this,message+" — capture continues; retrying automatically",Toast.LENGTH_LONG).show();
                }
            });
            else if(notify || !succeeded)runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
        }); } catch(java.util.concurrent.RejectedExecutionException e) {
            if(autosave)mapSaveInFlight=false;
            else if(notify)runOnUiThread(()->Toast.makeText(this,"Map save queue is closed",Toast.LENGTH_LONG).show());
        }
    }
    private void saveCurrentAutoMap() {
        MapArchive archive=mapCapture.snapshot();
        if(!archive.frames.isEmpty()||!archive.pois.isEmpty())persistMap(archive,autoMapName,false,true,mapCapture.count(),mapCapture.poiCount());
    }
    private void openMaps() {
        File[] files=new File(getFilesDir(),"maps").listFiles((dir,name)->name.matches("map_[0-9_]+\\.vmap"));
        if(files==null||files.length==0){Toast.makeText(this,"No saved maps yet",Toast.LENGTH_LONG).show();return;}
        java.util.Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        String[] names=new String[files.length];
        for(int i=0;i<files.length;i++) names[i]=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new java.util.Date(files[i].lastModified()))+" · "+(files[i].length()/1024)+" KB";
        new AlertDialog.Builder(this).setTitle("Saved 3D maps · stored on phone")
            .setItems(names,(d,i)->startActivity(new Intent(this,MapActivity.class).putExtra("map",files[i].getName()))).show();
    }

    private void refreshHud() {
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
        hud.updateDataStatus(training.active?training.status:(trainingEnabled?training.status.equals("VIDEO OFF")?"VIDEO ARMED":training.status:"VIDEO OFF"),gps.status);
        hud.updateGps(gps.reading(SystemClock.elapsedRealtimeNanos()));
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(intent.getBooleanExtra("resume_after_collection",false)){
            liveModeRequested=true;recordingStartPending=true;intent.removeExtra("resume_after_collection");
        }
    }
    @Override protected void onResume() {
        super.onResume();foreground=true;
        if(CollectionService.running){
            startActivity(new Intent(this,CollectionActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            return;
        }
        SharedPreferences currentSettings=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
        boolean wantGps=currentSettings.getBoolean("gps_speed_enabled",true);
        if(gps.enabled&&!wantGps)gps.disable();
        else gps.enabled=wantGps;
        estimatedGround=currentSettings.getBoolean("estimated_ground",false);
        hud.setBikeHints(currentSettings.getBoolean("bike_view_hints",true));
        hud.setLayers(currentSettings.getInt("overlay_layers",0));
        hud.setSensors(currentSettings.getBoolean("imu_overlay_enabled",false));
        if(!playbackMode){imu.start();gps.start();if(gpxArEnabled&&!gpxLocationMuted)gpxLocation.start(gpxLocationMode);}
        refreshHud();
        gpsBadgeHandler.removeCallbacks(gpsBadgeRefresh);
        gpsBadgeHandler.post(gpsBadgeRefresh);
        if(liveModeRequested)startAr();
    }
    private void startAr() {
        if(CollectionService.running){Toast.makeText(this,"Stop COLLECT ONLY before live AR",Toast.LENGTH_LONG).show();return;}
        if(rendererRunning)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            trackingLabel="PERMISSION";
            message="Camera access required for visual-inertial tracking";
            refreshHud();
            requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);
            return;
        }
        try {
            if(arSession==null) {
                if(ArCoreApk.getInstance().requestInstall(this,!installRequested)
                        ==ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                    installRequested=true;
                    trackingLabel="INSTALL AR";
                    message="Install Google Play Services for AR and return";
                    refreshHud();
                    return;
                }
                Session created=new Session(this);
                java.util.List<CameraConfig> cameras=created.getSupportedCameraConfigs(
                    new CameraConfigFilter(created).setTargetFps(java.util.EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)));
                if(!cameras.isEmpty())created.setCameraConfig(cameras.get(0));
                Config config=new Config(created);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                gpxDepthSupported=created.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY);
                config.setDepthMode(gpxDepthEnabled&&!playbackMode&&gpxDepthSupported?
                    Config.DepthMode.RAW_DEPTH_ONLY:Config.DepthMode.DISABLED);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                created.configure(config);
                if(playbackMode)created.setPlaybackDatasetUri(Uri.fromFile(playbackFile));
                arSession=created;
                gpxPoseClock.configure(this,created.getCameraConfig().getCameraId());
                textureAssigned=false;
            }
            if(arSession!=null&&!playbackMode){
                // This path also executes on unlock: session was paused in onPause.
                // A depth toggle never restarts camera/odometer while tracking.
                gpxDepthSupported=arSession.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY);
                Config previous=arSession.getConfig();
                Config.DepthMode desired=gpxDepthEnabled&&gpxDepthSupported?
                    Config.DepthMode.RAW_DEPTH_ONLY:Config.DepthMode.DISABLED;
                if(previous.getDepthMode()!=desired){previous.setDepthMode(desired);arSession.configure(previous);}
            }
            if(playbackMode&&arSession!=null){
                if(playbackFinished || !recordingStartPending){
                    // Pausing playback skips source frames: always restart from the beginning.
                    arSession.setPlaybackDatasetUri(Uri.fromFile(playbackFile));
                    odometer.reset();needsNewFile=true;recordingStartPending=true;
                    anchoredOdometry.reset();playbackFrames=playbackTrackedFrames=0;
                    playbackFinished=false;
                }
            }
            arSession.resume();
            surface.onResume();
            rendererRunning=true;
            frameScheduler.start();
            message=playbackMode?"REPLAY · "+playbackFile.getName()+" · no independent ground truth":"Move slowly, looking at textured static objects";
            if(recordingStartPending)beginRecording();
        } catch(Exception e) {
            recordingStartPending=false;
            recordButton.setText("START");
            recordButton.setContentDescription("Start live tracking and recording");
            trackingLabel="AR ERROR";
            message="ARCore unavailable: "+e.getClass().getSimpleName();
            refreshHud();
            new AlertDialog.Builder(this).setTitle("Cannot start tracking")
                    .setMessage(e.getMessage()==null?e.toString():e.getMessage())
                    .setPositiveButton("OK",null).show();
        }
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==GPX_AR_LOCATION_REQUEST){
            if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)setGpxArEnabled(true);
            else Toast.makeText(this,"Precise location required for experimental GPX AR",Toast.LENGTH_LONG).show();return;
        }
        if(request==LOCATION_REQUEST){
            boolean allowed=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(allowed){gps.enabled=true;gps.start();}else gps.disable();
            getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putBoolean("gps_speed_enabled",gps.enabled).apply();
            refreshHud();return;
        }
        if(request==CAMERA_REQUEST) {
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) startAr();
            else {
                liveModeRequested=false;
                recordingStartPending=false;
                recordButton.setText("START");
                recordButton.setContentDescription("Start live tracking and recording");
                trackingLabel="NO CAMERA";
                message="Enable camera permission in Android Settings";
                refreshHud();
            }
        }
    }
    @Override protected void onPause() {
        foreground=false;gpsBadgeHandler.removeCallbacks(gpsBadgeRefresh);gpxLocation.stop();gpxAr.trackingLost();gpxGeneration++;
        endGpxManualMode();
        frameScheduler.stop();boostPointer=-1;updatePowerLabel();
        imu.stop();
        gps.stopUpdates();
        long now=SystemClock.elapsedRealtime();
        if(odometer.snapshot(now).recording){
            odometer.pause(now);
            recorder.event("APP_PAUSED",System.currentTimeMillis());
            recordButton.setText("RESUME");
            recordButton.setContentDescription("Resume recording");
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(rendererRunning) {
            String autoName=autoMapName;
            surface.queueEvent(()->{
                training.stop(arSession);
                if(torchEnabled&&arSession!=null){
                    try {
                        Config config=arSession.getConfig();
                        config.setFlashMode(Config.FlashMode.OFF);
                        arSession.configure(config);
                    } catch(Exception ignored) { }
                    torchEnabled=false;
                    runOnUiThread(()->{
                        torchButton.setText("FLASH OFF");
                        torchButton.setTextColor(Color.WHITE);
                    });
                }
                stopTraceGuide();anchoredOdometry.reset();gpxReference.reset();mapper.clear();mapCapture.finishSegment();
                MapArchive archive=mapCapture.snapshot();
                if(!archive.frames.isEmpty()||!archive.pois.isEmpty())persistMap(archive,autoName,false);
            });
            surface.onPause();
            rendererRunning=false;
        }
        if(arSession!=null) arSession.pause();
        odometer.lostTracking();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if(gpxLocation!=null)gpxLocation.close();
        if(arSession!=null){
            arSession.close();
            arSession=null;
        }
        if(frameScheduler!=null)frameScheduler.stop();
        if(cadenceThread!=null)cadenceThread.quitSafely();
        recorder.shutdown();
        elevationIo.shutdownNow();
        mapIo.shutdown();
        super.onDestroy();
    }

    private void overlaySettings(){
        new AlertDialog.Builder(this).setTitle("Overlay renderer")
            .setMessage("Auto uses GPU overlays when available. A detected failure keeps Canvas active until Retry GPU. Canvas changes display only; camera and ARCore still require graphics support.")
            .setItems(new String[]{"Auto GPU with fallback","Force Canvas overlay","Retry GPU / clear previous failure"},(d,which)->{
                SharedPreferences prefs=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
                OverlayPolicy choice=new OverlayPolicy(prefs.getBoolean("overlay_canvas",false),prefs.getBoolean("overlay_gpu_failed",false));
                if(which==0)choice.chooseAuto();else if(which==1)choice.chooseCanvas();else choice.retry();
                prefs.edit().putBoolean("overlay_canvas",choice.canvas()).putBoolean("overlay_gpu_failed",choice.failed()).apply();
                surface.queueEvent(this::configureWorldOverlay);
            }).show();
    }
    private void publishRenderer(){
        String label=mapper.worldRendering?"GPU":overlayPolicy.failed()?"CANVAS*":"CANVAS";
        hud.setWorldGpu(mapper.worldRendering);runOnUiThread(()->hud.setRendererLabel(label));
    }
    private void releaseWorldOverlay(){
        WorldOverlayRenderer old=worldOverlay;worldOverlay=null;
        if(old!=null)try{old.release();}catch(RuntimeException|OutOfMemoryError ignored){}
        mapper.worldRendering=false;lastSceneMs=0;
    }
    private void disableWorldOverlay(Throwable e){
        android.util.Log.e("VectorGL","GPU overlay failed; Canvas fallback",e);
        releaseWorldOverlay();overlayPolicy.failure();
        getSharedPreferences("spatial_explorer",MODE_PRIVATE).edit().putBoolean("overlay_gpu_failed",true).putString("overlay_failure_device",android.os.Build.FINGERPRINT).apply();
        publishRenderer();
        recorder.event("OVERLAY_CANVAS_FALLBACK",System.currentTimeMillis());
        runOnUiThread(()->Toast.makeText(this,"GPU overlay unavailable — Canvas active. DATA > Overlay rendering to retry.",Toast.LENGTH_LONG).show());
    }
    private void configureWorldOverlay(){
        releaseWorldOverlay();SharedPreferences prefs=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
        boolean sameDevice=android.os.Build.FINGERPRINT.equals(prefs.getString("overlay_failure_device",""));
        overlayPolicy=new OverlayPolicy(prefs.getBoolean("overlay_canvas",false),sameDevice&&prefs.getBoolean("overlay_gpu_failed",false));
        if(overlayPolicy.useGpu())try{
            WorldOverlayRenderer candidate=new WorldOverlayRenderer();candidate.initialize();worldOverlay=candidate;mapper.worldRendering=true;
        }catch(RuntimeException|OutOfMemoryError e){disableWorldOverlay(e);return;}
        publishRenderer();
    }
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        GLES20.glClearColor(.025f,.07f,.105f,1);
        latestFrame=null;poseRate.reset();lastSceneMs=0;lastPoseReceivedMs=0;cameraHz=0;
        background=new CameraBackground();background.initialize();
        // Previous handles belong to a lost EGL context; never delete them in this new one.
        worldOverlay=null;configureWorldOverlay();textureAssigned=false;
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
        width=w;height=h;
        GLES20.glViewport(0,0,w,h);
    }
    /** Tracking runs through GL queue events even when no swap/render is requested. */
    private void processFrame() {
        Session s=arSession;
        if(s==null||background==null||!rendererRunning||
            android.opengl.EGL14.eglGetCurrentContext().equals(android.opengl.EGL14.EGL_NO_CONTEXT))return;
        long renderStartNs=SystemClock.elapsedRealtimeNanos(),arUpdateMs=0,metadataMs=0;
        try {
            if(!textureAssigned){
                s.setCameraTextureName(background.textureId());
                textureAssigned=true;
            }
            if(width<=0||height<=0) return;
            s.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),width,height);
            long phaseStartNs=SystemClock.elapsedRealtimeNanos();
            Frame frame=s.update();latestFrame=frame;
            if(playbackMode){
                if(s.getPlaybackStatus()==PlaybackStatus.FINISHED){endPlayback();return;}
                if(frame.getTimestamp()>0){playbackFrames++;
                    if(frame.getCamera().getTrackingState()==TrackingState.TRACKING)playbackTrackedFrames++;}
            }
            if(!poseRate.accept(frame.getTimestamp()))return;
            cameraHz=poseRate.hz();lastPoseReceivedMs=SystemClock.elapsedRealtime();
            arUpdateMs=(SystemClock.elapsedRealtimeNanos()-phaseStartNs)/1_000_000L;
            phaseStartNs=SystemClock.elapsedRealtimeNanos();
            try{training.frame(s,frame);}catch(Exception e){training.status="VIDEO METADATA ERROR";}
            metadataMs=(SystemClock.elapsedRealtimeNanos()-phaseStartNs)/1_000_000L;
            if(clearMap){gpxAr.trackingLost();gpxReference.reset();gpxGeneration++;traceGuide.clear();returnTraceRequested=false;guideText="";anchoredOdometry.reset();odometer.lostTracking();mapper.clear();if(worldOverlay!=null)worldOverlay.clear();clearMap=false;}
            Odometer.Result poseResult=Odometer.Result.INACTIVE;

            Camera camera=frame.getCamera();
            if(camera.getTrackingState()==TrackingState.TRACKING && frame.getTimestamp()>0) {
                if (!"TRACKING".equals(trackingLabel))
                    message="Tracking active — slow, textured motion is best";
                trackingLabel="TRACKING";
                Pose pose=camera.getPose();
                float[] xyz=pose.getTranslation();
                float[] quaternion=pose.getRotationQuaternion();
                if(odometer.isRecording()) {
                    try {
                        int oldReference=anchoredOdometry.referenceId();
                        Pose metric=anchoredOdometry.update(s,pose,frame.getTimestamp());
                        if(metric!=null){
                            if(oldReference!=anchoredOdometry.referenceId()){
                                // First/reacquired references start a segment; a same-frame rebase does not.
                                recorder.event("ODOMETRY_REFERENCE_"+anchoredOdometry.referenceId(),System.currentTimeMillis());
                            }
                            if(anchoredOdometry.freshReference){
                                odometer.lostTracking();
                                if(traceGuide.active()){traceGuide.clear();returnTraceRequested=false;guideText="RETURN STOPPED — reference lost";}
                            }
                            poseResult=odometer.onMeasuredPose(metric.tx(),metric.ty(),metric.tz(),xyz,frame.getTimestamp());
                            if(poseResult==Odometer.Result.ACCEPTED ||
                               poseResult==Odometer.Result.REFERENCE ||
                               poseResult==Odometer.Result.NO_CHANGE){
                                // ARCore timestamp may differ from Android elapsedRealtime:
                                // use verified conversion; never pair wrong timebases.
                                long syncedNs=gpxPoseClock.elapsedRealtimeNs(frame);
                                if(syncedNs>0)geoFusion.onPose(odometer.currentSegment(),
                                    syncedNs,SystemClock.elapsedRealtimeNanos(),metric.tx(),metric.tz());
                            }else if(poseResult==Odometer.Result.GAP ||
                                     poseResult==Odometer.Result.REJECTED){
                                geoFusion.reset();geoDiagnostics="GEO TRACK GAP";
                            }
                            if(poseResult==Odometer.Result.REJECTED||poseResult==Odometer.Result.GAP)anchoredOdometry.reset();
                            updateTraceGuide(metric);
                            final Pose a=anchoredOdometry.anchorPose,m=metric;
                            final long ts=frame.getTimestamp();final int id=anchoredOdometry.referenceId();
                            if(a!=null)recorder.sidecar(recorder.currentFile(),"_motion.csv",
                                "frame_time_ns,reference_id,world_x,world_y,world_z,anchor_x,anchor_y,anchor_z,anchor_qx,anchor_qy,anchor_qz,anchor_qw,metric_x,metric_y,metric_z",
                                ()->{float[] aq=a.getRotationQuaternion();return String.format(Locale.US,"%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f",ts,id,xyz[0],xyz[1],xyz[2],a.tx(),a.ty(),a.tz(),aq[0],aq[1],aq[2],aq[3],m.tx(),m.ty(),m.tz());});
                        } else if(frame.getTimestamp()>previousFrameNs){
                            stopTraceGuide();odometer.lostTracking();message="Distance paused — waiting for tracked reference";
                        }
                    }catch(RuntimeException referenceError){
                        stopTraceGuide();anchoredOdometry.reset();odometer.lostTracking();
                        message="Distance paused — reference unavailable";
                    }
                } else anchoredOdometry.reset();
                long wallMs=System.currentTimeMillis();
                if(frame.getTimestamp()-lastLoggedFrameNs>=100_000_000L
                       && odometer.isRecording()
                       && recorder.currentFile()!=null) {
                    Odometer.Snapshot data=odometer.snapshot(SystemClock.elapsedRealtime());
                    recorder.pose(wallMs,frame.getTimestamp(),xyz,quaternion,data);
                    lastLoggedFrameNs=frame.getTimestamp();
                }
            } else {
                stopTraceGuide();anchoredOdometry.reset();
                geoFusion.reset();geoDiagnostics="GEO TRACK LOST";
                odometer.lostTracking();
                String why=camera.getTrackingFailureReason().toString();
                trackingLabel="SEARCHING";
                message="Tracking paused: "+why.replace('_',' ').toLowerCase(Locale.US);
            }
            // 1 Hz diagnostic of tracking health, never a confidence proof.
            if(odometer.isRecording()&&recorder.currentFile()!=null&&frame.getTimestamp()>0&&
                SystemClock.elapsedRealtime()-lastTrackingQualityMs>=1000L){
                lastTrackingQualityMs=SystemClock.elapsedRealtime();
                recorder.sidecar(recorder.currentFile(),"_tracking_quality.csv",
                    "wall_time_ms,frame_time_ns,tracking_state,tracking_reason,pose_hz,segment,recording,gps_accuracy_m",
                    String.format(Locale.US,"%d,%d,%s,%s,%.2f,%d,1,",
                        System.currentTimeMillis(),frame.getTimestamp(),
                        camera.getTrackingState(),camera.getTrackingFailureReason(),
                        camera.getTrackingState()==TrackingState.TRACKING?cameraHz:0f,
                        odometer.currentSegment()));
            }
            if(resetGpxReference){gpxReference.reset();gpxAr.trackingLost();gpxGeneration++;resetGpxReference=false;}
            // The user can lock the display, walk with no recording/background
            // location, and unlock for a GPX camera glance. ARCore/GNSS must
            // reacquire: never restore an obsolete map anchor across the lock.
            if(gpxArEnabled&&!playbackMode&&camera.getTrackingState()==TrackingState.TRACKING){
                final long frameNs=frame.getTimestamp(),cameraElapsedNs=gpxPoseClock.elapsedRealtimeNs(frame),receiptNs=SystemClock.elapsedRealtimeNanos();
                Pose world=camera.getPose(),metric=null;
                try{
                    if(poseResult==Odometer.Result.REFERENCE||poseResult==Odometer.Result.GAP||poseResult==Odometer.Result.REJECTED)gpxReference.reset();
                    if(cameraElapsedNs>0)metric=gpxReference.update(s,world,frameNs);
                    if(metric!=null&&gpxReference.freshReference){gpxAr.trackingLost();gpxGeneration++;}
                    if(metric!=null)gpxAr.onPose(gpxGeneration,cameraElapsedNs,receiptNs,metric.tx(),metric.tz());
                    else {gpxReference.reset();gpxAr.trackingLost();gpxAr.onPose(gpxGeneration,0,receiptNs,0,0);}
                }catch(RuntimeException unavailable){gpxReference.reset();gpxAr.trackingLost();}
                final long routeRevision=gpxRouteRevision;final int generation=gpxGeneration;final float wx=world.tx(),wy=world.ty(),wz=world.tz();
                final Pose capturedMetric=metric;
                if(odometer.isRecording()&&recorder.currentFile()!=null)
                    recorder.sidecar(recorder.currentFile(),"_gpx_ar_poses.csv","ar_frame_ns,android_camera_elapsed_ns,receipt_elapsed_ns,tracking_segment,world_x,world_y,world_z,metric_x,metric_y,metric_z,route_revision",
                        ()->String.format(Locale.US,"%d,%d,%d,%d,%.9f,%.9f,%.9f,%s,%s,%s,%d",frameNs,cameraElapsedNs,receiptNs,generation,wx,wy,wz,
                        capturedMetric==null?"":Float.toString(capturedMetric.tx()),capturedMetric==null?"":Float.toString(capturedMetric.ty()),capturedMetric==null?"":Float.toString(capturedMetric.tz()),routeRevision));
            }else {gpxReference.reset();gpxAr.trackingLost();}
            if(frame.getTimestamp()>0) previousFrameNs=frame.getTimestamp();
            latestPoseResult=poseResult;
            try {
            mapCapture.update(s,frame,poseResult,odometer.currentSegment(),photoKeyframes,keyframeIntervalSeconds,keyframeMinDistance,keyframeMinRotation,pointConfidenceThreshold);
            PoiRequest request=pendingPoi;
            if(request!=null) {
                pendingPoi=null;
                String result=mapCapture.addPoi(s,frame,width*.5f,height*.41f,odometer.currentSegment(),request.type,request.label);
                if(result.startsWith("Placed "))recorder.event("POI_"+request.type,System.currentTimeMillis());
                runOnUiThread(()->Toast.makeText(this,result,Toast.LENGTH_LONG).show());
            }
            savedKeyframes=mapCapture.count();capturedImages=mapCapture.imageCount();savedPois=mapCapture.poiCount();mapFull=mapCapture.full();
            // Anchor updates follow every pose result. Publish bounded geometry at ~20 Hz.
            if(poseResult==Odometer.Result.REFERENCE || poseResult==Odometer.Result.GAP || poseResult==Odometer.Result.REJECTED){mapper.clear();if(worldOverlay!=null)worldOverlay.clear();}
            long now=SystemClock.elapsedRealtime();
            if(odometer.isRecording()&&!mapSaveInFlight
                    &&(savedKeyframes>autoSavedKeyframes||savedPois>autoSavedPois)
                    &&now>=mapRetryAfterMs&&now-lastAutoSaveAttemptMs>=5_000L) {
                MapArchive autosave=mapCapture.snapshot();
                if(!autosave.frames.isEmpty()||!autosave.pois.isEmpty()) {
                    mapSaveInFlight=true;
                    lastAutoSaveAttemptMs=now;
                    persistMap(autosave,autoMapName,false,true,savedKeyframes,savedPois);
                }
            }
            } catch(Exception mapError) {
                // Optional mapping must never invalidate VIO poses or interrupt video recording.
                android.util.Log.e("VectorMap","Map update failed; tracking continues",mapError);
            }
            long now=SystemClock.elapsedRealtime();
            if(now-lastHudUpdateMs>=Math.max(110,1000/frameScheduler.fps())) {
                lastHudUpdateMs=now;
                if(recorder.problem()!=null) message="CSV ERROR: check Android logcat";
                Odometer.Snapshot snap=odometer.snapshot(now);
                String currentTracking=trackingLabel,currentMessage=message;
                int marks=markerCount;
                runOnUiThread(()->{hud.update(snap,currentTracking,currentMessage,marks);hud.updateGuide(gpxArEnabled?gpxArText:guideText);hud.updateDataStatus(training.active?training.status:(trainingEnabled?training.status.equals("VIDEO OFF")?"VIDEO ARMED":training.status:"VIDEO OFF"),gps.status);hud.updateGps(gps.reading(SystemClock.elapsedRealtimeNanos()));});
            }
        } catch(Exception ex) {
            trackingLabel="TRACK LOST";
            message=ex.getClass().getSimpleName()+": check camera / AR service";
            stopTraceGuide();anchoredOdometry.reset();odometer.lostTracking();
            mapper.clear();
            mapCapture.finishSegment();
            runOnUiThread(()->{hud.updateScene(new SceneMapper.Snapshot(new java.util.ArrayList<>(),0,0,0,0,0),imu.snapshot(),0);refreshHud();});
        } finally {
            long durationMs=(SystemClock.elapsedRealtimeNanos()-renderStartNs)/1_000_000L;
            long nowMs=SystemClock.elapsedRealtime();
            if(durationMs>=100 && nowMs-lastSlowFrameLogMs>=5_000 && odometer.isRecording()) {
                lastSlowFrameLogMs=nowMs;
                recorder.event("SLOW_TRACK_UPDATE_MS_"+durationMs+"_AR_"+arUpdateMs+"_META_"+metadataMs,System.currentTimeMillis());
            }
        }
    }
    @Override public void onDrawFrame(GL10 gl){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
        if(!rendererRunning||arSession==null||background==null)return;
        // First draw establishes the EGL context; queued updates can then run without swaps.
        if(latestFrame==null)processFrame();
        Frame frame=latestFrame;if(frame==null||frame.getTimestamp()==0)return;
        long now=SystemClock.elapsedRealtime();
        try{
            background.draw(frame);
            Camera camera=frame.getCamera();
            if(now-lastSceneMs>=Math.max(50,1000/frameScheduler.fps())){
                lastSceneMs=now;
            java.util.List<Odometer.Point> history=odometer.snapshot(now).worldTrail;
            mapper.allowEstimatedGround=estimatedGround;
            dev.jboehm.vectorvio.gpxar.ArRouteGuide.Snapshot routeState=gpxAr.snapshot(SystemClock.elapsedRealtimeNanos());
            mapper.gpxRoute=routeState;mapper.gpxWorldFromMetric=gpxReference.worldFromMetric();mapper.gpxDisplayFlags=gpxDisplayFlags;mapper.gpxMode=gpxArEnabled;
            mapper.gpxDepthOcclusion=gpxArEnabled&&gpxDepthEnabled&&gpxDepthSupported&&!playbackMode;

            SceneMapper.Snapshot scene=mapper.update(arSession,frame,latestPoseResult,width,height,odometer.currentSegment(),mapCapture.pois(),pointConfidenceThreshold,history);
            if(worldOverlay!=null)try{worldOverlay.update(scene);}catch(RuntimeException|OutOfMemoryError e){
                disableWorldOverlay(e);
                scene=mapper.update(arSession,frame,latestPoseResult,width,height,odometer.currentSegment(),mapCapture.pois(),pointConfidenceThreshold,history);
            }
            gpxArText=describeGpxAr(routeState);
            if(gpxArEnabled&&odometer.isRecording()&&frame.getTimestamp()-lastGpxDiagnosticNs>=1_000_000_000L){
                lastGpxDiagnosticNs=frame.getTimestamp();
                recorder.sidecar(recorder.currentFile(),"_gpx_ar_alignment.csv","frame_time_ns,route_revision,state,segment,samples,residual_rms_m,horizontal_allowance_m,heading_allowance_rad,absolute_bias_observable,vertical_datum_known",
                    String.format(Locale.US,"%d,%d,%s,%d,%d,%s,%s,%s,false,false",frame.getTimestamp(),gpxRouteRevision,routeState.state,routeState.segment,routeState.samples,dev.jboehm.vectorvio.gpxar.GpxArJournal.value(routeState.residualMeters),dev.jboehm.vectorvio.gpxar.GpxArJournal.value(routeState.horizontalAllowanceMeters),dev.jboehm.vectorvio.gpxar.GpxArJournal.value(routeState.headingAllowanceRadians)));
            }
            final SceneMapper.Snapshot published=scene;float[] sensors=imu.snapshot();float hz=now-lastPoseReceivedMs>700?0:cameraHz;
            int keyframes=savedKeyframes,images=capturedImages,pois=savedPois;boolean full=mapFull;
            runOnUiThread(()->{hud.updateScene(published,sensors,hz);hud.updateCapture(keyframes,images,pois,full);});
            }
            if(worldOverlay!=null&&camera.getTrackingState()==TrackingState.TRACKING)
                try{worldOverlay.draw(camera,hud.layers(),width,height,getResources().getDisplayMetrics().density);}catch(RuntimeException|OutOfMemoryError e){disableWorldOverlay(e);}
        }catch(Exception displayError){
            if(now-lastOverlayErrorMs>=5_000){lastOverlayErrorMs=now;android.util.Log.e("VectorGL","Presentation failed; tracking continues",displayError);recorder.event("OVERLAY_SCENE_ERROR",System.currentTimeMillis());}
        }
    }
}
