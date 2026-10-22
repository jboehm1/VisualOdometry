package dev.jboehm.vectorvio;

/**
 * Experimental DEVICE-AXES relative attitude measured from actual gyroscope samples.
 *
 * This class never receives ARCore pose, camera-to-IMU extrinsics or a map frame.
 * An anchor denotes "zero rotation now", not camera pose or the person's heading.
 * Gyro bias is NOT calibrated; no confidence covariance is claimed. Short horizon.
 * No translation, odometer distance, bicycle speed or evacuation guidance.
 *
 * Android SensorEvent.timestamp and elapsedRealtimeNanos must share BOOTTIME.
 * All public operations synchronized because sensor callbacks and GL differ.
 */
public final class ShortHorizonGyro {
    static final long HORIZON_NS=1_500_000_000L;
    static final long MAX_SENSOR_AGE_NS=250_000_000L;
    static final long MAX_SAMPLE_DT_NS=100_000_000L;
    public static final class Estimate {
        public final boolean usable;
        public final String reason;
        public final long anchorAgeMs, sensorAgeMs;
        public final int samples;
        public final double x,y,z,w,angleDegrees;
        private Estimate(boolean ok,String reason,long anchorAge,long sampleAge,int n,
                double x,double y,double z,double w,double degrees){
            usable=ok;this.reason=reason;anchorAgeMs=anchorAge;
            sensorAgeMs=sampleAge;samples=n;this.x=x;this.y=y;this.z=z;
            this.w=w;angleDegrees=degrees;
        }
    }
    private long anchorNs=-1,lastNs=-1;
    private boolean invalid;
    private double qx,qy,qz,qw=1;
    private double wx,wy,wz;
    private int samples;

    public synchronized void reset(){
        anchorNs=-1;lastNs=-1;invalid=false;qx=qy=qz=0;qw=1;
        wx=wy=wz=0;samples=0;
    }
    /** Called only when a NEW live tracked frame was actually received. */
    public synchronized void anchor(long elapsedNs){
        reset();
        if(elapsedNs>0)anchorNs=elapsedNs;
    }
    /** Updates the right-multiplied rotation in the PHONE'S body axes. */
    public synchronized void gyro(long tNs,double gx,double gy,double gz){
        if(anchorNs<0||invalid)return;
        if(tNs<=anchorNs)return; // sensor callback predating most recent tracked frame
        if(!Double.isFinite(gx)||!Double.isFinite(gy)||!Double.isFinite(gz)||
           Math.abs(gx)>30||Math.abs(gy)>30||Math.abs(gz)>30){
            invalid=true;return;
        }
        if(tNs-anchorNs>HORIZON_NS)return;
        if(lastNs<0){
            lastNs=tNs;wx=gx;wy=gy;wz=gz;samples=1;return;
        }
        long dtNs=tNs-lastNs;
        if(dtNs<=0||dtNs>MAX_SAMPLE_DT_NS){invalid=true;return;}
        double dt=dtNs*1e-9;
        // Midpoint body angular rate, q_new = q_old ⊗ Exp(w*dt).
        double vx=.5*(wx+gx)*dt,vy=.5*(wy+gy)*dt,vz=.5*(wz+gz)*dt;
        double theta=Math.sqrt(vx*vx+vy*vy+vz*vz);
        double half=.5*theta;
        double factor=theta<1e-7?.5-theta*theta/48:Math.sin(half)/theta;
        double dx=vx*factor,dy=vy*factor,dz=vz*factor,dw=Math.cos(half);
        double nx=qw*dx+qx*dw+qy*dz-qz*dy;
        double ny=qw*dy-qx*dz+qy*dw+qz*dx;
        double nz=qw*dz+qx*dy-qy*dx+qz*dw;
        double nw=qw*dw-qx*dx-qy*dy-qz*dz;
        double norm=Math.sqrt(nx*nx+ny*ny+nz*nz+nw*nw);
        if(!Double.isFinite(norm)||norm<1e-6){invalid=true;return;}
        qx=nx/norm;qy=ny/norm;qz=nz/norm;qw=nw/norm;
        lastNs=tNs;wx=gx;wy=gy;wz=gz;samples++;
    }
    public synchronized Estimate snapshot(long elapsedNs){
        long anchorAge=anchorNs<0||elapsedNs<anchorNs?-1:(elapsedNs-anchorNs)/1_000_000L;
        long sampleAge=lastNs<0||elapsedNs<lastNs?-1:(elapsedNs-lastNs)/1_000_000L;
        String reason=anchorNs<0?"NO_VISUAL_ANCHOR":
            invalid?"GYRO_OUT_OF_ORDER_OR_GAP":
            anchorAge<0?"BAD_ELAPSED_CLOCK":
            elapsedNs-anchorNs>HORIZON_NS?"ANCHOR_EXPIRED":
            lastNs<0||samples<2?"INSUFFICIENT_GYRO_SAMPLES":
            sampleAge<0||elapsedNs-lastNs>MAX_SENSOR_AGE_NS?"GYRO_STALE":"DEVICE_DELTA_ONLY";
        boolean ok="DEVICE_DELTA_ONLY".equals(reason);
        double degrees=ok?Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(qw)))):Double.NaN;
        return new Estimate(ok,reason,anchorAge,sampleAge,samples,
            ok?qx:Double.NaN,ok?qy:Double.NaN,ok?qz:Double.NaN,ok?qw:Double.NaN,degrees);
    }
}
