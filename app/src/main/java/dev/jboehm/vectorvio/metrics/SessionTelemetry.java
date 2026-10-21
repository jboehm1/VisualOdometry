package dev.jboehm.vectorvio.metrics;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.FrameMetrics;
import android.view.Window;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Opt-out-free session diagnostics: no network, permissions, images or sensor control.
 * One resource sample/second; PSS every five seconds and thermal headroom every ten.
 * Motion/GL callbacks only update atomic counters and bounded histograms.
 */
public final class SessionTelemetry implements AutoCloseable {
    public static final String SCHEMA="vector-session-resources/1";
    private static final String[] COLUMNS=("schema,t_ns,wall_ms,event,segment,active_interval_s,process_cpu_ms,cpu_core_percent,cpu_capacity_percent,"+
        "logical_processors,java_heap_used_bytes,java_heap_committed_bytes,java_heap_limit_bytes,native_heap_allocated_bytes,"+
        "process_rss_kib,process_threads,process_pss_kib,process_private_dirty_kib,pss_age_s,system_available_bytes,system_low_memory,"+
        "battery_percent,battery_voltage_mv,battery_temperature_c,battery_status,battery_plugged,battery_health,"+
        "battery_current_ua,battery_average_current_ua,battery_charge_uah,battery_energy_nwh,device_discharge_power_mw,"+
        "thermal_status,thermal_headroom,thermal_headroom_age_s,power_save,interactive,brightness_setting,brightness_mode,"+
        "uid_rx_bytes,uid_tx_bytes,proc_read_bytes,proc_write_bytes,proc_rchar_bytes,proc_wchar_bytes,"+
        "accel_callbacks,gyro_callbacks,rotation_callbacks,accepted_steps,distinct_camera_frames,tracked_camera_frames,"+
        "untracked_camera_frames,duplicate_camera_callbacks,tracking_losses,tracking_recoveries,pose_exports,view_draw_callbacks,"+
        "window_frame_callbacks,window_metrics_dropped,window_gpu_duration_samples,invalid_sensor_lag_samples,collector_wall_ms").split(",");
    private static final double[] LATENCY_MS={.1,.25,.5,1,2,5,10,16.667,25,33.334,50,100,250,500,1000,Double.POSITIVE_INFINITY};
    private final Context context;
    private final File csv,metadata;
    private final BufferedWriter out;
    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> pending;
    private final ConcurrentHashMap<String,AtomicLong> counts=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,AtomicLongArray> histograms=new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,String> missing=new ConcurrentHashMap<>();
    private final AtomicLong lastFrame=new AtomicLong();
    private final AtomicInteger tracking=new AtomicInteger(-1);
    private volatile boolean active,closed,framePeriodReady;
    private Window window;
    private Window.OnFrameMetricsAvailableListener windowListener;
    private final JSONObject meta=new JSONObject();
    private int segment,samples;
    private long lastNs,lastCpuMs,lastPssNs,lastHeadroomNs;
    private Integer pss,dirty;
    private Double headroom;
    private double activeSeconds,cpuMs;
    private String error="";

