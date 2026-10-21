package dev.jboehm.vectorvio;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Actual S0 phone-only, foreground-only sensor inventory + measurement run.
 * Does not take a camera, request location, start navigation or load models.
 * In particular raw IMU samples are NOT translated into position/distance.
 */
public final class SensorLabActivity extends Activity implements SensorEventListener {
    private static final int EXPORT_REQUEST=921;
    private static final int MAX_SAMPLES=180_000; // bounded on-device capture, not a guarantee of duration
    private static final int[] SENSOR_TYPES={
        Sensor.TYPE_ACCELEROMETER,Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
        Sensor.TYPE_MAGNETIC_FIELD,Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
        Sensor.TYPE_PRESSURE,Sensor.TYPE_LIGHT,Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,Sensor.TYPE_GAME_ROTATION_VECTOR
    };
    private SensorManager manager;
    private HandlerThread sensorWorker;
    private Handler worker;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private TextView status,live,inventory;
    private Button start,stop,export;
    private boolean recording;
    private boolean resumed;
    private int sampleCount;
    private long lastEventNs,initialSensorNs;
    private final HashMap<Integer,Long> counts=new HashMap<>();
    private final HashMap<Integer,Long> lastTimestamps=new HashMap<>();
    private final HashMap<Integer,Double> measuredHz=new HashMap<>();
    private final HashMap<Integer,float[]> lastValues=new HashMap<>();
    private BufferedWriter csv;
    private File directory,lastCompleted;
    private JSONObject capabilities;
    private String outcome="Ready: probe available; no recording active.";
    private final Runnable renderTask=new Runnable(){
        @Override public void run(){
            if(!resumed)return;
            render();
            ui.postDelayed(this,500);
        }
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF091A26);
        getWindow().setNavigationBarColor(0xFF091A26);
        manager=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(27),dp(18),dp(28));
        page.setBackgroundColor(0xFF081923);
        page.addView(text("SENSOR LAB · PHONE ONLY",25,0xFF79F6DA));
        page.addView(text("Galaxy S21 / Android · hardware inventory, timestamps and local raw evidence. No camera, no recording without START, no cloud.",13,0xFFB2CDDA));
        status=text("READY",17,0xFFFFC98C);status.setPadding(0,dp(18),0,dp(13));page.addView(status);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        start=button("START SENSORS",row);
        stop=button("STOP",row);
        page.addView(row);
        LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
        export=button("EXPORT ZIP",row2);
        Button refresh=button("PROBE DEVICE",row2);
        page.addView(row2);
        live=text("No active sensor measurements.",14,Color.WHITE);
        live.setPadding(0,dp(18),0,dp(9));
        page.addView(live);
        inventory=text("Reading device capabilities…",12,0xFFA2C0CF);
        inventory.setPadding(0,dp(12),0,dp(20));
        page.addView(inventory);
        page.addView(text("IMPORTANT: IMU / gyro only do NOT determine position or bicycle speed. "
            +"Magnetic field and air pressure do not supply an absolute location. "
            +"Orientation is provisional when tracking is lost. This is a diagnostic lab, "
            +"NOT a navigation system for smoke, fire, evacuation or rescue. "
            +"Even without photos, exported magnetometer and barometer signatures can reveal a location. "
            +"The ZIP is only created when you explicitly export.",13,0xFFFFCD8C));
        Button back=button("BACK",page);
        back.setOnClickListener(v->finish());
        scroll.addView(page);
        setContentView(scroll);
        start.setOnClickListener(v->begin());
        stop.setOnClickListener(v->stopRun("Stopped by user"));
        refresh.setOnClickListener(v->probe());
        export.setOnClickListener(v->{
            if(lastCompleted==null){Toast.makeText(this,"Stop a session before export",Toast.LENGTH_LONG).show();return;}
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_TITLE,lastCompleted.getName()+".zip");
            startActivityForResult(intent,EXPORT_REQUEST);
        });
        probe();render();
    }
    private int dp(int d){return (int)(getResources().getDisplayMetrics().density*d+.5f);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setLineSpacing(dp(4),1f);return t;
    }
    private Button button(String title,LinearLayout row){
        Button b=new Button(this);b.setText(title);b.setTextSize(11);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF225163));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(53),1);
        lp.setMargins(dp(2),dp(5),dp(2),dp(5));
        row.addView(b,lp);return b;
    }
    private synchronized void probe(){
        if(recording){outcome="Stop session before probing capabilities.";render();return;}
        try {
            capabilities=SensorCapabilityReport.collect(this);
            StringBuilder display=new StringBuilder("RUNTIME CAPABILITIES (presence ≠ measured quality)\n");
            display.append("Model: ").append(capabilities.optString("phone_model"))
                .append(" · Android SDK ").append(capabilities.optInt("android_sdk")).append('\n');
            display.append("Sensors reported: ").append(capabilities.optJSONArray("sensors").length())
                .append(" · Camera2 modules: ").append(capabilities.optJSONArray("cameras").length()).append('\n');
            JSONObject feature=capabilities.optJSONObject("features");
            if(feature!=null)display.append("Wi-Fi RTT hardware: ").append(feature.optBoolean("wifi_rtt_chipset"))
                .append(" (requires compatible AP)\n");
            display.append("Detailed limits / ranges are exported in capabilities.json\n");
            for(int type:SENSOR_TYPES){
                Sensor sensor=manager==null?null:manager.getDefaultSensor(type);
                display.append(sensor==null?"– NOT AVAILABLE: ":"+ ").append(sensorLabel(type));
                if(sensor!=null)display.append(" · ").append(sensor.getVendor())
                    .append(" · min ").append(sensor.getMinDelay()).append(" μs");
                display.append('\n');
            }
            inventory.setText(display.toString());
            outcome="Device inventory ready; press START to measure sample delivery.";
        }catch(Exception e){outcome="Probe failed: "+e.getClass().getSimpleName();}
        render();
    }
    private static String sensorLabel(int type){
        switch(type){
            case Sensor.TYPE_ACCELEROMETER:return "Accelerometer";
            case Sensor.TYPE_GYROSCOPE:return "Gyroscope";
            case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:return "Raw accelerometer";
            case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:return "Raw gyroscope";
            case Sensor.TYPE_MAGNETIC_FIELD:return "Magnetic field";
            case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:return "Uncalibrated magnetic";
            case Sensor.TYPE_PRESSURE:return "Air pressure";
            case Sensor.TYPE_LIGHT:return "Ambient light";
            case Sensor.TYPE_GRAVITY:return "Android gravity (FUSED)";
            case Sensor.TYPE_LINEAR_ACCELERATION:return "Android linear acceleration (FUSED)";
            case Sensor.TYPE_GAME_ROTATION_VECTOR:return "Game rotation vector (FUSED)";
            default:return "Sensor "+type;
        }
    }
    private synchronized void begin(){
        if(recording)return;
        if(manager==null){outcome="No SensorManager";render();return;}
        File dir=new File(getFilesDir(),"sensor-lab/run-"+System.currentTimeMillis());
        if(!dir.mkdirs()){outcome="Cannot create private run directory";render();return;}
        BufferedWriter w=null;
        HandlerThread ht=null;
        try{
            // Snapshot before recording; do not mislabel advertised rates as actual delivery.
            capabilities=SensorCapabilityReport.collect(this);
            writeJson(new File(dir,"capabilities.json"),capabilities);
            w=new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(new File(dir,"sensor_samples.csv")),StandardCharsets.UTF_8),32768);
            w.write("sensor_type,sensor_label,sensor_timestamp_ns,elapsed_arrival_ns,x,y,z,w,accuracy\n");
            ht=new HandlerThread("S21SensorLabAcquisition");
            ht.start();
            Handler h=new Handler(ht.getLooper());
            counts.clear();lastTimestamps.clear();measuredHz.clear();lastValues.clear();
            sampleCount=0;lastEventNs=initialSensorNs=0;
            directory=dir;csv=w;sensorWorker=ht;worker=h;recording=true;
            int registered=0;
            for(int type:SENSOR_TYPES){
                Sensor sensor=manager.getDefaultSensor(type);
                // High-rate permission is not requested; measured actual rate is logged.
                if(sensor!=null && manager.registerListener(this,sensor,10_000,0,worker))registered++;
            }
            if(registered==0)throw new IOException("No sensors registered");
            outcome="RECORDING · "+registered+" available sensor types; timestamps are hardware-reported.";
        }catch(Exception e){
            recording=false;
            if(manager!=null)manager.unregisterListener(this);
            if(ht!=null)ht.quitSafely();
            if(w!=null)try{w.close();}catch(IOException ignored){}
            csv=null;directory=null;worker=null;sensorWorker=null;
            outcome="Cannot start run: "+e.getClass().getSimpleName();
        }
        render();
    }
    @Override public synchronized void onSensorChanged(SensorEvent e){
        if(!recording||csv==null)return;
        if(e==null||e.sensor==null||e.values.length==0)return;
        try{
            int type=e.sensor.getType();
            Long previous=lastTimestamps.get(type);
            if(previous!=null && e.timestamp<=previous)return; // stale or duplicate
            double hz=previous==null?0:1e9/(double)(e.timestamp-previous);
            if(hz>0&&hz<1000){
                Double smooth=measuredHz.get(type);
                measuredHz.put(type,smooth==null||smooth==0?hz:.85*smooth+.15*hz);
            }
            lastTimestamps.put(type,e.timestamp);
            counts.put(type,counts.containsKey(type)?counts.get(type)+1:1L);
            lastValues.put(type,e.values.clone());
            if(initialSensorNs==0)initialSensorNs=e.timestamp;
            lastEventNs=e.timestamp;
            long arrival=SystemClock.elapsedRealtimeNanos();
            csv.write(type+","+sensorLabel(type).replace(',',' ')+","+e.timestamp+","+arrival);
            for(int i=0;i<4;i++)csv.write(i<e.values.length?","+e.values[i]:",");
            csv.write(","+e.accuracy+"\n");
            sampleCount++;
            if(sampleCount>=MAX_SAMPLES)runOnUiThread(()->stopRun("Sample limit reached"));
        }catch(IOException e1){
            runOnUiThread(()->stopRun("STORAGE ERROR: capture stopped"));
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    private static void writeJson(File path,JSONObject json)throws IOException{
        try(Writer w=new OutputStreamWriter(new FileOutputStream(path),StandardCharsets.UTF_8)){
            w.write(json.toString(2));w.write("\n");
        }catch(org.json.JSONException e){throw new IOException("JSON serialization failure",e);}
    }
    private synchronized void stopRun(String reason){
        if(!recording)return;
        recording=false;
        manager.unregisterListener(this);
        if(sensorWorker!=null)sensorWorker.quitSafely();
        sensorWorker=null;worker=null;
        try{if(csv!=null){csv.flush();csv.close();}}catch(IOException ex){reason+="; CSV close failure";}
        csv=null;
        JSONObject summary=new JSONObject();
        try{
            summary.put("schema",1).put("engine","raw_Android_SensorManager_NO_VIO")
                .put("samples_written",sampleCount)
                .put("first_sensor_timestamp_ns",initialSensorNs)
                .put("last_sensor_timestamp_ns",lastEventNs)
                .put("stop_reason",reason)
                .put("ground_truth","NONE")
                .put("position_status","POSITION_UNKNOWN")
                .put("camera_used",false);
            JSONObject actual=new JSONObject();
            for(Map.Entry<Integer,Long> e:counts.entrySet()){
                JSONObject one=new JSONObject();
                one.put("samples",e.getValue());
                one.put("delivered_hz_ema",measuredHz.getOrDefault(e.getKey(),0.));
                actual.put(sensorLabel(e.getKey()),one);
            }
            summary.put("measured",actual);
            if(directory!=null)writeJson(new File(directory,"summary.json"),summary);
            lastCompleted=directory;
            outcome=reason+" · "+sampleCount+" raw samples saved on phone.";
        }catch(Exception ex){outcome="Stop: summary write failed; CSV may remain in private storage.";}
        directory=null;
        render();
    }
    private synchronized void render(){
        if(status==null)return;
        status.setText(outcome);
        if(start!=null)start.setEnabled(!recording);
        if(stop!=null)stop.setEnabled(recording);
        if(export!=null)export.setEnabled(!recording&&lastCompleted!=null);
        StringBuilder msg=new StringBuilder();
        msg.append(recording?"LIVE RAW SENSOR MEASUREMENTS":"NO LIVE SAMPLING")
            .append(" · ").append(sampleCount).append(" samples\n")
            .append("POSITION: UNKNOWN (NO CAMERA / NO MAP)\n")
            .append("Derived distance: NOT AVAILABLE\n");
        for(int type:SENSOR_TYPES){
            Long count=counts.get(type);
            if(count==null)continue;
            msg.append(sensorLabel(type)).append(": ").append(count)
                .append(" samples · ").append(String.format(Locale.US,"%.1f",measuredHz.getOrDefault(type,0.)))
                .append(" Hz (observed)");
            float[] vector=lastValues.get(type);
            if(vector!=null&&vector.length>=3)msg.append(String.format(Locale.US,
                "  [%.2f, %.2f, %.2f]",vector[0],vector[1],vector[2]));
            else if(vector!=null&&vector.length>0)msg.append(String.format(Locale.US,
                "  %.2f",vector[0]));
            msg.append('\n');
        }
        if(live!=null)live.setText(msg.toString());
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;ui.removeCallbacks(renderTask);ui.post(renderTask);
    }
    @Override protected void onPause(){
        resumed=false;ui.removeCallbacks(renderTask);
        stopRun("APP PAUSED · session ended, no hidden background sensing");
        super.onPause();
    }
    @Override protected void onDestroy(){
        ui.removeCallbacks(renderTask);
        stopRun("APP DESTROYED");
        super.onDestroy();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_REQUEST||result!=RESULT_OK||data==null||data.getData()==null)return;
        File completed=lastCompleted;
        if(completed==null||!completed.isDirectory())return;
        Uri dest=data.getData();
        new Thread(()->{
            String message;
            try(java.io.OutputStream out=getContentResolver().openOutputStream(dest,"w")){
                if(out==null)throw new IOException("Selected output is not writable");
                try(ZipOutputStream zip=new ZipOutputStream(out)){
                    long total=0;
                    for(String name:new String[]{"capabilities.json","sensor_samples.csv","summary.json"}){
                        File source=new File(completed,name);
                        if(!source.isFile())continue;
                        if(source.length()>40_000_000L || (total+=source.length())>40_000_000L)
                            throw new IOException("Export exceeds 40MB safety limit");
                        zip.putNextEntry(new ZipEntry(name));
                        try(InputStream in=new BufferedInputStream(new FileInputStream(source))){
                            byte[] buf=new byte[16_384];int n;
                            while((n=in.read(buf))!=-1)zip.write(buf,0,n);
                        }
                        zip.closeEntry();
                    }
                }
                message="Sensor ZIP exported — inspect privacy before sharing";
            }catch(Exception ex){message="Export failed: "+ex.getClass().getSimpleName();}
            final String resultMessage=message;
            runOnUiThread(()->Toast.makeText(this,resultMessage,Toast.LENGTH_LONG).show());
        },"S21SensorExport").start();
    }
}
