package dev.jboehm.vectorvio.gps;

/**
 * Conservative, geometrical GPX direction-change hint. A sharp bend is NOT
 * evidence of a physical crossroads or of a visually verified trail.
 */
public final class IntersectionAdvisor {
    public static final class Hint {
        public final double metersAhead,turnDegrees;
        public final boolean needsVisualCheck;
        Hint(double metersAhead,double turnDegrees,boolean needsVisualCheck){this.metersAhead=metersAhead;this.turnDegrees=turnDegrees;this.needsVisualCheck=needsVisualCheck;}
    }
    /** Looks for an upcoming turn in the next 45m of imported track only. */
    public static Hint upcoming(GpsRoute route,double progress,double gpsAccuracy){
        if(route==null||!Double.isFinite(progress)||!Double.isFinite(gpsAccuracy)||gpsAccuracy>25||gpsAccuracy<=0)return null;
        int count=route.points.size();
        for(int i=1;i<count-1;i++){
            double ahead=route.progressAt(i)-progress;
            if(ahead<0||ahead>45)continue;
            GpsRoute.Point a=route.points.get(i-1),b=route.points.get(i),c=route.points.get(i+1);
            double leg0=Geo.distance(a.lat,a.lon,b.lat,b.lon),leg1=Geo.distance(b.lat,b.lon,c.lat,c.lon);
            if(leg0<3||leg1<3)continue; // sparse or redundant points
            double before=Geo.bearing(a.lat,a.lon,b.lat,b.lon),after=Geo.bearing(b.lat,b.lon,c.lat,c.lon);
            double turn=Math.IEEEremainder(after-before,360);
            if(Math.abs(turn)>=40)return new Hint(ahead,turn,gpsAccuracy>Math.max(4,ahead*.5));
        }
        return null;
    }
    private IntersectionAdvisor(){}
}
