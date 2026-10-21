package dev.jboehm.vectorvio.location;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

/** Local-only A/B recording. Serial use from the dedicated location worker. */
public final class LocationComparisonRecorder implements AutoCloseable {
    public static final String RAW_CSV="fixes.csv",PAIRS_CSV="pairs.csv",META_JSON="metadata.json";
    private static final long MAX_BYTES=25L*1024*1024;
    private static final int MAX_ROWS=200_000;
    private final File directory;
    private final BufferedWriter raw,pairs;
    private final long startMs;
    private final String device;
    private long rows,lastFlushMs;
    private boolean closed;

    public LocationComparisonRecorder(File privateRoot,long startMs,String device)throws IOException {
        this.startMs=startMs;
        this.device=device==null?"unknown":device;
        if(!privateRoot.isDirectory()&&!privateRoot.mkdirs())throw new IOException("Cannot create private session root");
        SimpleDateFormat fmt=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        directory=new File(privateRoot,"location_ab_"+fmt.format(new Date(startMs))+"_"+
            UUID.randomUUID().toString().substring(0,8));
        if(!directory.mkdir())throw new IOException("Cannot create private session folder");
        raw=open(new File(directory,RAW_CSV));
        try{pairs=open(new File(directory,PAIRS_CSV));}
        catch(IOException e){raw.close();throw e;}
        raw.write("source,fix_elapsed_ns,receive_elapsed_ns,fix_wall_ms,latitude_deg,longitude_deg,horizontal_accuracy_m,altitude_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg,bearing_accuracy_deg,mock,validity\n");
        pairs.write("gps_time_ns,fused_time_ns,abs_delta_ms,disagreement_m,gps_accuracy_m,fused_accuracy_m,gps_lat,gps_lon,fused_lat,fused_lon\n");
        lastFlushMs=startMs;
    }
    private static BufferedWriter open(File f)throws IOException {
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f),StandardCharsets.UTF_8),32768);
    }
    private static String value(double n){return Double.isFinite(n)?Double.toString(n):"";}
    public File directory(){return directory;}
    public long rows(){return rows;}
    public void record(LocationSample s,LocationPairer.Pair pair,long wallNowMs)throws IOException {
        if(closed)throw new IOException("Session closed");
        if(rows>=MAX_ROWS || new File(directory,RAW_CSV).length()+new File(directory,PAIRS_CSV).length()>MAX_BYTES)
            throw new IOException("Logging limit reached");
        raw.write(s.source+","+s.fixElapsedNs+","+s.receiveElapsedNs+","+s.wallTimeMs+","+
            value(s.latitude)+","+value(s.longitude)+","+value(s.accuracyMeters)+","+
            value(s.altitudeMeters)+","+value(s.verticalAccuracyMeters)+","+
            value(s.speedMps)+","+value(s.speedAccuracyMps)+","+
            value(s.bearingDegrees)+","+value(s.bearingAccuracyDegrees)+","+
            s.mock+","+s.validity()+"\n");
        if(pair!=null)pairs.write(pair.gps.fixElapsedNs+","+pair.fused.fixElapsedNs+","+
            value(pair.absoluteTimestampDeltaNs/1e6)+","+value(pair.distanceMeters)+","+
            value(pair.gps.accuracyMeters)+","+value(pair.fused.accuracyMeters)+","+
            value(pair.gps.latitude)+","+value(pair.gps.longitude)+","+
            value(pair.fused.latitude)+","+value(pair.fused.longitude)+"\n");
        rows++;
        if(wallNowMs-lastFlushMs>=3000){raw.flush();pairs.flush();lastFlushMs=wallNowMs;}
    }
    public void finish(String reason,LocationPairer.Snapshot stats,long stopMs)throws IOException {
        if(closed)return;
        closed=true;IOException problem=null;
        try{raw.close();}catch(IOException e){problem=e;}
        try{pairs.close();}catch(IOException e){if(problem==null)problem=e;}
        try(BufferedWriter m=open(new File(directory,META_JSON))){
            m.write("{\n  \"schema\": \"vector-location-ab/1\",\n");
            m.write("  \"start_wall_ms\": "+startMs+",\n");
            m.write("  \"stop_wall_ms\": "+stopMs+",\n");
            m.write("  \"device\": \""+escape(device)+"\",\n");
            m.write("  \"stop_reason\": \""+escape(reason)+"\",\n");
            m.write("  \"gps_api\": \"Android LocationManager.GPS_PROVIDER\",\n");
            m.write("  \"fused_api\": \"Google Play services FusedLocationProviderClient PRIORITY_HIGH_ACCURACY\",\n");
            m.write("  \"requested_interval_ms\": 1000,\n");
            m.write("  \"foreground_only\": true,\n");
            m.write("  \"approximate_permission_allowed\": false,\n");
            m.write("  \"raw_fix_count\": "+rows+",\n");
            m.write("  \"paired_count\": "+stats.pairCount+",\n");
            m.write("  \"accuracy_ground_truth\": \"NOT_AVAILABLE\",\n");
            m.write("  \"note\": \"Both APIs may share GNSS; disagreement is not absolute positioning accuracy. No navigation feedback, VIO or PDR.\"\n}\n");
        }catch(IOException e){if(problem==null)problem=e;}
        if(problem!=null)throw problem;
    }
    private static String escape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")
                .replace("\r"," ").replace("\t"," ");
    }
    @Override public void close()throws IOException {
        if(!closed)finish("INTERRUPTED",new LocationPairer().snapshot(),System.currentTimeMillis());
    }

    /** Export exactly three finalized whitelisted files. No arbitrary private-file access. */
    public static void exportZip(File dir,OutputStream dest)throws IOException {
        if(dir==null||!dir.isDirectory())throw new IOException("Missing comparison session");
        byte[] buffer=new byte[65536];
        try(ZipOutputStream zip=new ZipOutputStream(dest)){
            for(String name:new String[]{META_JSON,RAW_CSV,PAIRS_CSV}){
                File f=new File(dir,name);
                if(!f.isFile()||!f.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())||
                   f.length()>MAX_BYTES)throw new IOException("Missing or oversized session member "+name);
                zip.putNextEntry(new ZipEntry(name));
                try(InputStream in=new FileInputStream(f)){int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}
                zip.closeEntry();
            }
            zip.finish();
        }
    }
}
