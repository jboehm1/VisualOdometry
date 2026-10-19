package dev.jboehm.vectorvio.pdr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.hardware.GeomagneticField;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.util.Locale;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import dev.jboehm.vectorvio.pdr.ai.TinyCarryModel;
import dev.jboehm.vectorvio.pdr.ai.TinyCarryDiagnostic;

/**
 * Fully on-device, foreground-only walking/running PDR screen.
 * Camera permission is never requested; ARCore is never initialized.
 * Backgrounding terminates the measurement segment instead of inventing movement.
 */
public final class PdrActivity extends Activity {
    private static final int ACTIVITY_REQUEST=711,LOCATION_REQUEST=712,EXPORT_REQUEST=713;
    private final PdrEngine engine=new PdrEngine();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private PdrSensorBridge bridge;
    private PdrTraceWriter writer;
    private PdrTrailView trail;
    private TextView numbers,status,details,sensors;
    private Button start,finish,export,gpsButton,stepLengthButton;
    private Button gaitButton,headingButton,calibrationButton,aiButton;
    private TinyCarryDiagnostic tinyAi;
    private boolean tinyAiEnabled;
    private LocationManager gps;
    private boolean gpsOn,exportReady,autoStopped;
    private boolean activityPermissionRequested;
    private boolean runningMode,phoneTopForward,recordedRunning,recordedPhoneTopForward;
    private SharedPreferences preferences;
    private PdrEngine.Snapshot completedSnapshot;
    private java.util.List<PdrEngine.Point> completedPath;
    private String recordedStepMode="NOT STARTED";
    private String completedTimingReport="NO SENSOR SAMPLES";
    private String note="READY";
    private final Runnable tick=new Runnable(){
        @Override public void run(){render();ui.postDelayed(this,350);}
    };
    private final LocationListener locationListener=new LocationListener(){
        @Override public void onLocationChanged(Location loc){
            if(!engine.isRecording())return;
            if(!loc.hasBearing() || !loc.hasSpeed() ||
               !loc.hasBearingAccuracy() || !loc.hasAccuracy()){
                invalidateGnssCourse(loc.getElapsedRealtimeNanos());
                return;
            }
            float bearingAccuracy=loc.getBearingAccuracyDegrees();
            // Android rotation-vector azimuth is magnetic north; GNSS bearing is
            // true north. Convert GNSS course into the same MAGNETIC frame.
            // Otherwise every switch in heading source injects declination error.
            GeomagneticField geomagnetic=new GeomagneticField(
                (float)loc.getLatitude(),(float)loc.getLongitude(),
                loc.hasAltitude()?(float)loc.getAltitude():0f,loc.getTime());
            double magneticCourse=Math.toRadians(loc.getBearing()-geomagnetic.getDeclination());
            engine.gpsCourse(loc.getElapsedRealtimeNanos(),magneticCourse,
                loc.getSpeed(),loc.getAccuracy(),bearingAccuracy);
            if(writer!=null) {
                try{writer.gpsCourse(loc.getElapsedRealtimeNanos(),magneticCourse,
                    loc.getSpeed(),loc.getAccuracy(),bearingAccuracy);}
                catch(RuntimeException e){note="STORAGE ERROR; STOPPED";finishSession(true);}
            }
        }
        @Override public void onProviderEnabled(String s){}
        @Override public void onProviderDisabled(String s){
            invalidateGnssCourse(SystemClock.elapsedRealtimeNanos());
        }
        @Override public void onStatusChanged(String s,int status,Bundle b){}
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF0A2530);
        getWindow().setNavigationBarColor(0xFF0A2530);
        gps=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        preferences=getSharedPreferences("camera_free_pdr",Context.MODE_PRIVATE);
        // Research-only model is fully checksum-pinned and opt-in each session.
        // A missing/replaced/corrupt asset cannot affect the operational odometer.
        try(InputStream in=getAssets().open("pdr_carry_model.b64")){
            byte[] modelText=new byte[16_384];
            int n=in.read(modelText);
            if(n<=0||in.read()!=-1)throw new java.io.IOException("invalid model asset size");
            TinyCarryModel model=TinyCarryModel.loadBase64(new String(
                    modelText,0,n,StandardCharsets.US_ASCII));
            tinyAi=new TinyCarryDiagnostic(model);
        }catch(Exception invalidModel){tinyAi=null;}
        runningMode=preferences.getBoolean("running_profile",false);
        // Never silently reuse phone/body orientation assumptions between sessions.
        phoneTopForward=false;
        engine.setStepLengthMeters(savedStride(runningMode));
        bridge=new PdrSensorBridge(this,engine,new PdrSensorBridge.Callback(){
            @Override public void sample(String type,long t,double x,double y,double z,int accuracy){
                log(type,t,x,y,z,accuracy);
                if(tinyAiEnabled && tinyAi!=null && engine.isRecording()){
                    if("RAW_GYRO".equals(type))tinyAi.gyro(t,(float)x,(float)y,(float)z);
                    else if("RAW_ACCEL".equals(type))
                        tinyAi.acceleration(t,(float)x,(float)y,(float)z);
                }
            }
            @Override public void step(long t){if(writer!=null)writer.step(t);}
            @Override public void rotationVector(long t,float[] values,int accuracy){
                if(writer==null)return;
                try{writer.rotationVector(t,values,accuracy);}
                catch(RuntimeException e){note="STORAGE ERROR; STOPPED";finishSession(true);}
            }
        });
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xFF0A1B24);
        int margin=dp(14);layout.setPadding(margin,dp(20),margin,margin);
        TextView title=text("PDR · CAMERA FREE",25,0xFF71F5D7);
        layout.addView(title);
        TextView subtitle=text("Live distance from steps. Local path only when direction is available.",13,0xFFADC7D2);
        layout.addView(subtitle);
        numbers=text("0.00 m  ·  0 steps",27,Color.WHITE);numbers.setPadding(0,dp(16),0,dp(10));layout.addView(numbers);
        status=text("READY",15,0xFFE3D28B);layout.addView(status);
        trail=new PdrTrailView(this);
        LinearLayout.LayoutParams chart=new LinearLayout.LayoutParams(-1,dp(270));
        chart.topMargin=dp(16);chart.bottomMargin=dp(16);
        layout.addView(trail,chart);
        details=text("Tap START to begin.",14,0xFFBFD2DA);
        layout.addView(details);
        sensors=text(bridge.sensorInventory(stepPermissionGranted()),12,0xFF91ADBA);
        sensors.setPadding(0,dp(8),0,dp(8));
        layout.addView(sensors);
        LinearLayout primary=new LinearLayout(this);
        start=button("START",primary);finish=button("STOP",primary);
        layout.addView(primary);
        LinearLayout secondary=new LinearLayout(this);
        gpsButton=button("GNSS ASSIST OFF",secondary);
        stepLengthButton=button("STEP 0.74 m",secondary);
        layout.addView(secondary);
        LinearLayout options=new LinearLayout(this);
        gaitButton=button("WALK",options);
        headingButton=button("DISTANCE ONLY",options);
        layout.addView(options);
        LinearLayout aiRow=new LinearLayout(this);
        aiButton=button("AI CARRY DIAGNOSTIC OFF",aiRow);
        layout.addView(aiRow);
        LinearLayout calibrateRow=new LinearLayout(this);
        calibrationButton=button("CALIBRATE LAST SESSION",calibrateRow);
        layout.addView(calibrateRow);
        LinearLayout bottom=new LinearLayout(this);
        export=button("EXPORT ZIP",bottom);
        Button back=button("BACK",bottom);
        layout.addView(bottom);
        TextView notice=text("Distance uses counted steps and the selected manual stride calibration. "
            +"DISTANCE ONLY is the safe default: no device yaw is treated as your travel direction. "
            +"PHONE FORWARD is experimental: hold screen facing up within 45° of level, top edge forward. "
            +"Valid GNSS course can supply direction in either mode. "
            +"Running has an independent CONFIGURABLE stride guess, not a learned running model. "
            +"No camera; no accuracy guarantee; this screen must stay in the foreground.",13,0xFF85A2AC);
        notice.setPadding(0,dp(16),0,0);layout.addView(notice);
        scroll.addView(layout);setContentView(scroll);
        start.setOnClickListener(v->startSession());
        finish.setOnClickListener(v->finishSession(false));
        export.setOnClickListener(v->exportZip());
        gpsButton.setOnClickListener(v->toggleGps());
        stepLengthButton.setOnClickListener(v->editStepLength());
        gaitButton.setOnClickListener(v->toggleGait());
        headingButton.setOnClickListener(v->togglePhoneHeading());
        aiButton.setOnClickListener(v->{
            if(tinyAi==null)return;
            tinyAiEnabled=!tinyAiEnabled;
            tinyAi.reset();render();
        });
        calibrationButton.setOnClickListener(v->calibrateLastSession());
        back.setOnClickListener(v->finish());
        render();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp,int color){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);return v;
    }
    private Button button(String name,LinearLayout parent){
        Button b=new Button(this);b.setText(name);b.setTextSize(12);b.setAllCaps(false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);
        p.setMargins(0,dp(8),dp(8),0);parent.addView(b,p);return b;
    }
    private double savedStride(boolean running){
        float stored=preferences.getFloat(running?"running_stride_m":"walking_stride_m",
                            running?1.05f:.74f);
        return Float.isFinite(stored) && stored>=.35f && stored<=1.8f
            ? stored : (running?1.05:.74);
    }
    private void saveStride(boolean running,double distance){
        preferences.edit().putFloat(running?"running_stride_m":"walking_stride_m",
                                   (float)distance).apply();
        if(runningMode==running)engine.setStepLengthMeters(distance);
    }
    private void toggleGait(){
        if(engine.isRecording())return;
        runningMode=!runningMode;
        preferences.edit().putBoolean("running_profile",runningMode).apply();
        engine.setStepLengthMeters(savedStride(runningMode));
        render();
    }
    private void togglePhoneHeading(){
        if(engine.isRecording())return;
        // Explicit opt-in is session-local; it is never persisted.
        phoneTopForward=!phoneTopForward;
        render();
    }
    private void calibrateLastSession(){
        if(engine.isRecording())return;
        if(completedSnapshot==null || completedSnapshot.steps<5){
            Toast.makeText(this,"Record at least 5 detected steps, then STOP.",
                           Toast.LENGTH_LONG).show();return;
        }
        EditText measured=new EditText(this);
        measured.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        measured.setSingleLine(true);
        measured.setText("100");
        new AlertDialog.Builder(this).setTitle("Calibrate "+
            (recordedRunning?"RUN":"WALK")+" — "+completedSnapshot.steps+" steps")
            .setMessage("Enter independently measured distance walked DURING the last session (metres). "
               +"The result is saved for future "+(recordedRunning?"RUN":"WALK")+" sessions. "
               +"Do not use an unverified estimated GPS or PDR distance.")
            .setView(measured).setNegativeButton("CANCEL",null)
            .setPositiveButton("CALIBRATE",(dialog,which)->{
                try{
                    double length=PdrCalibration.calibratedStepLength(
                        Double.parseDouble(measured.getText().toString()),
                        completedSnapshot.steps);
                    saveStride(recordedRunning,length);
                    note=String.format(Locale.US,"%s calibrated: %.3f m per step (NEXT sessions)",
                        recordedRunning?"RUN":"WALK",length);
                }catch(Exception ex){
                    new AlertDialog.Builder(this).setMessage(
                        "Calibration rejected: "+ex.getMessage())
                        .setPositiveButton("OK",null).show();
                }
                render();
            }).show();
    }
    /** Foreground-only; old data is exported before another recording overwrites it. */
    void startSession(){
        if(engine.isRecording())return;
        // Request activity permission BEFORE opening a measurement session.
        // Runtime permission UI may pause this Activity; starting before it
        // appeared caused an immediate stop and an empty first recording.
        if(android.os.Build.VERSION.SDK_INT>=29 && !stepPermissionGranted()
                && !activityPermissionRequested){
            activityPermissionRequested=true;
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                ACTIVITY_REQUEST);
            return;
        }
        if(exportReady){
            new AlertDialog.Builder(this).setTitle("Replace last session?")
              .setMessage("Export the previous ZIP before starting a new session.")
              .setNegativeButton("CANCEL",null)
              .setPositiveButton("DISCARD & START",(d,w)->{exportReady=false;openNewSession();})
              .show();return;
        }
        openNewSession();
    }
    private void openNewSession(){
        if(!bridge.hasStepSource(stepPermissionGranted())){
            note="NO STEP SOURCE: accelerometer/STEP_DETECTOR unavailable";
            render();return;
        }
        try {writer=new PdrTraceWriter(this);}
        catch(Exception e){note="Cannot start trace: "+e.getMessage();render();return;}
        completedSnapshot=null;completedPath=null;completedTimingReport="NO SENSOR SAMPLES";
        recordedRunning=runningMode;
        recordedPhoneTopForward=phoneTopForward;
        engine.setStepLengthMeters(savedStride(recordedRunning));
        long startNs=SystemClock.elapsedRealtimeNanos();
        engine.start(startNs);
        if(tinyAi!=null)tinyAi.reset();
        log("START",startNs,0,0,0,0);
        log("STEP_LENGTH",startNs,
            engine.snapshot(SystemClock.elapsedRealtimeNanos()).stepLengthM,0,0,0);
        bridge.start(stepPermissionGranted(),recordedPhoneTopForward);
        if(!bridge.hasActiveStepSource()){
            // Advertised availability is not a successful Android subscription.
            bridge.stop();engine.pause();
            try{writer.close();}catch(Exception ignored){}
            writer=null;exportReady=false;
            note="NO ACTIVE STEP SOURCE: sensor registration failed";
            render();return;
        }
        recordedStepMode=bridge.stepMode();
        autoStopped=false;exportReady=false;note="RECORDING";
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(gpsOn)startGps();
        ui.removeCallbacks(tick);ui.post(tick);
        render();
    }
    private boolean stepPermissionGranted(){
        return android.os.Build.VERSION.SDK_INT<29 ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)==PackageManager.PERMISSION_GRANTED;
    }
    private void log(String type,long t,double x,double y,double z,int accuracy){
        if(writer==null)return;
        try{writer.sample(type,t,x,y,z,accuracy);}
        catch(RuntimeException e){note="STORAGE ERROR; STOPPED";finishSession(true);}
    }
    private void finishSession(boolean background){
        if(!engine.isRecording())return;
        completedTimingReport=bridge.sessionTimingReport();
        // The AI diagnostic remains a separate session and never updates PDR state.
        bridge.stop();stopGps();
        engine.pause();
        completedSnapshot=engine.snapshot(SystemClock.elapsedRealtimeNanos());
        completedPath=engine.path();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try{if(writer!=null)writer.close();exportReady=writer!=null;}
        catch(Exception e){note="Trace error: "+e.getMessage();exportReady=false;}
        if(!note.startsWith("Trace error")&&!note.startsWith("STORAGE ERROR"))
            note=background?"STOPPED · screen backgrounded":"COMPLETE · export ZIP";
        autoStopped=background;
        // Reset the visual-heading assumption after every session. A new
        // session must deliberately re-authorize PHONE FORWARD.
        phoneTopForward=false;
        ui.removeCallbacks(tick);render();
    }
    private void editStepLength(){
        if(engine.isRecording()){
            Toast.makeText(this,"Stop first to change calibration",Toast.LENGTH_LONG).show();return;
        }
        EditText input=new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US,"%.3f",engine.snapshot(SystemClock.elapsedRealtimeNanos()).stepLengthM));
        new AlertDialog.Builder(this).setTitle("Step length in metres")
          .setMessage("Calibrate on a measured 100 m walk: 100 / detected steps. "
             +"Use separate values for walking and running.")
          .setView(input).setNegativeButton("CANCEL",null)
          .setPositiveButton("APPLY",(d,w)->{
              try{
                  double value=Double.parseDouble(input.getText().toString());
                  engine.setStepLengthMeters(value);
                  saveStride(runningMode,value);
              }
              catch(Exception e){Toast.makeText(this,"Use 0.35–1.8 metres",Toast.LENGTH_LONG).show();}
              render();
          }).show();
    }
    /** Explicit source-loss event keeps phone export and Java replay identical. */
    private void invalidateGnssCourse(long timeNs){
        if(engine.invalidateGpsCourse(timeNs))
            log("GPS_INVALID",timeNs,0,0,0,0);
    }
    private void toggleGps(){
        if(gpsOn){
            gpsOn=false;invalidateGnssCourse(SystemClock.elapsedRealtimeNanos());
            stopGps();render();return;
        }
        if(engine.isRecording() &&
           checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
               !=PackageManager.PERMISSION_GRANTED){
            Toast.makeText(this,"STOP first to request location permission; Android would interrupt this session.",
                Toast.LENGTH_LONG).show();
            return;
        }
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_REQUEST);
            return;
        }
        gpsOn=true;if(engine.isRecording())startGps();render();
    }
    private void startGps(){
        if(!gpsOn||gps==null)return;
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,locationListener);}
        catch(Exception e){
            note="GNSS unavailable: "+e.getMessage();
            invalidateGnssCourse(SystemClock.elapsedRealtimeNanos());
        }
    }
    private void stopGps(){
        if(gps!=null)try{gps.removeUpdates(locationListener);}catch(SecurityException ignored){}
    }
    private void exportZip(){
        if(!exportReady||writer==null){Toast.makeText(this,"Stop recording before exporting",Toast.LENGTH_SHORT).show();return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"vector-pdr-"+System.currentTimeMillis()+".zip");
        startActivityForResult(i,EXPORT_REQUEST);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            writer.export(this,data.getData(),
                completedPath!=null?completedPath:engine.path(),
                completedSnapshot!=null?completedSnapshot:engine.snapshot(SystemClock.elapsedRealtimeNanos()),
                recordedStepMode,recordedRunning?"RUN":"WALK",
                recordedPhoneTopForward?"PHONE_FORWARD":"DISTANCE_ONLY",completedTimingReport);
            Toast.makeText(this,"PDR ZIP exported",Toast.LENGTH_LONG).show();
        }catch(Exception e){new AlertDialog.Builder(this).setMessage("Export failed: "+e.getMessage())
               .setPositiveButton("OK",null).show();}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==LOCATION_REQUEST){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED){
            gpsOn=true;if(engine.isRecording())startGps();
        }render();}
        if(request==ACTIVITY_REQUEST){
            // Denial is not fatal: the explicit experimental accel fallback
            // is available. Do not restart a completed session here.
            if(!engine.isRecording() && !exportReady && !isFinishing() && !isDestroyed())
                startSession();
            else if(engine.isRecording()){
                bridge.start(stepPermissionGranted(),recordedPhoneTopForward);
                recordedStepMode=bridge.stepMode();
            }
            render();
        }
    }
    private void render(){
        if(numbers==null)return;
        PdrEngine.Snapshot s=engine.snapshot(SystemClock.elapsedRealtimeNanos());
        numbers.setText(String.format(Locale.US,"%.2f m   ·   %d steps",s.distanceM,s.steps));
        status.setText(note);
        gpsButton.setText(gpsOn?"GNSS ASSIST ON":"GNSS ASSIST OFF");
        stepLengthButton.setText(String.format(Locale.US,"STEP %.2f m",s.stepLengthM));
        start.setEnabled(!s.recording);finish.setEnabled(s.recording);export.setEnabled(exportReady);
        gaitButton.setEnabled(!s.recording);
        headingButton.setEnabled(!s.recording);
        calibrationButton.setEnabled(!s.recording && completedSnapshot!=null &&
                                     completedSnapshot.steps>=5);
        gaitButton.setText(runningMode?"PROFILE: RUN":"PROFILE: WALK");
        headingButton.setText(phoneTopForward?"PHONE FORWARD":"DISTANCE ONLY");
        aiButton.setEnabled(tinyAi!=null);
        aiButton.setText(tinyAi==null?"AI CARRY MODEL UNAVAILABLE":
            (tinyAiEnabled?"AI CARRY DIAGNOSTIC ON":"AI CARRY DIAGNOSTIC OFF"));
        sensors.setText(bridge.sensorInventory(stepPermissionGranted())+"\n"+
                        bridge.sessionDiagnostics()+"\n"+
                        (tinyAi==null?"AI research model unavailable":
                         (tinyAiEnabled?tinyAi.status():"AI carrying-position proxy OFF (experimental)")));
        details.setText(String.format(Locale.US,
            "Steps: %s\nHeading source: %s%s\nLocal magnetic XY (m): %.1f, %.1f\n"
           +"Steps without usable direction: %d / %d\n"
           +"Cadence: %.0f steps/min · estimated speed: %.2f m/s\n"
           +"Pressure elevation change: %s\nGNSS: %s (course only)\n"
           +"Phone heading: %s · never a measured body heading",
            recordedStepMode,s.headingSource.name(),
            Double.isFinite(s.headingDeg)?String.format(Locale.US," (%.0f°)",s.headingDeg):"",
            s.eastM,s.northM,s.stepsWithoutHeading,s.steps,
            s.cadenceStepsPerMin,s.estimatedSpeedMps,
            Double.isFinite(s.elevationM)?String.format(Locale.US,"%.1f m (uncalibrated)",s.elevationM):"unknown",
            gpsOn?"requested":"off",phoneTopForward?"opted in":"disabled"));
        trail.setPath(engine.path());
    }
    @Override protected void onPause(){
        finishSession(true);
        super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacks(tick);stopGps();bridge.stop();
        if(writer!=null)try{writer.close();}catch(Exception ignored){}
        super.onDestroy();
    }
}
