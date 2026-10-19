package dev.jboehm.vectorvio.gpxar;

import dev.jboehm.vectorvio.gps.GpsRoute;
import java.util.*;

/** Immutable bounded spatial index. Original edges are clipped, never snapped to the camera. */
public final class RouteIndex {
    private static final double R=6371008.8;
    private static final int CHUNK=128;
    private final double lat0,lon0,cos;
    private final double[] east,north;
    private final double[][] boxes;
    public RouteIndex(GpsRoute route){
        lat0=route.points.get(0).lat;lon0=route.points.get(0).lon;cos=Math.cos(Math.toRadians(lat0));
        if(Math.abs(lat0)>85)throw new IllegalArgumentException("Polar AR route not supported");
        int n=route.points.size();east=new double[n];north=new double[n];
        for(int i=0;i<n;i++){
            GpsRoute.Point p=route.points.get(i);east[i]=east(p.lon);north[i]=north(p.lat);
            if(Math.hypot(east[i],north[i])>100_000)throw new IllegalArgumentException("Split AR routes into sections below 100 km");
        }
        boxes=new double[(n-2)/CHUNK+1][4];
        for(int c=0;c<boxes.length;c++){
            double[] b=boxes[c];b[0]=b[1]=Double.POSITIVE_INFINITY;b[2]=b[3]=Double.NEGATIVE_INFINITY;
            for(int i=c*CHUNK;i<=Math.min(n-1,(c+1)*CHUNK);i++){
                b[0]=Math.min(b[0],east[i]);b[1]=Math.min(b[1],north[i]);b[2]=Math.max(b[2],east[i]);b[3]=Math.max(b[3],north[i]);
            }
        }
    }
    private double east(double lon){return Math.toRadians(((lon-lon0+540)%360)-180)*R*cos;}
    private double north(double lat){return Math.toRadians(lat-lat0)*R;}
    private double[] coordinate(double e,double n){return new double[]{lat0+Math.toDegrees(n/R),((lon0+Math.toDegrees(e/(R*cos))+540)%360)-180};}
    /** lat/lon endpoints of nearby clipped edges. Caps work for extremely dense or overlapping GPX. */
    public List<double[]> near(double lat,double lon,double radius,int limit){
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||!Double.isFinite(radius)||radius<=0||limit<=0)return Collections.emptyList();
        double x=east(lon),z=north(lat);List<double[]> out=new ArrayList<>();
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
