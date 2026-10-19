package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.gps.GpsRoute;
import dev.jboehm.vectorvio.location.LocationSample;

/** CSV encoding only; caller's recorder owns bounded asynchronous I/O and session identity. */
public final class GpxArJournal {
    private GpxArJournal(){}
    public static final String FIX_HEADER="route_revision,source,fix_elapsed_ns,receive_elapsed_ns,fix_wall_ms,latitude_deg,longitude_deg,horizontal_accuracy_m,altitude_m,vertical_accuracy_m,speed_mps,speed_accuracy_mps,bearing_deg,bearing_accuracy_deg,mock,queued_for_pairing";
    public static final String ROUTE_HEADER="route_revision,index,latitude_deg,longitude_deg,gpx_elevation_m,vertical_datum";
    public static String value(double n){return Double.isFinite(n)?Double.toString(n):"";}
    public static String fixRow(long revision,LocationSample s,boolean queued){
        return revision+","+s.source+","+s.fixElapsedNs+","+s.receiveElapsedNs+","+s.wallTimeMs+","+
            value(s.latitude)+","+value(s.longitude)+","+value(s.accuracyMeters)+","+value(s.altitudeMeters)+","+
            value(s.verticalAccuracyMeters)+","+value(s.speedMps)+","+value(s.speedAccuracyMps)+","+
            value(s.bearingDegrees)+","+value(s.bearingAccuracyDegrees)+","+s.mock+","+queued;
    }
    /** Build on recorder worker, not camera/UI thread. End newline is added by the recorder. */
    public static String routeRows(long revision,GpsRoute route){
        StringBuilder rows=new StringBuilder();int i=0;
        // Source is persisted visibly in the GPX track name (including reverse).
        // Unknown source GPX elevations must never be relabelled as ellipsoidal.
        String datum=route.name.contains(DemElevation.SUFFIX)?DemElevation.DATUM:"UNKNOWN";
        for(GpsRoute.Point p:route.points){
            if(i>0)rows.append('\n');rows.append(revision).append(',').append(i++).append(',')
                .append(value(p.lat)).append(',').append(value(p.lon)).append(',').append(value(p.ele)).append(',').append(datum);
        }
        return rows.toString();
    }
}
