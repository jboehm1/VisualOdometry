package dev.jboehm.vectorvio.gpxar;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import dev.jboehm.vectorvio.gps.GpsRoute;
import dev.jboehm.vectorvio.location.LocationSample;

public class ArRouteGuideTest {
    private static final double R=6371008.8,LAT=48,LON=7;
    private static final double E2=6.6943799901413165e-3,N=6378137/Math.sqrt(1-E2*Math.pow(Math.sin(Math.toRadians(LAT)),2)),M=6378137*(1-E2)/Math.pow(1-E2*Math.pow(Math.sin(Math.toRadians(LAT)),2),1.5);
    private static double lat(double n){return LAT+Math.toDegrees(n/M);}
    private static double lon(double e){return LON+Math.toDegrees(e/(N*Math.cos(Math.toRadians(LAT))));}
    static GpsRoute route(){return new GpsRoute("held-out bend",Arrays.asList(
        new GpsRoute.Point(lat(0),lon(0),300),new GpsRoute.Point(lat(40),lon(0),305),new GpsRoute.Point(lat(40),lon(40),Double.NaN)));}
    private static LocationSample fix(long ns,double e,double n,LocationSample.Source source,boolean mock){
        return new LocationSample(source,ns,ns+10_000_000,0,lat(n),lon(e),2,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,mock);
    }
    private static ArRouteGuide engine(){ArRouteGuide g=new ArRouteGuide();g.setRoute(route());g.setEnabled(true);return g;}
    /** World horizontal coordinates generated with a known gravity-preserving orientation. */
    private static double[] world(double e,double n,double theta){double c=Math.cos(theta),s=Math.sin(theta);return new double[]{c*e+s*n,-(-s*e+c*n)};}
    private static long feed(ArRouteGuide g,double theta,boolean reverse,double bias){
        long ns=1_000_000_000L;
        // Continuous 10 Hz poses, fixes at 1 Hz. Path changes direction: reflection cannot fit it.
        for(int i=0;i<=500;i++){
            double t=i/10.0,n=Math.min(30,t),e=Math.max(0,t-30)*(reverse?-1:1);
            double[] p=world(e,n,theta);g.onPose(1,ns,ns+20_000_000,p[0],p[1]);
            if(i>0&&i%10==0)g.onFix(fix(ns,e+bias,n,LocationSample.Source.GPS_PROVIDER,false));
            ns+=100_000_000;
        }
        return ns-100_000_000;
    }
    @Test public void curvedRoutesBothDirectionsUseCorrectHandedness(){
        for(boolean reverse:new boolean[]{false,true})for(double theta:new double[]{0,.73,-1.2}){
            ArRouteGuide g=engine();long now=feed(g,theta,reverse,0);ArRouteGuide.Snapshot s=g.snapshot(now);
            assertEquals(ArRouteGuide.State.CANDIDATE,s.state);assertTrue(s.residualMeters<.01);assertFalse(s.edges.isEmpty());
            for(double[] edge:s.edges){
                // Undo known orientation: points must remain on original north-then-east GPX.
                for(int i=0;i<4;i+=2){double e=Math.cos(theta)*edge[i]+Math.sin(theta)*edge[i+1];double n=Math.sin(theta)*edge[i]-Math.cos(theta)*edge[i+1];
                    assertTrue(Math.abs(e)<.02||Math.abs(n-40)<.02);
                }
            }
        }
    }
    @Test public void stationaryPureRotationCannotAcquireYaw(){
        ArRouteGuide g=engine();long ns=1_000_000_000;
        for(int i=0;i<100;i++,ns+=100_000_000){g.onPose(1,ns,ns,0,0);if(i%10==0)g.onFix(fix(ns,i%3,0,LocationSample.Source.GPS_PROVIDER,false));}
        assertEquals(ArRouteGuide.State.NEED_BASELINE,g.snapshot(ns).state);assertTrue(g.snapshot(ns).edges.isEmpty());
    }
    @Test public void commonBiasStaysUnobservableAndDoesNotClaimZeroUncertainty(){
        ArRouteGuide g=engine();long now=feed(g,.4,false,5);ArRouteGuide.Snapshot s=g.snapshot(now);
        assertEquals(ArRouteGuide.State.CANDIDATE,s.state);assertTrue(s.residualMeters<.01);
        assertFalse(s.absoluteBiasObservable);assertTrue(s.horizontalAllowanceMeters>=2);
    }
    @Test public void duplicateAndBadFixesDoNotIncreaseSamples(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);int before=g.snapshot(now).samples;
        assertFalse(g.onFix(fix(now,30,30,LocationSample.Source.GPS_PROVIDER,false)));
        assertFalse(g.onFix(fix(now+1,30,30,LocationSample.Source.GPS_PROVIDER,true)));
        assertEquals(before,g.snapshot(now).samples);
    }
    @Test public void staleFixHidesRouteInsteadOfFreezingConfidentLine(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);ArRouteGuide.Snapshot s=g.snapshot(now+ArRouteGuide.STALE_NS+1);
        assertEquals(ArRouteGuide.State.STALE,s.state);assertTrue(s.edges.isEmpty());
    }
    @Test public void trackingResetAndSegmentChangeRequireNewAlignment(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);g.onPose(2,now+100_000_000,now+100_000_000,0,0);
        assertTrue(g.snapshot(now+100_000_000).edges.isEmpty());assertEquals(0,g.snapshot(now+100_000_000).samples);
        g.trackingLost();assertEquals(ArRouteGuide.State.WAITING_POSES,g.snapshot(now).state);
    }
    @Test public void providerChangeDoesNotDoubleCountIndependentVotes(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);
        assertTrue(g.onFix(fix(now,30,30,LocationSample.Source.GOOGLE_FUSED,false)));
        assertEquals(1,g.snapshot(now).samples);assertTrue(g.snapshot(now).edges.isEmpty());
    }
    @Test public void clockMismatchAndInvalidPoseFailClosed(){
        ArRouteGuide g=engine();g.onPose(1,1,10_000_000_000L,0,0);
        assertEquals(ArRouteGuide.State.CLOCK_MISMATCH,g.snapshot(10_000_000_000L).state);
        g.onPose(1,10_000_000_000L,10_000_000_000L,Double.NaN,0);
        assertTrue(g.snapshot(10_000_000_000L).edges.isEmpty());
    }
    @Test public void futureFixWaitsForBracketingPose(){
        ArRouteGuide g=engine();long t=1_000_000_000L;g.onPose(1,t,t,0,0);
        assertTrue(g.onFix(fix(t+50_000_000,0,.5,LocationSample.Source.GPS_PROVIDER,false)));
        assertEquals(0,g.snapshot(t).samples);g.onPose(1,t+100_000_000,t+100_000_000,0,-1);
        assertEquals(1,g.snapshot(t+100_000_000).samples);
    }
    @Test public void routeWindowClipsSparseLongEdgesAndBoundsDenseWork(){
        RouteIndex idx=new RouteIndex(new GpsRoute("sparse",Arrays.asList(new GpsRoute.Point(lat(-100),LON,Double.NaN),new GpsRoute.Point(lat(100),LON,Double.NaN))));
        List<double[]> near=idx.near(LAT,LON,30,512);assertFalse(near.isEmpty());
        double span=0;for(double[] e:near)span+=Math.toRadians(e[2]-e[0])*M;assertEquals(60,span,.002);
        assertTrue(idx.near(lat(1000),lon(1000),30,512).isEmpty());
    }
    @Test public void disabledGuideDoesNotUseLiveFixes(){
        ArRouteGuide g=engine();g.setEnabled(false);assertFalse(g.onFix(fix(1_000_000_000,0,0,LocationSample.Source.GPS_PROVIDER,false)));
        assertEquals(ArRouteGuide.State.OFF,g.snapshot(1_000_000_000).state);
    }
    @Test public void navigatorIsReusedAndTargetDoesNotRequireInventedAltitude(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);ArRouteGuide.Snapshot s=g.snapshot(now);
        assertTrue(Double.isFinite(s.remainingMeters));assertTrue(Double.isFinite(s.offRouteMeters));
        assertFalse(s.verticalDatumKnown);
    }

    @Test public void datelineUsesShortArcAndInverseKeepsMetricCoordinates(){
        dev.jboehm.vectorvio.GeoAlignment a=new dev.jboehm.vectorvio.GeoAlignment();
        for(int i=0;i<10;i++){
            double e=i*10,n=(i%3)*3,longitude=((179.9998+Math.toDegrees(e/(N*Math.cos(Math.toRadians(LAT))))+540)%360)-180;
            assertTrue(a.add(1,1_000_000_000L+i*1_000_000_000L,e,n,lat(n),longitude,1));
        }
        dev.jboehm.vectorvio.GeoAlignment.Result fit=a.estimate();assertEquals(dev.jboehm.vectorvio.GeoAlignment.State.CANDIDATE,fit.state);
        double[] geo=fit.project(80,3),back=fit.unproject(geo[0],geo[1]);assertTrue(geo[1]<0);assertArrayEquals(new double[]{80,3},back,.0001);
    }

    @Test public void markersAreBoundedAndStaySeparateFromPositionMeasurements(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);ArRouteGuide.Snapshot s=g.snapshot(now);int samples=s.samples;
        assertTrue(s.markers.size()<=7);
        for(ArRouteGuide.Marker m:s.markers){assertTrue(Double.isFinite(m.x));assertTrue(Double.isFinite(m.z));assertFalse(m.label.isEmpty());}
        assertEquals(samples,g.snapshot(now).samples);
    }

    @Test public void gpsOnlyQuickViewAfterUnlockDoesNotFalsifyArAlignment(){
        ArRouteGuide guide=engine();long t=1_000_000_000L;
        assertTrue(guide.onFix(fix(t,0,0,LocationSample.Source.GPS_PROVIDER,false)));
        ArRouteGuide.Snapshot snap=guide.snapshot(t+10_000_000L);
        assertEquals(ArRouteGuide.State.WAITING_POSES,snap.state);
        assertEquals(0,snap.samples);
        assertTrue(snap.edges.isEmpty());
        assertTrue(Double.isFinite(snap.remainingMeters));
        assertTrue(Double.isNaN(snap.targetX));
        // Locked screen/tracking loss clears the transient nav & AR state.
        guide.trackingLost();
        ArRouteGuide.Snapshot resumed=guide.snapshot(t+20_000_000L);
        assertEquals(ArRouteGuide.State.WAITING_POSES,resumed.state);
        assertTrue(resumed.edges.isEmpty());
    }

    @Test public void gnssOutageRetainsAnchoredRouteOnlyWhileVioTrackingIsFreshAndBounded(){
        ArRouteGuide g=engine();long now=feed(g,0,false,0);
        ArRouteGuide.Snapshot initial=g.snapshot(now);
        assertEquals(ArRouteGuide.State.CANDIDATE,initial.state);
        long t=now;
        for(int i=1;i<=80;i++){
            t+=100_000_000L;
            g.onPose(1,t,t,20,0); // continuous VIO poses, but no GNSS fixes
        }
        ArRouteGuide.Snapshot hold=g.snapshot(t);
        assertEquals(ArRouteGuide.State.VIO_HOLD,hold.state);
        assertTrue(hold.holdSeconds>=7);
        assertTrue(hold.horizontalAllowanceMeters>initial.horizontalAllowanceMeters);
        assertFalse(hold.edges.isEmpty());
        assertTrue(Double.isNaN(hold.targetX)); // no stale GPS heading arrow
        for(int i=0;i<250;i++){t+=100_000_000L;g.onPose(1,t,t,20,0);}
        ArRouteGuide.Snapshot expired=g.snapshot(t);
        assertEquals(ArRouteGuide.State.STALE,expired.state);
        assertTrue(expired.edges.isEmpty());
        g.trackingLost();
        assertTrue(g.snapshot(t).edges.isEmpty());
    }

}