    /** Base is a filename prefix; files are <base>_resources.csv and metadata JSON. */
    public SessionTelemetry(Context ctx,File base,String engine,String configuration,Window w)throws IOException {
        context=ctx.getApplicationContext();
        csv=new File(base.getPath()+"_resources.csv");
        metadata=new File(base.getPath()+"_resources_metadata.json");
        out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csv),StandardCharsets.UTF_8),16384);
        out.write(String.join(",",COLUMNS));out.newLine();out.flush();
        worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"vector-resources");t.setDaemon(true);return t;});
        try {
            meta.put("schema",SCHEMA).put("engine",engine).put("configuration",configuration)
                .put("manufacturer",Build.MANUFACTURER).put("model",Build.MODEL).put("hardware",Build.HARDWARE)
                .put("build_fingerprint",Build.FINGERPRINT).put("sdk",Build.VERSION.SDK_INT)
                .put("abis",String.join(",",Build.SUPPORTED_ABIS)).put("pid",Process.myPid())
                .put("nominal_sample_period_s",1).put("pss_period_s",5).put("thermal_headroom_period_s",10)
                .put("clock","Android elapsedRealtimeNanos BOOTTIME; wall_ms is informational")
                .put("cpu_scope","This whole app process including rendering and collector; 100 percent = one logical core. External AR services excluded.")
                .put("power_scope","Whole device battery terminal estimate, not app-attributed or externally calibrated energy; discharge positive, charging negative.")
                .put("temperature_scope","Battery temperature only; not CPU/GPU temperature")
                .put("gpu_scope","Window GPU duration if available; excludes separate SurfaceView GL workload. No portable GPU utilization/frequency/temperature measurement.")
                .put("latency_scope","Observed callback delay / host wall durations, not photon-to-pose or GPU completion. Histogram quantiles are bucket upper bounds.")
                .put("sampling_overhead","Included in app CPU. collector_wall_ms records resource probe cost; no claim of zero overhead.")
                .put("accuracy_scope","Resources and availability do not establish metric trajectory accuracy. PDR and ARCore remain different estimators.")
                .put("missing_values","Empty CSV cells and JSON unavailable reasons; never replace unsupported readings by measured zero.")
                .put("gpu_utilization_percent",JSONObject.NULL).put("cpu_temperature_c",JSONObject.NULL)
                .put("gpu_temperature_c",JSONObject.NULL).put("gpu_frequency_hz",JSONObject.NULL);
            PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            meta.put("app_version",p.versionName).put("app_version_code",(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode));
        }catch(Exception e){missing.put("app_metadata",e.getClass().getSimpleName());}
        try{meta.put("arcore_package_version",context.getPackageManager().getPackageInfo("com.google.ar.core",0).versionName);}
        catch(Exception e){missing.put("arcore_package_version","Package unavailable or not visible to this app");}
        missing.put("gpu_utilization_percent","No portable unprivileged Android application API");
        missing.put("cpu_temperature_c","No portable unprivileged per-CPU temperature API");
        missing.put("gpu_temperature_c","No portable unprivileged GPU temperature API");
        missing.put("gpu_frequency_hz","No portable unprivileged GPU frequency API");
        try(InputStream in=context.getAssets().open("build_provenance.json")){
            java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();byte[] buf=new byte[1024];int n;
            while((n=in.read(buf))!=-1){if(b.size()+n>16384)throw new IOException("Oversized build provenance");b.write(buf,0,n);}
            meta.put("build_provenance",new JSONObject(b.toString("UTF-8")));
        }catch(Exception e){missing.put("build_provenance","Not packaged in this local/test build");}
        attachWindow(w);writeMetadata();
    }
    private void attachWindow(Window w) {
        if(w==null){missing.put("window_frame_metrics","No Activity window");return;}
        try {
            windowListener=(win,f,dropped)->{
                if(!active)return;
                count("window_frame_callbacks");add("window_metrics_dropped",dropped);
                duration("window_total",f.getMetric(FrameMetrics.TOTAL_DURATION));
                duration("window_draw",f.getMetric(FrameMetrics.DRAW_DURATION));
                if(Build.VERSION.SDK_INT>=31){long gpu=f.getMetric(FrameMetrics.GPU_DURATION);
                    if(gpu>=0){count("window_gpu_duration_samples");duration("window_gpu",gpu);}}
            };
            w.addOnFrameMetricsAvailableListener(windowListener,new Handler(Looper.getMainLooper()));window=w;
        }catch(RuntimeException e){missing.put("window_frame_metrics",e.getClass().getSimpleName());}
    }
    public void resume(){resume(null);}
    public synchronized void resume(String configuration) {
        if(closed||active)return;
        try{
            org.json.JSONArray segments=meta.optJSONArray("segment_configurations");
            if(segments==null){segments=new org.json.JSONArray();meta.put("segment_configurations",segments);}
            segments.put(new JSONObject().put("segment",segment+1).put("t_ns",SystemClock.elapsedRealtimeNanos())
                .put("configuration",configuration==null?meta.optString("configuration"):configuration));
        }catch(org.json.JSONException e){error=e.toString();}
        active=true;segment++;lastNs=0;lastCpuMs=0;tracking.set(-1);framePeriodReady=false;
        safeSample("RESUME");
        pending=worker.scheduleAtFixedRate(()->{synchronized(this){if(active&&!closed)safeSample("SAMPLE");}},1,1,TimeUnit.SECONDS);
    }
    public synchronized void pause() {
        if(!active||closed)return;
        if(pending!=null)pending.cancel(false);
        safeSample("PAUSE");active=false;writeMetadata();
    }
    public synchronized void flush() {try{out.flush();writeMetadata();}catch(IOException e){error=e.toString();}}
    public void count(String key){if(active)add(key,1);}
    private void add(String key,long n){if(n>=0)counts.computeIfAbsent(key,k->new AtomicLong()).addAndGet(n);}
    public void sensor(String type,long timestampNs) {
        if(!active)return;
        if(type.contains("ACCEL"))count("accel_callbacks");
        else if(type.contains("GYRO"))count("gyro_callbacks");
        else if(type.contains("ROTATION"))count("rotation_callbacks");
        else return;
        long lag=SystemClock.elapsedRealtimeNanos()-timestampNs;
        if(lag>=0&&lag<=10_000_000_000L)duration("sensor_callback_lag",lag);
        else count("invalid_sensor_lag_samples");
    }
    public void frame(long timestampNs,boolean tracked) { frame(timestampNs,tracked,"UNKNOWN"); }
    public void frame(long timestampNs,boolean tracked,String failure) {
        if(!active||timestampNs<=0)return;
        long old=lastFrame.get();
        if(timestampNs<=old){count("duplicate_camera_callbacks");return;}
        lastFrame.set(timestampNs);count("distinct_camera_frames");
        if(old>0&&framePeriodReady)duration("camera_frame_period",timestampNs-old);
        framePeriodReady=true;
        if(!tracked)count("tracking_failure_"+failure);
        count(tracked?"tracked_camera_frames":"untracked_camera_frames");
        int previous=tracking.getAndSet(tracked?1:0);
        if(previous==1&&!tracked)count("tracking_losses");
        if(previous==0&&tracked)count("tracking_recoveries");
        // Camera timestamp is deliberately not assumed to be sensor BOOTTIME here.
    }
    public void duration(String name,long nanos) {
        if(!active||nanos<0)return;
        AtomicLongArray bins=histograms.computeIfAbsent(name,k->new AtomicLongArray(LATENCY_MS.length));
        double ms=nanos/1e6;
        for(int i=0;i<LATENCY_MS.length;i++)if(ms<=LATENCY_MS[i]){bins.incrementAndGet(i);break;}
    }
    private void safeSample(String event) {
        try{sample(event);}catch(RuntimeException e){error="Resource sample failed: "+e;writeMetadata();}
    }
    private void sample(String event) {
        long probeStart=SystemClock.elapsedRealtimeNanos();
        LinkedHashMap<String,Object> v=new LinkedHashMap<>();
        long now=probeStart,cpu=Process.getElapsedCpuTime();
        double dt=lastNs>0?(now-lastNs)/1e9:0;
        Long dc=lastNs>0&&cpu>=lastCpuMs?cpu-lastCpuMs:null;
        int cores=Runtime.getRuntime().availableProcessors();
        v.put("schema",1);v.put("t_ns",now);v.put("wall_ms",System.currentTimeMillis());v.put("event",event);
        v.put("segment",segment);v.put("active_interval_s",dt);v.put("process_cpu_ms",cpu);
        Double percent=dc==null?null:MetricMath.cpuCorePercent(now-lastNs,dc);
        v.put("cpu_core_percent",percent);v.put("cpu_capacity_percent",percent==null?null:percent/cores);
        v.put("logical_processors",cores);activeSeconds+=dt;if(dc!=null)cpuMs+=dc;
        lastNs=now;lastCpuMs=cpu;
        Runtime rt=Runtime.getRuntime();
        v.put("java_heap_used_bytes",rt.totalMemory()-rt.freeMemory());v.put("java_heap_committed_bytes",rt.totalMemory());
        v.put("java_heap_limit_bytes",rt.maxMemory());v.put("native_heap_allocated_bytes",Debug.getNativeHeapAllocatedSize());
        Map<String,Long> status=proc("/proc/self/status",true);
        v.put("process_rss_kib",status.get("VmRSS"));v.put("process_threads",status.get("Threads"));
        Map<String,Long> io=proc("/proc/self/io",false);
        for(String[] pair:new String[][]{{"proc_read_bytes","read_bytes"},{"proc_write_bytes","write_bytes"},{"proc_rchar_bytes","rchar"},{"proc_wchar_bytes","wchar"}})v.put(pair[0],io.get(pair[1]));
        if(lastPssNs==0||now-lastPssNs>=5_000_000_000L){
            try{Debug.MemoryInfo m=new Debug.MemoryInfo();Debug.getMemoryInfo(m);pss=m.getTotalPss();dirty=m.getTotalPrivateDirty();lastPssNs=now;}
            catch(RuntimeException e){missing.put("process_pss_kib",e.getClass().getSimpleName());}
        }
        v.put("process_pss_kib",pss);v.put("process_private_dirty_kib",dirty);v.put("pss_age_s",lastPssNs==0?null:(now-lastPssNs)/1e9);
        try{ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(m);
            v.put("system_available_bytes",m.availMem);v.put("system_low_memory",m.lowMemory?1:0);
        }catch(RuntimeException e){missing.put("system_memory",e.getClass().getSimpleName());}
        try {
            Intent b=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            BatteryManager bm=(BatteryManager)context.getSystemService(Context.BATTERY_SERVICE);
            if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
                v.put("battery_percent",level>=0&&scale>0?100.*level/scale:null);
                int mv=b.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),temp=b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
                v.put("battery_voltage_mv",mv>0?mv:null);v.put("battery_temperature_c",temp==Integer.MIN_VALUE?null:temp/10.);
                v.put("battery_status",b.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN));
                v.put("battery_plugged",b.getIntExtra(BatteryManager.EXTRA_PLUGGED,-1));v.put("battery_health",b.getIntExtra(BatteryManager.EXTRA_HEALTH,BatteryManager.BATTERY_HEALTH_UNKNOWN));
                Long current=battery(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_NOW,"battery_current_ua",true);
                v.put("battery_current_ua",current);v.put("device_discharge_power_mw",current==null?null:MetricMath.batteryPowerMilliwatts(current,mv));
            }
            v.put("battery_average_current_ua",battery(bm,BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE,"battery_average_current_ua",true));
            v.put("battery_charge_uah",battery(bm,BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER,"battery_charge_uah",true));
            v.put("battery_energy_nwh",battery(bm,BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER,"battery_energy_nwh",false));
        }catch(RuntimeException e){missing.put("battery",e.getClass().getSimpleName());}
        try {
            PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
            v.put("power_save",pm.isPowerSaveMode()?1:0);v.put("interactive",pm.isInteractive()?1:0);
            if(Build.VERSION.SDK_INT>=29)v.put("thermal_status",pm.getCurrentThermalStatus());
            else missing.put("thermal_status","Requires API 29");
            if(Build.VERSION.SDK_INT>=30){
                if(lastHeadroomNs==0||now-lastHeadroomNs>=10_000_000_000L){
                    float h=pm.getThermalHeadroom(0);lastHeadroomNs=now;
                    headroom=Float.isFinite(h)?(double)h:null;
                    if(headroom==null)missing.put("thermal_headroom","Unavailable, unsupported or rate limited");
                }
                v.put("thermal_headroom",headroom);v.put("thermal_headroom_age_s",lastHeadroomNs==0?null:(now-lastHeadroomNs)/1e9);
            }else missing.put("thermal_headroom","Requires API 30");
        }catch(RuntimeException e){missing.put("thermal",e.getClass().getSimpleName());}
        try{v.put("brightness_setting",Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS));
            v.put("brightness_mode",Settings.System.getInt(context.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE));
        }catch(Exception e){missing.put("brightness_setting","Not reported");}
        try{long rx=TrafficStats.getUidRxBytes(Process.myUid()),tx=TrafficStats.getUidTxBytes(Process.myUid());
            v.put("uid_rx_bytes",rx<0?null:rx);v.put("uid_tx_bytes",tx<0?null:tx);
        }catch(RuntimeException e){missing.put("uid_network_bytes",e.getClass().getSimpleName());}
        for(String c:COLUMNS)if(counts.containsKey(c))v.put(c,counts.get(c).get());
        // Supported event counters can legitimately be zero, unlike absent resource probes.
        for(String c:Arrays.copyOfRange(COLUMNS,45,COLUMNS.length-1))v.putIfAbsent(c,0L);
        v.put("collector_wall_ms",(SystemClock.elapsedRealtimeNanos()-probeStart)/1e6);
        try{StringJoiner row=new StringJoiner(",");for(String c:COLUMNS)row.add(MetricMath.csv(v.get(c)));out.write(row.toString());out.newLine();out.flush();samples++;}
        catch(IOException e){error=e.toString();}
    }
    private Long battery(BatteryManager b,int property,String key,boolean integer) {
        long x=integer?b.getIntProperty(property):b.getLongProperty(property);
        if(x==Long.MIN_VALUE||x==Integer.MIN_VALUE){missing.put(key,"Android property unsupported");return null;}
        return x;
    }
    private Map<String,Long> proc(String path,boolean status) {
        Map<String,Long> r=new HashMap<>();
        try(BufferedReader f=new BufferedReader(new FileReader(path))){String line;int n=0;
            while((line=f.readLine())!=null&&n++<256){int colon=line.indexOf(':');if(colon<0)continue;
                String key=line.substring(0,colon),value=line.substring(colon+1).trim().split("\\s+")[0];
                if(!status||key.equals("VmRSS")||key.equals("Threads"))try{r.put(key,Long.parseLong(value));}catch(NumberFormatException ignored){}
            }
        }catch(IOException|SecurityException e){missing.put(path,"Not readable: "+e.getClass().getSimpleName());}
        return r;
    }
    private void writeMetadata() {
        try {
            JSONObject totals=new JSONObject(),hist=new JSONObject();
            for(Map.Entry<String,AtomicLong> c:counts.entrySet())totals.put(c.getKey(),c.getValue().get());
            for(Map.Entry<String,AtomicLongArray> h:histograms.entrySet()){
                org.json.JSONArray bins=new org.json.JSONArray();for(int i=0;i<LATENCY_MS.length;i++)bins.put(h.getValue().get(i));hist.put(h.getKey(),bins);
            }
            org.json.JSONArray bounds=new org.json.JSONArray();for(double x:LATENCY_MS)bounds.put(Double.isFinite(x)?x:JSONObject.NULL);
            meta.put("samples",samples).put("segments",segment).put("active_duration_s",activeSeconds)
                .put("active_process_cpu_ms",cpuMs).put("counters",totals).put("histogram_bounds_ms",bounds)
                .put("latency_histograms",hist).put("unavailable",new JSONObject(missing)).put("collector_error",error)
                .put("status",error.isEmpty()?"RECORDED":"PARTIAL_IO_ERROR");
            try(Writer f=new OutputStreamWriter(new FileOutputStream(metadata),StandardCharsets.UTF_8)){f.write(meta.toString(2));}
        }catch(Exception e){error=e.toString();}
    }
    @Override public synchronized void close() {
        if(closed)return;pause();closed=true;worker.shutdownNow();
        if(window!=null&&windowListener!=null)try{window.removeOnFrameMetricsAvailableListener(windowListener);}catch(RuntimeException ignored){}
        try{out.close();}catch(IOException e){error=e.toString();}writeMetadata();
    }
}
