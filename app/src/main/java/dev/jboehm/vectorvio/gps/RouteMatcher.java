package dev.jboehm.vectorvio.gps;
import java.util.*;
/** Online bounded Viterbi-style beam on the imported polyline, NOT an OSM graph.
 * Scores are engineering likelihood costs, not calibrated confidence percentages. */
public final class RouteMatcher {
    static final class State {final double s,cost;State(double s,double cost){this.s=s;this.cost=cost;}}
    private List<State> beam=new ArrayList<>();
    public void clear(){beam.clear();}
    public void seed(double progress){beam=new ArrayList<>();beam.add(new State(progress,0));}
    public GpsRoute.Match update(GpsRoute route,double lat,double lon,double accuracy,double low,double high,double expected,double travel,double dt,double bearing){
        List<GpsRoute.Match> candidates=route.candidates(lat,lon,low,high,Math.max(20,2*accuracy));
        if(candidates.isEmpty()){clear();return route.nearest(lat,lon,low,high,expected,accuracy);}
        ArrayList<State> next=new ArrayList<>();double sigma=Math.max(4,accuracy),beta=Math.max(6,accuracy+travel*.25);
        for(GpsRoute.Match m:candidates){
            double emission=.5*Math.pow(m.distance/sigma,2),transition=Double.POSITIVE_INFINITY;
            if(beam.isEmpty())transition=Math.abs(m.progress-expected)/Math.max(30,high-low);
            for(State prev:beam){double delta=m.progress-prev.s;if(Math.abs(delta)>80*dt+4*accuracy+20)continue;
                double cost=prev.cost+Math.abs(Math.abs(delta)-travel)/beta;
                if(Double.isFinite(bearing)&&Math.abs(delta)>2){GpsRoute.Point a=route.at(prev.s),b=route.at(m.progress);
                    double direction=Geo.bearing(a.lat,a.lon,b.lat,b.lon),difference=Math.IEEEremainder(direction-bearing,360);
                    cost+=.5*Math.pow(difference/35,2);
                }
                transition=Math.min(transition,cost);
            }
            if(Double.isFinite(transition))next.add(new State(m.progress,emission+transition));
        }
        if(next.isEmpty()){clear();return new GpsRoute.Match(expected,candidates.get(0).distance,true);}
        next.sort(Comparator.comparingDouble(s->s.cost));State winner=next.get(0);double distance=Double.POSITIVE_INFINITY;boolean ambiguous=false;
        for(GpsRoute.Match m:candidates)if(Math.abs(m.progress-winner.s)<.01)distance=m.distance;
        for(State s:next)if(Math.abs(s.s-winner.s)>Math.max(20,2*accuracy)&&s.cost-winner.cost<2.3){ambiguous=true;break;}
        beam=new ArrayList<>();for(int i=0;i<Math.min(12,next.size());i++)beam.add(new State(next.get(i).s,next.get(i).cost-winner.cost));
        return new GpsRoute.Match(winner.s,distance,ambiguous);
    }
}
