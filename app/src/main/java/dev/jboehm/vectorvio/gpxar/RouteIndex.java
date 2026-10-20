package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.gps.GpsRoute;
import java.util.*;
import dev.jboehm.vectorvio.geo.Wgs84LocalFrame;

/** Immutable bounded spatial index. Original edges are clipped, never snapped to the camera. */
public final class RouteIndex {
    private static final int CHUNK=128;
    private final Wgs84LocalFrame frame;
    private final double[] east,north;
    private final double[][] boxes;
    public RouteIndex(GpsRoute route){
        double lat0=route.points.get(0).lat,lon0=route.points.get(0).lon;
        if(Math.abs(lat0)>85)throw new IllegalArgumentException("Polar AR route not supported");
        frame=new Wgs84LocalFrame(lat0,lon0);
        ArrayList<double[]> expanded=new ArrayList<>();expanded.add(frame.toLocal(lat0,lon0));
        for(int i=1;i<route.points.size();i++){
            double start=route.progressAt(i-1),length=route.progressAt(i)-start;
            int parts=Math.max(1,(int)Math.ceil(length/25));
            if(parts>200000-expanded.size())throw new IllegalArgumentException("AR route densification exceeds 200000 vertices; split route");
            for(int j=1;j<=parts;j++){
                GpsRoute.Point p=j==parts?route.points.get(i):route.at(start+length*j/parts);
                double[] en=frame.toLocal(p.lat,p.lon);
                if(Math.hypot(en[0],en[1])>100000)throw new IllegalArgumentException("Split AR routes into sections below 100 km");
                expanded.add(en);
            }
        }
        int n=expanded.size();east=new double[n];north=new double[n];
        for(int i=0;i<n;i++){east[i]=expanded.get(i)[0];north[i]=expanded.get(i)[1];}
        boxes=new double[(n-2)/CHUNK+1][4];
        for(int c=0;c<boxes.length;c++){
            double[] b=boxes[c];b[0]=b[1]=Double.POSITIVE_INFINITY;b[2]=b[3]=Double.NEGATIVE_INFINITY;
            for(int i=c*CHUNK;i<=Math.min(n-1,(c+1)*CHUNK);i++){
                b[0]=Math.min(b[0],east[i]);b[1]=Math.min(b[1],north[i]);b[2]=Math.max(b[2],east[i]);b[3]=Math.max(b[3],north[i]);
            }
        }
    }
    private double[] coordinate(double e,double n){return frame.toGeographic(e,n);}

