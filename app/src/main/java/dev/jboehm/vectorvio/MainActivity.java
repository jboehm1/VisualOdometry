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
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
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
    private static final int CAMERA_REQUEST=140, EXPORT_REQUEST=141, IMPORT_MAP_REQUEST=142, LOCATION_REQUEST=143;
    private final Odometer odometer = new Odometer();
    private GLSurfaceView surface;
    private HudView hud;
    private ImuMonitor imu;
    private final SceneMapper mapper=new SceneMapper();
    private final MapCapture mapCapture=new MapCapture();
    private final java.util.concurrent.ExecutorService mapIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean photoKeyframes=false;
    private volatile int savedKeyframes=0,capturedImages=0,savedPois=0;
    private volatile boolean mapFull=false;
    private volatile float keyframeIntervalSeconds=1f,keyframeMinDistance=.75f,keyframeMinRotation=20f,pointConfidenceThreshold=.2f;
    private volatile PoiRequest pendingPoi;
    private volatile String autoMapName="map_"+System.currentTimeMillis()+"_0.vmap";
    private volatile boolean clearMap=false;
    private long lastSceneNs=0;
    private long previousFrameNs=0;
    private float cameraHz=0;
    private TextView recordButton;
    private TextView torchButton;
    private CameraBackground background;
    private CsvRecorder recorder;
    private TrainingCapture training;
    private GpsMonitor gps;
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

        recorder = new CsvRecorder(this);
        training=new TrainingCapture(this,recorder);
        gps=new GpsMonitor(this,(location,status)->{
            if(odometer.isRecording())recorder.sidecar(recorder.currentFile(),"_gps.csv","wall_time_ms,elapsed_realtime_ns,provider,latitude_deg,longitude_deg,altitude_m,horizontal_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg",String.format(Locale.US,"%d,%d,%s,%.9f,%.9f,%s,%.3f,%s,%s,%s",location.getTime(),location.getElapsedRealtimeNanos(),location.getProvider(),location.getLatitude(),location.getLongitude(),location.hasAltitude()?Double.toString(location.getAltitude()):"",location.getAccuracy(),location.hasSpeed()?Float.toString(location.getSpeed()):"",location.hasSpeedAccuracy()?Float.toString(location.getSpeedAccuracyMetersPerSecond()):"",location.hasBearing()?Float.toString(location.getBearing()):""));
            refreshHud();
        });
        SharedPreferences preferences=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
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
        surface.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));

        hud=new HudView(this);
        hud.setLayers(preferences.getInt("overlay_layers",0));
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
        TextView sensors=action("DATA",0xCC102D39,Color.WHITE);
        TextView map=action("MAPS",0xCC102D39,Color.WHITE);
        TextView simulation=action("SIM",0xCC102D39,Color.WHITE);
        torchButton=action("FLASH OFF",0xCC102D39,Color.WHITE);
        modes.addView(layers,weighted());modes.addView(sensors,weighted());modes.addView(map,weighted());modes.addView(simulation,weighted());modes.addView(torchButton,weighted());
        FrameLayout.LayoutParams modeLayout=new FrameLayout.LayoutParams(-1,dp(40),Gravity.TOP);
        modeLayout.topMargin=dp(72);modeLayout.leftMargin=dp(12);modeLayout.rightMargin=dp(12);
        root.addView(modes,modeLayout);
        layers.setOnClickListener(v->{hud.cycleLayers();preferences.edit().putInt("overlay_layers",hud.layers()).apply();});
        layers.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("Explore your local map")
                .setMessage("LAYERS cycles planes, point cloud and trail. Cyan = horizontal planes, violet = vertical planes, gold = measured scene points. These are geometric surfaces, not recognized objects.\n\nThe orange trail is displayed near the detected floor. FLOOR EST. means a 1.5 m phone-height assumption is used where the floor is unknown. The recorded camera trajectory is unchanged. Turn around to see where you walked. It is a see-through overlay, with no real-world occlusion.\n\nMAPS opens the detailed live scene map, 3D inspection, export, loop checks and settings. The current map auto-saves after captured keyframes and POIs; SAVE MAP creates an extra named snapshot. POIs need a tracked plane and are saved with the map.\n\nThe first START launches ARCore and recording together. PAUSE excludes movement until RESUME. Unknown motion across tracking gaps is excluded. DATA opens GPS, training capture and IMU display. IMU shows device-axis acceleration (including gravity) and angular velocity.\n\nSIM runs a deterministic generated indoor video and synchronized synthetic IMU without camera permission or ARCore; it is for repeatable debug tests, not measured sensor data.\n\nAccuracy is unmeasured. ARCore manages a local map; saved snapshots can be reopened in 3D, but do not restore the private ARCore tracking map. CSV includes diagnostic IMU samples, not calibrated camera–IMU research data.")
                .setPositiveButton("GOT IT",null).show();return true;
        });
        sensors.setOnClickListener(v->dataMenu());
        map.setOnClickListener(v->mapMenu());
        simulation.setOnClickListener(v->startActivity(new Intent(this,SimulationActivity.class)));
        torchButton.setOnClickListener(v->toggleTorch());
        torchButton.setOnLongClickListener(v->{new AlertDialog.Builder(this).setTitle("Camera torch")
            .setMessage("The torch is available during live AR tracking when the active ARCore camera configuration supports a flash. It can improve visibility in dim rooms, but may cause glare on reflective surfaces and can increase heat and battery use. SIM uses generated images and does not activate the phone LED.")
            .setPositiveButton("OK",null).show();return true;});
        setContentView(root);

        recordButton.setOnClickListener(v->toggleRecording());
        mark.setOnClickListener(v->mark());
        save.setOnClickListener(v->saveMap());
        reset.setOnClickListener(v->askReset());
        export.setOnClickListener(v->pickExport());
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
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
        recorder.event("START",System.currentTimeMillis());
        recordButton.setText("PAUSE");
        recordButton.setContentDescription("Pause recording");
        message=trainingEnabled?"Recording IMU, map and odometer — starting video":"Recording IMU, map and odometer — VIDEO OFF (enable in DATA)";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        recordingStartPending=false;
        if(trainingEnabled){surface.queueEvent(()->{try{training.start(arSession,recorder.currentFile());}catch(Exception e){runOnUiThread(()->Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show());}runOnUiThread(this::refreshHud);});}
        refreshHud();
    }

    private void dataMenu(){
        String[] choices={"Show / hide IMU overlay","GPS: "+(gps.enabled?"ON — disable":"OFF — enable foreground location"),"Training video: "+(trainingEnabled?"ON — disable":"OFF — arm video + IMU capture"),"What is captured?"};
        new AlertDialog.Builder(this).setTitle("DATA · capture and sensors").setItems(choices,(d,i)->{
            if(i==0)hud.toggleSensors();
            if(i==1){if(gps.enabled)gps.disable();else if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){gps.enabled=true;gps.start();}else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_REQUEST);refreshHud();}
            if(i==2){if(odometer.isRecording()){Toast.makeText(this,"Pause first to change video capture",Toast.LENGTH_LONG).show();return;}
                if(trainingEnabled){trainingEnabled=false;refreshHud();}
                else new AlertDialog.Builder(this).setTitle("Capture training video?").setMessage("START/RESUME will save the camera image and internal ARCore sensors in an MP4 on this phone, plus external IMU and frame metadata. PAUSE stops the video; RESUME creates another part. No microphone audio. Export as Dataset ZIP. Video may contain private surroundings. Capture uses extra storage and battery.").setNegativeButton("CANCEL",null).setPositiveButton("ARM CAPTURE",(a,b)->{trainingEnabled=true;training.status="VIDEO ARMED";refreshHud();}).show();
            }
            if(i==3)new AlertDialog.Builder(this).setTitle("Full camera frame · local data").setMessage("The reticle only selects the POI hit-test target. Cyan/violet polygons are detected planes, clipped for display; they do not crop the camera input. ARCore processes its camera stream.\n\nVIO speed measures motion relative to the viewed environment. Looking at a car interior can give near-zero VIO while the car moves: GPS ground speed is separate and is not silently fused.\n\nCSV: poses + diagnostic IMU. Dataset ZIP adds video parts only if armed, frame timestamps/intrinsics, device metadata and GPS fixes if enabled. ARCore poses are estimates, not training ground truth; camera/IMU offset and extrinsics need calibration. Simulation CSV is generated truth, not the estimated map or real footage.").setPositiveButton("OK",null).show();
        }).show();
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
    }
    @Override protected void onResume() {
        super.onResume();
        imu.start();
        gps.start();
        if(liveModeRequested)startAr();
    }
    private void startAr() {
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
                Config config=new Config(created);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED);
                config.setDepthMode(Config.DepthMode.DISABLED);
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
                created.configure(config);
                arSession=created;
                textureAssigned=false;
            }
            arSession.resume();
            surface.onResume();
            rendererRunning=true;
            message="Move slowly, looking at textured static objects";
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
        if(request==LOCATION_REQUEST){
            boolean allowed=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
            if(allowed){gps.enabled=true;gps.start();}else gps.disable();refreshHud();return;
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
                mapper.clear();mapCapture.finishSegment();
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
        if(arSession!=null){
            arSession.close();
            arSession=null;
        }
        recorder.shutdown();
        mapIo.shutdown();
        super.onDestroy();
    }

    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config) {
        GLES20.glClearColor(.025f,.07f,.105f,1);
        background=new CameraBackground();
        background.initialize();
        textureAssigned=false;
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h) {
        width=w;height=h;
        GLES20.glViewport(0,0,w,h);
    }
    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        Session s=arSession;
        if(s==null||background==null||!rendererRunning) return;
        long renderStartNs=SystemClock.elapsedRealtimeNanos(),arUpdateMs=0,metadataMs=0;
        try {
            if(!textureAssigned){
                s.setCameraTextureName(background.textureId());
                textureAssigned=true;
            }
            if(width<=0||height<=0) return;
            s.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),width,height);
            long phaseStartNs=SystemClock.elapsedRealtimeNanos();
            Frame frame=s.update();
            arUpdateMs=(SystemClock.elapsedRealtimeNanos()-phaseStartNs)/1_000_000L;
            phaseStartNs=SystemClock.elapsedRealtimeNanos();
            try{training.frame(s,frame);}catch(Exception e){training.status="VIDEO METADATA ERROR";}
            metadataMs=(SystemClock.elapsedRealtimeNanos()-phaseStartNs)/1_000_000L;
            if(clearMap){mapper.clear();clearMap=false;}
            Odometer.Result poseResult=Odometer.Result.INACTIVE;
            if(frame.getTimestamp()!=0) background.draw(frame);
            Camera camera=frame.getCamera();
            if(camera.getTrackingState()==TrackingState.TRACKING && frame.getTimestamp()>0) {
                if (!"TRACKING".equals(trackingLabel))
                    message="Tracking active — slow, textured motion is best";
                trackingLabel="TRACKING";
                Pose pose=camera.getPose();
                float[] xyz=pose.getTranslation();
                float[] quaternion=pose.getRotationQuaternion();
                poseResult=odometer.onPose(xyz[0],xyz[1],xyz[2],frame.getTimestamp());
                long wallMs=System.currentTimeMillis();
                if(frame.getTimestamp()-lastLoggedFrameNs>=100_000_000L
                       && odometer.isRecording()
                       && recorder.currentFile()!=null) {
                    Odometer.Snapshot data=odometer.snapshot(SystemClock.elapsedRealtime());
                    recorder.pose(wallMs,frame.getTimestamp(),xyz,quaternion,data);
                    lastLoggedFrameNs=frame.getTimestamp();
                }
            } else {
                odometer.lostTracking();
                String why=camera.getTrackingFailureReason().toString();
                trackingLabel="SEARCHING";
                message="Tracking paused: "+why.replace('_',' ').toLowerCase(Locale.US);
            }
            if(frame.getTimestamp()>previousFrameNs && previousFrameNs>0) {
                float hz=1_000_000_000f/(frame.getTimestamp()-previousFrameNs);
                cameraHz=.9f*cameraHz+.1f*hz;
            }
            if(frame.getTimestamp()>0) previousFrameNs=frame.getTimestamp();
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
            if(poseResult==Odometer.Result.REFERENCE || poseResult==Odometer.Result.GAP || poseResult==Odometer.Result.REJECTED) mapper.clear();
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
            if(now-lastSceneNs>=50) {
                lastSceneNs=now;
                SceneMapper.Snapshot scene=mapper.update(s,frame,poseResult,width,height,odometer.currentSegment(),mapCapture.pois(),pointConfidenceThreshold,odometer.snapshot(now).worldTrail);
                float[] sensors=imu.snapshot();float hz=cameraHz;
                int keyframes=savedKeyframes,images=capturedImages,pois=savedPois;boolean full=mapFull;
                runOnUiThread(()->{hud.updateScene(scene,sensors,hz);hud.updateCapture(keyframes,images,pois,full);});
            }
            if(now-lastHudUpdateMs>110) {
                lastHudUpdateMs=now;
                if(recorder.problem()!=null) message="CSV ERROR: check Android logcat";
                Odometer.Snapshot snap=odometer.snapshot(now);
                String currentTracking=trackingLabel,currentMessage=message;
                int marks=markerCount;
                runOnUiThread(()->{hud.update(snap,currentTracking,currentMessage,marks);hud.updateDataStatus(training.active?training.status:(trainingEnabled?training.status.equals("VIDEO OFF")?"VIDEO ARMED":training.status:"VIDEO OFF"),gps.status);});
            }
        } catch(Exception ex) {
            trackingLabel="TRACK LOST";
            message=ex.getClass().getSimpleName()+": check camera / AR service";
            odometer.lostTracking();
            mapper.clear();
            mapCapture.finishSegment();
            runOnUiThread(()->{hud.updateScene(new SceneMapper.Snapshot(new java.util.ArrayList<>(),0,0,0,0,0),imu.snapshot(),0);refreshHud();});
        } finally {
            long durationMs=(SystemClock.elapsedRealtimeNanos()-renderStartNs)/1_000_000L;
            long nowMs=SystemClock.elapsedRealtime();
            if(durationMs>=100 && nowMs-lastSlowFrameLogMs>=5_000 && odometer.isRecording()) {
                lastSlowFrameLogMs=nowMs;
                recorder.event("SLOW_RENDER_MS_"+durationMs+"_AR_"+arUpdateMs+"_META_"+metadataMs,System.currentTimeMillis());
            }
        }
    }
}
