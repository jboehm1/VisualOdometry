package dev.jboehm.vectorvio.pdr;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Android-only acquisition adapter. Core estimator has zero Android dependencies.
 * Sensor events already use the elapsedRealtimeNanos time base; do not replace it
 * with wall-clock. No camera, ARCore, Google Play Services or network needed.
 */
public final class PdrSensorBridge implements SensorEventListener {
    public interface Callback {
        void sample(String type, long tNs, double x, double y, double z, int accuracy);
        void step(long tNs);
    }
    private final SensorManager manager;
    private final PdrEngine engine;
    private final Callback callback;
    private final float[] r=new float[9], orientation=new float[3];
    private boolean registered, useStepDetector, magneticReliable=true;
    private String stepMode="ACCEL FALLBACK";
    public PdrSensorBridge(Context c,PdrEngine e,Callback cb){
        manager=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);
        engine=e;callback=cb;
    }
    /**
     * Permission must be checked by the Activity before this call. Without
     * ACTIVITY_RECOGNITION, use acceleration fallback, not a hidden permission bypass.
     */
    public void start(boolean stepPermission){
        stop(); if(manager==null)return;
        Sensor detector=stepPermission?manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR):null;
        useStepDetector=detector!=null;
        stepMode=useStepDetector?"ANDROID STEP_DETECTOR":"ACCEL FALLBACK (EXPERIMENTAL)";
        if(useStepDetector)register(detector,SensorManager.SENSOR_DELAY_NORMAL);
        // Accelerometer always recorded, also used for fallback.
        register(manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),SensorManager.SENSOR_DELAY_GAME);
        register(manager.getDefaultSensor(Sensor.TYPE_PRESSURE),SensorManager.SENSOR_DELAY_NORMAL);
        registered=true;
    }
    private void register(Sensor sensor,int rate){
        if(sensor!=null)manager.registerListener(this,sensor,rate);
    }
    public String stepMode(){return stepMode;}
    public boolean hasRotationVector(){
        return manager!=null&&manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)!=null;
    }
    public void stop(){
        if(manager!=null)manager.unregisterListener(this);
        registered=false;magneticReliable=true;
    }
    @Override public void onSensorChanged(SensorEvent e){
        if(!registered || !engine.isRecording())return;
        long t=e.timestamp;int type=e.sensor.getType();
        double x=e.values.length>0?e.values[0]:0;
        double y=e.values.length>1?e.values[1]:0;
        double z=e.values.length>2?e.values[2]:0;
        switch(type){
            case Sensor.TYPE_STEP_DETECTOR:
                callback.sample("RAW_STEP_DETECTOR",t,x,y,z,e.accuracy);
                if(engine.step(t))callback.step(t);
                break;
            case Sensor.TYPE_ACCELEROMETER:
                callback.sample("RAW_ACCEL",t,x,y,z,e.accuracy);
                if(!useStepDetector){
                    if(engine.acceleration(t,x,y,z))callback.step(t);
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                callback.sample("RAW_GYRO",t,x,y,z,e.accuracy);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                callback.sample("RAW_MAG",t,x,y,z,e.accuracy);
                double strength=Math.sqrt(x*x+y*y+z*z);
                magneticReliable=Double.isFinite(strength)&&strength>=20&&strength<=75;
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                callback.sample("RAW_ROTATION_VECTOR",t,x,y,z,e.accuracy);
                if(magneticReliable && e.accuracy!=SensorManager.SENSOR_STATUS_UNRELIABLE){
                    SensorManager.getRotationMatrixFromVector(r,e.values);
                    SensorManager.getOrientation(r,orientation);
                    engine.deviceHeading(t,orientation[0]);
                    callback.sample("HEADING",t,orientation[0],0,0,e.accuracy);
                }
                break;
            case Sensor.TYPE_PRESSURE:
                callback.sample("RAW_PRESSURE",t,x,0,0,e.accuracy);
                engine.pressure(t,x);
                callback.sample("PRESSURE",t,x,0,0,e.accuracy);
                break;
            default:break;
        }
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) { }
}
