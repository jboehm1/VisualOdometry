package dev.jboehm.vectorvio;

import android.opengl.Matrix;
import com.google.ar.core.*;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/** GL-thread-owned local map. Anchors follow ARCore map corrections; no persistent map. */
final class SceneMapper {
    static final class Shape {
        final float[] xy; final int kind; final String label;
        Shape(float[] xy,int kind,String label){this.xy=xy;this.kind=kind;this.label=label;}
    }
    static final class Snapshot {
        final List<Shape> shapes;
        final int horizontal,vertical,points,anchors;
        final float confidence;
        float heading,pitch,targetMeters=-1;
        Snapshot(List<Shape> shapes,int h,int v,int p,int a,float q) {
            this.shapes=shapes;horizontal=h;vertical=v;points=p;anchors=a;confidence=q;
        }
    }
    private final ArrayList<Anchor> trail=new ArrayList<>();
    private final float[] view=new float[16],projection=new float[16],vp=new float[16];
    void clear() { for(Anchor a:trail) a.detach(); trail.clear(); }
    Snapshot update(Session session,Frame frame,Odometer.Result result,int width,int height) {
        Camera camera=frame.getCamera();
        if(camera.getTrackingState()!=TrackingState.TRACKING) {
            clear();return new Snapshot(new ArrayList<>(),0,0,0,0,0);
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
        ArrayList<Shape> shapes=new ArrayList<>();
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
            if(xy.length>=6)shapes.add(new Shape(xy,vertical?1:0,String.format(java.util.Locale.US,
                "%s  %.1f × %.1f m",vertical?"VERTICAL":"HORIZONTAL",plane.getExtentX(),plane.getExtentZ())));
        }
        int points=0;float confidence=0;
        try(PointCloud cloud=frame.acquirePointCloud()) {
            FloatBuffer data=cloud.getPoints();points=data.remaining()/4;
            int stride=Math.max(1,(points+239)/240);
            for(int i=0;i<points;i++) {
                float x=data.get(),y=data.get(),z=data.get(),q=data.get();confidence+=q;
                if(i%stride!=0 || q<.2f)continue;
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
        Snapshot snapshot=new Snapshot(shapes,h,v,points,trail.size(),points==0?0:confidence/points);
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
