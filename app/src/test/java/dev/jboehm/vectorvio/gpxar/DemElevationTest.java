package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.gps.GpsRoute;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class DemElevationTest {
    private static GpsRoute route(double... heights){
        List<GpsRoute.Point> pts=new ArrayList<>();
        for(int i=0;i<heights.length;i++)pts.add(new GpsRoute.Point(48+i*.00045,7,heights[i]));
        return new GpsRoute("Mountain test",pts);
    }
    @Test public void missingElevationEnrichesWithoutChangingCoordinatesOrOriginal() throws Exception{
        GpsRoute before=route(Double.NaN,Double.NaN,Double.NaN);
        GpsRoute after=DemElevation.fill(before,points->{
            double[] result=new double[points.size()];
            for(int i=0;i<result.length;i++)result[i]=300+10.0*i/(result.length-1);
            return result;
        });
        assertTrue(after.name.endsWith(DemElevation.SUFFIX));
        for(int i=0;i<before.points.size();i++){
            assertEquals(before.points.get(i).lat,after.points.get(i).lat,0);
            assertEquals(before.points.get(i).lon,after.points.get(i).lon,0);
            assertTrue(Double.isNaN(before.points.get(i).ele));
            assertTrue(Double.isFinite(after.points.get(i).ele));
        }
        assertEquals(300,after.points.get(0).ele,1e-9);
        assertEquals(310,after.points.get(2).ele,1e-9);
    }
    @Test public void existingHeightsNeverTriggerNetwork() throws Exception{
        GpsRoute r=route(200,202);
        assertSame(r,DemElevation.fill(r,points->{fail("No external requests for original heights");return null;}));
        try{
            DemElevation.fill(route(200,Double.NaN),points->{fail("No mixing of unknown GPX and DEM datums");return null;});
            fail("Expected rejection of mixed datums");
        }catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("datum"));}
    }
    @Test public void errorAndMissingSamplesCannotProduceFalsifiedHeights() throws Exception{
        GpsRoute r=route(Double.NaN,Double.NaN);
        for(double[] reply:new double[][]{{0},{100,Double.NaN},{100,Double.POSITIVE_INFINITY},{100,200,300}}){
            try{DemElevation.fill(r,points->reply);fail("Expected strict DEM validation");}
            catch(IllegalArgumentException expected){}
        }
        assertEquals(2,DemElevation.missing(r));
    }
    @Test public void provenanceSurvivesLongTrackNamesAndReverse()throws Exception{
        char[] chars=new char[120];Arrays.fill(chars,'x');
        GpsRoute r=new GpsRoute(new String(chars),Arrays.asList(new GpsRoute.Point(48,7,Double.NaN),new GpsRoute.Point(48.001,7,Double.NaN)));
        GpsRoute enriched=DemElevation.fill(r,points->{double[] h=new double[points.size()];Arrays.fill(h,345);return h;});
        assertTrue(enriched.name.endsWith(DemElevation.SUFFIX));
        assertTrue(enriched.reverse().name.contains(DemElevation.SUFFIX));
    }
    @Test public void denseOrLongRouteQueriesAreBoundedAndInterpolateAllVertices()throws Exception{
        List<GpsRoute.Point> pts=new ArrayList<>();
        for(int i=0;i<5000;i++)pts.add(new GpsRoute.Point(48+i*.000018,7,Double.NaN));
        GpsRoute route=new GpsRoute("Dense",pts);
        GpsRoute enriched=DemElevation.fill(route,probes->{
            assertTrue(probes.size()<=DemElevation.MAX_SAMPLES);
            double[] a=new double[probes.size()];Arrays.fill(a,500);return a;
        });
        assertEquals(5000,enriched.points.size());
        for(GpsRoute.Point p:enriched.points)assertEquals(500,p.ele,1e-8);
    }
}
