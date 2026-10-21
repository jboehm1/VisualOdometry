package dev.jboehm.vectorvio.nativevio;

import java.util.ArrayDeque;

/** Serial acquisition thread only. Interpolates acceleration at each real gyro timestamp.
 * Never extrapolates, invents a sample during a gap, or changes sensor axes. */
public final class ImuSynchronizer {
    public interface Sink { void pair(long timeNs, double[] accelGyro); }
    private static final long MAX_GAP_NS=30_000_000L;
    private static final int CAPACITY=256;
    private static final class Sample {
        final long t; final double[] v;
        Sample(long t,double x,double y,double z){this.t=t;v=new double[]{x,y,z};}
    }
    private final ArrayDeque<Sample> accel=new ArrayDeque<>(),gyro=new ArrayDeque<>();
    private final Sink sink;
    private long lastAccel,lastGyro;
    public long dropped,paired;
    public ImuSynchronizer(Sink sink){this.sink=sink;}
    public void add(boolean acceleration,long t,double x,double y,double z){
        if(t<=0||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) {dropped++;return;}
        if(t<=(acceleration?lastAccel:lastGyro)){dropped++;return;}
        if(acceleration)lastAccel=t;else lastGyro=t;
        ArrayDeque<Sample> queue=acceleration?accel:gyro;
        if(queue.size()==CAPACITY){queue.removeFirst();dropped++;}
        queue.addLast(new Sample(t,x,y,z));drain();
    }
    private void drain(){
        while(!gyro.isEmpty()&&accel.size()>=2){
            Sample g=gyro.peekFirst(),a=accel.peekFirst();
            if(g.t<a.t){gyro.removeFirst();dropped++;continue;}
            Sample first=accel.removeFirst(),b=accel.peekFirst();
            if(b.t<=g.t){continue;}
            accel.addFirst(first);
            gyro.removeFirst();
            if(b.t-a.t>MAX_GAP_NS){dropped++;continue;}
            double fraction=(g.t-a.t)/(double)(b.t-a.t);
            double[] pair=new double[6];
            for(int i=0;i<3;i++){pair[i]=a.v[i]+fraction*(b.v[i]-a.v[i]);pair[i+3]=g.v[i];}
            sink.pair(g.t,pair);paired++;
        }
    }
}
