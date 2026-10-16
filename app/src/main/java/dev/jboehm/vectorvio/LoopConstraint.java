package dev.jboehm.vectorvio;

import java.util.ArrayList;
import java.util.List;

/** Explicit user-supplied same-pose endpoint constraint. No automatic place recognition.
 * Distributes a rigid correction over time; does not estimate a SLAM pose graph.
 */
final class LoopConstraint {
    static MapArchive apply(MapArchive source,int segment) {
        List<MapArchive.Keyframe> group=new ArrayList<>();
        for(MapArchive.Keyframe k:source.frames)if(k.segment==segment)group.add(k);
        if(group.size()<3)throw new IllegalArgumentException("Need at least three keyframes in one closed route");
        float[] first=group.get(0).pose,last=group.get(group.size()-1).pose;
        float[] qa={first[3],first[4],first[5],first[6]},qb={-last[3],-last[4],-last[5],last[6]};
        float[] correction=normalize(multiply(qa,qb));
        if(correction[3]<0)for(int i=0;i<4;i++)correction[i]=-correction[i];
        float[] rotated=rotate(correction,new float[]{last[0],last[1],last[2]});
        float[] translation={first[0]-rotated[0],first[1]-rotated[1],first[2]-rotated[2]};
        List<MapArchive.Keyframe> output=new ArrayList<>();int index=0;
        for(MapArchive.Keyframe k:source.frames) {
            if(k.segment!=segment){output.add(k);continue;}
            float t=(float)index++/(group.size()-1);
            float[] q=interpolate(correction,t);
            float[] p=rotate(q,new float[]{k.pose[0],k.pose[1],k.pose[2]});
            float[] orientation=multiply(q,new float[]{k.pose[3],k.pose[4],k.pose[5],k.pose[6]});
            float[] pose=new float[7];for(int i=0;i<3;i++)pose[i]=p[i]+t*translation[i];
            System.arraycopy(orientation,0,pose,3,4);
            float[] points=new float[k.points.length];
            for(int i=0;i<points.length;i+=4) {
                float[] point=rotate(q,new float[]{k.points[i],k.points[i+1],k.points[i+2]});
                for(int j=0;j<3;j++)points[i+j]=point[j]+t*translation[j];points[i+3]=k.points[i+3];
            }
            output.add(new MapArchive.Keyframe(segment,k.timestamp,pose,points,k.image));
        }
        long firstTime=group.get(0).timestamp,lastTime=group.get(group.size()-1).timestamp;
        List<MapArchive.Poi> marks=new ArrayList<>();
        for(MapArchive.Poi poi:source.pois) {
            if(poi.segment!=segment){marks.add(poi);continue;}
            float t=lastTime>firstTime?Math.max(0,Math.min(1,(float)(poi.timestamp-firstTime)/(lastTime-firstTime))):0;
            float[] q=interpolate(correction,t),p=rotate(q,new float[]{poi.pose[0],poi.pose[1],poi.pose[2]});
            float[] pose=new float[7];for(int j=0;j<3;j++)pose[j]=p[j]+t*translation[j];
            float[] orientation=multiply(q,new float[]{poi.pose[3],poi.pose[4],poi.pose[5],poi.pose[6]});
            System.arraycopy(normalize(orientation),0,pose,3,4);
            marks.add(new MapArchive.Poi(poi.segment,poi.timestamp,poi.type,poi.label,pose));
        }
        return new MapArchive(output,marks,true);
    }
    private static float[] interpolate(float[] correction,float t) {
        double theta=Math.acos(Math.max(-1,Math.min(1,correction[3])));
        float factor=theta<1e-6?t:(float)(Math.sin(t*theta)/Math.sin(theta));
        return normalize(new float[]{correction[0]*factor,correction[1]*factor,correction[2]*factor,(float)Math.cos(t*theta)});
    }
    private static float[] normalize(float[] q) {
        double n=0;for(float p:q)n+=p*p;
        if(n<1e-10)throw new IllegalArgumentException("Invalid rotation");
        n=Math.sqrt(n);for(int i=0;i<4;i++)q[i]/=n;return q;
    }
    private static float[] multiply(float[] a,float[] b) {
        return new float[]{a[3]*b[0]+a[0]*b[3]+a[1]*b[2]-a[2]*b[1],a[3]*b[1]-a[0]*b[2]+a[1]*b[3]+a[2]*b[0],a[3]*b[2]+a[0]*b[1]-a[1]*b[0]+a[2]*b[3],a[3]*b[3]-a[0]*b[0]-a[1]*b[1]-a[2]*b[2]};
    }
    private static float[] rotate(float[] q,float[] v) {
        float tx=2*(q[1]*v[2]-q[2]*v[1]),ty=2*(q[2]*v[0]-q[0]*v[2]),tz=2*(q[0]*v[1]-q[1]*v[0]);
        return new float[]{v[0]+q[3]*tx+q[1]*tz-q[2]*ty,v[1]+q[3]*ty+q[2]*tx-q[0]*tz,v[2]+q[3]*tz+q[0]*ty-q[1]*tx};
    }
}
