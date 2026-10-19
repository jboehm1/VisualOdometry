package dev.jboehm.vectorvio.pdr.ai;

import android.app.Application;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public final class TinyCarryModelTest {
    private TinyCarryModel model() throws Exception {
        Application app=RuntimeEnvironment.getApplication();
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try(InputStream in=app.getAssets().open("pdr_carry_model.b64")){
            byte[] buf=new byte[2048];int n;
            while((n=in.read(buf))!=-1)result.write(buf,0,n);
        }
        return TinyCarryModel.loadBase64(new String(result.toByteArray(),StandardCharsets.US_ASCII));
    }
    @Test public void genuineTrainedModelLoadsWithImmutableProvenance() throws Exception {
        TinyCarryModel m=model();
        assertEquals("CC-BY-SA-4.0",m.licenseId);
        assertTrue(m.datasetId.startsWith("SIMUL@ee7eb4"));
        assertEquals(TinyCarryModel.AUDITED_SHA256,m.sha256);
        assertEquals(451,TinyCarryModel.INPUT*TinyCarryModel.HIDDEN+
                         TinyCarryModel.HIDDEN+TinyCarryModel.HIDDEN*TinyCarryModel.OUTPUT+
                         TinyCarryModel.OUTPUT);
        assertEquals(432,TinyCarryModel.MACS);
        float[] x=new float[24];
        double[] y=m.predictFeatures(x);
        assertEquals(3,y.length);
        assertEquals(1.0,y[0]+y[1]+y[2],1e-9);
        for(double v:y)assertTrue(v>=0 && v<=1);
        // Independent reference computed from the Python checkpoint layout:
        // same fixed features, normalization, ReLU and softmax, no GT inputs.
        assertEquals(0.0004716125376276549,y[0],1e-6);
        assertEquals(0.5149571321795693,y[1],1e-6);
        assertEquals(0.4845712552828031,y[2],1e-6);
        for(int i=0;i<24;i++)x[i]=i*.5f-5f;
        y=m.predictFeatures(x);
        assertEquals(0.999989647309023,y[2],1e-6);
    }
    @Test public void tamperingAndInvalidModelFeaturesAreRejected() throws Exception {
        TinyCarryModel m=model();
        float[] features=new float[24];
        features[7]=Float.NaN;
        try{m.predictFeatures(features);fail("bad input");}
        catch(IllegalArgumentException expected){}
        Application app=RuntimeEnvironment.getApplication();
        ByteArrayOutputStream b=new ByteArrayOutputStream();
        try(InputStream in=app.getAssets().open("pdr_carry_model.b64")){
            byte[] buf=new byte[4096];int n;
            while((n=in.read(buf))!=-1)b.write(buf,0,n);
        }
        byte[] artifact=java.util.Base64.getDecoder().decode(
            new String(b.toByteArray(),StandardCharsets.US_ASCII).trim());
        artifact[100]^=1;
        try{TinyCarryModel.load(artifact,TinyCarryModel.AUDITED_SHA256);fail("tamper");}
        catch(java.io.IOException expected){}
    }
    @Test public void experimentalStreamingModelRunsAtTwoHertzWithoutChangingPdr() throws Exception {
        TinyCarryDiagnostic diag=new TinyCarryDiagnostic(model());
        dev.jboehm.vectorvio.pdr.PdrEngine core=new dev.jboehm.vectorvio.pdr.PdrEngine();
        core.start(1L);
        long first=1_000_000_000L;
        for(int i=0;i<140;i++){
            long t=first+i*20_000_000L;
            diag.gyro(t-1_000_000L,.1f,.1f,.1f);
            diag.acceleration(t,0f,0f,9.81f);
        }
        assertTrue(diag.inferenceCount()>=2);
        assertTrue(diag.inferenceCount()<=5);
        assertEquals(0,core.snapshot(first+2_800_000_000L).steps);
        assertEquals(0,core.snapshot(first+2_800_000_000L).distanceM,0);
        assertEquals(0,diag.rejectedCount());
        assertTrue(diag.status().contains("NOT odometry"));
        assertTrue(diag.meanInferenceMs()>=0);
        diag.reset();
        assertEquals(0,diag.inferenceCount());
    }
}
