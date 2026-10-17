package dev.jboehm.vectorvio;
import java.util.*;
/** Bounded world-frame display history. Segment breaks are retained, never stitched. */
final class RouteHistory {
    private final ArrayList<Odometer.Point> points=new ArrayList<>();
    void add(Odometer.Point p){
        if(points.size()>=8192){
            ArrayList<Odometer.Point> reduced=new ArrayList<>();
            for(int i=0;i<points.size();i++)if(i==0||i==points.size()-1||i%2==0||points.get(i-1).segment!=points.get(i).segment||points.get(i+1).segment!=points.get(i).segment)reduced.add(points.get(i));
            points.clear();points.addAll(reduced);
            if(points.size()>=8192)points.subList(1,4096).clear();
        }
        points.add(p);
    }
    List<Odometer.Point> snapshot(){return Collections.unmodifiableList(new ArrayList<>(points));}
    Odometer.Point last(){return points.isEmpty()?null:points.get(points.size()-1);}
    void clear(){points.clear();}
    static double[] bounds(List<Odometer.Point> p){
        if(p.isEmpty())return new double[]{0,0,2};
        double lx=p.get(0).x,hx=lx,lz=p.get(0).z,hz=lz;
        for(Odometer.Point v:p){lx=Math.min(lx,v.x);hx=Math.max(hx,v.x);lz=Math.min(lz,v.z);hz=Math.max(hz,v.z);}
        return new double[]{(lx+hx)/2,(lz+hz)/2,Math.max(2,Math.max(hx-lx,hz-lz)*1.25)};
    }
}
