package dev.jboehm.vectorvio.pdr.ai;

import java.util.Locale;

/** Explicit opt-in research-only proxy carry mode. Never touches the odometer. */
public final class TinyCarryDiagnostic {
    private final TinyCarryModel model;
    private final InertialWindow window=new InertialWindow();
    private long lastInferenceNs=Long.MIN_VALUE, count, rejected, sumCostNs, maxCostNs;
    private double[] last;
    public TinyCarryDiagnostic(TinyCarryModel model){if(model==null)throw new IllegalArgumentException("model required");this.model=model;}
    public void reset(){
        window.reset();lastInferenceNs=Long.MIN_VALUE;count=rejected=sumCostNs=maxCostNs=0;last=null;
    }
    public void gyro(long t,float x,float y,float z){window.gyro(t,x,y,z);}
    public void acceleration(long t,float x,float y,float z){
        if(!window.acceleration(t,x,y,z))return;
        long end=window.endTimeNs();
        if(lastInferenceNs!=Long.MIN_VALUE && end-lastInferenceNs<500_000_000L)return;
        lastInferenceNs=end;
        long start=System.nanoTime();
        try{last=model.predictWindow(window.snapshot());count++;}
        catch(IllegalArgumentException ex){rejected++;}
        finally{
            long elapsed=Math.max(0,System.nanoTime()-start);
            sumCostNs+=elapsed;maxCostNs=Math.max(maxCostNs,elapsed);
        }
    }
    public long inferenceCount(){return count;}
    public long rejectedCount(){return rejected;}
    public double[] lastScores(){return last==null?null:last.clone();}
    public double meanInferenceMs(){return count+rejected==0?0:(sumCostNs/(double)(count+rejected))/1e6;}
    public double maxInferenceMs(){return maxCostNs/1e6;}
    public String status(){
        if(count==0)return "AI CARRY EXPERIMENTAL: waiting for synchronized 64x6 IMU (no navigation fusion)";
        int chosen=0;
        for(int i=1;i<TinyCarryModel.OUTPUT;i++)if(last[i]>last[chosen])chosen=i;
        return String.format(Locale.US,"AI sensor-placement proxy: %s (score %.0f%%, UNCALIBRATED)"
            +" · %d inf · mean CPU %.3f ms · max %.3f ms · %d rejected · NOT odometry",
            TinyCarryModel.LABELS[chosen],last[chosen]*100,count,
            meanInferenceMs(),maxInferenceMs(),rejected);
    }
}
