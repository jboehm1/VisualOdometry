package dev.jboehm.vectorvio.pdr.ai;

import dev.jboehm.vectorvio.pdr.PdrEngine;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

/** Headless schema, sample timing, determinism, provenance and fallback tests. */
public final class NeuralFoundationTest {
    private static byte[] model(String magic,String frame,float dx,float dy) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream d=new DataOutputStream(bytes)){
            d.writeUTF(magic);
            d.writeUTF("synthetic-test-network-NOT-TRAINED");
            d.writeUTF("TEST_ONLY");
            d.writeUTF("SYNTHETIC_CONSTANT_BIAS");
            d.writeUTF(frame);
            d.writeInt(InertialWindow.FEATURES);
            d.writeInt(PortableInertialModel.HIDDEN);
            d.writeInt(PortableInertialModel.OUTPUT);
            // 24 means, normalization scales
            for(int i=0;i<InertialWindow.FEATURES;i++)d.writeFloat(0);
            for(int i=0;i<InertialWindow.FEATURES;i++)d.writeFloat(1);
            // Hidden layer weights and bias
            for(int i=0;i<PortableInertialModel.HIDDEN*InertialWindow.FEATURES;i++)
                d.writeFloat(0);
            for(int i=0;i<PortableInertialModel.HIDDEN;i++)d.writeFloat(0);
            // Output weights and output bias:
            for(int i=0;i<PortableInertialModel.OUTPUT*PortableInertialModel.HIDDEN;i++)
                d.writeFloat(0);
            d.writeFloat(dx);d.writeFloat(dy);
            d.writeFloat((float)Math.log(.25));d.writeFloat((float)Math.log(.4));
        }
        return bytes.toByteArray();
    }
    private static PortableInertialModel loadTestModel() throws IOException {
        byte[] bytes=model(PortableInertialModel.MAGIC,PortableInertialModel.FRAME,1.25f,-.75f);
        return PortableInertialModel.load(bytes,PortableInertialModel.sha256(bytes));
    }
    private static void fill(InertialWindow win,long first){
        for(int i=0;i<InertialWindow.FRAMES;i++){
            long t=first+i*20_000_000L;
            win.gyro(t-5_000_000L,0,0,0);
            boolean ready=win.acceleration(t,0,0,9.80665f);
            assertEquals(i==InertialWindow.FRAMES-1,ready);
        }
    }

    @Test public void causalSynchronizationProducesCorrectBoundedFeatureWindow(){
        InertialWindow win=new InertialWindow();
        long first=1_000_000_000L;
        fill(win,first);
        assertEquals(InertialWindow.FRAMES,win.bufferedFrames());
        assertEquals(first+(InertialWindow.FRAMES-1)*20_000_000L,win.endTimeNs());
        assertEquals(1.26,win.durationSeconds(),1e-8);
        float[] features=InertialWindow.features(win.snapshot());
        assertEquals(InertialWindow.FEATURES,features.length);
        assertEquals(9.80665,features[2],.0002);
        assertEquals(9.80665,features[18],.0002);
        assertEquals(0,features[6],1e-5);
        assertEquals(0,features[22],1e-5);
        // Caller modifying a returned snapshot cannot corrupt the bounded ring.
        float[] snapshot=win.snapshot();
        snapshot[0]=9999;
        assertEquals(0,win.snapshot()[0],0);
    }

    @Test public void noFutureGyroOutOfOrderOrLargeGapBridgesWindows(){
        InertialWindow win=new InertialWindow();
        win.gyro(200,0,0,0);
        assertFalse(win.acceleration(100,0,0,9.8f)); // future gyro
        assertEquals(0,win.bufferedFrames());
        win.reset();
        fill(win,1_000_000_000L);
        long t=win.endTimeNs()+200_000_000L;
        win.gyro(t-5_000_000L,0,0,0);
        assertFalse(win.acceleration(t,0,0,9.8f));
        assertEquals(1,win.bufferedFrames());
        assertEquals(1,win.timingResets());
        assertFalse(win.acceleration(t,0,0,9.8f)); // duplicate
        assertTrue(win.rejectedSamples()>0);
    }

    @Test public void rejectsCorruptInputFeatures(){
        float[] input=new float[InertialWindow.FRAMES*InertialWindow.CHANNELS];
        input[0]=Float.NaN;
        try{InertialWindow.features(input);fail("NaN accepted");}
        catch(IllegalArgumentException expected){}
        try{InertialWindow.features(new float[8]);fail("size accepted");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void exactVerifiedWeightsProduceRealDeterministicMlpMath() throws Exception {
        PortableInertialModel model=loadTestModel();
        assertEquals("synthetic-test-network-NOT-TRAINED",model.modelId);
        float[] features=new float[InertialWindow.FEATURES];
        Arrays.fill(features,0);
        NeuralMotionEstimate a=model.infer(features,2_000_000_000L,1.26);
        NeuralMotionEstimate b=model.infer(features,2_000_000_000L,1.26);
        assertEquals(a.deviceXMetres,b.deviceXMetres,0);
        assertEquals(1.25,a.deviceXMetres,1e-7);
        assertEquals(-.75,a.deviceYMetres,1e-7);
        assertEquals(.25,a.modelScaleX,1e-7);
        assertEquals(.4,a.modelScaleY,1e-7);
        assertEquals(PortableInertialModel.FRAME,a.outputFrame);
        assertFalse(a.covarianceCalibrated);
    }

    @Test public void checkpointTrustAndSchemaAreStrict() throws Exception {
        byte[] valid=model(PortableInertialModel.MAGIC,PortableInertialModel.FRAME,1,0);
        String sha=PortableInertialModel.sha256(valid);
        try{PortableInertialModel.load(valid,null);fail();}
        catch(IOException expected){}
        try{PortableInertialModel.load(valid,sha.replace('a','b'));fail();}
        catch(IOException expected){}
        byte[] truncated=Arrays.copyOf(valid,valid.length-2);
        try{PortableInertialModel.load(truncated,
                                      PortableInertialModel.sha256(truncated));fail();}
        catch(IOException expected){}
        byte[] wrongFrame=model(PortableInertialModel.MAGIC,"WORLD_EN_METRES",1,0);
        try{PortableInertialModel.load(wrongFrame,
                         PortableInertialModel.sha256(wrongFrame));fail();}
        catch(IOException expected){}
        byte[] wrongSchema=model("PDR_AI_MLP_V0",PortableInertialModel.FRAME,1,0);
        try{PortableInertialModel.load(wrongSchema,
                         PortableInertialModel.sha256(wrongSchema));fail();}
        catch(IOException expected){}
    }

    @Test public void neverPassesUnphysicalNeuralDisplacement(){
        float[] features=new float[InertialWindow.FEATURES];
        try{PortableInertialModel m=PortableInertialModel.load(
                 model(PortableInertialModel.MAGIC,PortableInertialModel.FRAME,100,0),
                 PortableInertialModel.sha256(model(PortableInertialModel.MAGIC,
                                                   PortableInertialModel.FRAME,100,0)));
            m.infer(features,2_000_000_000L,1.26);fail();
        }catch(IllegalStateException expected){
            // Candidate is rejected as greater than permitted locomotion.
        }catch(IOException unexpected){fail(unexpected.toString());}
    }

    @Test public void noModelNeverMakesUpAPathOrHeading(){
        NeuralDiagnosticRunner diagnostic=new NeuralDiagnosticRunner(null);
        PdrEngine odometer=new PdrEngine();
        odometer.start(1);
        long start=1_000_000_000L;
        for(int i=0;i<64;i++){
            long t=start+i*20_000_000L;
            diagnostic.gyro(t-5_000_000L,0,0,0);
            assertNull(diagnostic.acceleration(t,0,0,9.8f));
        }
        assertEquals(0,diagnostic.candidateCount());
        assertFalse(diagnostic.hasModel());
        PdrEngine.Snapshot state=odometer.snapshot(start+1_260_000_000L);
        assertEquals(0,state.steps);
        assertEquals(0,state.distanceM,0);
        assertEquals(PdrEngine.HeadingSource.UNAVAILABLE,state.headingSource);
    }

    @Test public void verifiedModelProducesOnlyAnIsolatedCandidate() throws Exception {
        NeuralDiagnosticRunner diagnostic=new NeuralDiagnosticRunner(loadTestModel());
        PdrEngine odometer=new PdrEngine();
        odometer.start(1);
        NeuralMotionEstimate candidate=null;
        long start=1_000_000_000L;
        for(int i=0;i<64;i++){
            long t=start+i*20_000_000L;
            diagnostic.gyro(t-5_000_000L,0,0,0);
            candidate=diagnostic.acceleration(t,0,0,9.80665f);
        }
        assertNotNull(candidate);
        assertEquals(1,diagnostic.candidateCount());
        assertEquals(1.25,candidate.deviceXMetres,1e-8);
        assertEquals(0,odometer.snapshot(candidate.endTimeNs).distanceM,0);
        assertEquals(0,odometer.snapshot(candidate.endTimeNs).eastM,0);
        assertEquals(0,odometer.snapshot(candidate.endTimeNs).northM,0);
        diagnostic.reset();
        assertNull(diagnostic.lastCandidate());
    }
}
