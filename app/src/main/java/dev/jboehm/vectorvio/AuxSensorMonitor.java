package dev.jboehm.vectorvio;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Samples ambient light, pressure and magnetic vectors at foreground-only low
 * rates to provide *contextual* diagnostics next to ARCore poses. No heading,
 * floor, absolute location or odometry correction is inferred from this class.
 */
final class AuxSensorMonitor implements SensorEventListener {
    static final class Sample {
        final long pressureTimeNs, magneticTimeNs, lightTimeNs;
        final float pressureHpa,magX,magY,magZ,lux;
        final int magneticAccuracy;
        Sample(long pt,long mt,long lt,float p,float mx,float my,float mz,float l,int accuracy){
            pressureTimeNs=pt;magneticTimeNs=mt;lightTimeNs=lt;
            pressureHpa=p;magX=mx;magY=my;magZ=mz;lux=l;magneticAccuracy=accuracy;
        }
        static long ageMs(long nowNs,long sensorNs){
            if(sensorNs<=0||nowNs<sensorNs)return -1;
            return (nowNs-sensorNs)/1_000_000L;
        }
        String csv(long nowNs){
            return nowNs+","+pressureHpa+","+ageMs(nowNs,pressureTimeNs)+
                ","+magX+","+magY+","+magZ+","+magneticAccuracy+","+
                ageMs(nowNs,magneticTimeNs)+","+lux+","+ageMs(nowNs,lightTimeNs);
        }
    }
    static final String CSV_HEADER=
        "elapsed_ns,pressure_hpa,pressure_age_ms,magnetic_x_ut,magnetic_y_ut,magnetic_z_ut,mag_accuracy,mag_age_ms,ambient_lux,light_age_ms";
    private final SensorManager manager;
    private final Sensor pressure,magnet,light;
    private boolean running;
    private float pressureValue=Float.NaN,magX=Float.NaN,magY=Float.NaN,magZ=Float.NaN,lux=Float.NaN;
    private int magAccuracy=-1;
    private long pressureNs,magNs,lightNs;
    AuxSensorMonitor(Context context){
        manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        pressure=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        magnet=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        light=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_LIGHT);
    }
    synchronized void start(){
        if(running || manager==null)return;
        reset();running=true;
        if(pressure!=null)manager.registerListener(this,pressure,200_000);
        if(magnet!=null)manager.registerListener(this,magnet,100_000);
        if(light!=null)manager.registerListener(this,light,200_000);
    }
    synchronized void stop(){
        running=false;
        if(manager!=null)manager.unregisterListener(this);
        reset();
    }
    private void reset(){
        pressureValue=magX=magY=magZ=lux=Float.NaN;
        magAccuracy=-1;pressureNs=magNs=lightNs=0;
    }
    @Override public synchronized void onSensorChanged(SensorEvent e){
        if(!running||e==null||e.values.length==0)return;
        switch(e.sensor.getType()){
            case Sensor.TYPE_PRESSURE:
                if(e.timestamp>pressureNs){pressureValue=e.values[0];pressureNs=e.timestamp;}
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                if(e.values.length>=3&&e.timestamp>magNs){
                    magX=e.values[0];magY=e.values[1];magZ=e.values[2];
                    magAccuracy=e.accuracy;magNs=e.timestamp;
                }
                break;
            case Sensor.TYPE_LIGHT:
                if(e.timestamp>lightNs){lux=e.values[0];lightNs=e.timestamp;}
                break;
            default:break;
        }
    }
    @Override public synchronized void onAccuracyChanged(Sensor sensor,int accuracy){
        if(sensor!=null&&sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD)magAccuracy=accuracy;
    }
    synchronized Sample snapshot(){
        return new Sample(pressureNs,magNs,lightNs,pressureValue,magX,magY,magZ,lux,magAccuracy);
    }
}
