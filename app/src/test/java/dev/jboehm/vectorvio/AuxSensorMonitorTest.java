package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AuxSensorMonitorTest {
    @Test public void evidenceNeverPretendsAbsoluteLocation(){
        AuxSensorMonitor.Sample a=new AuxSensorMonitor.Sample(
            1_000_000_000L,1_010_000_000L,0L,1005.2f,21f,12f,-9f,Float.NaN,2);
        String csv=a.csv(1_150_000_000L);
        assertEquals(10,AuxSensorMonitor.CSV_HEADER.split(",").length);
        assertEquals(10,csv.split(",").length);
        assertTrue(csv.contains("1005.2"));
        assertEquals(150,AuxSensorMonitor.Sample.ageMs(1_150_000_000L,1_000_000_000L));
        assertEquals(-1,AuxSensorMonitor.Sample.ageMs(1L,2L));
        assertEquals(-1,AuxSensorMonitor.Sample.ageMs(0,0));
    }
}
