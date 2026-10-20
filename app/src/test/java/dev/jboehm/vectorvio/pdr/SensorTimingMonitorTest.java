package dev.jboehm.vectorvio.pdr;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SensorTimingMonitorTest {
    @Test public void tracksTimeOrderGapsAndDeliveryWithoutInventingLoss(){
        SensorTimingMonitor monitor=new SensorTimingMonitor();
        int k=SensorTimingMonitor.ACCEL;
        monitor.observe(k,1_000_000_000L,1_010_000_000L);
        monitor.observe(k,1_020_000_000L,1_030_000_000L);
        monitor.observe(k,1_020_000_000L,1_040_000_000L); // duplicate
        monitor.observe(k,1_010_000_000L,1_050_000_000L); // reversed
        monitor.observe(k,1_400_000_000L,1_700_000_000L); // inferred gap/batch
        monitor.observe(k,1_420_000_000L,1_410_000_000L); // future timestamp
        SensorTimingMonitor.Stats s=monitor.stats(k);
        assertEquals(6,s.events);
        assertEquals(1,s.duplicates);
        assertEquals(1,s.outOfOrder);
        assertEquals(1,s.longIntervals);
        assertEquals(1,s.delayedCallbacks);
        assertEquals(1,s.futureTimestamps);
        assertEquals(380_000_000L,s.maximumIntervalNs);
        assertEquals(300_000_000L,s.maximumDelayNs);
        assertTrue(monitor.detailedReport().contains("gaps_over_250ms=1"));
        assertTrue(monitor.detailedReport().contains("not proof of lost samples"));
    }
    @Test public void stepAndPressureAreNeverCalledDroppedForNormalSparseEvents(){
        SensorTimingMonitor monitor=new SensorTimingMonitor();
        monitor.observe(SensorTimingMonitor.STEP,1_000_000_000L,1_000_000_000L);
        monitor.observe(SensorTimingMonitor.STEP,2_000_000_000L,2_000_000_000L);
        monitor.observe(SensorTimingMonitor.PRESSURE,1_000_000_000L,1_000_000_000L);
        monitor.observe(SensorTimingMonitor.PRESSURE,4_000_000_000L,4_000_000_000L);
        assertEquals(0,monitor.stats(SensorTimingMonitor.STEP).longIntervals);
        assertEquals(0,monitor.stats(SensorTimingMonitor.PRESSURE).longIntervals);
        monitor.beforeSessionCallback();
        assertEquals(1,monitor.beforeSessionCallbacks());
        monitor.reset();
        assertEquals(0,monitor.stats(SensorTimingMonitor.STEP).events);
        assertEquals(0,monitor.beforeSessionCallbacks());
    }
    @Test public void invalidAndOutOfRangeInputsCannotCorruptClock(){
        SensorTimingMonitor monitor=new SensorTimingMonitor();
        monitor.observe(SensorTimingMonitor.GYRO,-1,10);
        monitor.observe(SensorTimingMonitor.GYRO,20,-1);
        assertEquals(2,monitor.stats(SensorTimingMonitor.GYRO).invalidTimestamps);
        assertEquals(0,monitor.stats(SensorTimingMonitor.GYRO).longIntervals);
        try{monitor.observe(90,1,1);fail("sensor kind");}catch(IllegalArgumentException ok){}
    }
}
