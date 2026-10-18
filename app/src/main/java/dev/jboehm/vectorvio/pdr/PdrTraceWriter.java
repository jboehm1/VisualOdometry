package dev.jboehm.vectorvio.pdr;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streaming, private-cache trace. Never commit user motion data to Git. */
public final class PdrTraceWriter {
    private final File dir,events,raw;
    private BufferedWriter eventOut,rawOut;
    private int written;
    public PdrTraceWriter(Context c) throws IOException {
        dir=new File(c.getCacheDir(),"pdr_"+System.currentTimeMillis());
        if(!dir.mkdirs())throw new IOException("Cannot create PDR session directory");
        events=new File(dir,"events.csv");raw=new File(dir,"raw_sensors.csv");
        eventOut=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(events),StandardCharsets.UTF_8),32768);
        rawOut=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(raw),StandardCharsets.UTF_8),32768);
        eventOut.write("t_ns,type,x,y,z,bearing_accuracy_deg\n");
        rawOut.write("t_ns,type,x,y,z,w,accuracy\n");
    }
    public void sample(String type,long t,double x,double y,double z,int accuracy) {
        if(eventOut==null)return;
        try{
            BufferedWriter target=type.startsWith("RAW_")?rawOut:eventOut;
            if(type.startsWith("RAW_"))target.write(String.format(Locale.US,
                    "%d,%s,%.8f,%.8f,%.8f,,%d%n",t,type,x,y,z,accuracy));
            else target.write(String.format(Locale.US,
                    "%d,%s,%.8f,%.8f,%.8f,%n",t,type,x,y,z));
            if(++written%256==0){eventOut.flush();rawOut.flush();}
        }catch(IOException e){throw new IllegalStateException("PDR trace write failed",e);}
    }
    /** Full quaternion for ML dataset; heading remains a separately derived event. */
    public void rotationVector(long t,float[] v,int accuracy) {
        if(rawOut==null)return;
        try{
            rawOut.write(String.format(Locale.US,"%d,RAW_ROTATION_VECTOR,%.9f,%.9f,%.9f,%.9f,%d%n",
                t,v[0],v[1],v[2],v.length>3?v[3]:Double.NaN,accuracy));
        }catch(IOException e){throw new IllegalStateException("rotation write failed",e);}
    }
    /** Store all GNSS course rejection inputs for bit-for-bit replay gating. */
    public void gpsCourse(long t,double radians,float speed,float horizontalAccuracy,float bearingAccuracy) {
        if(eventOut==null)return;
        try {
            eventOut.write(String.format(Locale.US,"%d,GPS_COURSE,%.10f,%.6f,%.6f,%.6f%n",
                t,radians,speed,horizontalAccuracy,bearingAccuracy));
        }catch(IOException e){throw new IllegalStateException("GPS trace write failed",e);}
    }
    public void step(long t){sample("STEP",t,0,0,0,0);}
    public void close() throws IOException {
        if(eventOut!=null){eventOut.close();eventOut=null;}
        if(rawOut!=null){rawOut.close();rawOut=null;}
    }
    /** Old signature retained for the existing file-format and Robolectric tests. */
    public void export(Context ctx,Uri uri,List<PdrEngine.Point> points,
                       PdrEngine.Snapshot snap,String stepMode) throws IOException {
        export(ctx,uri,points,snap,stepMode,"UNSPECIFIED","UNSPECIFIED");
    }
    /** Call only after close(): export replayable events, raw sensors and metric path. */
    public void export(Context ctx,Uri uri,List<PdrEngine.Point> points,
                       PdrEngine.Snapshot snap,String stepMode,String gaitProfile,
                       String headingPolicy) throws IOException {
        close();
        try(OutputStream dst=ctx.getContentResolver().openOutputStream(uri)){
            if(dst==null)throw new IOException("No output stream");
            try(ZipOutputStream zip=new ZipOutputStream(dst,StandardCharsets.UTF_8)){
                addFile(zip,events,"events.csv");addFile(zip,raw,"raw_sensors.csv");
                zip.putNextEntry(new ZipEntry("path.csv"));
                zip.write("t_ns,east_m,north_m,gap_before\n".getBytes(StandardCharsets.UTF_8));
                for(PdrEngine.Point p:points)zip.write(String.format(Locale.US,
                    "%d,%.6f,%.6f,%s%n",p.timeNs,p.eastM,p.northM,p.gapBefore).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("metadata.txt"));
                zip.write(("VECTOR VIO PDR, no camera\n"+
                    "Format version: PDR-2 (gap_before in path.csv)\n"+
                    "Coordinate frame: local magnetic north/east-like metres (not WGS84).\n"+
                    "Timestamps: Android elapsedRealtimeNanos since boot.\n"+
                    "Distance: detected steps times configured step length; not ground truth.\n"+
                    "Heading: GNSS course if valid, else DEVICE TOP assumed walking forward.\n"+
                    "GNSS is NOT used to calculate PDR distance; no position fix applied.\n"+
                    "Pressure altitude: relative, weather drift possible.\n"+
                    "Step mode: "+stepMode+"\n"+
                    "Gait profile: "+gaitProfile+"\n"+
                    "Direction policy: "+headingPolicy+"\n"+
                    "Step length m: "+snap.stepLengthM+"\n"+
                    "Steps: "+snap.steps+"\n"+
                    "Estimated distance m: "+snap.distanceM+"\n"+
                    "Steps with no heading: "+snap.stepsWithoutHeading+"\n")
                    .getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
        }
    }
    private static void addFile(ZipOutputStream out,File file,String name)throws IOException{
        out.putNextEntry(new ZipEntry(name));
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[8192];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        out.closeEntry();
    }
}
