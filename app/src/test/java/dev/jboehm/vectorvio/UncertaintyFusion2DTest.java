package dev.jboehm.vectorvio;
import dev.jboehm.vectorvio.hybrid.UncertaintyFusion2D;
import org.junit.Test;
import static org.junit.Assert.*;

public final class UncertaintyFusion2DTest {
 @Test public void noGtMeansNoPrior(){
  UncertaintyFusion2D f=new UncertaintyFusion2D();
  assertFalse(f.update(100,0,0,5,UncertaintyFusion2D.MeasurementSource.GNSS_ALIGNED));
  assertFalse(f.estimate(100).mayRender3d());
 }
 @Test public void visualAnchorAndCausalPdrMotion(){
  UncertaintyFusion2D f=new UncertaintyFusion2D();
  assertTrue(f.update(1_000_000_000L,2,3,.2,UncertaintyFusion2D.MeasurementSource.VIO));
  assertTrue(f.predict(2_000_000_000L,.75,0,.5));
  assertEquals(UncertaintyFusion2D.State.INERTIAL,f.estimate(2_000_000_000L).state);
  assertEquals(2.75,f.estimate(2_000_000_000L).worldX,.001);
  assertTrue(f.update(2_001_000_000L,2.5,3,.2,UncertaintyFusion2D.MeasurementSource.VIO));
  assertEquals(UncertaintyFusion2D.State.VISUAL,f.estimate(2_001_000_000L).state);
 }
 @Test public void rejectsTeleportDespiteGoodGnssClaim(){
  UncertaintyFusion2D f=new UncertaintyFusion2D();
  f.initialize(1_000_000_000L,0,0,.5);
  assertFalse(f.update(2_000_000_000L,80,80,.1,UncertaintyFusion2D.MeasurementSource.GNSS_ALIGNED));
  assertEquals(0,f.estimate(2_000_000_000L).worldX,.001);
  assertEquals(1,f.rejected());
 }
 @Test public void unboundedOutageStops3dAndRequiresNewVisualReference(){
  UncertaintyFusion2D f=new UncertaintyFusion2D();f.initialize(1_000_000_000L,0,0,.5);
  assertFalse(f.estimate(34_000_000_000L).mayRender3d());
  f.reset();assertFalse(f.estimate(35_000_000_000L).mayRender3d());
 }
 @Test public void sensorFrameJumpAndTimingRejected(){
  UncertaintyFusion2D f=new UncertaintyFusion2D();f.initialize(1_000_000_000L,0,0,.5);
  assertFalse(f.predict(2_000_000_000L,70,0,.5));
  assertFalse(f.predict(4_000_000_000L,1,0,.5));
  assertFalse(f.predict(2_000_000_000L,1,0,Double.NaN));
 }
}
