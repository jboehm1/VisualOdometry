package dev.jboehm.vectorvio;
import org.junit.Test;
import static org.junit.Assert.*;
public final class BasaltStartSafetyTest {
  private static BasaltStartSafety.Inputs healthy(){
    var in=new BasaltStartSafety.Inputs();in.foreground=true;in.cameraOpened=true;
    in.streamConfigured=true;in.bootstrapAvailable=true;in.gyroAvailable=true;in.accelAvailable=true;
    in.nativeLibraryAvailable=true;in.nowNs=5_000_000_000L;in.lastCaptureReceivedNs=4_980_000_000L;
    in.lastImageReceivedNs=4_990_000_000L;in.lastGyroReceivedNs=4_999_000_000L;
    in.lastAccelReceivedNs=4_999_000_000L;return in;
  }
  @Test public void readyOnlyAfterRealCameraAndImuArrived(){
    var i=healthy();assertEquals(BasaltStartSafety.Reason.READY,BasaltStartSafety.evaluate(i));
    i.lastImageReceivedNs=0;assertEquals(BasaltStartSafety.Reason.CAMERA_FRAMES_STALE,BasaltStartSafety.evaluate(i));
    i=healthy();i.lastCaptureReceivedNs=2_000_000_000L;
    assertEquals(BasaltStartSafety.Reason.CAMERA_FRAMES_STALE,BasaltStartSafety.evaluate(i));
    i=healthy();i.lastGyroReceivedNs=0;assertEquals(BasaltStartSafety.Reason.IMU_STALE,BasaltStartSafety.evaluate(i));
    i=healthy();i.lastAccelReceivedNs=3_000_000_000L;assertEquals(BasaltStartSafety.Reason.IMU_STALE,BasaltStartSafety.evaluate(i));
  }
  @Test public void absentBootstrapMissingHardwareClosingAndSessionAllReject(){
    var i=healthy();i.bootstrapAvailable=false;assertEquals(BasaltStartSafety.Reason.NO_FACTORY_BOOTSTRAP,BasaltStartSafety.evaluate(i));
    i=healthy();i.gyroAvailable=false;assertEquals(BasaltStartSafety.Reason.IMU_NOT_AVAILABLE,BasaltStartSafety.evaluate(i));
    i=healthy();i.nativeLibraryAvailable=false;assertEquals(BasaltStartSafety.Reason.NATIVE_LIBRARY_UNAVAILABLE,BasaltStartSafety.evaluate(i));
    i=healthy();i.closing=true;assertEquals(BasaltStartSafety.Reason.PREVIOUS_SESSION_CLOSING,BasaltStartSafety.evaluate(i));
    i=healthy();i.recording=true;assertEquals(BasaltStartSafety.Reason.ALREADY_RECORDING,BasaltStartSafety.evaluate(i));
  }
  @Test public void staleAndFutureTimestampsCannotOpenJNI(){
    var i=healthy();i.nowNs=i.lastImageReceivedNs-1;
    assertNotEquals(BasaltStartSafety.Reason.READY,BasaltStartSafety.evaluate(i));
    i=healthy();i.foreground=false;
    assertEquals(BasaltStartSafety.Reason.CAMERA_NOT_OPEN,BasaltStartSafety.evaluate(i));
  }
}
