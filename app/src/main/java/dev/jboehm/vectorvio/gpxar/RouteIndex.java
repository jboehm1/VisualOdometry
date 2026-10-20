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
    /** Closest GPX point globally. Returns {lat,lon,distanceM}; never snaps actual fixes. */
    public double[] closest(double lat,double lon){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>85||Math.abs(lon)>180)return null;
        double[] q=frame.toLocal(lat,lon);double x=q[0],z=q[1];
        double best=Double.POSITIVE_INFINITY,px=0,pz=0;
        for(int c=0;c<boxes.length;c++){
            double[] b=boxes[c];
            double ex=Math.max(Math.max(b[0]-x,0),x-b[2]),ez=Math.max(Math.max(b[1]-z,0),z-b[3]);
            if(ex*ex+ez*ez>=best)continue;
            for(int i=c*CHUNK;i<Math.min(east.length-1,(c+1)*CHUNK);i++){
                double ax=east[i],az=north[i],dx=east[i+1]-ax,dz=north[i+1]-az;
                double len2=dx*dx+dz*dz;if(len2<1e-10)continue;
                double t=Math.max(0,Math.min(1,((x-ax)*dx+(z-az)*dz)/len2));
                double cx=ax+t*dx,cz=az+t*dz,dist2=(cx-x)*(cx-x)+(cz-z)*(cz-z);
                if(dist2<best){best=dist2;px=cx;pz=cz;}
            }
        }
        if(!Double.isFinite(best))return null;
        double[] ll=coordinate(px,pz);
        return new double[]{ll[0],ll[1],Math.sqrt(best)};
    }
    /** lat/lon endpoints of nearby clipped edges. Caps work for extremely dense or overlapping GPX. */
    public List<double[]> near(double lat,double lon,double radius,int limit){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(radius)||radius<=0||limit<=0)return Collections.emptyList();
        double[] center=frame.toLocal(lat,lon);double x=center[0],z=center[1];List<double[]> out=new ArrayList<>();
        for(int c=0;c<boxes.length&&out.size()<limit;c++){
            double[] b=boxes[c];if(b[0]>x+radius||b[2]<x-radius||b[1]>z+radius||b[3]<z-radius)continue;
            for(int i=c*CHUNK;i<Math.min(east.length-1,(c+1)*CHUNK)&&out.size()<limit;i++){
                double ax=east[i]-x,az=north[i]-z,dx=east[i+1]-east[i],dz=north[i+1]-north[i];
                double length2=dx*dx+dz*dz;if(length2<1e-12)continue;
                double t=Math.max(0,Math.min(1,-(ax*dx+az*dz)/length2));
                if(Math.hypot(ax+t*dx,az+t*dz)>radius)continue;
                double dot=ax*dx+az*dz,disc=dot*dot-length2*(ax*ax+az*az-radius*radius);
                if(disc<0)continue;
                double lo=Math.max(0,(-dot-Math.sqrt(disc))/length2),hi=Math.min(1,(-dot+Math.sqrt(disc))/length2);
                if(hi<=lo)continue;
                double[] a=coordinate(x+ax+lo*dx,z+az+lo*dz),e=coordinate(x+ax+hi*dx,z+az+hi*dz);
                out.add(new double[]{a[0],a[1],e[0],e[1]});
            }
        }
        return out;
    }
}
