package dev.jboehm.vectorvio.pdr.ai;

import java.util.Arrays;

/**
 * Bounded, causal asynchronous accel+gyro alignment for neural experimentation.
 * Assumes Android sensor coordinates and monotonic elapsedRealtimeNanos.
 *
 * Model contract: 64 approximately 50-Hz samples, channels
 * [ax,ay,az] in m/s², [gx,gy,gz] in rad/s (PHONE frame).
 * No GT / phone heading / GNSS is ever read here.
 * Gyroscope sample-and-hold uses only past samples (<=40ms old).
 * Invalid timing, nonfinite samples, or sensor gaps reset the window.
 */
public final class InertialWindow {
    public static final int FRAMES=64, CHANNELS=6, FEATURES=24;
    private static final long MIN_INTERVAL_NS=14_000_000L;
    private static final long MAX_INTERVAL_NS=55_000_000L;
    private static final long MAX_GYRO_AGE_NS=40_000_000L;
    private final float[][] ring=new float[FRAMES][CHANNELS];
    private final long[] times=new long[FRAMES];
    private final float[] gyro=new float[3];
    private long gyroNs=Long.MIN_VALUE, accelNs=Long.MIN_VALUE;
    private int count, next;
    private long rejectedSamples, timingResets;

    public void reset() {
        count=next=0;gyroNs=accelNs=Long.MIN_VALUE;
        Arrays.fill(gyro,0);
    }
    private void resetFrames() {
        count=next=0;timingResets++;
    }
    public long rejectedSamples(){return rejectedSamples;}
    public long timingResets(){return timingResets;}
    public int bufferedFrames(){return count;}

    public void gyro(long t, float x, float y, float z) {
        if(t<=0 || !finite(x,y,z) || (gyroNs!=Long.MIN_VALUE && t<=gyroNs)){
            rejectedSamples++;return;
        }
        gyro[0]=x;gyro[1]=y;gyro[2]=z;gyroNs=t;
    }
    /**
     * @return true only when a complete synchronized 64-frame window exists.
     * The caller may snapshot() immediately and invoke a model.
     */
    public boolean acceleration(long t, float x, float y, float z) {
        if(t<=0 || !finite(x,y,z) || (accelNs!=Long.MIN_VALUE && t<=accelNs)) {
            rejectedSamples++;return false;
        }
        if(gyroNs==Long.MIN_VALUE || gyroNs>t || t-gyroNs>MAX_GYRO_AGE_NS) {
            rejectedSamples++;
            if(accelNs!=Long.MIN_VALUE && t-accelNs>MAX_INTERVAL_NS)resetFrames();
            accelNs=t;return false;
        }
        long dt=accelNs==Long.MIN_VALUE?0:t-accelNs;
        if(dt>0 && dt<MIN_INTERVAL_NS) {
            // Decimate 100-Hz samples towards 50 Hz, without creating samples.
            return false;
        }
        if(dt>MAX_INTERVAL_NS)resetFrames();
        accelNs=t;
        float[] frame=ring[next];
        frame[0]=x;frame[1]=y;frame[2]=z;
        frame[3]=gyro[0];frame[4]=gyro[1];frame[5]=gyro[2];
        times[next]=t;
        next=(next+1)%FRAMES;
        if(count<FRAMES)count++;
        return count==FRAMES;
    }
    private static boolean finite(float x,float y,float z) {
        return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z);
    }

    /** Samples returned oldest->newest. A defensive copy never exposes ring state. */
    public float[] snapshot() {
        if(count!=FRAMES)throw new IllegalStateException("64 synchronized frames required");
        float[] data=new float[FRAMES*CHANNELS];
        for(int i=0;i<FRAMES;i++){
            int index=(next+i)%FRAMES;
            System.arraycopy(ring[index],0,data,i*CHANNELS,CHANNELS);
        }
        return data;
    }
    public long endTimeNs() {
        if(count!=FRAMES)throw new IllegalStateException("incomplete window");
        return times[(next+FRAMES-1)%FRAMES];
    }
    public double durationSeconds() {
        if(count!=FRAMES)throw new IllegalStateException("incomplete window");
        long start=times[next],end=endTimeNs();
        return (end-start)*1e-9;
    }

    /**
     * Feature order is immutable for a trained model:
     *   0..5   means (accelerometer XYZ, gyro XYZ)
     *   6..11  standard deviations
     *   12..17 last minus first values
     *   18..19 mean accel/gyro vector norms
     *   20..21 stddev accel/gyro vector norms
     *   22..23 RMS adjacent-sample difference norm (jerk-like, unscaled)
     *
     * They are NOT rotation-invariant as a whole: an arbitrary-carry model
     * must be trained with orientation diversity and independent GT.
     */
    public static float[] features(float[] data) {
        if(data==null||data.length!=FRAMES*CHANNELS)
            throw new IllegalArgumentException("expected 64*6 IMU samples");
        double[] mean=new double[CHANNELS],sq=new double[CHANNELS];
        double[] normMean=new double[2],normSq=new double[2];
        double[] differenceSq=new double[2];
        double[] first=new double[CHANNELS],last=new double[CHANNELS];
        for(int i=0;i<FRAMES;i++){
            for(int c=0;c<CHANNELS;c++){
                float v=data[i*CHANNELS+c];
                if(!Float.isFinite(v)||Math.abs(v)>1_000f)
                    throw new IllegalArgumentException("invalid IMU feature sample");
                if(i==0)first[c]=v;
                if(i==FRAMES-1)last[c]=v;
                mean[c]+=v;sq[c]+=v*v;
            }
            for(int t=0;t<2;t++){
                int start=t*3;
                double norm=Math.sqrt(data[i*CHANNELS+start]*data[i*CHANNELS+start]
                    +data[i*CHANNELS+start+1]*data[i*CHANNELS+start+1]
                    +data[i*CHANNELS+start+2]*data[i*CHANNELS+start+2]);
                normMean[t]+=norm;normSq[t]+=norm*norm;
                if(i>0){
                    double d0=data[i*CHANNELS+start]-data[(i-1)*CHANNELS+start];
                    double d1=data[i*CHANNELS+start+1]-data[(i-1)*CHANNELS+start+1];
                    double d2=data[i*CHANNELS+start+2]-data[(i-1)*CHANNELS+start+2];
                    differenceSq[t]+=d0*d0+d1*d1+d2*d2;
                }
            }
        }
        float[] f=new float[FEATURES];
        for(int c=0;c<CHANNELS;c++){
            double mu=mean[c]/FRAMES;
            f[c]=(float)mu;
            f[6+c]=(float)Math.sqrt(Math.max(0,sq[c]/FRAMES-mu*mu));
            f[12+c]=(float)(last[c]-first[c]);
        }
        for(int t=0;t<2;t++){
            double mu=normMean[t]/FRAMES;
            f[18+t]=(float)mu;
            f[20+t]=(float)Math.sqrt(Math.max(0,normSq[t]/FRAMES-mu*mu));
            f[22+t]=(float)Math.sqrt(differenceSq[t]/(FRAMES-1));
        }
        for(float v:f)if(!Float.isFinite(v))
            throw new IllegalArgumentException("nonfinite derived features");
        return f;
    }
}
