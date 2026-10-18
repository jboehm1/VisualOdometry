package dev.jboehm.vectorvio.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Android-only acquisition adapter. Core estimator has zero Android dependencies.
 * Sensor events already use the elapsedRealtimeNanos time base; do not replace it
 * with wall-clock. No camera, ARCore, Google Play Services or network needed.
 */
public final class PdrSensorBridge implements SensorEventListener {
    public interface Callback {
        void sample(String type, long tNs, double x, double y, double z, int accuracy);
        void step(long tNs);
        void rotationVector(long tNs,float[] values,int accuracy);
    }
    private final SensorManager manager;
    private final PdrEngine engine;
    private final Callback callback;
    private final SensorTimingMonitor timing=new SensorTimingMonitor();
    private final float[] r=new float[9], orientation=new float[3];
    private boolean registered, useStepDetector, accelerometerRegistered, magneticReliable, allowDeviceHeading;
    private long accelerationSamples, rotationSamples, magneticSamples, stepSensorSamples, fallbackSteps;
    private long firstSampleNs=Long.MIN_VALUE, latestSampleNs=Long.MIN_VALUE;
    private long latestMagneticNs=Long.MIN_VALUE;
    private static final long MAX_MAGNET_AGE_NS=2_000_000_000L;
    private static final float MIN_WORLD_UP=0.70710678f; // cos(45 degrees)

