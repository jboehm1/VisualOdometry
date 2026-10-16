package dev.jboehm.vectorvio;

import android.opengl.Matrix;
import com.google.ar.core.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** GL-thread-owned local map. Anchors follow ARCore map corrections; no persistent map. */
final class SceneMapper {
    static final class Shape {
        final float[] xy; final int kind; final String label;
        Shape(float[] xy,int kind,String label){this.xy=xy;this.kind=kind;this.label=label;}
    }
    static final class Snapshot {
        final List<Shape> shapes;
        final List<MapPoint> mapPoints;
        final List<MapSurface> surfaces;
        final List<float[]> path;
        final List<MapArchive.Poi> pois;
        final int horizontal,vertical,points,anchors,segment;
        final float confidence;
        float heading,pitch,targetMeters=-1;
        float cameraX,cameraZ;
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q){this(shapes,h,v,p,a,q,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),0);}
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q,List<MapPoint> mapPoints,List<MapSurface> surfaces,List<float[]> path,List<MapArchive.Poi> pois){this(shapes,h,v,p,a,q,mapPoints,surfaces,path,pois,0);}
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q,List<MapPoint> mapPoints,List<MapSurface> surfaces,List<float[]> path,List<MapArchive.Poi> pois,int segment) {
            this.shapes=shapes;horizontal=h;vertical=v;points=p;anchors=a;confidence=q;
            this.mapPoints=mapPoints;this.surfaces=surfaces;this.path=path;this.pois=pois;this.segment=segment;
        }
    }
    static final class MapPoint {final float x,y,z,confidence;MapPoint(float x,float y,float z,float q){this.x=x;this.y=y;this.z=z;confidence=q;}}
    static final class MapSurface {final float[] xz;final boolean vertical;MapSurface(float[] xz,boolean vertical){this.xz=xz;this.vertical=vertical;}}
    private final ArrayList<Anchor> trail=new ArrayList<>();
    private final LinkedHashMap<Long,MapPoint> mapPoints=new LinkedHashMap<>();
    private final float[] view=new float[16],projection=new float[16],vp=new float[16];
    void clear() { for(Anchor a:trail) a.detach(); trail.clear();mapPoints.clear(); }
    Snapshot update(Session session,Frame frame,Odometer.Result result,int width,int height,int segment,List<MapArchive.Poi> pois,float pointConfidence) {
        Camera camera=frame.getCamera();
        if(camera.getTrackingState()!=TrackingState.TRACKING) {
            clear();return new Snapshot(new ArrayList<>(),0,0,0,0,0,new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),new ArrayList<>(),segment);
        }
        if(result==Odometer.Result.REFERENCE || result==Odometer.Result.GAP || result==Odometer.Result.REJECTED) clear();
        if(result==Odometer.Result.REFERENCE || result==Odometer.Result.ACCEPTED) {
            Pose pose=camera.getPose();
            Pose previous=trail.isEmpty()?null:trail.get(trail.size()-1).getPose();
            float distance=previous==null?1:distance(previous,pose);
            if(distance>=.4f) {
                // Bound native resources; a trail is camera-height, never invented floor geometry.
                if(trail.size()>=96) trail.remove(0).detach();
                trail.add(session.createAnchor(pose));
            }
        }
        camera.getViewMatrix(view,0);camera.getProjectionMatrix(projection,0,.05f,100f);
        Matrix.multiplyMM(vp,0,projection,0,view,0);
        ArrayList<Shape> shapes=new ArrayList<>();ArrayList<MapSurface> surfaces=new ArrayList<>();
        int h=0,v=0;
        for(Plane plane:session.getAllTrackables(Plane.class)) {
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
            float[] xy=Projection.polygon(vp,xyz);
            float[] xz=new float[xyz.length/3*2];for(int i=0,j=0;i<xyz.length;i+=3){xz[j++]=xyz[i];xz[j++]=xyz[i+2];}
            if(surfaces.size()<24)surfaces.add(new MapSurface(xz,vertical));
            if(xy.length>=6)shapes.add(new Shape(xy,vertical?1:0,String.format(java.util.Locale.US,
                "%s  %.1f × %.1f m",vertical?"VERTICAL":"HORIZONTAL",plane.getExtentX(),plane.getExtentZ())));
        }
        int points=0;float confidence=0;
        try(PointCloud cloud=frame.acquirePointCloud()) {
            FloatBuffer data=cloud.getPoints();points=data.remaining()/4;
            int stride=Math.max(1,(points+239)/240);
            for(int i=0;i<points;i++) {
                float x=data.get(),y=data.get(),z=data.get(),q=data.get();confidence+=q;
                if(i%stride!=0 || q<pointConfidence)continue;
                float grid=.14f;int gx=(int)Math.floor(x/grid),gz=(int)Math.floor(z/grid);
                long cell=((long)gx<<32)^(gz&0xffffffffL);
                if(!mapPoints.containsKey(cell)) {
                    if(mapPoints.size()>=2400)mapPoints.remove(mapPoints.keySet().iterator().next());
                    mapPoints.put(cell,new MapPoint(x,y,z,q));
                }
                float[] xy=Projection.point(vp,x,y,z);
                if(xy!=null)shapes.add(new Shape(xy,2,""));
            }
        }
        float[] previous=null;
        for(Anchor anchor:trail) {
            if(anchor.getTrackingState()!=TrackingState.TRACKING){previous=null;continue;}
            Pose p=anchor.getPose();
            float[] xyz=p.getTranslation();
            if(previous!=null) {
                float[] line=Projection.line(vp,previous,xyz);
                if(line!=null)shapes.add(new Shape(line,3,""));
            }
            previous=xyz;
        }
        ArrayList<float[]> path=new ArrayList<>();
        for(Anchor anchor:trail)if(anchor.getTrackingState()==TrackingState.TRACKING)path.add(anchor.getPose().getTranslation());
        ArrayList<MapArchive.Poi> currentPois=new ArrayList<>();
        for(MapArchive.Poi poi:pois) {
            if(poi.segment!=segment)continue;
            currentPois.add(poi);
            float[] xyz={poi.pose[0],poi.pose[1],poi.pose[2]};float[] xy=Projection.point(vp,xyz[0],xyz[1],xyz[2]);
            if(xy!=null)shapes.add(new Shape(xy,4,poi.type+" · "+poi.label));
        }
        Snapshot snapshot=new Snapshot(shapes,h,v,points,trail.size(),points==0?0:confidence/points,
            new ArrayList<>(mapPoints.values()),surfaces,path,currentPois,segment);
        snapshot.cameraX=camera.getPose().tx();snapshot.cameraZ=camera.getPose().tz();
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
