package dev.jboehm.vectorvio.gps;
import java.util.*;
/** One continuous GPX segment. Disconnected segments are never stitched. */
public final class GpsRoute {
    public static final int MAX_POINTS=50000;
    public static final class Point {
        public final double lat,lon,ele;
        public Point(double lat,double lon,double ele){if(!Geo.valid(lat,lon)||(!Double.isNaN(ele)&&!Double.isFinite(ele)))throw new IllegalArgumentException("Invalid coordinate");this.lat=lat;this.lon=lon;this.ele=ele;}
    }
    public final String name;
    public final List<Point> points;
    private final double[] along;
    public double progressAt(int index){return along[index];}
    private final double[][] units,normals;
    private final double[] angles;
    public final double length;
    public GpsRoute(String name,List<Point> points){
        if(points.size()<2||points.size()>MAX_POINTS)throw new IllegalArgumentException("Route needs 2–50000 points");
        this.name=cleanName(name);this.points=Collections.unmodifiableList(new ArrayList<>(points));
        int n=points.size();along=new double[n];units=new double[n][];normals=new double[n-1][];angles=new double[n-1];
        for(int i=0;i<n;i++){Point p=points.get(i);units[i]=Geo.unit(p.lat,p.lon);if(i>0){
            double[] cross=Geo.cross(units[i-1],units[i]);double norm=Geo.norm(cross),dot=Geo.dot(units[i-1],units[i]);
            double angle=Math.atan2(norm,dot);if(angle>Math.PI-1e-7)throw new IllegalArgumentException("Antipodal route edge is ambiguous");
            angles[i-1]=angle;normals[i-1]=norm>1e-15?new double[]{cross[0]/norm,cross[1]/norm,cross[2]/norm}:null;
            along[i]=along[i-1]+angle*Geo.R;
        }}
        length=along[n-1];if(length<.1)throw new IllegalArgumentException("Route has no measurable length");
    }
    public static String cleanName(String s){
        if(s==null)return "Parcours";String clean=s.replaceAll("[\\p{Cntrl}]"," ").trim();
        return clean.isEmpty()?"Parcours":clean.substring(0,Math.min(120,clean.length()));
    }
    public GpsRoute reverse(){ArrayList<Point> p=new ArrayList<>(points);Collections.reverse(p);return new GpsRoute(name+" · retour",p);}
    public Point at(double s){
        s=Math.max(0,Math.min(length,s));int i=Arrays.binarySearch(along,s);if(i>=0)return points.get(i);i=Math.max(1,-i-1);
        double angle=angles[i-1];if(angle<1e-12)return points.get(i);
        double t=(s-along[i-1])/(angle*Geo.R),a=Math.sin((1-t)*angle)/Math.sin(angle),b=Math.sin(t*angle)/Math.sin(angle);
        double[] u=units[i-1],v=units[i];double x=a*u[0]+b*v[0],y=a*u[1]+b*v[1],z=a*u[2]+b*v[2];
        return new Point(Math.toDegrees(Math.atan2(z,Math.hypot(x,y))),Math.toDegrees(Math.atan2(y,x)),Double.NaN);
    }
    public static final class Match {
        public final double progress,distance;
        public final boolean ambiguous;
        Match(double s,double d,boolean ambiguous){progress=s;distance=d;this.ambiguous=ambiguous;}
    }
    public List<Match> candidates(double lat,double lon,double low,double high,double radius){
        double[] p=Geo.unit(lat,lon);ArrayList<Match> result=new ArrayList<>();
        int first=Math.max(0,insertion(low)-1),last=Math.min(angles.length,insertion(high)+1);
        for(int i=first;i<last;i++){
            double start=Math.max(low,along[i]),end=Math.min(high,along[i+1]);if(end<start)continue;
            double[] a=units[i],n=normals[i];double t=0;
            if(n!=null){double d=Geo.dot(p,n);double[] q={p[0]-d*n[0],p[1]-d*n[1],p[2]-d*n[2]};double norm=Geo.norm(q);
                if(norm>1e-12){q[0]/=norm;q[1]/=norm;q[2]/=norm;t=Math.atan2(Geo.dot(Geo.cross(a,q),n),Geo.dot(a,q));}
            }
            double s=Math.max(start,Math.min(end,along[i]+t*Geo.R));
            Point q=atEdge(i,s);double dist=Geo.distance(lat,lon,q.lat,q.lon);
            for(double e:new double[]{start,end}){q=atEdge(i,e);double d=Geo.distance(lat,lon,q.lat,q.lon);if(d<dist){dist=d;s=e;}}
            if(dist>radius)continue;
            Match match=new Match(s,dist,false);boolean merged=false;
            for(int k=0;k<result.size();k++)if(Math.abs(result.get(k).progress-s)<5){if(dist<result.get(k).distance)result.set(k,match);merged=true;break;}
            if(!merged)result.add(match);
            result.sort(Comparator.comparingDouble(m->m.distance));if(result.size()>24)result.remove(result.size()-1);
        }
        return result;
    }
    public Match nearest(double lat,double lon,double low,double high,double expected,double ambiguityRadius){
        List<Match> matches=candidates(lat,lon,low,high,Double.POSITIVE_INFINITY);
        if(matches.isEmpty())return new Match(expected,Double.POSITIVE_INFINITY,false);
        Match best=matches.get(0);
        for(Match m:matches)if(Math.abs(m.distance-best.distance)<1e-5&&Math.abs(m.progress-expected)<Math.abs(best.progress-expected))best=m;
        boolean ambiguous=false;for(Match m:matches)if(Math.abs(m.progress-best.progress)>Math.max(50,4*ambiguityRadius)&&m.distance<=best.distance+ambiguityRadius){ambiguous=true;break;}
        return new Match(best.progress,best.distance,ambiguous);
    }
    private int insertion(double s){int i=Arrays.binarySearch(along,s);return i>=0?i:-i-1;}
    private Point atEdge(int i,double s){
        double a=angles[i];if(a<1e-12)return points.get(i);
        double t=(s-along[i])/(a*Geo.R),ka=Math.sin((1-t)*a)/Math.sin(a),kb=Math.sin(t*a)/Math.sin(a);
        double[] u=units[i],v=units[i+1];double x=ka*u[0]+kb*v[0],y=ka*u[1]+kb*v[1],z=ka*u[2]+kb*v[2];
        return new Point(Math.toDegrees(Math.atan2(z,Math.hypot(x,y))),Math.toDegrees(Math.atan2(y,x)),Double.NaN);
    }
}
