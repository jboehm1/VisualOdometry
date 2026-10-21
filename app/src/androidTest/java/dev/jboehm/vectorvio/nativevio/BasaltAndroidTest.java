package dev.jboehm.vectorvio.nativevio;

import android.test.InstrumentationTestCase;
import android.os.SystemClock;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Runs the actual compiled JNI estimator in Android, without ARCore or reference poses. */
public final class BasaltAndroidTest extends InstrumentationTestCase {
    public void testShutdownWithFullImuQueueBeforeFirstImage()throws Exception {
        assertNull(BasaltBridge.unavailableReason());
        FutureTask<Void> run=new FutureTask<>(()->{
            double[] p={640,480,500,500,320,240,1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1,.000282,.016,.0001,.001,200};
            double[] imu={0,0,9.81,0,0,0};
            try(BasaltBridge bridge=new BasaltBridge()){
                bridge.start(p,1_000_000_000L,imu);
                for(int i=1;i<=320;i++)if(!bridge.imu(1_000_000_000L+i*5_000_000L,imu))break;
            }
            return null;
        });
        Thread t=new Thread(run,"native-shutdown-test");t.setDaemon(true);t.start();run.get(15,TimeUnit.SECONDS);
    }
    public void testRealMonocularImagesAndImuProducePoses()throws Exception {
        assertNull(BasaltBridge.unavailableReason());
        long started=SystemClock.elapsedRealtime();
        int poses=0,qualified=0;long last=0;double translationNorm=0;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(
            getInstrumentation().getContext().getAssets().open("mono_fixture.bin"),65536));BasaltBridge bridge=new BasaltBridge()){
            byte[] magic=new byte[8];in.readFully(magic);assertEquals("VBMONO1\n",new String(magic,StandardCharsets.UTF_8));
            int width=in.readInt(),height=in.readInt(),n=in.readInt();
            double[] p=new double[27];for(int i=0;i<27;i++)p[i]=in.readDouble();int frames=in.readInt();
            assertTrue(n>100&&n<10000);assertEquals(400,frames);
            long[] times=new long[n];double[][] imu=new double[n][6];
            for(int i=0;i<n;i++){times[i]=in.readLong();for(int j=0;j<6;j++)imu[i][j]=in.readDouble();}
            bridge.start(p,times[0],imu[0]);int index=1;byte[] image=new byte[width*height];
            long[] time=new long[1];double[] values=new double[9];
            for(int f=0;f<frames;f++){
                long frame=in.readLong();in.readFully(image);
                while(index<n&&times[index]<=frame+10_000_000L){
                    long deadline=SystemClock.elapsedRealtime()+10000;
                    while(!bridge.imu(times[index],imu[index])){assertTrue("IMU backpressure timeout",SystemClock.elapsedRealtime()<deadline);Thread.sleep(5);}
                    index++;
                }
                long deadline=SystemClock.elapsedRealtime()+10000;
                // Exposure unavailable in this dataset: explicit unknown sentinel.
                while(!bridge.frame(frame,image,0)){assertTrue("Image backpressure timeout",SystemClock.elapsedRealtime()<deadline);Thread.sleep(5);}
                Thread.sleep(45);int quality=bridge.poll(time,values);
                if(time[0]>last){
                    last=time[0];poses++;if(quality==2)qualified++;
                    for(double v:values)assertTrue("Finite native state",Double.isFinite(v));
                    double q=0;for(int j=3;j<7;j++)q+=values[j]*values[j];assertEquals(1,q,1e-3);
                    translationNorm=Math.max(translationNorm,Math.sqrt(values[0]*values[0]+values[1]*values[1]+values[2]*values[2]));
                }
            }
            assertEquals(-1,in.read());
        }
        JSONObject result=new JSONObject().put("engine",BasaltBridge.version()).put("poses_observed",poses)
            .put("quality_heuristic_observed",qualified).put("maximum_translation_norm_m",translationNorm)
            .put("wall_ms",SystemClock.elapsedRealtime()-started).put("android_sdk",android.os.Build.VERSION.SDK_INT)
            .put("abis",new org.json.JSONArray(android.os.Build.SUPPORTED_ABIS)).put("truth_available_to_engine",false)
            .put("limits","Real mono rectified EuRoC sensors on Android emulator; not Camera2 hardware, S21 accuracy, energy or production qualification");
        File output=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"native-smoke.json");
        try(Writer w=new OutputStreamWriter(new FileOutputStream(output),StandardCharsets.UTF_8)){w.write(result.toString(2));}
        assertTrue("Actual estimator must publish poses: "+result,poses>=50);
        assertTrue("Visual landmarks/features must be available: "+result,qualified>=10);
        assertTrue("Real movement must appear: "+result,translationNorm>.10);
        System.out.println("BASALT_ANDROID_REPLAY "+result);
    }
}
