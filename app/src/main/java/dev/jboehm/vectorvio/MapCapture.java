package dev.jboehm.vectorvio;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import com.google.ar.core.*;
import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.util.*;

/** GL thread only. Bounded anchored sparse snapshots; images captured only after opt-in. */
final class MapCapture {
    private static final class Live {
        final Anchor anchor;final float[] points;final byte[] image;final long timestamp;final int segment;
        Live(Anchor anchor,float[] points,byte[] image,long timestamp,int segment) {
            this.anchor=anchor;this.points=points;this.image=image;this.timestamp=timestamp;this.segment=segment;
        }
    }
    private static final class LivePoi {
        final Anchor anchor;final int segment;final long timestamp;final String type,label;
        LivePoi(Anchor anchor,int segment,long timestamp,String type,String label){this.anchor=anchor;this.segment=segment;this.timestamp=timestamp;this.type=type;this.label=label;}
    }
    private final List<Live> live=new ArrayList<>();
    private final List<MapArchive.Keyframe> frozen=new ArrayList<>();
    private final List<LivePoi> livePois=new ArrayList<>();
    private final List<MapArchive.Poi> frozenPois=new ArrayList<>();
    private int segment;
    private long lastCaptureNs;
    private float[] previousQuaternion;
    private Pose lastPose;
    private Anchor start;
    private long lastTimestamp;
    private long imageBytes;
    float closureMeters=-1;
    void reset(){finishSegment();frozen.clear();frozenPois.clear();segment=0;lastCaptureNs=0;lastTimestamp=0;imageBytes=0;}
    void finishSegment() {
        frozen.addAll(resolve());
        frozenPois.addAll(resolvePois());
        for(Live k:live)k.anchor.detach();live.clear();
        for(LivePoi poi:livePois)poi.anchor.detach();livePois.clear();
        if(start!=null){start.detach();start=null;}
        lastPose=null;previousQuaternion=null;closureMeters=-1;
    }
    boolean full(){return count()>=MapArchive.MAX_FRAMES || live.size()>=64;}
    int count(){return frozen.size()+live.size();}
    int poiCount(){return frozenPois.size()+livePois.size();}
    String addPoi(Session session,Frame frame,float x,float y,int currentSegment,String type,String label) {
        if(frame.getCamera().getTrackingState()!=TrackingState.TRACKING)return "Wait for TRACKING before placing a point.";
        if(poiCount()>=MapArchive.MAX_POIS)return "Map marker limit reached (64).";
        List<HitResult> hits=frame.hitTest(x,y);
        for(HitResult hit:hits) {
            if(hit.getTrackable() instanceof Plane) {
                Plane plane=(Plane)hit.getTrackable();
                if(plane.getTrackingState()!=TrackingState.TRACKING||!plane.isPoseInPolygon(hit.getHitPose()))continue;
                Anchor anchor=session.createAnchor(hit.getHitPose());
                livePois.add(new LivePoi(anchor,currentSegment,frame.getTimestamp(),type,label));
                return "Placed "+type.toLowerCase(Locale.US)+": "+label;
            }
        }
        return "Aim the reticle at a tracked plane, then place the marker.";
    }
    List<MapArchive.Poi> pois(){List<MapArchive.Poi> all=new ArrayList<>(frozenPois);all.addAll(resolvePois());return all;}
    int imageCount(){int n=0;for(Live k:live)if(k.image.length>0)n++;for(MapArchive.Keyframe k:frozen)if(k.image.length>0)n++;return n;}
    void update(Session session,Frame frame,Odometer.Result result,int currentSegment,boolean images,float intervalSeconds,float minDistance,float minRotationDegrees,float pointConfidence) {
        if(frame.getCamera().getTrackingState()!=TrackingState.TRACKING){finishSegment();return;}
        if(currentSegment!=segment){finishSegment();segment=currentSegment;}
        if(result==Odometer.Result.GAP || result==Odometer.Result.REJECTED){finishSegment();}
        if(result==Odometer.Result.INACTIVE || result==Odometer.Result.REJECTED)return;
        Pose pose=frame.getCamera().getPose();
        if(start==null)start=session.createAnchor(pose);
        if(start.getTrackingState()==TrackingState.TRACKING)closureMeters=distance(start.getPose(),pose);
        lastPose=pose;lastTimestamp=frame.getTimestamp();
        if(count()>=MapArchive.MAX_FRAMES || live.size()>=64 || frame.getTimestamp()-lastCaptureNs<intervalSeconds*1_000_000_000L)return;
        float[] q=pose.getRotationQuaternion();
        if(!live.isEmpty()) {
            Pose previous=live.get(live.size()-1).anchor.getPose();
            float dot=0;for(int i=0;i<4;i++)dot+=q[i]*previousQuaternion[i];
            double turn=Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(dot))));
            if(distance(pose,previous)<minDistance && turn<minRotationDegrees)return;
        }
        Anchor anchor=session.createAnchor(pose);float[] points;
        try(PointCloud cloud=frame.acquirePointCloud()) {
            FloatBuffer data=cloud.getPoints();int count=data.remaining()/4;
            int stride=Math.max(1,(count+MapArchive.MAX_POINTS-1)/MapArchive.MAX_POINTS);
            ArrayList<Float> local=new ArrayList<>();Pose inverse=pose.inverse();
            for(int i=0;i<count;i++) {
                float x=data.get(),y=data.get(),z=data.get(),confidence=data.get();
                if(i%stride!=0 || !Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)
                        ||!Float.isFinite(confidence)||confidence<pointConfidence||confidence>1)continue;
                float[] p=inverse.transformPoint(new float[]{x,y,z});
                if(!Float.isFinite(p[0])||!Float.isFinite(p[1])||!Float.isFinite(p[2]))continue;
                for(float v:p)local.add(v);local.add(confidence);
            }
            points=new float[local.size()];for(int i=0;i<points.length;i++)points[i]=local.get(i);
        } catch(Exception e){anchor.detach();return;}
        byte[] image=new byte[0];
        if(images && imageBytes<MapArchive.MAX_IMAGE_TOTAL_BYTES) {
            try(Image cameraImage=frame.acquireCameraImage()){
                byte[] encoded=jpeg(cameraImage);
                if(encoded.length<=MapArchive.MAX_IMAGE_BYTES && imageBytes+encoded.length<=MapArchive.MAX_IMAGE_TOTAL_BYTES) {
                    image=encoded;imageBytes+=encoded.length;
                }
            }
            catch(Exception ignored){ /* Optional image unavailable; keep the measured keyframe. */ }
        }
        live.add(new Live(anchor,points,image,frame.getTimestamp(),segment));
        lastCaptureNs=frame.getTimestamp();previousQuaternion=q;
    }
    MapArchive snapshot(){List<MapArchive.Keyframe> all=new ArrayList<>(frozen);all.addAll(resolve());return new MapArchive(all,pois(),false);}
    String loopCheck() {
        if(start==null || lastPose==null || start.getTrackingState()!=TrackingState.TRACKING)return "No tracked start anchor. Start recording and return within the same segment.";
        Pose origin=start.getPose();float[] a=origin.getRotationQuaternion(),b=lastPose.getRotationQuaternion();float dot=0;
        for(int i=0;i<4;i++)dot+=a[i]*b[i];
        double angle=Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(dot))));
        return String.format(Locale.US,"Return-to-start separation: %.3f m\nOrientation difference: %.1f°\n\nValid only if you physically returned the CAMERA to the same position and orientation. This includes placement error and drift. ARCore may refine its local map; this action does not force loop closure or alter measurements.\n\nFrame timestamp: %d ns",distance(origin,lastPose),angle,lastTimestamp);
    }
    private List<MapArchive.Keyframe> resolve() {
        List<MapArchive.Keyframe> result=new ArrayList<>();
        for(Live k:live) {
            if(k.anchor.getTrackingState()==TrackingState.STOPPED)continue;
            Pose p=k.anchor.getPose();float[] xyzq=new float[7];
            System.arraycopy(p.getTranslation(),0,xyzq,0,3);System.arraycopy(p.getRotationQuaternion(),0,xyzq,3,4);
            float[] world=new float[k.points.length];
            for(int i=0;i<world.length;i+=4) {
                p.transformPoint(k.points,i,world,i);world[i+3]=k.points[i+3];
            }
            if(MapArchive.validPose(xyzq))result.add(new MapArchive.Keyframe(k.segment,k.timestamp,xyzq,MapArchive.cleanPoints(world),k.image));
        }
        if(lastPose!=null && !live.isEmpty() && lastTimestamp>live.get(live.size()-1).timestamp && frozen.size()+result.size()<MapArchive.MAX_FRAMES) {
            float[] pose=new float[7];System.arraycopy(lastPose.getTranslation(),0,pose,0,3);System.arraycopy(lastPose.getRotationQuaternion(),0,pose,3,4);
            if(MapArchive.validPose(pose))result.add(new MapArchive.Keyframe(segment,lastTimestamp,pose,new float[0],new byte[0]));
        }
        return result;
    }
    private List<MapArchive.Poi> resolvePois() {
        List<MapArchive.Poi> result=new ArrayList<>();
        for(LivePoi poi:livePois) {
            // A temporarily paused anchor can still hold a useful last pose.
            // Retain it through short tracking interruptions; discard only anchors
            // ARCore has permanently stopped.
            if(poi.anchor.getTrackingState()==TrackingState.STOPPED)continue;
            Pose p=poi.anchor.getPose();float[] pose=new float[7];
            System.arraycopy(p.getTranslation(),0,pose,0,3);System.arraycopy(p.getRotationQuaternion(),0,pose,3,4);
            if(MapArchive.validPose(pose))result.add(new MapArchive.Poi(poi.segment,poi.timestamp,poi.type,poi.label,pose));
        }
        return result;
    }
    private static float distance(Pose a,Pose b){float x=a.tx()-b.tx(),y=a.ty()-b.ty(),z=a.tz()-b.tz();return (float)Math.sqrt(x*x+y*y+z*z);}
    /** YUV_420_888 supports arbitrary row/pixel strides; downsample to <=640px for bounded capture cost. */
    static byte[] jpeg(Image image) {
        int step=2;while(image.getWidth()/step>640)step*=2;
        Rect crop=image.getCropRect();int w=(crop.width()/step)&~1,h=(crop.height()/step)&~1;
        byte[] nv21=new byte[w*h*3/2];Image.Plane[] planes=image.getPlanes();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)nv21[y*w+x]=sample(planes[0],crop.left+x*step,crop.top+y*step);
        int offset=w*h;
        for(int y=0;y<h/2;y++)for(int x=0;x<w/2;x++) {
            int sx=crop.left/2+x*step,sy=crop.top/2+y*step;
            nv21[offset++]=sample(planes[2],sx,sy);nv21[offset++]=sample(planes[1],sx,sy);
        }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        new YuvImage(nv21,ImageFormat.NV21,w,h,null).compressToJpeg(new Rect(0,0,w,h),75,bytes);
        return bytes.toByteArray();
    }
    private static byte sample(Image.Plane p,int x,int y){return p.getBuffer().get(p.getBuffer().position()+y*p.getRowStride()+x*p.getPixelStride());}
}
