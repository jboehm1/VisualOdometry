package dev.jboehm.vectorvio;
import java.util.*;

/** Optional local-session backtracking aid. Works on a frozen, decimated metric
 * route. UI tolerances below never affect odometry or recorded poses. */
final class TraceGuide {
    private final ArrayList<Odometer.Point> route=new ArrayList<>();
    private double[] along;
    private int segment;
    private double cursor;
    void clear(){route.clear();along=null;}
    boolean active(){return along!=null;}
    boolean start(List<Odometer.Point> points,Odometer.Point current){
        clear();segment=current.segment;
        for(Odometer.Point p:points)if(p.segment==segment)route.add(p);
        if(route.isEmpty())return false;
        if(route.get(route.size()-1).distance(current)>0)route.add(current);
        if(route.size()<2)return false;
        along=new double[route.size()];for(int i=1;i<along.length;i++)along[i]=along[i-1]+route.get(i).distance(route.get(i-1));
        cursor=along[along.length-1];if(cursor<.5){clear();return false;}return true;
    }
    static final class Guidance {
        final double remaining,offRoute,x,y,z;
        final boolean arrived;
        Guidance(double s,double d,Odometer.Point p){remaining=s;offRoute=d;x=p.x;y=p.y;z=p.z;arrived=s<.6&&d<.6;}
    }
    Guidance update(Odometer.Point p){
        if(!active()||p.segment!=segment){clear();return null;}
        double best=Double.POSITIVE_INFINITY,bestS=cursor;Odometer.Point closest=null;
        // Limit branch search to the next 20m of reverse travel: crossings must not
        // snap to a distant earlier part of the route. Large deviations need care.
        for(int i=route.size()-1;i>0;i--){
            if(along[i]<cursor-20||along[i-1]>cursor+.5)continue;
            Odometer.Point a=route.get(i-1),b=route.get(i);double dx=b.x-a.x,dy=b.y-a.y,dz=b.z-a.z;
            double len=dx*dx+dy*dy+dz*dz;if(len==0)continue;
            double u=Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy+(p.z-a.z)*dz)/len));
            Odometer.Point q=new Odometer.Point(a.x+u*dx,a.y+u*dy,a.z+u*dz);
            double d=p.distance(q),s=along[i-1]+u*Math.sqrt(len);
            if(d<best-1e-6){best=d;bestS=s;closest=q;}
        }
        if(closest==null)return null;
        if(best<=1)cursor=Math.min(cursor,bestS);
        // When off-route, point to its closest location; once joined, look 1.5m
        // backwards along it. These are guidance/display distances only.
        Odometer.Point target=closest;
        if(best<=1){double desired=Math.max(0,cursor-1.5);for(int i=1;i<route.size();i++)if(along[i]>=desired){
            Odometer.Point a=route.get(i-1),b=route.get(i);double length=along[i]-along[i-1];double u=length>0?(desired-along[i-1])/length:0;
            target=new Odometer.Point(a.x+u*(b.x-a.x),a.y+u*(b.y-a.y),a.z+u*(b.z-a.z));break;
        }}
        return new Guidance(bestS,best,target);
    }
}
