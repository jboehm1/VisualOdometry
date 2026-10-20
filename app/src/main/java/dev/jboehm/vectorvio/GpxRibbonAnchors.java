package dev.jboehm.vectorvio;

import com.google.ar.core.*;
import java.util.*;

/** GL-thread bounded local anchors for projected GPX geometry. No measurement filtering.
 * Reuses anchors across geographic revisions. Geometry is local between revisions, so
 * ARCore world corrections move each patch with its own nearby tracked reference. */
final class GpxRibbonAnchors {
    static final int LIMIT=32,CREATIONS_PER_FRAME=2;
    static final float TILE=6f;
    interface Reference { Pose pose(); boolean tracking(); void detach(); }
    interface Factory { Reference create(Pose pose); }
    private static final class Patch {
        Reference anchor;float[] local;long revision,topology;
        Patch(Reference anchor){this.anchor=anchor;}
    }
    private static final class Group {
        final long key;final int tx,tz;final ArrayList<float[]> edges=new ArrayList<>();long hash=1;
        double distance;
        Group(long key,int tx,int tz){this.key=key;this.tx=tx;this.tz=tz;}
    }
    private final LinkedHashMap<Long,Patch> patches=new LinkedHashMap<>();
    private int segment;
    private long retryNs;
    int size(){return patches.size();}
    void clear(){for(Patch p:patches.values())detach(p.anchor);patches.clear();segment=0;retryNs=0;}
    private static boolean tracking(Reference ref){try{return ref.tracking();}catch(RuntimeException unavailable){return false;}}
    private static void detach(Reference ref){try{ref.detach();}catch(RuntimeException ignored){}}
    List<float[]> update(Session session,Pose frame,List<float[]> edges,int segment,long revision,long nowNs,float cameraX,float cameraZ){
        return update(p->{Anchor a=session.createAnchor(p);return new Reference(){
            public Pose pose(){return a.getPose();}public boolean tracking(){return a.getTrackingState()==TrackingState.TRACKING;}
            public void detach(){a.detach();}
        };},frame,edges,segment,revision,nowNs,cameraX,cameraZ);
    }
    List<float[]> update(Factory factory,Pose worldFromMetric,List<float[]> edges,int currentSegment,long revision,long nowNs,float cameraX,float cameraZ){
        if(worldFromMetric==null||currentSegment<=0||edges.isEmpty()){clear();return Collections.emptyList();}
        if(segment!=currentSegment){clear();segment=currentSegment;}
        Pose metricFromWorld=worldFromMetric.inverse();Map<Long,Group> groups=new LinkedHashMap<>();
        for(float[] edge:edges){
            if(edge.length!=6)continue;boolean valid=true;for(float v:edge)valid&=Float.isFinite(v);if(!valid)continue;
            float[] a=metricFromWorld.transformPoint(new float[]{edge[0],edge[1],edge[2]}),b=metricFromWorld.transformPoint(new float[]{edge[3],edge[4],edge[5]});
            if(Math.hypot(a[0]-b[0],a[2]-b[2])>1.01)continue; // projected subdivisions only, never a long unbounded edge
            int tx=(int)Math.floor((a[0]+b[0])*.5/TILE),tz=(int)Math.floor((a[2]+b[2])*.5/TILE);
            long key=((long)tx<<32)^(tz&0xffffffffL);
            Group g=groups.get(key);if(g==null){g=new Group(key,tx,tz);groups.put(key,g);}
            g.edges.add(edge);
            // Horizontal topology only: local reference carries vertical/map corrections.
            for(float v:new float[]{a[0],a[2],b[0],b[2]})g.hash=31*g.hash+Math.round(v*100);
        }
        ArrayList<Group> visible=new ArrayList<>(groups.values());
        for(Group g:visible){float[] center=worldFromMetric.transformPoint(new float[]{(g.tx+.5f)*TILE,0,(g.tz+.5f)*TILE});g.distance=Math.hypot(center[0]-cameraX,center[2]-cameraZ);}
        visible.sort(Comparator.comparingDouble(g->g.distance));if(visible.size()>LIMIT)visible.subList(LIMIT,visible.size()).clear();
        Set<Long> wanted=new HashSet<>();for(Group g:visible)wanted.add(g.key);
        Iterator<Map.Entry<Long,Patch>> it=patches.entrySet().iterator();
        while(it.hasNext()){Map.Entry<Long,Patch> e=it.next();if(!wanted.contains(e.getKey())||!tracking(e.getValue().anchor)){detach(e.getValue().anchor);it.remove();}}
        int created=0;ArrayList<float[]> result=new ArrayList<>();
        for(Group g:visible){
            Patch p=patches.get(g.key);
            if(p==null){
                if(created>=CREATIONS_PER_FRAME||nowNs<retryNs)continue;
                created++;
                try{
                    float[] mid=worldFromMetric.transformPoint(new float[]{(g.tx+.5f)*TILE,0,(g.tz+.5f)*TILE});
                    mid[1]=g.edges.get(0)[1];Reference ref=factory.create(Pose.makeTranslation(mid));
                    if(!tracking(ref)){detach(ref);continue;}
                    p=new Patch(ref);patches.put(g.key,p);
                }catch(RuntimeException failure){retryNs=nowNs+2_000_000_000L;continue;}
            }
            Pose anchorPose;try{anchorPose=p.anchor.pose();}catch(RuntimeException unavailable){detach(p.anchor);patches.remove(g.key);continue;}
            if(p.local==null||p.revision!=revision||p.topology!=g.hash){
                float[] local=new float[g.edges.size()*6];int offset=0;Pose inverse=anchorPose.inverse();
                for(float[] edge:g.edges)for(int i=0;i<6;i+=3){float[] q=inverse.transformPoint(new float[]{edge[i],edge[i+1],edge[i+2]});System.arraycopy(q,0,local,offset,3);offset+=3;}
                p.local=local;p.revision=revision;p.topology=g.hash;
            }
            for(int i=0;i<p.local.length;i+=6){float[] a=anchorPose.transformPoint(new float[]{p.local[i],p.local[i+1],p.local[i+2]}),b=anchorPose.transformPoint(new float[]{p.local[i+3],p.local[i+4],p.local[i+5]});
                result.add(new float[]{a[0],a[1],a[2],b[0],b[1],b[2]});}
        }
        return result;
    }
}
