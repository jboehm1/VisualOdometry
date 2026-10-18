package dev.jboehm.vectorvio.gps;
import java.util.*;
/** Synthetic reference-route tests, not road-network recognition or device accuracy. */
public final class GpsBenchmark {
    private static GpsRoute.Point p(double x,double y){return new GpsRoute.Point(Math.toDegrees(y/Geo.R),Math.toDegrees(x/Geo.R),Double.NaN);}
    public static void main(String[] args){
        int runs=0,failures=0;double worst=0;long started=System.nanoTime();
        GpsRoute route=new GpsRoute("parallel-loop",Arrays.asList(p(0,0),p(400,0),p(400,25),p(0,25),p(0,0)));
        for(int seed=0;seed<30;seed++){
            Random rng=new Random(seed+20261018);GpsNavigator nav=new GpsNavigator(route);double squares=0;int count=0;
            for(int t=0;t<390;t++){
                double truth=2*t;GpsRoute.Point actual=route.at(truth);double noiseX=rng.nextGaussian()*5,noiseY=rng.nextGaussian()*5;
                // Half of seeds have a persistent 7m bias toward the parallel path.
                if(seed%2==0)noiseY+=7;
                GpsNavigator.Fix f=new GpsNavigator.Fix(actual.lat+Math.toDegrees(noiseY/Geo.R),actual.lon+Math.toDegrees(noiseX/Geo.R),12,2,Double.NaN,Double.NaN,(t+1)*1_000_000_000L);
                nav.update(f,f.ns);
                if(t>10){double error=nav.progress-truth;squares+=error*error;count++;}
                if(nav.status==GpsNavigator.Status.ARRIVED&&truth<route.length-30)throw new AssertionError("premature arrival");
            }
            double rmse=Math.sqrt(squares/count);worst=Math.max(worst,rmse);if(rmse>20)failures++;runs++;
        }
        ArrayList<GpsRoute.Point> dense=new ArrayList<>();for(int i=0;i<50000;i++)dense.add(p(i,0));GpsRoute large=new GpsRoute("50000 points",dense);GpsNavigator nav=new GpsNavigator(large);long begin=System.nanoTime();
        for(int i=0;i<1000;i++){GpsRoute.Point a=large.at(i*2);GpsNavigator.Fix f=new GpsNavigator.Fix(a.lat,a.lon,5,2,90,Double.NaN,(i+1)*1_000_000_000L);nav.update(f,f.ns);}
        double ms=(System.nanoTime()-begin)/1e6;
        System.out.printf(Locale.US,"{\"scenario\":\"synthetic_parallel_loop_850m\",\"seeds\":%d,\"failures\":%d,\"worst_progress_rmse_m\":%.6f,\"dense_50000_points_1000_fixes_host_ms\":%.3f,\"total_host_ms\":%.3f}%n",runs,failures,worst,ms,(System.nanoTime()-started)/1e6);
        if(failures>0)throw new AssertionError("progress regression");
        if(Math.abs(nav.progress-1998)>10)throw new AssertionError("dense route progress");
    }
}
