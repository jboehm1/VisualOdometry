package dev.jboehm.vectorvio.pdr;

import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic guard-rail fixtures, NOT a ground-truth human step benchmark. */
public final class StepPeakDetectorTest {
    private static int syntheticGait(int samplesPerStep,int steps){
        StepPeakDetector detector=new StepPeakDetector();
        long time=1_000_000_000L;
        long dt=600_000_000L/samplesPerStep;
        int emitted=0;
        for(int k=0;k<steps;k++){
            for(int j=0;j<samplesPerStep;j++){
                double z=j==samplesPerStep/3?14.5:9.81;
                if(detector.accept(time,0,0,z))emitted++;
                time+=dt;
            }
        }
        return emitted;
    }
    @Test public void rhythmicGaitWorksAcrossSampleRates(){
        int at25Hz=syntheticGait(15,9);
        int at50Hz=syntheticGait(30,9);
        int at100Hz=syntheticGait(60,9);
        assertTrue(at25Hz>=6);
        assertEquals(at25Hz,at50Hz);
        assertEquals(at50Hz,at100Hz);
    }
    @Test public void isolatedImpactsDoNotImmediatelyBecomeSteps(){
        StepPeakDetector p=new StepPeakDetector();
        long t=1_000_000_000L;
        int accepted=0;
        for(int shock=0;shock<5;shock++){
            for(int sample=0;sample<30;sample++){
                if(p.accept(t,0,0,sample==15?18:9.81))accepted++;
                t+=20_000_000L;
            }
            // Stops longer than the reset window; shocks are not rhythmic steps.
            t+=2_000_000_000L;
        }
        assertEquals(0,accepted);
    }
    @Test public void shortReboundDoesNotResetEstablishedCadence(){
        StepPeakDetector detector=new StepPeakDetector();
        long time=1_000_000_000L;
        int counted=0;
        int reboundCounted=0;
        // 600ms genuine rhythmic gait, with extra shock 320ms after
        // each of the final 3 real peaks. A rebound must not replace the
        // previous valid gait interval and cause a following real step loss.
        for(int k=0;k<9;k++){
            for(int j=0;j<30;j++){
                boolean extra=k>=6 && j==26;
                double z=(j==10 || extra)?14.5:9.81;
                if(detector.accept(time,0,0,z)){
                    counted++;
                    if(extra)reboundCounted++;
                }
                time+=20_000_000L;
            }
        }
        assertEquals(0,reboundCounted);
        assertTrue("real steps lost after rebound",counted>=7);
    }
    @Test public void resetDiscardsPreviousCadence(){
        StepPeakDetector p=new StepPeakDetector();
        long t=1_000_000_000L;
        int accepted=0;
        for(int k=0;k<4;k++)for(int j=0;j<30;j++){
            if(p.accept(t,0,0,j==10?14.5:9.81))accepted++;
            t+=20_000_000L;
        }
        assertTrue(accepted>0);
        p.reset();
        int afterReset=0;
        for(int j=0;j<30;j++){
            if(p.accept(t,0,0,j==10?14.5:9.81))afterReset++;
            t+=20_000_000L;
        }
        assertEquals(0,afterReset);
    }
}
