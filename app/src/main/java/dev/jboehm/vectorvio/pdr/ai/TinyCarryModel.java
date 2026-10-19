package dev.jboehm.vectorvio.pdr.ai;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Audited tiny REAL supervised 24->16->3 IMU model, CPU-only.
 * IMPORTANT: labels refer to SIMUL experiment sensor placement, NOT reliable
 * device location or heading. Research DIAGNOSTIC ONLY; never feed PdrEngine.
 * No Android/TFLite dependency; all predictions use float32 checkpoint weights.
 */
public final class TinyCarryModel {
    public static final String MAGIC="PDR_TINY_CARRY_V1";
    public static final String OUTPUT_FRAME="SENSOR_PLACEMENT_PROXY_3_CLASSES";
    public static final int INPUT=24,HIDDEN=16,OUTPUT=3,MACS=INPUT*HIDDEN+HIDDEN*OUTPUT;
    public static final String[] LABELS={"front-pocket sensor","hand sensor","back-pocket sensor"};
    /** SHA is pinned in audited Java source; editing the asset alone cannot retune it. */
    public static final String AUDITED_SHA256="9e572920b796d03ea528a040694039851461b5c39e6d1549809c751da3940dc2";
    private final float[] mean,scale,w1,b1,w2,b2;
    public final String modelId, licenseId, datasetId, sha256;

    private TinyCarryModel(String id,String license,String dataset,String sha,
                           float[] means,float[] scales,float[] layer1,float[] bias1,
                           float[] layer2,float[] bias2){
        modelId=id;licenseId=license;datasetId=dataset;sha256=sha;
        mean=means;scale=scales;w1=layer1;b1=bias1;w2=layer2;b2=bias2;
    }
    public static TinyCarryModel loadBase64(String base64) throws IOException {
        if(base64==null||base64.length()>16_384)throw new IOException("invalid carry model text");
        byte[] data;
        try{data=java.util.Base64.getDecoder().decode(base64.trim());}
        catch(IllegalArgumentException e){throw new IOException("invalid model base64",e);}
        return load(data,AUDITED_SHA256);
    }
    public static TinyCarryModel load(byte[] data,String trustedSha) throws IOException {
        if(data==null||data.length<200||data.length>10_000)throw new IOException("model size");
        String actual=sha256(data);
        if(trustedSha==null||!trustedSha.matches("[a-f0-9]{64}")||
            !MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                                   trustedSha.getBytes(StandardCharsets.US_ASCII)))
            throw new IOException("untrusted carry model checksum");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))){
            if(!MAGIC.equals(in.readUTF()))throw new IOException("model magic");
            String id=in.readUTF(),lic=in.readUTF(),dataset=in.readUTF(),frame=in.readUTF();
            if(!OUTPUT_FRAME.equals(frame)||id.length()>100||id.isEmpty()||
               !"CC-BY-SA-4.0".equals(lic)||!dataset.startsWith("SIMUL@ee7eb4e89bf33"))
                throw new IOException("model identity/attribution/frame");
            if(in.readInt()!=INPUT||in.readInt()!=HIDDEN||in.readInt()!=OUTPUT)
                throw new IOException("model dimensions");
            float[] mu=read(in,INPUT),sigma=read(in,INPUT);
            for(float f:sigma)if(f<.000001||f>1e5)throw new IOException("bad normalization");
            float[] w1=read(in,INPUT*HIDDEN),b1=read(in,HIDDEN);
            float[] w2=read(in,HIDDEN*OUTPUT),b2=read(in,OUTPUT);
            if(in.read()!=-1)throw new IOException("extra model bytes");
            return new TinyCarryModel(id,lic,dataset,actual,mu,sigma,w1,b1,w2,b2);
        }
    }
    private static float[] read(DataInputStream in,int size) throws IOException {
        float[] result=new float[size];
        for(int i=0;i<size;i++){
            float f=in.readFloat();
            if(!Float.isFinite(f)||Math.abs(f)>1e6f)throw new IOException("invalid weight");
            result[i]=f;
        }
        return result;
    }
    public static String sha256(byte[] bytes){
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder b=new StringBuilder();
            for(byte h:hash)b.append(String.format(Locale.US,"%02x",h&255));
            return b.toString();
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    /** Exactly 432 MACs, no GPU and no native library; output is not calibrated probability. */
    public double[] predictFeatures(float[] features){
        if(features==null||features.length!=INPUT)throw new IllegalArgumentException("24 features required");
        double[] hidden=new double[HIDDEN];
        for(int k=0;k<HIDDEN;k++){
            double s=b1[k];
            for(int i=0;i<INPUT;i++){
                float v=features[i];
                if(!Float.isFinite(v))throw new IllegalArgumentException("non-finite features");
                double normalized=(v-mean[i])/scale[i];
                if(!Double.isFinite(normalized)||Math.abs(normalized)>1000)
                    throw new IllegalArgumentException("out-of-domain IMU");
                s+=normalized*w1[k*INPUT+i];
            }
            hidden[k]=Math.max(s,0);
        }
        double[] y=new double[OUTPUT];
        double high=-Double.MAX_VALUE;
        for(int j=0;j<OUTPUT;j++){
            double v=b2[j];
            for(int k=0;k<HIDDEN;k++)v+=hidden[k]*w2[j*HIDDEN+k];
            if(!Double.isFinite(v))throw new IllegalArgumentException("bad neural output");
            y[j]=v;high=Math.max(high,v);
        }
        double z=0;
        for(int j=0;j<OUTPUT;j++){y[j]=Math.exp(y[j]-high);z+=y[j];}
        for(int j=0;j<OUTPUT;j++)y[j]/=z;
        return y;
    }
    public double[] predictWindow(float[] samples){
        return predictFeatures(InertialWindow.features(samples));
    }
}
