package dev.jboehm.vectorvio.pdr.research;

/** Guard rails on synthetic pulses only; no claim about human gait accuracy. */
public final class StepResearchSelfTest {
    public static void main(String[] args) {
        for(boolean adaptive:new boolean[]{false,true}) {
            int expected=-1;
            for(int hz:new int[]{25,50,100}) {
                PeakCandidate a=new PeakCandidate(adaptive), b=new PeakCandidate(adaptive);
                long t=1_000_000_000L;int steps=0;
                for(int i=0;i<hz*12;i++,t+=1_000_000_000L/hz) {
                    double z=(i%(hz*3/5)==hz/5)?14.5:9.81;
                    // Rejected invalid future and stale callbacks cannot change
                    // the result of subsequent valid observations.
                    b.accept(t+20_000_000_000L,Double.NaN,0,0);
                    b.accept(t-1_000_000_000L,0,0,9.81);
                    boolean one=a.accept(t,0,0,z), two=b.accept(t,0,0,z);
                    if(one!=two)throw new AssertionError("rejected sample mutates state");
                    if(one)steps++;
                }
                if(steps<17 || steps>20)throw new AssertionError("synthetic gait "+hz+": "+steps);
                if(expected>=0 && Math.abs(steps-expected)>1)throw new AssertionError("sample-rate inconsistency");
                expected=steps;
                a.reset();
                for(int i=0;i<hz*3;i++,t+=1_000_000_000L/hz)
                    if(a.accept(t,0,0,i==hz?18:9.81))throw new AssertionError("isolated impact counted");
            }
        }
        System.out.println("Research challenger causal/reset/rate guard rails pass; not human accuracy.");
    }
}
