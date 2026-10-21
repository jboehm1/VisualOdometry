package dev.jboehm.vectorvio.pdr;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PdrCalibrationTest {
    @Test public void calibratedStrideIsIndependentDistancePerStep(){
        assertEquals(.8,PdrCalibration.calibratedStepLength(100,125),1e-12);
        assertEquals(1.2,PdrCalibration.calibratedStepLength(120,100),1e-12);
    }
    @Test public void invalidOrImplausibleCalibrationsAreRejected(){
        double[] invalid={Double.NaN,Double.POSITIVE_INFINITY,-1,1.0,60_000};
        for(double distance:invalid)
            try {PdrCalibration.calibratedStepLength(distance,100);fail();}
            catch(IllegalArgumentException expected){}
        int[] steps={0,-1,1,4};
        for(int n:steps)
            try {PdrCalibration.calibratedStepLength(100,n);fail();}
            catch(IllegalArgumentException expected){}
        try{PdrCalibration.calibratedStepLength(100,5);fail();}
        catch(IllegalArgumentException expected){}
        try{PdrCalibration.calibratedStepLength(100,1000);fail();}
        catch(IllegalArgumentException expected){}
    }
}
