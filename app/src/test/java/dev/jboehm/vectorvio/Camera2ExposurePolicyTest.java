package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Camera2ExposurePolicyTest {
 @Test public void exposureIsClampedToHardwareAndFrame(){
  assertEquals(10_000_000L,Camera2ExposurePolicy.clampShutter(90_000_000L,1000,50_000_000L,10_000_000L));
  assertEquals(1_000_000L,Camera2ExposurePolicy.clampShutter(1000,1_000_000L,50_000_000L,33_333_333L));
 }
 @Test public void isoClampsSafely(){
  assertEquals(100,Camera2ExposurePolicy.clampIso(50,100,1600));
  assertEquals(1600,Camera2ExposurePolicy.clampIso(9900,100,1600));
 }
 @Test(expected=IllegalArgumentException.class)public void invalidHardwareRangeRejected(){
  Camera2ExposurePolicy.clampIso(100,0,200);
 }
}
