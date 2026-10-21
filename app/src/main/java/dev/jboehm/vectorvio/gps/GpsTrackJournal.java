package dev.jboehm.vectorvio.gps;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Crash-recoverable line journal; incomplete last line is never interpreted.
 * GPS validity/continuity gates affect the derived GPX, not an AR/VIO stream. */
public final class GpsTrackJournal implements Closeable {
    private final File file;private final FileOutputStream bytes;private final BufferedWriter out;
    private GpsNavigator.Fix previous;private boolean gap=true;private long writtenBytes;
    public int count;
    public GpsTrackJournal(File file)throws IOException{this.file=file;bytes=new FileOutputStream(file,false);out=new BufferedWriter(new OutputStreamWriter(bytes,StandardCharsets.UTF_8),32768);writeRecord("VECTOR_GPS_TRACK_V2\n");out.flush();}
    public boolean append(GpsNavigator.Fix f,long now)throws IOException{
        if(count>=GpsRoute.MAX_POINTS)throw new IOException("Recording limit reached; save this track");
        // Preserve received coordinates/accuracy even if unsuitable for a derived route.
        writeRecord("R,"+f.lat+","+f.lon+","+(Double.isFinite(f.ele)?f.ele:"")+","+f.ns+","+f.accuracy+","+f.speed+","+f.bearing+"\n");
        if(!Geo.valid(f.lat,f.lon)||!Double.isFinite(f.accuracy)||f.accuracy<=0||f.accuracy>30||f.ns<=0||f.ns>now||now-f.ns>5_000_000_000L){gap=true;return false;}
        if(previous!=null&&f.ns<=previous.ns)return false;
        if(previous!=null){double dt=(f.ns-previous.ns)*1e-9;if(dt>15)gap=true;
            if(!GpsFixRules.plausible(previous,f)){previous=null;gap=true;return false;}}
        if(gap){writeRecord("B\n");gap=false;}
        writeRecord("P,"+f.lat+","+f.lon+","+(Double.isFinite(f.ele)?f.ele:"")+","+f.ns+","+f.accuracy+"\n");count++;previous=f;return true;
    }
    private void writeRecord(String row)throws IOException{
        int size=row.getBytes(StandardCharsets.UTF_8).length;
        if(writtenBytes+size>GpxIO.MAX_BYTES)throw new IOException("Recording byte limit reached; save this track");
        out.write(row);writtenBytes+=size;
    }
    public void flush()throws IOException{out.flush();}
    @Override public void close()throws IOException{try{out.flush();bytes.getFD().sync();}finally{try{out.close();}finally{bytes.close();}}}
    public static List<GpsRoute> recover(File file)throws Exception{
        if(file.length()>GpxIO.MAX_BYTES+1024)throw new IOException("Track journal too large");
        ArrayList<GpsRoute> result=new ArrayList<>();ArrayList<GpsRoute.Point> points=new ArrayList<>();int total=0;
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){ByteArrayOutputStream line=new ByteArrayOutputStream();int b;boolean first=true;
            while((b=in.read())!=-1){if(b!='\n'){if(line.size()>512)throw new IOException("Invalid journal line");line.write(b);continue;}
                String s=line.toString("UTF-8");line.reset();if(first){first=false;if(!s.equals("VECTOR_GPS_TRACK_V1")&&!s.equals("VECTOR_GPS_TRACK_V2"))throw new IOException("Unknown track journal");continue;}
                if(s.startsWith("R,"))continue;
                if(s.equals("B")){add(result,points);points=new ArrayList<>();}
                else if(s.startsWith("P,")){String[] v=s.split(",",-1);if(v.length!=6)throw new IOException("Invalid track point");points.add(new GpsRoute.Point(Double.parseDouble(v[1]),Double.parseDouble(v[2]),v[3].isEmpty()?Double.NaN:Double.parseDouble(v[3])));if(++total>GpsRoute.MAX_POINTS)throw new IOException("Too many track points");}
                else throw new IOException("Invalid track record");
            }
        }
        add(result,points);if(result.isEmpty())throw new IOException("No continuous recorded segment with two points");return result;
    }
    private static void add(List<GpsRoute> out,List<GpsRoute.Point> p)throws IOException{
        if(p.size()<2)return;
        try{out.add(new GpsRoute("Promenade · segment "+(out.size()+1),p));}catch(IllegalArgumentException noLength){return;}
        if(out.size()>GpxIO.MAX_SEGMENTS)throw new IOException("Too many recording segments");
    }
}
