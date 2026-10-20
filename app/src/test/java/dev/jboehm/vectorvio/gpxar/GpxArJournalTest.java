package dev.jboehm.vectorvio.gpxar;
import org.junit.Test;
import static org.junit.Assert.*;
import dev.jboehm.vectorvio.location.LocationSample;
import dev.jboehm.vectorvio.gps.GpsRoute;
import java.util.Arrays;
public class GpxArJournalTest {
    @Test public void rawRejectedFixRetainsTimestampsSourceAndOptionalFields(){
        LocationSample s=new LocationSample(LocationSample.Source.GOOGLE_FUSED,100,200,300,48,7,Double.NaN,301,2,3,1,40,5,true);
        String row=GpxArJournal.fixRow(4,s,false);String[] cells=row.split(",",-1);
        assertEquals(GpxArJournal.FIX_HEADER.split(",").length,cells.length);assertEquals("GOOGLE_FUSED",cells[1]);assertEquals("100",cells[2]);assertEquals("200",cells[3]);assertEquals("",cells[7]);assertEquals("301.0",cells[8]);assertTrue(row.endsWith(",true,false"));
    }
    @Test public void demEnrichedRouteExportsItsApproximateDatumIncludingReverse()throws Exception{
        GpsRoute r=new GpsRoute("Elevation test",Arrays.asList(new GpsRoute.Point(48,7,Double.NaN),new GpsRoute.Point(48.001,7,Double.NaN)));
        GpsRoute filled=DemElevation.fill(r,probes->{double[] elevations=new double[probes.size()];Arrays.fill(elevations,400);return elevations;});
        String rows=GpxArJournal.routeRows(42,filled.reverse());
        assertTrue(rows.contains(",400.0,EVRS2000_ESTIMATED"));
        assertFalse(rows.contains("UNKNOWN"));
    }
    @Test public void routeExportKeepsUnknownElevationAndRevisionWithoutInventingZero(){
        GpsRoute r=new GpsRoute("name with, comma",Arrays.asList(new GpsRoute.Point(48,7,300),new GpsRoute.Point(48.001,7.001,Double.NaN)));
        String rows=GpxArJournal.routeRows(5,r);assertEquals(2,rows.split("\\n").length);assertTrue(rows.endsWith(",,UNKNOWN"));assertTrue(rows.startsWith("5,0,"));
    }
}
