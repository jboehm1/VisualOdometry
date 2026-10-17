package dev.jboehm.vectorvio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Diagnostic device-axis measurements, independent of ARCore's calibrated fusion. */
final class ImuMonitor implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor accelerometer, gyroscope;
    private final float[] accel=new float[3], gyro=new float[3];
    private long accelTime, gyroTime;
    private float accelHz, gyroHz;
    interface Listener { void sample(int type,long timestamp,float x,float y,float z); }
    private Listener listener;
    void setListener(Listener listener){this.listener=listener;}
    ImuMonitor(Context context) {
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer=manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope=manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }
    void start() {
        synchronized(this) { accelTime=gyroTime=0; accelHz=gyroHz=0; }
        if(accelerometer!=null) manager.registerListener(this,accelerometer,5_000);
        if(gyroscope!=null) manager.registerListener(this,gyroscope,5_000);
    }
    void stop() { manager.unregisterListener(this); }
    @Override public synchronized void onSensorChanged(SensorEvent e) {
        boolean a=e.sensor.getType()==Sensor.TYPE_ACCELEROMETER;
        if(listener!=null)listener.sample(e.sensor.getType(),e.timestamp,e.values[0],e.values[1],e.values[2]);
        long previous=a?accelTime:gyroTime;
        float rate=previous>0 && e.timestamp>previous?1_000_000_000f/(e.timestamp-previous):0;
        System.arraycopy(e.values,0,a?accel:gyro,0,3);
        if(a) { accelTime=e.timestamp; accelHz=.9f*accelHz+.1f*rate; }
        else { gyroTime=e.timestamp; gyroHz=.9f*gyroHz+.1f*rate; }
    }
    synchronized float[] snapshot() {
        long now=android.os.SystemClock.elapsedRealtimeNanos();
        return new float[]{accel[0],accel[1],accel[2],gyro[0],gyro[1],gyro[2],
            accelTime>0 && now-accelTime<1_000_000_000L?accelHz:0,
            gyroTime>0 && now-gyroTime<1_000_000_000L?gyroHz:0};
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy) { }
}
