package dev.jboehm.vectorvio.gps;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class GpsEngineEdgeTest {
    private GpsRoute.Point p(double x,double y){return new GpsRoute.Point(Math.toDegrees(y/Geo.R),Math.toDegrees(x/Geo.R),Double.NaN);}
    private GpsNavigator.Fix fix(double x,double y,double accuracy,int t){GpsRoute.Point p=p(x,y);return new GpsNavigator.Fix(p.lat,p.lon,accuracy,2,Double.NaN,Double.NaN,(t+1)*1_000_000_000L);}
    private void push(GpsNavigator n,double x,double y,double a,int t){GpsNavigator.Fix f=fix(x,y,a,t);n.update(f,f.ns);}
    private GpsNavigator nav(){return new GpsNavigator(new GpsRoute("route",Arrays.asList(p(0,0),p(500,0))));}
    @Test public void futureInvalidAndNegativeAccuracyAreNeverProgress(){
        GpsNavigator n=nav();push(n,0,0,5,0);double s=n.progress;
        GpsNavigator.Fix future=fix(200,0,5,1);assertFalse(n.update(future,future.ns-1));assertEquals(s,n.progress,0);
        push(n,200,0,-1,2);assertEquals(s,n.progress,0);
    }
    @Test public void longGapRecoversOnlyAfterSeveralClearFixesWithoutInventedInterimProgress(){
        GpsNavigator n=nav();push(n,0,0,5,0);push(n,2,0,5,1);double before=n.progress;
        push(n,80,0,5,30);assertEquals(GpsNavigator.Status.REACQUIRE,n.status);assertEquals(before,n.progress,0);
        push(n,82,0,5,31);assertEquals(before,n.progress,0);
        push(n,84,0,5,32);assertEquals(GpsNavigator.Status.FOLLOWING,n.status);assertEquals(84,n.progress,5);
    }
    @Test public void poorAccuracyDoesNotStayFreshForever(){
        GpsNavigator n=nav();push(n,0,0,100,0);n.tick(2_000_000_000L);assertEquals(GpsNavigator.Status.POOR_ACCURACY,n.status);
        n.tick(20_000_000_000L);assertEquals(GpsNavigator.Status.STALE,n.status);
    }
    @Test public void reverseRouteAndDistanceAreConsistent(){
        GpsRoute r=nav().route;GpsRoute reverse=r.reverse();assertEquals(r.length,reverse.length,1e-6);assertEquals(r.points.get(1).lon,reverse.points.get(0).lon,0);
        GpsNavigator n=new GpsNavigator(reverse);push(n,500,0,5,0);for(int i=1;i<50;i++)push(n,500-2*i,0,5,i);
        assertEquals(98,n.progress,10);
    }
    @Test public void oneOffRouteFixDoesNotTriggerAnAlertAndOneRejectedJumpDoesNotArrive(){
        GpsNavigator n=nav();push(n,0,0,5,0);push(n,2,50,5,1);assertTrue(n.status!=GpsNavigator.Status.OFF_ROUTE);
        push(n,4,0,5,2);push(n,10000,0,5,3);assertEquals(GpsNavigator.Status.OUTLIER,n.status);assertTrue(n.progress<30);
        push(n,6,0,5,4);assertEquals(GpsNavigator.Status.FOLLOWING,n.status);
    }
    @Test public void invalidSavedProgressCannotPoisonTheRouteState(){
        GpsNavigator n=nav();try{n.selectProgress(Double.NaN);fail();}catch(IllegalArgumentException expected){}
        push(n,0,0,5,0);assertTrue(Double.isFinite(n.progress));
    }
}
