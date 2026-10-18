package dev.jboehm.vectorvio.pdr;

import android.app.Application;
import android.net.Uri;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

/** Tests the actual Android writer rather than duplicating the ZIP serializer. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class PdrExportTest {
    private static String entry(ZipFile zip,String name) throws Exception {
        ZipEntry e=zip.getEntry(name);
        assertNotNull(name+" absent",e);
        try(java.io.InputStream in=zip.getInputStream(e)){
            java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
            byte[] block=new byte[4096];int n;
            while((n=in.read(block))>=0){if(n>0)buf.write(block,0,n);}
            return buf.toString("UTF-8");
        }
    }
    @Test public void exportedPathContainsGapAndReplayableDistanceEvents() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        PdrEngine p=new PdrEngine();
        long t0=1_000_000_000L;
        p.start(t0);
        PdrTraceWriter writer=new PdrTraceWriter(app);
        writer.sample("STEP_LENGTH",t0,.8,0,0,0);
        p.setStepLengthMeters(.8);
        p.deviceHeading(t0,0);
        writer.sample("HEADING",t0,0,0,0,3);
        p.step(t0+500_000_000L);writer.step(t0+500_000_000L);
        p.step(t0+5_000_000_000L);writer.step(t0+5_000_000_000L);
        p.deviceHeading(t0+5_100_000_000L,0);
        writer.sample("HEADING",t0+5_100_000_000L,0,0,0,3);
        p.step(t0+5_500_000_000L);writer.step(t0+5_500_000_000L);
        File zipFile=new File(app.getCacheDir(),"pdr-test-"+System.nanoTime()+".zip");
        try{
            writer.export(app,Uri.fromFile(zipFile),p.path(),
                p.snapshot(t0+5_500_000_000L),"ANDROID STEP_DETECTOR");
            assertTrue(zipFile.length()>0);
            try(ZipFile zip=new ZipFile(zipFile)){
                String path=entry(zip,"path.csv");
                String events=entry(zip,"events.csv");
                String raw=entry(zip,"raw_sensors.csv");
                String metadata=entry(zip,"metadata.txt");
                assertTrue(path.startsWith("t_ns,east_m,north_m,gap_before\n"));
                assertTrue(path.contains(",true\n"));
                assertTrue(path.contains(",false\n"));
                assertTrue(events.contains("STEP_LENGTH,0.80000000"));
                assertTrue(events.contains("STEP,"));
                assertTrue(raw.startsWith("t_ns,type,x,y,z,w,accuracy\n"));
                assertTrue(metadata.contains("Estimated distance m: 2.4000000000000004")
                        ||metadata.contains("Estimated distance m: 2.4"));
                assertTrue(metadata.contains("Steps with no heading: 1"));
            }
        }finally{zipFile.delete();}
    }
}
