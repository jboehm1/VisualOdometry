package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.gps.GpsRoute;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Optional, explicitly requested DEM enrichment. Never modifies GPS fixes, the
 * original external GPX, or the AR ground model. DEM heights are estimates,
 * NOT ARCore surface observations. An incomplete fetch never writes anything.
 */
public final class DemElevation {
    public static final String SUFFIX=" · EU-DEM 25m (estimated EVRS2000)";
    public static final String DATUM="EVRS2000_ESTIMATED";
    public static final int MAX_SAMPLES=1001;
    private DemElevation(){}
    public interface Source { double[] lookup(List<GpsRoute.Point> points) throws Exception; }

    public static int missing(GpsRoute route){
        int count=0;for(GpsRoute.Point p:route.points)if(!Double.isFinite(p.ele))count++;return count;
    }
    public static GpsRoute fill(GpsRoute route,Source source) throws Exception{
        if(route==null||source==null)throw new IllegalArgumentException("Missing route / elevation source");
        int absent=missing(route);
        if(absent==0)return route;
        // Mixing unlabelled existing GPX heights with EVRS2000 DEM would be
        // dishonest: GPX has no mandatory vertical datum. Keep the route intact.
        if(absent!=route.points.size())throw new IllegalArgumentException(
            "Partial GPX heights: unknown vertical datum; no automatic mixing");
        int intervals=Math.min(MAX_SAMPLES-1,Math.max(1,(int)Math.ceil(route.length/50.0)));
        List<GpsRoute.Point> probes=new ArrayList<>(intervals+1);
        for(int i=0;i<=intervals;i++)probes.add(route.at(route.length*i/intervals));
        double[] heights=source.lookup(probes);
        if(heights==null||heights.length!=probes.size())
            throw new IllegalArgumentException("DEM response count mismatch");
        for(double h:heights)if(!Double.isFinite(h)||h< -500||h>9000)
            throw new IllegalArgumentException("DEM returned missing or invalid elevation");
        ArrayList<GpsRoute.Point> out=new ArrayList<>(route.points.size());
        for(int i=0;i<route.points.size();i++){
            double t=(route.progressAt(i)/route.length)*intervals;
            int low=Math.min(intervals-1,Math.max(0,(int)Math.floor(t)));
            double fraction=Math.max(0,Math.min(1,t-low));
            double elevation=heights[low]+fraction*(heights[low+1]-heights[low]);
            GpsRoute.Point p=route.points.get(i);
            out.add(new GpsRoute.Point(p.lat,p.lon,elevation));
        }
        return new GpsRoute(route.name+SUFFIX,out);
    }

    /** The public OpenTopoData endpoint: EU-DEM v1.1, vertical datum EVRS2000.
     * One request/sec, maximum 100 coordinates/request. No background telemetry.
     */
    public static final class OpenTopoData implements Source {
        private static final int BATCH=60;
        @Override public double[] lookup(List<GpsRoute.Point> points) throws Exception{
            if(points.isEmpty()||points.size()>MAX_SAMPLES)throw new IllegalArgumentException("DEM sample count exceeded");
            double[] heights=new double[points.size()];
            for(int start=0;start<points.size();start+=BATCH){
                if(start>0)Thread.sleep(1100); // Public service fair-use: 1 call/second.
                int end=Math.min(points.size(),start+BATCH);
                StringBuilder locations=new StringBuilder();
                for(int i=start;i<end;i++){
                    if(i>start)locations.append('|');
                    GpsRoute.Point p=points.get(i);
                    locations.append(String.format(java.util.Locale.US,"%.6f,%.6f",p.lat,p.lon));
                }
                String encoded=URLEncoder.encode(locations.toString(),"UTF-8");
                URL url=new URL("https://api.opentopodata.org/v1/eudem25m?locations="+encoded);
                HttpsURLConnection connection=(HttpsURLConnection)url.openConnection();
                connection.setConnectTimeout(8000);connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept","application/json");
                try{
                    if(connection.getResponseCode()!=200)
                        throw new java.io.IOException("DEM service unavailable (HTTP "+connection.getResponseCode()+")");
                    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                    try(InputStream input=connection.getInputStream()){
                        byte[] part=new byte[4096];int n;
                        while((n=input.read(part))!=-1){
                            if(buffer.size()+n>256*1024)throw new java.io.IOException("DEM response too large");
                            buffer.write(part,0,n);
                        }
                    }
                    JSONObject doc=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
                    if(!"OK".equals(doc.optString("status")))
                        throw new java.io.IOException("DEM returned an unsuccessful status");
                    JSONArray rows=doc.getJSONArray("results");
                    if(rows.length()!=end-start)throw new java.io.IOException("DEM point count mismatch");
                    for(int i=start;i<end;i++){
                        JSONObject row=rows.getJSONObject(i-start);
                        if(row.isNull("elevation")||!"eudem25m".equals(row.optString("dataset")))
                            throw new java.io.IOException("EU-DEM elevation missing / unexpected dataset");
                        JSONObject location=row.getJSONObject("location");
                        GpsRoute.Point p=points.get(i);
                        if(Math.abs(location.getDouble("lat")-p.lat)>0.00001 ||
                           Math.abs(location.getDouble("lng")-p.lon)>0.00001)
                            throw new java.io.IOException("DEM coordinate mismatch");
                        heights[i]=row.getDouble("elevation");
                    }
                }finally{connection.disconnect();}
            }
            return heights;
        }
    }
}
