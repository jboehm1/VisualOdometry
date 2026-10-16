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
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
 * GPS-free, on-device VIO recorder. ARCore fuses camera and inertial sensors.
 * No GPS, Internet or raw camera images are recorded. Portrait-only prototype.
 * Tracking is deliberately suspended across app backgrounding / relocalization.
 */
public final class MainActivity extends Activity implements GLSurfaceView.Renderer {
    private static final int CAMERA_REQUEST=140, EXPORT_REQUEST=141, IMPORT_MAP_REQUEST=142;
    private final Odometer odometer = new Odometer();
    private GLSurfaceView surface;
    private HudView hud;
    private ImuMonitor imu;
    private final SceneMapper mapper=new SceneMapper();
    private final MapCapture mapCapture=new MapCapture();
    private final java.util.concurrent.ExecutorService mapIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private volatile boolean photoKeyframes=false;
    private volatile int savedKeyframes=0,capturedImages=0;
    private volatile String autoMapName="map_"+System.currentTimeMillis()+"_0.vmap";
    private volatile boolean clearMap=false;
    private long lastSceneNs=0;
    private long previousFrameNs=0;
    private float cameraHz=0;
    private TextView recordButton;
    private CameraBackground background;
    private CsvRecorder recorder;
    private volatile Session arSession;
    private volatile int width=0,height=0;
    private volatile boolean textureAssigned=false;
    private volatile boolean rendererRunning=false;
    private volatile String message="Scan textured surfaces slowly to initialize tracking";
    private volatile String trackingLabel="STARTING";
    private long lastHudUpdateMs=0,lastLoggedFrameNs=0;
    private boolean installRequested=false,needsNewFile=true;
    private int markerCount=0;
    private File exportFile;

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
        root.addView(hud,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(8),0,dp(8),0);
        recordButton=action("START",0xFF70FADE,0xFF061723);
        TextView mark=action("MARK",0xFF213946,Color.WHITE);
        TextView reset=action("RESET",0xFF213946,Color.WHITE);
        TextView export=action("EXPORT",0xFF213946,Color.WHITE);
        actions.addView(recordButton,weighted());
        actions.addView(mark,weighted());
        actions.addView(reset,weighted());
        actions.addView(export,weighted());
        FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-1,dp(61),Gravity.BOTTOM);
        controls.leftMargin=dp(9);controls.rightMargin=dp(9);controls.bottomMargin=dp(17);
        root.addView(actions,controls);
        LinearLayout modes=new LinearLayout(this);
        TextView layers=action("LAYERS",0xCC102D39,Color.WHITE);
        TextView sensors=action("IMU",0xCC102D39,Color.WHITE);
        TextView map=action("MAPS",0xCC102D39,Color.WHITE);
        modes.addView(layers,weighted());modes.addView(sensors,weighted());modes.addView(map,weighted());
        FrameLayout.LayoutParams modeLayout=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);
        modeLayout.topMargin=dp(142);modeLayout.leftMargin=dp(12);modeLayout.rightMargin=dp(12);
        root.addView(modes,modeLayout);
        layers.setOnClickListener(v->hud.cycleLayers());
        layers.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("Explore your local map")
                .setMessage("LAYERS cycles planes, point cloud and trail. Cyan = horizontal planes, violet = vertical planes, gold = measured scene points. These are geometric surfaces, not recognized objects.\n\nThe cyan trail follows the camera at phone height. Turn around to see where you walked. It is a see-through overlay, with no real-world occlusion.\n\nMAPS opens save, 3D inspection, export, live-map expansion and loop checks for the current independent segment. Unknown motion across tracking gaps is excluded. IMU shows device-axis acceleration (including gravity) and angular velocity.\n\nAccuracy is unmeasured. ARCore manages a local map; saved snapshots can be reopened in 3D, but do not restore the private ARCore tracking map. CSV includes diagnostic IMU samples, not calibrated camera–IMU research data.")
                .setPositiveButton("GOT IT",null).show();return true;
        });
        sensors.setOnClickListener(v->hud.toggleSensors());
        map.setOnClickListener(v->mapMenu());
        setContentView(root);

        recordButton.setOnClickListener(v->toggleRecording());
        mark.setOnClickListener(v->mark());
        reset.setOnClickListener(v->askReset());
        export.setOnClickListener(v->pickExport());
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);
        p.setMargins(dp(4),0,dp(4),0);
        return p;
    }
    private TextView action(String text,int bg,int fg) {
        TextView v=new TextView(this);
        v.setText(text);v.setTextColor(fg);v.setTextSize(12f);
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
            Toast.makeText(this,"ARCore is not ready",Toast.LENGTH_SHORT).show();
            return;
        }
        long now=SystemClock.elapsedRealtime();
        if(odometer.snapshot(now).recording) {
            odometer.pause(now);
            recorder.event("PAUSE",System.currentTimeMillis());
            recordButton.setText("RESUME");
            message="Paused — movement during pause is excluded";
            clearMap=true;
            surface.queueEvent(()->mapCapture.finishSegment());
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            if(needsNewFile) {
                recorder.newSession();
                needsNewFile=false;
            }
            odometer.start(now);
            recorder.event("START",System.currentTimeMillis());
            recordButton.setText("PAUSE");
            message="Recording only while ARCore tracking is valid";
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        refreshHud();
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
                surface.queueEvent(()->{MapArchive previous=mapCapture.snapshot();if(!previous.frames.isEmpty())persistMap(previous,previousAutoName,false);mapCapture.reset();});
                autoMapName="map_"+System.currentTimeMillis()+"_0.vmap";
                needsNewFile=true;
                markerCount=0;
                recordButton.setText("START");
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                message="Ready — press START for a new measurement";
                refreshHud();
            }).show();
    }

    private void pickExport() {
        File[] candidates=recorderSessions();
        if(candidates.length==0) {
            Toast.makeText(this,"No recorded CSV yet",Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names=new String[candidates.length];
        for(int i=0;i<candidates.length;i++) names[i]=candidates[i].getName();
        new AlertDialog.Builder(this)
            .setTitle("Export a recording")
            .setItems(names,(dialog,which)->{
                exportFile=candidates[which];
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE,exportFile.getName());
                try { startActivityForResult(intent,EXPORT_REQUEST); }
                catch(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
    }
    private File[] recorderSessions() {
        File directory=new File(getFilesDir(),"sessions");
        File[] files=directory.listFiles((dir,name)->name.startsWith("vector_vio_")&&name.endsWith(".csv"));
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
                    imported.write(target);
                    runOnUiThread(()->startActivity(new Intent(this,MapActivity.class).putExtra("map",target.getName())));
                } catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Import failed: "+e.getMessage(),Toast.LENGTH_LONG).show());}
            });
            return;
        }
        if(request!=EXPORT_REQUEST || result!=RESULT_OK || data==null||data.getData()==null||exportFile==null) return;
        Uri uri=data.getData();
        recorder.export(this,exportFile,uri,status->Toast.makeText(this,status,Toast.LENGTH_LONG).show());
    }

    private void mapMenu() {
        String[] choices={"Expand / collapse live map","Save mapping snapshot ("+savedKeyframes+" keyframes)",
            "Open saved 3D maps / export", "Return-to-start loop check", "Photo keyframes: "+(photoKeyframes?"ON":"OFF"), "Import saved map ZIP"};
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
                if(index==4) {
                    if(photoKeyframes){photoKeyframes=false;Toast.makeText(this,"Future photo capture disabled; existing photos retained",Toast.LENGTH_LONG).show();}
                    else new AlertDialog.Builder(this).setTitle("Save real camera keyframes?")
                        .setMessage("Optional JPEG photos are saved with map snapshots on this phone and included when exporting a map ZIP. They may contain people or private surroundings. No upload occurs. Up to 128 sparse camera keyframes; no video recording.")
                        .setNegativeButton("CANCEL",null).setPositiveButton("ENABLE PHOTOS",(d,w)->photoKeyframes=true).show();
                }
            }).show();
    }
    private void saveMap() {
        if(!rendererRunning){Toast.makeText(this,"Open tracking first",Toast.LENGTH_SHORT).show();return;}
        surface.queueEvent(()->{
            MapArchive archive=mapCapture.snapshot();
            if(archive.frames.isEmpty()){runOnUiThread(()->Toast.makeText(this,"Start recording and scan a scene first",Toast.LENGTH_LONG).show());return;}
            persistMap(archive,"map_"+System.currentTimeMillis()+".vmap",true);
        });
    }
    private void persistMap(MapArchive archive,String name,boolean notify) {
        if(mapIo.isShutdown())return;
        mapIo.execute(()->{
            File directory=new File(getFilesDir(),"maps");
            File target=new File(directory,name),temporary=new File(directory,name+".tmp");
            String result;
            try {
                if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create map folder");
                archive.write(temporary);
                if(!temporary.renameTo(target))throw new java.io.IOException("Cannot finalize map");
                result="Saved "+archive.frames.size()+" keyframes. Open MAPS to inspect in 3D.";
            }catch(Exception e){temporary.delete();result="Map save failed: "+e.getMessage();}
            String message=result;
            if(notify || message.startsWith("Map save failed"))runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());
        });
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
    }
    @Override protected void onResume() {
        super.onResume();
        imu.start();
        startAr();
    }
    private void startAr() {
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
        } catch(Exception e) {
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
        if(request==CAMERA_REQUEST) {
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED) startAr();
            else {
                trackingLabel="NO CAMERA";
                message="Enable camera permission in Android Settings";
                refreshHud();
            }
        }
    }
    @Override protected void onPause() {
        imu.stop();
        long now=SystemClock.elapsedRealtime();
        if(odometer.snapshot(now).recording){
            odometer.pause(now);
            recorder.event("APP_PAUSED",System.currentTimeMillis());
            recordButton.setText("RESUME");
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(rendererRunning) {
            String autoName=autoMapName;
            surface.queueEvent(()->{
                mapper.clear();mapCapture.finishSegment();
                MapArchive archive=mapCapture.snapshot();
                if(!archive.frames.isEmpty())persistMap(archive,autoName,false);
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
        try {
            if(!textureAssigned){
                s.setCameraTextureName(background.textureId());
                textureAssigned=true;
            }
            if(width<=0||height<=0) return;
            s.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),width,height);
            Frame frame=s.update();
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
                       && odometer.snapshot(SystemClock.elapsedRealtime()).recording
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
            mapCapture.update(s,frame,poseResult,odometer.currentSegment(),photoKeyframes);
            savedKeyframes=mapCapture.count();capturedImages=mapCapture.imageCount();
            // Anchor updates follow every pose result. Publish bounded geometry at ~20 Hz.
            if(poseResult==Odometer.Result.REFERENCE || poseResult==Odometer.Result.GAP || poseResult==Odometer.Result.REJECTED) mapper.clear();
            long now=SystemClock.elapsedRealtime();
            if(now-lastSceneNs>=50) {
                lastSceneNs=now;
                SceneMapper.Snapshot scene=mapper.update(s,frame,poseResult,width,height);
                float[] sensors=imu.snapshot();float hz=cameraHz;
                int keyframes=savedKeyframes,images=capturedImages;
                runOnUiThread(()->{hud.updateScene(scene,sensors,hz);hud.updateCapture(keyframes,images);});
            }
            if(now-lastHudUpdateMs>110) {
                lastHudUpdateMs=now;
                if(recorder.problem()!=null) message="CSV ERROR: check Android logcat";
                Odometer.Snapshot snap=odometer.snapshot(now);
                String currentTracking=trackingLabel,currentMessage=message;
                int marks=markerCount;
                runOnUiThread(()->hud.update(snap,currentTracking,currentMessage,marks));
            }
        } catch(Exception ex) {
            trackingLabel="TRACK LOST";
            message=ex.getClass().getSimpleName()+": check camera / AR service";
            odometer.lostTracking();
            mapper.clear();
            mapCapture.finishSegment();
            runOnUiThread(()->{hud.updateScene(new SceneMapper.Snapshot(new java.util.ArrayList<>(),0,0,0,0,0),imu.snapshot(),0);refreshHud();});
        }
    }
}