    /**
     * Global camera-preview LOD. All imported edges stay in this index, regardless
     * of the display budget. Samples are distributed ACROSS the full geographic
     * route rather than taking the first N edges or only the nearest 90 m.
     * Far preview segments are deliberately dashed and approximate, never floor
     * observations. No sampled edge is bridged across discarded geometry.
     */
    public List<double[]> world(double lat,double lon,double horizonMeters,int maxEdges){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>85||
           Math.abs(lon)>180||!Double.isFinite(horizonMeters)||horizonMeters<=90||maxEdges<=0)
            return Collections.emptyList();
        double[] q=frame.toLocal(lat,lon);
        // Four distance bands retain distant sections even on dense, looping tracks.
        double[] bands={300,1500,6000,horizonMeters};
        int[] counts=new int[4];
        for(int i=0;i<east.length-1;i++){
            int band=worldBand(i,q[0],q[1],bands);
            if(band>=0)counts[band]++;
        }
        int nonEmpty=0;for(int count:counts)if(count>0)nonEmpty++;
        if(nonEmpty==0)return Collections.emptyList();
        int[] quota=new int[4];
        int remaining=maxEdges;
        for(int k=0;k<4;k++)if(counts[k]>0){
            quota[k]=Math.min(counts[k],Math.max(1,maxEdges/nonEmpty));
            remaining-=quota[k];
        }
        // Use unused bands' capacity, favoring closer detail without starving far.
        for(int k=0;k<4&&remaining>0;k++){
            int add=Math.min(remaining,counts[k]-quota[k]);
            quota[k]+=add;remaining-=add;
        }
        int[] visited=new int[4];
        ArrayList<double[]> out=new ArrayList<>(Math.min(maxEdges,4096));
        for(int i=0;i<east.length-1;i++){
            int band=worldBand(i,q[0],q[1],bands);
            if(band<0)continue;
            int n=visited[band]++;
            // Evenly distribute the selected edges throughout every GPX band.
            if(((long)(n+1)*quota[band])/counts[band] ==
               ((long)n*quota[band])/counts[band])continue;
            double[] a=coordinate(east[i],north[i]),b=coordinate(east[i+1],north[i+1]);
            if(a!=null&&b!=null)out.add(new double[]{a[0],a[1],b[0],b[1]});
        }
        return out;
    }
    private int worldBand(int i,double x,double z,double[] bands){
        double mx=(east[i]+east[i+1])*.5-x,mz=(north[i]+north[i+1])*.5-z;
        double distance=Math.hypot(mx,mz);
        if(distance<=90||distance>bands[3])return -1;
        if(distance<=bands[0])return 0;
        if(distance<=bands[1])return 1;
        return distance<=bands[2]?2:3;
    }

    /** Returns {lat,lon,distanceMeters} to the closest segment of the WHOLE GPX.
     * Never modifies sensor positions, navigation state or camera poses. */
    public double[] closest(double lat,double lon){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>85||Math.abs(lon)>180)return null;
        double[] query=frame.toLocal(lat,lon);
        double x=query[0],z=query[1],best=Double.POSITIVE_INFINITY,nearestX=0,nearestZ=0;
        for(int c=0;c<boxes.length;c++){
            double[] b=boxes[c];
            double bx=Math.max(Math.max(b[0]-x,0),x-b[2]);
            double bz=Math.max(Math.max(b[1]-z,0),z-b[3]);
            if(bx*bx+bz*bz>=best)continue;
            for(int i=c*CHUNK;i<Math.min(east.length-1,(c+1)*CHUNK);i++){
                double ax=east[i],az=north[i],dx=east[i+1]-ax,dz=north[i+1]-az;
                double sq=dx*dx+dz*dz;if(sq<1e-12)continue;
                double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/sq));
                double px=ax+t*dx,pz=az+t*dz;
                double ds=(px-x)*(px-x)+(pz-z)*(pz-z);
                if(ds<best){best=ds;nearestX=px;nearestZ=pz;}
            }
        }
        if(!Double.isFinite(best))return null;
        double[] ll=coordinate(nearestX,nearestZ);
        return ll==null?null:new double[]{ll[0],ll[1],Math.sqrt(best)};
    }
    /** lat/lon endpoints of nearby clipped edges. Caps work for extremely dense or overlapping GPX. */
    private static final class Candidate {
        final int index;final double distance;final double[] coords;
        Candidate(int index,double distance,double[] coords){this.index=index;this.distance=distance;this.coords=coords;}
    }
    /** Prefer NEAREST local segments, but emit retained lines in GPX order. */
    public List<double[]> near(double lat,double lon,double radius,int limit){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(radius)||radius<=0||limit<=0)
            return Collections.emptyList();
        double[] center=frame.toLocal(lat,lon), xz=center;
        double x=xz[0],z=xz[1];
        PriorityQueue<Candidate> top=new PriorityQueue<>((a,b)->{
            int rank=Double.compare(b.distance,a.distance);
            return rank!=0?rank:Integer.compare(b.index,a.index);
        });
        for(int c=0;c<boxes.length;c++){
            double[] b=boxes[c];
            if(b[0]>x+radius||b[2]<x-radius||b[1]>z+radius||b[3]<z-radius)continue;
            for(int i=c*CHUNK;i<Math.min(east.length-1,(c+1)*CHUNK);i++){
                double ax=east[i]-x,az=north[i]-z,dx=east[i+1]-east[i],dz=north[i+1]-north[i];
                double length2=dx*dx+dz*dz;if(length2<1e-12)continue;
                double t=Math.max(0,Math.min(1,-(ax*dx+az*dz)/length2));
                double distance=Math.hypot(ax+t*dx,az+t*dz);
                if(distance>radius)continue;
                double dot=ax*dx+az*dz,disc=dot*dot-length2*(ax*ax+az*az-radius*radius);
                if(disc<0)continue;
                double lo=Math.max(0,(-dot-Math.sqrt(disc))/length2),hi=Math.min(1,(-dot+Math.sqrt(disc))/length2);
                if(hi<=lo)continue;
                if(top.size()==limit && distance>=top.peek().distance)continue;
                double[] a=coordinate(x+ax+lo*dx,z+az+lo*dz),e=coordinate(x+ax+hi*dx,z+az+hi*dz);
                top.offer(new Candidate(i,distance,new double[]{a[0],a[1],e[0],e[1]}));
                if(top.size()>limit)top.poll();
            }
        }
        ArrayList<Candidate> keep=new ArrayList<>(top);
        keep.sort(Comparator.comparingInt(p->p.index));
        ArrayList<double[]> result=new ArrayList<>(keep.size());
        for(Candidate c:keep)result.add(c.coords);
        return result;
    }
}
