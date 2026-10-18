package dev.jboehm.vectorvio.pdr.ai;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * A tiny REAL feed-forward neural network interpreter with NO Android SDK,
 * inference service, network, native library or third-party runtime dependency.
 *
 * It DOES NOT bundle learned weights. This is an interface for a future
 * separately trained, provenance-reviewed checkpoint, NOT RoNIN/TLIO/EqNIO.
 *
 * All displacements are DEVICE-XY candidates, never world EN / body heading.
 * They must not update PdrEngine without explicit frame transforms, verified
 * calibration, held-out evidence and fusion quality gating.
 *
 * Binary contract (big-endian Java DataInputStream):
 *   magic UTF("PDR_AI_MLP_V1"), modelId UTF, license UTF, dataset UTF,
 *   outputFrame UTF("DEVICE_XY_METRES"), int inputFeatures(24), int hidden(32),
 *   int outputs(4), float[24] inputMean, float[24] inputScale,
 *   float[32*24] w1, float[32] b1, float[4*32] w2, float[4] b2.
 * Output: dx metres, dy metres, log(scale_x metres), log(scale_y metres).
 * A scale is NOT a calibrated covariance or statistical confidence bound.
 */
public final class PortableInertialModel {
    public static final String MAGIC="PDR_AI_MLP_V1";
    public static final String FRAME="DEVICE_XY_METRES";
    public static final int INPUT=InertialWindow.FEATURES, HIDDEN=32, OUTPUT=4;
    private static final int MAX_BYTES=100_000;
    private final float[] mean,scale,w1,b1,w2,b2;
    public final String modelId, licenseId, trainingDataset, sha256;

    private PortableInertialModel(String id,String license,String dataset,String sha,
                                  float[] mean,float[] scale,float[] w1,float[] b1,
                                  float[] w2,float[] b2) {
        modelId=id;licenseId=license;trainingDataset=dataset;sha256=sha;
        this.mean=mean;this.scale=scale;this.w1=w1;this.b1=b1;this.w2=w2;this.b2=b2;
    }

    /** Require an externally reviewed SHA-256; self-asserted metadata is not trust. */
    public static PortableInertialModel load(byte[] artifact,String trustedSha256) throws IOException {
        if(artifact==null || artifact.length<100 || artifact.length>MAX_BYTES)
            throw new IOException("Invalid model size");
        String sha=sha256(artifact);
        if(trustedSha256==null || !trustedSha256.matches("[a-f0-9]{64}")
                || !MessageDigest.isEqual(sha.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                                         trustedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("Neural checkpoint SHA256 is missing or mismatched");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(artifact))){
            if(!MAGIC.equals(in.readUTF()))throw new IOException("Unsupported model schema");
            String id=in.readUTF(),license=in.readUTF(),dataset=in.readUTF(),frame=in.readUTF();
            if(id.isEmpty() || id.length()>100 || license.isEmpty() || license.length()>120
               || dataset.isEmpty() || dataset.length()>200 || !FRAME.equals(frame))
                throw new IOException("Invalid model provenance or output coordinate frame");
            if(in.readInt()!=INPUT || in.readInt()!=HIDDEN || in.readInt()!=OUTPUT)
                throw new IOException("Incompatible neural network dimensions");
            float[] mean=readArray(in,INPUT), scale=readArray(in,INPUT);
            for(float v:scale)if(v<=1e-6f || v>1e6f)
                throw new IOException("Invalid normalization scale");
            float[] w1=readArray(in,HIDDEN*INPUT),b1=readArray(in,HIDDEN);
            float[] w2=readArray(in,OUTPUT*HIDDEN),b2=readArray(in,OUTPUT);
            if(in.read()!=-1)throw new IOException("Trailing bytes in neural checkpoint");
            return new PortableInertialModel(id,license,dataset,sha,
                                             mean,scale,w1,b1,w2,b2);
        }
    }
    private static float[] readArray(DataInputStream in,int count) throws IOException {
        float[] v=new float[count];
        for(int i=0;i<count;i++){
            v[i]=in.readFloat();
            if(!Float.isFinite(v[i]) || Math.abs(v[i])>1e6f)
                throw new IOException("Invalid neural weight");
        }
        return v;
    }
    public static String sha256(byte[] input) {
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder sb=new StringBuilder(64);
            for(byte v:hash)sb.append(String.format(Locale.US,"%02x",v&255));
            return sb.toString();
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }

    public NeuralMotionEstimate infer(float[] rawFeatures,long endTimeNs,double windowSeconds) {
        if(endTimeNs<=0 || !Double.isFinite(windowSeconds)
            || windowSeconds<.7 || windowSeconds>2.5)
            throw new IllegalArgumentException("Invalid neural input time coverage");
        if(rawFeatures==null || rawFeatures.length!=INPUT)
            throw new IllegalArgumentException("Wrong feature vector size");
        double[] normalized=new double[INPUT];
        for(int i=0;i<INPUT;i++){
            if(!Float.isFinite(rawFeatures[i]))
                throw new IllegalArgumentException("nonfinite input feature");
            double n=(rawFeatures[i]-mean[i])/scale[i];
            if(!Double.isFinite(n) || Math.abs(n)>1_000)
                throw new IllegalArgumentException("Input distribution outside model safety bounds");
            normalized[i]=n;
        }
        double[] hidden=new double[HIDDEN];
        for(int h=0;h<HIDDEN;h++){
            double value=b1[h];
            for(int i=0;i<INPUT;i++)value+=normalized[i]*w1[h*INPUT+i];
            hidden[h]=Math.max(0,value); // ReLU
            if(!Double.isFinite(value)||value>1e9)
                throw new IllegalStateException("Unstable neural hidden layer");
        }
        double[] y=new double[OUTPUT];
        for(int o=0;o<OUTPUT;o++){
            double value=b2[o];
            for(int h=0;h<HIDDEN;h++)value+=hidden[h]*w2[o*HIDDEN+h];
            if(!Double.isFinite(value))throw new IllegalStateException("Unstable neural output");
            y[o]=value;
        }
        double speed=Math.hypot(y[0],y[1])/windowSeconds;
        if(speed>8 || y[2]<-7 || y[2]>4 || y[3]<-7 || y[3]>4)
            throw new IllegalStateException("Unphysical or uncalibrated neural candidate");
        return new NeuralMotionEstimate(endTimeNs,y[0],y[1],
                                        Math.exp(y[2]),Math.exp(y[3]),
                                        modelId,FRAME);
    }
}
