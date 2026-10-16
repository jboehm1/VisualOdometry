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
    private static final int CAMERA_REQUEST=140, EXPORT_REQUEST=141;
    private final Odometer odometer = new Odometer();
    private GLSurfaceView surface;
    private HudView hud;
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
        setContentView(root);

        recordButton.setOnClickListener(v->toggleRecording());
        mark.setOnClickListener(v->mark());
        reset.setOnClickListener(v->askReset());
        export.setOnClickListener(v->pickExport());
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
    }
    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(47),1);
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
        if(request!=EXPORT_REQUEST || result!=RESULT_OK || data==null||data.getData()==null||exportFile==null) return;
        Uri uri=data.getData();
        recorder.export(this,exportFile,uri,status->Toast.makeText(this,status,Toast.LENGTH_LONG).show());
    }

    private void refreshHud() {
        hud.update(odometer.snapshot(SystemClock.elapsedRealtime()),trackingLabel,message,markerCount);
    }
    @Override protected void onResume() {
        super.onResume();
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
                config.setPlaneFindingMode(Config.PlaneFindingMode.DISABLED);
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
        long now=SystemClock.elapsedRealtime();
        if(odometer.snapshot(now).recording){
            odometer.pause(now);
            recorder.event("APP_PAUSED",System.currentTimeMillis());
            recordButton.setText("RESUME");
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(rendererRunning) {
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
            if(frame.getTimestamp()!=0) background.draw(frame);
            Camera camera=frame.getCamera();
            if(camera.getTrackingState()==TrackingState.TRACKING && frame.getTimestamp()>0) {
                trackingLabel="TRACKING";
                Pose pose=camera.getPose();
                float[] xyz=pose.getTranslation();
                float[] quaternion=pose.getRotationQuaternion();
                odometer.onPose(xyz[0],xyz[1],xyz[2],frame.getTimestamp());
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
            long now=SystemClock.elapsedRealtime();
            if(now-lastHudUpdateMs>110) {
                lastHudUpdateMs=now;
                Odometer.Snapshot snap=odometer.snapshot(now);
                String currentTracking=trackingLabel,currentMessage=message;
                int marks=markerCount;
                runOnUiThread(()->hud.update(snap,currentTracking,currentMessage,marks));
            }
        } catch(Exception ex) {
            trackingLabel="TRACK LOST";
            message=ex.getClass().getSimpleName()+": check camera / AR service";
            odometer.lostTracking();
        }
    }
}