    static boolean usablePhoneHeading(boolean optedIn,float worldUp,
                                      boolean magneticQuality,int accuracy,
                                      long magneticAgeNs){
        return optedIn && Float.isFinite(worldUp) && worldUp>=MIN_WORLD_UP
             && magneticQuality && magneticAgeNs>=0 && magneticAgeNs<=MAX_MAGNET_AGE_NS
             && accuracy!=SensorManager.SENSOR_STATUS_UNRELIABLE;
    }
    private String stepMode="ACCEL FALLBACK";
    public PdrSensorBridge(Context c,PdrEngine e,Callback cb){
        manager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        engine=e;callback=cb;
    }
    /**
     * Permission must be checked by the Activity before this call. Without
     * ACTIVITY_RECOGNITION, use acceleration fallback, not a hidden permission bypass.
     */
    public void start(boolean stepPermission) {
        start(stepPermission, false);
    }
    /**
     * A phone's yaw is NOT the person's heading. Device-heading use is explicitly
     * opt-in and accepted only when the screen is approximately horizontal,
     * face up, with its top edge pointing in the direction of travel.
     * GNSS course is managed independently by PdrActivity.
     */
    public void start(boolean stepPermission,boolean phoneTopForward) {
        stop();timing.reset();allowDeviceHeading=phoneTopForward;
        if(manager==null){stepMode="NO SENSOR MANAGER";registered=false;return;}
        Sensor detector=stepPermission?manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR):null;
        // A sensor can be advertised by Android but its listener registration
        // may still fail. Never disable acceleration fallback unless subscribed.
        useStepDetector=detector!=null && register(detector,SensorManager.SENSOR_DELAY_NORMAL);
        // Accelerometer always recorded if registration succeeds; otherwise there
        // must be a registered STEP_DETECTOR before the session can proceed.
        accelerometerRegistered=register(manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_GAME);
        stepMode=useStepDetector?"ANDROID STEP_DETECTOR":
            (accelerometerRegistered?"ACCEL FALLBACK (EXPERIMENTAL)":"NO ACTIVE STEP SOURCE");
        register(manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_PRESSURE),SensorManager.SENSOR_DELAY_NORMAL);
        accelerationSamples=rotationSamples=magneticSamples=stepSensorSamples=fallbackSteps=0;
        firstSampleNs=latestSampleNs=Long.MIN_VALUE;
        latestMagneticNs=Long.MIN_VALUE;
        registered=true;
    }
    private boolean register(Sensor sensor,int rate){
        if(sensor==null)return false;
        try {return manager.registerListener(this,sensor,rate);}
        catch(SecurityException exception){return false;}
    }
    public String stepMode(){return stepMode;}
    public boolean hasActiveStepSource(){return registered && (useStepDetector || accelerometerRegistered);}
    public boolean hasStepSource(boolean permissionGranted){
        return manager!=null &&
            ((permissionGranted && manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)!=null)
             || manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!=null);
    }
    private boolean has(int type){
        return manager!=null && manager.getDefaultSensor(type)!=null;
    }
    public String sensorInventory(boolean permissionGranted){
        return "SENSORS — step detector: " + (has(Sensor.TYPE_STEP_DETECTOR)
                    ? (permissionGranted?"available":"permission needed"):"missing")
            + "; accel: " + (has(Sensor.TYPE_ACCELEROMETER)?"yes":"no")
            + "; gyro: " + (has(Sensor.TYPE_GYROSCOPE)?"yes":"no")
            + "; magnet: " + (has(Sensor.TYPE_MAGNETIC_FIELD)?"yes":"no")
            + "; rotation vector: " + (has(Sensor.TYPE_ROTATION_VECTOR)?"yes":"no")
            + "; pressure: " + (has(Sensor.TYPE_PRESSURE)?"yes":"no");
    }
    public String sessionTimingReport(){return timing.detailedReport();}
    public String sessionTimingSummary(){return timing.summary();}
    public String sessionDiagnostics(){
        return "Sensor samples — accel "+accelerationSamples+"; rotation "+
            rotationSamples+"; magnetic "+magneticSamples+
            "; hardware step "+stepSensorSamples+"; fallback steps "+fallbackSteps+
            "; magnet "+(magneticReliable?"plausible":"unverified / disturbed")
            +"\n"+timing.summary();
    }
    public boolean isDeviceHeadingEnabled(){return allowDeviceHeading;}
    public long lastSampleNs(){return latestSampleNs;}
    public long firstSampleNs(){return firstSampleNs;}
    public boolean hasRotationVector(){
        return manager!=null&&manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!=null;
    }
    public void stop(){
        if(manager!=null)manager.unregisterListener(this);
        registered=false;useStepDetector=false;accelerometerRegistered=false;
        magneticReliable=false;allowDeviceHeading=false;
        latestMagneticNs=Long.MIN_VALUE;
    }
    private void invalidateHeading(long t,int accuracy){
        if(engine.invalidateDeviceHeading(t))
            callback.sample("HEADING_INVALID",t,0,0,0,accuracy);
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(!registered || !engine.isRecording())return;
        long t=e.timestamp;int type=e.sensor.getType();
        if(!engine.isInCurrentSession(t)){
            timing.beforeSessionCallback();
            return; // never log buffered samples from before START
        }
        switch(type){
            case Sensor.TYPE_ACCELEROMETER: timing.observe(SensorTimingMonitor.ACCEL,t,SystemClock.elapsedRealtimeNanos());break;
            case Sensor.TYPE_GYROSCOPE: timing.observe(SensorTimingMonitor.GYRO,t,SystemClock.elapsedRealtimeNanos());break;
            case Sensor.TYPE_MAGNETIC_FIELD: timing.observe(SensorTimingMonitor.MAGNET,t,SystemClock.elapsedRealtimeNanos());break;
            case Sensor.TYPE_ROTATION_VECTOR: timing.observe(SensorTimingMonitor.ROTATION,t,SystemClock.elapsedRealtimeNanos());break;
            case Sensor.TYPE_PRESSURE: timing.observe(SensorTimingMonitor.PRESSURE,t,SystemClock.elapsedRealtimeNanos());break;
            case Sensor.TYPE_STEP_DETECTOR: timing.observe(SensorTimingMonitor.STEP,t,SystemClock.elapsedRealtimeNanos());break;
            default:break;
        }
        if(firstSampleNs==Long.MIN_VALUE)firstSampleNs=t;
        latestSampleNs=Math.max(latestSampleNs,t);
        double x=e.values.length>0?e.values[0]:0;
        double y=e.values.length>1?e.values[1]:0;
        double z=e.values.length>2?e.values[2]:0;
        switch(type){
            case Sensor.TYPE_STEP_DETECTOR:
                stepSensorSamples++;
                callback.sample("RAW_STEP_DETECTOR",t,x,y,z,e.accuracy);
                if(engine.step(t))callback.step(t);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accelerationSamples++;
                callback.sample("RAW_ACCEL",t,x,y,z,e.accuracy);
                if(!useStepDetector){
                    if(engine.acceleration(t,x,y,z)){
                        fallbackSteps++;
                        callback.step(t);
                    }
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                callback.sample("RAW_GYRO",t,x,y,z,e.accuracy);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                magneticSamples++;
                callback.sample("RAW_MAG",t,x,y,z,e.accuracy);
                double strength=Math.sqrt(x*x+y*y+z*z);
                magneticReliable=Double.isFinite(strength)&&strength>=20&&strength<=75
                    &&e.accuracy!=SensorManager.SENSOR_STATUS_UNRELIABLE;
                latestMagneticNs=t;
                if(!magneticReliable)invalidateHeading(t,e.accuracy);
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                rotationSamples++;
                if(e.values.length<3 || !Float.isFinite(e.values[0]) ||
                   !Float.isFinite(e.values[1]) || !Float.isFinite(e.values[2]) ||
                   (e.values.length>3 && !Float.isFinite(e.values[3]))){
                    invalidateHeading(t,e.accuracy);break;
                }
                callback.rotationVector(t,e.values,e.accuracy);
                // r[8] is the world-up component of device screen-normal.
                // >= cos(45deg) means screen face up and within 45° of level.
                SensorManager.getRotationMatrixFromVector(r,e.values);
                long age=latestMagneticNs==Long.MIN_VALUE?-1L:t-latestMagneticNs;
                if(usablePhoneHeading(allowDeviceHeading,r[8],magneticReliable,
                                      e.accuracy,age)){
                    SensorManager.getOrientation(r,orientation);
                    engine.deviceHeading(t,orientation[0]);
                    callback.sample("HEADING",t,orientation[0],0,0,e.accuracy);
                } else invalidateHeading(t,e.accuracy);
                break;
            case Sensor.TYPE_PRESSURE:
                callback.sample("RAW_PRESSURE",t,x,0,0,e.accuracy);
                engine.pressure(t,x);
                callback.sample("PRESSURE",t,x,0,0,e.accuracy);
                break;
            default:break;
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) {
        // Sensor accuracy can deteriorate without an immediately following MAG sample.
        if(registered && sensor!=null &&
           sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD &&
           accuracy==SensorManager.SENSOR_STATUS_UNRELIABLE){
            magneticReliable=false;
            if(latestSampleNs!=Long.MIN_VALUE)
                invalidateHeading(latestSampleNs,accuracy);
        }
    }
}
