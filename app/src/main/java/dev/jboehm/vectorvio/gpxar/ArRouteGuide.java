package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.GeoAlignment;
import dev.jboehm.vectorvio.geo.PoseFixSynchronizer;
import dev.jboehm.vectorvio.gps.GpsRoute;
import dev.jboehm.vectorvio.gps.GpsNavigator;
import dev.jboehm.vectorvio.location.LocationSample;
import java.util.*;

/** Pure-Java experimental placement. Never writes poses, distance, GPX or raw fixes. */
public final class ArRouteGuide {
    public static final long STALE_NS=5_000_000_000L;
    public enum State { OFF, NO_ROUTE, WAITING_POSES, CLOCK_MISMATCH, WAITING_FIXES, NEED_BASELINE, INCONSISTENT, STALE, CANDIDATE }
    public static final class Marker {
        public final double x,z;public final String label;public final boolean endpoint;
        Marker(double x,double z,String label,boolean endpoint){this.x=x;this.z=z;this.label=label;this.endpoint=endpoint;}
    }
    public static final class Snapshot {
        public final State state;
        public final String routeName;
        public final int samples,segment;
        public long alignmentRevision;
        public List<Marker> markers=Collections.emptyList();
        public final double residualMeters,horizontalAllowanceMeters,headingAllowanceRadians;
        public final boolean absoluteBiasObservable=false,verticalDatumKnown=false;
        /** AR x/z edge endpoints, clipped near the camera. No terrain heights invented. */
        public final List<double[]> edges;
        public final GpsNavigator.Status navigationStatus;
        public final double remainingMeters,offRouteMeters,targetX,targetZ,latitude,longitude;
        Snapshot(State state,String name,int segment,GeoAlignment.Result fit,List<double[]> edges,GpsNavigator navigator,double[] target,double x,double z){
            this.state=state;routeName=name;this.segment=segment;this.edges=Collections.unmodifiableList(edges);
            double[] geo=state==State.CANDIDATE&&fit!=null?fit.project(x,-z):null;
            latitude=geo==null?Double.NaN:geo[0];longitude=geo==null?Double.NaN:geo[1];
            navigationStatus=navigator==null?GpsNavigator.Status.SEARCHING:navigator.status;
            remainingMeters=navigator==null?Double.NaN:Math.max(0,navigator.route.length-navigator.progress);
            offRouteMeters=navigator==null?Double.NaN:navigator.offDistance;
            targetX=target==null?Double.NaN:target[0];targetZ=target==null?Double.NaN:-target[1];
            samples=fit==null?0:fit.samples;residualMeters=fit==null?Double.NaN:fit.residualRmsMeters;
            // Diagnostic allowances, NOT calibrated confidence intervals. No sqrt(sample count).
            horizontalAllowanceMeters=fit==null?Double.NaN:Math.max(fit.typicalGpsAccuracyMeters,fit.residualRmsMeters);
            headingAllowanceRadians=fit==null||fit.baselineMeters<=0?Double.NaN:
                Math.atan2(2*horizontalAllowanceMeters,fit.baselineMeters);
        }
    }
    private final GeoAlignment alignment=new GeoAlignment();
    private final PoseFixSynchronizer poses=new PoseFixSynchronizer();
    private final ArrayDeque<LocationSample> pending=new ArrayDeque<>();
    private GpsRoute route;
    private RouteIndex index;
    private GpsNavigator navigator;
    private GeoAlignment.Result fit;
    private int segment;
    private long lastPoseNs,lastFixNs,lastSeenFixNs,alignmentRevision;
    private LocationSample.Source source;
    private State state=State.OFF;
    private boolean enabled;
    private double x,z;
    public synchronized void setRoute(GpsRoute route){
        RouteIndex next=route==null?null:new RouteIndex(route);
        this.route=route;index=next;resetTracking();
    }
    public synchronized void setEnabled(boolean value){enabled=value;resetTracking();}
    public synchronized boolean hasRoute(){return route!=null;}
    public synchronized void resetTracking(){
        alignment.reset();poses.reset();pending.clear();fit=null;segment=0;
        navigator=route==null?null:new GpsNavigator(route);if(navigator!=null)navigator.joinNearest();
        lastPoseNs=lastFixNs=lastSeenFixNs=0;source=null;
        state=!enabled?State.OFF:route==null?State.NO_ROUTE:State.WAITING_POSES;
    }
    /** Call on AR discontinuity, pause/reset and tracking loss. Keeps the imported route. */
    public synchronized void trackingLost(){
        if(lastPoseNs==0&&lastFixNs==0&&pending.isEmpty()&&state!=State.CLOCK_MISMATCH)return;
        resetTracking();
    }
    public synchronized void onPose(int segment,long frameNs,long nowNs,double x,double z){
        if(!enabled||route==null)return;
        if(frameNs<=0||nowNs<=0||Math.abs((double)frameNs-nowNs)>2_000_000_000d){
            resetTracking();state=State.CLOCK_MISMATCH;return;
        }
        if(segment<=0||!Double.isFinite(x)||!Double.isFinite(z)){trackingLost();return;}
        if(this.segment!=segment||(lastPoseNs>0&&frameNs-lastPoseNs>700_000_000L))resetTracking();
        if(frameNs<=lastPoseNs)return;
        this.segment=segment;this.x=x;this.z=z;lastPoseNs=frameNs;
        poses.push(segment,frameNs,x,z);
        if(state==State.WAITING_POSES||state==State.CLOCK_MISMATCH)state=State.WAITING_FIXES;
        drain(nowNs);
    }
    /** Raw logging happens upstream even for rejected samples. Limits are pairing, not motion filters. */
    public synchronized boolean onFix(LocationSample fix){
        if(!enabled||route==null||fix==null||fix.source==null||fix.mock||fix.fixElapsedNs<=0||fix.receiveElapsedNs<=0||
           !Double.isFinite(fix.latitude)||Math.abs(fix.latitude)>85||!Double.isFinite(fix.longitude)||Math.abs(fix.longitude)>180||
           !Double.isFinite(fix.accuracyMeters)||fix.accuracyMeters<=0||fix.accuracyMeters>50||
           (double)fix.receiveElapsedNs-fix.fixElapsedNs>STALE_NS||(double)fix.receiveElapsedNs-fix.fixElapsedNs< -500_000_000)return false;
        if(source!=null&&source!=fix.source){
            alignment.reset();pending.clear();fit=null;lastFixNs=lastSeenFixNs=0;state=State.WAITING_FIXES;
            navigator=new GpsNavigator(route);navigator.joinNearest();
        }
        source=fix.source;
        if(fix.fixElapsedNs<=lastSeenFixNs)return false;
        lastSeenFixNs=fix.fixElapsedNs;
        if(pending.size()==8)pending.removeFirst();pending.addLast(fix);
        drain(fix.receiveElapsedNs);return true;
    }
    private void drain(long nowNs){
        while(!pending.isEmpty()){
            LocationSample f=pending.peekFirst();
            if(f.fixElapsedNs>lastPoseNs)break;
            pending.removeFirst();
            if(nowNs-f.fixElapsedNs>STALE_NS)continue;
            PoseFixSynchronizer.Pose p=poses.interpolate(f.fixElapsedNs);
            if(p==null)continue;
            // ARCore x/y(up)/z(back) -> right-handed horizontal x/-z before EN rotation.
            if(alignment.add(p.segment,f.fixElapsedNs,p.x,-p.z,f.latitude,f.longitude,f.accuracyMeters)){
                lastFixNs=f.fixElapsedNs;fit=alignment.estimate();alignmentRevision++;
                navigator.update(new GpsNavigator.Fix(f.latitude,f.longitude,f.accuracyMeters,f.speedMps,f.bearingDegrees,f.altitudeMeters,f.fixElapsedNs),nowNs);
                switch(fit.state){
                    case CANDIDATE:state=State.CANDIDATE;break;
                    case NEED_BASELINE:state=State.NEED_BASELINE;break;
                    case INCONSISTENT:state=State.INCONSISTENT;break;
                    default:state=State.WAITING_FIXES;
                }
            }
        }
    }
    public synchronized Snapshot snapshot(long nowNs){
        State shown=state;
        if(shown==State.CANDIDATE&&(nowNs<lastFixNs||nowNs-lastFixNs>STALE_NS||nowNs-lastPoseNs>700_000_000L))shown=State.STALE;
        ArrayList<double[]> edges=new ArrayList<>();
        if(navigator!=null)navigator.tick(nowNs);
        double[] target=null;
        if(shown==State.CANDIDATE&&fit!=null){
            if(navigator.status==GpsNavigator.Status.FOLLOWING||navigator.status==GpsNavigator.Status.OFF_ROUTE||navigator.status==GpsNavigator.Status.ARRIVED){
                GpsRoute.Point next=route.at(Math.min(route.length,navigator.progress+(navigator.status==GpsNavigator.Status.OFF_ROUTE?0:10)));
                target=fit.unproject(next.lat,next.lon);
            }
            double[] geo=fit.project(x,-z);
            if(geo!=null)for(double[] edge:index.near(geo[0],geo[1],30,512)){
                double[] a=fit.unproject(edge[0],edge[1]),b=fit.unproject(edge[2],edge[3]);
                if(a!=null&&b!=null)edges.add(new double[]{a[0],-a[1],b[0],-b[1]});
            }
        }
        Snapshot snapshot=new Snapshot(shown,route==null?"":route.name,segment,fit,edges,navigator,target,x,z);snapshot.alignmentRevision=alignmentRevision;
        if(shown==State.CANDIDATE&&fit!=null&&target!=null){
            List<Marker> markers=new ArrayList<>();
            double progress=navigator.progress;
            for(double at=Math.max(25,Math.floor(progress/25)*25);at<=Math.min(route.length,progress+60)&&markers.size()<5;at+=25){
                if(at>=route.length-1)continue;addMarker(markers,at,String.format(Locale.US,"%.0f m · %.0f m left",at,route.length-at),false);
            }
            addMarker(markers,0,"START",true);addMarker(markers,route.length,"FINISH",true);
            snapshot.markers=Collections.unmodifiableList(markers);
        }
        return snapshot;
    }
    private void addMarker(List<Marker> markers,double progress,String text,boolean endpoint){
        GpsRoute.Point p=route.at(progress);double[] q=fit.unproject(p.lat,p.lon);
        if(q!=null&&Math.hypot(q[0]-x,-q[1]-z)<=30)markers.add(new Marker(q[0],-q[1],text,endpoint));
    }

}
