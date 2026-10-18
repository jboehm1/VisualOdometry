package dev.jboehm.vectorvio.gps;
/** Shared continuity checks for guidance and derived GPX recording. Raw journal
 * rows are preserved separately, including rejected-position diagnostics. */
public final class GpsFixRules {
    private GpsFixRules(){}
    public static boolean plausible(GpsNavigator.Fix previous,GpsNavigator.Fix next){
        if(previous==null)return true;
        double dt=(next.ns-previous.ns)*1e-9;if(dt<=0)return false;
        double distance=Geo.distance(previous.lat,previous.lon,next.lat,next.lon);
        if(distance>80*dt+3*(previous.accuracy+next.accuracy))return false;
        if(Double.isFinite(previous.speed)&&previous.speed>=0&&previous.speed<80&&Double.isFinite(next.speed)&&next.speed>=0&&next.speed<80){
            double maximum=(previous.speed+next.speed)*.5*dt+Math.max(20,3*(previous.accuracy+next.accuracy))+3*dt*dt;
            if(distance>maximum)return false;
        }
        return true;
    }
}
