package dev.jboehm.vectorvio.gps;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class GpsCoreTest {
    private GpsRoute.Point p(double x,double y){return new GpsRoute.Point(Math.toDegrees(y/Geo.R),Math.toDegrees(x/Geo.R),Double.NaN);}
    private GpsRoute line(){return new GpsRoute("Test",Arrays.asList(p(0,0),p(500,0)));}
    private GpsNavigator.Fix fix(double x,double y,double acc,int t){GpsRoute.Point p=p(x,y);return new GpsNavigator.Fix(p.lat,p.lon,acc,2,Double.NaN,Double.NaN,1_000_000_000L+t*1_000_000_000L);}
    private void update(GpsNavigator n,double x,double y,double acc,int t){GpsNavigator.Fix f=fix(x,y,acc,t);n.update(f,f.ns);}
    private List<GpsRoute> xml(String s)throws Exception{return GpxIO.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));}
    @Test public void geodesicAndAntimeridianAreContinuous(){
        assertEquals(111195.08,Geo.distance(0,0,0,1),.1);
        GpsRoute r=new GpsRoute("date line",Arrays.asList(new GpsRoute.Point(0,179.9,0),new GpsRoute.Point(0,-179.9,0)));
        assertEquals(22239.016,r.length,.1);assertEquals(180,Math.abs(r.at(r.length/2).lon),1e-7);
        GpsRoute.Match m=r.nearest(0,180,0,r.length,0,5);assertEquals(0,m.distance,.001);assertEquals(r.length/2,m.progress,.01);
        GpsRoute pole=new GpsRoute("pole",Arrays.asList(new GpsRoute.Point(89.9,-45,0),new GpsRoute.Point(89.9,45,0)));assertTrue(Double.isFinite(pole.at(pole.length/2).lat));
    }
    @Test public void gpxPreservesSegmentsNamesAndPrecision()throws Exception{
        String s="<gpx xmlns='http://www.topografix.com/GPX/1/1'><trk><name>A &amp; B</name><trkseg><trkpt lat='48.123456789' lon='7.1'/><trkpt lat='48.123556789' lon='7.2'><ele>12.5</ele></trkpt></trkseg><trkseg><trkpt lat='49' lon='8'/><trkpt lat='49.1' lon='8.1'/></trkseg></trk><rte><rtept lat='1' lon='2'/><rtept lat='2' lon='3'/></rte></gpx>";
        List<GpsRoute> routes=xml(s);assertEquals(3,routes.size());assertEquals("A & B",routes.get(0).name);assertEquals(48.123456789,routes.get(0).points.get(0).lat,0);
        ByteArrayOutputStream out=new ByteArrayOutputStream();GpxIO.write(routes,out);assertEquals(3,GpxIO.read(new ByteArrayInputStream(out.toByteArray())).size());
    }
    @Test public void malformedOrUnsafeGpxFailsWithoutSilentBridges()throws Exception{
        for(String s:Arrays.asList("<!DOCTYPE gpx [<!ENTITY e SYSTEM 'file:///etc/passwd'>]><gpx/>","<gpx><trk><trkseg><trkpt lat='NaN' lon='0'/><trkpt lat='1' lon='1'/></trkseg></trk></gpx>","<gpx><rte><rtept lat='91' lon='0'/><rtept lat='1' lon='1'/></rte></gpx>","<html/>","<gpx><trk><trkseg><trkpt lat='1' lon='1'/></trkseg></trk></gpx>")){
            try{xml(s);fail("must reject");}catch(Exception expected){}
        }
    }
    @Test public void gpxBudgetIsEnforced()throws Exception{
        StringBuilder s=new StringBuilder("<gpx><rte>");for(int i=0;i<=GpsRoute.MAX_POINTS;i++)s.append("<rtept lat='1' lon='2'/>");s.append("</rte></gpx>");try{xml(s.toString());fail();}catch(Exception expected){assertTrue(expected.getMessage().contains("50000"));}
    }
    @Test public void jitterIsMatchedWhileRawOffRouteAndAccuracyRemainVisible(){
        GpsNavigator n=new GpsNavigator(line());Random random=new Random(46);
        for(int t=0;t<=100;t++)update(n,t*2,random.nextGaussian()*5,8,t);
        assertEquals(200,n.progress,20);assertEquals(GpsNavigator.Status.FOLLOWING,n.status);
        for(int t=101;t<=108;t++)update(n,200,60,5,t);
        assertEquals(GpsNavigator.Status.OFF_ROUTE,n.status);assertTrue(n.offDistance>50);
        for(int t=109;t<=115;t++)update(n,205,0,5,t);assertEquals(GpsNavigator.Status.FOLLOWING,n.status);
        double progress=n.progress;update(n,350,0,100,116);assertEquals(GpsNavigator.Status.POOR_ACCURACY,n.status);assertEquals(progress,n.progress,0);
    }
    @Test public void loopDoesNotArriveAtStartOrJumpAcrossParallelReturn(){
        GpsRoute r=new GpsRoute("loop",Arrays.asList(p(0,0),p(300,0),p(300,20),p(0,20),p(0,0)));
        GpsNavigator n=new GpsNavigator(r);for(int t=0;t<=100;t++)update(n,2*t,14,15,t);
        assertTrue(n.progress>170);assertTrue(n.progress<250);assertTrue(n.status!=GpsNavigator.Status.ARRIVED);
        GpsNavigator join=new GpsNavigator(r);join.joinNearest();update(join,150,10,10,0);assertEquals(GpsNavigator.Status.AMBIGUOUS,join.status);
    }
    @Test public void staleAndOutOfOrderFixesCannotAdvanceAndLongGapNeedsConfirmation(){
        GpsNavigator n=new GpsNavigator(line());update(n,0,0,5,0);update(n,2,0,5,1);double s=n.progress;
        update(n,400,0,5,0);assertEquals(s,n.progress,0);n.tick(10_000_000_000L);assertEquals(GpsNavigator.Status.STALE,n.status);
        update(n,100,0,5,30);assertEquals(GpsNavigator.Status.REACQUIRE,n.status);assertEquals(s,n.progress,0);
        n.selectProgress(100);update(n,100,0,5,31);assertEquals(100,n.progress,5);
    }
    @Test public void arrivalRequiresEndpointProgressAndMultipleFreshFixes(){
        GpsNavigator n=new GpsNavigator(line());n.selectProgress(495);
        update(n,500,0,4,0);assertTrue(n.status!=GpsNavigator.Status.ARRIVED);update(n,500,0,4,1);update(n,500,0,4,2);assertEquals(GpsNavigator.Status.ARRIVED,n.status);
        GpsNavigator.Fix stale=fix(500,0,4,3);assertFalse(n.update(stale,stale.ns+6_000_000_000L));assertEquals(GpsNavigator.Status.STALE,n.status);
    }
    @Test public void interruptedJournalIgnoresTailAndPreservesGaps()throws Exception{
        File f=Files.createTempFile("gpsjournal",".txt").toFile();try{
            try(GpsTrackJournal j=new GpsTrackJournal(f)){j.append(fix(0,0,5,0),1_000_000_000L);j.append(fix(2,0,5,1),2_000_000_000L);j.append(fix(100,0,5,30),31_000_000_000L);j.append(fix(102,0,5,31),32_000_000_000L);}
            try(FileOutputStream out=new FileOutputStream(f,true)){out.write("P,48,7".getBytes(StandardCharsets.UTF_8));}
            List<GpsRoute> r=GpsTrackJournal.recover(f);assertEquals(2,r.size());assertEquals(2,r.get(0).length,.01);assertEquals(2,r.get(1).length,.01);
        }finally{f.delete();}
    }
    @Test public void rawPoorFixIsKeptWhileDerivedGpxHasExplicitGaps()throws Exception{
        File f=Files.createTempFile("gpsraw",".txt").toFile();try{
            try(GpsTrackJournal j=new GpsTrackJournal(f)){
                j.append(fix(0,0,5,0),1_000_000_000L);j.append(fix(2,0,5,1),2_000_000_000L);
                assertFalse(j.append(fix(4,0,100,2),3_000_000_000L));j.append(fix(6,0,5,3),4_000_000_000L);j.append(fix(8,0,5,4),5_000_000_000L);
            }
            String text=new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);assertTrue(text.contains(",100.0,"));assertEquals(2,GpsTrackJournal.recover(f).size());
        }finally{f.delete();}
    }
    @Test public void journalByteLimitIncludesBufferedRejectedFixes()throws Exception{
        File f=File.createTempFile("gps-limit",".journal");boolean limited=false;
        try{
            try(GpsTrackJournal journal=new GpsTrackJournal(f)){
                GpsNavigator.Fix bad=fix(0,0,100,1);
                for(int i=0;i<1000000;i++)try{journal.append(bad,bad.ns);}catch(IOException expected){limited=true;break;}
            }
            assertTrue(limited);assertTrue(f.length()<=GpxIO.MAX_BYTES);
        }finally{f.delete();}
    }
}
