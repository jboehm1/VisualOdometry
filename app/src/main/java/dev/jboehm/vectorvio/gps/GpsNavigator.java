package dev.jboehm.vectorvio.gps;
/** Deterministic GPS-only navigation state. All times are monotonic nanoseconds. */
public final class GpsNavigator {
    public enum Status { SEARCHING,TO_START,FOLLOWING,OFF_ROUTE,AMBIGUOUS,POOR_ACCURACY,STALE,REACQUIRE,OUTLIER,ARRIVED,PAUSED }
    public static final class Fix {
        public final double lat,lon,accuracy,speed,bearing,ele;public final long ns;
        public Fix(double lat,double lon,double accuracy,double speed,double bearing,double ele,long ns){this.lat=lat;this.lon=lon;this.accuracy=accuracy;this.speed=speed;this.bearing=bearing;this.ele=ele;this.ns=ns;}
    }
    public final GpsRoute route;
    private final RouteMatcher matcher=new RouteMatcher();
    private Fix accepted;
    private boolean initialized,join,reacquire,off;
    private int outside,inside,arrivals,reacquireCount;
    private long reacquireSince;private double reacquireRadius;private Fix reacquireFix;
    private long outsideSince,arrivalSince,lastReceived;
    public double progress,offDistance=Double.NaN,targetBearing=Double.NaN;
    public Status status=Status.SEARCHING;
    public GpsNavigator(GpsRoute route){this.route=route;}
    public void joinNearest(){join=true;initialized=false;reacquire=false;accepted=null;lastReceived=0;matcher.clear();outside=inside=arrivals=0;off=false;status=Status.SEARCHING;}
    public void selectProgress(double s){if(!Double.isFinite(s))throw new IllegalArgumentException("Invalid route progress");progress=Math.max(0,Math.min(route.length,s));join=false;initialized=true;reacquire=false;accepted=null;lastReceived=0;matcher.seed(progress);outside=inside=arrivals=0;off=false;status=Status.SEARCHING;}
    public boolean update(Fix f,long now){
        if(f==null||!Geo.valid(f.lat,f.lon)||!Double.isFinite(f.accuracy)||f.accuracy<=0||f.ns<=0||f.ns>now||now-f.ns>5_000_000_000L){status=Status.STALE;return false;}
        if(f.ns<=lastReceived)return false;lastReceived=f.ns;
        if(f.accuracy>30){status=Status.POOR_ACCURACY;outside=inside=arrivals=0;matcher.clear();return false;}
        double dt=accepted==null?1:(f.ns-accepted.ns)*1e-9;
        if(!reacquire&&accepted!=null&&dt>15){
            reacquire=true;reacquireCount=0;reacquireFix=null;
            reacquireRadius=Math.max(100,Math.min(3000,dt*(Double.isFinite(f.speed)?Math.max(5,f.speed+2):10)+4*f.accuracy));matcher.clear();
        }
        if(reacquire){
            double stepTime=reacquireFix==null?1:Math.max(.001,(f.ns-reacquireFix.ns)*1e-9);
            double movement=reacquireFix==null?0:Geo.distance(reacquireFix.lat,reacquireFix.lon,f.lat,f.lon);
            GpsRoute.Match candidate=matcher.update(route,f.lat,f.lon,f.accuracy,Math.max(0,progress-reacquireRadius),Math.min(route.length,progress+reacquireRadius),progress,movement,stepTime,Double.NaN);
            reacquireFix=f;offDistance=candidate.distance;
            if(!candidate.ambiguous&&f.accuracy<=15&&candidate.distance+f.accuracy<20){
                if(reacquireCount++==0)reacquireSince=f.ns;
                if(reacquireCount>=3&&f.ns-reacquireSince>=2_000_000_000L){
                    progress=candidate.progress;accepted=f;reacquire=false;off=false;outside=inside=arrivals=0;status=Status.FOLLOWING;
                    GpsRoute.Point target=route.at(Math.min(route.length,progress+15));targetBearing=Geo.bearing(f.lat,f.lon,target.lat,target.lon);return true;
                }
            }else reacquireCount=0;
            status=candidate.ambiguous?Status.AMBIGUOUS:Status.REACQUIRE;return false;
        }
        double travel=accepted==null?0:Geo.distance(accepted.lat,accepted.lon,f.lat,f.lon);
        if(!GpsFixRules.plausible(accepted,f)){
            status=Status.OUTLIER;matcher.clear();return false;
        }
        if(!initialized&&!join){
            GpsRoute.Point start=route.points.get(0);offDistance=Geo.distance(f.lat,f.lon,start.lat,start.lon);targetBearing=Geo.bearing(f.lat,f.lon,start.lat,start.lon);
            accepted=f;if(offDistance>Math.max(15,Math.min(30,2*f.accuracy))){status=Status.TO_START;return true;}initialized=true;progress=0;matcher.seed(0);
        }
        double radius=accepted==null?100:Math.max(60,Math.min(1200,travel+4*f.accuracy+30));
        double low=join?0:Math.max(0,progress-radius),high=join?route.length:Math.min(route.length,progress+radius);
        double observed=Double.isFinite(f.speed)&&f.speed>=0&&f.speed<80?f.speed*Math.min(15,dt):travel;
        GpsRoute.Match m=matcher.update(route,f.lat,f.lon,f.accuracy,low,high,progress,observed,Math.max(.001,dt),Double.isFinite(f.bearing)&&f.speed>=1?f.bearing:Double.NaN);
        offDistance=m.distance;accepted=f;
        if(m.ambiguous){status=Status.AMBIGUOUS;arrivals=0;return true;}
        if(join){initialized=true;join=false;}
        if(m.distance<=20+2*f.accuracy)progress=m.progress;
        if(m.distance-f.accuracy>20){if(outside++==0)outsideSince=f.ns;inside=0;if(outside>=3&&f.ns-outsideSince>=2_000_000_000L)off=true;}
        else if(m.distance+f.accuracy<20){outside=0;if(++inside>=2)off=false;}
        else {outside=inside=0;}
        GpsRoute.Point end=route.points.get(route.points.size()-1);
        boolean close=route.length-progress<25&&f.accuracy<=20&&Geo.distance(f.lat,f.lon,end.lat,end.lon)<Math.max(10,f.accuracy)&&!off;
        if(close){if(arrivals++==0)arrivalSince=f.ns;}else arrivals=0;
        status=arrivals>=3&&f.ns-arrivalSince>=2_000_000_000L?Status.ARRIVED:off?Status.OFF_ROUTE:Status.FOLLOWING;
        GpsRoute.Point target=route.at(off?m.progress:Math.min(route.length,progress+Math.max(15,Math.min(100,observed*5/Math.max(.001,dt)))));
        targetBearing=Geo.bearing(f.lat,f.lon,target.lat,target.lon);return true;
    }
    public void tick(long now){if(status==Status.POOR_ACCURACY&&now-lastReceived<=5_000_000_000L)return;if(lastReceived>0&&now-lastReceived>5_000_000_000L){status=Status.STALE;outside=inside=arrivals=0;return;}if(accepted==null){if(status!=Status.POOR_ACCURACY&&status!=Status.REACQUIRE)status=Status.SEARCHING;}else if(now-accepted.ns>5_000_000_000L){status=Status.STALE;outside=inside=arrivals=0;}}
}
