package dev.jboehm.vectorvio;

import android.opengl.Matrix;
import com.google.ar.core.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** GL-thread-owned local map. Anchors follow ARCore map corrections; no persistent map. */
final class SceneMapper {
    private static final float[] EMPTY=new float[0];
    static final class Shape {
        final float[] xy,world; final int kind; final String label;
        Shape(float[] xy,int kind,String label){this(xy,kind,label,null);}
        Shape(float[] xy,int kind,String label,float[] world){this.xy=xy;this.kind=kind;this.label=label;this.world=world;}
    }
    static final class Snapshot {
        final List<Shape> shapes;
        final List<MapPoint> mapPoints;
        final List<MapSurface> surfaces;
        final List<float[]> path;
        final List<MapArchive.Poi> pois;
        final int horizontal,vertical,points,anchors,segment;
        final float confidence;
        float heading,pitch,targetMeters=-1,gpxDirectionRadians=Float.NaN;
        float gpxReturnBearing=Float.NaN,gpxReturnMeters=Float.NaN;
        int gpxObservedPieces,gpxEstimatedPieces;
        float cameraX,cameraZ;
        boolean groundEstimated,groundMissing,cameraPoseValid;
        int groundPoints;
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q){this(shapes,h,v,p,a,q,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),0);}
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q,List<MapPoint> mapPoints,List<MapSurface> surfaces,List<float[]> path,List<MapArchive.Poi> pois){this(shapes,h,v,p,a,q,mapPoints,surfaces,path,pois,0);}
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q,List<MapPoint> mapPoints,List<MapSurface> surfaces,List<float[]> path,List<MapArchive.Poi> pois,int segment) {
            this.shapes=shapes;horizontal=h;vertical=v;points=p;anchors=a;confidence=q;
            this.mapPoints=mapPoints;this.surfaces=surfaces;this.path=path;this.pois=pois;this.segment=segment;
        }
    }
    static final class MapPoint {final float x,y,z,confidence;MapPoint(float x,float y,float z,float q){this.x=x;this.y=y;this.z=z;confidence=q;}}
    static final class MapSurface {final float[] xz;final boolean vertical;MapSurface(float[] xz,boolean vertical){this.xz=xz;this.vertical=vertical;}}
    dev.jboehm.vectorvio.gpxar.ArRouteGuide.Snapshot gpxRoute;
    int gpxDisplayFlags=dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.ALL;
    boolean gpxMode;
    boolean gpxDepthOcclusion;
    Pose gpxWorldFromMetric;
    private final GpxDepthOcclusion depthOcclusion=new GpxDepthOcclusion();
    private final GpxRibbonAnchors gpxAnchors=new GpxRibbonAnchors();
    private final GpxWorldRibbon gpxRibbon=new GpxWorldRibbon();
    boolean gpxEstimatedGround=true;
    volatile int gpxGroundEdges,gpxAnchorCount;
    boolean worldRendering;
    boolean allowEstimatedGround;
    private final ArrayList<Anchor> trail=new ArrayList<>();
    private final LinkedHashMap<Long,MapPoint> mapPoints=new LinkedHashMap<>();
    private final float[] view=new float[16],projection=new float[16],vp=new float[16];
    void clear() { for(Anchor a:trail) a.detach(); trail.clear();mapPoints.clear();gpxAnchors.clear();gpxRibbon.reset();depthOcclusion.reset();gpxAnchorCount=0; }
    Snapshot update(Session session,Frame frame,Odometer.Result result,int width,int height,int segment,List<MapArchive.Poi> pois,float pointConfidence,List<Odometer.Point> history) {
        Camera camera=frame.getCamera();
        if(camera.getTrackingState()!=TrackingState.TRACKING) {
            clear();return new Snapshot(new ArrayList<>(),0,0,0,0,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),segment);
        }
        if(result==Odometer.Result.REFERENCE || result==Odometer.Result.GAP || result==Odometer.Result.REJECTED) clear();
        camera.getViewMatrix(view,0);camera.getProjectionMatrix(projection,0,.05f,100f);
        Matrix.multiplyMM(vp,0,projection,0,view,0);
        ArrayList<Shape> shapes=new ArrayList<>();ArrayList<MapSurface> surfaces=new ArrayList<>();
        List<GroundTrail.Floor> floors=new ArrayList<>();
        int h=0,v=0;
        java.util.Collection<Plane> planes=session.getAllTrackables(Plane.class);
        for(Plane plane:planes) {
            if(plane.getTrackingState()!=TrackingState.TRACKING || plane.getSubsumedBy()!=null) continue;
            boolean vertical=plane.getType()==Plane.Type.VERTICAL;
            if(vertical)v++;else h++;
            if(shapes.size()>=24)continue;
            FloatBuffer polygon=plane.getPolygon();
            if(polygon.remaining()<6)continue;
            float[] xyz=new float[polygon.remaining()/2*3];
            Pose center=plane.getCenterPose();
            for(int i=0;i<xyz.length;i+=3) {
                float[] world=center.transformPoint(new float[]{polygon.get(),0,polygon.get()});
                System.arraycopy(world,0,xyz,i,3);
            }
            float[] xy=worldRendering?EMPTY:Projection.polygon(vp,xyz);
            float[] xz=new float[xyz.length/3*2];for(int i=0,j=0;i<xyz.length;i+=3){xz[j++]=xyz[i];xz[j++]=xyz[i+2];}
            if(surfaces.size()<24)surfaces.add(new MapSurface(xz,vertical));
            if(plane.getType()==Plane.Type.HORIZONTAL_UPWARD_FACING){
                float[] normal=center.getYAxis();
                floors.add(new GroundTrail.Floor(center.tx(),center.ty(),center.tz(),normal[0],normal[1],normal[2],xz));
            }
            if(worldRendering||xy.length>=6)shapes.add(new Shape(xy,vertical?1:0,"",worldRendering?xyz:null));
        }
        int points=0;float confidence=0;
        try(PointCloud cloud=frame.acquirePointCloud()) {
            FloatBuffer data=cloud.getPoints();points=data.remaining()/4;
            int stride=Math.max(1,(points+239)/240);
            for(int i=0;i<points;i++) {
                float x=data.get(),y=data.get(),z=data.get(),q=data.get();confidence+=q;
                if(i%stride!=0 || !Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(q)||q<pointConfidence)continue;
                float grid=.14f;int gx=(int)Math.floor(x/grid),gz=(int)Math.floor(z/grid);
                long cell=((long)gx<<32)^(gz&0xffffffffL);
                if(!mapPoints.containsKey(cell)) {
                    if(mapPoints.size()>=2400)mapPoints.remove(mapPoints.keySet().iterator().next());
                    mapPoints.put(cell,new MapPoint(x,y,z,q));
                }
                float[] xy=worldRendering?EMPTY:Projection.point(vp,x,y,z);
                if(xy!=null)shapes.add(new Shape(xy,2,"",worldRendering?new float[]{x,y,z}:null));
            }
        }
        // Project the recorded world path. No per-breadcrumb native anchors.
        // Breaks remain explicit; no fabricated motion through tracking outages.
        ArrayList<float[]> path=new ArrayList<>();
        Odometer.Point previous=null;float[] previousGround=null;boolean groundEstimated=false,groundMissing=false;int groundPoints=0;
        for(Odometer.Point point:history) {
            float ground=GroundTrail.floorY(point,floors);
            path.add(new float[]{(float)point.x,(float)point.y,(float)point.z});
            if(!Float.isFinite(ground)){
                groundMissing=true;
                if(!allowEstimatedGround){previous=null;previousGround=null;continue;}
                ground=(float)point.y-1.5f;groundEstimated=true;
            }else groundPoints++;
            float[] xyz={(float)point.x,ground+.025f,(float)point.z};
            if(previous!=null && previous.segment==point.segment) {
                float[] line=worldRendering?EMPTY:Projection.line(vp,previousGround,xyz);
                if(line!=null)shapes.add(new Shape(line,3,"",worldRendering?new float[]{previousGround[0],previousGround[1],previousGround[2],xyz[0],xyz[1],xyz[2]}:null));
            }
            previous=point;previousGround=xyz;
        }
        gpxGroundEdges=0;
        List<double[]> worldEdges=new ArrayList<>();
        if(gpxRoute!=null&&gpxWorldFromMetric!=null)for(double[] edge:gpxRoute.edges){
            float[] a=gpxWorldFromMetric.transformPoint(new float[]{(float)edge[0],0,(float)edge[1]}),b=gpxWorldFromMetric.transformPoint(new float[]{(float)edge[2],0,(float)edge[3]});
            worldEdges.add(new double[]{a[0],a[2],b[0],b[2]});
        }
        GpxWorldRibbon.Result ribbons=gpxRibbon.project(worldEdges,camera.getPose().tx(),camera.getPose().ty(),
            camera.getPose().tz(),floors,gpxRoute==null?0:gpxRoute.segment,gpxMode&&gpxEstimatedGround);
        List<float[]> anchored=gpxAnchors.update(session,gpxWorldFromMetric,ribbons.observed,gpxRoute==null?0:gpxRoute.segment,
            gpxRoute==null?0:gpxRoute.alignmentRevision,android.os.SystemClock.elapsedRealtimeNanos(),camera.getPose().tx(),camera.getPose().tz());
        gpxAnchorCount=gpxAnchors.size();
        // Depth never decides terrain height: it may only hide a segment
        // definitely behind a confidently measured foreground surface.
        List<float[]> visible=gpxDepthOcclusion
            ?depthOcclusion.apply(frame,camera,vp,anchored,android.os.SystemClock.elapsedRealtimeNanos())
            :anchored;
        if(!gpxDepthOcclusion)depthOcclusion.reset();
        for(float[] edge:visible){
            float[] xy=worldRendering?EMPTY:Projection.line(vp,new float[]{edge[0],edge[1],edge[2]},new float[]{edge[3],edge[4],edge[5]});
            if(xy!=null)shapes.add(new Shape(xy,5,"",worldRendering?edge:null));
            gpxGroundEdges++;
        }
        // Kind 7 is always a transparent, dashed, hypothetical flat plane.
        // It is NOT measured terrain, is never used for ARCore/GNSS alignment.
        for(float[] edge:ribbons.estimated){
            float[] xy=worldRendering?EMPTY:Projection.line(vp,new float[]{edge[0],edge[1],edge[2]},new float[]{edge[3],edge[4],edge[5]});
            if(xy!=null)shapes.add(new Shape(xy,7,"APPROX FLOOR",worldRendering?edge:null));
        }
        if((gpxGroundEdges>0||!ribbons.estimated.isEmpty())&&gpxRoute!=null&&gpxWorldFromMetric!=null){
            for(dev.jboehm.vectorvio.gpxar.ArRouteGuide.Marker marker:gpxRoute.markers){
                int flag=marker.endpoint?dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.ENDPOINTS:dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.DISTANCES;
                if((gpxDisplayFlags&flag)==0)continue;
                float[] pos=gpxWorldFromMetric.transformPoint(new float[]{(float)marker.x,0,(float)marker.z});
                float floor=GroundTrail.floorY(pos[0],camera.getPose().ty(),pos[2],floors);if(!Float.isFinite(floor))continue;
                float[] xy=Projection.point(vp,pos[0],floor+.15f,pos[2]);
                if(xy!=null)shapes.add(new Shape(xy,6,marker.label));
            }
        }
        ArrayList<MapArchive.Poi> currentPois=new ArrayList<>();
        for(MapArchive.Poi poi:pois) {
            if(poi.segment!=segment)continue;
            currentPois.add(poi);
            if(gpxMode&&(gpxDisplayFlags&dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.LOCAL_POIS)==0)continue;
            float[] xyz={poi.pose[0],poi.pose[1],poi.pose[2]};float[] xy=Projection.point(vp,xyz[0],xyz[1],xyz[2]);
            if(xy!=null){double distance=Math.sqrt(Math.pow(xyz[0]-camera.getPose().tx(),2)+Math.pow(xyz[1]-camera.getPose().ty(),2)+Math.pow(xyz[2]-camera.getPose().tz(),2));
                shapes.add(new Shape(xy,4,(gpxMode?String.format(java.util.Locale.US,"%.1f m · ",distance):"")+poi.type+" · "+poi.label));}
        }
        Snapshot snapshot=new Snapshot(shapes,h,v,points,history.size(),points==0?0:confidence/points,
            new ArrayList<>(mapPoints.values()),surfaces,path,currentPois,segment);
        snapshot.groundEstimated=groundEstimated;
        snapshot.groundMissing=groundMissing;snapshot.groundPoints=groundPoints;
        snapshot.cameraPoseValid=true;
        snapshot.cameraX=camera.getPose().tx();snapshot.cameraZ=camera.getPose().tz();
        snapshot.gpxObservedPieces=gpxGroundEdges;
        snapshot.gpxEstimatedPieces=ribbons.estimated.size();
        if(gpxRoute!=null&&Double.isFinite(gpxRoute.nearestX)&&Double.isFinite(gpxRoute.nearestZ)&&gpxWorldFromMetric!=null){
            float[] closest=gpxWorldFromMetric.transformPoint(new float[]{(float)gpxRoute.nearestX,0,(float)gpxRoute.nearestZ});
            float dx=closest[0]-snapshot.cameraX,dz=closest[2]-snapshot.cameraZ;
            float[] right=camera.getDisplayOrientedPose().getXAxis(),back=camera.getDisplayOrientedPose().getZAxis();
            snapshot.gpxReturnMeters=(float)Math.hypot(dx,dz);
            if(snapshot.gpxReturnMeters>.5f)
                snapshot.gpxReturnBearing=(float)Math.atan2(dx*right[0]+dz*right[2],-(dx*back[0]+dz*back[2]));
        }
        if((gpxDisplayFlags&dev.jboehm.vectorvio.gpxar.RouteDisplayOptions.DIRECTION)!=0&&gpxRoute!=null&&gpxWorldFromMetric!=null&&Double.isFinite(gpxRoute.targetX)&&Double.isFinite(gpxRoute.targetZ)){
            float[] target=gpxWorldFromMetric.transformPoint(new float[]{(float)gpxRoute.targetX,0,(float)gpxRoute.targetZ});
            double dx=target[0]-snapshot.cameraX,dz=target[2]-snapshot.cameraZ;
            float[] direction=camera.getDisplayOrientedPose().getZAxis();
            if(Math.hypot(dx,dz)>.5&&Math.hypot(direction[0],direction[2])>.2){
                double angle=Math.atan2(dx,-dz)-Math.atan2(-direction[0],direction[2]);
                snapshot.gpxDirectionRadians=(float)Math.atan2(Math.sin(angle),Math.cos(angle));
            }
        }
        float[] axis=camera.getDisplayOrientedPose().getZAxis();
        snapshot.heading=(float)Math.atan2(-axis[0],-axis[2]);
        snapshot.pitch=(float)Math.asin(Math.max(-1,Math.min(1,-axis[1])));
        for(HitResult hit:frame.hitTest(width*.5f,height*.41f)) {
            if(hit.getTrackable() instanceof Plane && ((Plane)hit.getTrackable()).isPoseInPolygon(hit.getHitPose())) {
                snapshot.targetMeters=hit.getDistance();break;
            }
        }
        return snapshot;
    }
    private float distance(Pose a,Pose b) {
        float x=a.tx()-b.tx(),y=a.ty()-b.ty(),z=a.tz()-b.tz();return (float)Math.sqrt(x*x+y*y+z*z);
    }
}
